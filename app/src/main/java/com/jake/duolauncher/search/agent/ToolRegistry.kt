package com.jake.duolauncher.search.agent

import com.jake.duolauncher.search.agent.tools.*

/**
 * Every capability, in one list. A new tool is one file and one line here; the prompt, the grammar and the docs are generated from it, so
 * nothing else needs editing.
 */
internal object ToolRegistry {
    val builtIn: List<Tool> = listOf(
        TimerTool,
        StopwatchTool,
        TorchTool,
        FocusTool,
        NoteTool,
        AlarmTool,
        OpenAppTool,
        DialTool,
        MessageTool,
        OpenUrlTool,
    )

    /** The tools whose capabilities are present right now: a tool that needs contacts is not offered without them. */
    fun available(capabilities: Set<Capability>, tools: List<Tool> = builtIn): List<Tool> =
        tools.filter { tool -> tool.needs.all { it in capabilities } }

    fun byName(name: String): Tool? = builtIn.firstOrNull { it.name == name }
}
