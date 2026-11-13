package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

internal object TimerTool : Tool(
    name = "timer",
    summary = "Starts a countdown timer that the island shows.",
    params = listOf(Param("seconds", Kind.Int, min = 1, max = 86_400), Param("label", Kind.Text, required = false, maxLength = 60)),
    effect = Effect.Local,
    examples = listOf("count down 15 minutes", "egg timer 7 minutes", "set a timer for an hour and a half"),
) {
    private val timeLike = Regex("""\d|\b(am|pm|tomorrow|today|tonight|morning|evening|minutes?|hours?|seconds?|timer|reminder)\b""", RegexOption.IGNORE_CASE)

    override fun describe(params: Params, env: ToolEnv): String? =
        params.int("seconds")?.let { "Start a timer for ${Speak.duration(it)}" }

    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val seconds = params.int("seconds") ?: return ToolResult(false, "I didn't catch how long the timer should be.")
        env.startTimer(seconds.toLong())
        // A label that only says a time again ("6am tomorrow", "30 minutes") is not a name for the timer, and reads as if it were one.
        val label = params.text("label")?.takeUnless { timeLike.containsMatchIn(it) }?.let { " ($it)" }.orEmpty()
        return ToolResult(true, "Timer set for ${Speak.duration(seconds)}$label",
            undo = { env.cancelTimer(); ToolResult(true, "Timer cancelled") })
    }
}
