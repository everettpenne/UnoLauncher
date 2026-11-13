package com.jake.duolauncher.search.agent

/** How the person wants the agent to behave. [askAll] is the one switch that makes every action wait for a tap ("Ask before every
 *  action"); [alwaysAsk] names single tools that should, and is empty by default. */
internal class AgentSettings(val alwaysAsk: Set<String> = emptySet(), val askAll: Boolean = false)

/**
 * The whole confirmation policy: only what leaves the phone asks first. Everything local runs, and its summary is the confirmation.
 * One function, so changing the posture is one edit rather than a search through tools.
 */
internal object ToolPolicy {
    fun needsConfirmation(tool: Tool, settings: AgentSettings = AgentSettings()): Boolean =
        tool.effect == Effect.LeavesPhone || settings.askAll || tool.name in settings.alwaysAsk
}
