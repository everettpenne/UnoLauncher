package com.jake.duolauncher.search

import org.json.JSONObject

/**
 * Turns the one JSON object Uno Lab's assistant writes in ACTION mode into a [QuickAction] that Spotlight shows as a row. Nothing here runs
 * anything: the row runs only when it is tapped, because the model can be wrong. Anything unknown, malformed or out of range is null, and
 * Spotlight then asks for a plain answer instead. Pure (org.json only), so it is unit-tested.
 */
internal object AssistantIntent {
    private const val MAX_TEXT = 500
    private const val MAX_NAME = 80
    private val clock = Regex("""^(\d{1,2}):(\d{2})$""")

    /** What a reply in ACTION mode comes to: an action to offer, a reason Uno can't do what was asked (said instead of asking for an
     *  answer, which the model can only guess at), or nothing (a question: ask for an answer). */
    sealed interface Outcome {
        data class Act(val action: QuickAction) : Outcome
        data class Cannot(val why: String) : Outcome
        data object None : Outcome
    }

    const val TIMER_LIMIT = "Timers run for up to 24 hours. For longer, set an alarm or a countdown widget."
    const val ALARM_LIMIT = "Alarms can be set up to 24 hours ahead."
    const val NOT_A_TIME = "That isn't a time on the clock."

    /** The action in [json] alone, or null (see [read]). */
    fun parse(json: String): QuickAction? = (read(json, "") as? Outcome.Act)?.action

    /**
     * Reads [json] against the [query] it answered. Where the words are clear and the model is known to slip, the words win: "in 10
     * minutes" is a delay even if the model wrote 10:00, a timer of 0 seconds ("time how long this takes") is the stopwatch, and a label
     * that only repeats the length ("3 hours") is dropped.
     */
    fun read(json: String, query: String): Outcome {
        val obj = runCatching { JSONObject(extract(json) ?: return Outcome.None) }.getOrNull() ?: return Outcome.None
        val action = obj.optString("action")
        if (action == "timer") {
            val seconds = whole(obj, "seconds") ?: return Outcome.None
            if (seconds == 0L) return Outcome.Act(QuickAction.Stopwatch())
            if (seconds > SpotlightSources.MAX_TIMER_SECONDS) return Outcome.Cannot(TIMER_LIMIT)
        }
        if (action == "alarm") {
            val asked = relativeMinutes(query)
            if (asked != null) {
                return if (asked > SpotlightSources.MAX_ALARM_MINUTES) Outcome.Cannot(ALARM_LIMIT)
                else Outcome.Act(QuickAction.Alarm(AlarmTarget.In(asked), SpotlightSources.alarmInLabel(asked)))
            }
            if (obj.has("in_minutes")) whole(obj, "in_minutes")?.let { if (it > SpotlightSources.MAX_ALARM_MINUTES) return Outcome.Cannot(ALARM_LIMIT) }
            else if (alarm(obj) == null && obj.opt("at") is String) return Outcome.Cannot(NOT_A_TIME)
        }
        return pick(obj)?.let { Outcome.Act(it) } ?: Outcome.None
    }

    private val relative = Regex("""\bin\s+(an?|one|half an?|\d+(?:\.\d+)?)\s*(hours?|hrs?|h|minutes?|mins?|m)\b""", RegexOption.IGNORE_CASE)

    /** "in 10 minutes", "in an hour", "in half an hour", "in 1.5 h": the delay asked for, in minutes; null when the words give none. */
    fun relativeMinutes(query: String): Int? {
        val m = relative.find(query) ?: return null
        val amount = when (val n = m.groupValues[1].lowercase()) {
            "a", "an", "one" -> 1.0
            "half a", "half an" -> .5
            else -> n.toDoubleOrNull() ?: return null
        }
        val minutes = amount * (if (m.groupValues[2].lowercase().startsWith("h")) 60 else 1)
        return minutes.toInt().takeIf { it >= 1 }
    }

    private val lengthLabel = Regex("""^(\d+(\.\d+)?\s*(s|secs?|seconds?|m|mins?|minutes?|h|hrs?|hours?|days?)|timer|alarm|countdown|reminder)$""", RegexOption.IGNORE_CASE)

    private fun pick(obj: JSONObject): QuickAction? {
        return when (obj.optString("action")) {
            "timer" -> timer(obj)
            "alarm" -> alarm(obj)
            "stopwatch" -> QuickAction.Stopwatch()
            "torch" -> QuickAction.Torch()
            "focus" -> when (obj.opt("on")) {
                true -> QuickAction.Focus(true, "Turn Focus on", "Hides the apps you picked")
                false -> QuickAction.Focus(false, "Turn Focus off", "Shows the apps Focus hid")
                else -> null
            }
            "note" -> text(obj, "text", MAX_TEXT)?.let { QuickAction.Note(it, "Save note") }
            "open_app" -> text(obj, "name", MAX_NAME)?.let { QuickAction.OpenApp(it, "Open $it") }
            "call" -> text(obj, "name", MAX_NAME)?.let { QuickAction.Call(it, "Call $it") }
            "text" -> text(obj, "name", MAX_NAME)?.let { QuickAction.Text(it, "Text $it") }
            "web" -> text(obj, "query", HandoffLogic.MAX_QUERY)?.let { QuickAction.WebSearch(it, "Search the web for “$it”") }
            else -> null // "none", or anything the schema does not have
        }
    }

    /** The first {...} in [raw]: the grammar asks for exactly one object, but a stray space or newline around it is harmless. */
    private fun extract(raw: String): String? {
        val start = raw.indexOf('{'); val end = raw.lastIndexOf('}')
        return if (start in 0 until end) raw.substring(start, end + 1) else null
    }

    private fun text(obj: JSONObject, key: String, max: Int): String? {
        val value = obj.opt(key) as? String ?: return null
        val clean = value.filter { !it.isISOControl() }.trim()
        return clean.takeIf { it.isNotEmpty() && it.length <= max }
    }

    /** A whole number field, only if it really is one (no strings, no fractions). */
    private fun whole(obj: JSONObject, key: String): Long? = when (val v = obj.opt(key)) {
        is Int -> v.toLong()
        is Long -> v
        is Double -> v.takeIf { it == Math.floor(it) && !it.isInfinite() }?.toLong()
        is java.math.BigDecimal -> runCatching { v.longValueExact() }.getOrNull()
        else -> null
    }

    private fun timer(obj: JSONObject): QuickAction.Timer? {
        val seconds = whole(obj, "seconds") ?: return null
        if (seconds < 1 || seconds > SpotlightSources.MAX_TIMER_SECONDS) return null
        val base = SpotlightSources.timerLabel(seconds)
        val name = text(obj, "label", MAX_NAME)?.takeUnless { lengthLabel.matches(it) }
        return QuickAction.Timer(seconds, if (name != null) "$base · $name" else base)
    }

    private fun alarm(obj: JSONObject): QuickAction.Alarm? {
        if (obj.has("in_minutes")) {
            val minutes = whole(obj, "in_minutes") ?: return null
            if (minutes < 1 || minutes > SpotlightSources.MAX_ALARM_MINUTES) return null
            return QuickAction.Alarm(AlarmTarget.In(minutes.toInt()), SpotlightSources.alarmInLabel(minutes.toInt()))
        }
        val m = clock.matchEntire((obj.opt("at") as? String)?.trim() ?: return null) ?: return null
        val hour = m.groupValues[1].toInt(); val minute = m.groupValues[2].toInt()
        if (hour !in 0..23 || minute !in 0..59) return null
        return QuickAction.Alarm(AlarmTarget.At(hour, minute), SpotlightSources.alarmClockLabel(hour, minute))
    }

    /**
     * Whether [query] is asking something rather than asking the phone to do something. A question will not turn into an action, so
     * Spotlight skips the action ask for it: one model round trip instead of two, which is the largest part of a warm answer.
     *
     * Deliberately conservative — a command that this misses only costs the old action ask first, while a question it wrongly calls a
     * command would be answered as "I can't do that". So a leading command verb or "can you…" wins over a question mark.
     */
    fun looksLikeQuestion(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return false
        if (commandStart.containsMatchIn(q) || requestStart.containsMatchIn(q)) return false
        // "give me three tips", "explain compound interest": an ask for words, never one of the phone's actions.
        if (answerStart.containsMatchIn(q)) return true
        return q.endsWith("?") || questionStart.containsMatchIn(q)
    }

    private val answerStart = Regex(
        """^(give|explain|describe|define|summari[sz]e|translate|suggest|recommend|list|name|show)\b""",
        RegexOption.IGNORE_CASE)

    private val commandStart = Regex(
        """^(set|start|stop|cancel|turn|open|launch|call|text|message|remind|add|make|play|pause|snooze|dismiss|note|remember|create)\b""",
        RegexOption.IGNORE_CASE)
    private val requestStart = Regex("""^(can|could|would|will)\s+you\b""", RegexOption.IGNORE_CASE)
    private val questionStart = Regex("""^(who|what|when|where|why|how|which|whose|is|are|was|were|do|does|did)\b""",
        RegexOption.IGNORE_CASE)
}

/** What Spotlight shows of an answer: plain text. Markdown marks are taken out, and text that is the model talking to itself (its
 *  instructions, a bracketed placeholder) is not shown as an answer. Pure, so it is unit-tested. */
internal object AnswerText {
    private val meta = Regex("""^(\[|you are (an?|the) (expert|helpful|ai|assistant|precise)|think through|analy[sz]e the request|understand what)""", RegexOption.IGNORE_CASE)

    /** The answer to show, or null when [raw] is not one. */
    fun clean(raw: String): String? {
        val t = raw.replace("**", "").replace("__", "").replace(Regex("""(?m)^#+\s*"""), "").trim()
        if (t.isEmpty() || meta.containsMatchIn(t)) return null
        return t
    }

    const val UNCLEAR = "Qwen couldn't answer that clearly. Try asking another way, or continue in Uno Lab."
}

/** When Spotlight offers to ask Uno Lab's assistant. Pure, so it is unit-tested. */
internal object AskPolicy {
    const val MAX_QUERY = 300

    /**
     * A question, not a name: two words at least, and nothing already answers it. A sum or a conversion ([smart]) or a typed command
     * ([quick]) is answered on the spot, so the model is never offered for it. Nothing runs here: the row only sends the query when tapped.
     */
    fun offer(query: String, smart: SmartAnswer?, quick: QuickAction?, state: AssistantState): Boolean {
        val q = query.trim()
        if (q.isEmpty() || q.length > MAX_QUERY) return false
        if (state != AssistantState.READY && state != AssistantState.LOADING) return false
        if (smart != null || quick != null) return false
        return q.split(Regex("""\s+""")).count { w -> w.any { it.isLetterOrDigit() } } >= 2
    }
}
