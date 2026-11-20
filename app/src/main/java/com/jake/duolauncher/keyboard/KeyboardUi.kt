package com.jake.duolauncher.keyboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.shape.CornerSize
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.translate
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
import com.jake.duolauncher.appearance.ContinuousShape
import com.jake.duolauncher.appearance.Corner
import com.jake.duolauncher.appearance.DuoTypography
import com.jake.duolauncher.appearance.continuousPath
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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
private class KeyPalette(val dark: Boolean, accent: Int?, blurred: Boolean, val classic: Boolean = false, val rim: Boolean = true) {
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
    /** The launcher's one accent: the wallpaper's colour when that is on, Uno orange otherwise (it was iOS's blue, the only blue in Uno). */
    val action = accent?.let { Color(it) } ?: com.jake.duolauncher.panel.IosOrange
    val sub = ink.copy(alpha = .55f)
    val pill = if (classic) ink.copy(alpha = .08f) else if (dark) Color(0x2EFFFFFF) else Color(0xA6FFFFFF)
    val divider = ink.copy(alpha = if (classic) .18f else .14f)
    /** The solid colour of the magnified key and the accent strip. */
    val balloon = if (classic) letter else if (dark) Color(0xFF6E6E73) else Color.White
    /** iOS 26's keys are rounder than the flat keys before them; both have the launcher's continuous corner. */
    val keyRadius = if (classic) 6.dp else 10.dp
    val rowGap = if (classic) 10.dp else 11.dp
}

private val LocalKeyboardActions = staticCompositionLocalOf<KeyboardActions?> { null }
private val LocalKeyHeight = staticCompositionLocalOf { 46.dp }
/** The order of overlapping fingers (see [KeyTouches]); the keyboard's own, shared by every key on it. */
private val LocalKeyTouches = staticCompositionLocalOf { KeyTouches() }

private val KeyGap = 6.dp
/** The gap between rows; the classic style's was a point tighter. Provided with the palette. */
private val LocalRowGap = staticCompositionLocalOf { 11.dp }
private val RowGap: Dp @Composable get() = LocalRowGap.current
/** Room under the bottom row. Android's own keyboard-switcher button sits in the navigation bar just below the Return key on phones that
 * show it, and a thumb aiming at the bottom row landed on it; the extra room keeps the two apart. 20 dp was still too close in use. */
private val BottomGap = 32.dp
private val LaneHeight = 40.dp
/** The widest the keys are allowed to spread: on an unfolded or tablet screen they stay thumb-sized instead of stretching. */
private val MaxKeyboardWidth = 640.dp

/** The keyboard. [inSettings] draws it for the live preview on the settings page: no navigation-bar room, the same look. */
@Composable
internal fun KeyboardScreen(ui: KeyboardUiState, actions: KeyboardActions, inSettings: Boolean = false) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val config = LocalConfiguration.current
    val palette = remember(ui.accent, context, config.uiMode, ui.blurred, ui.classic, ui.edgeHighlight) { KeyPalette(ui.dark(context), ui.accent, ui.blurred && !ui.classic, ui.classic, ui.edgeHighlight) }
    // Turned sideways the keys are shorter, so the keyboard does not cover the whole screen; the height setting scales both.
    val base = if (config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 36.dp else 46.dp
    val keyHeight = base * ui.heightScale
    // Rows are rebuilt only when the page, shift, layout or a key setting changes, not on every suggestion update.
    val rows = remember(ui.page, ui.shift, ui.numberRow, ui.globe, ui.layout, ui.emojiKey, ui.field) {
        KeyboardModel.rows(ui.page, ui.shift, ui.numberRow, ui.globe, ui.layout, ui.emojiKey, ui.field)
    }
    // The classic panel is square-topped, as it was; the glass one has rounded corners the window's blur follows.
    val corner = if (ui.classic) 0.dp else PANEL_CORNER_DP.dp
    androidx.compose.material3.MaterialTheme(typography = DuoTypography) {
        CompositionLocalProvider(LocalKeyboardActions provides actions, LocalKeyHeight provides keyHeight, LocalRowGap provides palette.rowGap,
            LocalKeyTouches provides ui.touches) {
            BoxWithConstraints(Modifier.fillMaxWidth()
                .clip(remember(corner, inSettings) { val bottom = if (inSettings) corner else 0.dp
                    ContinuousShape(CornerSize(corner), CornerSize(corner), CornerSize(bottom), CornerSize(bottom)) })
                .drawWithCache {
                    // The glass edge: a bright hairline along the top, fading down the rounded corners; the classic one is a plain line.
                    val r = corner.toPx()
                    val edge = continuousPath(0f, 0f, size.width, size.height + r, r)
                    val panel = Brush.verticalGradient(listOf(palette.panelTop, palette.panelBottom))
                    val rim = Brush.verticalGradient(0f to palette.panelRim, .12f to Color.Transparent)
                    onDrawBehind {
                        drawRect(panel)
                        if (palette.classic) drawRect(palette.panelRim, size = Size(size.width, 1f))
                        else if (palette.rim) drawPath(edge, rim, style = Stroke(1.dp.toPx()))
                    }
                }
                .onGloballyPositioned { ui.width = it.size.width.toFloat(); ui.root = it }) {
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
                        Box(Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { actions.haptic(HapticKind.MODIFIER); actions.setOneHanded(0) }
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
                TrailLayer(ui, palette)
                PreviewLayer(ui, palette)
                AccentLayer(ui, palette)
            }
        }
    }
}

/** How far a key's touch reaches beyond its face. The faces have gaps between them and a thumb does not: every point of the key area
 * belongs to the nearest key, so each key's touch runs to the middle of the gap around it, and the keys at the ends of a row run to
 * the edge of the panel (past the half-key inset of QWERTY's middle row, [leftFaces] and [rightFaces] being that inset in widths of
 * the key itself). Only where a touch counts changes; the faces are drawn where they were.
 */
@Immutable
private data class KeyReach(val left: Dp = 0.dp, val top: Dp = 0.dp, val right: Dp = 0.dp, val bottom: Dp = 0.dp, val leftFaces: Float = 0f, val rightFaces: Float = 0f) {
    companion object { val None = KeyReach() }
}

/** Lays what follows out larger than the key by [r], centred so the key's face is where it was. */
private fun Modifier.reach(r: KeyReach): Modifier = if (r == KeyReach.None) this else layout { measurable, c ->
    val w = c.maxWidth; val h = c.maxHeight
    val l = r.left.roundToPx() + (r.leftFaces * w).toInt(); val rt = r.right.roundToPx() + (r.rightFaces * w).toInt()
    val t = r.top.roundToPx(); val b = r.bottom.roundToPx()
    val p = measurable.measure(Constraints.fixed(w + l + rt, h + t + b))
    layout(w, h) { p.place(-l, -t) }
}

/** How far below the bottom row a touch still counts for it (the room under the keys is there to keep a thumb off the navigation bar). */
private val BottomReach = 10.dp
/** The shortest time a key's card stays up, so the quickest tap still shows which key it was. A later key's card replaces it at once. */
private const val CARD_MIN_MS = 70L
/** How long a key's pressed look takes to fade once the finger lifts: it comes on at once and leaves softly. */
private const val RELEASE_FADE_MS = 130

private val NotPressed: () -> Boolean = { false }

@Composable
private fun KeyRows(ui: KeyboardUiState, actions: KeyboardActions, palette: KeyPalette, rows: List<List<Key>>) {
    // Each page's keys are its own: a key is never reused as another page's key in the same place, so a finger still on a key when the
    // page changes (the page keys act as they are touched) is not left holding a different key.
    key(ui.page) {
        Column(verticalArrangement = Arrangement.spacedBy(RowGap)) {
            val rowOffset = if (ui.numberRow && ui.page == KeyPage.LETTERS) 1 else 0
            val widest = rows.drop(rowOffset).take(2).maxOfOrNull { r -> r.count { it is Key.Char } } ?: 10
            val half = RowGap / 2; val side = KeyGap / 2
            val edge = if (palette.classic) 3.dp else 4.dp
            rows.forEachIndexed { rawIndex, row ->
                val index = rawIndex - rowOffset
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                    // A shorter letter row is inset by half the difference on each side (the middle row of QWERTY by half a key), so every
                    // letter key is the same width, as on every phone keyboard.
                    val letters = row.count { it is Key.Char }
                    val inset = if (ui.page == KeyPage.LETTERS && index in 0..1) (widest - letters) / 2f else 0f
                    if (inset > 0f) Spacer(Modifier.weight(inset))
                    row.forEachIndexed { i, key ->
                        val weight = weightFor(key, ui, rawIndex, rows.size, widest, letters, row)
                        val first = i == 0; val last = i == row.lastIndex
                        val reach = KeyReach(
                            left = if (first) edge + (if (inset > 0f) KeyGap else 0.dp) else side,
                            right = if (last) edge + (if (inset > 0f) KeyGap else 0.dp) else side,
                            top = half, bottom = if (rawIndex == rows.lastIndex) BottomReach else half,
                            leftFaces = if (first) inset / weight else 0f, rightFaces = if (last) inset / weight else 0f)
                        KeyView(key, ui, actions, palette, Modifier.weight(weight), reach)
                    }
                    if (inset > 0f) Spacer(Modifier.weight(inset))
                }
            }
        }
    }
}

private fun weightFor(key: Key, ui: KeyboardUiState, row: Int, rowCount: Int, widest: Int, letters: Int, keys: List<Key>): Float = when (key) { // row counts from the top
    is Key.Char -> key.weight
    // Shift and Delete share what the third row's letters leave of the widest row, but never shrink below a key and a quarter.
    Key.Shift, Key.Backspace -> if (ui.page == KeyPage.LETTERS) ((widest - letters) / 2f).coerceAtLeast(1.25f) else 1.5f
    is Key.Page -> if (row == rowCount - 1) 1.4f else 1.5f
    Key.Globe -> 1.1f
    Key.Emoji -> 1.1f
    // The space bar takes what the bottom row's other keys leave: the emoji and globe keys, and an address field's punctuation.
    Key.Space -> 7.6f - keys.sumOf { k -> when (k) { Key.Globe, Key.Emoji -> 1.1; is Key.Char -> k.weight.toDouble(); else -> 0.0 } }.toFloat()
    Key.Enter -> 2.2f
}

@Composable
private fun KeyView(key: Key, ui: KeyboardUiState, actions: KeyboardActions, palette: KeyPalette, modifier: Modifier, reach: KeyReach) {
    when (key) {
        is Key.Char -> LetterKey(key, ui, actions, palette, modifier, reach)
        Key.Shift -> {
            val on = ui.shift != ShiftState.OFF
            // Shift acts as it is touched, and held it is a modifier: letters typed while it is down are capitals and it turns off
            // when it is let go. A shift that is already on for one letter (a new sentence) waits to see which it is: let go with
            // nothing typed it was a tap (off, or caps lock on a quick second tap), and with a letter typed it was a hold.
            val hold = remember { IntArray(2) }
            DownKey(palette, modifier, if (on) KeyRole.LETTER else KeyRole.SPECIAL, "key-shift", reach, HapticKind.MODIFIER,
                onDown = {
                    hold[0] = ui.touches.count
                    hold[1] = if (ui.shift != ShiftState.ONCE) 1 else 0
                    if (hold[1] == 1) actions.shift()
                    ui.shiftHeld = true
                },
                onUp = {
                    ui.shiftHeld = false
                    if (ui.touches.count != hold[0]) { if (ui.shift == ShiftState.ONCE) ui.shift = ShiftState.OFF }
                    else if (hold[1] == 0) actions.shift() else actions.shiftReleased()
                }) {
                ShiftGlyph(filled = on, color = palette.ink)
                if (ui.shift == ShiftState.LOCKED) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(width = 14.dp, height = 2.dp).background(palette.ink, Corner.pill))
            }
        }
        Key.Backspace -> RepeatingKey(palette, modifier, onHaptic = actions::haptic, onStep = { held -> actions.backspace(held) }, reach = reach)
        // The page keys change the keys as they are touched, like the keys that type: the next key is there a touch sooner.
        is Key.Page -> DownKey(palette, modifier, KeyRole.SPECIAL, "key-page", reach, HapticKind.MODIFIER, onDown = { actions.page(key.target) }) {
            Text(key.label, color = palette.ink, fontSize = 16.sp, fontWeight = FontWeight.Normal)
        }
        Key.Emoji -> GlassKey(palette, modifier, KeyRole.SPECIAL, tag = "key-emoji", hapticDown = HapticKind.MODIFIER, onTap = { actions.page(KeyPage.EMOJI) }, reach = reach) {
            Icon(Icons.Rounded.EmojiEmotions, "Emoji", tint = palette.ink, modifier = Modifier.size(22.dp))
        }
        Key.Globe -> GlassKey(palette, modifier, KeyRole.SPECIAL, tag = "key-globe", hapticDown = HapticKind.MODIFIER, onTap = { actions.globe() },
            onLongPress = { actions.haptic(HapticKind.PREVIEW); actions.keyboardPicker() }, reach = reach) {
            Icon(Icons.Rounded.Language, "Switch keyboard", tint = palette.ink, modifier = Modifier.size(22.dp))
        }
        Key.Space -> SpaceKey(ui, actions, palette, modifier, reach)
        Key.Enter -> {
            val accent = ui.enter.sendsAction || ui.emojiSearch != null
            GlassKey(palette, modifier, if (accent) KeyRole.ACTION else KeyRole.SPECIAL, tag = "key-enter", hapticDown = HapticKind.RETURN, onTap = { actions.enter() }, reach = reach,
                // A Return that sends, searches or goes is never fired by the next key landing: that calls it off. A plain new line is
                // typed in order, like the space.
                early = !ui.enter.sendsAction) {
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

/** Where a key's face is: the key keeps it up to date as it is laid out, and what draws over the keys (the card, the strip of
 * alternatives) asks it where the face is at the moment the key is touched, in the keyboard's own coordinates. It is also the key's
 * identity for the card: the key whose face this is hides under the card while it is up.
 */
internal class KeyFaceRef {
    var coords: LayoutCoordinates? = null
    /** The face in [root]'s coordinates, not cut by anything that clips, or null while either is not on screen. */
    fun rectIn(root: LayoutCoordinates?): Rect? {
        val c = coords ?: return null
        if (root == null || !c.isAttached || !root.isAttached) return null
        return root.localBoundingBoxOf(c, clipBounds = false)
    }
}

/** The card for a key whose face is [key] (in the keyboard's coordinates), or null where there is no room for one above it. */
private fun balloonFor(key: Rect, panelWidth: Float, density: Density): Balloon? = with(density) {
    KeyBalloon.place(key.left, key.top, key.right, panelWidth, full = 58.dp.toPx(), least = 34.dp.toPx(), grow = 20.dp.toPx(), margin = 2.dp.toPx())
}

/** A letter key. It types the moment the finger lands (not when it lifts), which is the fastest a key can respond, and
 * shows a magnified preview while held (never in password fields), the key itself hiding under it as on iOS. Holding a key
 * that has alternatives (its digit on the top row, accented letters, symbol variants) opens a strip; sliding to one and lifting swaps
 * the character just typed for it. Sliding across the letters instead swipes a word. Each key handles its own finger, so two fingers
 * can type at once; the key also reports where it is, for autocorrect's touch model and the swipe decoder.
 *
 * A tap that lands on the line between this key and the next may be read as the next (see [KeyboardActions.aim]); that key then
 * shows the card and is the one typed. Everything that amends the letter just typed (the flick to punctuation, the alternatives,
 * the start of a swipe) only happens while no other key has acted since, so with two fingers down neither edits the other's letter.
 */
@Composable
private fun LetterKey(key: Key.Char, ui: KeyboardUiState, actions: KeyboardActions, palette: KeyPalette, modifier: Modifier, reach: KeyReach) {
    val pressedState = remember { mutableStateOf(false) }
    var pressed by pressedState
    val face = remember { KeyFaceRef() }
    // Where touches are measured from: the key's touch area, which is larger than its face (see KeyReach). Its place is read as the
    // key is touched, with every key's centre (KeyboardUiState.refreshCentres), all in the keyboard view's own coordinates.
    val cell = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // Whether this key has its alternatives strip open (the strip itself is drawn by AccentLayer).
    val accentingState = remember { mutableStateOf(false) }
    var accenting by accentingState
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val cellWidth = with(density) { 40.dp.toPx() }
    val flingSlop = with(density) { 22.dp.toPx() }
    val moveSlop = with(density) { 9.dp.toPx() }
    // The key's label changes with shift. Reading the latest through State keeps the touch handler alive across that change:
    // keying it on the label restarted every key's handler on the first keystroke after shift, dropping an overlapping tap.
    val current by rememberUpdatedState(key)
    val passwordField by rememberUpdatedState(ui.password)
    val previews by rememberUpdatedState(ui.keyPreviews && !ui.password)
    val onLetters by rememberUpdatedState(ui.page == KeyPage.LETTERS)
    val swipeOn by rememberUpdatedState(ui.swipe && ui.page == KeyPage.LETTERS && ui.layout.english && ui.emojiSearch == null)
    val layout by rememberUpdatedState(ui.layout)
    val numberRow by rememberUpdatedState(ui.numberRow)
    val classic by rememberUpdatedState(ui.classic)
    val digitHint = if (ui.page == KeyPage.LETTERS && !ui.numberRow && key.label.length == 1) KeyLayouts.digitFor(ui.layout, key.label[0]) else null
    // The card is up for this key (its own touch, or its neighbour's that was meant for it): the key hides under it. Read where the
    // key is drawn, never where it is composed, so a key press redraws two keys and composes none.
    val carded = remember { derivedStateOf { ui.preview?.owner === face && !accentingState.value } }
    val held = remember { derivedStateOf { pressedState.value || ui.proxy === face } }
    // With no card (they are off, this is a password, or there is no room for one) the key itself rises a little instead.
    val lift = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        snapshotFlow { held.value && !carded.value && !classic }.collectLatest { lift.animateTo(if (it) 1f else 0f, spring(dampingRatio = .55f, stiffness = 900f)) }
    }
    val letterKey = key.label.length == 1 && key.label[0].isLetter()
    DisposableEffect(key.label, ui.page) {
        val ch = key.label.lowercase()[0]
        if (letterKey && ui.page == KeyPage.LETTERS) ui.faces[ch] = face
        onDispose { if (ui.faces[ch] === face) ui.faces.remove(ch) }
    }
    DisposableEffect(Unit) {
        // A key that goes away while it is held (the page changed under it) takes its card and its strip with it.
        onDispose {
            if (ui.preview?.owner === face) ui.preview = null
            if (ui.proxy === face) ui.proxy = null
            if (accentingState.value) ui.accents = null
        }
    }
    Box(modifier) {
        // The face's place is taken outside the layer that lifts the key, so it is the key at rest.
        GlassKey(palette, Modifier.onGloballyPositioned { face.coords = it }.fillMaxWidth().graphicsLayer { val s = 1f + .08f * lift.value; scaleX = s; scaleY = s }, KeyRole.LETTER,
            pressed = { held.value }, hidden = { carded.value },
            tag = "key-${key.label}", onTap = null, reach = reach,
            pointer = Modifier.onGloballyPositioned { cell[0] = it }.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    ui.touches.landed()
                    ui.refreshCentres()
                    val origin = cell[0]?.takeIf { it.isAttached }?.positionInRoot() ?: Offset.Zero
                    val rootX = ui.root?.takeIf { it.isAttached }?.positionInRoot()?.x ?: 0f
                    val downPt = Pt(origin.x + down.position.x, origin.y + down.position.y)
                    var typed = current
                    var target = face
                    val single = typed.label.length == 1
                    val letter = single && typed.label[0].isLetter()
                    if (letter && onLetters) {
                        val aimed = actions.aim(typed.label, downPt)
                        if (aimed != typed.label) ui.faces[aimed.lowercase()[0]]?.let { other -> target = other; typed = Key.Char(aimed) }
                    }
                    if (target === face) pressed = true else ui.proxy = target
                    actions.haptic(HapticKind.LETTER)
                    actions.type(typed.output, downPt)
                    ui.touches.acted()
                    val stamp = ui.touches.count
                    // Whether this is still the last key to have acted: only then is the last character typed this key's own.
                    fun alone() = ui.touches.count == stamp
                    val anchor = target.rectIn(ui.root)
                    val balloon = if (previews && single && anchor != null) balloonFor(anchor, ui.width, density) else null
                    var card: KeyPreview? = if (balloon != null && anchor != null) KeyPreview(typed.label, anchor, balloon, target).also { ui.preview = it } else null
                    val shownAt = android.os.SystemClock.uptimeMillis()
                    /** Takes the card down now (something else is about to be drawn there). */
                    fun dropCard() { card?.let { if (ui.preview === it) ui.preview = null }; card = null }
                    /** The finger has lifted: the pressed look goes, and the card with it once it has been up long enough to be seen. */
                    fun release() {
                        pressed = false
                        if (ui.proxy === target) ui.proxy = null
                        val c = card ?: return
                        card = null
                        if (ui.preview !== c) return
                        val left = CARD_MIN_MS - (android.os.SystemClock.uptimeMillis() - shownAt)
                        if (left <= 0L) ui.preview = null else scope.launch { delay(left); if (ui.preview === c) ui.preview = null }
                    }
                    val digit = if (letter && onLetters && !numberRow) KeyLayouts.digitFor(layout, typed.label[0]) else null
                    val options = if (single) listOfNotNull(digit) + KeyboardModel.alternates(typed.label) else emptyList()
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
                            if (!swiping && alone() && GestureDecoder.isSwipe(path, geometry)) {
                                swiping = true; fling = null
                                dropCard(); pressed = false
                                if (ui.proxy === target) ui.proxy = null
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
                        ui.trail.clear(); release()
                        return@awaitEachGesture
                    }
                    val symbol = fling
                    if (symbol != null) {
                        if (alone()) actions.replaceLast(symbol)                     // the letter already typed becomes the punctuation
                        release()
                        return@awaitEachGesture
                    }
                    if (ended || options.isEmpty() || moved || anchor == null || !alone()) { release(); return@awaitEachGesture }
                    // Held still past the delay with alternatives: open the strip, with a soft pop.
                    dropCard()
                    accenting = true
                    actions.haptic(HapticKind.PREVIEW)
                    var chosen = if (digit != null) 0 else -1
                    ui.accents = AccentState(options, chosen, anchor)
                    val strip = accentLayout(anchor, ui.width, options.size, cellWidth)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        val index = ((origin.x - rootX + change.position.x - strip.left) / cellWidth).toInt().coerceIn(0, options.lastIndex)
                        if (index != chosen) { chosen = index; ui.accents = AccentState(options, chosen, anchor); actions.haptic(HapticKind.SELECT) }
                        change.consume()
                        if (!change.pressed) break
                    }
                    if (alone()) options.getOrNull(chosen)?.let(actions::replaceLast)
                    ui.accents = null; accenting = false; release()
                }
            }) {
            // Hidden under its card by fading the layer it is drawn in, which redraws the key and composes nothing.
            Box(Modifier.matchParentSize().graphicsLayer { alpha = if (carded.value) 0f else 1f }, contentAlignment = Alignment.Center) {
                Text(key.label, color = palette.ink, fontSize = 23.sp, fontWeight = FontWeight.Normal)
                // Fossify's digit hints: the top row's digits, small in the corner, when there is no number row. Hidden in password fields too.
                if (digitHint != null && !passwordField) Text(digitHint, color = palette.sub, fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 4.dp))
            }
        }
    }
}

/** Where the strip of alternatives for the key at [anchor] goes, left to right, in the keyboard's coordinates. */
private fun accentLayout(anchor: Rect, panelWidth: Float, count: Int, cell: Float): Rect {
    val width = count * cell
    val left = (anchor.center.x - width / 2f).coerceIn(4f, (panelWidth - width - 4f).coerceAtLeast(4f))
    return Rect(left, anchor.top - 60f, left + width, anchor.top - 8f)
}

/** The magnified key: a glass balloon rising out of the key, like iOS's. It sits in the lane above the top row. Where it goes was
 * worked out when the key was touched ([KeyBalloon]), from where the key's face was then, so it is always over the key that typed.
 */
@Composable
private fun BoxScope.PreviewLayer(ui: KeyboardUiState, palette: KeyPalette) {
    val p = ui.preview ?: return
    val density = LocalDensity.current
    val key = p.key
    val headLeft = p.balloon.left; val headTop = p.balloon.top; val headW = p.balloon.width; val headH = p.balloon.height
    val r = with(density) { (if (palette.classic) 8.dp else 12.dp).toPx() }
    // matchParentSize, not fillMaxSize: the keyboard window wraps its content, and a child that fills a wrapping window makes it as
    // tall as the screen, which Android reads as a keyboard covering everything and hides it the moment a key is held.
    Box(Modifier.matchParentSize().drawBehind {
        // The balloon: a head wider than the key, a neck, and the key itself, in one solid glass colour (a preview over see-through keys
        // must not let the key under it show), with a soft shadow and the bright rim on top.
        val solid = palette.balloon
        val head = continuousPath(headLeft, headTop, headW, headH, r)
        val body = continuousPath(key.left, key.top, key.width, key.height, palette.keyRadius.toPx())
        fun shape(color: Color, dy: Float) = translate(top = dy) {
            drawPath(head, color)
            drawRect(color, Offset(key.left, headTop + headH - r), Size(key.width, key.height * .5f + r))
            drawPath(body, color)
        }
        shape(palette.shadow, if (palette.classic) 1.dp.toPx() else 2.dp.toPx()); shape(solid, 0f)
        if (!palette.classic && palette.rim) drawPath(head, Brush.verticalGradient(listOf(palette.rimTop, Color.Transparent), startY = headTop, endY = headTop + headH * .5f),
            style = Stroke(1.dp.toPx()))
    }.testTag("key-preview")) {
        Box(Modifier.offset { IntOffset(headLeft.toInt(), headTop.toInt()) }.size(width = with(density) { headW.toDp() }, height = with(density) { headH.toDp() })
            .testTag("key-preview-head"), contentAlignment = Alignment.Center) { Text(p.label, color = palette.ink, fontSize = 34.sp) }
    }
}

/** The alternatives strip for a held key, in a layer of the keyboard's own window (see PreviewLayer): above the key when there is room,
 * and below it otherwise (the top row of a password field has no lane above it).
 */
@Composable
private fun BoxScope.AccentLayer(ui: KeyboardUiState, palette: KeyPalette) {
    val a = ui.accents ?: return
    val density = LocalDensity.current
    val cell = with(density) { 40.dp.toPx() }
    val layout = accentLayout(a.bounds, ui.width, a.options.size, cell)
    val height = with(density) { 52.dp.toPx() }
    val above = a.bounds.top - height - 8f
    val top = if (above >= 2f) above else a.bounds.bottom + 6f
    val solid = palette.balloon
    Box(Modifier.matchParentSize()) {
        Row(Modifier.offset { IntOffset(layout.left.toInt(), top.toInt()) }.height(52.dp)
            .drawWithCache {
                val outline = continuousPath(0f, 0f, size.width, size.height, 12.dp.toPx())
                onDrawBehind {
                    translate(top = 2.dp.toPx()) { drawPath(outline, palette.shadow) }
                    drawPath(outline, solid)
                }
            }.testTag("accent-strip")) {
            a.options.forEachIndexed { i, text ->
                // The chosen one is concentric with the strip it sits in: the strip's 12 dp less the 2 dp around it.
                Box(Modifier.size(width = 40.dp, height = 52.dp).then(if (i == a.index) Modifier.padding(2.dp).background(palette.action, Corner.inside(12.dp, 2.dp)) else Modifier),
                    contentAlignment = Alignment.Center) { Text(text, color = if (i == a.index) Color.White else palette.ink, fontSize = 24.sp) }
            }
        }
    }
}

/** The trail a swiping finger leaves: a ribbon in the accent colour with a bright core, thinning and fading toward its tail. */
@Composable
private fun BoxScope.TrailLayer(ui: KeyboardUiState, palette: KeyPalette) {
    if (ui.trail.isEmpty()) return
    Box(Modifier.matchParentSize().drawBehind {
        // The trail's points are in the keyboard view's coordinates, as every touch is; the panel's place in it is read as it is drawn.
        val o = ui.root?.takeIf { it.isAttached }?.positionInRoot() ?: Offset.Zero
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
    repeatHaptic: HapticKind = HapticKind.DELETE_REPEAT, reach: KeyReach = KeyReach.None, icon: (@Composable (pressed: Boolean) -> Unit)? = null) {
    var pressed by remember { mutableStateOf(false) }
    val touches = LocalKeyTouches.current
    val step by rememberUpdatedState(onStep)
    GlassKey(palette, modifier, role, pressed = { pressed }, tag = tag, onTap = null, reach = reach,
        pointer = Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                touches.landed(); touches.acted()
                pressed = true; onHaptic(firstHaptic); step(0)
                // Held long enough, it repeats, faster the longer it is held; released or cancelled, it stops.
                var held = 0
                while (true) {
                    val up = withTimeoutOrNull(if (held == 0) 420L else if (held < 8) 90L else 45L) { waitForUpOrCancellation() }
                    if (up != null || !currentEvent.changes.any { it.pressed }) break
                    held++
                    touches.acted(); onHaptic(repeatHaptic); step(held)
                }
                pressed = false
            }
        }) {
        if (icon != null) icon(pressed)
        else Icon(if (pressed) Icons.AutoMirrored.Rounded.Backspace else Icons.AutoMirrored.Outlined.Backspace, "Delete", tint = palette.ink, modifier = Modifier.size(24.dp))
    }
}

/** A key that acts the moment it is touched (Shift, the page keys), and is told when it is let go: [onUp] also runs if the key goes
 * away while it is held, so what [onDown] began is always ended.
 */
@Composable
private fun DownKey(palette: KeyPalette, modifier: Modifier, role: KeyRole, tag: String, reach: KeyReach, haptic: HapticKind,
    onDown: () -> Unit, onUp: () -> Unit = {}, content: @Composable BoxScope.() -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val touches = LocalKeyTouches.current
    val actions = LocalKeyboardActions.current
    val downNow by rememberUpdatedState(onDown)
    val upNow by rememberUpdatedState(onUp)
    GlassKey(palette, modifier, role, pressed = { pressed }, tag = tag, onTap = null, reach = reach,
        pointer = Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                touches.landed()
                pressed = true; actions?.haptic(haptic); downNow()
                try {
                    // Held until every finger on it has lifted: sliding a little off a held Shift does not let it go.
                    do { val event = awaitPointerEvent() } while (event.changes.any { it.pressed })
                } finally { pressed = false; upNow() }
            }
        }, content = content)
}

/** A key's face with its touch. [pressed] and [hidden] are asked as the key is drawn, so a press redraws the key and composes nothing;
 * the pressed look comes on with the touch and fades when it lifts. A key with [onTap] acts when it is released, unless another key
 * lands first, in which case it acts then (see [KeyTouches]); the keys that act as they are touched bring their own [pointer].
 * The touch reaches beyond the face by [reach].
 */
@Composable
private fun GlassKey(palette: KeyPalette, modifier: Modifier, role: KeyRole, tag: String, onTap: (() -> Unit)?,
    pressed: () -> Boolean = NotPressed, hidden: () -> Boolean = NotPressed, pointer: Modifier = Modifier, hapticDown: HapticKind? = null,
    onLongPress: (() -> Unit)? = null, height: Dp = LocalKeyHeight.current, reach: KeyReach = KeyReach.None, early: Boolean = true,
    content: @Composable BoxScope.() -> Unit) {
    val actions0 = LocalKeyboardActions.current
    val touches = LocalKeyTouches.current
    val downState = remember { mutableStateOf(false) }
    var down by downState
    val longPress by rememberUpdatedState(onLongPress)
    val tap by rememberUpdatedState(onTap)
    val haptic by rememberUpdatedState(hapticDown)
    val actsEarly by rememberUpdatedState(early)
    val isPressed by rememberUpdatedState(pressed)
    // Keyed on nothing: the tap is read through State, so a key whose action is a new lambda on every composition (all of them) does
    // not have its touch handling restarted, and a touch lost, each time the keyboard recomposes.
    val tapModifier = if (onTap != null) Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false); down = true
            haptic?.let { actions0?.haptic(it) }
            val held = longPress
            if (held != null) {
                touches.landed()
                val up = withTimeoutOrNull(450L) { waitForUpOrCancellation() }
                if (up == null && currentEvent.changes.any { it.pressed }) { held(); waitForUpOrCancellation(); down = false; return@awaitEachGesture }
                down = false
                if (up != null) { touches.acted(); tap?.invoke() }
                return@awaitEachGesture
            }
            val ticket = touches.wait(actsEarly) { tap?.invoke() }
            val up = waitForUpOrCancellation(); down = false
            if (up != null) touches.release(ticket) else touches.cancel(ticket)
        }
    } else Modifier
    val glow = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        snapshotFlow { isPressed() || downState.value }.collectLatest { if (it) glow.snapTo(1f) else glow.animateTo(0f, tween(RELEASE_FADE_MS)) }
    }
    Box(modifier.height(height).keyFace(palette, role) { if (hidden()) null else if (isPressed() || downState.value) 1f else glow.value }.testTag(tag),
        contentAlignment = Alignment.Center) {
        // The touch is a layer of its own under the label, larger than the face by the key's reach.
        Box(Modifier.matchParentSize().reach(reach).then(tapModifier).then(pointer))
        content()
    }
}

/** A key's outlines at one size, with the launcher's continuous corner: built once for the size (see [keyFace]) and drawn every frame. */
private class KeyOutlines(size: Size, radius: Float, val lift: Float, inset: Float) {
    val h = size.height - lift
    val shadow = continuousPath(0f, lift, size.width, h, radius)
    val body = continuousPath(0f, 0f, size.width, h, radius)
    val rim = continuousPath(inset, inset, size.width - 2 * inset, h - 2 * inset, (radius - inset).coerceAtLeast(0f))
}

/** Draws a key's face behind its content. [state] says, at draw time, how pressed it is (0 at rest, 1 under a finger, between the two
 * as the press fades), or null while it is hidden (under its card).
 */
private fun Modifier.keyFace(palette: KeyPalette, role: KeyRole, radius: Dp = palette.keyRadius, state: () -> Float?): Modifier = drawWithCache {
    val outlines = KeyOutlines(size, radius.toPx(), 1.dp.toPx(), .5.dp.toPx())
    onDrawBehind { state()?.let { drawKey(palette, role, it, outlines) } }
}

/** A glass key: a soft shadow beneath, a translucent body brightening toward its top, and a specular rim, brightest along the top edge
 * where light catches it (unless Edge Highlight is off). A pressed key glows, by [pressed] from 0 to 1.
 */
private fun DrawScope.drawKey(palette: KeyPalette, role: KeyRole, pressed: Float, k: KeyOutlines) {
    if (palette.classic) {
        // Uno 0.97's key: flat, with a thin darker line under it, as on iOS before glass. A pressed key swaps shades: a special key
        // turns light and a letter key or the space bar turns the special keys' grey, so every key answers a touch.
        val body = when (role) {
            KeyRole.ACTION -> palette.action.copy(alpha = 1f - .2f * pressed)
            KeyRole.LETTER -> lerp(palette.letter, palette.special, pressed)
            KeyRole.SPECIAL -> lerp(palette.special, palette.specialPressed, pressed)
        }
        drawPath(k.shadow, palette.shadow)
        drawPath(k.body, body)
        return
    }
    val body = when (role) {
        KeyRole.ACTION -> palette.action
        KeyRole.LETTER -> palette.letter
        KeyRole.SPECIAL -> palette.special
    }
    drawPath(k.shadow, palette.shadow)
    drawPath(k.body, Brush.verticalGradient(listOf(lerpWhite(body, .10f), body), endY = k.h))
    if (pressed > 0f) (if (role == KeyRole.ACTION) Color(0x33000000) else palette.pressedGlow).let { glow -> drawPath(k.body, glow.copy(alpha = glow.alpha * pressed)) }
    if (palette.rim) drawPath(k.rim, Brush.verticalGradient(0f to palette.rimTop, .45f to Color.Transparent, 1f to palette.rimBottom, startY = 0f, endY = k.h),
        style = Stroke(1.dp.toPx()))
}

private fun lerpWhite(c: Color, t: Float) = Color(c.red + (1f - c.red) * t, c.green + (1f - c.green) * t, c.blue + (1f - c.blue) * t, c.alpha)

/** The space bar. A tap types a space; sliding a finger along it moves the cursor, a character for each few millimetres
 * (like FlorisBoard's space-bar cursor control), which is the quick way to fix a typo in the middle of a sentence. With more than one
 * layout on, it names the one in use, as Fossify Keyboard's does.
 *
 * The space is typed when the bar is let go (a slide must be able to become the cursor instead), or the moment another key is
 * touched while it is still down: in fast typing the next letter lands before the thumb has left the bar, and the space must still
 * come first.
 */
@Composable
private fun SpaceKey(ui: KeyboardUiState, actions: KeyboardActions, palette: KeyPalette, modifier: Modifier, reach: KeyReach) {
    val density = LocalDensity.current
    var pressed by remember { mutableStateOf(false) }
    var sliding by remember { mutableStateOf(false) }
    val slop = with(density) { 10.dp.toPx() }
    val step = with(density) { 14.dp.toPx() }
    val touches = ui.touches
    GlassKey(palette, modifier, KeyRole.LETTER, pressed = { pressed }, tag = "key-space", onTap = null, reach = reach,
        pointer = Modifier.pointerInput(ui.spaceCursor) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                pressed = true; actions.haptic(HapticKind.SPACE)
                val ticket = touches.wait { actions.space() }
                var moved: Float; var emitted = 0; var dragging = false
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    moved = change.position.x - down.position.x
                    // A slide becomes the cursor only while the space is still waiting: once another key has typed it, it is typed.
                    if (ui.spaceCursor && !dragging && abs(moved) > slop && touches.isWaiting(ticket)) { dragging = true; sliding = true; touches.cancel(ticket) }
                    if (dragging) {
                        val target = ((moved - kotlin.math.sign(moved) * slop) / step).toInt()
                        if (target != emitted) { actions.moveCursor(target - emitted); emitted = target; actions.haptic(HapticKind.CURSOR) }
                        change.consume()
                    }
                    if (!change.pressed) break
                }
                if (!dragging) touches.release(ticket)
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

/** A round glass button for the lane: the tools, Paste, the emoji search's close. Like the keys it answers the touch (the tick and a
 * brighter pill) and acts when it is let go.
 */
@Composable
private fun LaneButton(icon: ImageVector, description: String, palette: KeyPalette, tag: String, actions: KeyboardActions, active: Boolean = false,
    onClick: () -> Unit) {
    val touches = LocalKeyTouches.current
    val click by rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    Box(Modifier.size(LaneHeight).padding(3.dp).clip(Corner.pill)
        .background(if (active) palette.action.copy(alpha = .9f) else palette.pill)
        .drawBehind { if (pressed) drawRect(palette.pressedGlow) }
        .pointerInput(Unit) { awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false); pressed = true; actions.haptic(HapticKind.MODIFIER)
            val up = waitForUpOrCancellation(); pressed = false
            if (up != null) { touches.landed(); click() }
        } }
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
    // The slot under a finger, which shows the pill a correction has: every touch on the keyboard is answered as it lands.
    var pressedSlot by remember { mutableIntStateOf(-1) }
    Row(modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val s = slots.getOrNull(index)
            val highlight = s != null && (s.kind == SuggestionKind.CORRECTION || (s.kind == SuggestionKind.GESTURE && index == 0 && ui.swiping))
            Box(Modifier.weight(1f).fillMaxHeight().padding(vertical = 3.dp, horizontal = 2.dp)
                .then(if (highlight || pressedSlot == index) Modifier.clip(Corner.pill).background(palette.pill) else Modifier)
                .then(if (s != null) Modifier.pointerInput(s) {
                    // A tap picks it; a long press forgets it, when it is something the keyboard learned from you (Gboard's drag-to-remove).
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        pressedSlot = index; actions.haptic(HapticKind.MODIFIER)
                        val up = withTimeoutOrNull(550L) { waitForUpOrCancellation() }
                        val held = up == null && currentEvent.changes.any { it.pressed }
                        if (held && actions.forget(s)) { waitForUpOrCancellation(); pressedSlot = -1; return@awaitEachGesture }
                        // Held but nothing to forget: it is still picked when the finger lifts.
                        val picked = (if (held) waitForUpOrCancellation() else up) != null
                        pressedSlot = -1
                        // A key still held (the space bar, in fast typing) acts before the word that was picked after it.
                        if (picked) { ui.touches.landed(); actions.pick(s) }
                    }
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
    Row(Modifier.width(120.dp).fillMaxHeight().padding(vertical = 4.dp).clip(Corner.pill).background(palette.pill).padding(horizontal = 10.dp),
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
                Row(Modifier.fillMaxWidth().clip(ContinuousShape(12.dp)).keyFace(palette, KeyRole.LETTER, 12.dp) { 0f }
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
                Row(Modifier.fillMaxWidth().height(searchH).padding(horizontal = 4.dp).clip(Corner.pill).background(palette.pill)
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
            if (base != null) Row(Modifier.align(Alignment.Center).clip(Corner.medium)
                .background(if (palette.dark) Color(0xFF3A3A3D) else Color.White, Corner.medium)
                .padding(6.dp).testTag("emoji-tones")) {
                (listOf(base) + EmojiIndex.TONES.map { EmojiIndex.withTone(base, it) }).forEach { v ->
                    Box(Modifier.size(46.dp).clickable { actions.haptic(HapticKind.SELECT); actions.emoji(v); toning = null }, contentAlignment = Alignment.Center) {
                        Text(v, fontSize = 28.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyGap), verticalAlignment = Alignment.CenterVertically) {
            DownKey(palette, Modifier.weight(1.5f), KeyRole.SPECIAL, "key-page", KeyReach.None, HapticKind.MODIFIER, onDown = { actions.page(KeyPage.LETTERS) }) {
                Text(stringResource(R.string.abc), color = palette.ink, fontSize = 16.sp)
            }
            Row(Modifier.weight(7f).height(keyHeight), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                val tabs = (if (hasRecents) listOf(-1 to "🕒") else emptyList()) + categories.mapIndexed { i, c -> i to c.tab }
                tabs.forEach { (index, glyph) ->
                    Box(Modifier.weight(1f).fillMaxHeight().pointerInput(index) { detectTapGestures { actions.haptic(HapticKind.MODIFIER); selected = index; toning = null } }
                        .testTag("emoji-tab-$index"), contentAlignment = Alignment.Center) {
                        if (index == tab) Box(Modifier.size(32.dp).clip(Corner.pill).background(palette.pill))
                        Text(glyph, fontSize = 19.sp, modifier = Modifier.then(if (index == tab) Modifier else Modifier.graphicsLayer { alpha = .55f }))
                    }
                }
            }
            RepeatingKey(palette, Modifier.weight(1.5f), onHaptic = actions::haptic, onStep = { held -> actions.backspace(held) })
        }
    }
}
