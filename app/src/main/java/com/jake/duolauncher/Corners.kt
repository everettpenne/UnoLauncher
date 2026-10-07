package com.jake.duolauncher

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
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

/** A rounded rectangle with "continuous" corners: the curve eases into the straight edge over
 * a longer run than a circular arc does, which is what gives iOS shapes their softer look.
 * The construction follows the published Figma corner-smoothing geometry (a circular arc
 * flanked by two cubic easing curves), at the 60% smoothing Apple's shapes match.
 */
internal object Squircle {
    const val SMOOTHING = 0.6f

    fun build(width: Float, height: Float, radius: Float, sink: PathSink, smoothing: Float = SMOOTHING) {
        val half = min(width, height) / 2f
        val r = radius.coerceIn(0f, half)
        if (r < 0.01f) {
            sink.moveTo(0f, 0f); sink.lineTo(width, 0f); sink.lineTo(width, height); sink.lineTo(0f, height)
            sink.close()
            return
        }
        // The easing run is (1 + smoothing) * r along each edge. When two corners would overlap
        // there isn't room for it, so the smoothing gives way first and the radius is kept.
        val xi = min(smoothing, half / r - 1f).coerceAtLeast(0f)
        val p = min((1f + xi) * r, half)
        val arc = 90f * (1f - xi)
        val arcLength = sin(rad(arc / 2f)) * r * sqrt(2f)
        val p3p4 = r * tan(rad((90f - arc) / 4f))
        val beta = 45f * xi
        val c = p3p4 * cos(rad(beta))
        val d = c * tan(rad(beta))
        val b = (p - arcLength - c - d) / 3f
        val a = 2f * b
        // A circular arc of `arc` degrees as one cubic: handle length 4/3 * tan(arc / 4) * r.
        val handle = 4f / 3f * tan(rad(arc) / 4f) * r
        val t0x = cos(rad(beta)); val t0y = sin(rad(beta))
        val t1x = cos(rad(beta + arc)); val t1y = sin(rad(beta + arc))

        // One corner in a local frame: the corner vertex at the origin, entering along the top
        // edge from (-p, 0) and leaving down the right edge at (0, p). Rotated into the others.
        val e1x = -p + a + b + c; val e1y = d
        val e2x = e1x + arcLength; val e2y = e1y + arcLength
        fun corner(map: (Float, Float) -> Pair<Float, Float>) {
            fun cubic(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
                val (ax, ay) = map(x1, y1); val (bx, by) = map(x2, y2); val (cx, cy) = map(x3, y3)
                sink.cubicTo(ax, ay, bx, by, cx, cy)
            }
            cubic(-p + a, 0f, -p + a + b, 0f, e1x, e1y)
            cubic(e1x + handle * t0x, e1y + handle * t0y, e2x - handle * t1x, e2y - handle * t1y, e2x, e2y)
            cubic(e2x + d, e2y + c, e2x + d, e2y + b + c, 0f, p)
        }

        sink.moveTo(p, 0f)
        sink.lineTo(width - p, 0f)
        corner { x, y -> (width + x) to y }                 // top right
        sink.lineTo(width, height - p)
        corner { x, y -> (width - y) to (height + x) }      // bottom right
        sink.lineTo(p, height)
        corner { x, y -> (-x) to (height - y) }             // bottom left
        sink.lineTo(0f, p)
        corner { x, y -> y to (-x) }                        // top left
        sink.close()
    }

    private fun rad(degrees: Float) = degrees * (Math.PI.toFloat() / 180f)
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
 * Containers use circular-arc corners because the glass lens (and Material) accept only
 * [RoundedCornerShape]; feeding the lens a continuous-curve outline throws at draw time. App
 * icons never go through the lens, so [icon] gets the true continuous curve.
 */
internal object Corner {
    /** App icons are 22.37% of their side, scale-independent, so they work at every size. */
    const val ICON_FRACTION = 0.2237f

    /** Rows, highlights, and small tiles. */
    val small: Shape = RoundedCornerShape(12.dp)
    /** Cards and items sitting inside a panel. */
    val medium: Shape = RoundedCornerShape(18.dp)
    /** Widgets, folders, and drop targets. */
    val large: Shape = RoundedCornerShape(24.dp)
    /** Containers: the dock, the status rail, sheets, and panels. */
    val xlarge: Shape = RoundedCornerShape(32.dp)
    /** Capsules and circles, which have no corner to smooth. */
    val pill: Shape = RoundedCornerShape(percent = 50)
    /** The app-icon silhouette, with continuous (squircle) corners. */
    val icon: Shape = SquircleShape(fraction = ICON_FRACTION)

    /** A shape concentric with a container of radius [outer] that is inset by [inset]. */
    fun inside(outer: Dp, inset: Dp): Shape = RoundedCornerShape((outer - inset).coerceAtLeast(4.dp))
}

/** iOS-style switch: a green track when on, a quiet grey one when off, and a white thumb in both. */
internal val IosSwitchColors: androidx.compose.material3.SwitchColors
    @androidx.compose.runtime.Composable get() = androidx.compose.material3.SwitchDefaults.colors(
        checkedThumbColor = androidx.compose.ui.graphics.Color.White,
        checkedTrackColor = androidx.compose.ui.graphics.Color(0xFF34C759),
        checkedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
        uncheckedThumbColor = androidx.compose.ui.graphics.Color.White,
        uncheckedTrackColor = androidx.compose.ui.graphics.Color(0xFF787880).copy(alpha = .4f),
        uncheckedBorderColor = androidx.compose.ui.graphics.Color.Transparent)

/** Material components take rounded shapes only; keep their steps on the same scale. */
internal val DuoMaterialShapes = androidx.compose.material3.Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp))
