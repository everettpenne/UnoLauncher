package com.jake.duolauncher.appearance

import com.jake.duolauncher.island.*

import com.jake.duolauncher.*
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

/** A rounded rectangle whose corners are a superellipse (|x|^n + |y|^n = 1, n = 5), the curve iOS's app icons follow: much squarer than a circular arc,
 * with the curvature rising and falling smoothly instead of switching on at the end of a straight edge. With the radius an icon uses (22.37 percent of its
 * side) the corner takes in half of each side, so an icon is the whole superellipse; a widget's 26 dp radius takes in about 58 dp of each edge. It used to be
 * the Figma corner-smoothing construction at 60 percent, which reads rounder than iOS's.
 */
internal object Squircle {
    /** The superellipse's exponent. 2 is a circle, 4 the classic squircle; iOS's icons are close to 5. */
    const val EXPONENT = 5f
    /** How much of a side the corner takes, in radii: 2.235 radii is half of an icon's side at 22.37 percent. */
    const val REACH = 2.235f
    private const val STEPS = 28
    @Suppress("unused") const val SMOOTHING = 0.6f

    @Suppress("UNUSED_PARAMETER")
    fun build(width: Float, height: Float, radius: Float, sink: PathSink, smoothing: Float = SMOOTHING) {
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

    // Small, medium and large are the suite's (the Glass kit's 8, 16 and 24 dp, as Thumper, Spice and Seekur use), which are iOS's own steps.
    /** Rows, highlights, and small tiles. */
    val small: Shape = RoundedCornerShape(8.dp)
    /** Cards and items sitting inside a panel. */
    val medium: Shape = RoundedCornerShape(16.dp)
    /** Widgets, folders, and drop targets. */
    val large: Shape = RoundedCornerShape(24.dp)
    /** Widgets, as iOS 26 draws them: a 26 dp continuous (squircle) corner, the grouped card's radius, which a widget shares on iOS. The lens that
     *  Glass-style widgets use accepts only circular arcs, so those use [widgetRound], the same radius. */
    val widget: Shape = SquircleShape(radius = WIDGET_DP.dp)
    val widgetRound: Shape = RoundedCornerShape(WIDGET_DP.dp)
    const val WIDGET_DP = 26f
    /** Containers: the dock, the status rail and panels. */
    val xlarge: Shape = RoundedCornerShape(32.dp)
    /** Sheets (settings, the widget picker): [SHEET_DP], so the grouped cards 16 dp inside them come out at iOS 26's and the Glass kit's
     * 26 dp. */
    val sheet: Shape = RoundedCornerShape(SHEET_DP.dp)
    const val SHEET_DP = 42f
    /** Capsules and circles, which have no corner to smooth. */
    val pill: Shape = RoundedCornerShape(percent = 50)
    /** The app-icon silhouette, with continuous (squircle) corners. */
    val icon: Shape = SquircleShape(fraction = ICON_FRACTION)

    /** A shape concentric with a container of radius [outer] that is inset by [inset]. */
    fun inside(outer: Dp, inset: Dp): Shape = RoundedCornerShape((outer - inset).coerceAtLeast(4.dp))
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

/** Material components take rounded shapes only; keep their steps on the same scale. */
internal val DuoMaterialShapes = androidx.compose.material3.Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp))

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
