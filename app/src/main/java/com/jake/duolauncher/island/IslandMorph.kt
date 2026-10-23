package com.jake.duolauncher.island

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.sqrt

/**
 * The geometry that keeps the island's motion in one place on screen: everything that grows out of it starts from where its body really is, and
 * everything that goes back into it ends there. Pure (no Compose state), so it is unit-tested.
 *
 * The rule the island keeps is that the camera is the anchor. The body swells, squashes and stretches about the camera (never about its own centre,
 * which for the open panel is far below it, so a press or a fast morph used to lift the panel's top edge off the camera and drop it back); what
 * belongs to the top strip (the privacy dots, the capsules) stays on the camera's line while the body opens and closes beneath it.
 */
internal object IslandMorph {
    /** The corner radius of a body [width] x [height] px: as round as its shorter side allows, up to [IslandGeometry.CORNER_CAP_DP], scaled by the
     * theme's [panelScale]. The island and search share it, so search is the island's shape at every size it passes through. */
    fun cornerPx(width: Float, height: Float, density: Float, panelScale: Float = 1f): Float {
        val half = (minOf(width, height) / 2f).coerceAtLeast(0f)
        val cap = IslandGeometry.CORNER_CAP_DP * density
        return if (panelScale < 1f) minOf(half, cap) * panelScale else minOf(half, cap * panelScale)
    }

    /** Where the body scales from, as fractions of its size: the camera when it is at the top, otherwise the top centre (no camera) or the
     * camera's own edge (turned sideways). */
    fun pivot(frame: IslandFrame): Pair<Float, Float> {
        val hole = frame.hole
        if (frame.width <= 0f || frame.height <= 0f) return .5f to 0f
        if (hole == null) return .5f to 0f
        return (hole.centerX / frame.width).coerceIn(0f, 1f) to (hole.centerY / frame.height).coerceIn(0f, 1f)
    }

    /** [x], [y] (window px) where they end up when the body [frame] is scaled by [sx], [sy] about its [pivot]. */
    fun scaled(frame: IslandFrame, x: Float, y: Float, sx: Float, sy: Float): Pair<Float, Float> {
        val (fx, fy) = pivot(frame)
        val ox = frame.left + fx * frame.width
        val oy = frame.top + fy * frame.height
        return ox + (x - ox) * sx to oy + (y - oy) * sy
    }

    /** The line the top strip lives on, in window px: the camera's centre when it is at the top, else the body's middle. */
    fun stripCenterY(frame: IslandFrame): Float {
        val hole = frame.hole
        return if (hole != null && frame.side == IslandSide.TOP) frame.top + hole.centerY else frame.top + frame.height / 2f
    }

    /** How far in from the body's right (or left) edge its outline is at [y] (window px), given its corner radius [corner]. Zero along the straight
     * part of the side; at a rounded corner, as far in as the curve is there. */
    fun cornerInset(frame: IslandFrame, y: Float, corner: Float): Float {
        if (corner <= 0f) return 0f
        val local = y - frame.top
        val fromEdge = when {
            local < corner -> corner - local
            local > frame.height - corner -> local - (frame.height - corner)
            else -> return 0f
        }.coerceAtMost(corner)
        return corner - sqrt((corner * corner - fromEdge * fromEdge).coerceAtLeast(0f))
    }

    /**
     * The centre of privacy dot [index] of [count] (window px): inside the body's right end, on the camera's line, the last [IslandPrivacy.END_PAD_DP]
     * from the edge and further in where a rounded corner curves away. On the collapsed pill the camera's line is its middle, so this is where the
     * dots always were; as the island opens they stay up beside the camera instead of sliding down to the middle of the open panel's side.
     */
    fun dotCenter(frame: IslandFrame, index: Int, count: Int, density: Float, corner: Float): Pair<Float, Float> {
        val y = stripCenterY(frame)
        val n = maxOf(count, index + 1)
        val right = frame.left + frame.width - cornerInset(frame, y, corner)
        val x = right - (IslandPrivacy.END_PAD_DP + (n - index) * IslandPrivacy.STEP_DP) * density + IslandPrivacy.DOT_DP * density / 2f
        return x to y
    }

    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    fun lerp(a: PxRect, b: PxRect, t: Float) = PxRect(lerp(a.left, b.left, t), lerp(a.top, b.top, t), lerp(a.right, b.right, t), lerp(a.bottom, b.bottom, t))

    /** The rectangle a frame covers on screen. */
    fun rectOf(frame: IslandFrame) = PxRect(frame.left, frame.top, frame.left + frame.width, frame.top + frame.height)
}

/** The island's outline at whatever size its body is: [IslandMorph.cornerPx] of that size. One instance serves every frame of a morph, since the
 * radius follows from the size alone. */
internal class IslandBodyShape(private val density: Float, private val panelScale: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(IslandMorph.cornerPx(size.width, size.height, this.density, panelScale))))
}

/** A rounded rectangle at [rect] (in the layer's own px) with corner [radius]: the shape search clips itself to as it grows out of the island. */
internal class MorphRectShape(private val rect: PxRect, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(rect.left, rect.top, rect.right, rect.bottom, CornerRadius(radius.coerceAtLeast(0f))))
}
