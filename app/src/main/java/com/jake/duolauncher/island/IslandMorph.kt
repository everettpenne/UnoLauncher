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
    /** The corner radius of a body [width] x [height] px: as round as its shorter side allows, up to [IslandGeometry.cornerCapDp], scaled by the
     * theme's [panelScale]. The island and search share it, so search is the island's shape at every size it passes through. */
    fun cornerPx(width: Float, height: Float, density: Float, panelScale: Float = 1f): Float {
        val half = (minOf(width, height) / 2f).coerceAtLeast(0f)
        val cap = IslandGeometry.cornerCapDp * density
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

    /** Up to this height (dp) a capsule body is the full Apple pill; from there to twice the open corner it eases back to a stadium, which is what
     *  a continuous corner of half the height is, so the pill grows into the open panel without a jump. */
    private const val PILL_MAX_DP = 48f

    /**
     * How much of Apple's squircle-ended pill ([com.jake.duolauncher.appearance.SquircleCapsule]) a body [width] x [height] px with corner [radius]
     * gets: none unless it is a capsule (its corner is half its height); all of it at pill heights; none on a circle (a detached capsule, the
     * ring round the camera), which Apple keeps round.
     */
    fun squircleness(width: Float, height: Float, radius: Float, density: Float): Float {
        val short = minOf(width, height); val long = maxOf(width, height)
        if (!(short > 0f) || radius < short / 2f - .5f) return 0f
        val full = 2f * IslandGeometry.cornerCapDp
        val byHeight = if (full <= PILL_MAX_DP) 1f else ((full - short / density) / (full - PILL_MAX_DP)).coerceIn(0f, 1f)
        val byAspect = ((long / short - 1f) / .6f).coerceIn(0f, 1f)
        return byHeight * byAspect
    }

    /** The island's outline at [width] x [height] px with corner [radius]: Apple's squircle pill while it is a pill, the continuous corner once it
     *  has grown into a panel. Everything that draws or clips to the island's body uses this. */
    fun outline(width: Float, height: Float, radius: Float, density: Float, sink: com.jake.duolauncher.appearance.PathSink) {
        val q = squircleness(width, height, radius, density)
        if (q > .01f) com.jake.duolauncher.appearance.SquircleCapsule.build(width, height, q, sink)
        else { val r = radius.coerceAtLeast(0f); com.jake.duolauncher.appearance.ContinuousCorners.build(width, height, r, r, r, r, sink) }
    }

    /** [outline] as a Compose path at ([left], [top]). */
    fun outlinePath(left: Float, top: Float, width: Float, height: Float, radius: Float, density: Float): androidx.compose.ui.graphics.Path =
        androidx.compose.ui.graphics.Path().also {
            outline(width, height, radius, density, com.jake.duolauncher.appearance.ComposePathSink(it))
            if (left != 0f || top != 0f) it.translate(androidx.compose.ui.geometry.Offset(left, top))
        }

    fun lerp(a: PxRect, b: PxRect, t: Float) = PxRect(lerp(a.left, b.left, t), lerp(a.top, b.top, t), lerp(a.right, b.right, t), lerp(a.bottom, b.bottom, t))

    /** The rectangle a frame covers on screen. */
    fun rectOf(frame: IslandFrame) = PxRect(frame.left, frame.top, frame.left + frame.width, frame.top + frame.height)
}

/** The island's outline ([IslandMorph.outline]: Apple's squircle pill, growing into a continuous-cornered panel) at whatever size its body is: [IslandMorph.cornerPx] of that size. One instance serves every frame of a morph, since the
 * radius follows from the size alone. */
internal class IslandBodyShape(private val density: Float, private val panelScale: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(IslandMorph.outlinePath(0f, 0f, size.width, size.height, IslandMorph.cornerPx(size.width, size.height, this.density, panelScale), this.density))
}

/** [IslandMorph.outline] as a [androidx.compose.foundation.shape.CornerBasedShape], for the glass lens (which takes only those, and reads the corner
 *  for its refraction) while it clips to the island's real outline. */
internal class IslandOutlineShape(private val radiusPx: Float, private val densityValue: Float) :
    androidx.compose.foundation.shape.CornerBasedShape(androidx.compose.foundation.shape.CornerSize(radiusPx), androidx.compose.foundation.shape.CornerSize(radiusPx),
        androidx.compose.foundation.shape.CornerSize(radiusPx), androidx.compose.foundation.shape.CornerSize(radiusPx)) {
    override fun createOutline(size: Size, topStart: Float, topEnd: Float, bottomEnd: Float, bottomStart: Float, layoutDirection: LayoutDirection): Outline =
        Outline.Generic(IslandMorph.outlinePath(0f, 0f, size.width, size.height, radiusPx, densityValue))
    override fun copy(topStart: androidx.compose.foundation.shape.CornerSize, topEnd: androidx.compose.foundation.shape.CornerSize,
        bottomEnd: androidx.compose.foundation.shape.CornerSize, bottomStart: androidx.compose.foundation.shape.CornerSize) = IslandOutlineShape(radiusPx, densityValue)
    override fun equals(other: Any?) = other is IslandOutlineShape && other.radiusPx == radiusPx && other.densityValue == densityValue
    override fun hashCode() = radiusPx.hashCode() * 31 + densityValue.hashCode()
}

/** A rounded rectangle at [rect] (in the layer's own px) with corner [radius]: the shape search clips itself to as it grows out of the island. */
internal class MorphRectShape(private val rect: PxRect, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(IslandMorph.outlinePath(rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top, radius.coerceAtLeast(0f), density.density))
}
