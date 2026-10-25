package com.jake.duolauncher

import com.jake.duolauncher.appearance.*
import com.jake.duolauncher.panel.IosOrange

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Launcher settings as iOS Settings lays them out: grouped lists. Each group is an optional small capital heading, an inset card of rows
 * divided by hairlines, and an optional footer that carries the explanation, so row names stay short ("Wallpaper Depth", not a sentence)
 * and the detail is still one glance away. The card's corners are concentric with the sheet's: the sheet is [SHEET_RADIUS] and the cards
 * sit [SHEET_INSET] inside it.
 */

internal val SHEET_RADIUS: Dp = Corner.SHEET_DP.dp
internal val SHEET_INSET: Dp = 16.dp
/** The settings card: concentric with the sheet it sits in (42 − 16 = 26 dp, the Glass kit's grouped card and iOS 26's). */
internal val SettingsCardShape = Corner.inside(SHEET_RADIUS, SHEET_INSET)

/** The rows of one group, collected in order (an `if` around `row { }` leaves a row out). */
internal class SettingsRows {
    internal val rows = mutableListOf<@Composable () -> Unit>()
    /** One row of the card. Its content gets the card's 16 dp side margins; the hairline above it is drawn for it. */
    fun row(content: @Composable () -> Unit) { rows += content }
}

/**
 * One group of settings: [header] above the card (shown in small capitals), the rows [build] adds, and [footer] below it. A group with no
 * rows shows just its header and footer, which suits a short explanation that belongs to no single switch.
 */
@Composable
internal fun SettingsGroup(modifier: Modifier = Modifier, header: String? = null, footer: String? = null, tag: String? = null,
    build: SettingsRows.() -> Unit) {
    val rows = SettingsRows().apply(build).rows
    val ink = MaterialTheme.colorScheme.onSurface
    Column(modifier.fillMaxWidth().padding(top = Space.S.dp).then(if (tag != null) Modifier.testTag(tag) else Modifier)) {
        if (header != null) Text(header.uppercase(), Modifier.padding(start = Space.L.dp, end = Space.L.dp, bottom = 6.dp),
            style = UnoType.caption1.copy(fontWeight = FontWeight.Normal), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (rows.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(SettingsCardShape).background(ink.copy(alpha = .06f))) {
            rows.forEachIndexed { i, content ->
                if (i > 0) Box(Modifier.padding(start = Space.L.dp).fillMaxWidth().height(.5.dp).background(ink.copy(alpha = .14f)))
                Box(Modifier.fillMaxWidth().padding(horizontal = Space.L.dp), contentAlignment = Alignment.CenterStart) { content() }
            }
        }
        if (footer != null) Text(footer, Modifier.padding(start = Space.L.dp, end = Space.L.dp, top = 6.dp),
            style = UnoType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The launcher's one accent: Uno orange, or the wallpaper's colour when Appearance › Accent says so. */
internal val SettingsAccent: Color @Composable get() = LocalWallpaperAccent.current?.accent ?: IosOrange

/** One of several choices, iOS's way: the name, and a check mark at the end of the chosen one. */
@Composable
internal fun CheckRow(title: String, selected: Boolean, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick).testTag(tag),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .45f))
        if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = SettingsAccent)
    }
}

/** An on/off item in a list of several (an app store, a layout): the name, and a check mark when it is on. */
@Composable
internal fun ToggleCheckRow(title: String, checked: Boolean, tag: String, enabled: Boolean = true, detail: String? = null,
    leading: (@Composable () -> Unit)? = null, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(value = checked, enabled = enabled, role = Role.Checkbox) { onToggle() }.testTag(tag),
        verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) { leading(); Spacer(Modifier.width(Space.M.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .45f))
            if (detail != null) Text(detail, style = UnoType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (checked) Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = SettingsAccent)
    }
}

/** A row that opens another page: an optional icon tile, the name, an optional current value, and a chevron. */
@Composable
internal fun NavRow(title: String, tag: String, value: String? = null, icon: ImageVector? = null, iconTint: Color? = null,
    onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).testTag(tag),
        verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { IconTile(icon, iconTint ?: SettingsAccent); Spacer(Modifier.width(Space.M.dp)) }
        Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (value != null) Text(value, Modifier.padding(start = Space.S.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f))
    }
}

/** A button as a row, iOS's way: the action's name in the accent (or in red, for one that removes something). */
@Composable
internal fun ActionRow(title: String, tag: String, enabled: Boolean = true, destructive: Boolean = false, onClick: () -> Unit) {
    val colour = if (destructive) Color(0xFFFF3B30) else SettingsAccent
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick).testTag(tag),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = colour.copy(alpha = if (enabled) 1f else .4f), maxLines = 1)
    }
}

/** A name and a value that is only shown, not changed here. */
@Composable
internal fun ValueRow(title: String, value: String, tag: String? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) {}
        .then(if (tag != null) Modifier.testTag(tag) else Modifier), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A row of mutually exclusive choices as glass chips, for two to four short options (Glass: Clear, Frosted, Off). */
@Composable
internal fun <T> SegmentRow(options: List<T>, selected: T, label: (T) -> String, tag: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.S.dp), horizontalArrangement = Arrangement.spacedBy(Space.S.dp)) {
        options.forEach { option ->
            GlassChip(option == selected, { onSelect(option) }, { Text(label(option), maxLines = 1) }, Modifier.weight(1f).testTag(tag(option)))
        }
    }
}

/** iOS Settings' coloured square behind a white glyph. Its corners are concentric with nothing, so it takes the small icon radius. */
@Composable
internal fun IconTile(icon: ImageVector, colour: Color) {
    Box(Modifier.size(30.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(7.dp)).background(colour), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(19.dp), tint = Color.White)
    }
}
