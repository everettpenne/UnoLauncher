package com.jake.duolauncher

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.palette.graphics.Palette
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The colours "Color from wallpaper" hands to the interface, all one hue taken from the wallpaper:
 * [accent] for active controls, [glass] for the tint of glass surfaces, and [ink] for text and glyphs that
 * sit on that glass (and on themed icons).
 */
internal class WallpaperAccent(val accent: Color, val glass: Color, val ink: Color)

/** Pure colour maths, so the legibility guarantees are unit-tested rather than eyeballed. */
internal object AccentMath {
    /** Returns h in 0..360, s and l in 0..1. */
    fun toHsl(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f; val g = ((argb shr 8) and 0xFF) / 255f; val b = (argb and 0xFF) / 255f
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        val d = mx - mn
        if (d == 0f) return floatArrayOf(0f, 0f, l)
        val s = d / (1f - abs(2f * l - 1f))
        val h = when (mx) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return floatArrayOf((h + 360f) % 360f, s.coerceIn(0f, 1f), l)
    }

    fun fromHsl(h: Float, s: Float, l: Float): Int {
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r, g, b) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(v: Float) = ((v + m).coerceIn(0f, 1f) * 255f + .5f).toInt()
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    /** Derives the interface colours from one wallpaper colour. A grey or washed-out wallpaper still gets a
     * visible accent (saturation is floored). Lightness is then walked until the contrast targets hold on the
     * glass, because perceived brightness depends on hue: a yellow and a blue of equal lightness are very far
     * apart, so a fixed lightness leaves yellow accents invisible on pale glass.
     */
    fun derive(swatch: Int, dark: Boolean): WallpaperAccent {
        val hsl = toHsl(swatch)
        val h = hsl[0]
        val glass = Color(fromHsl(h, (hsl[1] * .6f).coerceIn(.22f, .5f), if (dark) .22f else .90f))
        // Walk away from the glass's lightness (darker on light glass, lighter on dark glass) until readable.
        fun readable(s: Float, start: Float, target: Float): Color {
            val step = if (dark) .02f else -.02f
            var l = start
            while (l in 0f..1f) {
                val c = Color(fromHsl(h, s, l))
                if (contrast(c, glass) >= target) return c
                l += step
            }
            return if (dark) Color.White else Color.Black
        }
        return WallpaperAccent(
            accent = readable(hsl[1].coerceIn(.5f, .85f), if (dark) .62f else .50f, 3.2f),
            glass = glass,
            ink = readable((hsl[1] * .5f).coerceIn(.2f, .45f), if (dark) .88f else .22f, 7.5f))
    }

    /** WCAG contrast ratio between two colours. */
    fun contrast(a: Color, b: Color): Float {
        val la = AdaptiveInkMath.luminance(a); val lb = AdaptiveInkMath.luminance(b)
        return (max(la, lb) + .05f) / (min(la, lb) + .05f)
    }
}

internal val LocalWallpaperAccent = compositionLocalOf<WallpaperAccent?> { null }

/** Takes a colour from the wallpaper by rendering it exactly as the Home background does (photo or the
 * drawn dunes), small, and asking Palette for its most vibrant swatch.
 */
internal object WallpaperAccents {
    private var memoKey: Triple<Int, Boolean, Int>? = null
    private var memo: WallpaperAccent? = null

    fun compute(context: Context, dark: Boolean): WallpaperAccent {
        val photo = cachedLauncherBackground(context)
        // The same wallpaper and theme give the same answer; the cache revision changes when either does.
        val key = Triple(LauncherBackgroundCache.revision.intValue, dark, System.identityHashCode(photo))
        synchronized(this) { if (key == memoKey) memo?.let { return it } }
        val result = AccentMath.derive(swatchOf(photo, dark), dark)
        synchronized(this) { memoKey = key; memo = result }
        return result
    }

    private fun swatchOf(photo: android.graphics.Bitmap?, dark: Boolean): Int {
        val columns = 48; val rows = 104
        val bitmap = ImageBitmap(columns, rows)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(columns.toFloat(), rows.toFloat())) {
            drawLauncherBackground(photo?.asImageBitmap(), dark)
        }
        val palette = Palette.from(bitmap.asAndroidBitmap()).maximumColorCount(16).generate()
        val swatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.darkVibrantSwatch
            ?: palette.mutedSwatch ?: palette.dominantSwatch
        return swatch?.rgb ?: 0xFFB48A4A.toInt()
    }
}

/** The accent for the current wallpaper and theme, or null while the setting is off. */
@Composable
internal fun rememberWallpaperAccent(enabled: Boolean, dark: Boolean): WallpaperAccent? {
    val context = LocalContext.current
    val revision = LauncherBackgroundCache.revision.intValue
    return remember(enabled, dark, revision) { if (enabled) WallpaperAccents.compute(context, dark) else null }
}
