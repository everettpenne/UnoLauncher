package com.jake.duolauncher.agent.training

import com.jake.duolauncher.AppEntry
import com.jake.duolauncher.search.agent.AgentLoop
import com.jake.duolauncher.search.agent.AgentOutcome
import com.jake.duolauncher.search.agent.Capability
import com.jake.duolauncher.search.agent.EvalRow
import com.jake.duolauncher.search.agent.EvalSet
import com.jake.duolauncher.search.agent.Executor
import com.jake.duolauncher.search.agent.ParseResult
import com.jake.duolauncher.search.agent.PlanParser
import com.jake.duolauncher.search.agent.PlanRequest
import com.jake.duolauncher.search.agent.Planner
import com.jake.duolauncher.search.agent.ReplyTarget
import com.jake.duolauncher.search.agent.Tool
import com.jake.duolauncher.search.agent.ToolEnv
import kotlinx.coroutines.runBlocking

/**
 * A phone that only writes down what was done to it. Every tool acts through [ToolEnv], so the list in [did] is everything a turn
 * changed, in order: "timer 1200", "dial 5550142", "reply Sam Patel/Messages: on my way".
 *
 * Its state (a timer running, music playing, the messages waiting) is read from the same `<context>` and `<known>` blocks the planner
 * was shown, so a tool that looks before it acts sees what the model was told.
 */
internal class RecordingEnv(context: String = "", grounding: String = "") : ToolEnv {
    val did = ArrayList<String>()
    private var timer = "Timer" in context
    private var stopwatch = "Stopwatch" in context
    private var torch = false
    private var focus = "Focus is on" in context || "Do Not Disturb is on" in context
    private val playing = "Playing now" in context
    private var level = 40
    private val waiting = OutcomeScorer.waitingIn(grounding)
    private val list = ArrayList<String>()

    override val noteSlot: Int? = 0
    override val checklistSlot: Int? = 1
    override val apps: List<AppEntry> = emptyList()
    override fun now(): java.time.LocalDateTime = OutcomeScorer.NOW
    override fun capabilities(): Set<Capability> = Capability.entries.toSet()
    override fun focusOn() = focus
    override fun toggleFocus() { focus = !focus; did += "focus $focus" }
    override fun startTimer(seconds: Long) { timer = true; did += "timer $seconds" }
    override fun cancelTimer() { timer = false; did += "timer cancelled" }
    override fun timerRunning() = timer
    override fun toggleStopwatch(): Boolean { stopwatch = !stopwatch; did += "stopwatch $stopwatch"; return stopwatch }
    override fun stopwatchRunning() = stopwatch
    override fun resetStopwatch() { stopwatch = false; did += "stopwatch reset" }
    override fun toggleTorch(): Boolean { torch = !torch; did += "torch $torch"; return torch }
    override fun setTorch(on: Boolean) { torch = on; did += "torch $on" }
    override fun openMessage(number: String, text: String) { did += "message ${digits(number)}: ${words(text)}" }
    override fun media(action: String): Boolean { did += "media $action"; return playing }
    override fun volume(): Int? = level
    override fun setVolume(percent: Int): Boolean { level = percent; did += "volume $percent"; return true }
    override fun addChecklist(item: String): Boolean { did += "checklist ${words(item)}"; return list.add(words(item)) }
    override fun removeChecklist(item: String) { list.remove(words(item)) }
    override fun openDirections(place: String) { did += "directions ${words(place)}" }
    override fun openAlarm(hour: Int?, minute: Int?, inMinutes: Int?) { did += "alarm $hour:$minute +$inMinutes" }
    override fun openAlarm(hour: Int?, minute: Int?, inMinutes: Int?, label: String?) { did += "alarm $hour:$minute +$inMinutes ${words(label.orEmpty())}".trim() }
    override fun openApp(entry: AppEntry): Boolean { did += "app ${entry.label}"; return true }
    override fun openDialer(number: String) { did += "dial ${digits(number)}" }
    override fun openMessages(number: String) { did += "message ${digits(number)}" }
    override fun openUrl(url: String) { did += "url ${url.lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')}" }
    override fun openCalendar(title: String, inMinutes: Int, durationMinutes: Int) { did += "calendar ${words(title)} +$inMinutes for $durationMinutes" }
    override fun openWebSearch(query: String) { did += "search ${words(query)}" }
    override fun openSettingsPanel(panel: String) { did += "panel $panel" }
    override fun writeNote(text: String): String? { did += "note ${words(text)}"; return "" }
    override fun openCamera(mode: String): Boolean { did += "camera $mode"; return true }
    override fun composeEmail(to: String, subject: String, body: String): Boolean { did += "email ${to.lowercase()}: ${words(subject)} / ${words(body)}"; return true }
    private var ring = "ring"
    override fun ringer(): String? = ring
    override fun setRinger(mode: String): Boolean { ring = mode; did += "ringer $mode"; return true }
    override fun playFromSearch(what: String): Boolean { did += "play ${words(what)}"; return true }
    private var bright = 50
    override fun brightness(): Int? = bright
    override fun setBrightness(percent: Int): Boolean { bright = percent; did += "brightness $percent"; return true }
    override fun deviceAction(action: String): Boolean { did += "device $action"; return true }
    override fun addContact(name: String, number: String): Boolean { did += "contact ${words(name)} ${digits(number)}"; return true }
    override fun openAppInfo(entry: AppEntry): Boolean { did += "appinfo ${entry.label}"; return true }
    override fun showAlarms(): Boolean { did += "alarms shown"; return true }
    override fun openCalendarAt(day: java.time.LocalDate): Boolean { did += "calendar at $day"; return true }
    override fun replyTargets(): List<ReplyTarget> = waiting
    override fun sendReply(key: String, text: String): Boolean {
        val to = waiting.firstOrNull { it.key == key }
        did += "reply ${to?.who}/${to?.app}: ${words(text)}"
        return to != null
    }

    private fun digits(text: String) = text.filter { it.isDigit() }

    /** Free text as its words, without the little ones a model may add or drop ("the capital of peru" is "capital of peru"). */
    private fun words(text: String) = OutcomeScorer.word.findAll(text.lowercase()).map { it.value }.filter { it !in OutcomeScorer.small }.joinToString(" ")
}

/** How one turn ended, as far as the person could tell: what kind of ending, and what was done to the phone. */
internal data class Ending(val kind: String, val did: List<String>, val said: String)

/** One row, replayed: what the expected plan comes to, what the model's reply comes to, and how they differ. */
internal class OutcomeRow(val query: String, val wanted: Ending, val got: Ending, val planExact: Boolean, val reply: String?) {
    val same: Boolean get() = wanted.kind == got.kind && wanted.did == got.did

    /** Why the turn did not end as it should have, in the few kinds worth counting apart; null when it did. */
    val cause: String? get() = when {
        same -> null
        got.kind == "failed" -> "the reply could not be used"
        wanted.kind == "done" && got.kind == "asked" -> "asked instead of acting"
        wanted.kind == "done" && got.kind == "refused" -> "refused instead of acting"
        wanted.kind == "done" && got.kind == "done" && wanted.did.map { it.substringBefore(' ') } != got.did.map { it.substringBefore(' ') } -> "did a different thing"
        wanted.kind == "done" && got.kind == "done" -> "did the right thing with a different value"
        wanted.kind == "asked" && got.kind == "done" -> "acted instead of asking"
        wanted.kind == "refused" && got.kind == "done" -> "acted instead of refusing"
        else -> "ended as ${got.kind}, not ${wanted.kind}"
    }
}

/**
 * Scores a model's replies by what they come to, not by what they say. Each reply goes through the real [AgentLoop] (its checks, its
 * mending, its questions) against a [RecordingEnv], and so does the plan the row expects; the row passes when both turns end the same
 * way and do the same things to the phone. [PlanScorer] compares the plan's text, which calls two right plans different and cannot see
 * what the loop does to a plan after the model has written it.
 */
internal object OutcomeScorer {
    val NOW: java.time.LocalDateTime = java.time.LocalDateTime.of(2026, 11, 23, 14, 0)
    val word = Regex("""[\p{L}\p{N}']+""")
    val small = setOf("the", "a", "an", "to", "please")

    private val waitingLine = Regex("""Messages waiting for a reply \(newest first\): (.*)""")

    /** The messages waiting, read back from a `<known>` block ("Sam Patel in Messages; Dana in Signal"), newest first. */
    fun waitingIn(grounding: String): List<ReplyTarget> = waitingLine.find(grounding)?.groupValues?.get(1).orEmpty().split("; ")
        .filter { " in " in it }.mapIndexed { i, each -> ReplyTarget("k$i", each.substringBeforeLast(" in ").trim(), each.substringAfterLast(" in ").trim(), 1_000L - i) }

    /** The text between `<tag>` and `</tag>` in a user turn as the launcher sends it, or "". */
    fun block(turn: String, tag: String): String = turn.substringAfter("<$tag>\n", "").substringBefore("\n</$tag>", "")

    /** Runs one turn of the real loop on [reply] and says how it ended. A plan that leaves the phone is approved, so what it would
     *  send is in the ending. */
    fun ending(query: String, reply: String, context: String, grounding: String, tools: List<Tool>): Ending = runBlocking {
        val env = RecordingEnv(context, grounding)
        val planner = object : Planner {
            override suspend fun plan(request: PlanRequest): ParseResult =
                runCatching { PlanParser.parse(reply, tools) }.getOrElse { ParseResult.Failed("The planner did not answer with JSON.") }
        }
        when (val outcome = AgentLoop(planner, Executor(env, clock = { 0L }), tools = tools, confirm = { true }).run(query, context, grounding)) {
            is AgentOutcome.Done -> Ending(if (outcome.outcomes.all { it.result.ok }) "done" else "done, with a failure", env.did, outcome.summary)
            is AgentOutcome.Asks -> Ending("asked", env.did, outcome.question)
            is AgentOutcome.Refused -> Ending("refused", env.did, outcome.reason)
            is AgentOutcome.Failed -> Ending("failed", env.did, outcome.why)
            AgentOutcome.Cancelled -> Ending("cancelled", env.did, "")
        }
    }

    /** [turns]: each request's user turn as it was sent (the facts and the names beside it); a request with none is run bare. */
    fun score(rows: List<EvalRow>, replies: Map<String, String>, turns: Map<String, String>, tools: List<Tool>): List<OutcomeRow> {
        val plans = PlanScorer.score(rows, replies, tools).rows.associate { it.query to it.exact }
        return rows.map { row ->
            val turn = turns[row.query].orEmpty()
            val context = block(turn, "context"); val grounding = block(turn, "known")
            val wanted = ending(row.query, EvalSet.reply(row), context, grounding, tools)
            val reply = replies[row.query]
            val got = if (reply == null) Ending("failed", emptyList(), "no reply") else ending(row.query, reply, context, grounding, tools)
            // A row that expects a search offer does not say what words to search for (the launcher takes them from the request), so
            // only that a search was made is compared.
            val search = row.expected is EvalRow.Expected.Search
            fun Ending.judged() = if (search) copy(did = did.map { it.substringBefore(' ') }) else this
            OutcomeRow(row.query, wanted.judged(), got.judged(), plans[row.query] == true, reply)
        }
    }
}
