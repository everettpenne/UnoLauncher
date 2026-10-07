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
import androidx.compose.ui.util.lerp
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

/** Liquid-glass surface: vibrancy, blur, and lens refraction over the recorded backdrop.
 * [refraction] (0..1) scales the lens: height, displacement, and at the top end
 * chromatic aberration and depth weighting, matching the demo's deep-refraction look.
 */
@Composable
internal fun Modifier.liquidGlass(
    backdrop: LayerBackdrop,
    shape: Shape,
    tint: Color = Glass.copy(alpha = .3f),
    blurRadius: Float = 2f,
    refraction: Float = .55f,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        vibrancy()
        blur(blurRadius.dp.toPx())
        lens(
            refractionHeight = lerp(6f, 30f, refraction).dp.toPx(),
            refractionAmount = lerp(10f, 60f, refraction).dp.toPx(),
            depthEffect = true,
            chromaticAberration = refraction > 0.7f,
        )
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
