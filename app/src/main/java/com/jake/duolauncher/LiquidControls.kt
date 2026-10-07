package com.jake.duolauncher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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

/** iOS-style slider: an orange-tinted Material slider when glass is off, and a liquid
 * track with a glass thumb when [glass] is available. The thumb refracts whatever is
 * behind it; dragging or tapping sets the value like a stock slider.
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
        if (detent != lastDetent && (detents > 1 || detent >= 0)) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        lastDetent = detent
        onValueChange(v)
    }
    if (glass == null) {
        Slider(value, reportChange, modifier, valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = IosOrange,
                inactiveTrackColor = IosGrey.copy(alpha = .28f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent))
        return
    }
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    var width by remember { mutableStateOf(0) }
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    val progress by animateFloatAsState(fraction, label = "slider fraction")
    val interaction = remember { MutableInteractionSource() }
    val pressed by rememberPressProgress(interaction)
    val thumbSize = 28.dp
    val trackHeight = 6.dp
    val thumbPx = with(LocalDensity.current) { thumbSize.toPx() }
    // Dragging past an end stretches the thumb a little and springs it back on release.
    var pull by remember { mutableFloatStateOf(0f) }
    val stretch by animateFloatAsState(pull, spring(dampingRatio = .45f, stiffness = 500f), label = "slider stretch")
    val maxStretchPx = with(LocalDensity.current) { 10.dp.toPx() }

    Box(modifier.fillMaxWidth().height(40.dp).onSizeChanged { width = it.width }
        .pointerInput(valueRange, width) {
            if (width <= 0) return@pointerInput
            detectTapGestures { tap ->
                val fx = (tap.x / width).coerceIn(0f, 1f)
                val raw = if (isLtr) fx else 1f - fx
                reportChange(valueRange.start + raw * (valueRange.endInclusive - valueRange.start))
            }
        }
        .pointerInput(valueRange, width) {
            if (width <= 0) return@pointerInput
            detectDragGestures(onDragEnd = { pull = 0f }, onDragCancel = { pull = 0f }) { change, _ ->
                val unclamped = change.position.x / width
                val fx = unclamped.coerceIn(0f, 1f)
                // How far the finger is past the track, with resistance so it never runs away.
                pull = ((unclamped - fx) * width * .35f).coerceIn(-maxStretchPx, maxStretchPx) * (if (isLtr) 1f else -1f)
                val raw = if (isLtr) fx else 1f - fx
                reportChange(valueRange.start + raw * (valueRange.endInclusive - valueRange.start))
            }
        }
        .semantics(mergeDescendants = true) { contentDescription = "Slider ${(progress * 100).roundToInt()} percent" },
        contentAlignment = Alignment.CenterStart) {
        // Track: quiet grey under an orange active run.
        Box(Modifier.fillMaxWidth().height(trackHeight).clip(RoundedCornerShape(percent = 50))
            .background(IosGrey.copy(alpha = .28f)))
        Box(Modifier.fillMaxWidth(progress).height(trackHeight).clip(RoundedCornerShape(percent = 50))
            .background(IosOrange.copy(alpha = .9f)))
        // Thumb: a glass circle over the recorded backdrop.
        Box(Modifier.graphicsLayer {
                translationX = progress * width - thumbPx / 2f + stretch
                scaleX = 1f + .12f * pressed + kotlin.math.abs(stretch) / maxStretchPx * .18f; scaleY = 1f + .12f * pressed
            }
            .liquidGlass(glass.backdrop, CircleShape, glass.tint.copy(alpha = .4f),
                blurRadius = 4f, settings = glass.settings)
            .pressGlow(pressed, CircleShape)
            .size(thumbSize)
            .testTag("liquid-slider-thumb"))
    }
}

/** iOS-style toggle: a green Material switch when glass is off, and a liquid track with
 * a sliding glass thumb when [glass] is available. Tap or drag the thumb to flip it.
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
    val trackWidth = 51.dp
    val thumbSize = 27.dp
    val travel = with(density) { (trackWidth - thumbSize - 4.dp).toPx() }
    val fraction by animateFloatAsState(if (checked) 1f else 0f, label = "switch fraction")
    val interaction = remember { MutableInteractionSource() }
    val pressed by rememberPressProgress(interaction)
    val trackColor = lerp(IosGrey.copy(alpha = .38f), IosGreen, fraction)

    Box(modifier.size(width = trackWidth, height = 31.dp)
        .clickableToggle(interaction, checked, onCheckedChange)
        .semantics { role = Role.Switch; contentDescription = if (checked) "On" else "Off" },
        contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(percent = 50)).background(trackColor))
        Box(Modifier.graphicsLayer {
                translationX = if (isLtr) 2.dp.toPx() + fraction * travel else 0f
                scaleX = 1f + .08f * pressed; scaleY = 1f + .08f * pressed
            }
            .liquidGlass(glass.backdrop, CircleShape, Color.White.copy(alpha = .92f),
                blurRadius = 3f, settings = glass.settings)
            .pressGlow(pressed, CircleShape)
            .size(thumbSize)
            .testTag("liquid-switch-thumb"))
    }
}

private fun Modifier.clickableToggle(
    interaction: MutableInteractionSource,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
): Modifier = clickable(
    interactionSource = interaction,
    indication = null,
    role = Role.Switch,
    onClick = { onCheckedChange(!checked) })
