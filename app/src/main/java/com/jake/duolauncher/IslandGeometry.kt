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
)

/** Pure layout for the dynamic island: wraps the camera hole, whatever its position or size.
 * [scale] (0..1) sizes the collapsed capsule from compact to large; the midpoint matches
 * the original fixed constants.
 */
internal object IslandGeometry {
    /** Room either side of the hole for the collapsed face's content, in dp. */
    private const val SLOT_DP = 58f
    private const val MIN_HALF_HEIGHT_DP = 17f
    private const val EXPANDED_WIDTH_DP = 336f
    private const val EXPANDED_BODY_DP = 112f
    private const val EDGE_MARGIN_DP = 8f
    private const val NO_CUTOUT_WIDTH_DP = 120f
    private const val NO_CUTOUT_HEIGHT_DP = 34f

    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** The cutout that holds the front camera: a rectangle near the top of the screen, and the one
     * nearest the horizontal center when there are several (side waterfalls and rounded-corner
     * rectangles are ignored).
     */
    fun pickCutout(rects: List<PxRect>, screenWidth: Float, screenHeight: Float): PxRect? =
        rects.filter { it.width > 1f && it.height > 1f && it.centerY < screenHeight * .15f }
            .minByOrNull { abs(it.centerX - screenWidth / 2f) }

    /** [progress] is 0 for the collapsed capsule and 1 for the expanded panel. */
    fun frame(env: IslandEnvironment, density: Float, progress: Float, scale: Float = .5f): IslandFrame {
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
            // Symmetric about the hole, with 2-10 dp of island around it depending on the size
            // setting and never shorter than a 30-42 dp capsule. A hole that sits close to the
            // top edge leaves less room above it, so the island shrinks to the margin that fits
            // rather than crossing the screen edge.
            val surround = mix(2f, 10f, size) * d
            val wanted = max(mix(15f, 21f, size) * d, cutout.height / 2f + surround)
            val half = wanted.coerceAtMost(cutout.centerY).coerceAtLeast(cutout.height / 2f)
            top = cutout.centerY - half
            collapsedHeight = half * 2f
            collapsedWidth = cutout.width + 2f * mix(50f, 66f, size) * d
        } else {
            centerX = env.screenWidth / 2f
            top = env.statusBarHeight + 4f * d
            collapsedHeight = mix(30f, 38f, size) * d
            collapsedWidth = mix(108f, 132f, size) * d
        }
        // The expanded panel hangs below the hole; its body starts under it.
        val holeBottom = cutout?.bottom ?: (top + collapsedHeight)
        val expandedHeight = (holeBottom - top) + EXPANDED_BODY_DP * d
        val expandedWidth = min(EXPANDED_WIDTH_DP * d, max(collapsedWidth, env.screenWidth - 2f * margin))
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

/** Reads the camera cutout and bars from the live window. Cheap enough to call on layout. */
internal fun readIslandEnvironment(view: View): IslandEnvironment {
    val root = view.rootView
    val insets = view.rootWindowInsets
    val width = root.width.toFloat()
    val height = root.height.toFloat()
    val rects = insets?.displayCutout?.boundingRects.orEmpty()
        .map { PxRect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
    return IslandEnvironment(
        cutout = IslandGeometry.pickCutout(rects, width, height),
        screenWidth = width,
        statusBarHeight = (insets?.getInsets(WindowInsets.Type.statusBars())?.top ?: 0).toFloat(),
    )
}
