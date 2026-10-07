package com.jake.duolauncher

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.pow
import kotlin.math.sqrt

/** The text and icon color for a glass surface. iOS flips glass content between light and dark
 * from the brightness behind it; a fixed white is unreadable over a pale photo or sky.
 */
@Immutable
internal data class GlassInk(val primary: Color) {
    /** The same ink, softened for secondary text and inactive icons. */
    fun soft(alpha: Float = .8f) = primary.copy(alpha = alpha)

    companion object {
        val Light = GlassInk(Color.White)
        val Dark = GlassInk(Color(0xFF14232B))
    }
}

/** What built-in glass content should draw with. [GlassCard]s and similar provide it. */
internal val LocalGlassInk = compositionLocalOf { GlassInk.Light }

/** A coarse grid of the wallpaper's relative luminance (0 black .. 1 white), enough to ask what
 * lies behind a rectangle without sampling pixels while the screen scrolls.
 */
internal class WallpaperLuma(val columns: Int, val rows: Int, private val cells: FloatArray) {
    init { require(columns > 0 && rows > 0 && cells.size == columns * rows) }

    /** Mean luminance over a rectangle given as fractions of the screen (0..1 on both axes). */
    fun mean(left: Float, top: Float, right: Float, bottom: Float): Float {
        val x0 = (left.coerceIn(0f, 1f) * columns).toInt().coerceIn(0, columns - 1)
        val x1 = (right.coerceIn(0f, 1f) * columns).toInt().coerceIn(x0, columns - 1)
        val y0 = (top.coerceIn(0f, 1f) * rows).toInt().coerceIn(0, rows - 1)
        val y1 = (bottom.coerceIn(0f, 1f) * rows).toInt().coerceIn(y0, rows - 1)
        var sum = 0f
        var count = 0
        for (y in y0..y1) for (x in x0..x1) { sum += cells[y * columns + x]; count++ }
        return sum / count
    }
}

internal object AdaptiveInkMath {
    private val darkInkLuminance = luminance(GlassInk.Dark.primary)

    private fun linear(channel: Float): Float =
        if (channel <= .04045f) channel / 12.92f else ((channel + .055f) / 1.055f).pow(2.4f)

    /** WCAG relative luminance of an sRGB color. */
    fun luminance(r: Float, g: Float, b: Float): Float = .2126f * linear(r) + .7152f * linear(g) + .0722f * linear(b)
    fun luminance(color: Color): Float = luminance(color.red, color.green, color.blue)

    /** Luminance of a [tint] laid over a backdrop of luminance [backdrop] at [alpha]. */
    fun effective(backdrop: Float, tint: Color, alpha: Float): Float =
        backdrop * (1f - alpha) + luminance(tint) * alpha

    /** The backdrop luminance at which white and the dark ink have equal contrast (about .2):
     * brighter than this reads better in dark ink, darker in white.
     */
    val crossover: Float get() = sqrt(1.05f * (darkInkLuminance + .05f)) - .05f

    /** Whether to use the dark ink on a backdrop of [luminance]. The switch has a small dead band
     * around the crossover so a surface does not flicker while content slides past it.
     */
    fun useDarkInk(luminance: Float, currentlyDark: Boolean, band: Float = .04f): Boolean =
        if (currentlyDark) luminance > crossover - band else luminance > crossover + band
}

/** Renders the wallpaper exactly as [DuneWallpaper] does, at a tiny size, and keeps its luminance. */
internal fun buildWallpaperLuma(photo: Bitmap?, dark: Boolean, columns: Int = 16, rows: Int = 36): WallpaperLuma {
    val bitmap = ImageBitmap(columns, rows)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(columns.toFloat(), rows.toFloat())) {
        drawLauncherBackground(photo?.asImageBitmap(), dark)
    }
    val pixels = IntArray(columns * rows)
    bitmap.readPixels(pixels, 0, 0, columns, rows)
    val cells = FloatArray(pixels.size) { index ->
        val argb = pixels[index]
        AdaptiveInkMath.luminance(
            ((argb shr 16) and 0xFF) / 255f, ((argb shr 8) and 0xFF) / 255f, (argb and 0xFF) / 255f)
    }
    return WallpaperLuma(columns, rows, cells)
}

internal val LocalWallpaperLuma = compositionLocalOf<WallpaperLuma?> { null }

@Composable
internal fun rememberWallpaperLuma(dark: Boolean): WallpaperLuma {
    val context = LocalContext.current
    val revision = LauncherBackgroundCache.revision.intValue
    return remember(revision, dark) { buildWallpaperLuma(cachedLauncherBackground(context), dark) }
}

/** A glass surface's ink, plus the [track] modifier that measures where the surface sits. */
internal class AdaptiveInk(val color: Color, val track: Modifier) {
    fun soft(alpha: Float = .8f) = color.copy(alpha = alpha)
    val glassInk get() = GlassInk(color)
}

/** Picks light or dark ink for a glass surface from the wallpaper behind it, tinted by [tint] at
 * [tintAlpha]. Attach [AdaptiveInk.track] to the surface; the color follows the surface as it
 * moves, with a dead band and a short fade so it never flickers.
 */
@Composable
internal fun rememberAdaptiveInk(tint: Color, tintAlpha: Float): AdaptiveInk {
    val luma = LocalWallpaperLuma.current
    var dark by remember { mutableStateOf(false) }
    val track = remember(luma, tint, tintAlpha) {
        if (luma == null) Modifier else Modifier.onGloballyPositioned { coordinates ->
            val root = coordinates.findRootCoordinates().size
            if (root.width <= 0 || root.height <= 0) return@onGloballyPositioned
            val b = coordinates.boundsInRoot()
            val behind = luma.mean(b.left / root.width, b.top / root.height, b.right / root.width, b.bottom / root.height)
            val next = AdaptiveInkMath.useDarkInk(AdaptiveInkMath.effective(behind, tint, tintAlpha), dark)
            if (next != dark) dark = next
        }
    }
    val color by animateColorAsState(if (dark) GlassInk.Dark.primary else GlassInk.Light.primary,
        tween(220), label = "glass ink")
    return AdaptiveInk(color, track)
}
