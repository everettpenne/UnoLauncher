package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

internal object StopwatchTool : Tool(
    name = "stopwatch",
    summary = "Starts or stops the island's stopwatch.",
    effect = Effect.Local,
) {
    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val running = env.toggleStopwatch()
        return ToolResult(true, if (running) "Stopwatch running" else "Stopwatch stopped",
            undo = { env.toggleStopwatch(); ToolResult(true, "Stopwatch toggled back") })
    }
}
