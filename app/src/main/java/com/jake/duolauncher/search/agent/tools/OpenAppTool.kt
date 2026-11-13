package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

internal object OpenAppTool : Tool(
    name = "open_app",
    summary = "Opens an installed app by its name.",
    params = listOf(Param("app", Kind.Text, maxLength = 60)),
    effect = Effect.Local,
    examples = listOf("launch maps", "open the calculator"),
) {
    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val wanted = params.text("app") ?: return ToolResult(false, "I didn't catch which app to open.")
        val byName = env.apps
        if (byName.isEmpty()) return ToolResult(false, "I can't see your apps from here. Open search once, then ask me again.")
        val match = byName.firstOrNull { it.label.equals(wanted, ignoreCase = true) }
            ?: byName.firstOrNull { it.label.startsWith(wanted, ignoreCase = true) }
            ?: byName.firstOrNull { it.label.contains(wanted, ignoreCase = true) }
            ?: return ToolResult(false, "I couldn't find an app called \"$wanted\".")
        if (!env.openApp(match)) return ToolResult(false, "Android wouldn't open ${match.label}.")
        return ToolResult(true, "Opened ${match.label}")
    }
}
