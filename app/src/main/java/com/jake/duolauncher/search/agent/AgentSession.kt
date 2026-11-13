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
) {
    var state by mutableStateOf<AgentState>(AgentState.Idle)
        private set

    /** The sentence the person sees when the turn ends: what was done, the question, or why not. */
    var summary by mutableStateOf<String?>(null)
        private set

    /** Whether the last turn left something that can be taken back. */
    var canUndo by mutableStateOf(false)
        private set

    private var job: Job? = null
    private var gate: CompletableDeferred<Boolean>? = null
    private var lastExecutor: Executor? = null

    /** Plans and runs [query]. A previous turn is abandoned. */
    fun run(query: String, facts: String = "", grounding: Grounding = Grounding(), planOnly: Boolean = false) {
        job?.cancel()
        gate?.complete(false)
        gate = null
        // A new question starts clean: the old turn's sentence and undo do not belong to it.
        summary = null
        canUndo = false
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
        job = scope.launch {
            val outcome = loop.run(query, facts, GroundingLogic.block(query, grounding.people, grounding.apps, grounding.replies), planOnly)
            summary = when (outcome) {
                is AgentOutcome.Done -> outcome.summary
                is AgentOutcome.Asks -> outcome.question
                is AgentOutcome.Refused -> outcome.reason
                is AgentOutcome.Failed -> outcome.why
                AgentOutcome.Cancelled -> "Nothing was done."
            }
            log("outcome $outcome")
            canUndo = outcome is AgentOutcome.Done && outcome.canUndo
            lastExecutor = executor
            publish()
        }
    }

    /** Answers a [AgentState.Confirming] turn. Ignored when nothing is waiting. */
    fun confirm(accepted: Boolean) {
        gate?.complete(accepted)
        gate = null
    }

    /** Takes back the last turn's most recent undoable step. */
    suspend fun undo(): ToolResult? {
        val result = lastExecutor?.undoLast() ?: return null
        canUndo = lastExecutor?.canUndo == true
        summary = result.say
        return result
    }

    /** Ends the turn's state without discarding what it did: the island keeps the summary. */
    fun clear() {
        job?.cancel()
        gate?.complete(false)
        gate = null
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

    private companion object { const val LOG_TAG = "UnoAgent" }

    /** For tests: waits for the running turn to finish. */
    internal suspend fun awaitTurn() = job?.join()
}
