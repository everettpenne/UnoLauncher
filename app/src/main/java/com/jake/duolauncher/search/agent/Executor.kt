package com.jake.duolauncher.search.agent

/**
 * Runs a plan, in order, with a step cap so a wrong plan cannot run away: at most [MAX_STEPS] steps, and at most [MAX_PER_TOOL] of any one
 * tool. Collects what each step said and keeps the undo functions for the summary's Undo.
 */
internal class Executor(
    private val env: ToolEnv,
    private val settings: AgentSettings = AgentSettings(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    data class Outcome(val step: Step, val result: ToolResult, val ms: Long)

    private val undoStack = ArrayDeque<suspend () -> ToolResult>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()

    /** Runs [plan], calling [onStep] before each step so the UI can say what is happening. Stops at the first failure. */
    suspend fun run(plan: Plan.Steps, onStep: suspend (Step) -> Unit = {}): List<Outcome> {
        val outcomes = ArrayList<Outcome>(plan.steps.size)
        val perTool = HashMap<String, Int>()
        for (step in plan.steps.take(MAX_STEPS)) {
            val seen = (perTool[step.tool.name] ?: 0) + 1
            if (seen > MAX_PER_TOOL) {
                outcomes += Outcome(step, ToolResult(false, "I stopped: that plan used ${step.tool.name} too many times."), 0)
                break
            }
            perTool[step.tool.name] = seen
            onStep(step)
            val started = clock()
            val result = runCatching { step.tool.execute(step.params, env) }
                .getOrElse { ToolResult(false, "That didn't work: ${it.message ?: "unknown error"}") }
            val ms = clock() - started
            outcomes += Outcome(step, result, ms)
            Trace.record(TraceEntry(started, step.tool.name, step.params.values, result.ok, result.say, ms))
            result.undo?.let { undoStack.addLast(it) }
            if (!result.ok) break
        }
        return outcomes
    }

    /** Undoes the most recent undoable step. Returns what it did, or null when there is nothing to undo. */
    suspend fun undoLast(): ToolResult? {
        val undo = undoStack.removeLastOrNull() ?: return null
        return runCatching { undo() }.getOrElse { ToolResult(false, "I couldn't undo that.") }
    }

    /** What the plan would run, without running it: the dry run the tests and the "what would you do?" mode use. A tool's own
     *  wording where it has one; otherwise the tool and the values the plan gave it (not what [bind] settled, which is not the plan's). */
    fun describe(plan: Plan.Steps): List<String> = plan.steps.take(MAX_STEPS).map { step ->
        step.tool.describe(step.params, env) ?: run {
            val params = step.params.values.filter { (k, v) -> v.isNotBlank() && !k.startsWith("_") }.entries.joinToString(", ") { "${it.key}=${it.value}" }
            step.tool.name + if (params.isEmpty()) "" else " ($params)"
        }
    }

    /** The plan with each step's target settled (Tool.bind), once: what is then described, confirmed and run is the same thing. */
    fun bind(plan: Plan.Steps): Plan.Steps = Plan.Steps(plan.steps.map { Step(it.tool, it.tool.bind(it.params, env)) })

    /** The first reason a step cannot be done as asked (Tool.problem), or null. */
    fun problem(plan: Plan.Steps): String? = plan.steps.take(MAX_STEPS).firstNotNullOfOrNull { it.tool.problem(it.params, env) }

    companion object {
        const val MAX_STEPS = 3
        const val MAX_PER_TOOL = 2
    }
}
