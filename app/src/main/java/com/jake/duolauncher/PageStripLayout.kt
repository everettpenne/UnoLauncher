package com.jake.duolauncher

import kotlin.math.floor

/** Item geometry of the page-dots strip: an optional Discover button, one dot per Home page, and
 * the All apps button. The selection lens needs to know where each item sits, so the widths
 * here are the single source for both the strip's layout and the lens's position.
 *
 * Logical pages follow the pager: Discover is -1, Home pages are 0 until `dots`, and All apps is
 * `dots`.
 */
internal object PageStripLayout {
    const val ICON_DP = 32f
    const val DOT_DP = 28f
    const val LENS_WIDTH_DP = 40f
    const val LENS_HEIGHT_DP = 28f
    /** More pages than this collapse to a "2 / 9" counter, which has no per-page items. */
    const val MAX_DOTS = 6

    /** Item widths in dp, left to right. */
    fun widths(showCompass: Boolean, dots: Int): List<Float> =
        buildList {
            if (showCompass) add(ICON_DP)
            repeat(dots) { add(DOT_DP) }
            add(ICON_DP)
        }

    /** Item centres in dp from the strip's left edge. */
    fun centers(widths: List<Float>): List<Float> {
        var left = 0f
        return widths.map { width -> (left + width / 2f).also { left += width } }
    }

    /** The index of the item at [x] dp from the strip's left edge (clamped to the ends). */
    fun itemAt(x: Float, widths: List<Float>): Int {
        var edge = 0f
        widths.forEachIndexed { index, width ->
            edge += width
            if (x < edge) return index
        }
        return widths.lastIndex
    }

    private fun firstItemPage(showCompass: Boolean) = if (showCompass) -1 else 0

    /** The logical page an item selects. */
    fun pageFor(item: Int, showCompass: Boolean): Int = item + firstItemPage(showCompass)

    /** The item for a logical page, or null when that page has no item (Discover while it's hidden). */
    fun itemFor(page: Int, showCompass: Boolean, dots: Int): Int? {
        val item = page - firstItemPage(showCompass)
        return item.takeIf { it in 0..(if (showCompass) dots + 1 else dots) }
    }

    /** Where the lens centre sits for a pager [position] in page units (fractional while the pager
     * moves), in dp from the strip's left edge. Positions past either end hold at that end item.
     */
    fun centerAt(position: Float, widths: List<Float>, showCompass: Boolean): Float {
        val centers = centers(widths)
        val t = (position - firstItemPage(showCompass)).coerceIn(0f, centers.lastIndex.toFloat())
        val lower = floor(t).toInt().coerceIn(0, centers.lastIndex)
        val upper = (lower + 1).coerceAtMost(centers.lastIndex)
        val fraction = t - lower
        return centers[lower] + (centers[upper] - centers[lower]) * fraction
    }
}
