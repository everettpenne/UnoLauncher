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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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

private val KeyShape = RoundedCornerShape(10.dp)
private val KeyHeight = 46.dp

@Composable
internal fun KeyboardScreen(ui: KeyboardUiState, actions: KeyboardActions) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val palette = remember(ui.accent, context) { KeyPalette(ui.dark(context), ui.accent) }
    androidx.compose.material3.MaterialTheme(typography = DuoTypography) {
        Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(palette.panelTop, palette.panelBottom)))
            .drawBehind { drawRect(Brush.horizontalGradient(listOf(Color.White.copy(alpha = .75f), Color.White.copy(alpha = .1f))), size = androidx.compose.ui.geometry.Size(size.width, 1.5.dp.toPx())) }
            .padding(horizontal = 4.dp).padding(top = 8.dp, bottom = 6.dp).navigationBarsPadding().testTag("uno-keyboard"),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyboardModel.rows(ui.page, ui.shift).forEachIndexed { index, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    // The middle row of letters is inset half a key on each side, as on every phone keyboard.
                    if (ui.page == KeyPage.LETTERS && index == 1) Spacer(Modifier.weight(.5f))
                    row.forEach { key -> KeyView(key, ui, actions, palette, Modifier.weight(weightFor(key, ui.page, index))) }
                    if (ui.page == KeyPage.LETTERS && index == 1) Spacer(Modifier.weight(.5f))
                }
            }
        }
    }
}

private fun weightFor(key: Key, page: KeyPage, row: Int): Float = when (key) {
    is Key.Char -> 1f
    Key.Shift, Key.Backspace -> 1.4f
    is Key.Page -> if (row == 3) 1.5f else 1.4f
    Key.Globe -> 1.1f
    Key.Space -> 5f
    Key.Enter -> 2.2f
}

@Composable
private fun KeyView(key: Key, ui: KeyboardUiState, actions: KeyboardActions, palette: KeyPalette, modifier: Modifier) {
    val view = LocalView.current
    when (key) {
        is Key.Char -> LetterKey(key, ui, actions, palette, modifier)
        Key.Shift -> {
            val on = ui.shift != ShiftState.OFF
            GlassKey(palette, modifier, special = !on, lit = on, tag = "key-shift", onTap = { actions.tick(view); actions.shift() }) {
                Icon(Icons.Rounded.ArrowUpward, "Shift", tint = palette.ink, modifier = Modifier.size(22.dp))
                if (ui.shift == ShiftState.LOCKED) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(width = 14.dp, height = 2.dp).background(palette.ink))
            }
        }
        Key.Backspace -> RepeatingKey(palette, modifier, onTick = { actions.tick(view) }, onStep = { actions.backspace() }) {
            Icon(Icons.Rounded.Backspace, "Delete", tint = palette.ink, modifier = Modifier.size(22.dp))
        }
        is Key.Page -> GlassKey(palette, modifier, special = true, tag = "key-page", onTap = { actions.tick(view); actions.page(key.target) }) {
            Text(key.label, color = palette.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        Key.Globe -> GlassKey(palette, modifier, special = true, tag = "key-globe", onTap = { actions.tick(view); actions.globe() }) {
            Icon(Icons.Rounded.Language, "Switch keyboard", tint = palette.ink, modifier = Modifier.size(22.dp))
        }
        Key.Space -> GlassKey(palette, modifier, special = false, tag = "key-space", onTap = { actions.tick(view); actions.space() }) {
            Text("space", color = palette.ink.copy(alpha = .55f), fontSize = 14.sp)
        }
        Key.Enter -> {
            val accent = ui.enter.sendsAction
            GlassKey(palette, modifier, special = !accent, accentFill = accent, tag = "key-enter", onTap = { actions.tick(view); actions.enter() }) {
                if (ui.enter == EnterKind.RETURN) Icon(Icons.Rounded.KeyboardReturn, "Return", tint = palette.ink, modifier = Modifier.size(22.dp))
                else Text(ui.enter.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }
    }
}

/** A key that types on release, shows a magnified preview while held (never in password fields), and offers accents on a long press. */
@Composable
private fun LetterKey(key: Key.Char, ui: KeyboardUiState, actions: KeyboardActions, palette: KeyPalette, modifier: Modifier) {
    val view = LocalView.current
    var pressed by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var accents by remember { mutableStateOf<List<String>?>(null) }
    var accentIndex by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val windowWidth = view.rootView.width
    val cell = with(density) { 40.dp.toPx() }
    val letter = key.label.length == 1 && key.label[0].isLetter()
    GlassKey(palette, modifier, special = false, pressed = pressed, tag = "key-${key.label}", onTap = null,
        pointer = Modifier.onGloballyPositioned { bounds = it.boundsInWindow() }.pointerInput(key.label, ui.page, ui.shift) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                pressed = true; actions.tick(view)
                val options = if (letter) KeyboardModel.alternates(key.label) else emptyList()
                val quick = withTimeoutOrNull(380L) { waitForUpOrCancellation() }
                if (quick != null || options.isEmpty() && !currentEvent.changes.any { it.pressed }) {
                    pressed = false; actions.type(key.output); return@awaitEachGesture
                }
                if (options.isNotEmpty()) {
                    accents = options; accentIndex = -1
                    val layout = accentLayout(bounds, windowWidth.toFloat(), options.size, cell)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        accentIndex = ((bounds.left + change.position.x - layout.left) / cell).toInt().coerceIn(0, options.lastIndex)
                        change.consume()
                        if (!change.pressed) break
                    }
                    val chosen = options.getOrNull(accentIndex) ?: key.output
                    accents = null; pressed = false; actions.type(chosen)
                } else {
                    waitForUpOrCancellation(); pressed = false; actions.type(key.output)
                }
            }
        }) {
        Text(key.label, color = palette.ink, fontSize = 22.sp, fontWeight = FontWeight.Normal)
    }
    if (pressed && accents == null && !ui.password && letter) Popup(popupPositionProvider = PreviewPosition(bounds), properties = PopupProperties(focusable = false)) {
        Box(Modifier.size(width = 56.dp, height = 64.dp).drawBehind { drawKey(palette, special = false, pressed = false, radius = 14.dp.toPx()) }.testTag("key-preview"),
            contentAlignment = Alignment.Center) { Text(key.label, color = palette.ink, fontSize = 34.sp) }
    }
    accents?.let { options ->
        Popup(popupPositionProvider = AccentPosition(bounds, windowWidth.toFloat(), options.size, cell), properties = PopupProperties(focusable = false)) {
            Row(Modifier.height(52.dp).drawBehind { drawKey(palette, special = false, pressed = false, radius = 14.dp.toPx()) }.padding(horizontal = 0.dp).testTag("accent-strip")) {
                options.forEachIndexed { i, text ->
                    Box(Modifier.size(width = 40.dp, height = 52.dp).then(if (i == accentIndex) Modifier.background(palette.action, RoundedCornerShape(10.dp)) else Modifier),
                        contentAlignment = Alignment.Center) { Text(text, color = if (i == accentIndex) Color.White else palette.ink, fontSize = 24.sp) }
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
private fun RepeatingKey(palette: KeyPalette, modifier: Modifier, onTick: () -> Unit, onStep: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    GlassKey(palette, modifier, special = true, pressed = pressed, tag = "key-backspace", onTap = null,
        pointer = Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true; onTick(); onStep()
                // Held long enough, it repeats, faster the longer it is held; released or cancelled, it stops.
                var held = 0
                while (true) {
                    val up = withTimeoutOrNull(if (held == 0) 420L else if (held < 8) 90L else 45L) { waitForUpOrCancellation() }
                    if (up != null || !currentEvent.changes.any { it.pressed }) break
                    onStep(); held++
                }
                pressed = false
            }
        }, content = content)
}

@Composable
private fun GlassKey(palette: KeyPalette, modifier: Modifier, special: Boolean, tag: String, onTap: (() -> Unit)?,
    pressed: Boolean = false, lit: Boolean = false, accentFill: Boolean = false, pointer: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit) {
    var down by remember { mutableStateOf(false) }
    val tapModifier = if (onTap != null) Modifier.pointerInput(onTap) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false); down = true
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
