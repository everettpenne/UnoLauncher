package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

internal object CalendarTool : Tool(
    name = "calendar_event",
    summary = "Opens a calendar event, prefilled: a title, how long from now it starts, and how long it lasts.",
    params = listOf(
        Param("title", Kind.Text, maxLength = 120),
        Param("in_minutes", Kind.Int, min = 0, max = 10_080),
        Param("duration_minutes", Kind.Int, min = 5, max = 1_440),
    ),
    effect = Effect.Local,
) {
    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val title = params.text("title") ?: return ToolResult(false, "I didn't catch what the event is called.")
        val inMinutes = params.int("in_minutes") ?: return ToolResult(false, "I didn't catch when it should start.")
        val duration = params.int("duration_minutes") ?: return ToolResult(false, "I didn't catch how long it should last.")
        env.openCalendar(title, inMinutes, duration)
        val whenText = if (inMinutes == 0) "now" else "in ${Speak.duration(inMinutes * 60)}"
        return ToolResult(true, "Calendar open with \"$title\" $whenText for ${Speak.duration(duration * 60)} — tap Save to add it")
    }
}
