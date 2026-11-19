package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

internal object StopwatchTool : Tool(
    name = "stopwatch",
    summary = "Starts, stops or resets the island's stopwatch.",
    params = listOf(Param("action", Kind.Enum, choices = listOf("start", "stop", "reset"))),
    effect = Effect.Local,
    examples = listOf("time my run", "stopwatch", "pause the stopwatch", "reset the stopwatch", "how long does this lap take"),
) {
    override fun describe(params: Params, env: ToolEnv): String? = params.text("action")?.let { "${it.replaceFirstChar(Char::uppercase)} the stopwatch" }

    override suspend fun execute(params: Params, env: ToolEnv): ToolResult = when (params.text("action") ?: "start") {
        "stop" ->
            if (!env.stopwatchRunning()) ToolResult(true, "The stopwatch isn't running")
            else { env.toggleStopwatch(); ToolResult(true, "Stopwatch stopped", undo = { env.toggleStopwatch(); ToolResult(true, "Stopwatch running again") }) }
        "reset" ->
            if (!env.stopwatchStarted()) ToolResult(true, "The stopwatch is already at zero")
            else { env.resetStopwatch(); ToolResult(true, "Stopwatch reset") }
        else ->
            if (env.stopwatchRunning()) ToolResult(true, "The stopwatch is already running")
            else { env.toggleStopwatch(); ToolResult(true, "Stopwatch running", undo = { env.toggleStopwatch(); ToolResult(true, "Stopwatch stopped") }) }
    }
}
