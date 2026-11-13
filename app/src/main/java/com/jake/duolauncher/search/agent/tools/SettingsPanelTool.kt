package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

/**
 * The switches an ordinary app cannot flip: Wi-Fi, Bluetooth, NFC and airplane mode are the system's, so the honest action is to open the
 * page that has the switch rather than to refuse the request.
 */
internal object SettingsPanelTool : Tool(
    name = "settings_panel",
    summary = "Opens an Android settings page: wifi, bluetooth, nfc, airplane, display, sound, battery.",
    params = listOf(Param("panel", Kind.Enum, choices = listOf("wifi", "bluetooth", "nfc", "airplane", "display", "sound", "battery"))),
    effect = Effect.Local,
    examples = listOf("wifi settings", "turn bluetooth off", "show battery settings"),
) {
    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val panel = params.text("panel") ?: return ToolResult(false, "I didn't catch which setting.")
        env.openSettingsPanel(panel)
        return ToolResult(true, "${panel.replaceFirstChar { it.uppercase() }} settings open — switch it there")
    }
}
