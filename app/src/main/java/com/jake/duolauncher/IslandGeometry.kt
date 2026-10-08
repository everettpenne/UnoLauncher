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
    /** Which screen edge the camera is on. TOP is the usual portrait case; LEFT and RIGHT mean the phone is turned sideways. */
    val side: IslandSide = IslandSide.TOP,
)

/** The screen edge the front camera sits on. */
internal enum class IslandSide { TOP, LEFT, RIGHT }

/** What the window reports about the camera and system bars. */
internal data class IslandEnvironment(
    val cutout: PxRect?, val screenWidth: Float, val statusBarHeight: Float,
    val dockWidthPx: Float = 0f,
    /** 0 when unknown, in which case a cutout is always treated as being on the top edge. */
    val screenHeight: Float = 0f,
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
    /** Clearance between the hole and the island's edge that the island must keep at all times. */
    private const val WRAP_DP = 4f
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
    fun pickCutout(rects: List<PxRect>, screenWidth: Float, screenHeight: Float): PxRect? {
        val real = rects.filter { it.width > 1f && it.height > 1f }
        real.filter { it.centerY < screenHeight * .15f }.minByOrNull { abs(it.centerX - screenWidth / 2f) }?.let { return it }
        // Turned sideways the camera is on the left or right edge. Only a small rectangle counts: a waterfall edge or a
        // rounded-corner strip runs most of the way down the screen and is not the camera.
        return real.filter { (it.centerX < screenWidth * .15f || it.centerX > screenWidth * .85f) &&
                it.height < screenHeight * .35f && it.width < screenWidth * .35f }
            .minByOrNull { abs(it.centerY - screenHeight / 2f) }
    }

    /** Which edge [cutout] is on, given the screen. */
    fun sideOf(cutout: PxRect?, screenWidth: Float, screenHeight: Float): IslandSide = when {
        cutout == null || screenHeight <= 0f || cutout.centerY < screenHeight * .15f -> IslandSide.TOP
        cutout.centerX < screenWidth / 2f -> IslandSide.LEFT
        else -> IslandSide.RIGHT
    }

    /** The camera hole from every cutout rectangle the display reports plus the bounds of the cutout path: the picked camera
     * rectangle, tightened to the visible hole. Both readers (Home's window insets and the overlay's display) must go through
     * this: the raw rectangle is often tall and starts at the very top, which leaves no room for the island's size setting.
     */
    fun holeFor(rects: List<PxRect>, pathBounds: PxRect?, screenWidth: Float, screenHeight: Float): PxRect? =
        pickCutout(rects, screenWidth, screenHeight)?.let { refine(it, pathBounds) }

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
    /** [compactness] (0..1) narrows the collapsed capsule from its full width, with room beside the hole for content, to just a
     * ring around the hole. The overlay over other apps is compact while it has nothing to say, so it does not cover the system
     * status bar's own icons; it is 0 (full width) on Home and whenever there is content to show.
     */
    fun frame(env: IslandEnvironment, density: Float, progress: Float, scale: Float = .5f,
        extraBodyDp: Float = 0f, extraWidthDp: Float = 0f, compactness: Float = 0f): IslandFrame {
        val d = density
        val p = progress.coerceIn(0f, 1f)
        val size = scale.coerceIn(0f, 1f)
        val margin = EDGE_MARGIN_DP * d
        val cutout = env.cutout
        val side = sideOf(cutout, env.screenWidth, env.screenHeight)
        if (cutout != null && side != IslandSide.TOP)
            return sideFrame(env, cutout, side, d, p, size, extraBodyDp, extraWidthDp)
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
            val c = compactness.coerceIn(0f, 1f)
            val sideRoom = (1f - c) * 2f * mix(44f, 80f, size) * d
            // The compact width is the hole plus a ring each side, never less than the wrap clearance used below: a pill
            // narrower than hole + 2 * wrap makes that clamp's range empty (min above max) and throws.
            collapsedWidth = cutout.width + sideRoom + c * 2f * max(ring, WRAP_DP * d) + extraWidthDp * d
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
            val wrap = WRAP_DP * d
            // The range is empty if the island is narrower than the hole plus its clearance; then keep it over the hole.
            val hi = cutout.left - wrap
            val lo = min(cutout.right + wrap - width, hi)
            left = left.coerceIn(lo, hi)
        }
        left = left.coerceIn(0f, max(0f, env.screenWidth - width))
        return IslandFrame(left, top, width, height,
            cutout?.let { PxRect(it.left - left, it.top - top, it.right - left, it.bottom - top) })
    }

    /** The island when the camera is on the left or right edge (phone turned sideways). It stays where the camera is: a vertical
     * pill hugging the hole, growing inward from the edge for an event or the full panel, centred on the camera.
     */
    private fun sideFrame(env: IslandEnvironment, cutout: PxRect, side: IslandSide, d: Float, p: Float, size: Float,
        extraBodyDp: Float, extraWidthDp: Float): IslandFrame {
        val ring = mix(MIN_RING_DP * d, 10f * d, size)
        val collapsedHeight = cutout.height + 2f * ring
        // The pill reaches from the screen edge (or just past the hole's outer side) to just past its inner side.
        val outerEdge = if (side == IslandSide.LEFT) max(0f, cutout.left - ring) else min(env.screenWidth, cutout.right + ring)
        val innerEdge = if (side == IslandSide.LEFT) cutout.right + ring else cutout.left - ring
        // An event widens it inward; the extra is eased in so it does not jump when the animation starts.
        val inward = (extraWidthDp + mix(44f, 80f, size) * min(1f, extraWidthDp / 24f)) * d
        val collapsedWidth = abs(innerEdge - outerEdge) + inward
        val dockClearance = env.dockWidthPx + 16f * d
        val expandedWidth = min(EXPANDED_WIDTH_DP * d, max(collapsedWidth, env.screenWidth - 2f * dockClearance))
        val expandedHeight = max(collapsedHeight, (mix(96f, 128f, size) + extraBodyDp + 24f) * d)
        val width = collapsedWidth + (expandedWidth - collapsedWidth) * p
        val height = collapsedHeight + (expandedHeight - collapsedHeight) * p
        val left = if (side == IslandSide.LEFT) outerEdge else outerEdge - width
        var top = cutout.centerY - height / 2f
        if (env.screenHeight > 0f) top = top.coerceIn(0f, max(0f, env.screenHeight - height))
        val clampedLeft = left.coerceIn(0f, max(0f, env.screenWidth - width))
        return IslandFrame(clampedLeft, top, width, height,
            PxRect(cutout.left - clampedLeft, cutout.top - top, cutout.right - clampedLeft, cutout.bottom - top), side)
    }
}

/** Reads the camera cutout from a display directly: authoritative for overlay windows, which
 * do not reliably receive the cutout in their own window insets.
 */
internal fun readDisplayCutout(display: android.view.Display, screenWidth: Float, screenHeight: Float): PxRect? {
    val cutout = runCatching { display.cutout }.getOrNull()
    val rects = cutout?.boundingRects.orEmpty()
        .map { PxRect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
    // The path hugs the hole itself; without it the island is sized from the (often much taller) bounding rectangle.
    val pathBounds = runCatching {
        cutout?.cutoutPath?.let { path -> android.graphics.RectF().also { path.computeBounds(it, true) } }
    }.getOrNull()?.let { PxRect(it.left, it.top, it.right, it.bottom) }
    return IslandGeometry.holeFor(rects, pathBounds, screenWidth, screenHeight)
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
        screenHeight = height,
    )
}
