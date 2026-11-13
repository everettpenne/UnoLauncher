package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

internal object TimerTool : Tool(
    name = "timer",
    summary = "Starts a countdown timer that the island shows.",
    params = listOf(Param("seconds", Kind.Int, min = 1, max = 86_400), Param("label", Kind.Text, required = false, maxLength = 60)),
    effect = Effect.Local,
) {
    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val seconds = params.int("seconds") ?: return ToolResult(false, "I didn't catch how long the timer should be.")
        env.startTimer(seconds.toLong())
        val label = params.text("label")?.let { " ($it)" }.orEmpty()
        return ToolResult(true, "Timer set for ${Speak.duration(seconds)}$label",
            undo = { env.cancelTimer(); ToolResult(true, "Timer cancelled") })
    }
}
