package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

internal object TorchTool : Tool(
    name = "torch",
    summary = "Turns the flashlight on or off.",
    params = listOf(Param("on", Kind.Bool)),
    effect = Effect.Local,
    examples = listOf("flashlight", "switch the torch on", "torch off"),
) {
    override fun describe(params: Params, env: ToolEnv): String? = params.bool("on")?.let { if (it) "Turn the torch on" else "Turn the torch off" }

    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        // Set, not flipped: "turn off the torch" with the torch already off leaves it off.
        val on = params.bool("on") ?: true
        env.setTorch(on)
        return ToolResult(true, if (on) "Torch on" else "Torch off",
            undo = { env.setTorch(!on); ToolResult(true, if (on) "Torch off" else "Torch on") })
    }
}
