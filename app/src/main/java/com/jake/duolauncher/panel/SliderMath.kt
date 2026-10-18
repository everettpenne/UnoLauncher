package com.jake.duolauncher.panel

/** Pure value <-> position rules shared by every iOS-style slider (the glass and Material paths alike), kept
 * apart from the composables so the mapping is unit-tested. All three agree, so a thumb drawn from [thumbX]
 * sits exactly where [fraction] of [value] in [range] belongs.
 */
internal object SliderMath {
    /** Where [value] sits inside [range], 0 to 1, clamped so an out-of-range value never draws off the track. */
    fun fraction(value: Float, range: ClosedFloatingPointRange<Float>): Float =
        if (range.endInclusive <= range.start) 0f
        else ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)

    /** The value a thumb at [fraction] (0 to 1) represents in [range]. */
    fun valueAt(fraction: Float, range: ClosedFloatingPointRange<Float>): Float =
        range.start + fraction.coerceIn(0f, 1f) * (range.endInclusive - range.start)

    /** The thumb's horizontal offset so its whole pill sits at [fraction] inside a [trackWidth] track. */
    fun thumbX(fraction: Float, trackWidth: Float, thumbWidth: Float): Float =
        fraction.coerceIn(0f, 1f) * (trackWidth - thumbWidth)

    /** The thumb's speed in fractions of the track per second when a finger moved [dxPx] pixels in [dtMillis]
     * milliseconds across a [trackWidth]-pixel track. Mirrored for right-to-left so a positive result always means
     * "toward the top of [valueAt]'s range" (the same sign the liquid stretch expects). */
    fun velocityFraction(dxPx: Float, dtMillis: Long, trackWidth: Float, isLtr: Boolean): Float =
        if (trackWidth <= 0f || dtMillis <= 0L) 0f
        else (dxPx / trackWidth) * 1000f / dtMillis * (if (isLtr) 1f else -1f)
}
