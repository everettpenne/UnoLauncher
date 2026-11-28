package com.jake.duolauncher.search.agent

/** What the planner is given: the question, the facts Uno gathered, the tools, and anything grounding resolved. */
internal data class PlanRequest(
    val query: String,
    val context: String,
    val tools: List<Tool>,
    val grounding: String = "",
    /** Which contract the planner is asked under ([Catalogue.V1] or [Catalogue.V2]): the prompt, the grammar and the parser follow it. */
    val protocol: Int = Catalogue.V1,
)

/** Turns a question into a plan. The real one asks Uno Lab; the tests use a fake. */
internal interface Planner {
    suspend fun plan(request: PlanRequest): ParseResult
    /** What the model wrote for the last [plan], word for word and before it was read, for the assistant log; null when nothing came. */
    val lastReply: String? get() = null
}

/**
 * The words a request is sent back to the planner with, when a turn takes more than one question. They are part of what the planner is
 * trained on, so they are written in one place and the training set is built from the same functions. Each goes after the request,
 * inside the same turn, so Uno Lab needs to know nothing about them.
 */
internal object AgentTurns {
    /** The reply broke a rule: the reason, and "Reply again". (The first planners were trained on exactly this line.) */
    fun retry(query: String, why: String): String = "$query\n(Your last reply could not be used: $why. Reply again.)"

    /** What looking found, a line a source, or that it found nothing. */
    fun found(query: String, sources: List<LookSource>, found: String): String =
        "$query\nFound:\n" + found.ifBlank { sources.joinToString("\n") { com.jake.duolauncher.search.context.ContextText.found(it.id, null) } }

    /** The step that was tried, as the planner would write it, and why it failed. */
    fun failed(query: String, step: Step, why: String): String =
        "$query\nTried: " + stepJson(step) + "\nFailed: " + why.replace('\n', ' ').take(200)

    fun stepJson(step: Step): String = org.json.JSONObject().put("tool", step.tool.name)
        .put("params", org.json.JSONObject(step.params.values.filterKeys { !it.startsWith("_") })).toString()
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
    /** The contract the planner was trained on. Under [Catalogue.V2] it may ask to look something up first ([facts]) and is asked
     *  again when a plan's first step fails. */
    private val protocol: Int = Catalogue.V1,
    /** What the phone holds of the kinds asked for, as lines for the planner; "" when there is nothing. Read only when the planner asks. */
    private val facts: suspend (sources: List<LookSource>, query: String) -> String = { _, _ -> "" },
) {
    /** For the assistant log: every reply the planner wrote this turn (the one that was sent back as well as the one that was used),
     *  and the plan as it stood last (after it was mended, and bound once it was to be run). */
    val replies = ArrayList<String>()
    var plan: Plan.Steps? = null
        private set

    /** [planOnly]: stop once there is a plan and say what it would do; nothing is confirmed or run. */
    suspend fun run(query: String, context: String = "", grounding: String = "", planOnly: Boolean = false): AgentOutcome {
        onState(AgentState.Planning(query))
        var parsed = asked(query, context, grounding)
        // The planner wants a fact first (protocol 2): it is read, and the planner is asked once more with it. It may not look twice.
        var request = query
        ((parsed as? ParseResult.Ok)?.plan as? Plan.Look)?.let { look ->
            val found = runCatching { facts(look.sources, query) }.getOrDefault("").trim()
            request = AgentTurns.found(query, look.sources, found)
            parsed = asked(request, context, grounding)
            if ((parsed as? ParseResult.Ok)?.plan is Plan.Look) parsed = ParseResult.Failed("I couldn't find what I needed for that.")
        }
        return finish(parsed, request, context, grounding, planOnly, mayRecover = true)
    }

    /** One question to the planner, and once more with the reason when its reply broke a rule (a tool that does not exist, a value
     *  out of range). */
    private suspend fun asked(query: String, context: String, grounding: String): ParseResult {
        val first = ask(query, context, grounding)
        val failed = (first as? ParseResult.Failed)?.takeIf { it.retry } ?: return first
        return ask(AgentTurns.retry(query, failed.why), context, grounding)
    }

    private suspend fun finish(parsed: ParseResult, query: String, context: String, grounding: String, planOnly: Boolean, mayRecover: Boolean): AgentOutcome {
        return when (val result = parsed) {
            is ParseResult.Failed -> failed(result.why)
            is ParseResult.Ok -> when (val plan = result.plan) {
                is Plan.Clarify -> { onState(AgentState.Asking(plan.question)); AgentOutcome.Asks(plan.question) }
                is Plan.Refuse -> {
                    onState(AgentState.Failed(plan.reason, search = SearchOffer.forRequest(query).takeIf { searchTool != null }))
                    AgentOutcome.Refused(plan.reason)
                }
                is Plan.OfferSearch -> offerSearch(plan.query, query, planOnly)
                is Plan.Look -> failed("I couldn't find what I needed for that.")
                is Plan.Steps -> {
                    val outcome = proceed(plan, query, grounding, planOnly)
                    // A plan whose first step failed, with nothing done (protocol 2): the planner is told what was tried and why
                    // it failed, once, and may plan another way, ask, or refuse. Nothing is retried after a step has changed anything.
                    val broke = (outcome as? AgentOutcome.Done)?.outcomes?.takeIf { protocol >= Catalogue.V2 && mayRecover && !planOnly }
                        ?.singleOrNull()?.takeIf { !it.result.ok }
                    if (broke == null) outcome
                    else finish(asked(AgentTurns.failed(query, broke.step, broke.result.say), context, grounding), query, context, grounding, planOnly, mayRecover = false)
                        .let { second -> if (second is AgentOutcome.Failed) outcome else second }
                }
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
        runCatching { planner.plan(PlanRequest(query, context, tools, grounding, protocol)) }
            .also { planner.lastReply?.let { reply -> replies += reply } }
            // A turn that was dropped for a new one is not a planner that could not be reached: it ends, and says nothing.
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrElse { ParseResult.Failed("I couldn't reach the planner: ${it.message ?: "unknown error"}") }

    private suspend fun proceed(planned: Plan.Steps, query: String, grounding: String, planOnly: Boolean, mayAskAgain: Boolean = true,
        recognised: Boolean = false): AgentOutcome {
        val said = query + "\n" + grounding
        // Each step put right from the person's own words (Tool.mended), then swapped for the step that was plainly meant where a
        // tool says so (Tool.instead). The rules are the tools' own: the loop knows none of them.
        val plan = executor.instead(Plan.Steps(planned.steps.map { Step(it.tool, it.tool.mended(it.params, said)) }), query, said, tools)
        this.plan = plan
        // Nothing the person did not give (an address, a number, a time) is acted on: the plan becomes a question instead.
        executor.missing(plan, said)?.let { (index, missing) ->
            onState(AgentState.Asking(missing.question))
            return AgentOutcome.Asks(missing.question, missing.param?.takeIf { mayAskAgain }?.let { Fix(plan, index, it) })
        }
        if (planOnly) return planned(plan)
        return runSteps(executor.bind(plan), unsure = !recognised && RequestKinds.mismatch(query, plan))
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
