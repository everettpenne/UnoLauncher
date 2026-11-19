package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

internal object TimerCancelTool : Tool(
    name = "timer_cancel",
    summary = "Cancels the running countdown timer.",
    effect = Effect.Local,
    examples = listOf("cancel the timer", "stop the countdown", "I don't need the timer any more"),
) {
    override fun describe(params: Params, env: ToolEnv): String? = "Cancel the timer"

    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        if (!env.timerRunning()) return ToolResult(true, "No timer is running")
        env.cancelTimer()
        return ToolResult(true, "Timer cancelled")
    }
}
