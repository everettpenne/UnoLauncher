package com.jake.duolauncher.island

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.sin

/**
 * The now-playing glow: the track's colour rising from the island's bottom edge, with a few soft tongues that sway and swell like a
 * low flame while the audio is really playing (paused, it rests as a still glow). Since 0.106 it is a livelier flame: the tongues sway
 * further and a quick flicker rides on their slow swell, in a stronger colour.
 */
internal object FlameShape {
    const val TONGUES = 5

    /**
     * Where tongue [i] stands and how tall it is at time [t] (seconds), as fractions of the island's width (left to right) and of its
     * height. Each tongue sways and swells on its own periods, so they drift in and out of step and never repeat in lockstep.
     */
    fun tongue(i: Int, t: Float): Pair<Float, Float> = tongueX(i, t) to tongueH(i, t)

    /** The tongue's centre along the island's width, as a fraction. Split from [tongue] so the draw loop reads it without a Pair per frame. */
    fun tongueX(i: Int, t: Float): Float {
        val phase = i * 1.3f
        return (i + .5f) / TONGUES + .06f * sin(t * (1.4f + .23f * i) + phase)
    }

    /** The tongue's height, as a fraction of the island's height. */
    fun tongueH(i: Int, t: Float): Float {
        val phase = i * 1.3f
        // A slow swell, a quicker sway, and a fast flicker on top, each on its own period per tongue.
        return .5f + .2f * sin(t * (1.9f + .31f * i) + phase * 1.7f) + .09f * sin(t * 4.6f + i) + .07f * sin(t * (9.3f + .7f * i) + i * 2.1f)
    }
}

/**
 * Draws the glow with [wash] (the bottom wash) and [tongue] (a unit radial gradient), cached by the caller for the track's lifetime.
 * [t] moves the tongues when [animated], otherwise they rest at a gentler strength. The animated [alpha] is applied at draw time, so
 * neither brush bakes it, and the tongue is a unit gradient scaled out to the tongue's radius and height, so its centre and radius
 * (which move and breathe every frame) never rebuild the brush.
 */
internal fun DrawScope.drawBottomGlow(wash: Brush, tongue: Brush, alpha: Float, t: Float, animated: Boolean) {
    // The base: a soft wash rising from the bottom edge.
    drawRect(wash, alpha = alpha)
    val radius = size.width / FlameShape.TONGUES * .95f
    for (i in 0 until FlameShape.TONGUES) {
        val fx = FlameShape.tongueX(i, if (animated) t else 0f)
        val fh = FlameShape.tongueH(i, if (animated) t else 0f)
        val cx = fx * size.width
        val height = (fh * size.height).coerceAtLeast(radius * .2f)
        val base = Offset(cx, size.height)
        // A circle of light squashed into a tongue: the unit gradient drawn at the tongue's base and scaled out to its radius and
        // height, which stretches it up from the edge by the tongue's height.
        translate(base.x, base.y) {
            scale(radius, height, pivot = Offset.Zero) {
                drawCircle(tongue, 1f, Offset.Zero, alpha = alpha)
            }
        }
    }
}

/**
 * A clock, in seconds, that ticks with the screen while [active] and stands still otherwise, so nothing redraws while no audio plays.
 * It counts from when the flame starts, so it never wraps mid-song.
 */
@Composable
internal fun rememberFlameClock(active: Boolean): State<Float> = produceState(0f, active) {
    if (!active) return@produceState
    // Honours Compose's infinite-animation policy, so a test that waits for the screen to be idle is not held by a flame that never ends.
    // Seconds since the flame started, not since boot wrapped every ~17 minutes: the wrap jumped every tongue at once (a visible flicker in
    // the middle of a song). Counted from the start, a float keeps millisecond steps for many hours of one session.
    var start = -1L
    while (true) withInfiniteAnimationFrameNanos { nanos -> if (start < 0L) start = nanos; value = ((nanos - start) / 1_000_000L) / 1000f }
}
