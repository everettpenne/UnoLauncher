package com.jake.duolauncher.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowRight
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.jake.duolauncher.DuoTypography
import kotlinx.coroutines.withTimeoutOrNull

/** What a key is, for painting: a letter-style key (light), a special key (grey), or the blue action key. */
private enum class KeyRole { LETTER, SPECIAL, ACTION }

/** The colours of the keyboard, flat and close to iOS: white letter keys and grey special keys on a pale panel in light mode,
 * the dark equivalents at night, and a thin line under each key where it meets the panel.
 */
private class KeyPalette(val dark: Boolean, accent: Int?) {
    val ink = if (dark) Color.White else Color.Black
    val panel = if (dark) Color(0xFF2A2A2C) else Color(0xFFD1D3D9)
    val divider = if (dark) Color(0x33FFFFFF) else Color(0x22000000)
    val letter = if (dark) Color(0xFF5D5D60) else Color.White
    val special = if (dark) Color(0xFF3C3C3F) else Color(0xFFAEB3BE)
    val specialPressed = if (dark) Color(0xFF5D5D60) else Color.White
    val shadow = if (dark) Color(0xB3000000) else Color(0xFF898A8D)
    val action = accent?.let { Color(it) } ?: Color(0xFF007AFF)
    val sub = ink.copy(alpha = .55f)
}

private val LocalKeyboardActions = androidx.compose.runtime.staticCompositionLocalOf<KeyboardActions?> { null }
private val LocalKeyHeight = androidx.compose.runtime.staticCompositionLocalOf { 46.dp }

private val KeyRadius = 6.dp
private val KeyGap = 6.dp
private val RowGap = 10.dp
/** Room under the bottom row. Android's own keyboard-switcher button sits in the navigation bar just below the Return key on phones that
 * show it, and a thumb aiming at the bottom edge of Return landed on it; the extra room keeps the two apart. */
private val BottomGap = 20.dp
private val LaneHeight = 40.dp
/** The widest the keys are allowed to spread: on an unfolded or tablet screen they stay thumb-sized instead of stretching. */
private val MaxKeyboardWidth = 640.dp

@Composable
internal fun KeyboardScreen(ui: KeyboardUiState, actions: KeyboardActions) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val config = LocalConfiguration.current
    val palette = remember(ui.accent, context, config.uiMode) { KeyPalette(ui.dark(context), ui.accent) }
    // Turned sideways the keys are shorter, so the keyboard does not cover the whole screen.
    val keyHeight = if (config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 36.dp else 46.dp
    // Rows are rebuilt only when the page, shift, number row or switcher changes, not on every suggestion update.
    val rows = remember(ui.page, ui.shift, ui.numberRow, ui.globe) { KeyboardModel.rows(ui.page, ui.shift, ui.numberRow, ui.globe) }
    val rootTopLeft = remember { mutableStateOf(Offset.Zero) }
    androidx.compose.material3.MaterialTheme(typography = DuoTypography) {
        CompositionLocalProvider(LocalKeyboardActions provides actions, LocalKeyHeight provides keyHeight) {
            BoxWithConstraints(Modifier.fillMaxWidth().background(palette.panel)
                .drawBehind { drawRect(palette.divider, size = Size(size.width, 1f)) }
                .onGloballyPositioned { rootTopLeft.value = it.positionInWindow(); ui.width = it.size.width.toFloat() }) {
                val oneHanded = ui.oneHanded
                val full = minOf(maxWidth, MaxKeyboardWidth)
                val contentWidth = if (oneHanded != 0) full * .8f else full
                val align = when (oneHanded) { 1 -> Alignment.TopStart; 2 -> Alignment.TopEnd; else -> Alignment.TopCenter }
                Column(Modifier.align(align).width(contentWidth).padding(horizontal = 3.dp).padding(top = 6.dp, bottom = BottomGap)
                    .navigationBarsPadding().testTag("uno-keyboard"), verticalArrangement = Arrangement.spacedBy(RowGap)) {
                    // The lane above the keys holds the suggestions and the Paste button. It is always there (except in password
                    // fields, which never preview keys), so the magnified key always has room above the top row.
                    if (!ui.password && ui.page != KeyPage.EMOJI) SuggestionLane(ui, palette, actions)
                    if (ui.page == KeyPage.EMOJI) EmojiPanel(ui, actions, palette, keyHeight, extra = if (ui.password) 0.dp else LaneHeight + RowGap)
                    else {
                        val rowOffset = if (ui.numberRow && ui.page == KeyPage.LETTERS) 1 else 0
                        rows.forEachIndexed { rawIndex, row ->
                            val index = rawIndex - rowOffset
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                                // The middle row of letters is inset half a key on each side, as on every phone keyboard.
                                if (ui.page == KeyPage.LETTERS && index == 1) Spacer(Modifier.weight(.5f))
                                row.forEach { key -> KeyView(key, ui, actions, palette, Modifier.weight(weightFor(key, ui.page, rawIndex, rows.size, ui.globe))) }
                                if (ui.page == KeyPage.LETTERS && index == 1) Spacer(Modifier.weight(.5f))
                            }
                        }
                    }
                }
                // One-handed: the free side holds a button to give the keyboard its full width back. It is laid over the keyboard with
                // matchParentSize, so it takes its size from the keys and can never make the keyboard window taller (a fill-size
                // child of a window that wraps its content grows to the whole screen).
                if (oneHanded != 0) Row(Modifier.matchParentSize()) {
                    val side: @Composable RowScope.() -> Unit = {
                        Box(Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { actions.setOneHanded(0) }
                            .testTag("one-handed-expand"), contentAlignment = Alignment.Center) {
                            Icon(if (oneHanded == 1) Icons.Rounded.KeyboardDoubleArrowRight else Icons.Rounded.KeyboardDoubleArrowLeft, "Full width keyboard",
                                tint = palette.sub, modifier = Modifier.size(28.dp))
                        }
                    }
                    if (oneHanded == 2) side()
                    Spacer(Modifier.width(contentWidth))
                    if (oneHanded == 1) side()
                }
                // The magnified key preview is drawn here, in the keyboard's own window, in a layer of its own so a key press
                // redraws this and nothing else. It used to be a Popup, which creates and tears down a window for every key.
                PreviewLayer(ui, palette, rootTopLeft)
                AccentLayer(ui, palette, rootTopLeft)
            }
        }
    }
}

private fun weightFor(key: Key, page: KeyPage, row: Int, rowCount: Int, globe: Boolean): Float = when (key) { // row counts from the top
    is Key.Char -> 1f
    Key.Shift, Key.Backspace -> 1.5f
    is Key.Page -> if (row == rowCount - 1) 1.4f else 1.5f
    Key.Globe -> 1.1f
    Key.Emoji -> 1.1f
    Key.Space -> if (globe) 4f else 5.1f
    Key.Enter -> 2.2f
}

@Composable
private fun KeyView(key: Key, ui: KeyboardUiState, actions: KeyboardActions, palette: KeyPalette, modifier: Modifier) {
    when (key) {
        is Key.Char -> LetterKey(key, ui, actions, palette, modifier)
        Key.Shift -> {
            val on = ui.shift != ShiftState.OFF
            GlassKey(palette, modifier, if (on) KeyRole.LETTER else KeyRole.SPECIAL, tag = "key-shift", hapticDown = HapticKind.MODIFIER, onTap = { actions.shift() }) {
                ShiftGlyph(filled = on, color = if (on && !palette.dark) Color.Black else palette.ink)
                if (ui.shift == ShiftState.LOCKED) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(width = 14.dp, height = 2.dp).background(palette.ink))
            }
        }
        Key.Backspace -> RepeatingKey(palette, modifier, onHaptic = actions::haptic, onStep = { held -> actions.backspace(held) })
        is Key.Page -> GlassKey(palette, modifier, KeyRole.SPECIAL, tag = "key-page", hapticDown = HapticKind.MODIFIER, onTap = { actions.page(key.target) }) {
            Text(key.label, color = palette.ink, fontSize = 16.sp, fontWeight = FontWeight.Normal)
        }
        Key.Emoji -> GlassKey(palette, modifier, KeyRole.SPECIAL, tag = "key-emoji", hapticDown = HapticKind.MODIFIER, onTap = { actions.page(KeyPage.EMOJI) }) {
            Icon(Icons.Rounded.EmojiEmotions, "Emoji", tint = palette.ink, modifier = Modifier.size(22.dp))
        }
        Key.Globe -> GlassKey(palette, modifier, KeyRole.SPECIAL, tag = "key-globe", hapticDown = HapticKind.MODIFIER, onTap = { actions.globe() }) {
            Icon(Icons.Rounded.Language, "Switch keyboard", tint = palette.ink, modifier = Modifier.size(22.dp))
        }
        Key.Space -> SpaceKey(ui, actions, palette, modifier)
        Key.Enter -> {
            val accent = ui.enter.sendsAction
            GlassKey(palette, modifier, if (accent) KeyRole.ACTION else KeyRole.SPECIAL, tag = "key-enter", hapticDown = HapticKind.RETURN, onTap = { actions.enter() }) {
                Text(ui.enter.label, color = if (accent) Color.White else palette.ink, fontSize = 16.sp,
                    fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.Center)
            }
        }
    }
}

/** iOS's shift glyph: a hollow arrow, filled while shift is on. */
@Composable
private fun ShiftGlyph(filled: Boolean, color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
        val w = size.width; val h = size.height
        val p = Path().apply {
            moveTo(w * .5f, h * .06f); lineTo(w * .96f, h * .52f); lineTo(w * .68f, h * .52f); lineTo(w * .68f, h * .90f)
            lineTo(w * .32f, h * .90f); lineTo(w * .32f, h * .52f); lineTo(w * .04f, h * .52f); close()
        }
        if (filled) drawPath(p, color, style = Fill)
        drawPath(p, color, style = Stroke(1.6.dp.toPx(), join = StrokeJoin.Round))
    }
}

/** A letter key. It types the moment the finger lands (not when it lifts), which is the fastest a key can respond, and
 * shows a magnified preview while held (never in password fields), the key itself hiding under it as on iOS. Holding a key
 * that has alternatives (accented letters, symbol variants) opens a strip; sliding to one and lifting swaps the character just
 * typed for it. Each key handles its own finger, so two fingers can type at once.
 */
@Composable
private fun LetterKey(key: Key.Char, ui: KeyboardUiState, actions: KeyboardActions, palette: KeyPalette, modifier: Modifier) {
    var pressed by remember { mutableStateOf(false) }
    // Where the key is, kept for the preview and the accent strip. Only read when a key is pressed, so it is not observable state:
    // writing it on every layout would invalidate the key for nothing.
    val bounds = remember { arrayOf(Rect.Zero) }
    // Whether this key has its alternatives strip open (the strip itself is drawn by AccentLayer).
    var accenting by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val cell = with(density) { 40.dp.toPx() }
    // The key's label changes with shift. Reading the latest through State keeps the touch handler alive across that change:
    // keying it on the label restarted every key's handler on the first keystroke after shift, dropping an overlapping tap.
    val current by rememberUpdatedState(key)
    val passwordField by rememberUpdatedState(ui.password)
    Box(modifier) {
        GlassKey(palette, Modifier.fillMaxWidth(), KeyRole.LETTER, pressed = pressed, hidden = pressed && !ui.password && !accenting,
            tag = "key-${key.label}", onTap = null,
            pointer = Modifier.onGloballyPositioned { bounds[0] = it.boundsInWindow() }.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val typed = current
                    pressed = true
                    actions.haptic(HapticKind.LETTER)
                    actions.type(typed.output)
                    if (!passwordField && typed.label.length == 1) ui.preview = KeyPreview(typed.label, bounds[0])
                    val options = if (typed.label.length == 1) KeyboardModel.alternates(typed.label) else emptyList()
                    val quick = withTimeoutOrNull(if (options.isEmpty()) 60_000L else 380L) { waitForUpOrCancellation() }
                    if (quick != null || options.isEmpty()) { pressed = false; ui.preview = null; return@awaitEachGesture }
                    ui.preview = null
                    accenting = true
                    var chosen = -1
                    ui.accents = AccentState(options, chosen, bounds[0])
                    val layout = accentLayout(bounds[0], ui.width, options.size, cell)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        val index = ((bounds[0].left + change.position.x - layout.left) / cell).toInt().coerceIn(0, options.lastIndex)
                        if (index != chosen) { chosen = index; ui.accents = AccentState(options, chosen, bounds[0]); actions.haptic(HapticKind.SELECT) }
                        change.consume()
                        if (!change.pressed) break
                    }
                    options.getOrNull(chosen)?.let(actions::replaceLast)
                    ui.accents = null; accenting = false; pressed = false
                }
            }) {
            Text(key.label, color = if (pressed && !ui.password && !accenting) Color.Transparent else palette.ink, fontSize = 23.sp, fontWeight = FontWeight.Normal)
        }
    }
}

private fun accentLayout(anchor: Rect, windowWidth: Float, count: Int, cell: Float): Rect {
    val width = count * cell
    val left = (anchor.center.x - width / 2f).coerceIn(4f, (windowWidth - width - 4f).coerceAtLeast(4f))
    return Rect(left, anchor.top - 60f, left + width, anchor.top - 8f)
}

/** The magnified key: a balloon rising out of the key, like iOS's. It sits in the lane above the top row. */
@Composable
private fun BoxScope.PreviewLayer(ui: KeyboardUiState, palette: KeyPalette, origin: State<Offset>) {
    val p = ui.preview ?: return
    val density = LocalDensity.current
    val o = origin.value
    val key = Rect(p.bounds.left - o.x, p.bounds.top - o.y, p.bounds.right - o.x, p.bounds.bottom - o.y)
    val minHead = with(density) { 34.dp.toPx() }
    val headH = minOf(with(density) { 58.dp.toPx() }, key.top - with(density) { 2.dp.toPx() })
    if (headH < minHead) return                       // no room above this key, so nothing is drawn rather than something clipped
    val headW = key.width * 1.5f
    val headLeft = (key.center.x - headW / 2f).coerceIn(2f, (ui.width - headW - 2f).coerceAtLeast(2f))
    val headTop = key.top - headH
    val r = with(density) { 8.dp.toPx() }
    val lift = with(density) { 1.dp.toPx() }
    // matchParentSize, not fillMaxSize: the keyboard window wraps its content, and a child that fills a wrapping window makes it as
    // tall as the screen, which Android reads as a keyboard covering everything and hides it the moment a key is held.
    Box(Modifier.matchParentSize().drawBehind {
        // The balloon: a head wider than the key, a neck, and the key itself, drawn in one colour with a thin line beneath.
        fun shape(color: Color, dy: Float) {
            drawRoundRect(color, Offset(headLeft, headTop + dy), Size(headW, headH), CornerRadius(r))
            drawRect(color, Offset(key.left, headTop + headH - r + dy), Size(key.width, key.height * .5f + r))
            drawRoundRect(color, Offset(key.left, key.top + dy), Size(key.width, key.height - lift), CornerRadius(r * .75f))
        }
        shape(palette.shadow, lift); shape(palette.letter, 0f)
    }.testTag("key-preview")) {
        Box(Modifier.offset { IntOffset(headLeft.toInt(), headTop.toInt()) }.size(width = with(density) { headW.toDp() }, height = with(density) { headH.toDp() }),
            contentAlignment = Alignment.Center) { Text(p.label, color = palette.ink, fontSize = 34.sp) }
    }
}

/** The alternatives strip for a held key, in a layer of the keyboard's own window (see PreviewLayer): above the key when there is room,
 * and below it otherwise (the top row of a password field has no lane above it).
 */
@Composable
private fun BoxScope.AccentLayer(ui: KeyboardUiState, palette: KeyPalette, origin: State<Offset>) {
    val a = ui.accents ?: return
    val density = LocalDensity.current
    val cell = with(density) { 40.dp.toPx() }
    val o = origin.value
    val layout = accentLayout(a.bounds, ui.width, a.options.size, cell)
    val height = with(density) { 52.dp.toPx() }
    val above = a.bounds.top - o.y - height - 8f
    val top = if (above >= 2f) above else a.bounds.bottom - o.y + 6f
    Box(Modifier.matchParentSize()) {
        Row(Modifier.offset { IntOffset((layout.left - o.x).toInt(), top.toInt()) }.height(52.dp)
            .drawBehind { drawKey(palette, KeyRole.LETTER, pressed = false, radius = 10.dp.toPx(), lift = 1.dp.toPx()) }.testTag("accent-strip")) {
            a.options.forEachIndexed { i, text ->
                Box(Modifier.size(width = 40.dp, height = 52.dp).then(if (i == a.index) Modifier.background(palette.action, RoundedCornerShape(8.dp)) else Modifier),
                    contentAlignment = Alignment.Center) { Text(text, color = if (i == a.index) Color.White else palette.ink, fontSize = 24.sp) }
            }
        }
    }
}

/** Backspace: acts once on press, then repeats while held, faster the longer it is held and taking whole words after a while. */
@Composable
private fun RepeatingKey(palette: KeyPalette, modifier: Modifier, onHaptic: (HapticKind) -> Unit, onStep: (Int) -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    GlassKey(palette, modifier, KeyRole.SPECIAL, pressed = pressed, tag = "key-backspace", onTap = null,
        pointer = Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true; onHaptic(HapticKind.DELETE); onStep(0)
                // Held long enough, it repeats, faster the longer it is held; released or cancelled, it stops.
                var held = 0
                while (true) {
                    val up = withTimeoutOrNull(if (held == 0) 420L else if (held < 8) 90L else 45L) { waitForUpOrCancellation() }
                    if (up != null || !currentEvent.changes.any { it.pressed }) break
                    held++
                    onHaptic(HapticKind.DELETE_REPEAT); onStep(held)
                }
                pressed = false
            }
        }) {
        Icon(if (pressed) Icons.AutoMirrored.Rounded.Backspace else Icons.AutoMirrored.Outlined.Backspace, "Delete", tint = palette.ink, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun GlassKey(palette: KeyPalette, modifier: Modifier, role: KeyRole, tag: String, onTap: (() -> Unit)?,
    pressed: Boolean = false, hidden: Boolean = false, pointer: Modifier = Modifier, hapticDown: HapticKind? = null,
    content: @Composable BoxScope.() -> Unit) {
    val actions0 = LocalKeyboardActions.current
    var down by remember { mutableStateOf(false) }
    val tapModifier = if (onTap != null) Modifier.pointerInput(onTap) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false); down = true
            hapticDown?.let { actions0?.haptic(it) }
            val up = waitForUpOrCancellation(); down = false
            if (up != null) onTap()
        }
    } else Modifier
    Box(modifier.height(LocalKeyHeight.current).drawBehind {
        if (!hidden) drawKey(palette, role, pressed || down, KeyRadius.toPx(), lift = 1.dp.toPx())
    }.then(tapModifier).then(pointer).testTag(tag), contentAlignment = Alignment.Center, content = content)
}

/** A flat key with a thin darker line under it, as on iOS. Special keys turn light while pressed. */
private fun DrawScope.drawKey(palette: KeyPalette, role: KeyRole, pressed: Boolean, radius: Float, lift: Float) {
    val corner = CornerRadius(radius)
    val body = when (role) {
        KeyRole.ACTION -> if (pressed) palette.action.copy(alpha = .8f) else palette.action
        KeyRole.LETTER -> palette.letter
        KeyRole.SPECIAL -> if (pressed) palette.specialPressed else palette.special
    }
    drawRoundRect(palette.shadow, Offset(0f, lift), Size(size.width, size.height - lift), corner)
    drawRoundRect(body, Offset.Zero, Size(size.width, size.height - lift), corner)
}

/** The space bar. A tap types a space; sliding a finger along it moves the cursor, a character for each few millimetres
 * (like FlorisBoard's space-bar cursor control), which is the quick way to fix a typo in the middle of a sentence.
 */
@Composable
private fun SpaceKey(ui: KeyboardUiState, actions: KeyboardActions, palette: KeyPalette, modifier: Modifier) {
    val density = LocalDensity.current
    var pressed by remember { mutableStateOf(false) }
    var sliding by remember { mutableStateOf(false) }
    val slop = with(density) { 10.dp.toPx() }
    val step = with(density) { 14.dp.toPx() }
    GlassKey(palette, modifier, KeyRole.LETTER, pressed = pressed, tag = "key-space", onTap = null,
        pointer = Modifier.pointerInput(ui.spaceCursor) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                pressed = true; actions.haptic(HapticKind.SPACE)
                var moved = 0f; var emitted = 0; var dragging = false
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    moved = change.position.x - down.position.x
                    if (ui.spaceCursor && !dragging && kotlin.math.abs(moved) > slop) { dragging = true; sliding = true }
                    if (dragging) {
                        val target = ((moved - kotlin.math.sign(moved) * slop) / step).toInt()
                        if (target != emitted) { actions.moveCursor(target - emitted); emitted = target; actions.haptic(HapticKind.CURSOR) }
                        change.consume()
                    }
                    if (!change.pressed) break
                }
                if (!dragging) actions.space()
                pressed = false; sliding = false
            }
        }) {
        Text(if (sliding) "←  cursor  →" else "space", color = palette.ink.copy(alpha = if (sliding) .55f else 1f), fontSize = 16.sp)
    }
}

/** The lane above the keys: three suggestion slots, and a Paste button when something is on the clipboard. When a correction is
 * coming it sits in the middle, in bold, and is what the space bar will apply; tapping any slot uses it.
 */
@Composable
private fun SuggestionLane(ui: KeyboardUiState, palette: KeyPalette, actions: KeyboardActions) {
    Row(Modifier.fillMaxWidth().height(LaneHeight).testTag("suggestion-strip"), verticalAlignment = Alignment.CenterVertically) {
        // Always three slots, so the keys below never change height while you type.
        val suggestions = if (ui.showStrip) ui.suggestions else emptyList()
        val slots = suggestions.take(3)
        Row(Modifier.weight(1f).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { index ->
                val s = slots.getOrNull(index)
                val bold = s != null && s.kind == SuggestionKind.CORRECTION
                Box(Modifier.weight(1f).fillMaxHeight().then(if (s != null) Modifier.pointerInput(s) {
                    awaitEachGesture { awaitFirstDown(requireUnconsumed = false); val up = waitForUpOrCancellation(); if (up != null) { actions.haptic(HapticKind.MODIFIER); actions.pick(s) } }
                } else Modifier).testTag("suggestion-$index"), contentAlignment = Alignment.Center) {
                    if (s != null) Text(if (s.kind == SuggestionKind.TYPED && slots.any { it.kind == SuggestionKind.CORRECTION }) "“${s.text}”" else s.text,
                        color = palette.ink, fontSize = 17.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
                }
                if (index < 2) Box(Modifier.width(1.dp).height(22.dp).background(palette.ink.copy(alpha = .18f)))
            }
        }
        if (ui.pasteAvailable) Box(Modifier.width(44.dp).fillMaxHeight().pointerInput(Unit) {
            awaitEachGesture { awaitFirstDown(requireUnconsumed = false); val up = waitForUpOrCancellation(); if (up != null) { actions.haptic(HapticKind.MODIFIER); actions.paste() } }
        }.testTag("paste-button"), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ContentPaste, "Paste", tint = palette.ink, modifier = Modifier.size(22.dp))
        }
    }
}

/** The emoji panel: a scrolling grid of the chosen category, with the category tabs along the bottom beside the ABC and delete keys,
 * as on iOS. It is exactly as tall as the keys it replaces, so the keyboard does not change size.
 */
@Composable
private fun EmojiPanel(ui: KeyboardUiState, actions: KeyboardActions, palette: KeyPalette, keyHeight: Dp, extra: Dp) {
    val categories = ui.emoji
    val hasRecents = ui.recents.isNotEmpty()
    var selected by remember { mutableIntStateOf(if (hasRecents) -1 else 0) }
    val tab = if (selected == -1 && !hasRecents) 0 else selected
    val items = if (tab == -1) ui.recents else categories.getOrNull(tab)?.items.orEmpty()
    // The emoji page has no suggestion lane, so the grid takes the lane's height as well and the keyboard stays the same size.
    val gridHeight = keyHeight * 3 + RowGap * 2 + extra
    Column(Modifier.fillMaxWidth().testTag("emoji-panel"), verticalArrangement = Arrangement.spacedBy(RowGap)) {
        LazyVerticalGrid(GridCells.Fixed(8), Modifier.fillMaxWidth().height(gridHeight)) {
            items(items, key = { it }) { e ->
                Box(Modifier.height(46.dp).pointerInput(e) { detectTapGestures { actions.haptic(HapticKind.LETTER); actions.emoji(e) } },
                    contentAlignment = Alignment.Center) { Text(e, fontSize = 27.sp) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyGap), verticalAlignment = Alignment.CenterVertically) {
            GlassKey(palette, Modifier.weight(1.5f), KeyRole.SPECIAL, tag = "key-page", hapticDown = HapticKind.MODIFIER, onTap = { actions.page(KeyPage.LETTERS) }) {
                Text("ABC", color = palette.ink, fontSize = 16.sp)
            }
            Row(Modifier.weight(7f).height(keyHeight), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                val tabs = (if (hasRecents) listOf(-1 to "🕒") else emptyList()) + categories.mapIndexed { i, c -> i to c.tab }
                tabs.forEach { (index, glyph) ->
                    Box(Modifier.weight(1f).fillMaxHeight().pointerInput(index) { detectTapGestures { actions.haptic(HapticKind.MODIFIER); selected = index } }
                        .testTag("emoji-tab-$index"), contentAlignment = Alignment.Center) {
                        Text(glyph, fontSize = 19.sp, modifier = Modifier.then(if (index == tab) Modifier else Modifier.graphicsAlpha(.55f)))
                        if (index == tab) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(width = 14.dp, height = 2.dp).background(palette.action))
                    }
                }
            }
            RepeatingKey(palette, Modifier.weight(1.5f), onHaptic = actions::haptic, onStep = { held -> actions.backspace(held) })
        }
    }
}

private fun Modifier.graphicsAlpha(alpha: Float) = this.then(Modifier.graphicsLayer { this.alpha = alpha })
