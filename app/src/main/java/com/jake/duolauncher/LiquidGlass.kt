package com.jake.duolauncher

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight

/** Records Home's wallpaper and page content so glass surfaces can refract it. */
@Composable
internal fun rememberHomeBackdrop(): LayerBackdrop = rememberLayerBackdrop()

/** Records a subtree into [backdrop] without applying any glass of its own. */
@Composable
internal fun Modifier.recordBackdrop(backdrop: LayerBackdrop): Modifier = layerBackdrop(backdrop)

/** Liquid-glass surface: vibrancy, blur, and lens refraction over the recorded backdrop. */
@Composable
internal fun Modifier.liquidGlass(
    backdrop: LayerBackdrop,
    shape: Shape,
    tint: Color = Glass.copy(alpha = .3f),
    blurRadius: Float = 2f,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        vibrancy()
        blur(blurRadius.dp.toPx())
        lens(12f.dp.toPx(), 24f.dp.toPx())
    },
    highlight = { Highlight.Plain },
    onDrawSurface = { drawRect(tint) },
)

/** A glass tint derived from the committed launcher background: the photo's muted
 * palette color blended into the theme glass, or the theme glass itself for the
 * drawn dunes. Recomputes only when the background changes.
 */
@Composable
internal fun rememberGlassTint(base: Color): Color {
    val context = LocalContext.current
    val revision = LauncherBackgroundCache.revision.intValue
    val photo = remember(revision) { cachedLauncherBackground(context) }
    return remember(revision, base) {
        if (photo == null) base
        else runCatching {
            val scale = 64f / maxOf(photo.width, photo.height)
            val scaled = Bitmap.createScaledBitmap(photo,
                (photo.width * scale).toInt().coerceAtLeast(1),
                (photo.height * scale).toInt().coerceAtLeast(1), true)
            val palette = Palette.from(scaled).generate()
            val argb: Int = palette.getMutedColor(palette.getDominantColor(base.toArgb()))
            lerp(base, Color(argb), .5f)
        }.getOrElse { base }
    }
}
