package com.jake.duolauncher.island

import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.*
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

/** The island's look, in the Dynamic island settings page: presets, shapes, motion, colours, and theme files. */
@Composable
internal fun IslandStyleSection(controller: IslandThemeController?) {
    val theme = IslandThemes.active
    var notice by remember { mutableStateOf<String?>(null) }
    val message = notice ?: controller?.message
    val ink = MaterialTheme.colorScheme.onSurface

    // The live preview at the top of the island page replays its scene with the new look, so a colour or a motion is seen, not imagined.
    val preview = LocalIslandPreview.current
    fun change(candidate: IslandTheme) {
        val renamed = if (candidate.name == theme.name && IslandThemes.presets.any { it.name == theme.name }) candidate.copy(name = "Custom", author = "") else candidate
        val problem = IslandThemeMath.problem(renamed)
        if (problem != null) notice = problem else {
            notice = null; controller?.clearMessage(); IslandThemes.apply(renamed)
            // a new panel or button shape is only seen on the open island; everything else shows in whatever is playing
            if (renamed.panel != theme.panel || renamed.controls != theme.controls) preview?.show(PreviewScene.OPEN) else preview?.replay()
        }
    }

    Column(Modifier.fillMaxWidth().testTag("island-style"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassDivider()
        GlassHeading("ISLAND STYLE")
        IslandThemePreview(theme)
        GlassChips(IslandThemes.presets, IslandThemes.presets.firstOrNull { it.name == theme.name } ?: IslandThemes.presets[0],
            { it.name }, { IslandThemes.apply(it); notice = null; controller?.clearMessage(); preview?.replay() }, tagPrefix = "island-preset")
        if (IslandThemes.presets.none { it.name == theme.name }) GlassNote("Using ${theme.name}${if (theme.author.isNotEmpty()) " by ${theme.author}" else ""}.")

        GlassHeading("PANEL SHAPE")
        GlassChips(PanelShape.values().toList(), theme.panel, { it.label }, { change(theme.copy(panel = it)) }, tagPrefix = "island-panel", key = { it.name })
        GlassHeading("BUTTONS")
        GlassChips(ControlShape.values().toList(), theme.controls, { it.label }, { change(theme.copy(controls = it)) }, tagPrefix = "island-controls", key = { it.name })
        GlassHeading("MOTION")
        GlassChips(IslandMotion.values().toList(), theme.motion, { it.label }, { change(theme.copy(motion = it)) }, tagPrefix = "island-motion", key = { it.name })
        GlassNote("Shapes are rounded rectangles because the glass lens cannot bend around other outlines.")

        GlassHeading("COLOURS")
        SwatchRow("Body", theme.fill, FILLS, "fill") { change(theme.copy(fill = it)) }
        SwatchRow("Text", theme.ink, INKS, "ink") { change(theme.copy(ink = it)) }
        SwatchRow("Playback", theme.playback, ACCENTS, "playback") { change(theme.copy(playback = it)) }
        SwatchRow("Charging", theme.charging, ACCENTS, "charging") { change(theme.copy(charging = it)) }
        SwatchRow("Timer and silent", theme.timer, ACCENTS, "timer") { change(theme.copy(timer = it)) }
        SwatchRow("Focus", theme.focus, ACCENTS, "focus") { change(theme.copy(focus = it)) }
        SwatchRow("Highlight (apps with no colour)", theme.highlight, ACCENTS, "highlight") { change(theme.copy(highlight = it)) }
        GlassHeading("APP COLOURS")
        GlassChips(listOf(true, false), theme.appColors, { if (it) "Each app's own" else "One highlight" }, { change(theme.copy(appColors = it)) },
            tagPrefix = "island-appcolors", key = { it.toString() })
        GlassNote(if (theme.appOverrides.isEmpty()) "Colours for single apps can be set in a theme file (see the island themes guide)."
            else "${theme.appOverrides.size} app colour${if (theme.appOverrides.size == 1) "" else "s"} set by this theme.")
        GlassHeading("APP ICON SHAPE")
        GlassChips(IconShape.values().toList(), theme.iconShape, { it.label }, { change(theme.copy(iconShape = it)) }, tagPrefix = "island-iconshape", key = { it.name })
        GlassHeading("PROGRESS")
        SwatchRow("While filling", theme.progressActive ?: theme.highlight, ACCENTS, "progress") { change(theme.copy(progressActive = it)) }
        SwatchRow("When full", theme.progressFinished ?: theme.highlight, ACCENTS, "finished") { change(theme.copy(progressFinished = it)) }
        GlassChips(listOf(false, true), theme.showPercentage, { if (it) "Show percentage" else "Bar only" }, { change(theme.copy(showPercentage = it)) },
            tagPrefix = "island-percent", key = { it.toString() })
        GlassHeading("NAVIGATION")
        GlassChips(listOf(false, true), theme.swapNavigation, { if (it) "Arrow on the right" else "Arrow on the left" }, { change(theme.copy(swapNavigation = it)) },
            tagPrefix = "island-navswap", key = { it.toString() })
        GlassNote("The body stays dark so the camera hole is hidden, and every colour must stay readable on it. Camera, microphone, recording, VPN and USB marks and the call buttons keep their own colours so they are always recognisable.")

        GlassHeading("SHARE")
        GlassButton("Import a theme", { controller?.importTheme() }, Modifier.fillMaxWidth(), enabled = controller != null, tag = "island-theme-import")
        GlassButton("Export this theme", { controller?.exportTheme() }, Modifier.fillMaxWidth(), enabled = controller != null, tag = "island-theme-export")
        GlassButton(stringResource(com.jake.duolauncher.R.string.island_theme_copy), { controller?.copyTheme() }, Modifier.fillMaxWidth(), enabled = controller != null, tag = "island-theme-copy")
        GlassButton(stringResource(com.jake.duolauncher.R.string.island_theme_paste), { controller?.pasteTheme() }, Modifier.fillMaxWidth(), enabled = controller != null, tag = "island-theme-paste")
        GlassButton("Reset to Classic", { IslandThemes.reset(); notice = null; controller?.clearMessage() }, Modifier.fillMaxWidth(), tag = "island-theme-reset")
        GlassNote("Themes are small text files. Only install ones from people you trust; a theme can change how the island looks and moves, never what it does or what it can see.")
        if (message != null) Text(message, color = ink, style = UnoType.footnote.tight, modifier = Modifier.testTag("island-theme-message"))
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
    val bodyShape = androidx.compose.foundation.shape.RoundedCornerShape((22f * theme.panel.scale).dp)
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = ink, style = UnoType.subheadline.tight)
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
