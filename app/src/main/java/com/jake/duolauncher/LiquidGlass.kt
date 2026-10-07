package com.jake.duolauncher

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight

/** Home's glass sources. A [LayerBackdrop] holds exactly one recorded layer and one set of
 * layer coordinates, so attaching the same instance to both the wallpaper and the pager
 * made the pager (drawn second) overwrite the wallpaper recording. The dock and status
 * rail sit beside the pager, outside its bounds, so they sampled empty pixels and the lens
 * had nothing to bend: only the tint and rim showed. Each layer now records into its own
 * backdrop, and glass surfaces sample [combined], which draws both in order.
 */
internal class HomeBackdrop(val wallpaper: LayerBackdrop, val pages: LayerBackdrop, val combined: Backdrop)

@Composable
internal fun rememberHomeBackdrop(): HomeBackdrop {
    val wallpaper = rememberLayerBackdrop()
    val pages = rememberLayerBackdrop()
    val combined = rememberCombinedBackdrop(wallpaper, pages)
    return remember(wallpaper, pages, combined) { HomeBackdrop(wallpaper, pages, combined) }
}

/** Records a subtree into [backdrop] without applying any glass of its own. */
@Composable
internal fun Modifier.recordBackdrop(backdrop: LayerBackdrop): Modifier = layerBackdrop(backdrop)

/** Upper end of the refraction slider. 1.0 was the original ceiling; values above it
 * extrapolate the lens mapping for an exaggerated, full-pill lens.
 */
internal const val MAX_REFRACTION = 2f

/** Glass settings for surfaces drawn inside the Home pager (widgets, Discover, the feed).
 * Those surfaces must sample the wallpaper-only backdrop: the pager itself records into
 * the pages backdrop, so sampling that from inside it would draw the layer into itself.
 * Null when liquid glass is off, and outside the pager.
 */
internal class PageGlass(val backdrop: Backdrop, val tint: Color, val refraction: Float)

internal val LocalPageGlass = compositionLocalOf<PageGlass?> { null }

/** Liquid-glass surface: vibrancy, blur, and lens refraction over the recorded backdrop.
 * [refraction] (0..1) scales the lens: height, displacement, and at the top end
 * chromatic aberration and depth weighting, matching the demo's deep-refraction look.
 */
@Composable
internal fun Modifier.liquidGlass(
    backdrop: Backdrop,
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
        // Strong by default: the old 6..30dp band and 10..60dp displacement read as flat
        // glass on a Duo-sized dock. The band now covers most of a 64-84dp-wide surface
        // at the top of the slider, so the whole pill behaves like a curved lens.
        lens(
            refractionHeight = lerp(14f, 40f, refraction).coerceAtMost(64f).dp.toPx(),
            refractionAmount = lerp(32f, 120f, refraction).dp.toPx(),
            depthEffect = true,
            chromaticAberration = refraction >= 0.5f,
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

/** A Material modal sheet that floats as an inset liquid-glass card when glass is on, and
 * is the stock opaque sheet otherwise. The sheet's dialog window is full-screen like the
 * launcher's, so window coordinates line up and the glass samples Home through the dialog.
 * The tint follows the theme surface so sheet text keeps its contrast in light and dark.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlassModalSheet(
    glass: PageGlass?,
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (glass == null) {
        ModalBottomSheet(onDismissRequest, modifier, sheetState, containerColor = containerColor,
            properties = properties, content = content)
        return
    }
    val shape = RoundedCornerShape(32.dp)
    ModalBottomSheet(onDismissRequest, modifier, sheetState, shape = shape,
        containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp, scrimColor = Color.Black.copy(alpha = .22f), dragHandle = null,
        properties = properties) {
        Column(Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp).fillMaxWidth()
            .liquidGlass(glass.backdrop, shape, MaterialTheme.colorScheme.surface.copy(alpha = .58f),
                blurRadius = 8f, refraction = glass.refraction.coerceAtMost(1f))) {
            BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
            content()
        }
    }
}
