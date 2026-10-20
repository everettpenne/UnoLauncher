package com.jake.duolauncher.island

/**
 * Where the system status bar's icons sit, so the island's edges can fall in the gaps between them and never cut an icon in half.
 *
 * Measured on a Pixel 10a (2.625 density) from screenshots with the island off and on. On the left the clock is followed by the notification
 * icons, one every [ICON_PITCH_DP]; on the right the icons are packed against the screen's right edge, so their gaps are a fixed distance from
 * it however many extra icons (do not disturb, Bluetooth, VPN, alarm) are showing. Android lays the notification icons out only around the
 * camera hole, and the island is much wider than the hole, so icons run under it. An icon the island covers wholly is fine; an icon it
 * covers half is the failure. Putting every island edge in a gap, a little way inside it ([INSET_DP]) so the island's rounded ends clear
 * the icons beside them, keeps every icon either wholly covered or wholly clear, whatever is showing.
 *
 * Everything here is in dp and pure, so it is unit-tested apart from the screen.
 */
internal object StatusBarGrid {
    /** How far inside a gap an island's edge sits, in dp: the gap is about 8 dp wide, and a rounded end falls back a little at the corners. */
    const val INSET_DP = 1.5f
    /** The middle of the gap between the clock and the first notification icon, from the left edge, with a one-digit hour. */
    const val CLOCK_GAP_DP = 66.3f
    /** The middle of the gap between the first and second notification icons, from the left edge, with a one-digit hour. */
    const val FIRST_ICON_GAP_DP = 88.8f
    /** The distance from one notification icon to the next. */
    const val ICON_PITCH_DP = 21.85f
    /** How much wider the clock is with a two-digit hour (10, 11, 12 or any 24-hour time): every icon after it moves right by this. */
    const val DIGIT_DP = 7.2f
    /** The middle of each gap on the right, as a distance from the screen's right edge, nearest the camera first: before the do-not-disturb
     * slot, between it and the signal icon, between signal and Wi-Fi, between Wi-Fi and the battery, and past the battery. */
    private val RIGHT_GAPS_FROM_EDGE_DP = floatArrayOf(134.9f, 115.4f, 87.6f, 64.4f, 17.5f)
    /** The most left-hand gaps worth listing: past these the gap is under the camera. */
    private const val LEFT_GAP_COUNT = 9
    /** The room an island keeps to the camera hole at least, in dp. */
    private const val HOLE_WRAP_DP = 6f

    /** The x of each left-hand gap, in dp from the left edge: index 0 is after the clock, index 1 after the first icon, and so on. [shiftDp]
     * is the clock's extra width ([DIGIT_DP] with a two-digit hour, else 0). */
    fun leftGaps(shiftDp: Float): List<Float> =
        listOf(CLOCK_GAP_DP + shiftDp) + (0 until LEFT_GAP_COUNT - 1).map { FIRST_ICON_GAP_DP + shiftDp + it * ICON_PITCH_DP }

    /** The x of each right-hand gap, in dp from the left edge, nearest the camera first. */
    fun rightGaps(screenWidthDp: Float): List<Float> = RIGHT_GAPS_FROM_EDGE_DP.map { screenWidthDp - it }

    /** A stretch of the status bar an island covers, in dp from the left edge. */
    data class Span(val left: Float, val right: Float) { val width: Float get() = right - left }

    /** Where the islands go: the main island, and the capsule on each side of it when there is one. */
    data class Layout(val pillLeft: Float, val pillRight: Float, val left: Span?, val right: Span?)

    /**
     * Snaps the main island to the gaps. [desiredLeft] and [desiredRight] are the edges it would have anyway (dp); the island grows outward to
     * the nearest gap on each side (it never shrinks by more than [INSET_DP]), and never past the clock on the left or the battery on the right.
     * [holeLeft] and [holeRight] are the camera hole, which it must still wrap. With [wantLeft] a capsule is placed to its left, covering two
     * icon slots, so the island stays at least that far from the clock; with [wantRight] one is placed to its right, over as few slots as make
     * it at least [capsuleMinWidth] wide. [dots] privacy dots sit inside the island's right end, which asks it for more room.
     */
    fun layout(desiredLeft: Float, desiredRight: Float, screenWidthDp: Float, clockShiftDp: Float, holeLeft: Float, holeRight: Float,
        wantLeft: Boolean, wantRight: Boolean, dots: Int, capsuleMinWidth: Float): Layout {
        val lg = leftGaps(clockShiftDp)
        val rg = rightGaps(screenWidthDp)
        // Left: the nearest gap at or beyond (left of) the wanted edge, that still leaves the hole wrapped.
        var j = lg.indexOfLast { it <= desiredLeft }.coerceAtLeast(0)
        val maxJ = lg.indexOfLast { it <= holeLeft - HOLE_WRAP_DP }.coerceAtLeast(0)
        j = j.coerceAtMost(maxJ)
        if (wantLeft) j = j.coerceIn(minOf(2, maxJ), maxJ)
        // Right: the nearest gap at or beyond (right of) the wanted edge; dots need more room, asked for by skipping gaps.
        val minI = when { dots <= 0 -> 0; dots <= 2 -> 1; else -> 2 }
        var i = rg.indexOfFirst { it >= desiredRight && it >= holeRight + HOLE_WRAP_DP }
        if (i >= 0) i = i.coerceAtLeast(minI)
        val pillLeft = lg[j] + INSET_DP
        val pillRight = if (i >= 0) rg[i] - INSET_DP else desiredRight
        val left = if (wantLeft && j >= 2) Span(lg[j - 2] + INSET_DP, lg[j] - INSET_DP) else null
        val right = if (wantRight && i >= 0) {
            val k = (i + 1 until rg.size).firstOrNull { rg[it] - rg[i] - 2 * INSET_DP >= capsuleMinWidth }
            k?.let { Span(rg[i] + INSET_DP, rg[it] - INSET_DP) }
        } else null
        return Layout(pillLeft, pillRight, left, right)
    }

    /** The layout when the status bar's icons are not known to be where they are on a Pixel (no top camera, say): the main island as wanted,
     * and each capsule beside it with a small gap, [capsuleMinWidth] wide. */
    fun flush(desiredLeft: Float, desiredRight: Float, wantLeft: Boolean, wantRight: Boolean, capsuleMinWidth: Float, gap: Float = 6f): Layout =
        Layout(desiredLeft, desiredRight,
            if (wantLeft) Span(desiredLeft - gap - capsuleMinWidth, desiredLeft - gap) else null,
            if (wantRight) Span(desiredRight + gap, desiredRight + gap + capsuleMinWidth) else null)
}
