package com.jake.duolauncher.keyboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PushPin as PushPinOutline
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import com.jake.duolauncher.R
import com.jake.duolauncher.appearance.DuoTypography
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot

/** What a key is, for painting: a letter-style key (bright glass), a special key (tinted glass), or the accent action key. */
private enum class KeyRole { LETTER, SPECIAL, ACTION }

/** The colours of the keyboard. The default is liquid glass after iOS 26: over a blurred window the panel is a thin frosted tint and
 * the keys are translucent glass with a bright rim along their top edge; where Android cannot blur, the same look is drawn nearly
 * opaque so the keys stay legible over anything. [classic] is the flat look of Uno 0.97 and before (white and grey keys on an opaque
 * pale panel, a thin line under each key), for those who prefer it; everything the keyboard does is the same in both.
 */
private class KeyPalette(val dark: Boolean, accent: Int?, blurred: Boolean, val classic: Boolean = false) {
    val ink = if (dark) Color.White else if (classic) Color.Black else Color(0xFF0B0B0D)
    val panelTop = when {
        classic -> if (dark) Color(0xFF2A2A2C) else Color(0xFFD1D3D9)
        dark -> Color(0xFF2C2C30).copy(alpha = if (blurred) .62f else .97f)
        else -> Color(0xFFF4F5F8).copy(alpha = if (blurred) .66f else .98f)
    }
    val panelBottom = when {
        classic -> panelTop
        dark -> Color(0xFF1C1C1F).copy(alpha = if (blurred) .72f else 1f)
        else -> Color(0xFFD9DCE3).copy(alpha = if (blurred) .74f else 1f)
    }
    val panelRim = if (classic) (if (dark) Color(0x33FFFFFF) else Color(0x22000000)) else if (dark) Color(0x40FFFFFF) else Color(0xCCFFFFFF)
    val letter = when {
        classic -> if (dark) Color(0xFF5D5D60) else Color.White
        dark -> Color(0xFF6E6E73).copy(alpha = if (blurred) .62f else .92f)
        else -> Color.White.copy(alpha = if (blurred) .82f else 1f)
    }
    val special = when {
        classic -> if (dark) Color(0xFF3C3C3F) else Color(0xFFAEB3BE)
        dark -> Color(0xFF48484D).copy(alpha = if (blurred) .62f else .95f)
        else -> Color(0xFFBEC3CC).copy(alpha = if (blurred) .72f else 1f)
    }
    /** Classic special keys turn the letter colour while pressed; glass keys glow. */
    val specialPressed = if (dark) Color(0xFF5D5D60) else Color.White
    val pressedGlow = if (dark) Color(0x33FFFFFF) else Color(0x14000000)
    val rimTop = if (dark) Color(0x52FFFFFF) else Color(0xF2FFFFFF)
    val rimBottom = if (dark) Color(0x33000000) else Color(0x1F000000)
    val shadow = if (classic) (if (dark) Color(0xB3000000) else Color(0xFF898A8D)) else if (dark) Color(0x66000000) else Color(0x29000000)
    val action = accent?.let { Color(it) } ?: if (classic) Color(0xFF007AFF) else Color(0xFF0A84FF)
    val sub = ink.copy(alpha = .55f)
    val pill = if (classic) ink.copy(alpha = .08f) else if (dark) Color(0x2EFFFFFF) else Color(0xA6FFFFFF)
    val divider = ink.copy(alpha = if (classic) .18f else .14f)
    /** The solid colour of the magnified key and the accent strip. */
    val balloon = if (classic) letter else if (dark) Color(0xFF6E6E73) else Color.White
    val keyRadius = if (classic) 6.dp else 9.dp
    val rowGap = if (classic) 10.dp else 11.dp
}

private val LocalKeyboardActions = staticCompositionLocalOf<KeyboardActions?> { null }
private val LocalKeyHeight = staticCompositionLocalOf { 46.dp }

private val KeyGap = 6.dp
/** The gap between rows; the classic style's was a point tighter. Provided with the palette. */
private val LocalRowGap = staticCompositionLocalOf { 11.dp }
private val RowGap: Dp @Composable get() = LocalRowGap.current
/** Room under the bottom row. Android's own keyboard-switcher button sits in the navigation bar just below the Return key on phones that
 * show it, and a thumb aiming at the bottom edge of Return landed on it; the extra room keeps the two apart. */
private val BottomGap = 20.dp
private val LaneHeight = 40.dp
/** The widest the keys are allowed to spread: on an unfolded or tablet screen they stay thumb-sized instead of stretching. */
private val MaxKeyboardWidth = 640.dp

/** The keyboard. [inSettings] draws it for the live preview on the settings page: no navigation-bar room, the same look. */
@Composable
internal fun KeyboardScreen(ui: KeyboardUiState, actions: KeyboardActions, inSettings: Boolean = false) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val config = LocalConfiguration.current
    val palette = remember(ui.accent, context, config.uiMode, ui.blurred, ui.classic) { KeyPalette(ui.dark(context), ui.accent, ui.blurred && !ui.classic, ui.classic) }
    // Turned sideways the keys are shorter, so the keyboard does not cover the whole screen; the height setting scales both.
    val base = if (config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 36.dp else 46.dp
    val keyHeight = base * ui.heightScale
    // Rows are rebuilt only when the page, shift, layout or a key setting changes, not on every suggestion update.
    val rows = remember(ui.page, ui.shift, ui.numberRow, ui.globe, ui.layout, ui.emojiKey) {
        KeyboardModel.rows(ui.page, ui.shift, ui.numberRow, ui.globe, ui.layout, ui.emojiKey)
    }
    val rootTopLeft = remember { mutableStateOf(Offset.Zero) }
    // The classic panel is square-topped, as it was; the glass one has rounded corners the window's blur follows.
    val corner = if (ui.classic) 0.dp else PANEL_CORNER_DP.dp
    androidx.compose.material3.MaterialTheme(typography = DuoTypography) {
        CompositionLocalProvider(LocalKeyboardActions provides actions, LocalKeyHeight provides keyHeight, LocalRowGap provides palette.rowGap) {
            BoxWithConstraints(Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = corner, topEnd = corner, bottomStart = if (inSettings) corner else 0.dp, bottomEnd = if (inSettings) corner else 0.dp))
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(palette.panelTop, palette.panelBottom)))
                    // The glass edge: a bright hairline along the top, fading down the rounded corners; the classic one is a plain line.
                    val r = corner.toPx()
                    if (palette.classic) drawRect(palette.panelRim, size = Size(size.width, 1f))
                    else drawRoundRect(Brush.verticalGradient(0f to palette.panelRim, .12f to Color.Transparent), size = Size(size.width, size.height + r),
                        cornerRadius = CornerRadius(r), style = Stroke(1.dp.toPx()))
                }
                .onGloballyPositioned { rootTopLeft.value = it.positionInWindow(); ui.width = it.size.width.toFloat() }) {
                val oneHanded = ui.oneHanded
                val full = minOf(maxWidth, MaxKeyboardWidth)
                val contentWidth = if (oneHanded != 0) full * .8f else full
                val align = when (oneHanded) { 1 -> Alignment.TopStart; 2 -> Alignment.TopEnd; else -> Alignment.TopCenter }
                val keyRows = if (ui.page == KeyPage.EMOJI) 4 else rows.size
                val keyArea = keyHeight * keyRows + RowGap * (keyRows - 1)
                Column(Modifier.align(align).width(contentWidth).padding(horizontal = if (palette.classic) 3.dp else 4.dp).padding(top = if (palette.classic) 6.dp else 7.dp, bottom = if (inSettings) 10.dp else BottomGap)
                    .then(if (inSettings) Modifier else Modifier.navigationBarsPadding()).testTag("uno-keyboard"), verticalArrangement = Arrangement.spacedBy(RowGap)) {
                    // The lane above the keys holds the suggestions, the tools and the Paste button. It is always there (except in password
                    // fields, which never preview keys), so the magnified key always has room above the top row.
                    if (!ui.password && ui.page != KeyPage.EMOJI) SuggestionLane(ui, palette, actions)
                    AnimatedContent(targetState = if (ui.page == KeyPage.EMOJI) KeyPanel.NONE to true else ui.panel to false,
                        transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(90)) }, label = "keyboard-panel") { (panel, emojiPage) ->
                        when {
                            emojiPage -> EmojiPanel(ui, actions, palette, keyHeight, extra = if (ui.password) 0.dp else LaneHeight + RowGap)
                            panel == KeyPanel.CLIPBOARD -> ClipboardPanel(ui, actions, palette, keyArea)
                            panel == KeyPanel.EDIT -> EditPanel(ui, actions, palette, keyArea)
                            panel == KeyPanel.LAYOUTS -> LayoutPanel(ui, actions, palette, keyArea)
                            else -> KeyRows(ui, actions, palette, rows)
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
                // The magnified key preview, the accent strip and the swipe trail are drawn here, in the keyboard's own window, each in a
                // layer of its own so a key press redraws that and nothing else. They used to be Popups, which create a window per key.
                TrailLayer(ui, palette, rootTopLeft)
                PreviewLayer(ui, palette, rootTopLeft)
                AccentLayer(ui, palette, rootTopLeft)
            }
        }
    }
}

@Composable
private fun KeyRows(ui: KeyboardUiState, actions: KeyboardActions, palette: KeyPalette, rows: List<List<Key>>) {
    Column(verticalArrangement = Arrangement.spacedBy(RowGap)) {
        val rowOffset = if (ui.numberRow && ui.page == KeyPage.LETTERS) 1 else 0
        val widest = rows.drop(rowOffset).take(2).maxOfOrNull { r -> r.count { it is Key.Char } } ?: 10
        rows.forEachIndexed { rawIndex, row ->
            val index = rawIndex - rowOffset
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                // A shorter letter row is inset by half the difference on each side (the middle row of QWERTY by half a key), so every
                // letter key is the same width, as on every phone keyboard.
                val letters = row.count { it is Key.Char }
                val inset = if (ui.page == KeyPage.LETTERS && index in 0..1) (widest - letters) / 2f else 0f
                if (inset > 0f) Spacer(Modifier.weight(inset))
                row.forEach { key -> KeyView(key, ui, actions, palette, Modifier.weight(weightFor(key, ui, rawIndex, rows.size, widest, letters))) }
                if (inset > 0f) Spacer(Modifier.weight(inset))
            }
        }
    }
}

private fun weightFor(key: Key, ui: KeyboardUiState, row: Int, rowCount: Int, widest: Int, letters: Int): Float = when (key) { // row counts from the top
    is Key.Char -> 1f
    // Shift and Delete share what the third row's letters leave of the widest row, but never shrink below a key and a quarter.
    Key.Shift, Key.Backspace -> if (ui.page == KeyPage.LETTERS) ((widest - letters) / 2f).coerceAtLeast(1.25f) else 1.5f
    is Key.Page -> if (row == rowCount - 1) 1.4f else 1.5f
    Key.Globe -> 1.1f
    Key.Emoji -> 1.1f
    Key.Space -> 7.6f - (if (ui.globe) 1.1f else 0f) - (if (ui.emojiKey) 1.1f else 0f)
    Key.Enter -> 2.2f
}

@Composable
private fun KeyView(key: Key, ui: KeyboardUiState, actions: KeyboardActions, palette: KeyPalette, modifier: Modifier) {
    when (key) {
        is Key.Char -> LetterKey(key, ui, actions, palette, modifier)
        Key.Shift -> {
            val on = ui.shift != ShiftState.OFF
            GlassKey(palette, modifier, if (on) KeyRole.LETTER else KeyRole.SPECIAL, tag = "key-shift", hapticDown = HapticKind.MODIFIER, onTap = { actions.shift() }) {
                ShiftGlyph(filled = on, color = palette.ink)
                if (ui.shift == ShiftState.LOCKED) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(width = 14.dp, height = 2.dp).background(palette.ink, RoundedCornerShape(1.dp)))
            }
        }
        Key.Backspace -> RepeatingKey(palette, modifier, onHaptic = actions::haptic, onStep = { held -> actions.backspace(held) })
        is Key.Page -> GlassKey(palette, modifier, KeyRole.SPECIAL, tag = "key-page", hapticDown = HapticKind.MODIFIER, onTap = { actions.page(key.target) }) {
            Text(key.label, color = palette.ink, fontSize = 16.sp, fontWeight = FontWeight.Normal)
        }
        Key.Emoji -> GlassKey(palette, modifier, KeyRole.SPECIAL, tag = "key-emoji", hapticDown = HapticKind.MODIFIER, onTap = { actions.page(KeyPage.EMOJI) }) {
            Icon(Icons.Rounded.EmojiEmotions, "Emoji", tint = palette.ink, modifier = Modifier.size(22.dp))
        }
        Key.Globe -> GlassKey(palette, modifier, KeyRole.SPECIAL, tag = "key-globe", hapticDown = HapticKind.MODIFIER, onTap = { actions.globe() },
            onLongPress = { actions.haptic(HapticKind.PREVIEW); actions.keyboardPicker() }) {
            Icon(Icons.Rounded.Language, "Switch keyboard", tint = palette.ink, modifier = Modifier.size(22.dp))
        }
        Key.Space -> SpaceKey(ui, actions, palette, modifier)
        Key.Enter -> {
            val accent = ui.enter.sendsAction || ui.emojiSearch != null
            GlassKey(palette, modifier, if (accent) KeyRole.ACTION else KeyRole.SPECIAL, tag = "key-enter", hapticDown = HapticKind.RETURN, onTap = { actions.enter() }) {
                if (ui.enter == EnterKind.RETURN && ui.emojiSearch == null && palette.classic) Text(ui.enter.label, color = palette.ink, fontSize = 16.sp)
                else if (ui.enter == EnterKind.RETURN && ui.emojiSearch == null) Icon(Icons.AutoMirrored.Rounded.KeyboardReturn, ui.enter.label, tint = palette.ink, modifier = Modifier.size(22.dp))
                else Text(if (ui.emojiSearch != null) stringResource(R.string.kb_done) else ui.enter.label, color = Color.White, fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
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
 * that has alternatives (its digit on the top row, accented letters, symbol variants) opens a strip; sliding to one and lifting swaps
 * the character just typed for it. Sliding across the letters instead swipes a word. Each key handles its own finger, so two fingers
 * can type at once; the key also reports where it is, for autocorrect's touch model and the swipe decoder.
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
    val flingSlop = with(density) { 22.dp.toPx() }
    val moveSlop = with(density) { 9.dp.toPx() }
    // The key's label changes with shift. Reading the latest through State keeps the touch handler alive across that change:
    // keying it on the label restarted every key's handler on the first keystroke after shift, dropping an overlapping tap.
    val current by rememberUpdatedState(key)
    val passwordField by rememberUpdatedState(ui.password)
    val showPreview = ui.keyPreviews && !ui.password
    val previews by rememberUpdatedState(showPreview)
    val swipeOn by rememberUpdatedState(ui.swipe && ui.page == KeyPage.LETTERS && ui.layout.english && ui.emojiSearch == null)
    val layout by rememberUpdatedState(ui.layout)
    val digitHint = if (ui.page == KeyPage.LETTERS && !ui.numberRow && key.label.length == 1) KeyLayouts.digitFor(ui.layout, key.label[0]) else null
    val lift by animateFloatAsState(if (pressed && !showPreview && !ui.classic) 1f else 0f, spring(dampingRatio = .55f, stiffness = 900f), label = "key-lift")
    Box(modifier) {
        GlassKey(palette, Modifier.fillMaxWidth().graphicsLayer { val s = 1f + .08f * lift; scaleX = s; scaleY = s }, KeyRole.LETTER,
            pressed = pressed, hidden = pressed && showPreview && !accenting,
            tag = "key-${key.label}", onTap = null,
            pointer = Modifier.onGloballyPositioned {
                val b = it.boundsInWindow(); bounds[0] = b
                if (ui.page == KeyPage.LETTERS && key.label.length == 1) {
                    ui.keyCentres[key.label.lowercase()[0]] = Pt(b.center.x, b.center.y); ui.keyWidth = b.width; ui.keyHeight = b.height
                }
            }.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val typed = current
                    val origin = bounds[0].topLeft
                    val downPt = Pt(origin.x + down.position.x, origin.y + down.position.y)
                    pressed = true
                    actions.haptic(HapticKind.LETTER)
                    actions.type(typed.output, downPt)
                    if (previews && typed.label.length == 1) ui.preview = KeyPreview(typed.label, bounds[0])
                    val letter = typed.label.length == 1 && typed.label[0].isLetter()
                    val digit = if (letter && ui.page == KeyPage.LETTERS && !ui.numberRow) KeyLayouts.digitFor(layout, typed.label[0]) else null
                    val options = if (typed.label.length == 1) listOfNotNull(digit) + KeyboardModel.alternates(typed.label) else emptyList()
                    val canSwipe = swipeOn && letter
                    val geometry = if (canSwipe) KeyGeometry(HashMap(ui.keyCentres), ui.keyWidth, ui.keyHeight) else KeyGeometry.EMPTY
                    val path = arrayListOf(downPt)
                    var swiping = false
                    var moved = false
                    var ended = false
                    var fling: String? = null
                    var lastKey: Char? = typed.label.lowercase().firstOrNull()
                    val start = System.currentTimeMillis()
                    while (true) {
                        // A key with alternatives that is held still opens them after a moment; a moving finger never does.
                        val wait = if (options.isNotEmpty() && !moved) 380L - (System.currentTimeMillis() - start) else 60_000L
                        if (wait <= 0L) break
                        val event = withTimeoutOrNull(wait) { awaitPointerEvent() } ?: break
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) { ended = true; break }        // released or cancelled
                        val p = Pt(origin.x + change.position.x, origin.y + change.position.y)
                        if (!moved && hypot(p.x - downPt.x, p.y - downPt.y) > moveSlop) moved = true
                        if (!moved) continue
                        if (canSwipe) {
                            path += p
                            if (!swiping && GestureDecoder.isSwipe(path, geometry)) {
                                swiping = true; fling = null
                                ui.preview = null; pressed = false
                                actions.swipeStart()
                                ui.trail.clear(); path.forEach { ui.trail.add(Offset(it.x, it.y)) }
                            }
                        }
                        if (swiping) {
                            ui.trail.add(Offset(p.x, p.y))
                            val over = geometry.near(p, .5f).firstOrNull()
                            if (over != null && over != lastKey) { lastKey = over; actions.haptic(HapticKind.SWIPE_KEY) }
                            if (path.size % 5 == 0) actions.swipeMove(path.toList())
                            change.consume(); continue
                        }
                        // A quick vertical flick on a letter turns it into a comma (down) or an apostrophe (up) instead.
                        if (letter) {
                            val dx = p.x - downPt.x; val dy = p.y - downPt.y
                            fling = if (abs(dy) > abs(dx) * 1.3f) KeyboardModel.flingSymbol(dy, flingSlop) else null
                            if (fling != null) change.consume()
                        }
                    }
                    if (swiping) {
                        actions.swipeEnd(path.toList())
                        ui.trail.clear(); ui.preview = null; pressed = false
                        return@awaitEachGesture
                    }
                    val symbol = fling
                    if (symbol != null) {
                        actions.replaceLast(symbol)                                  // the letter already typed becomes the punctuation
                        ui.preview = null; pressed = false
                        return@awaitEachGesture
                    }
                    if (ended || options.isEmpty() || moved) { ui.preview = null; pressed = false; return@awaitEachGesture }
                    // Held still past the delay with alternatives: open the strip, with a soft pop.
                    ui.preview = null
                    accenting = true
                    actions.haptic(HapticKind.PREVIEW)
                    var chosen = if (digit != null) 0 else -1
                    ui.accents = AccentState(options, chosen, bounds[0])
                    val layoutRect = accentLayout(bounds[0], ui.width, options.size, cell)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        val index = ((bounds[0].left + change.position.x - layoutRect.left) / cell).toInt().coerceIn(0, options.lastIndex)
                        if (index != chosen) { chosen = index; ui.accents = AccentState(options, chosen, bounds[0]); actions.haptic(HapticKind.SELECT) }
                        change.consume()
                        if (!change.pressed) break
                    }
                    options.getOrNull(chosen)?.let(actions::replaceLast)
                    ui.accents = null; accenting = false; pressed = false
                }
            }) {
            val hide = pressed && showPreview && !accenting
            Text(key.label, color = if (hide) Color.Transparent else palette.ink, fontSize = 23.sp, fontWeight = FontWeight.Normal)
            // Fossify's digit hints: the top row's digits, small in the corner, when there is no number row. Hidden in password fields too.
            if (digitHint != null && !passwordField) Text(digitHint, color = if (hide) Color.Transparent else palette.sub, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 4.dp))
        }
    }
}

private fun accentLayout(anchor: Rect, windowWidth: Float, count: Int, cell: Float): Rect {
    val width = count * cell
    val left = (anchor.center.x - width / 2f).coerceIn(4f, (windowWidth - width - 4f).coerceAtLeast(4f))
    return Rect(left, anchor.top - 60f, left + width, anchor.top - 8f)
}

/** The magnified key: a glass balloon rising out of the key, like iOS's. It sits in the lane above the top row. */
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
    val r = with(density) { (if (palette.classic) 8.dp else 12.dp).toPx() }
    // matchParentSize, not fillMaxSize: the keyboard window wraps its content, and a child that fills a wrapping window makes it as
    // tall as the screen, which Android reads as a keyboard covering everything and hides it the moment a key is held.
    Box(Modifier.matchParentSize().drawBehind {
        // The balloon: a head wider than the key, a neck, and the key itself, in one solid glass colour (a preview over see-through keys
        // must not let the key under it show), with a soft shadow and the bright rim on top.
        val solid = palette.balloon
        fun shape(color: Color, dy: Float) {
            drawRoundRect(color, Offset(headLeft, headTop + dy), Size(headW, headH), CornerRadius(r))
            drawRect(color, Offset(key.left, headTop + headH - r + dy), Size(key.width, key.height * .5f + r))
            drawRoundRect(color, Offset(key.left, key.top + dy), Size(key.width, key.height), CornerRadius(r * .75f))
        }
        shape(palette.shadow, if (palette.classic) 1.dp.toPx() else 2.dp.toPx()); shape(solid, 0f)
        if (!palette.classic) drawRoundRect(Brush.verticalGradient(listOf(palette.rimTop, Color.Transparent), startY = headTop, endY = headTop + headH * .5f),
            Offset(headLeft, headTop), Size(headW, headH), CornerRadius(r), style = Stroke(1.dp.toPx()))
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
    val solid = palette.balloon
    Box(Modifier.matchParentSize()) {
        Row(Modifier.offset { IntOffset((layout.left - o.x).toInt(), top.toInt()) }.height(52.dp)
            .drawBehind {
                drawRoundRect(palette.shadow, Offset(0f, 2.dp.toPx()), size, CornerRadius(12.dp.toPx()))
                drawRoundRect(solid, cornerRadius = CornerRadius(12.dp.toPx()))
            }.testTag("accent-strip")) {
            a.options.forEachIndexed { i, text ->
                Box(Modifier.size(width = 40.dp, height = 52.dp).then(if (i == a.index) Modifier.background(palette.action, RoundedCornerShape(10.dp)) else Modifier),
                    contentAlignment = Alignment.Center) { Text(text, color = if (i == a.index) Color.White else palette.ink, fontSize = 24.sp) }
            }
        }
    }
}

/** The trail a swiping finger leaves: a ribbon in the accent colour with a bright core, thinning and fading toward its tail. */
@Composable
private fun BoxScope.TrailLayer(ui: KeyboardUiState, palette: KeyPalette, origin: State<Offset>) {
    if (ui.trail.isEmpty()) return
    Box(Modifier.matchParentSize().drawBehind {
        val o = origin.value
        val pts = ui.trail.takeLast(48)
        val n = pts.size
        if (n < 2) return@drawBehind
        val wide = 9.dp.toPx(); val core = 2.5.dp.toPx()
        for (i in 1 until n) {
            val t = i / (n - 1f)
            val a = pts[i - 1] - o; val b = pts[i] - o
            drawLine(palette.action.copy(alpha = .18f + .42f * t), a, b, strokeWidth = wide * (.35f + .65f * t), cap = StrokeCap.Round)
            drawLine(Color.White.copy(alpha = .25f + .6f * t), a, b, strokeWidth = core * (.4f + .6f * t), cap = StrokeCap.Round)
        }
    }.testTag("swipe-trail"))
}

/** Backspace: acts once on press, then repeats while held, faster the longer it is held and taking whole words after a while. */
@Composable
private fun RepeatingKey(palette: KeyPalette, modifier: Modifier, onHaptic: (HapticKind) -> Unit, onStep: (Int) -> Unit,
    role: KeyRole = KeyRole.SPECIAL, tag: String = "key-backspace", firstHaptic: HapticKind = HapticKind.DELETE,
    repeatHaptic: HapticKind = HapticKind.DELETE_REPEAT, icon: (@Composable (pressed: Boolean) -> Unit)? = null) {
    var pressed by remember { mutableStateOf(false) }
    GlassKey(palette, modifier, role, pressed = pressed, tag = tag, onTap = null,
        pointer = Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true; onHaptic(firstHaptic); onStep(0)
                // Held long enough, it repeats, faster the longer it is held; released or cancelled, it stops.
                var held = 0
                while (true) {
                    val up = withTimeoutOrNull(if (held == 0) 420L else if (held < 8) 90L else 45L) { waitForUpOrCancellation() }
                    if (up != null || !currentEvent.changes.any { it.pressed }) break
                    held++
                    onHaptic(repeatHaptic); onStep(held)
                }
                pressed = false
            }
        }) {
        if (icon != null) icon(pressed)
        else Icon(if (pressed) Icons.AutoMirrored.Rounded.Backspace else Icons.AutoMirrored.Outlined.Backspace, "Delete", tint = palette.ink, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun GlassKey(palette: KeyPalette, modifier: Modifier, role: KeyRole, tag: String, onTap: (() -> Unit)?,
    pressed: Boolean = false, hidden: Boolean = false, pointer: Modifier = Modifier, hapticDown: HapticKind? = null,
    onLongPress: (() -> Unit)? = null, height: Dp = LocalKeyHeight.current,
    content: @Composable BoxScope.() -> Unit) {
    val actions0 = LocalKeyboardActions.current
    var down by remember { mutableStateOf(false) }
    val longPress by rememberUpdatedState(onLongPress)
    val tapModifier = if (onTap != null) Modifier.pointerInput(onTap) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false); down = true
            hapticDown?.let { actions0?.haptic(it) }
            val held = longPress
            if (held != null) {
                val up = withTimeoutOrNull(450L) { waitForUpOrCancellation() }
                if (up == null && currentEvent.changes.any { it.pressed }) { held(); waitForUpOrCancellation(); down = false; return@awaitEachGesture }
                down = false
                if (up != null) onTap()
                return@awaitEachGesture
            }
            val up = waitForUpOrCancellation(); down = false
            if (up != null) onTap()
        }
    } else Modifier
    Box(modifier.height(height).drawBehind {
        if (!hidden) drawKey(palette, role, pressed || down, palette.keyRadius.toPx())
    }.then(tapModifier).then(pointer).testTag(tag), contentAlignment = Alignment.Center, content = content)
}

/** A glass key: a soft shadow beneath, a translucent body brightening toward its top, and a specular rim, brightest along the top edge
 * where light catches it. A pressed key glows.
 */
private fun DrawScope.drawKey(palette: KeyPalette, role: KeyRole, pressed: Boolean, radius: Float) {
    val corner = CornerRadius(radius)
    if (palette.classic) {
        // Uno 0.97's key: flat, with a thin darker line under it, as on iOS before glass. Special keys turn light while pressed.
        val body = when (role) {
            KeyRole.ACTION -> if (pressed) palette.action.copy(alpha = .8f) else palette.action
            KeyRole.LETTER -> palette.letter
            KeyRole.SPECIAL -> if (pressed) palette.specialPressed else palette.special
        }
        val line = 1.dp.toPx()
        drawRoundRect(palette.shadow, Offset(0f, line), Size(size.width, size.height - line), corner)
        drawRoundRect(body, Offset.Zero, Size(size.width, size.height - line), corner)
        return
    }
    val body = when (role) {
        KeyRole.ACTION -> palette.action
        KeyRole.LETTER -> palette.letter
        KeyRole.SPECIAL -> palette.special
    }
    val lift = 1.dp.toPx()
    drawRoundRect(palette.shadow, Offset(0f, lift), Size(size.width, size.height - lift), corner)
    val h = size.height - lift
    drawRoundRect(Brush.verticalGradient(listOf(lerpWhite(body, .10f), body), endY = h), Offset.Zero, Size(size.width, h), corner)
    if (pressed) drawRoundRect(if (role == KeyRole.ACTION) Color(0x33000000) else palette.pressedGlow, Offset.Zero, Size(size.width, h), corner)
    val inset = .5.dp.toPx()
    drawRoundRect(Brush.verticalGradient(0f to palette.rimTop, .45f to Color.Transparent, 1f to palette.rimBottom, startY = 0f, endY = h),
        Offset(inset, inset), Size(size.width - 2 * inset, h - 2 * inset), CornerRadius(radius - inset), style = Stroke(1.dp.toPx()))
}

private fun lerpWhite(c: Color, t: Float) = Color(c.red + (1f - c.red) * t, c.green + (1f - c.green) * t, c.blue + (1f - c.blue) * t, c.alpha)

/** The space bar. A tap types a space; sliding a finger along it moves the cursor, a character for each few millimetres
 * (like FlorisBoard's space-bar cursor control), which is the quick way to fix a typo in the middle of a sentence. With more than one
 * layout on, it names the one in use, as Fossify Keyboard's does.
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
                var moved: Float; var emitted = 0; var dragging = false
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    moved = change.position.x - down.position.x
                    if (ui.spaceCursor && !dragging && abs(moved) > slop) { dragging = true; sliding = true }
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
        val label = when {
            sliding -> stringResource(R.string.kb_cursor)
            ui.layoutCount > 1 -> ui.layout.name
            else -> stringResource(R.string.kb_space)
        }
        Text(label, color = palette.ink.copy(alpha = if (sliding || ui.layoutCount > 1) .6f else 1f), fontSize = if (ui.layoutCount > 1) 14.sp else 16.sp, maxLines = 1)
    }
}

/** A round glass button for the lane: the tools, Paste, the emoji search's close. */
@Composable
private fun LaneButton(icon: ImageVector, description: String, palette: KeyPalette, tag: String, actions: KeyboardActions, active: Boolean = false,
    onClick: () -> Unit) {
    Box(Modifier.size(LaneHeight).padding(3.dp).clip(RoundedCornerShape(50))
        .background(if (active) palette.action.copy(alpha = .9f) else palette.pill)
        .pointerInput(onClick) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false); val up = waitForUpOrCancellation(); if (up != null) { actions.haptic(HapticKind.MODIFIER); onClick() } } }
        .testTag(tag), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = if (active) Color.White else palette.ink, modifier = Modifier.size(20.dp))
    }
}

/** The lane above the keys. Normally: a tools button, three suggestion slots and a Paste button when something is on the clipboard.
 * When a correction is coming it sits in the middle in a glass pill and is what the space bar will apply. The tools button swaps the
 * suggestions for the tools (clipboard, text editing, layouts, one-handed, voice, settings); the autofill service's chips take the
 * suggestions' place while it has some; and an emoji search shows its query and results here.
 */
@Composable
private fun SuggestionLane(ui: KeyboardUiState, palette: KeyPalette, actions: KeyboardActions) {
    Row(Modifier.fillMaxWidth().height(LaneHeight).testTag("suggestion-strip"), verticalAlignment = Alignment.CenterVertically) {
        val search = ui.emojiSearch
        when {
            search != null -> EmojiSearchLane(search, ui, palette, actions)
            ui.tools || ui.panel != KeyPanel.NONE -> ToolsLane(ui, palette, actions)
            else -> {
                LaneButton(Icons.Rounded.Apps, "Tools", palette, "tools-button", actions) { actions.tools(true) }
                if (ui.inline.isNotEmpty()) InlineChips(ui.inline, Modifier.weight(1f))
                else SuggestionSlots(ui, palette, actions, Modifier.weight(1f))
                if (ui.pasteAvailable) LaneButton(Icons.Rounded.ContentPaste, "Paste", palette, "paste-button", actions) { actions.paste() }
            }
        }
    }
}

@Composable
private fun SuggestionSlots(ui: KeyboardUiState, palette: KeyPalette, actions: KeyboardActions, modifier: Modifier) {
    // Always three slots, so the keys below never change height while you type.
    val suggestions = if (ui.showStrip) ui.suggestions else emptyList()
    val slots = suggestions.take(3)
    Row(modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val s = slots.getOrNull(index)
            val highlight = s != null && (s.kind == SuggestionKind.CORRECTION || (s.kind == SuggestionKind.GESTURE && index == 0 && ui.swiping))
            Box(Modifier.weight(1f).fillMaxHeight().padding(vertical = 3.dp, horizontal = 2.dp)
                .then(if (highlight) Modifier.clip(RoundedCornerShape(50)).background(palette.pill) else Modifier)
                .then(if (s != null) Modifier.pointerInput(s) {
                    awaitEachGesture { awaitFirstDown(requireUnconsumed = false); val up = waitForUpOrCancellation(); if (up != null) { actions.haptic(HapticKind.MODIFIER); actions.pick(s) } }
                } else Modifier).testTag("suggestion-$index"), contentAlignment = Alignment.Center) {
                if (s != null) when (s.kind) {
                    SuggestionKind.EMOJI -> Text(s.text, fontSize = 22.sp)
                    SuggestionKind.CLIP -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Icon(Icons.Rounded.ContentPaste, "Paste copied text", tint = palette.sub, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(ClipHistory.preview(s.text, 24), color = palette.ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    else -> {
                        val quoted = (s.kind == SuggestionKind.TYPED && slots.any { it.kind == SuggestionKind.CORRECTION }) || s.kind == SuggestionKind.REVERT
                        Text(if (quoted) "“${s.text}”" else s.text, color = palette.ink, fontSize = 17.sp,
                            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
                    }
                }
            }
            if (index < 2 && !highlight && slots.getOrNull(index + 1)?.kind != SuggestionKind.CORRECTION)
                Box(Modifier.width(1.dp).height(20.dp).background(palette.divider))
        }
    }
}

/** The autofill service's chips (a saved login, a card, an address), scrolling sideways. They are the service's own views. */
@Composable
private fun InlineChips(views: List<android.view.View>, modifier: Modifier) {
    LazyRow(modifier.fillMaxHeight().testTag("autofill-chips"), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 4.dp)) {
        items(views.size) { i ->
            AndroidView(factory = { ctx -> android.widget.FrameLayout(ctx) }, update = { frame ->
                val v = views[i]
                if (v.parent !== frame) { (v.parent as? android.view.ViewGroup)?.removeView(v); frame.removeAllViews(); frame.addView(v) }
            })
        }
    }
}

@Composable
private fun RowScope.ToolsLane(ui: KeyboardUiState, palette: KeyPalette, actions: KeyboardActions) {
    LaneButton(if (ui.panel != KeyPanel.NONE) Icons.Rounded.Keyboard else Icons.Rounded.Close, "Back to the keys", palette, "tools-close", actions) {
        if (ui.panel != KeyPanel.NONE) actions.panel(KeyPanel.NONE) else actions.tools(false)
    }
    Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        LaneButton(Icons.Rounded.ContentPasteSearch, "Clipboard", palette, "tool-clipboard", actions, active = ui.panel == KeyPanel.CLIPBOARD) { actions.panel(KeyPanel.CLIPBOARD) }
        LaneButton(Icons.Rounded.TextFields, "Text editing", palette, "tool-edit", actions, active = ui.panel == KeyPanel.EDIT) { actions.panel(KeyPanel.EDIT) }
        if (ui.layoutCount > 1) LaneButton(Icons.Rounded.Language, "Layouts", palette, "tool-layouts", actions, active = ui.panel == KeyPanel.LAYOUTS) { actions.panel(KeyPanel.LAYOUTS) }
        LaneButton(if (ui.oneHanded == 0) Icons.Rounded.KeyboardDoubleArrowLeft else Icons.Rounded.KeyboardDoubleArrowRight, "One-handed", palette, "tool-one-handed",
            actions, active = ui.oneHanded != 0) { actions.setOneHanded(when (ui.oneHanded) { 0 -> 1; 1 -> 2; else -> 0 }) }
        if (ui.voice) LaneButton(Icons.Rounded.Mic, "Voice typing", palette, "tool-voice", actions) { actions.voice() }
        LaneButton(Icons.Rounded.Settings, "Keyboard settings", palette, "tool-settings", actions) { actions.openSettings() }
    }
}

@Composable
private fun RowScope.EmojiSearchLane(query: String, ui: KeyboardUiState, palette: KeyPalette, actions: KeyboardActions) {
    LaneButton(Icons.Rounded.Close, "Close emoji search", palette, "emoji-search-close", actions) { actions.emojiSearch(false) }
    Row(Modifier.width(120.dp).fillMaxHeight().padding(vertical = 4.dp).clip(RoundedCornerShape(50)).background(palette.pill).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Search, null, tint = palette.sub, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(query.ifEmpty { stringResource(R.string.kb_search_emoji) }, color = if (query.isEmpty()) palette.sub else palette.ink, fontSize = 15.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("emoji-search-query"))
    }
    val results = remember(query, ui.emojiIndex) { ui.emojiIndex?.search(query).orEmpty() }
    LazyRow(Modifier.weight(1f).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
        items(results) { e ->
            Box(Modifier.size(LaneHeight).pointerInput(e) { detectTapGestures { actions.haptic(HapticKind.LETTER); actions.emoji(e) } }, contentAlignment = Alignment.Center) {
                Text(e, fontSize = 24.sp)
            }
        }
    }
}

/** A labelled glass button for the panels. */
@Composable
private fun PanelButton(label: String, icon: ImageVector?, palette: KeyPalette, tag: String, modifier: Modifier, role: KeyRole = KeyRole.SPECIAL,
    height: Dp, onClick: () -> Unit) {
    GlassKey(palette, modifier, role, tag = tag, hapticDown = HapticKind.MODIFIER, onTap = onClick, height = height) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) Icon(icon, null, tint = if (role == KeyRole.ACTION) Color.White else palette.ink, modifier = Modifier.size(19.dp))
            if (icon != null && label.isNotEmpty()) Spacer(Modifier.width(6.dp))
            if (label.isNotEmpty()) Text(label, color = if (role == KeyRole.ACTION) Color.White else palette.ink, fontSize = 15.sp, maxLines = 1)
        }
    }
}

/** The clipboard history, as Fossify Keyboard has it: pinned clips first, then what was copied in the last hour. Tap one to type it;
 * the pin keeps it, the cross forgets it.
 */
@Composable
private fun ClipboardPanel(ui: KeyboardUiState, actions: KeyboardActions, palette: KeyPalette, height: Dp) {
    Column(Modifier.fillMaxWidth().height(height).testTag("clipboard-panel")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.kb_clipboard), color = palette.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (ui.clips.any { !it.pinned }) Text(stringResource(R.string.kb_clear_recent), color = palette.action, fontSize = 14.sp,
                modifier = Modifier.clickable { actions.haptic(HapticKind.MODIFIER); actions.clearClips() }.padding(6.dp).testTag("clipboard-clear"))
        }
        if (ui.clips.isEmpty()) Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.kb_clipboard_empty), color = palette.sub, fontSize = 14.sp, textAlign = TextAlign.Center)
        } else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 4.dp)) {
            items(ui.clips, key = { it.text }) { clip ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).drawBehind { drawKey(palette, KeyRole.LETTER, false, 12.dp.toPx()) }
                    .clickable { actions.haptic(HapticKind.MODIFIER); actions.pasteClip(clip.text) }.padding(start = 12.dp).testTag("clip"),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(ClipHistory.preview(clip.text, 120), color = palette.ink, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                    Icon(if (clip.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPinOutline, if (clip.pinned) "Unpin" else "Pin",
                        tint = if (clip.pinned) palette.action else palette.sub,
                        modifier = Modifier.size(40.dp).clickable { actions.haptic(HapticKind.SELECT); actions.pinClip(clip.text, !clip.pinned) }.padding(10.dp))
                    Icon(Icons.Rounded.Close, "Forget", tint = palette.sub,
                        modifier = Modifier.size(40.dp).clickable { actions.haptic(HapticKind.SELECT); actions.removeClip(clip.text) }.padding(10.dp))
                }
            }
        }
    }
}

/** Text editing without reaching for the field: arrows (held, they repeat), start and end of line, select, select all, copy, cut,
 * paste, undo and redo, in the keys' place.
 */
@Composable
private fun EditPanel(ui: KeyboardUiState, actions: KeyboardActions, palette: KeyPalette, height: Dp) {
    val gap = 8.dp
    val rowH = (height - gap * 3) / 4
    @Composable fun arrow(op: EditOp, icon: ImageVector, description: String, modifier: Modifier) =
        RepeatingKey(palette, modifier.height(rowH), onHaptic = actions::haptic, onStep = { actions.edit(op) }, tag = "edit-${op.name.lowercase()}",
            firstHaptic = HapticKind.MODIFIER, repeatHaptic = HapticKind.CURSOR) { _ -> Icon(icon, description, tint = palette.ink, modifier = Modifier.size(24.dp)) }
    Row(Modifier.fillMaxWidth().height(height).testTag("edit-panel"), horizontalArrangement = Arrangement.spacedBy(gap)) {
        Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                PanelButton(stringResource(R.string.kb_start), Icons.Rounded.FirstPage, palette, "edit-home", Modifier.weight(1f), height = rowH) { actions.edit(EditOp.HOME) }
                arrow(EditOp.UP, Icons.Rounded.KeyboardArrowUp, "Up", Modifier.weight(1f))
                PanelButton(stringResource(R.string.kb_end), Icons.Rounded.LastPage, palette, "edit-end", Modifier.weight(1f), height = rowH) { actions.edit(EditOp.END) }
            }
            Row(Modifier.height(rowH * 2 + gap), horizontalArrangement = Arrangement.spacedBy(gap)) {
                arrow(EditOp.LEFT, Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Left", Modifier.weight(1f).height(rowH * 2 + gap))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                    PanelButton(stringResource(R.string.kb_select), Icons.Rounded.HighlightAlt, palette, "edit-select", Modifier.fillMaxWidth(),
                        role = if (ui.selecting) KeyRole.ACTION else KeyRole.SPECIAL, height = rowH) { actions.edit(EditOp.SELECT) }
                    arrow(EditOp.DOWN, Icons.Rounded.KeyboardArrowDown, "Down", Modifier.fillMaxWidth())
                }
                arrow(EditOp.RIGHT, Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Right", Modifier.weight(1f).height(rowH * 2 + gap))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                PanelButton(stringResource(R.string.kb_undo), Icons.AutoMirrored.Rounded.Undo, palette, "edit-undo", Modifier.weight(1f), height = rowH) { actions.edit(EditOp.UNDO) }
                PanelButton(stringResource(R.string.kb_redo), Icons.AutoMirrored.Rounded.Redo, palette, "edit-redo", Modifier.weight(1f), height = rowH) { actions.edit(EditOp.REDO) }
            }
        }
        Column(Modifier.weight(1.6f), verticalArrangement = Arrangement.spacedBy(gap)) {
            PanelButton(stringResource(R.string.kb_select_all), Icons.Rounded.SelectAll, palette, "edit-select-all", Modifier.fillMaxWidth(), height = rowH) { actions.edit(EditOp.SELECT_ALL) }
            PanelButton(stringResource(R.string.kb_copy), Icons.Rounded.ContentCopy, palette, "edit-copy", Modifier.fillMaxWidth(), height = rowH) { actions.edit(EditOp.COPY) }
            PanelButton(stringResource(R.string.kb_cut), Icons.Rounded.ContentCut, palette, "edit-cut", Modifier.fillMaxWidth(), height = rowH) { actions.edit(EditOp.CUT) }
            PanelButton(stringResource(R.string.kb_paste), Icons.Rounded.ContentPaste, palette, "edit-paste", Modifier.fillMaxWidth(), height = rowH) { actions.edit(EditOp.PASTE) }
        }
    }
}

/** The layouts turned on in Settings, to pick one directly (the globe key goes round them in turn), and Android's keyboard list. */
@Composable
private fun LayoutPanel(ui: KeyboardUiState, actions: KeyboardActions, palette: KeyPalette, height: Dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val enabled = remember(ui.layoutCount) {
        KeyLayouts.parse(context.getSharedPreferences("extras", android.content.Context.MODE_PRIVATE).getString("kbLayouts", null))
    }
    LazyColumn(Modifier.fillMaxWidth().height(height).testTag("layout-panel"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(enabled, key = { it.id }) { l ->
            PanelButton(l.name, if (l == ui.layout) Icons.Rounded.Check else null, palette, "layout-${l.id}", Modifier.fillMaxWidth(),
                role = if (l == ui.layout) KeyRole.LETTER else KeyRole.SPECIAL, height = 44.dp) { actions.chooseLayout(l.id) }
        }
        item {
            PanelButton(stringResource(R.string.kb_other_keyboards), Icons.Rounded.Keyboard, palette, "layout-system", Modifier.fillMaxWidth(), height = 44.dp) { actions.keyboardPicker() }
        }
    }
}

/** The emoji panel: a search pill, a scrolling grid of the chosen category, and the category tabs along the bottom beside the ABC and
 * delete keys, as on iOS. Holding an emoji that takes a skin tone offers the five tones. It is exactly as tall as the keys it replaces,
 * so the keyboard does not change size.
 */
@Composable
private fun EmojiPanel(ui: KeyboardUiState, actions: KeyboardActions, palette: KeyPalette, keyHeight: Dp, extra: Dp) {
    val categories = ui.emoji
    val hasRecents = ui.recents.isNotEmpty()
    var selected by remember { mutableIntStateOf(if (hasRecents) -1 else 0) }
    var toning by remember { mutableStateOf<String?>(null) }
    val tab = if (selected == -1 && !hasRecents) 0 else selected
    val items = if (tab == -1) ui.recents else categories.getOrNull(tab)?.items.orEmpty()
    // The emoji page has no suggestion lane, so the grid takes the lane's height as well and the keyboard stays the same size.
    val gridHeight = keyHeight * 3 + RowGap * 2 + extra
    val searchH = 34.dp
    Column(Modifier.fillMaxWidth().testTag("emoji-panel"), verticalArrangement = Arrangement.spacedBy(RowGap)) {
        Box(Modifier.fillMaxWidth().height(gridHeight)) {
            Column {
                Row(Modifier.fillMaxWidth().height(searchH).padding(horizontal = 4.dp).clip(RoundedCornerShape(50)).background(palette.pill)
                    .clickable { actions.haptic(HapticKind.MODIFIER); actions.emojiSearch(true) }.padding(horizontal = 12.dp).testTag("emoji-search"),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, tint = palette.sub, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.kb_search_emoji), color = palette.sub, fontSize = 15.sp)
                }
                LazyVerticalGrid(GridCells.Fixed(8), Modifier.fillMaxWidth().weight(1f)) {
                    items(items, key = { it }) { e ->
                        val toneable = ui.emojiIndex?.toneable(e) == true
                        Box(Modifier.height(46.dp).pointerInput(e, toneable) {
                            detectTapGestures(onLongPress = { if (toneable) { actions.haptic(HapticKind.PREVIEW); toning = e } }) { actions.haptic(HapticKind.LETTER); actions.emoji(e) }
                        }, contentAlignment = Alignment.Center) { Text(e, fontSize = 27.sp) }
                    }
                }
            }
            val base = toning
            if (base != null) Row(Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp))
                .drawBehind { drawRoundRect(if (palette.dark) Color(0xFF3A3A3D) else Color.White, cornerRadius = CornerRadius(16.dp.toPx())) }
                .padding(6.dp).testTag("emoji-tones")) {
                (listOf(base) + EmojiIndex.TONES.map { EmojiIndex.withTone(base, it) }).forEach { v ->
                    Box(Modifier.size(46.dp).clickable { actions.haptic(HapticKind.SELECT); actions.emoji(v); toning = null }, contentAlignment = Alignment.Center) {
                        Text(v, fontSize = 28.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyGap), verticalAlignment = Alignment.CenterVertically) {
            GlassKey(palette, Modifier.weight(1.5f), KeyRole.SPECIAL, tag = "key-page", hapticDown = HapticKind.MODIFIER, onTap = { actions.page(KeyPage.LETTERS) }) {
                Text(stringResource(R.string.abc), color = palette.ink, fontSize = 16.sp)
            }
            Row(Modifier.weight(7f).height(keyHeight), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                val tabs = (if (hasRecents) listOf(-1 to "🕒") else emptyList()) + categories.mapIndexed { i, c -> i to c.tab }
                tabs.forEach { (index, glyph) ->
                    Box(Modifier.weight(1f).fillMaxHeight().pointerInput(index) { detectTapGestures { actions.haptic(HapticKind.MODIFIER); selected = index; toning = null } }
                        .testTag("emoji-tab-$index"), contentAlignment = Alignment.Center) {
                        if (index == tab) Box(Modifier.size(32.dp).clip(RoundedCornerShape(50)).background(palette.pill))
                        Text(glyph, fontSize = 19.sp, modifier = Modifier.then(if (index == tab) Modifier else Modifier.graphicsLayer { alpha = .55f }))
                    }
                }
            }
            RepeatingKey(palette, Modifier.weight(1.5f), onHaptic = actions::haptic, onStep = { held -> actions.backspace(held) })
        }
    }
}
