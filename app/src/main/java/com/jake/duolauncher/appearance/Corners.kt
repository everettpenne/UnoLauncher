package com.jake.duolauncher.appearance

import com.jake.duolauncher.island.*

import com.jake.duolauncher.*
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Receives the segments of an outline. Lets the squircle geometry stay plain math, shared
 * by Compose paths (shapes) and framework paths (the app-icon bitmap mask).
 */
internal interface PathSink {
    fun moveTo(x: Float, y: Float)
    fun lineTo(x: Float, y: Float)
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float)
    fun close()
}

internal class ComposePathSink(private val path: Path) : PathSink {
    override fun moveTo(x: Float, y: Float) = path.moveTo(x, y)
    override fun lineTo(x: Float, y: Float) = path.lineTo(x, y)
    override fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        path.cubicTo(x1, y1, x2, y2, x3, y3)
    override fun close() = path.close()
}

internal class AndroidPathSink(private val path: android.graphics.Path) : PathSink {
    override fun moveTo(x: Float, y: Float) = path.moveTo(x, y)
    override fun lineTo(x: Float, y: Float) = path.lineTo(x, y)
    override fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        path.cubicTo(x1, y1, x2, y2, x3, y3)
    override fun close() = path.close()
}

/** The app-icon silhouette: a rounded rectangle whose corners are a superellipse (|x|^n + |y|^n = 1, n = 5), the curve iOS's app icons follow. With the
 * radius an icon uses (22.37 percent of its side) the corner takes in half of each side, so an icon is the whole superellipse. Only icons use it: on
 * anything larger than an icon the corner would take in about 2.2 radii of each edge and read soft, so every other surface uses [ContinuousCorners].
 */
internal object Squircle {
    /** The superellipse's exponent. 2 is a circle, 4 the classic squircle; iOS's icons are close to 5. */
    const val EXPONENT = 5f
    /** How much of a side the corner takes, in radii: 2.235 radii is half of an icon's side at 22.37 percent. */
    const val REACH = 2.235f
    private const val STEPS = 28

    fun build(width: Float, height: Float, radius: Float, sink: PathSink) {
        val half = min(width, height) / 2f
        val r = radius.coerceIn(0f, half)
        if (r < 0.01f) {
            sink.moveTo(0f, 0f); sink.lineTo(width, 0f); sink.lineTo(width, height); sink.lineTo(0f, height)
            sink.close()
            return
        }
        val c = min(r * REACH, half)
        val power = 2.0 / EXPONENT
        // u and v are the superellipse quadrant's two coordinates as the angle t goes from 0 to 90 degrees: u = sin(t)^p rises from 0 to 1 while v = cos(t)^p falls
        // from 1 to 0, with p = 2 / n (p = 1 would be a circle).
        val us = FloatArray(STEPS + 1); val vs = FloatArray(STEPS + 1)
        for (i in 0..STEPS) {
            val t = (Math.PI / 2.0) * i / STEPS
            us[i] = Math.pow(Math.sin(t), power).toFloat(); vs[i] = Math.pow(Math.cos(t), power).toFloat()
        }
        sink.moveTo(c, 0f)
        sink.lineTo(width - c, 0f)
        for (i in 1..STEPS) sink.lineTo(width - c + c * us[i], c - c * vs[i])                  // top right
        sink.lineTo(width, height - c)
        for (i in 1..STEPS) sink.lineTo(width - c + c * vs[i], height - c + c * us[i])         // bottom right
        sink.lineTo(c, height)
        for (i in 1..STEPS) sink.lineTo(c - c * us[i], height - c + c * vs[i])                 // bottom left
        sink.lineTo(0f, c)
        for (i in 1..STEPS) sink.lineTo(c - c * vs[i], c - c * us[i])                          // top left
        sink.close()
    }
}

/** iOS's continuous corner for everything that is not an app icon (cards, sheets, panels, menus, widgets, the dock): a circular arc in the middle, eased
 * into each straight edge by a cubic on either side, so the curvature rises gradually instead of switching on where the edge ends. This is Figma's corner
 * smoothing construction; at 60 percent ([SMOOTHING]) it is the published match for iOS, and the corner takes in 1.6 radii of each edge (a circular arc takes
 * in 1). When a side is too short for that, the smoothing gives way first and then the radius, so a capsule ends in true semicircles.
 */
internal object ContinuousCorners {
    const val SMOOTHING = 0.6f

    /** Builds the outline clockwise from the top edge. Radii are in the order top left, top right, bottom right, bottom left (absolute, not start/end). */
    fun build(width: Float, height: Float, topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float, sink: PathSink,
              smoothing: Float = SMOOTHING) {
        // A shape with no area (the island's inner fill while it is at its smallest) draws nothing, as Compose's own round rects do.
        if (!(width > 0f && height > 0f)) return
        val budget = min(width, height) / 2f
        val tl = OneCorner(topLeft, smoothing, budget); val tr = OneCorner(topRight, smoothing, budget)
        val br = OneCorner(bottomRight, smoothing, budget); val bl = OneCorner(bottomLeft, smoothing, budget)
        sink.moveTo(tl.reach, 0f)
        tr.draw(sink, width, 0f, -1f, 0f, 0f, 1f)
        br.draw(sink, width, height, 0f, -1f, -1f, 0f)
        bl.draw(sink, 0f, height, 1f, 0f, 0f, -1f)
        tl.draw(sink, 0f, 0f, 0f, 1f, 1f, 0f)
        sink.close()
    }

    /** One corner, in its own frame: p runs from the corner back along the edge that arrives at it, q along the edge that leaves it. */
    private class OneCorner(radius: Float, smoothing: Float, budget: Float) {
        val r = radius.coerceIn(0f, budget)
        /** How far along each edge the corner starts. */
        val reach: Float
        private val a: Float; private val b: Float; private val c: Float; private val d: Float; private val arc: Float
        /** The arc's angle in radians: the whole 90 degrees with no smoothing, 36 at 60 percent. */
        private val sweep: Float

        init {
            val s = if (r <= 0f) 0f else min(smoothing, budget / r - 1f).coerceAtLeast(0f)
            reach = (1f + s) * r
            val arcDegrees = 90f * (1f - s)
            sweep = rad(arcDegrees)
            arc = sin(rad(arcDegrees / 2f)) * r * sqrt(2f)
            val alpha = (90f - arcDegrees) / 2f
            val beta = 45f * s
            c = r * tan(rad(alpha / 2f)) * cos(rad(beta))
            d = c * tan(rad(beta))
            b = (reach - arc - c - d) / 3f
            a = 2f * b
        }

        /** Lines to this corner from the previous one, then draws it. (vx, vy) is the corner; (ux, uy) points back along the arriving edge and
         *  (wx, wy) along the leaving one. */
        fun draw(sink: PathSink, vx: Float, vy: Float, ux: Float, uy: Float, wx: Float, wy: Float) {
            fun x(p: Float, q: Float) = vx + p * ux + q * wx
            fun y(p: Float, q: Float) = vy + p * uy + q * wy
            if (r < 0.01f) { sink.lineTo(vx, vy); return }
            sink.lineTo(x(reach, 0f), y(reach, 0f))
            // The easing in: the curvature climbs from zero on the edge to the arc's.
            sink.cubicTo(x(reach - a, 0f), y(reach - a, 0f), x(reach - a - b, 0f), y(reach - a - b, 0f), x(arc + d, d), y(arc + d, d))
            // The arc, centred on (r, r), from (arc + d, d) to (d, arc + d), as one cubic (it spans at most 90 degrees, where a cubic is within 0.03% of a circle).
            val k = 4f / 3f * tan(sweep / 4f) * r
            val tp0 = (d - r) / r; val tq0 = -(arc + d - r) / r                     // the tangent where the arc starts
            val tp1 = (arc + d - r) / r; val tq1 = -(d - r) / r                     // and where it ends
            sink.cubicTo(x(arc + d + k * tp0, d + k * tq0), y(arc + d + k * tp0, d + k * tq0),
                x(d - k * tp1, arc + d - k * tq1), y(d - k * tp1, arc + d - k * tq1), x(d, arc + d), y(d, arc + d))
            // The easing out, the first cubic mirrored.
            sink.cubicTo(x(0f, reach - a - b), y(0f, reach - a - b), x(0f, reach - a), y(0f, reach - a), x(0f, reach), y(0f, reach))
        }
    }

    private fun rad(degrees: Float) = degrees * (Math.PI.toFloat() / 180f)
}

/** A rounded rectangle with [ContinuousCorners]. It is a [CornerBasedShape], like Compose's own [RoundedCornerShape], so the glass lens (which reads the
 * radii for its refraction and clips to this outline), Material's [androidx.compose.material3.Shapes] and anything that copies a shape's corners all take
 * it; only the curve differs. */
@Immutable
internal class ContinuousShape(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize) :
    CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    constructor(all: CornerSize) : this(all, all, all, all)
    constructor(radius: Dp) : this(CornerSize(radius))

    override fun createOutline(size: Size, topStart: Float, topEnd: Float, bottomEnd: Float, bottomStart: Float, layoutDirection: LayoutDirection): Outline {
        if (topStart + topEnd + bottomEnd + bottomStart == 0f) return Outline.Rectangle(size.toRect())
        val ltr = layoutDirection == LayoutDirection.Ltr
        return Outline.Generic(Path().also {
            ContinuousCorners.build(size.width, size.height,
                topLeft = if (ltr) topStart else topEnd, topRight = if (ltr) topEnd else topStart,
                bottomRight = if (ltr) bottomEnd else bottomStart, bottomLeft = if (ltr) bottomStart else bottomEnd, sink = ComposePathSink(it))
        })
    }

    override fun copy(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize) =
        ContinuousShape(topStart, topEnd, bottomEnd, bottomStart)

    override fun equals(other: Any?) = other is ContinuousShape && topStart == other.topStart && topEnd == other.topEnd &&
        bottomEnd == other.bottomEnd && bottomStart == other.bottomStart
    override fun hashCode() = ((topStart.hashCode() * 31 + topEnd.hashCode()) * 31 + bottomEnd.hashCode()) * 31 + bottomStart.hashCode()
    override fun toString() = "ContinuousShape(topStart = $topStart, topEnd = $topEnd, bottomEnd = $bottomEnd, bottomStart = $bottomStart)"
}

/** A [ContinuousCorners] rectangle of [width] by [height] at ([left], [top]) with one [radius]: for outlines drawn by hand (the island's body
 *  as it morphs, rims drawn as strokes), so they match the [ContinuousShape]s around them. */
internal fun continuousPath(left: Float, top: Float, width: Float, height: Float, radius: Float): Path = Path().also {
    ContinuousCorners.build(width, height, radius, radius, radius, radius, ComposePathSink(it))
    if (left != 0f || top != 0f) it.translate(androidx.compose.ui.geometry.Offset(left, top))
}

@Immutable
internal class SquircleShape(private val radius: Dp = 0.dp, private val fraction: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = if (fraction > 0f) fraction * min(size.width, size.height) else with(density) { radius.toPx() }
        return Outline.Generic(Path().also { Squircle.build(size.width, size.height, r, ComposePathSink(it)) })
    }
    override fun equals(other: Any?) = other is SquircleShape && other.radius == radius && other.fraction == fraction
    override fun hashCode() = 31 * radius.hashCode() + fraction.hashCode()
}

/** The launcher's one corner scale, in the iOS manner: a few steps and concentric nesting (an
 * inner shape's radius is its container's radius minus the inset between them). Use these
 * instead of ad-hoc radii so every surface reads as one family.
 *
 * Every step has iOS's continuous corner ([ContinuousShape]), glass or not: the lens accepts any
 * [CornerBasedShape]. App icons have their own superellipse, [icon].
 */
internal object Corner {
    /** App icons are 22.37% of their side, scale-independent, so they work at every size. */
    const val ICON_FRACTION = 0.2237f

    // Small, medium and large are the suite's (the Glass kit's 8, 16 and 24 dp, as Thumper, Spice and Seekur use), which are iOS's own steps.
    const val SMALL_DP = 8f
    const val MEDIUM_DP = 16f
    const val LARGE_DP = 24f
    const val XLARGE_DP = 32f
    /** Rows, highlights, and small tiles. */
    val small: CornerBasedShape = ContinuousShape(SMALL_DP.dp)
    /** Cards and items sitting inside a panel. */
    val medium: CornerBasedShape = ContinuousShape(MEDIUM_DP.dp)
    /** Menus and drop targets. */
    val large: CornerBasedShape = ContinuousShape(LARGE_DP.dp)
    /** Widgets, as iOS 26 draws them: 26 dp, the grouped card's radius, which a widget shares on iOS. Card and Glass widgets both use it. */
    val widget: CornerBasedShape = ContinuousShape(WIDGET_DP.dp)
    const val WIDGET_DP = 26f
    /** Floating containers: panels, dialogs, folders, the side dock and the status rail. */
    val xlarge: CornerBasedShape = ContinuousShape(XLARGE_DP.dp)
    /** Sheets (settings, the widget picker): [SHEET_DP], so the grouped cards 16 dp inside them come out at iOS 26's and the Glass kit's
     * 26 dp. */
    val sheet: CornerBasedShape = ContinuousShape(SHEET_DP.dp)
    const val SHEET_DP = 42f
    /** Capsules and circles. The continuous construction gives way to true semicircles when the radius is half the height, as iOS's capsules have. */
    val pill: CornerBasedShape = ContinuousShape(CornerSize(50))
    /** The app-icon silhouette, with continuous (squircle) corners. */
    val icon: Shape = SquircleShape(fraction = ICON_FRACTION)

    /** A shape concentric with a container of radius [outer] that is inset by [inset]. */
    fun inside(outer: Dp, inset: Dp): CornerBasedShape = ContinuousShape(insideRadius(outer, inset))
    fun insideRadius(outer: Dp, inset: Dp): Dp = (outer - inset).coerceAtLeast(4.dp)

    /** A sheet that floats [inset] in from the sides and bottom of the screen: its top corners are [SHEET_DP], and its bottom corners are
     *  concentric with the display's own corners (the display's radius less [inset]), as iOS 26's sheets are. Where the display reports no
     *  rounded corners (an emulator, a square screen) all four are [SHEET_DP]. */
    @Composable
    fun floatingSheet(inset: Dp): CornerBasedShape {
        val bottom = displayCornerRadius()?.let { insideRadius(it, inset) } ?: SHEET_DP.dp
        return remember(bottom) {
            ContinuousShape(CornerSize(SHEET_DP.dp), CornerSize(SHEET_DP.dp), CornerSize(bottom), CornerSize(bottom))
        }
    }
}

/** The radius of the display's bottom corners, as the system reports it (Android 12+), or null where it reports none. */
@Composable
internal fun displayCornerRadius(): Dp? {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(context, density) {
        val display = runCatching { context.display }.getOrNull() ?: return@remember null
        val px = listOf(android.view.RoundedCorner.POSITION_BOTTOM_LEFT, android.view.RoundedCorner.POSITION_BOTTOM_RIGHT)
            .mapNotNull { display.getRoundedCorner(it)?.radius }.maxOrNull() ?: return@remember null
        if (px <= 0) null else with(density) { px.toDp() }
    }
}

/** iOS-style switch: a track in the launcher's one accent (the wallpaper's, or orange) when on, a quiet grey one when off, and a white thumb in both. */
internal val IosSwitchColors: androidx.compose.material3.SwitchColors
    @androidx.compose.runtime.Composable get() = androidx.compose.material3.SwitchDefaults.colors(
        checkedThumbColor = androidx.compose.ui.graphics.Color.White,
        checkedTrackColor = LocalWallpaperAccent.current?.accent ?: com.jake.duolauncher.panel.IosOrange,
        checkedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
        uncheckedThumbColor = androidx.compose.ui.graphics.Color.White,
        uncheckedTrackColor = androidx.compose.ui.graphics.Color(0xFF787880).copy(alpha = .4f),
        uncheckedBorderColor = androidx.compose.ui.graphics.Color.Transparent)

/** Material's steps, taken from Uno's own scale (it had 12 and 18 dp of its own), with the same continuous corner. */
internal val DuoMaterialShapes = androidx.compose.material3.Shapes(
    extraSmall = Corner.small, small = Corner.small,
    medium = Corner.medium, large = Corner.large,
    extraLarge = Corner.xlarge)

/**
 * The spacing scale: every gap and padding in Uno's own screens is one of these (dp). Sheets, settings, panels, search and folders were moved
 * onto it from a scatter of 10, 14, 18 and 20 dp; the island, the keyboard and the insides of widgets keep their tuned spacing.
 */
internal object Space {
    const val XS = 4
    const val S = 8
    const val M = 12
    const val L = 16
    const val XL = 24
}
