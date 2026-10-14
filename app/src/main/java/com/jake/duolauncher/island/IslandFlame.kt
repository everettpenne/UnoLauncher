package com.jake.duolauncher.island

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.sin

/**
 * The now-playing glow: the track's colour rising from the island's bottom edge, with a few soft tongues that sway and swell like a
 * low flame while the audio is really playing (paused, it rests as a still glow). The motion is slow and small on purpose: it is
 * meant to be felt at the edge of vision, not watched.
 */
internal object FlameShape {
    const val TONGUES = 5

    /**
     * Where tongue [i] stands and how tall it is at time [t] (seconds), as fractions of the island's width (left to right) and of its
     * height. Each tongue sways and swells on its own periods, so they drift in and out of step and never repeat in lockstep.
     */
    fun tongue(i: Int, t: Float): Pair<Float, Float> {
        val phase = i * 1.3f
        val x = (i + .5f) / TONGUES + .035f * sin(t * (.9f + .17f * i) + phase)
        val h = .42f + .14f * sin(t * (1.3f + .21f * i) + phase * 1.7f) + .06f * sin(t * 2.9f + i)
        return x to h
    }
}

/** Draws the glow in [color] at [alpha]; [t] moves the tongues when [animated], otherwise they rest at a gentler strength. */
internal fun DrawScope.drawBottomGlow(color: Color, alpha: Float, t: Float, animated: Boolean) {
    // The base: a soft wash rising from the bottom edge.
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = alpha * .16f), color.copy(alpha = alpha * .55f))))
    val radius = size.width / FlameShape.TONGUES * .95f
    val strength = if (animated) 1f else .6f
    for (i in 0 until FlameShape.TONGUES) {
        val (fx, fh) = FlameShape.tongue(i, if (animated) t else 0f)
        val cx = fx * size.width
        val height = (fh * size.height).coerceAtLeast(radius * .2f)
        val base = Offset(cx, size.height)
        // A circle of light squashed into a tongue: stretched up from the edge by the tongue's height.
        scale(1f, height / radius, pivot = base) {
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha * .5f * strength), Color.Transparent), base, radius), radius, base)
        }
    }
}

/**
 * A clock, in seconds, that ticks with the screen while [active] and stands still otherwise, so nothing redraws while no audio plays.
 * It wraps every ~17 minutes (the flame never repeats visibly anyway) to keep the number small enough for a float to stay smooth.
 */
@Composable
internal fun rememberFlameClock(active: Boolean): State<Float> = produceState(0f, active) {
    if (!active) return@produceState
    // Honours Compose's infinite-animation policy, so a test that waits for the screen to be idle is not held by a flame that never ends.
    while (true) withInfiniteAnimationFrameNanos { nanos -> value = ((nanos / 1_000_000L) % 1_000_000L) / 1000f }
}
