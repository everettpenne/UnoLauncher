package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

internal object TorchTool : Tool(
    name = "torch",
    summary = "Turns the flashlight on or off.",
    effect = Effect.Local,
    examples = listOf("flashlight", "switch the torch on"),
) {
    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val on = env.toggleTorch()
        return ToolResult(true, if (on) "Torch on" else "Torch off",
            undo = { env.toggleTorch(); ToolResult(true, "Torch toggled back") })
    }
}
