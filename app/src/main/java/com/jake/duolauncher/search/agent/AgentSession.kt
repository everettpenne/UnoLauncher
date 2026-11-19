package com.jake.duolauncher.search.agent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * One agent turn, owned by the app rather than by the panel — the same shape as [com.jake.duolauncher.search.AssistantSession], so search can
 * close while a plan runs and the island keeps saying what is happening.
 *
 * It owns the confirmation handshake: a plan that leaves the phone parks in [AgentState.Confirming] until the UI calls [confirm]. Everything
 * else it decides (grounding, the loop, the summary) is already unit-tested elsewhere; this class is the wiring and is tested with fakes.
 */
internal class AgentSession(
    private val planner: () -> Planner,
    /** The environment for this turn: re-read per run, because the panel that supplies it can be rebuilt between turns. */
    private val env: () -> ToolEnv,
    private val tools: List<Tool> = ToolRegistry.builtIn,
    /** Read for each turn, so a switch flipped in settings holds from the next question. */
    private val settings: () -> AgentSettings = { AgentSettings() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    /** Called after every state change, so the island's card can follow the turn. */
    private val publish: () -> Unit = {},
    /** Whether search is on screen. A finished turn the person is looking at needs no line on the island, and does not expire under them. */
    private val searchOpen: () -> Boolean = { false },
    /** How long a finished turn's line stays on the island when search is closed, before the turn clears itself. */
    private val expiryMs: Long = EXPIRY_MS,
    private val clock: () -> Long = { System.currentTimeMillis() },
    /** Told each finished exchange (what was said, what came back, and which kind), for the chat history. */
    private val record: (said: String, reply: String, kind: com.jake.duolauncher.search.ChatTurn.Kind) -> Unit = { _, _, _ -> },
) {
    var state by mutableStateOf<AgentState>(AgentState.Idle)
        private set

    /** The sentence the person sees when the turn ends: what was done, the question, or why not. */
    var summary by mutableStateOf<String?>(null)
        private set

    /** Whether the last turn left something that can be taken back. */
    var canUndo by mutableStateOf(false)
        private set

    /** The question this turn is for. The panel shows the turn only beside that question, so a result never sits under a new one. */
    var asked by mutableStateOf<String?>(null)
        private set

    /** An undo is running: the panel's Undo waits for it, so a second tap cannot fire a second undo. */
    var undoing by mutableStateOf(false)
        private set

    /** The turn is over (done, failed, or a question back to the person) and only waiting to be seen. */
    val settled: Boolean get() = state is AgentState.Done || state is AgentState.Failed || state is AgentState.Asking

    /** A question back to the person that their next words can answer: the request it came from, the question, and (when one value
     *  is all that was missing) where the answer goes. One per turn: a second question ends the turn. */
    class Awaiting(val request: String, val question: String, val fix: Fix?)
    var awaiting by mutableStateOf<Awaiting?>(null)
        private set

    private var job: Job? = null
    private var expiry: Job? = null
    private var gate: CompletableDeferred<Boolean>? = null
    private var lastExecutor: Executor? = null

    /** Plans and runs [query]. A previous turn is abandoned. */
    fun run(query: String, facts: String = "", grounding: Grounding = Grounding(), planOnly: Boolean = false) {
        awaiting = null
        groundingNow = grounding
        turn(asked = query, request = query, mayAwait = true) { loop, block -> loop.run(query, facts, block(query), planOnly) }
    }

    /**
     * The person's [answer] to the question this turn asked. Where one value was missing it is filled in and the turn carries on at
     * once; otherwise the request is planned again with the question and the answer beside it. With nothing waiting, it is a new request.
     */
    fun answer(answer: String, facts: String = "", grounding: Grounding = Grounding(), planOnly: Boolean = false) {
        val waiting = awaiting ?: return run(answer, facts, grounding, planOnly)
        awaiting = null
        groundingNow = grounding
        val fix = waiting.fix
        turn(asked = answer, request = waiting.request, mayAwait = false) { loop, block ->
            if (fix != null) loop.resume(fix, waiting.request, answer, block(waiting.request + " " + answer), planOnly)
            else {
                val asked = "${waiting.request}\nYou asked: ${waiting.question}\nAnswer: ${answer.trim()}"
                loop.run(asked, facts, block(waiting.request + " " + answer), planOnly)
            }
        }
    }

    private var groundingNow: Grounding = Grounding()

    private var last: LastTurn? = null

    /** The last task, while it can still be followed up ([FollowUp.WINDOW_MS]). It outlives its card: "undo that" works after search has
     *  been closed and opened again. */
    fun previous(): LastTurn? = last?.takeIf { clock() - it.atMs < FollowUp.WINDOW_MS }

    /** Whether [query] follows on from the last task, so the panel should hand it to [followUp] and not treat it as a new request. */
    fun follows(query: String): Boolean = previous()?.let { FollowUp.read(query, it.steps) != FollowUp.Kind.None } == true

    /**
     * Words about the last task: taking it back, doing it again, changing one of its values (none of which needs the planner), or
     * anything else that leans on it, which is planned with the last task beside it. False when [query] is not a follow-up.
     */
    fun followUp(query: String, facts: String = "", grounding: Grounding = Grounding(), planOnly: Boolean = false): Boolean {
        val prev = previous() ?: return false
        val kind = FollowUp.read(query, prev.steps)
        if (kind == FollowUp.Kind.None) return false
        awaiting = null
        groundingNow = grounding
        // What the task is remembered by is everything said for it, so it can be done again or changed again.
        val said = when (kind) {
            is FollowUp.Kind.Amend -> prev.request + "\n" + kind.said
            FollowUp.Kind.Refers -> query
            else -> prev.request
        }
        turn(asked = query, request = said, mayAwait = kind == FollowUp.Kind.Refers) { loop, block ->
            when (kind) {
                FollowUp.Kind.Undo -> {
                    val result = prev.executor.undoLast()
                    val outcome = if (result == null) AgentOutcome.Done("There is nothing left to undo.", emptyList(), false)
                        else if (result.ok) AgentOutcome.Done(result.say, emptyList(), prev.executor.canUndo) else AgentOutcome.Failed(result.say)
                    state = if (outcome is AgentOutcome.Done) AgentState.Done(outcome.summary, outcome.canUndo) else AgentState.Failed((outcome as AgentOutcome.Failed).why)
                    outcome
                }
                FollowUp.Kind.Again -> loop.rerun(Plan.Steps(prev.steps), prev.request, block(prev.request), planOnly)
                is FollowUp.Kind.Amend -> loop.rerun(kind.plan, said, block(prev.request), planOnly)
                else -> loop.run(query, facts, listOf(block(query), FollowUp.block(prev)).filter { it.isNotBlank() }.joinToString("\n"), planOnly)
            }
        }
        return true
    }

    private fun turn(asked: String, request: String, mayAwait: Boolean, body: suspend (AgentLoop, (String) -> String) -> AgentOutcome) {
        job?.cancel()
        expiry?.cancel()
        gate?.complete(false)
        gate = null
        // A new question starts clean: the old turn's sentence and undo do not belong to it.
        summary = null
        canUndo = false
        this.asked = asked.trim()
        val settings = settings()
        val env = env()
        // Only what the phone can do right now is offered: a tool whose capability is missing is not in the catalogue at all.
        val tools = ToolRegistry.available(env.capabilities(), tools)
        val executor = Executor(env, settings)
        val loop = AgentLoop(
            planner = planner(),
            executor = executor,
            tools = tools,
            settings = settings,
            confirm = {
                // The loop has already published Confirming(steps); wait for the person.
                val waiting = CompletableDeferred<Boolean>()
                gate = waiting
                waiting.await()
            },
            onState = { state = it; log("state ${it::class.simpleName}: $it"); publish() },
        )
        val grounding = groundingNow
        job = scope.launch {
            val outcome = body(loop) { words -> GroundingLogic.block(words, grounding.people, grounding.apps, grounding.replies) }
            summary = when (outcome) {
                is AgentOutcome.Done -> outcome.summary
                is AgentOutcome.Asks -> outcome.question
                is AgentOutcome.Refused -> outcome.reason
                is AgentOutcome.Failed -> outcome.why
                AgentOutcome.Cancelled -> "Nothing was done."
            }
            log("outcome $outcome")
            val kind = when (outcome) {
                is AgentOutcome.Done, AgentOutcome.Cancelled -> com.jake.duolauncher.search.ChatTurn.Kind.DONE
                is AgentOutcome.Asks -> com.jake.duolauncher.search.ChatTurn.Kind.ASKED
                is AgentOutcome.Refused -> com.jake.duolauncher.search.ChatTurn.Kind.REFUSED
                is AgentOutcome.Failed -> null
            }
            if (kind != null) record(asked, summary.orEmpty(), kind)
            if (outcome is AgentOutcome.Asks && mayAwait) awaiting = Awaiting(request, outcome.question, outcome.fix)
            canUndo = outcome is AgentOutcome.Done && outcome.canUndo
            lastExecutor = executor
            // What was done is remembered for the next words to be about. Taking it back leaves the task it undid as the last one.
            if (outcome is AgentOutcome.Done && outcome.outcomes.any { it.result.ok })
                last = LastTurn(request, outcome.outcomes.filter { it.result.ok }.map { it.step }, outcome.summary, clock(), executor)
            publish()
            expireUnseen()
        }
    }

    /**
     * A finished turn nobody is looking at says so on the island for a few seconds and then ends: what it did is already visible in
     * its own right (the timer is on the island), and a line that never leaves is noise. One the person is looking at in search stays
     * until they move on ([seen], or a new question). A question back to the person waits for them.
     */
    private fun expireUnseen() {
        expiry?.cancel()
        if (state !is AgentState.Done && state !is AgentState.Failed) return
        expiry = scope.launch {
            kotlinx.coroutines.delay(expiryMs)
            if ((state is AgentState.Done || state is AgentState.Failed) && !searchOpen()) clear()
        }
    }

    /** Search showing the turn has closed, or the person has moved on to another question: a turn that is over is cleared. One still
     *  planning, acting or waiting for a yes is left alone. */
    fun seen() {
        // A question the person has not answered yet waits for them a while: closing search to look something up does not lose it.
        if (state is AgentState.Asking && awaiting != null) {
            expiry?.cancel()
            expiry = scope.launch { kotlinx.coroutines.delay(FollowUp.WINDOW_MS); if (state is AgentState.Asking) clear() }
            return
        }
        if (settled) clear()
    }

    /** The person typed a different question: this turn is not for it. A finished one goes; a running one is abandoned. */
    fun leftFor(query: String) {
        // A question waiting for its answer stays while the answer is typed.
        if (awaiting != null && state is AgentState.Asking) return
        if (asked != null && query.trim() != asked) clear()
    }

    /** Answers a [AgentState.Confirming] turn. Ignored when nothing is waiting. */
    fun confirm(accepted: Boolean) {
        gate?.complete(accepted)
        gate = null
    }

    /** Takes back the last turn's most recent undoable step. */
    suspend fun undo(): ToolResult? {
        if (undoing) return null
        val executor = lastExecutor ?: return null
        undoing = true
        val result = try { executor.undoLast() } finally { undoing = false }
        result ?: return null
        canUndo = executor.canUndo
        summary = result.say
        // The turn's state is what the panel and the island draw: it now says what the undo did (or that it could not).
        state = AgentState.Done(result.say, canUndo)
        log("undo ${result.ok}: ${result.say}")
        publish()
        expireUnseen()
        return result
    }

    /** Ends the turn's state without discarding what it did: the island keeps the summary. */
    fun clear() {
        job?.cancel()
        expiry?.cancel()
        gate?.complete(false)
        gate = null
        asked = null
        awaiting = null
        state = AgentState.Idle
        summary = null
        canUndo = false
        lastExecutor = null
        publish()
    }

    /**
     * Logs the turn for a device run, and only when asked for: the lines carry what was asked and what was done (a reply's words), which
     * do not belong in the phone's log by default. A test turns them on with `adb shell setprop log.tag.UnoAgent DEBUG`.
     * Silent where Android's log is not there (unit tests).
     */
    private fun log(message: String) {
        runCatching { if (android.util.Log.isLoggable(LOG_TAG, android.util.Log.DEBUG)) android.util.Log.d(LOG_TAG, message) }
    }

    companion object {
        private const val LOG_TAG = "UnoAgent"
        const val EXPIRY_MS = 6_000L
    }

    /** For tests: waits for the running turn to finish. */
    internal suspend fun awaitTurn() = job?.join()
}
