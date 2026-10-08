package com.jake.duolauncher

import android.view.View
import android.view.WindowInsets
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** An axis-aligned rectangle in pixels. The island works in *window* pixels, the space that
 * [android.view.DisplayCutout] reports in, and only converts at the last step.
 */
internal data class PxRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f
}

/** Where the island sits, in window pixels, and where the camera hole falls inside it
 * ([hole] is in the island's own coordinates, origin at its top-left).
 */
internal data class IslandFrame(
    val left: Float, val top: Float, val width: Float, val height: Float, val hole: PxRect?,
)

/** What the window reports about the camera and system bars. */
internal data class IslandEnvironment(
    val cutout: PxRect?, val screenWidth: Float, val statusBarHeight: Float,
    val dockWidthPx: Float = 0f,
)

/** Pure layout for the dynamic island: wraps the camera hole, whatever its position or size.
 * [scale] (0..1) sizes the collapsed capsule from compact to large; the midpoint matches
 * the original fixed constants.
 */
internal object IslandGeometry {
    /** Room either side of the hole for the collapsed face's content, in dp. */
    private const val SLOT_DP = 58f
    /** Clear space kept above the island, so a pill never runs to the very top of the screen. */
    private const val TOP_MARGIN_DP = 6f
    /** Island left around the hole at the smallest size setting, in dp. */
    private const val MIN_RING_DP = 3f
    private const val EXPANDED_WIDTH_DP = 336f
    private const val EDGE_MARGIN_DP = 8f
    private const val NO_CUTOUT_WIDTH_DP = 120f
    private const val NO_CUTOUT_HEIGHT_DP = 34f

    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** Extra collapsed width, in dp, for an event title of [titleChars] characters to fit beside the
     * hole: the text (about 6.4 dp a character at 12 sp semibold, plus padding) minus the slot the
     * capsule already has on that side at this size setting. Never less than a small, readable grow.
     */
    fun eventExtraWidthDp(titleChars: Int, scale: Float): Float {
        val slot = mix(44f, 80f, scale.coerceIn(0f, 1f))
        return max(24f, 2f * (6.4f * titleChars + 8f - slot)).coerceAtMost(140f)
    }

    /** The cutout that holds the front camera: a rectangle near the top of the screen, and the one
     * nearest the horizontal center when there are several (side waterfalls and rounded-corner
     * rectangles are ignored).
     */
    fun pickCutout(rects: List<PxRect>, screenWidth: Float, screenHeight: Float): PxRect? =
        rects.filter { it.width > 1f && it.height > 1f && it.centerY < screenHeight * .15f }
            .minByOrNull { abs(it.centerX - screenWidth / 2f) }

    /** The visible hole inside the cutout's bounding [rect]. Android reports the bounding
     * rectangle, which on many phones is much taller than the hole (often starting at y = 0);
     * the cutout *path* hugs the hole itself. Sizing the island from the rectangle made it run
     * to the top of the screen and made the size setting a no-op, so use the path's bounds when
     * they are a sensible part of the rectangle.
     */
    fun refine(rect: PxRect, pathBounds: PxRect?): PxRect {
        if (pathBounds == null) return rect
        val inside = PxRect(max(rect.left, pathBounds.left), max(rect.top, pathBounds.top),
            min(rect.right, pathBounds.right), min(rect.bottom, pathBounds.bottom))
        return if (inside.width >= 4f && inside.height >= 4f) inside else rect
    }

    /** [progress] is 0 for the collapsed capsule and 1 for the expanded panel. */
    /** [extraBodyDp] adds height to the expanded panel for optional rows (the playback controls);
     * [extraWidthDp] widens the collapsed capsule so an event's title fits, as iOS's island grows to show one.
     */
    fun frame(env: IslandEnvironment, density: Float, progress: Float, scale: Float = .5f,
        extraBodyDp: Float = 0f, extraWidthDp: Float = 0f): IslandFrame {
        val d = density
        val p = progress.coerceIn(0f, 1f)
        val size = scale.coerceIn(0f, 1f)
        val margin = EDGE_MARGIN_DP * d
        val cutout = env.cutout
        val centerX: Float
        val top: Float
        val collapsedWidth: Float
        val collapsedHeight: Float
        if (cutout != null) {
            centerX = cutout.centerX
            // Symmetric about the hole. The size setting spans the whole range that fits: from a
            // few dp of island around the hole up to the most the screen allows while keeping
            // TOP_MARGIN_DP clear above it, so the slider always has an effect and the pill never
            // runs to the top edge. A hole already close to the top simply gets the least ring.
            val holeHalf = cutout.height / 2f
            val room = max(0f, (cutout.centerY - TOP_MARGIN_DP * d) - holeHalf)
            val ring = mix(min(MIN_RING_DP * d, room), room, size)
            val half = holeHalf + ring
            top = cutout.centerY - half
            collapsedHeight = half * 2f
            collapsedWidth = cutout.width + (2f * mix(44f, 80f, size) + extraWidthDp) * d
        } else {
            centerX = env.screenWidth / 2f
            top = env.statusBarHeight + 8f * d
            collapsedHeight = mix(28f, 44f, size) * d
            collapsedWidth = (mix(96f, 152f, size) + extraWidthDp) * d
        }
        // The expanded panel hangs below the hole; its body starts under it.
        val holeBottom = cutout?.bottom ?: (top + collapsedHeight)
        val expandedHeight = (holeBottom - top) + (mix(96f, 128f, size) + extraBodyDp) * d
        // The expanded panel must clear the dock/rail strip: cap its width so it can never
        // reach the glass column on the right, whatever the dock-width preset.
        val dockClearance = env.dockWidthPx + 16f * d
        val expandedWidth = min(EXPANDED_WIDTH_DP * d,
            max(collapsedWidth, env.screenWidth - 2f * dockClearance))
        val width = collapsedWidth + (expandedWidth - collapsedWidth) * p
        val height = collapsedHeight + (expandedHeight - collapsedHeight) * p
        // Keep the island off the screen edges, but never at the cost of the hole: it must always
        // wrap the camera, so a hole near (or touching) an edge pulls the island over it.
        var left = (centerX - width / 2f).coerceIn(margin, max(margin, env.screenWidth - width - margin))
        if (cutout != null) {
            val wrap = 4f * d
            left = left.coerceIn(cutout.right + wrap - width, cutout.left - wrap)
        }
        left = left.coerceIn(0f, max(0f, env.screenWidth - width))
        return IslandFrame(left, top, width, height,
            cutout?.let { PxRect(it.left - left, it.top - top, it.right - left, it.bottom - top) })
    }
}

/** Reads the camera cutout from a display directly: authoritative for overlay windows, which
 * do not reliably receive the cutout in their own window insets.
 */
internal fun readDisplayCutout(display: android.view.Display, screenWidth: Float, screenHeight: Float): PxRect? {
    val rects = runCatching { display.cutout?.boundingRects.orEmpty() }.getOrDefault(emptyList())
        .map { PxRect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
    return IslandGeometry.pickCutout(rects, screenWidth, screenHeight)
}

/** Reads the camera cutout and bars from the live window. Cheap enough to call on layout. */
internal fun readIslandEnvironment(view: View, dockWidthPx: Float = 0f,
    screenWidth: Int = view.rootView.width, screenHeight: Int = view.rootView.height,
    statusBarHeightPx: Int = -1): IslandEnvironment {
    val insets = view.rootWindowInsets
    val width = screenWidth.toFloat()
    val height = screenHeight.toFloat()
    val displayCutout = insets?.displayCutout
    val rects = displayCutout?.boundingRects.orEmpty()
        .map { PxRect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
    val picked = IslandGeometry.pickCutout(rects, width, height)
    // Tighten the reported rectangle to the visible hole using the cutout's own outline.
    val hole = picked?.let { rect ->
        val bounds = android.graphics.RectF()
        val path = displayCutout?.cutoutPath
        if (path == null) rect else {
            path.computeBounds(bounds, true)
            IslandGeometry.refine(rect, PxRect(bounds.left, bounds.top, bounds.right, bounds.bottom))
        }
    }
    return IslandEnvironment(
        cutout = hole,
        screenWidth = width,
        statusBarHeight = (if (statusBarHeightPx >= 0) statusBarHeightPx
            else insets?.getInsets(WindowInsets.Type.statusBars())?.top ?: 0).toFloat(),
        dockWidthPx = dockWidthPx,
    )
}
