package com.jake.duolauncher.appearance

import com.jake.duolauncher.*
import com.jake.duolauncher.panel.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import androidx.compose.ui.util.lerp

/*
 * Uno's glass UI kit: the building blocks for any menu, dialog or settings panel that is not a launcher surface of its
 * own. A new menu is built from these and nothing else, so that it looks and behaves like the rest of Home and moves
 * with the glass settings. Do not reach for Material's AlertDialog, Switch, FilterChip, Surface or OutlinedButton in
 * a menu: they are opaque, tinted by Material's palette, and read as a different app. The rules are in docs/design.md
 * ("Building menus").
 *
 *   GlassDialog     a centred glass card over a dim scrim, for a widget's menu or a short confirmation
 *   GlassButton     a pill action (plain, or prominent in the wallpaper accent)
 *   GlassChips      a row of mutually exclusive pill choices (the glass counterpart of a segmented control)
 *   GlassSwitchRow  a label, optional help line and the liquid switch
 *   GlassHeading    a section title;   GlassDivider  a hairline between sections
 *   GlassRow        a tappable row with a title, a subtitle and a trailing slot
 *
 * Inside a GlassDialog (or a GlassModalSheet) the liquid controls refract the panel they sit on. With liquid glass
 * off, GlassDialog falls back to a stock dialog of the same content, and every control here stays legible on it.
 */

private val GlassInkOnPanel: Color @Composable get() = MaterialTheme.colorScheme.onSurface

/** The wallpaper accent, or iOS orange when there is none, and the ink that reads on it. */
@Composable private fun accentPair(): Pair<Color, Color> {
    val a = LocalWallpaperAccent.current
    return (a?.accent ?: IosOrange) to (a?.ink ?: Color.Black)
}

/** A glass dialog: a card over a dimmed Home, with [title], scrolling [content] and [actions] (usually one or two
 *  [GlassButton]s) along the bottom. Tapping outside dismisses it. */
@Composable
internal fun GlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalPageGlass.current
    if (glass == null) {
        AlertDialog(onDismissRequest = onDismissRequest, modifier = modifier, title = { Text(title) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) },
            confirmButton = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions) })
        return
    }
    val shape = Corner.xlarge
    val panelBackdrop = rememberLayerBackdrop()
    val accent = LocalWallpaperAccent.current
    val surface = MaterialTheme.colorScheme.surface.let { base ->
        if (accent != null) androidx.compose.ui.graphics.lerp(base, accent.glass, .5f) else base
    }.copy(alpha = .62f)
    val panelGlass = PageGlass(panelBackdrop, surface, glass.settings)
    Dialog(onDismissRequest, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismissRequest)
            .safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(modifier.widthIn(max = 420.dp).fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures { } }   // taps inside the card are not "outside"
                .drawBackdrop(
                    backdrop = glass.backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(lerp(8f, 44f, glass.settings.height).dp.toPx(), lerp(16f, 132f, glass.settings.amount).dp.toPx(),
                            depthEffect = true, chromaticAberration = glass.settings.chromatic > 0.05f)
                    },
                    highlight = { GlassRim.Light },
                    exportedBackdrop = panelBackdrop,
                    onDrawSurface = { drawRect(surface) })
                .padding(20.dp)) {
                CompositionLocalProvider(LocalPageGlass provides panelGlass, LocalContentColor provides GlassInkOnPanel) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = GlassInkOnPanel)
                    Column(Modifier.weight(1f, fill = false).padding(top = 12.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically, content = actions)
                }
            }
        }
    }
}

/** A pill action. [prominent] fills it with the wallpaper accent (use for the one primary action of a menu). */
@Composable
internal fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, prominent: Boolean = false,
    enabled: Boolean = true, tag: String? = null) {
    val (accent, accentInk) = accentPair()
    val ink = GlassInkOnPanel
    val shape = RoundedCornerShape(percent = 50)
    val fill = if (prominent) accent.copy(alpha = if (enabled) .92f else .35f) else ink.copy(alpha = .10f)
    Box(modifier.heightIn(min = 48.dp).clip(shape).background(fill)
        .border(BorderStroke(1.dp, if (prominent) Color.White.copy(alpha = .25f) else ink.copy(alpha = .16f)), shape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center) {
        Text(text, color = (if (prominent) accentInk else ink).copy(alpha = if (enabled) 1f else .45f), fontSize = 14.sp,
            fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** A row of mutually exclusive pill choices; the selected one is filled with the wallpaper accent. Scrolls sideways
 *  when the choices do not fit. [tagPrefix] gives each a test tag, `<prefix>-<key>`. */
@Composable
internal fun <T> GlassChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit,
    modifier: Modifier = Modifier, tagPrefix: String? = null, key: (T) -> String = { label(it) }) {
    val (accent, accentInk) = accentPair()
    val ink = GlassInkOnPanel
    val shape = RoundedCornerShape(percent = 50)
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val on = option == selected
            Box(Modifier.heightIn(min = 40.dp).clip(shape)
                .background(if (on) accent.copy(alpha = .9f) else ink.copy(alpha = .10f))
                .border(BorderStroke(1.dp, if (on) Color.White.copy(alpha = .25f) else ink.copy(alpha = .16f)), shape)
                .clickable(role = Role.RadioButton) { onSelect(option) }
                .then(if (tagPrefix != null) Modifier.testTag("$tagPrefix-${key(option)}") else Modifier)
                .padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(label(option), color = if (on) accentInk else ink, fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}

/** A label with an optional help line and the liquid switch at the end. */
@Composable
internal fun GlassSwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit, modifier: Modifier = Modifier,
    help: String? = null, tag: String? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, color = GlassInkOnPanel, fontSize = 15.sp)
            if (help != null) Text(help, color = GlassInkOnPanel.copy(alpha = .65f), fontSize = 12.sp)
        }
        LiquidSwitchControl(checked, onChecked, LocalPageGlass.current,
            if (tag != null) Modifier.testTag(tag) else Modifier)
    }
}

/** A tappable row: title, optional subtitle, and a trailing slot (a check mark, a chevron, a value). */
@Composable
internal fun GlassRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).clip(Corner.medium).clickable(role = Role.Button, onClick = onClick)
        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) { leading(); Spacer(Modifier.width(10.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = GlassInkOnPanel, fontSize = 15.sp, maxLines = 1)
            if (subtitle != null) Text(subtitle, color = GlassInkOnPanel.copy(alpha = .65f), fontSize = 12.sp, maxLines = 2)
        }
        if (trailing != null) trailing()
    }
}

/** A section title inside a menu. */
@Composable
internal fun GlassHeading(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = GlassInkOnPanel.copy(alpha = .72f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = .6.sp)
}

/** A hairline between sections. */
@Composable
internal fun GlassDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(GlassInkOnPanel.copy(alpha = .12f)))
}

/** A small explanatory line under a control. */
@Composable
internal fun GlassNote(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = GlassInkOnPanel.copy(alpha = .62f), fontSize = 12.sp)
}

/** A text field in the glass style: a soft translucent well with a hairline rim, [placeholder] shown while empty. Put
 *  [tag] on it for tests; it is the editable node itself. */
@Composable
internal fun GlassTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    placeholder: String = "", singleLine: Boolean = false, minHeight: androidx.compose.ui.unit.Dp = 48.dp,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE, tag: String? = null) {
    val ink = GlassInkOnPanel
    val shape = Corner.medium
    androidx.compose.foundation.text.BasicTextField(value, onValueChange,
        modifier.fillMaxWidth().heightIn(min = minHeight).then(if (tag != null) Modifier.testTag(tag) else Modifier),
        singleLine = singleLine, maxLines = maxLines,
        textStyle = androidx.compose.ui.text.TextStyle(color = ink, fontSize = 15.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(ink),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().heightIn(min = minHeight).clip(shape).background(ink.copy(alpha = .08f))
                .border(BorderStroke(1.dp, ink.copy(alpha = .16f)), shape).padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart) {
                if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, color = ink.copy(alpha = .45f), fontSize = 15.sp)
                inner()
            }
        })
}
