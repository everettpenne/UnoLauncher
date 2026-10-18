package com.jake.duolauncher.island

import kotlin.math.exp

/**
 * One home for every island drag: pull-to-peek, swipe-up-to-close, the hold-open rest, and Spotlight's
 * collapse all read their thresholds and their low-pass filter from here. Keeping the constants together
 * is what makes a future feel change one edit instead of five. Everything is pure and read outside
 * composition (the peek-jitter contract): the gesture steps the filter per touch event, and the animation
 * collector only ever sees the filtered value.
 */
internal object IslandDrag {
    /** The filter time constant: short enough to feel instant, long enough to swallow per-touch jitter (30–60 ms). */
    const val LOW_PASS_TAU_S = 0.04f
    private const val LOW_PASS_GAP_S = 0.5f

    // Pull-to-peek thresholds.
    const val PULL_SLOP_DP = 8f
    const val PULL_TO_PEEK_DP = 56f
    const val PULL_TO_FULL_DP = 140f
    /** A held peek tucks away by itself after this long, if nothing touches it. */
    const val PEEK_HOLD_MS = 5_000L

    // Swipe-up-to-close thresholds.
    const val CLOSE_DP = 32f
    const val CLOSE_VELOCITY_PX_S = 600f

    // Hold-open thresholds.
    const val HOLD_ZONE_DP = 56f
    const val HOLD_AFTER_MS = 350L

    /** What letting go of a pull does. */
    enum class PeekOutcome { COLLAPSE, PEEK, OPEN }

    /** What letting go of a swipe-up does. */
    enum class CloseOutcome { STAY, CLOSE }

    /**
     * A critically-damped 1st-order low-pass filter for a finger-driven value. It lets intentional moves through in a few
     * tens of milliseconds while killing sub-frame touch jitter, and it reports the *filtered* value's velocity so a release
     * spring continues the smooth motion rather than the raw finger's noise.
     */
    class Filter(private val tauSeconds: Float = LOW_PASS_TAU_S) {
        private var value = Float.NaN
        private var velocity = 0f

        /** Steps the filter toward [raw] over [dtSeconds]; returns the filtered value. The first step (or a long gap) snaps to [raw]. */
        fun step(dtSeconds: Float, raw: Float): Float {
            if (dtSeconds <= 0f || dtSeconds > LOW_PASS_GAP_S || value.isNaN()) {
                value = raw; velocity = 0f; return raw
            }
            val alpha = 1f - exp(-dtSeconds / tauSeconds)
            val next = value + (raw - value) * alpha
            // The filtered value's own rate of change, smoothed with the same time constant.
            velocity += ((next - value) / dtSeconds - velocity) * alpha
            value = next
            return next
        }

        /** The filtered value's velocity (units per second), for seeding a release spring. */
        fun velocity(): Float = velocity
    }

    /**
     * How open the island is for a pull of [dyPx], where [peek] is the fraction the peek rests at. The first stretch of the
     * pull takes the island to the peek; the rest takes it on to fully open. Pulling up from a peek ([from] > 0) closes it
     * again, and pulling down opens it further.
     */
    fun peekFraction(dyPx: Float, density: Float, peek: Float, from: Float = 0f): Float {
        val dy = dyPx / density
        return if (from <= 0f) {
            if (dy <= PULL_TO_PEEK_DP) (dy / PULL_TO_PEEK_DP * peek).coerceAtLeast(0f)
            else (peek + (dy - PULL_TO_PEEK_DP) / (PULL_TO_FULL_DP - PULL_TO_PEEK_DP) * (1f - peek)).coerceAtMost(1f)
        } else (from + dy / (PULL_TO_FULL_DP - PULL_TO_PEEK_DP) * (1f - peek)).coerceIn(0f, 1f)
    }

    /** What letting go of a pull does. A short pull drops back; a pull that has reached the peek holds it; one that is nearly all
     * the way opens it. Decided by the last place the finger was, so a drag that Android cancels part-way still lands in the peek. */
    fun peekOutcome(fraction: Float, peek: Float): PeekOutcome = when {
        fraction < peek * .15f -> PeekOutcome.COLLAPSE
        fraction >= peek + (1f - peek) * .6f -> PeekOutcome.OPEN
        else -> PeekOutcome.PEEK
    }

    /** The open fraction (1 = fully open) for a finger that has moved up [upPx] pixels from the height [from] it was grabbed
     * at, over a full-close travel of [travelPx] pixels, so the bottom edge follows the finger one-to-one. */
    fun closeFraction(upPx: Float, travelPx: Float, from: Float = 1f): Float =
        if (travelPx <= 0f) from else (from - upPx / travelPx).coerceIn(0f, 1f)

    /** What letting go of a swipe-up does. [upDp] is the upward travel in dp (positive = up); [upVelocityPx] is the release speed
     * in px/s (positive = up); [slopDp] is the touch slop in dp. A movement within the slop is a tap or a hold and never closes. */
    fun closeDecide(upDp: Float, upVelocityPx: Float, slopDp: Float): CloseOutcome = when {
        upDp <= slopDp -> CloseOutcome.STAY
        upDp >= CLOSE_DP -> CloseOutcome.CLOSE
        upVelocityPx >= CLOSE_VELOCITY_PX_S -> CloseOutcome.CLOSE
        else -> CloseOutcome.STAY
    }

    /** Whether a press at [y] (within a surface [height] tall) lands on the bottom strip that holds the island open. */
    fun holdInZone(y: Float, height: Float, density: Float): Boolean =
        height > 0f && y >= height - HOLD_ZONE_DP * density && y <= height
}
