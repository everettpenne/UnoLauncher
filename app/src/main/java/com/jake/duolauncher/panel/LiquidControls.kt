package com.jake.duolauncher.panel

import androidx.compose.material3.minimumInteractiveComponentSize
import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.core.spring
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** iOS accent palette: switches are green, sliders and highlights are orange. */
internal val IosGreen = Color(0xFF34C759)
internal val IosGreenDark = Color(0xFF30D158)
internal val IosOrange = Color(0xFFFF9500)
internal val IosGrey = Color(0xFF787880)

/**
 * The glass thumb of the switch and the slider, as on iOS: at rest a plain white pill, and while it is held it grows, turns clear and
 * bends the track beneath it like a lens, with a soft inner shadow and an all-round edge light. [press] is 0..1 and [velocity] the
 * thumb's speed across the track (it stretches along the motion and squashes across it, so it feels like a drop of liquid).
 * The track is recorded into [trackBackdrop] by the caller; the thumb sees it (slightly magnified while held) over [glass]'s backdrop.
 */
@Composable
private fun Modifier.liquidThumb(glass: PageGlass, trackBackdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    press: () -> Float, velocity: () -> Float, lensHeightDp: Float = 5f, lensAmountDp: Float = 10f): Modifier {
    val seen = rememberCombinedBackdrop(glass.backdrop, rememberBackdrop(trackBackdrop) { drawTrack ->
        val p = press()
        scale(lerp(2f / 3f, .75f, p), lerp(0f, .75f, p)) { drawTrack() }
    })
    return drawBackdrop(
        backdrop = seen,
        shape = { RoundedCornerShape(percent = 50) },
        effects = {
            val p = press()
            blur(8f.dp.toPx() * (1f - p))
            lens(lensHeightDp.dp.toPx() * p, lensAmountDp.dp.toPx() * p, chromaticAberration = true)
        },
        highlight = {
            val p = press()
            Highlight.Ambient.copy(width = Highlight.Ambient.width / 1.5f, blurRadius = Highlight.Ambient.blurRadius / 1.5f, alpha = p)
        },
        shadow = { Shadow(radius = 4f.dp, color = Color.Black.copy(alpha = .08f)) },
        innerShadow = { val p = press(); InnerShadow(radius = 4f.dp * p, alpha = p) },
        layerBlock = {
            val p = press()
            val grown = lerp(1f, 1.5f, p)
            val v = velocity() / 50f
            scaleX = grown / (1f - (v * .75f).coerceIn(-.2f, .2f))
            scaleY = grown * (1f - (v * .25f).coerceIn(-.2f, .2f))
        },
        onDrawSurface = { drawRect(Color.White.copy(alpha = 1f - press())) })
}

/** iOS-style slider: an orange-tinted Material slider when glass is off, and a liquid
 * track with a glass thumb when [glass] is available. The thumb is white until it is held,
 * then turns into clear glass that bends the track; dragging or tapping sets the value.
 */
@Composable
internal fun LiquidSliderControl(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    glass: PageGlass?,
    modifier: Modifier = Modifier,
    /** More than 1 gives the slider that many steps, each marked with a haptic tick, like iOS's volume. */
    detents: Int = 0,
) {
    val haptic = LocalHapticFeedback.current
    // Ticks fire as the value crosses a step or reaches an end, not on every pixel of a drag.
    var lastDetent by remember { mutableIntStateOf(Int.MIN_VALUE) }
    val reportChange = { v: Float ->
        val f = ((v - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        val detent = if (detents > 1) (f * detents).roundToInt() else if (f <= 0f) 0 else if (f >= 1f) 1 else -1
        if (detent != lastDetent && (detents > 1 || detent >= 0)) UnoFeedback.play(Cue.TICK, haptic)
        lastDetent = detent
        onValueChange(v)
    }
    if (glass == null) {
        Slider(value, reportChange, modifier, valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = LocalWallpaperAccent.current?.accent ?: IosOrange,
                inactiveTrackColor = IosGrey.copy(alpha = .28f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent))
        return
    }
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    var width by remember { mutableStateOf(0) }
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    val progressAnim = remember { Animatable(fraction, .0005f) }
    LaunchedEffect(fraction) { progressAnim.animateTo(fraction, spring(.8f, 700f, .0005f)) }
    val progress = progressAnim.value
    val scope = rememberCoroutineScope()
    val press = remember { Animatable(0f, .001f) }
    val trackBackdrop = rememberLayerBackdrop()
    val thumbWidth = 40.dp
    val thumbHeight = 24.dp
    val trackHeight = 6.dp
    val thumbPx = with(LocalDensity.current) { thumbWidth.toPx() }
    // Dragging past an end stretches the thumb a little and springs it back on release.
    var pull by remember { mutableFloatStateOf(0f) }
    val stretch by animateFloatAsState(pull, spring(dampingRatio = .45f, stiffness = 500f), label = "slider stretch")
    val maxStretchPx = with(LocalDensity.current) { 10.dp.toPx() }
    val accent = (LocalWallpaperAccent.current?.accent ?: IosOrange).copy(alpha = .9f)

    Box(modifier.fillMaxWidth().height(40.dp).onSizeChanged { width = it.width }
        .pointerInput(valueRange, width, isLtr) {
            if (width <= 0) return@pointerInput
            fun valueAt(x: Float): Float {
                val raw = (x / width).coerceIn(0f, 1f).let { if (isLtr) it else 1f - it }
                return valueRange.start + raw * (valueRange.endInclusive - valueRange.start)
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                scope.launch { press.animateTo(1f, spring(.5f, 300f, .001f)) }
                reportChange(valueAt(down.position.x))
                drag(down.id) { change ->
                    val unclamped = change.position.x / width
                    val fx = unclamped.coerceIn(0f, 1f)
                    // How far the finger is past the track, with resistance so it never runs away.
                    pull = ((unclamped - fx) * width * .35f).coerceIn(-maxStretchPx, maxStretchPx) * (if (isLtr) 1f else -1f)
                    reportChange(valueAt(change.position.x))
                    change.consume()
                }
                pull = 0f
                scope.launch { press.animateTo(0f, spring(.5f, 300f, .001f)) }
            }
        }
        .semantics(mergeDescendants = true) { contentDescription = "Slider ${(progress * 100).roundToInt()} percent" },
        contentAlignment = Alignment.CenterStart) {
        // Track: quiet grey under an accent active run. Recorded, so the held thumb can bend it.
        Box(Modifier.fillMaxWidth().height(trackHeight).layerBackdrop(trackBackdrop).clip(RoundedCornerShape(percent = 50))) {
            Box(Modifier.fillMaxSize().background(IosGrey.copy(alpha = .28f)))
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(accent))
        }
        // Thumb: white at rest, clear liquid glass while held.
        Box(Modifier.graphicsLayer { translationX = progress * (width - thumbPx) + stretch }
            .liquidThumb(glass, trackBackdrop, { press.value }, { progressAnim.velocity * 5f }, lensHeightDp = 10f, lensAmountDp = 14f)
            .size(thumbWidth, thumbHeight)
            .testTag("liquid-slider-thumb"))
    }
}

/** iOS-style toggle: a green Material switch when glass is off, and with glass a track and a pill thumb that is white at rest and,
 * while held, grows and turns into a clear lens over the track and stretches as it moves. Tap it, or drag the thumb and let go.
 */
@Composable
internal fun LiquidSwitchControl(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    glass: PageGlass?,
    modifier: Modifier = Modifier,
) {
    if (glass == null) {
        Switch(checked, onCheckedChange, modifier, colors = IosSwitchColors)
        return
    }
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val density = LocalDensity.current
    val trackWidth = 64.dp; val trackHeight = 28.dp
    val thumbWidth = 40.dp; val thumbHeight = 24.dp
    val inset = (trackHeight - thumbHeight) / 2
    val travel = with(density) { (trackWidth - thumbWidth - inset * 2).toPx() }
    val scope = rememberCoroutineScope()
    val position = remember { Animatable(if (checked) 1f else 0f, .001f) }
    val press = remember { Animatable(0f, .001f) }
    var dragging by remember { mutableStateOf(false) }
    val latestChecked by rememberUpdatedState(checked)
    val latestChange by rememberUpdatedState(onCheckedChange)
    LaunchedEffect(checked) { if (!dragging) position.animateTo(if (checked) 1f else 0f, spring(.7f, 380f, .001f)) }
    // The same accent as every other selected control (buttons, chips, checkboxes, sliders): the wallpaper's, or orange. It was iOS green here,
    // so a settings page showed green switches beside orange checkmarks.
    val accent = LocalWallpaperAccent.current?.accent ?: IosOrange
    val trackOff = IosGrey.copy(alpha = .38f)
    val trackBackdrop = rememberLayerBackdrop()

    Box(modifier.minimumInteractiveComponentSize().size(width = trackWidth, height = trackHeight)
        .pointerInput(isLtr, travel) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                scope.launch { press.animateTo(1f, spring(.5f, 300f, .001f)) }
                var moved = false
                dragging = true
                drag(down.id) { change ->
                    val dx = (change.position - change.previousPosition).x
                    if (dx != 0f) {
                        moved = true
                        val next = (position.value + (if (isLtr) dx else -dx) / travel).coerceIn(0f, 1f)
                        scope.launch { position.snapTo(next) }
                    }
                    change.consume()
                }
                dragging = false
                val target = if (moved) (if (position.value >= .5f) 1f else 0f) else (if (latestChecked) 0f else 1f)
                scope.launch {
                    press.animateTo(0f, spring(.5f, 300f, .001f))
                }
                scope.launch { position.animateTo(target, spring(.7f, 380f, .001f)) }
                if ((target == 1f) != latestChecked) latestChange(target == 1f)
            }
        }
        .semantics {
            role = Role.Switch
            toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
            contentDescription = if (checked) "On" else "Off"
            onClick { onCheckedChange(!checked); true }
        },
        contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxSize().layerBackdrop(trackBackdrop).clip(RoundedCornerShape(percent = 50))
            .drawBehind { drawRect(lerp(trackOff, accent, position.value)) })
        Box(Modifier.graphicsLayer {
                val pad = inset.toPx()
                val x = pad + position.value * travel
                translationX = if (isLtr) x else -x
            }
            .liquidThumb(glass, trackBackdrop, { press.value }, { position.velocity * travel / 1000f * 20f })
            .size(thumbWidth, thumbHeight)
            .testTag("liquid-switch-thumb"))
    }
}
