package com.jake.duolauncher.island

/**
 * How wide the pill is while it shows something, worked out from what it shows rather than fixed. The pill stays symmetric about the
 * camera: each side is as wide as the wider of its two contents (the glyph on the left, the time or status on the right), so the two
 * sides mirror each other. Pure, so the rules are unit-tested; DynamicIsland measures the text and passes widths in dp.
 *
 * It replaced a fixed content width (and, for an event flash, an estimate from the number of characters): a short "1:23" sat in a pill
 * built for far more, and a long app name ran past the pill's end.
 */
internal object IslandPillWidth {
    /** Room between the camera and the content beside it, in dp. */
    const val CAMERA_GAP_DP = 6f
    /** The pill grows and shrinks in steps of this many dp, so a small change in text never nudges it. */
    const val STEP_DP = 4f
    /** Never closer than this to the screen's edge, in dp. */
    const val SCREEN_MARGIN_DP = 8f

    /**
     * The text to measure for [text]: every digit replaced by the widest one, so a counting clock ("9:59", "10:00", "1:23:45") is
     * measured at the width its digits can take and the pill changes only when a digit is added, never as the seconds tick.
     */
    fun template(text: String): String = text.map { if (it.isDigit()) '8' else it }.joinToString("")

    /**
     * Half the pill's width, from the camera's centre, in dp. [holeHalfDp] is half the camera's width; [leadDp] and [trailDp] are the
     * widths of what sits left and right of it (a glyph, the measured text); [endInsetDp] is the inset of the content from the pill's
     * rounded end. Never less than [minHalfDp] (the resting pill), never wider than the screen allows ([screenHalfDp] is half the
     * screen's width, measured from the camera's centre to the nearer edge).
     */
    fun halfDp(holeHalfDp: Float, leadDp: Float, trailDp: Float, endInsetDp: Float, minHalfDp: Float, screenHalfDp: Float): Float {
        val side = maxOf(leadDp, trailDp) + endInsetDp + CAMERA_GAP_DP
        val stepped = kotlin.math.ceil((holeHalfDp + side) / STEP_DP) * STEP_DP
        return stepped.coerceAtLeast(minHalfDp).coerceAtMost(maxOf(minHalfDp, screenHalfDp - SCREEN_MARGIN_DP))
    }
}
