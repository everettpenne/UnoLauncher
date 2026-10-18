package com.jake.duolauncher.appearance

import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
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

/** The ink kit controls draw in. Null means the panel's `onSurface` (settings sheet, dialogs). A surface whose text colour adapts to the
 *  wallpaper (the All apps panel) or is fixed (the Photos viewer's black bar) provides its own, and the kit controls follow it. */
internal val LocalKitInk = androidx.compose.runtime.compositionLocalOf<Color?> { null }

internal val GlassInkOnPanel: Color @Composable get() = LocalKitInk.current ?: MaterialTheme.colorScheme.onSurface

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
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) },
            confirmButton = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions) })
        return
    }
    val shape = Corner.xlarge
    val panelBackdrop = rememberLayerBackdrop()
    val dialogTone = rememberGlassTone()
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
                        glassColor(dialogTone)
                        blur(8f.dp.toPx())
                        lens(lerp(8f, 44f, glass.settings.height).dp.toPx(), lerp(16f, 132f, glass.settings.amount).dp.toPx(),
                            depthEffect = true, chromaticAberration = glass.settings.chromatic > 0.05f)
                    },
                    highlight = { GlassRim.Light },
                    exportedBackdrop = panelBackdrop,
                    onDrawSurface = { drawRect(surface) })
                .padding(16.dp)) {
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
    GlassButton(onClick, modifier.then(if (tag != null) Modifier.testTag(tag) else Modifier), enabled, prominent) {
        Text(text, maxLines = 1)
    }
}

/**
 * The surface of a glass button or selected chip, as in the Backdrop library's own button: a capsule of the panel behind it, bent by a
 * small lens and tinted, that swells and drifts under the finger with a light that follows it (see [GlassPressState]). [tint], when
 * given, colours it (the wallpaper accent for a prominent button); [surface] is a plain wash for the resting look.
 */
@Composable
private fun Modifier.glassControlSurface(glass: PageGlass, press: GlassPressState, tint: Color?, surface: Color?, shadowed: Boolean): Modifier {
    val tone = rememberGlassTone()
    return drawBackdrop(
        backdrop = glass.backdrop,
        shape = { RoundedCornerShape(percent = 50) },
        effects = {
            glassColor(tone)
            blur(2f.dp.toPx())
            lens(10f.dp.toPx(), 20f.dp.toPx())
        },
        highlight = { GlassRim.Light },
        shadow = { if (shadowed) Shadow(radius = 8f.dp, color = Color.Black.copy(alpha = .10f)) else null },
        layerBlock = { with(press) { deform() } },
        onDrawSurface = {
            if (tint != null) { drawRect(tint, blendMode = BlendMode.Hue); drawRect(tint.copy(alpha = .75f)) }
            if (surface != null) drawRect(surface)
        }
    ).then(press.light).then(press.watcher)
}

/** A row of mutually exclusive pill choices; the selected one is filled with the wallpaper accent. Scrolls sideways
 *  when the choices do not fit. [tagPrefix] gives each a test tag, `<prefix>-<key>`. */
@Composable
internal fun <T> GlassChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit,
    modifier: Modifier = Modifier, tagPrefix: String? = null, key: (T) -> String = { label(it) }) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            GlassChip(option == selected, { onSelect(option) }, { Text(label(option), maxLines = 1) },
                if (tagPrefix != null) Modifier.testTag("$tagPrefix-${key(option)}") else Modifier)
        }
    }
}

/** A label with an optional help line and the liquid switch at the end. */
@Composable
internal fun GlassSwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit, modifier: Modifier = Modifier,
    help: String? = null, tag: String? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, color = GlassInkOnPanel, style = UnoType.subheadline)
            if (help != null) Text(help, color = GlassInkOnPanel.copy(alpha = .65f), style = UnoType.caption1)
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
        if (leading != null) { leading(); Spacer(Modifier.width(8.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = GlassInkOnPanel, style = UnoType.subheadline, maxLines = 1)
            if (subtitle != null) Text(subtitle, color = GlassInkOnPanel.copy(alpha = .65f), style = UnoType.caption1, maxLines = 2)
        }
        if (trailing != null) trailing()
    }
}

/** A section title inside a menu. */
@Composable
internal fun GlassHeading(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = GlassInkOnPanel.copy(alpha = .72f), style = UnoType.caption1.copy(fontWeight = FontWeight.SemiBold, letterSpacing = .6.sp))
}

/** A hairline between sections. */
@Composable
internal fun GlassDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(GlassInkOnPanel.copy(alpha = .12f)))
}

/** A small explanatory line under a control. */
@Composable
internal fun GlassNote(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = GlassInkOnPanel.copy(alpha = .62f), style = UnoType.caption1)
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
        textStyle = UnoType.subheadline.copy(color = ink),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(ink),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().heightIn(min = minHeight).clip(shape).background(ink.copy(alpha = .08f))
                .border(BorderStroke(1.dp, ink.copy(alpha = .16f)), shape).padding(horizontal = 12.dp, vertical = 12.dp),
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart) {
                if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, color = ink.copy(alpha = .45f), style = UnoType.subheadline)
                inner()
            }
        })
}

/*
 * Slot-style overloads: the same shapes as Material's Button, FilterChip, OutlinedTextField, AlertDialog, Checkbox and
 * RadioButton, so a screen written against Material can move onto the glass kit by changing the name. New code can use
 * either form; the label-string forms above are shorter for simple cases.
 */

/** A pill action with arbitrary content (an icon and a label, say). [prominent] fills it with the wallpaper accent. */
@Composable
internal fun GlassButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, prominent: Boolean = false,
    content: @Composable RowScope.() -> Unit) {
    val (accent, accentInk) = accentPair()
    val ink = GlassInkOnPanel
    val shape = RoundedCornerShape(percent = 50)
    val glass = LocalPageGlass.current
    val press = rememberGlassPress()
    val content_ = (if (prominent) accentInk else ink).copy(alpha = if (enabled) 1f else .45f)
    // With glass on, the button is a lensed glass capsule that responds to the finger; with it off, the flat pill it always was.
    val look = if (glass != null) Modifier.glassControlSurface(glass, press,
            tint = if (prominent) accent.copy(alpha = if (enabled) 1f else .45f) else null,
            surface = if (prominent) null else ink.copy(alpha = if (enabled) .10f else .05f), shadowed = true)
        else Modifier.clip(shape).background(if (prominent) accent.copy(alpha = if (enabled) .92f else .35f) else ink.copy(alpha = .10f))
            .border(BorderStroke(1.dp, if (prominent) Color.White.copy(alpha = .25f) else ink.copy(alpha = .16f)), shape)
            .graphicsLayer { with(press) { swell() } }.then(press.watcher)
    Row(modifier.heightIn(min = 48.dp).then(look)
        .clickable(interactionSource = null, indication = null, enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        CompositionLocalProvider(LocalContentColor provides content_) {
            androidx.compose.material3.ProvideTextStyle(UnoType.subheadline.copy(fontWeight = FontWeight.SemiBold)) { content() }
        }
    }
}

/** One choice among several. The selected one is a lit glass capsule in the wallpaper accent; the others are flat and quiet,
 *  and every one swells and glows under the finger. (A lens on every chip would be costly where a row has many.) */
@Composable
internal fun GlassChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val (accent, accentInk) = accentPair()
    val ink = GlassInkOnPanel
    val shape = RoundedCornerShape(percent = 50)
    val glass = LocalPageGlass.current
    val press = rememberGlassPress()
    val look = if (selected && glass != null) Modifier.glassControlSurface(glass, press, tint = accent.copy(alpha = .95f), surface = null, shadowed = false)
        else Modifier.clip(shape).background(if (selected) accent.copy(alpha = .9f) else ink.copy(alpha = .10f))
            .border(BorderStroke(1.dp, if (selected) Color.White.copy(alpha = .25f) else ink.copy(alpha = .16f)), shape)
            .graphicsLayer { with(press) { swell() } }.then(press.light).then(press.watcher)
    Box(modifier.heightIn(min = 48.dp).then(look)
        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, interactionSource = null, indication = null, onClick = onClick)
        .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides (if (selected) accentInk else ink)) {
            androidx.compose.material3.ProvideTextStyle(UnoType.footnote.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)) { label() }
        }
    }
}

/** A labelled text field in the glass style (the label sits above the well). The slot parameters match Material's field. */
@Composable
internal fun GlassField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null, placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null, singleLine: Boolean = false, minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default) {
    val ink = GlassInkOnPanel
    val shape = Corner.medium
    // [modifier] (a test tag, a weight, a width) goes on the editable node itself, so tests can type into it and layout works as with
    // Material's field; the label and supporting text are drawn inside its decoration.
    androidx.compose.foundation.text.BasicTextField(value, onValueChange, modifier, enabled = enabled,
        singleLine = singleLine, minLines = minLines, maxLines = maxLines, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        textStyle = UnoType.subheadline.copy(color = ink),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(ink),
        decorationBox = { inner ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (label != null) CompositionLocalProvider(LocalContentColor provides ink.copy(alpha = .7f)) {
                    androidx.compose.material3.ProvideTextStyle(UnoType.caption1) { label() }
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape).background(ink.copy(alpha = if (enabled) .08f else .04f))
                    .border(BorderStroke(1.dp, ink.copy(alpha = .16f)), shape).padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (leadingIcon != null) { CompositionLocalProvider(LocalContentColor provides ink.copy(alpha = .7f)) { leadingIcon() }; Spacer(Modifier.width(8.dp)) }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) CompositionLocalProvider(LocalContentColor provides ink.copy(alpha = .45f)) {
                            androidx.compose.material3.ProvideTextStyle(UnoType.subheadline) { placeholder() }
                        }
                        inner()
                    }
                    if (trailingIcon != null) { Spacer(Modifier.width(8.dp)); CompositionLocalProvider(LocalContentColor provides ink.copy(alpha = .7f)) { trailingIcon() } }
                }
                if (supportingText != null) CompositionLocalProvider(LocalContentColor provides ink.copy(alpha = .6f)) {
                    androidx.compose.material3.ProvideTextStyle(UnoType.caption1) { supportingText() }
                }
            }
        })
}

/** A glass dialog with Material's AlertDialog slots, for screens that already use them. */
@Composable
internal fun GlassAlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit, modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null, title: (@Composable () -> Unit)? = null, text: (@Composable () -> Unit)? = null) {
    val glass = LocalPageGlass.current
    if (glass == null) {
        AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = modifier,
            dismissButton = dismissButton, title = title, text = text)
        return
    }
    GlassDialog(onDismissRequest, "", modifier, actions = { dismissButton?.invoke(); confirmButton() }) {
        if (title != null) androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
        if (text != null) text()
    }
}

/** A round check that fills with the wallpaper accent. In a list, make the whole row `toggleable` and pass null here, so the label
 *  and the check are one control for a screen reader and the whole row is the touch target. */
@Composable
internal fun GlassCheckbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val (accent, accentInk) = accentPair()
    val ink = GlassInkOnPanel
    // A checkbox flip carries the same toggle haptic as the liquid switch. Null [onCheckedChange] marks a display-only
    // check (the row owns the toggle), so no haptic fires here.
    val haptic = LocalHapticFeedback.current
    Box(modifier.size(48.dp).then(if (onCheckedChange != null) Modifier.toggleable(value = checked, enabled = enabled, role = Role.Checkbox,
        onValueChange = { new -> UnoFeedback.play(Cue.TOGGLE, haptic); onCheckedChange(new) }) else Modifier),
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (checked) accent else Color.Transparent)
            .border(BorderStroke(1.5.dp, if (checked) accent else ink.copy(alpha = .45f)), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center) {
            if (checked) Text(stringResource(com.jake.duolauncher.R.string.text_5), color = accentInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** A round radio mark that fills with the wallpaper accent. In a list, make the row `selectable` and pass null here (see [GlassCheckbox]). */
@Composable
internal fun GlassRadio(selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val (accent, _) = accentPair()
    val ink = GlassInkOnPanel
    Box(modifier.size(48.dp).then(if (onClick != null) Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(22.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .border(BorderStroke(1.5.dp, if (selected) accent else ink.copy(alpha = .45f)), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(12.dp).clip(androidx.compose.foundation.shape.CircleShape).background(accent))
        }
    }
}

/** A round icon action, 48 dp, in the kit's ink. [content] is usually an `Icon` (it is tinted by the content colour). */
@Composable
internal fun GlassIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    val ink = GlassInkOnPanel
    val shape = androidx.compose.foundation.shape.CircleShape
    Box(modifier.size(48.dp).clip(shape).background(ink.copy(alpha = .10f))
        .border(BorderStroke(1.dp, ink.copy(alpha = .16f)), shape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides ink.copy(alpha = if (enabled) 1f else .45f)) { content() }
    }
}
