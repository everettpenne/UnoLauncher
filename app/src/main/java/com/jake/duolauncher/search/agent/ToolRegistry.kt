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
        CalendarTool,
        WebSearchTool,
        SettingsPanelTool,
        ReplyTool,
        TimerCancelTool,
        MediaTool,
        VolumeTool,
        ChecklistTool,
        DirectionsTool,
    )

    /**
     * The list from protocol 2 on (the 4B planner): the first nineteen, the three the launcher used to recognise itself (a reminder,
     * Uno's Style, Uno's settings), and the everyday things that were missing. [builtIn] is left exactly as the first planners were
     * trained on it; which list a phone uses follows the planner it has (AgentRouting.protocol).
     */
    val next: List<Tool> = builtIn.map { if (it === SettingsPanelTool) SettingsPagesTool else it } + listOf(
        ReminderTool, CameraTool, EmailTool, RingerTool, PlayTool, BrightnessTool, DeviceTool, ContactAddTool, AppInfoTool,
        AlarmsShowTool, CalendarOpenTool, StyleTool, UnoSettingsTool,
    )

    fun forProtocol(protocol: Int): List<Tool> = if (protocol >= Catalogue.V2) next else builtIn

    /** The tools whose capabilities are present right now: a tool that needs contacts is not offered without them. */
    fun available(capabilities: Set<Capability>, tools: List<Tool> = builtIn): List<Tool> =
        tools.filter { tool -> tool.needs.all { it in capabilities } }

    fun byName(name: String): Tool? = builtIn.firstOrNull { it.name == name }
}
