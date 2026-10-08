package com.jake.duolauncher.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.jake.duolauncher.DuoTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** The colours the keys are painted with. The keyboard can't sample what is behind it (it lives in its own window), so
 * the glass is painted: a lit tile, a sheen, and a rim that is bright toward the upper left like every Uno glass edge.
 */
private class KeyPalette(val dark: Boolean, accent: Int?) {
    val ink = if (dark) Color(0xFFF2F6F8) else Color(0xFF1B2A33)
    val panelTop = if (dark) Color(0xFF1E2C34) else Color(0xFFD8E4EA)
    val panelBottom = if (dark) Color(0xFF131E24) else Color(0xFFC3D3DB)
    val keyLit = if (dark) Color(0xFF3A4B55) else Color(0xFFFFFFFF)
    val keyDeep = if (dark) Color(0xFF2A3841) else Color(0xFFEAF0F3)
    val specialLit = if (dark) Color(0xFF2B3A43) else Color(0xFFCBD9E0)
    val specialDeep = if (dark) Color(0xFF212D35) else Color(0xFFB9CAD3)
    val action = accent?.let { Color(it) } ?: Color(0xFF3B82B6)
}

private val LocalKeyboardActions = androidx.compose.runtime.staticCompositionLocalOf<KeyboardActions?> { null }

private val KeyShape = RoundedCornerShape(10.dp)
private val KeyHeight = 46.dp

@Composable
internal fun KeyboardScreen(ui: KeyboardUiState, actions: KeyboardActions) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val palette = remember(ui.accent, context) { KeyPalette(ui.dark(context), ui.accent) }
    // Rows are rebuilt only when the page, shift or number row changes, not on every suggestion update.
    val rows = remember(ui.page, ui.shift, ui.numberRow) { KeyboardModel.rows(ui.page, ui.shift, ui.numberRow) }
    var rootTopLeft by remember { mutableStateOf(Offset.Zero) }
    androidx.compose.material3.MaterialTheme(typography = DuoTypography) {
        androidx.compose.runtime.CompositionLocalProvider(LocalKeyboardActions provides actions) {
        Box(Modifier.onGloballyPositioned { rootTopLeft = it.positionInWindow(); ui.width = it.size.width.toFloat() }) {
        Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(palette.panelTop, palette.panelBottom)))
            .drawBehind { drawRect(Brush.horizontalGradient(listOf(Color.White.copy(alpha = .75f), Color.White.copy(alpha = .1f))), size = androidx.compose.ui.geometry.Size(size.width, 1.5.dp.toPx())) }
            .padding(horizontal = 4.dp).padding(top = 8.dp, bottom = 6.dp).navigationBarsPadding().testTag("uno-keyboard"),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ui.showStrip) SuggestionStrip(ui.suggestions, palette, actions)
            val rowOffset = if (ui.numberRow && ui.page == KeyPage.LETTERS) 1 else 0
            rows.forEachIndexed { rawIndex, row ->
                val index = rawIndex - rowOffset
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    // The middle row of letters is inset half a key on each side, as on every phone keyboard.
                    if (ui.page == KeyPage.LETTERS && index == 1) Spacer(Modifier.weight(.5f))
                    row.forEach { key -> KeyView(key, ui, actions, palette, Modifier.weight(weightFor(key, ui.page, rawIndex))) }
                    if (ui.page == KeyPage.LETTERS && index == 1) Spacer(Modifier.weight(.5f))
                }
            }
        }
        // The magnified key preview is drawn here, in the keyboard's own window. It used to be a Popup, which creates and
        // tears down a whole window for every key pressed, a real cost when typing fast.
        ui.preview?.let { p ->
            val density = LocalDensity.current
            val w = with(density) { 56.dp.toPx() }; val h = with(density) { 64.dp.toPx() }
            Box(Modifier.offset { IntOffset((p.bounds.center.x - rootTopLeft.x - w / 2f).toInt().coerceIn(2, (size0(ui) - w - 2f).toInt().coerceAtLeast(2)),
                    (p.bounds.top - rootTopLeft.y - h + with(density) { 6.dp.toPx() }).toInt()) }
                .size(width = 56.dp, height = 64.dp).drawBehind { drawKey(palette, special = false, pressed = false, radius = 14.dp.toPx()) }.testTag("key-preview"),
                contentAlignment = Alignment.Center) { Text(p.label, color = palette.ink, fontSize = 34.sp) }
        }
        }
        }
    }
}

private fun size0(ui: KeyboardUiState) = ui.width

private fun weightFor(key: Key, page: KeyPage, row: Int): Float = when (key) { // row counts from the top, number row included
    is Key.Char -> 1f
    Key.Shift, Key.Backspace -> 1.4f
    is Key.Page -> 1.4f
    Key.Globe -> 1.1f
    Key.Space -> 5f
    Key.Enter -> 2.2f
}

@Composable
private fun KeyView(key: Key, ui: KeyboardUiState, actions: KeyboardActions, palette: KeyPalette, modifier: Modifier) {
    when (key) {
        is Key.Char -> LetterKey(key, ui, actions, palette, modifier)
        Key.Shift -> {
            val on = ui.shift != ShiftState.OFF
            GlassKey(palette, modifier, special = !on, lit = on, tag = "key-shift", hapticDown = HapticKind.MODIFIER, onTap = { actions.shift() }) {
                Icon(Icons.Rounded.ArrowUpward, "Shift", tint = palette.ink, modifier = Modifier.size(22.dp))
                if (ui.shift == ShiftState.LOCKED) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(width = 14.dp, height = 2.dp).background(palette.ink))
            }
        }
        Key.Backspace -> RepeatingKey(palette, modifier, onHaptic = actions::haptic, onStep = { actions.backspace() }) {
            Icon(Icons.Rounded.Backspace, "Delete", tint = palette.ink, modifier = Modifier.size(22.dp))
        }
        is Key.Page -> GlassKey(palette, modifier, special = true, tag = "key-page", hapticDown = HapticKind.MODIFIER, onTap = { actions.page(key.target) }) {
            Text(key.label, color = palette.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        Key.Globe -> GlassKey(palette, modifier, special = true, tag = "key-globe", hapticDown = HapticKind.MODIFIER, onTap = { actions.globe() }) {
            Icon(Icons.Rounded.Language, "Switch keyboard", tint = palette.ink, modifier = Modifier.size(22.dp))
        }
        Key.Space -> SpaceKey(ui, actions, palette, modifier)
        Key.Enter -> {
            val accent = ui.enter.sendsAction
            GlassKey(palette, modifier, special = !accent, accentFill = accent, tag = "key-enter", hapticDown = HapticKind.RETURN, onTap = { actions.enter() }) {
                if (ui.enter == EnterKind.RETURN) Icon(Icons.Rounded.KeyboardReturn, "Return", tint = palette.ink, modifier = Modifier.size(22.dp))
                else Text(ui.enter.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }
    }
}

/** A letter key. It types the moment the finger lands (not when it lifts), which is the fastest a key can respond, and
 * shows a magnified preview while held (never in password fields). Holding a letter that has accents opens a strip; sliding
 * to one and lifting swaps the letter just typed for it. Each key handles its own finger, so two fingers can type at once.
 */
@Composable
private fun LetterKey(key: Key.Char, ui: KeyboardUiState, actions: KeyboardActions, palette: KeyPalette, modifier: Modifier) {
    var pressed by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var accents by remember { mutableStateOf<List<String>?>(null) }
    var accentIndex by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val cell = with(density) { 40.dp.toPx() }
    val letter = key.label.length == 1 && key.label[0].isLetter()
    // The key's label changes with shift. Reading the latest through State keeps the touch handler alive across that change:
    // keying it on the label restarted every key's handler on the first keystroke after shift, dropping an overlapping tap.
    val current by rememberUpdatedState(key)
    val passwordField by rememberUpdatedState(ui.password)
    Box(modifier) {
        GlassKey(palette, Modifier.fillMaxWidth(), special = false, pressed = pressed, tag = "key-${key.label}", onTap = null,
            pointer = Modifier.onGloballyPositioned { bounds = it.boundsInWindow() }.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val typed = current
                    pressed = true
                    actions.haptic(HapticKind.LETTER)
                    actions.type(typed.output)
                    if (!passwordField && typed.label.length == 1 && typed.label[0].isLetter() || !passwordField && typed.label.length == 1) ui.preview = KeyPreview(typed.label, bounds)
                    val options = if (typed.label.length == 1 && typed.label[0].isLetter()) KeyboardModel.alternates(typed.label) else emptyList()
                    val quick = withTimeoutOrNull(if (options.isEmpty()) 60_000L else 380L) { waitForUpOrCancellation() }
                    if (quick != null || options.isEmpty()) { pressed = false; ui.preview = null; return@awaitEachGesture }
                    ui.preview = null
                    accents = options; accentIndex = -1
                    val layout = accentLayout(bounds, ui.width, options.size, cell)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        val index = ((bounds.left + change.position.x - layout.left) / cell).toInt().coerceIn(0, options.lastIndex)
                        if (index != accentIndex) { accentIndex = index; actions.haptic(HapticKind.SELECT) }
                        change.consume()
                        if (!change.pressed) break
                    }
                    options.getOrNull(accentIndex)?.let(actions::replaceLast)
                    accents = null; pressed = false
                }
            }) {
            Text(key.label, color = palette.ink, fontSize = 22.sp, fontWeight = FontWeight.Normal)
        }
        accents?.let { options ->
            Popup(popupPositionProvider = AccentPosition(bounds, ui.width, options.size, cell), properties = PopupProperties(focusable = false)) {
                Row(Modifier.height(52.dp).drawBehind { drawKey(palette, special = false, pressed = false, radius = 14.dp.toPx()) }.testTag("accent-strip")) {
                    options.forEachIndexed { i, text ->
                        Box(Modifier.size(width = 40.dp, height = 52.dp).then(if (i == accentIndex) Modifier.background(palette.action, RoundedCornerShape(10.dp)) else Modifier),
                            contentAlignment = Alignment.Center) { Text(text, color = if (i == accentIndex) Color.White else palette.ink, fontSize = 24.sp) }
                    }
                }
            }
        }
    }
}

private fun accentLayout(anchor: androidx.compose.ui.geometry.Rect, windowWidth: Float, count: Int, cell: Float): androidx.compose.ui.geometry.Rect {
    val width = count * cell
    val left = (anchor.center.x - width / 2f).coerceIn(4f, (windowWidth - width - 4f).coerceAtLeast(4f))
    return androidx.compose.ui.geometry.Rect(left, anchor.top - 60f, left + width, anchor.top - 8f)
}

private class PreviewPosition(private val anchor: androidx.compose.ui.geometry.Rect) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
        IntOffset((anchor.center.x - popupContentSize.width / 2f).toInt().coerceIn(2, (windowSize.width - popupContentSize.width - 2).coerceAtLeast(2)),
            (anchor.top - popupContentSize.height - 6f).toInt())
}

private class AccentPosition(private val anchor: androidx.compose.ui.geometry.Rect, private val windowWidth: Float, private val count: Int,
    private val cell: Float) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val rect = accentLayout(anchor, windowWidth, count, cell)
        return IntOffset(rect.left.toInt(), (anchor.top - popupContentSize.height - 8f).toInt())
    }
}

/** A key that acts once on press, then repeats while held (backspace). */
@Composable
private fun RepeatingKey(palette: KeyPalette, modifier: Modifier, onHaptic: (HapticKind) -> Unit, onStep: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    GlassKey(palette, modifier, special = true, pressed = pressed, tag = "key-backspace", onTap = null,
        pointer = Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true; onHaptic(HapticKind.DELETE); onStep()
                // Held long enough, it repeats, faster the longer it is held; released or cancelled, it stops.
                var held = 0
                while (true) {
                    val up = withTimeoutOrNull(if (held == 0) 420L else if (held < 8) 90L else 45L) { waitForUpOrCancellation() }
                    if (up != null || !currentEvent.changes.any { it.pressed }) break
                    onHaptic(HapticKind.DELETE_REPEAT); onStep(); held++
                }
                pressed = false
            }
        }, content = content)
}

@Composable
private fun GlassKey(palette: KeyPalette, modifier: Modifier, special: Boolean, tag: String, onTap: (() -> Unit)?,
    pressed: Boolean = false, lit: Boolean = false, accentFill: Boolean = false, pointer: Modifier = Modifier,
    hapticDown: HapticKind? = null,
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
    Box(modifier.height(KeyHeight).drawBehind {
        val isPressed = pressed || down
        if (accentFill) drawKey(palette, special, isPressed, 10.dp.toPx(), fill = palette.action)
        else drawKey(palette, special && !lit, isPressed, 10.dp.toPx(), strong = lit)
    }.then(tapModifier).then(pointer).testTag(tag), contentAlignment = Alignment.Center, content = content)
}

/** The painted glass: a lit tile, a sheen on the upper left, and a rim brightest toward the light. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawKey(palette: KeyPalette, special: Boolean, pressed: Boolean,
    radius: Float, fill: Color? = null, strong: Boolean = false) {
    val corner = CornerRadius(radius)
    val lit = fill ?: if (strong) palette.keyLit else if (special) palette.specialLit else palette.keyLit
    val deep = fill?.copy(alpha = .85f) ?: if (strong) palette.keyDeep else if (special) palette.specialDeep else palette.keyDeep
    val press = if (pressed) .72f else 1f
    drawRoundRect(Brush.verticalGradient(listOf(lit.copy(alpha = lit.alpha * press), deep.copy(alpha = deep.alpha * press))), cornerRadius = corner)
    drawRoundRect(Brush.radialGradient(listOf(Color.White.copy(alpha = if (palette.dark) .12f else .45f), Color.Transparent),
        center = Offset(size.width * .2f, 0f), radius = size.width * .8f), cornerRadius = corner)
    val stroke = 1.2.dp.toPx()
    drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha = if (palette.dark) .55f else .95f), Color.White.copy(alpha = .08f),
        Color.White.copy(alpha = if (palette.dark) .18f else .4f)), Offset.Zero, Offset(size.width, size.height)),
        topLeft = Offset(stroke / 2, stroke / 2), size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
        cornerRadius = corner, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
    if (!pressed) drawRoundRect(Color.Black.copy(alpha = if (palette.dark) .35f else .10f), topLeft = Offset(0f, size.height - 1.dp.toPx()),
        size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()), cornerRadius = corner)
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
    GlassKey(palette, modifier, special = false, pressed = pressed, tag = "key-space", onTap = null,
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
        Text(if (sliding) "\u2190  cursor  \u2192" else "space", color = palette.ink.copy(alpha = .55f), fontSize = 14.sp)
    }
}

/** Three suggestion slots above the keys. When a correction is coming it sits in the middle, in bold, and is what the
 * space bar will apply; tapping any slot uses it.
 */
@Composable
private fun SuggestionStrip(suggestions: List<Suggestion>, palette: KeyPalette, actions: KeyboardActions) {
    Row(Modifier.fillMaxWidth().height(38.dp).testTag("suggestion-strip"), verticalAlignment = Alignment.CenterVertically) {
        // Always three slots, so the keys below never change height while you type.
        val slots = suggestions.take(3)
        repeat(3) { index ->
            val s = slots.getOrNull(index)
            val bold = s != null && s.kind == SuggestionKind.CORRECTION
            Box(Modifier.weight(1f).fillMaxHeight().then(if (s != null) Modifier.pointerInput(s) {
                awaitEachGesture { awaitFirstDown(requireUnconsumed = false); val up = waitForUpOrCancellation(); if (up != null) { actions.haptic(HapticKind.MODIFIER); actions.pick(s) } }
            } else Modifier).testTag("suggestion-$index"), contentAlignment = Alignment.Center) {
                if (s != null) Text(if (s.kind == SuggestionKind.TYPED && slots.any { it.kind == SuggestionKind.CORRECTION }) "\u201C${s.text}\u201D" else s.text,
                    color = palette.ink, fontSize = 17.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
            }
            if (index < 2) Box(Modifier.width(1.dp).height(20.dp).background(palette.ink.copy(alpha = .2f)))
        }
    }
}
