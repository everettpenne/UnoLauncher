package com.jake.duolauncher.island

import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.*
import com.jake.duolauncher.SettingsGroup
import com.jake.duolauncher.DropdownRow
import com.jake.duolauncher.appearance.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The island's look, in the Dynamic island settings page, laid out as the rest of Settings: grouped rows, a menu for each choice of several,
 * a switch for each on and off, and the explanations in the groups' footers. A change replays the live preview at the top of the page, on the
 * open island when it is something only the open island shows.
 */
@Composable
internal fun IslandStyleSection(controller: IslandThemeController?) {
    val theme = IslandThemes.active
    var notice by remember { mutableStateOf<String?>(null) }
    val message = notice ?: controller?.message
    val ink = MaterialTheme.colorScheme.onSurface

    val preview = LocalIslandPreview.current
    fun change(candidate: IslandTheme) {
        val renamed = if (candidate.name == theme.name && IslandThemes.presets.any { it.name == theme.name }) candidate.copy(name = "Custom", author = "") else candidate
        val problem = IslandThemeMath.problem(renamed)
        if (problem != null) notice = problem else {
            notice = null; controller?.clearMessage(); IslandThemes.apply(renamed)
            // a new panel or button shape, or a headline change, is only seen on the open island; everything else shows in whatever is playing
            if (renamed.panel != theme.panel || renamed.controls != theme.controls || renamed.face != theme.face || renamed.heroTint != theme.heroTint)
                preview?.show(PreviewScene.OPEN) else preview?.replay()
        }
    }

    Column(Modifier.fillMaxWidth().testTag("island-style"), verticalArrangement = Arrangement.spacedBy(Space.S.dp)) {
        IslandThemePreview(theme)
        // The theme in use: one of the built-in ones, or "Custom" (or an imported theme's own name) once anything is changed by hand.
        val preset = IslandThemes.presets.firstOrNull { it.name == theme.name }
        SettingsGroup(footer = if (preset == null && theme.author.isNotEmpty()) "${theme.name} by ${theme.author}." else null) {
            row { DropdownRow("Theme", IslandThemes.presets, preset, { it.name }, "island-preset", value = preset?.name ?: theme.name,
                optionTag = { "island-preset-${it.name}" }) { IslandThemes.apply(it); notice = null; controller?.clearMessage(); preview?.replay() } }
        }

        IslandHeadlineGroup(theme, { change(it) }, onChanged = { preview?.show(PreviewScene.OPEN) })

        SettingsGroup(header = "Shape & Motion", footer = "Shapes are rounded rectangles because the glass lens cannot bend around other outlines.") {
            row { DropdownRow("Panel", PanelShape.values().toList(), theme.panel, { it.label }, "island-panel", optionTag = { "island-panel-${it.name}" }) { change(theme.copy(panel = it)) } }
            row { DropdownRow("Buttons", ControlShape.values().toList(), theme.controls, { it.label }, "island-controls", optionTag = { "island-controls-${it.name}" }) { change(theme.copy(controls = it)) } }
            row { DropdownRow("App Icons", IconShape.values().toList(), theme.iconShape, { it.label }, "island-iconshape", optionTag = { "island-iconshape-${it.name}" }) { change(theme.copy(iconShape = it)) } }
            row { DropdownRow("Motion", IslandMotion.values().toList(), theme.motion, { it.label }, "island-motion", optionTag = { "island-motion-${it.name}" }) { change(theme.copy(motion = it)) } }
        }
        SettingsGroup(footer = "Playful adds shapes that morph, digits that roll, wavy progress and little bursts. Off keeps the cards still.") {
            row { com.jake.duolauncher.SettingsSwitch("Playful", theme.playful, { change(theme.copy(playful = it)) }, "island-playful") }
        }

        SettingsGroup(header = "Colours", footer = "The body stays dark so the camera hole is hidden, and every colour must stay readable on it. Camera, microphone, recording, VPN and USB marks and the call buttons keep their own colours so they are always recognisable.") {
            row { SwatchRow("Body", theme.fill, FILLS, "fill") { change(theme.copy(fill = it)) } }
            row { SwatchRow("Text", theme.ink, INKS, "ink") { change(theme.copy(ink = it)) } }
            row { SwatchRow("Playback", theme.playback, ACCENTS, "playback") { change(theme.copy(playback = it)) } }
            row { SwatchRow("Charging", theme.charging, ACCENTS, "charging") { change(theme.copy(charging = it)) } }
            row { SwatchRow("Timer and Silent", theme.timer, ACCENTS, "timer") { change(theme.copy(timer = it)) } }
            row { SwatchRow("Focus", theme.focus, ACCENTS, "focus") { change(theme.copy(focus = it)) } }
            row { SwatchRow("Highlight", theme.highlight, ACCENTS, "highlight") { change(theme.copy(highlight = it)) } }
        }
        SettingsGroup(footer = when {
            theme.appOverrides.isNotEmpty() -> "${theme.appOverrides.size} app colour${if (theme.appOverrides.size == 1) "" else "s"} set by this theme."
            else -> "Off, every app wears the highlight. Colours for single apps can be set in a theme file (see the island themes guide)."
        }) {
            row { com.jake.duolauncher.SettingsSwitch("Each App's Own Colour", theme.appColors, { change(theme.copy(appColors = it)) }, "island-appcolors") }
        }
        SettingsGroup(header = "Progress") {
            row { SwatchRow("While Filling", theme.progressActive ?: theme.highlight, ACCENTS, "progress") { change(theme.copy(progressActive = it)) } }
            row { SwatchRow("When Full", theme.progressFinished ?: theme.highlight, ACCENTS, "finished") { change(theme.copy(progressFinished = it)) } }
            row { com.jake.duolauncher.SettingsSwitch("Show Percentage", theme.showPercentage, { change(theme.copy(showPercentage = it)) }, "island-percent") }
        }
        SettingsGroup(header = "Navigation Arrow") {
            row { com.jake.duolauncher.SegmentRow(listOf(false, true), theme.swapNavigation, { if (it) "Right" else "Left" }, { "island-navswap-$it" }) { change(theme.copy(swapNavigation = it)) } }
        }

        SettingsGroup(header = "Share", footer = "Themes are small text files. Only install ones from people you trust; a theme can change how the island looks and moves, never what it does or what it can see.") {
            row { com.jake.duolauncher.ActionRow("Import a Theme", "island-theme-import", enabled = controller != null) { controller?.importTheme() } }
            row { com.jake.duolauncher.ActionRow("Export This Theme", "island-theme-export", enabled = controller != null) { controller?.exportTheme() } }
            row { com.jake.duolauncher.ActionRow(stringResource(com.jake.duolauncher.R.string.island_theme_copy), "island-theme-copy", enabled = controller != null) { controller?.copyTheme() } }
            row { com.jake.duolauncher.ActionRow(stringResource(com.jake.duolauncher.R.string.island_theme_paste), "island-theme-paste", enabled = controller != null) { controller?.pasteTheme() } }
            row { com.jake.duolauncher.ActionRow("Reset to Classic", "island-theme-reset", destructive = true) { IslandThemes.reset(); notice = null; controller?.clearMessage() } }
        }
        if (message != null) Text(message, Modifier.padding(horizontal = Space.L.dp).testTag("island-theme-message"), color = ink, style = UnoType.footnote.tight)
    }

    val pending = controller?.preview
    if (pending != null) GlassDialog(onDismissRequest = { controller.cancelPreview() }, title = "Install this theme?", actions = {
        GlassButton("Cancel", { controller.cancelPreview() }, tag = "island-theme-cancel")
        GlassButton("Apply", { controller.applyPreview() }, prominent = true, tag = "island-theme-apply")
    }) {
        Text(pending.name, style = UnoType.headline.tight)
        if (pending.author.isNotEmpty()) GlassNote("by ${pending.author}")
        IslandThemePreview(pending)
        GlassNote("Panel: ${pending.panel.label}. Buttons: ${pending.controls.label}. Motion: ${pending.motion.label}.")
    }
}

/** A small island drawn in [theme]: the body, text, the four accents and a button, so a choice can be seen before it is used. */
@Composable
private fun IslandThemePreview(theme: IslandTheme) {
    val bodyShape = com.jake.duolauncher.appearance.ContinuousShape((22f * theme.panel.scale).dp)
    Box(Modifier.fillMaxWidth().clip(Corner.medium).background(Color(0xFF5B7285)).padding(vertical = 14.dp).testTag("island-theme-preview"),
        contentAlignment = Alignment.Center) {
        Row(Modifier.clip(bodyShape).background(theme.fill).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(com.jake.duolauncher.R.string.n_12_41), color = theme.ink, style = UnoType.subheadline.tight, fontWeight = FontWeight.SemiBold)
            listOf(theme.playback, theme.charging, theme.timer, theme.focus).forEach { c ->
                Box(Modifier.size(12.dp).clip(CircleShape).background(c))
            }
            Box(Modifier.height(26.dp).clip(theme.controlShape()).background(theme.chip()).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(com.jake.duolauncher.R.string.open), color = theme.ink, style = UnoType.caption1.tight)
            }
        }
    }
}

@Composable
private fun SwatchRow(label: String, selected: Color, palette: List<Color>, key: String, onPick: (Color) -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = ink)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            palette.forEach { c ->
                val on = c == selected
                Box(Modifier.size(36.dp).padding(2.dp).clip(CircleShape).background(c)
                    .border(BorderStroke(if (on) 3.dp else 1.dp, if (on) ink else ink.copy(alpha = .25f)), CircleShape)
                    .clickable { onPick(c) }
                    .semantics { contentDescription = "$label ${"#%06X".format(c.value.shr(32).toInt() and 0xFFFFFF)}" }
                    .testTag("island-swatch-$key-${"%06X".format(c.value.shr(32).toInt() and 0xFFFFFF)}"))
            }
        }
    }
}

private val FILLS = listOf(0xFF000000, 0xFF0A0A0C, 0xFF0F0F10, 0xFF05071A, 0xFF0B0805, 0xFF06120C, 0xFF140812).map { Color(it) }
private val INKS = listOf(0xFFFFFFFF, 0xFFF2F2F2, 0xFFE9EDFF, 0xFFFFF3DC, 0xFFDFF7E8, 0xFFFFE3F0).map { Color(it) }
private val ACCENTS = listOf(0xFFFF375F, 0xFFFF9F0A, 0xFFFFD60A, 0xFF34C759, 0xFF30D158, 0xFF00E5FF, 0xFF5E8BFF, 0xFF7D7AFF, 0xFFB388FF, 0xFFFF2BD6, 0xFFE0E0E0).map { Color(it) }
