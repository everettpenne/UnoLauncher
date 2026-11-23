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
    /** What the model wrote for the last [plan], word for word and before it was read, for the assistant log; null when nothing came. */
    val lastReply: String? get() = null
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
    /** [search]: what a web search for the request would look for, when one could help (a refusal is not a dead end). */
    data class Failed(val why: String, val search: String? = null) : AgentState
}

/** A plan waiting on one value from the person: which step, and which of its parameters. */
internal data class Fix(val plan: Plan.Steps, val step: Int, val param: String)

/** How a turn ended. */
internal sealed interface AgentOutcome {
    data class Done(val summary: String, val outcomes: List<Executor.Outcome>, val canUndo: Boolean) : AgentOutcome
    /** [fix]: the plan is whole but for one value; the person's answer fills it and the turn carries on without planning again. */
    data class Asks(val question: String, val fix: Fix? = null) : AgentOutcome
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
    /** For the assistant log: every reply the planner wrote this turn (the one that was sent back as well as the one that was used),
     *  and the plan as it stood last (after it was mended, and bound once it was to be run). */
    val replies = ArrayList<String>()
    var plan: Plan.Steps? = null
        private set

    /** [planOnly]: stop once there is a plan and say what it would do; nothing is confirmed or run. */
    suspend fun run(query: String, context: String = "", grounding: String = "", planOnly: Boolean = false): AgentOutcome {
        onState(AgentState.Planning(query))
        var parsed = ask(query, context, grounding)
        // A reply that broke a rule (a tool that does not exist, a value out of range) is sent back once with the reason.
        (parsed as? ParseResult.Failed)?.takeIf { it.retry }?.let { first ->
            parsed = ask("$query\n(Your last reply could not be used: ${first.why}. Reply again.)", context, grounding)
        }
        return when (val result = parsed) {
            is ParseResult.Failed -> failed(result.why)
            is ParseResult.Ok -> when (val plan = result.plan) {
                is Plan.Clarify -> { onState(AgentState.Asking(plan.question)); AgentOutcome.Asks(plan.question) }
                is Plan.Refuse -> {
                    onState(AgentState.Failed(plan.reason, search = SearchOffer.forRequest(query).takeIf { searchTool != null }))
                    AgentOutcome.Refused(plan.reason)
                }
                is Plan.OfferSearch -> offerSearch(plan.query, query, planOnly)
                is Plan.Steps -> proceed(plan, query, grounding, planOnly)
            }
        }
    }

    /** Carries on a turn that stopped to ask for one value ([Fix]): the person's [answer] fills it, and nothing is planned again. */
    suspend fun resume(fix: Fix, request: String, answer: String, grounding: String = "", planOnly: Boolean = false): AgentOutcome {
        val step = fix.plan.steps.getOrNull(fix.step) ?: return failed("I lost track of what I was doing.")
        val filled = when (val checked = step.tool.validate(step.params.values + (fix.param to answer.trim()))) {
            is Validation.Ok -> checked.value
            is Validation.Rejected -> return failed(checked.why)
        }
        val plan = Plan.Steps(fix.plan.steps.mapIndexed { i, s -> if (i == fix.step) Step(s.tool, filled) else s })
        return proceed(plan, "$request\nAnswer: ${answer.trim()}", grounding, planOnly, mayAskAgain = false)
    }

    /** Runs a plan that needs no planning: the last task again, or with a value the person changed. [said] is everything they have
     *  said for it, which the plan's values are still checked against. */
    /** [recognised]: the launcher read the request itself and the plan is exactly what it read ([DirectSkills]), so it is not
     *  checked again against what kind of thing the request named: that check is for a plan a model wrote. */
    suspend fun rerun(plan: Plan.Steps, said: String, grounding: String = "", planOnly: Boolean = false, recognised: Boolean = false): AgentOutcome =
        proceed(plan, said, grounding, planOnly, mayAskAgain = false, recognised = recognised)

    private val searchTool: Tool? get() = tools.firstOrNull { it.name == "web_search" }

    private suspend fun ask(query: String, context: String, grounding: String): ParseResult =
        runCatching { planner.plan(PlanRequest(query, context, tools, grounding)) }
            .also { planner.lastReply?.let { reply -> replies += reply } }
            // A turn that was dropped for a new one is not a planner that could not be reached: it ends, and says nothing.
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrElse { ParseResult.Failed("I couldn't reach the planner: ${it.message ?: "unknown error"}") }

    private suspend fun proceed(planned: Plan.Steps, query: String, grounding: String, planOnly: Boolean, mayAskAgain: Boolean = true,
        recognised: Boolean = false): AgentOutcome {
        val said = query + "\n" + grounding
        val plan = repaired(Plan.Steps(planned.steps.map { Step(it.tool, it.tool.mended(it.params, said)) }), query, said)
        this.plan = plan
        // Nothing the person did not give (an address, a number, a time) is acted on: the plan becomes a question instead.
        executor.missing(plan, said)?.let { (index, missing) ->
            onState(AgentState.Asking(missing.question))
            return AgentOutcome.Asks(missing.question, missing.param?.takeIf { mayAskAgain }?.let { Fix(plan, index, it) })
        }
        if (planOnly) return planned(plan)
        return runSteps(executor.bind(plan), unsure = !recognised && RequestKinds.mismatch(query, plan))
    }

    /**
     * "tell Priya I'll call tonight" with a message from Priya waiting is a reply to it, whatever number the planner wrote: a message
     * step whose number nobody gave becomes a reply to the sender the request names. It still waits for a yes, as every reply does.
     */
    private fun repaired(plan: Plan.Steps, query: String, said: String): Plan.Steps {
        val reply = tools.firstOrNull { it.name == "reply" } ?: return plan
        return Plan.Steps(plan.steps.map { step ->
            if (step.tool.name != "message" || step.tool.missing(step.params, said) == null) return@map step
            val text = step.params.text("text") ?: return@map step
            val sender = executor.replyTargets().sortedByDescending { it.postedAt }.firstOrNull { GroundingLogic.mentions(query.lineSequence().first(), it.who) }
                ?: return@map step
            Step(reply, Params(mapOf("to" to sender.who, "text" to text)))
        })
    }

    /** The planner thinks a web search would help. It is put to the person; nothing is searched on the planner's word. */
    private suspend fun offerSearch(offered: String, query: String, planOnly: Boolean): AgentOutcome {
        val web = searchTool ?: return failed("I can't search the web from here.")
        val words = SearchOffer.query(offered, query)
        if (planOnly) {
            val summary = "Would ask: Search the web for “$words”?"
            onState(AgentState.Done(summary, false))
            return AgentOutcome.Done(summary, emptyList(), false)
        }
        return runSteps(Plan.Steps(listOf(Step(web, Params(mapOf("query" to words))))), unsure = true)
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
        this.plan = plan
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
