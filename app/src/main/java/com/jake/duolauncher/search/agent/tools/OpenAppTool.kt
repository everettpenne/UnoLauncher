package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

internal object OpenAppTool : Tool(
    name = "open_app",
    summary = "Opens an installed app by its name.",
    params = listOf(Param("app", Kind.Text, maxLength = 60)),
    effect = Effect.Local,
) {
    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val wanted = params.text("app") ?: return ToolResult(false, "I didn't catch which app to open.")
        val byName = env.apps
        val match = byName.firstOrNull { it.label.equals(wanted, ignoreCase = true) }
            ?: byName.firstOrNull { it.label.startsWith(wanted, ignoreCase = true) }
            ?: byName.firstOrNull { it.label.contains(wanted, ignoreCase = true) }
            ?: return ToolResult(false, "I couldn't find an app called \"$wanted\".")
        env.openApp(match)
        return ToolResult(true, "Opened ${match.label}")
    }
}
