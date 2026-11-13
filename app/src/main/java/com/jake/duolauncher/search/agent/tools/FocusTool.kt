package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

internal object FocusTool : Tool(
    name = "focus",
    summary = "Turns Focus (Do Not Disturb) on or off; it hides the apps the person picked.",
    params = listOf(Param("on", Kind.Bool)),
    effect = Effect.Local,
) {
    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val want = params.bool("on") ?: return ToolResult(false, "I didn't catch whether Focus should be on or off.")
        val already = env.focusOn() == want
        if (!already) env.toggleFocus()
        val state = if (want) "on" else "off"
        return ToolResult(true, if (already) "Focus already $state" else "Focus $state",
            undo = if (already) null else { { env.toggleFocus(); ToolResult(true, "Focus ${if (want) "off" else "on"} again") } })
    }
}
