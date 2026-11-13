package com.jake.duolauncher.search.agent

/** What the planner is given: the question, the facts Uno gathered, the tools, and anything grounding resolved. */
internal data class PlanRequest(
    val query: String,
    val context: String,
    val tools: List<Tool>,
    val grounding: String = "",
)

/** Turns a question into a plan. The real one asks Uno Lab; the tests use a fake. */
internal interface Planner {
    suspend fun plan(request: PlanRequest): ParseResult
}

/** Where the loop is, for the island and the panel to show. */
internal sealed interface AgentState {
    data object Idle : AgentState
    data class Planning(val query: String) : AgentState
    data class Confirming(val steps: List<String>) : AgentState
    data class Acting(val step: Step) : AgentState

    /** The end of a turn: what was done, in a sentence. */
    data class Done(val summary: String, val canUndo: Boolean) : AgentState
    data class Asking(val question: String) : AgentState
    data class Failed(val why: String) : AgentState
}

/** How a turn ended. */
internal sealed interface AgentOutcome {
    data class Done(val summary: String, val outcomes: List<Executor.Outcome>, val canUndo: Boolean) : AgentOutcome
    data class Asks(val question: String) : AgentOutcome
    data class Refused(val reason: String) : AgentOutcome
    data object Cancelled : AgentOutcome
    data class Failed(val why: String) : AgentOutcome
}

/**
 * One turn: ask the planner, confirm only if something leaves the phone, run it, and say what was done.
 *
 * Local actions run without a tap — their summary is the confirmation — so the only await in the happy path is the plan itself. The loop owns
 * the ordering and the reporting; it never executes anything a tool did not declare, and it never decides a side effect itself.
 */
internal class AgentLoop(
    private val planner: Planner,
    private val executor: Executor,
    private val tools: List<Tool> = ToolRegistry.builtIn,
    private val settings: AgentSettings = AgentSettings(),
    /** Asked only when a step leaves the phone; returns false and nothing runs. */
    private val confirm: suspend (steps: List<String>) -> Boolean = { true },
    private val onState: (AgentState) -> Unit = {},
) {
    /** [planOnly]: stop once there is a plan and say what it would do; nothing is confirmed or run. */
    suspend fun run(query: String, context: String = "", grounding: String = "", planOnly: Boolean = false): AgentOutcome {
        onState(AgentState.Planning(query))
        val parsed = runCatching { planner.plan(PlanRequest(query, context, tools, grounding)) }
            .getOrElse { ParseResult.Failed("I couldn't reach the planner: ${it.message ?: "unknown error"}") }
        return when (parsed) {
            is ParseResult.Failed -> failed(parsed.why)
            is ParseResult.Ok -> when (val plan = parsed.plan) {
                is Plan.Clarify -> { onState(AgentState.Asking(plan.question)); AgentOutcome.Asks(plan.question) }
                is Plan.Refuse -> { onState(AgentState.Failed(plan.reason)); AgentOutcome.Refused(plan.reason) }
                is Plan.Steps -> {
                    // Nothing the person did not give (an address, a number) is acted on: the plan becomes a question instead.
                    val madeUp = executor.invented(plan, query + "\n" + grounding)
                    when {
                        madeUp != null -> { onState(AgentState.Asking(madeUp)); AgentOutcome.Asks(madeUp) }
                        planOnly -> planned(plan)
                        else -> runSteps(executor.bind(plan), unsure = RequestKinds.mismatch(query, plan))
                    }
                }
            }
        }
    }

    /** Undoes the most recent undoable step of the last turn. */
    suspend fun undo(): ToolResult? = executor.undoLast()

    private fun planned(plan: Plan.Steps): AgentOutcome {
        val summary = "Would: " + executor.describe(plan).joinToString(" · ")
        onState(AgentState.Done(summary, false))
        return AgentOutcome.Done(summary, emptyList(), false)
    }

    /** [unsure]: the plan is not the kind of thing the request named, so it is shown and asked about even though nothing in it
     *  leaves the phone. */
    private suspend fun runSteps(plan: Plan.Steps, unsure: Boolean = false): AgentOutcome {
        // A step that cannot be done as asked (nobody to reply to, two people it could mean) is a question, before anything runs
        // and before anyone is asked to approve it.
        executor.problem(plan)?.let { question -> onState(AgentState.Asking(question)); return AgentOutcome.Asks(question) }
        val describe = executor.describe(plan)
        val leaving = plan.steps.filter { ToolPolicy.needsConfirmation(it.tool, settings) }
        if (leaving.isNotEmpty() || unsure) {
            onState(AgentState.Confirming(describe))
            val go = runCatching { confirm(describe) }.getOrDefault(false)
            if (!go) { onState(AgentState.Done("Nothing was done.", false)); return AgentOutcome.Cancelled }
        }
        val outcomes = executor.run(plan) { step -> onState(AgentState.Acting(step)) }
        val summary = summaryOf(outcomes)
        onState(AgentState.Done(summary, executor.canUndo))
        return AgentOutcome.Done(summary, outcomes, executor.canUndo)
    }

    private fun failed(why: String): AgentOutcome {
        onState(AgentState.Failed(why))
        return AgentOutcome.Failed(why)
    }

    companion object {
        /** The one sentence the person sees: what each step did, and the first failure if there was one. Pure, so it is unit-tested. */
        fun summaryOf(outcomes: List<Executor.Outcome>): String {
            if (outcomes.isEmpty()) return "Nothing was done."
            val done = outcomes.filter { it.result.ok }.map { it.result.say }
            val failed = outcomes.firstOrNull { !it.result.ok }
            return when {
                failed != null && done.isEmpty() -> failed.result.say
                failed != null -> done.joinToString(" · ") + " — but " + failed.result.say.replaceFirstChar { it.lowercase() }
                else -> done.joinToString(" · ")
            }
        }
    }
}
