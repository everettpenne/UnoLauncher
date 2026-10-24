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
/** A person's correction to the measured grid, in dp: the left gaps moved by [leftShift] (and their spacing changed by [pitchDelta]), the
 * right gaps moved by [rightShift] (positive is rightward). All zero is the Pixel 10a as measured. */
internal data class GridCalibration(val leftShift: Float = 0f, val pitchDelta: Float = 0f, val rightShift: Float = 0f) {
    fun clamped() = GridCalibration(leftShift.coerceIn(-30f, 30f), pitchDelta.coerceIn(-6f, 6f), rightShift.coerceIn(-30f, 30f))
}

internal object StatusBarGrid {
    /** How far inside a gap a seam's edges sit, in dp: where a capsule meets the main island, both edges share one gap, 3 dp apart. */
    const val INSET_DP = 1.5f
    /** How far past a gap's middle an *outer* edge sits, away from the island, in dp. An island's end is round, so at the height of an icon's
     * top and bottom it falls back from its edge by about 1.6 dp; an edge in the middle of a narrow gap (signal to Wi-Fi is only 6 dp) then
     * showed a sliver of the icon it covers above and below its end. Pushing the edge outward a little covers the icon at the corners too. */
    const val OUTER_BIAS_DP = 1f
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

    /** How much wider the clock is than with a one-digit hour, for the hour now ([hour12] is Calendar.HOUR, 0..11): any 24-hour time and
     * 10, 11 and 12 o'clock have two digits. */
    fun clockShiftDp(hour12: Int, is24Hour: Boolean): Float = if (is24Hour || hour12 == 0 || hour12 >= 10) DIGIT_DP else 0f

    /** The x of each left-hand gap, in dp from the left edge: index 0 is after the clock, index 1 after the first icon, and so on. [shiftDp]
     * is the clock's extra width ([DIGIT_DP] with a two-digit hour, else 0). */
    fun leftGaps(shiftDp: Float, cal: GridCalibration = GridCalibration()): List<Float> =
        listOf(CLOCK_GAP_DP + shiftDp + cal.leftShift) +
            (0 until LEFT_GAP_COUNT - 1).map { FIRST_ICON_GAP_DP + shiftDp + cal.leftShift + it * (ICON_PITCH_DP + cal.pitchDelta) }

    /** The x of each right-hand gap, in dp from the left edge, nearest the camera first. */
    fun rightGaps(screenWidthDp: Float, cal: GridCalibration = GridCalibration()): List<Float> = RIGHT_GAPS_FROM_EDGE_DP.map { screenWidthDp - it + cal.rightShift }

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
     *
     * [measuredLeft] and [measuredRight], when given, are gaps read from the status bar itself ([StatusBarScan.gaps]), in the same order as
     * [leftGaps] and [rightGaps]; they replace the measured Pixel grid and its calibration on that side.
     */
    fun layout(desiredLeft: Float, desiredRight: Float, screenWidthDp: Float, clockShiftDp: Float, holeLeft: Float, holeRight: Float,
        wantLeft: Boolean, wantRight: Boolean, dots: Int, capsuleMinWidth: Float, cal: GridCalibration = GridCalibration(),
        measuredLeft: List<Float>? = null, measuredRight: List<Float>? = null): Layout {
        val lg = measuredLeft?.takeIf { it.isNotEmpty() } ?: leftGaps(clockShiftDp, cal)
        val rg = measuredRight?.takeIf { it.isNotEmpty() } ?: rightGaps(screenWidthDp, cal)
        // Left: the nearest gap at or beyond (left of) the wanted edge, that still leaves the hole wrapped.
        var j = lg.indexOfLast { it <= desiredLeft }.coerceAtLeast(0)
        val maxJ = lg.indexOfLast { it <= holeLeft - HOLE_WRAP_DP }.coerceAtLeast(0)
        j = j.coerceAtMost(maxJ)
        if (wantLeft) j = j.coerceIn(minOf(2, maxJ), maxJ)
        // Right: the nearest gap at or beyond (right of) the wanted edge; dots need more room, asked for by skipping gaps.
        val minI = when { dots <= 0 -> 0; dots <= 2 -> 1; else -> 2 }
        var i = rg.indexOfFirst { it >= desiredRight && it >= holeRight + HOLE_WRAP_DP }
        if (i >= 0) i = i.coerceAtLeast(minI)
        val left = if (wantLeft && j >= 2) Span(lg[j - 2] - OUTER_BIAS_DP, lg[j] - INSET_DP) else null
        val right = if (wantRight && i >= 0) {
            val k = (i + 1 until rg.size).firstOrNull { rg[it] - rg[i] - INSET_DP + OUTER_BIAS_DP >= capsuleMinWidth }
            k?.let { Span(rg[i] + INSET_DP, rg[it] + OUTER_BIAS_DP) }
        } else null
        // An edge shared with a capsule is half of a seam; an edge with nothing beyond it is an outer edge.
        val pillLeft = if (left != null) lg[j] + INSET_DP else lg[j] - OUTER_BIAS_DP
        val pillRight = if (i < 0) desiredRight else if (right != null) rg[i] - INSET_DP else rg[i] + OUTER_BIAS_DP
        return Layout(pillLeft, pillRight, left, right)
    }

    /** How far an island's edge keeps from an icon, in dp: its end is round and falls back about 1.6 dp at an icon's top and bottom. */
    const val EDGE_CLEAR_DP = 1.75f
    /** The seam between the main island and a capsule beside it, in dp. */
    const val SEAM_DP = 3f
    /** The most a centred island may grow past the width it wants, each side, in dp, to find edges that clear the icons on both sides. Without a
     * limit it kept growing until a clear spot lined up on both sides, which with the grid's conservative slots (every notification slot counted
     * as full) made the resting island far wider than it needed to be; past this it snaps each edge on its own instead. */
    const val MAX_SYMMETRIC_GROW_DP = 8f
    /** The least room either side of the island to the screen's edge, in dp. */
    private const val SCREEN_MARGIN_DP = 6f

    /**
     * The status bar's icons as the measured grid has them, for when the bar itself has not been read: on the left the clock and each notification
     * icon between two gaps, on the right each slot between two gaps (the battery last). Conservative: a slot the grid keeps (do not disturb, say)
     * counts as an icon even when nothing is showing there.
     */
    fun gridIcons(screenWidthDp: Float, clockShiftDp: Float, cal: GridCalibration = GridCalibration()): List<Span> {
        val lg = leftGaps(clockShiftDp, cal); val rg = rightGaps(screenWidthDp, cal)
        val half = 2.5f
        val left = listOf(Span(lg[0] - 30f, lg[0] - half)) + (0 until lg.size - 1).map { Span(lg[it] + half, lg[it + 1] - half) }
        val right = (0 until rg.size - 1).map { Span(rg[it] + half, rg[it + 1] - half) }
        return left + right
    }

    /**
     * The island symmetric about the camera: one half-width for both sides, the smallest at least as wide as it wants (and as its privacy dots
     * need) for which *both* edges fall clear of every icon, so each icon is wholly under the island or wholly beside it. Snapping each edge to its
     * own nearest gap ([layout]) left the island a few dp longer on one side than the other, off centre on the camera; with the icons' real places
     * (or the grid's) the edges can instead be chosen together. The clock and the battery are never covered.
     *
     * A capsule beside it keeps [SEAM_DP] from the island and grows outward to the first point clear of icons at least [capsuleMinWidth] away.
     * Null when no symmetric width within [MAX_SYMMETRIC_GROW_DP] of the wanted one fits (the caller then snaps each edge on its own).
     */
    fun symmetric(desiredLeft: Float, desiredRight: Float, holeLeft: Float, holeRight: Float, icons: List<Span>, screenWidthDp: Float,
        wantLeft: Boolean, wantRight: Boolean, dots: Int, capsuleMinWidth: Float): Layout? {
        val cx = (holeLeft + holeRight) / 2f
        val m = EDGE_CLEAR_DP
        val leftIcons = icons.filter { (it.left + it.right) / 2f < cx }.sortedBy { it.left }
        val rightIcons = icons.filter { (it.left + it.right) / 2f >= cx }.sortedBy { it.left }
        // Never over the clock (the first icon on the left) or the battery (the last on the right).
        val minX = (leftIcons.firstOrNull()?.right?.plus(m)) ?: SCREEN_MARGIN_DP
        val maxX = (rightIcons.lastOrNull()?.left?.minus(m)) ?: (screenWidthDp - SCREEN_MARGIN_DP)
        fun freeLeft(x: Float) = x >= minX && leftIcons.none { x > it.left - m && x < it.right + m }
        fun freeRight(x: Float) = x <= maxX && rightIcons.none { x > it.left - m && x < it.right + m }
        val dotRoom = if (dots > 0) IslandPrivacy.widthDp(dots) else 0f
        val wanted = maxOf(cx - desiredLeft, desiredRight - cx, (holeRight - holeLeft) / 2f + HOLE_WRAP_DP) + dotRoom
        // The half-widths at which an edge just clears an icon, from either side; the answer is one of these or the wanted width itself.
        val candidates = (listOf(wanted) + leftIcons.map { cx - (it.left - m) } + rightIcons.map { (it.right + m) - cx })
            .filter { it >= wanted && it <= wanted + MAX_SYMMETRIC_GROW_DP }.sorted()
        val half = candidates.firstOrNull { freeLeft(cx - it) && freeRight(cx + it) } ?: return null
        return withCapsules(cx - half, cx + half, icons, holeLeft, holeRight, screenWidthDp, wantLeft, wantRight, capsuleMinWidth)
    }

    /**
     * [layout] made symmetric about the camera, at its wider half: the fallback when [symmetric] finds no width within its growth limit.
     * Snapping each edge on its own (the old fallback) left the island visibly off centre (5 px to the right over apps on a Pixel 10a);
     * using the wider half keeps both edges at least as clear as the per-edge snap left the further one, and the island centred.
     */
    fun centred(layout: Layout, holeLeft: Float, holeRight: Float, icons: List<Span>, screenWidthDp: Float, wantLeft: Boolean,
        wantRight: Boolean, capsuleMinWidth: Float): Layout {
        val cx = (holeLeft + holeRight) / 2f
        val half = maxOf(cx - layout.pillLeft, layout.pillRight - cx).coerceAtMost(minOf(cx, screenWidthDp - cx) - SCREEN_MARGIN_DP)
        return withCapsules(cx - half, cx + half, icons, holeLeft, holeRight, screenWidthDp, wantLeft, wantRight, capsuleMinWidth)
    }

    /** [pillLeft]..[pillRight] with a capsule on each wanted side: [SEAM_DP] from the island, out to the first point clear of [icons] at least
     * [capsuleMinWidth] away, never over the clock or the battery; a side with no room gets none. */
    private fun withCapsules(pillLeft: Float, pillRight: Float, icons: List<Span>, holeLeft: Float, holeRight: Float, screenWidthDp: Float,
        wantLeft: Boolean, wantRight: Boolean, capsuleMinWidth: Float): Layout {
        val cx = (holeLeft + holeRight) / 2f
        val m = EDGE_CLEAR_DP
        val leftIcons = icons.filter { (it.left + it.right) / 2f < cx }
        val rightIcons = icons.filter { (it.left + it.right) / 2f >= cx }
        val minX = leftIcons.minByOrNull { it.left }?.right?.plus(m) ?: SCREEN_MARGIN_DP
        val maxX = rightIcons.maxByOrNull { it.right }?.left?.minus(m) ?: (screenWidthDp - SCREEN_MARGIN_DP)
        fun freeLeft(x: Float) = x >= minX && leftIcons.none { x > it.left - m && x < it.right + m }
        fun freeRight(x: Float) = x <= maxX && rightIcons.none { x > it.left - m && x < it.right + m }
        fun outward(inner: Float, dir: Int, free: (Float) -> Boolean, limit: Float): Float? {
            var x = inner + dir * capsuleMinWidth
            val stops = (if (dir < 0) leftIcons else rightIcons).map { if (dir < 0) it.left - m else it.right + m }
                .filter { if (dir < 0) it <= x else it >= x }.sortedBy { kotlin.math.abs(it - x) }
            if (!free(x)) x = stops.firstOrNull { free(it) } ?: return null
            return x.takeIf { if (dir < 0) it >= limit else it <= limit }
        }
        val left = if (wantLeft) (pillLeft - SEAM_DP).takeIf { freeLeft(it) }?.let { inner ->
            outward(inner, -1, ::freeLeft, minX)?.let { Span(it, inner) } } else null
        val right = if (wantRight) (pillRight + SEAM_DP).takeIf { freeRight(it) }?.let { inner ->
            outward(inner, 1, ::freeRight, maxX)?.let { Span(inner, it) } } else null
        return Layout(pillLeft, pillRight, left, right)
    }

    /**
     * Each edge on its own, with the status bar's real icons: an edge stays where the island wants it unless that is inside an icon, and then
     * moves outward just past the icon. Snapping to the gaps' middles ([layout] with read gaps) instead sent an edge all the way out to the gap
     * after the last icon when the icons nearby were few (a clock and one notification icon), making the island far wider than it wanted.
     * Capsules are placed as [symmetric] places them.
     */
    fun nearest(desiredLeft: Float, desiredRight: Float, holeLeft: Float, holeRight: Float, icons: List<Span>, screenWidthDp: Float,
        wantLeft: Boolean, wantRight: Boolean, dots: Int, capsuleMinWidth: Float): Layout {
        val cx = (holeLeft + holeRight) / 2f
        val m = EDGE_CLEAR_DP
        val leftIcons = icons.filter { (it.left + it.right) / 2f < cx }
        val rightIcons = icons.filter { (it.left + it.right) / 2f >= cx }
        val dotRoom = if (dots > 0) IslandPrivacy.widthDp(dots) else 0f
        var l = minOf(desiredLeft, holeLeft - HOLE_WRAP_DP)
        var r = maxOf(desiredRight, holeRight + HOLE_WRAP_DP) + dotRoom
        // Step outward past any icon an edge lands in (icons can be packed, so repeat until clear).
        repeat(icons.size + 1) { leftIcons.firstOrNull { l > it.left - m && l < it.right + m }?.let { l = it.left - m } }
        repeat(icons.size + 1) { rightIcons.firstOrNull { r > it.left - m && r < it.right + m }?.let { r = it.right + m } }
        // Never over the clock or the battery: if stepping out reached them, stop just inside instead.
        leftIcons.minByOrNull { it.left }?.let { clock -> if (l < clock.right + m) l = leftIcons.filter { it.right + m <= desiredLeft }.maxOfOrNull { it.right + m } ?: clock.right + m }
        rightIcons.maxByOrNull { it.right }?.let { battery -> if (r > battery.left - m) r = rightIcons.filter { it.left - m >= desiredRight }.minOfOrNull { it.left - m } ?: battery.left - m }
        return withCapsules(l, r, icons, holeLeft, holeRight, screenWidthDp, wantLeft, wantRight, capsuleMinWidth)
    }

    /** The layout when the status bar's icons are not known to be where they are on a Pixel (no top camera, say): the main island as wanted,
     * and each capsule beside it with a small gap, [capsuleMinWidth] wide. */
    fun flush(desiredLeft: Float, desiredRight: Float, wantLeft: Boolean, wantRight: Boolean, capsuleMinWidth: Float, gap: Float = 6f): Layout =
        Layout(desiredLeft, desiredRight,
            if (wantLeft) Span(desiredLeft - gap - capsuleMinWidth, desiredLeft - gap) else null,
            if (wantRight) Span(desiredRight + gap, desiredRight + gap + capsuleMinWidth) else null)
}
