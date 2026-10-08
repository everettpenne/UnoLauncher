package com.jake.duolauncher

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.util.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
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

/** The three lens knobs, each 0..1 and independently persisted:
 * [height] is the rim band the lens bends, [amount] is the displacement it applies, and
 * [chromatic] engages the color fringe above a small threshold.
 */
internal data class GlassSettings(
    val height: Float = .55f,
    val amount: Float = .55f,
    val chromatic: Float = 0f,
) {
    companion object { val Default = GlassSettings() }
}

/** Glass settings for surfaces drawn inside the Home pager (widgets, Discover, the feed).
 * Those surfaces must sample the wallpaper-only backdrop: the pager itself records into
 * the pages backdrop, so sampling that from inside it would draw the layer into itself.
 * Null when liquid glass is off, and outside the pager.
 */
internal class PageGlass(val backdrop: Backdrop, val tint: Color, val settings: GlassSettings)

internal val LocalPageGlass = compositionLocalOf<PageGlass?> { null }

/** The rim light every glass surface shares. A uniform rim ([Highlight.Plain]) reads as an outline; this one is a
 * directional specular highlight, brightest where the edge faces the light (upper left) and fading to a faint
 * rim opposite, which is what makes the glass read as having thickness. One definition, so the dock, widgets,
 * panels, sheets, lens and island always agree on where the light is.
 */
internal object GlassRim {
    val Light: Highlight get() = Highlight.Default
}

/** Liquid-glass surface: vibrancy, blur, and lens refraction over the recorded backdrop,
 * tuned by the three [GlassSettings] knobs so every surface reads as one family.
 */
@Composable
internal fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: Shape,
    tint: Color = Glass.copy(alpha = .3f),
    blurRadius: Float = 2f,
    settings: GlassSettings = GlassSettings.Default,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        vibrancy()
        blur(blurRadius.dp.toPx())
        lens(
            refractionHeight = lerp(8f, 44f, settings.height).coerceAtMost(72f).dp.toPx(),
            refractionAmount = lerp(16f, 132f, settings.amount).dp.toPx(),
            depthEffect = true,
            chromaticAberration = settings.chromatic > 0.05f,
        )
    },
    highlight = { GlassRim.Light },
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
 * Controls inside the sheet sample [LocalPageGlass], which is set to the sheet's own
 * exported backdrop so sliders and switches refract the panel they sit on.
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
    val shape = Corner.xlarge
    val sheetBackdrop = rememberLayerBackdrop()
    val accent = LocalWallpaperAccent.current
    val sheetSurface = MaterialTheme.colorScheme.surface.let { base ->
        if (accent != null) androidx.compose.ui.graphics.lerp(base, accent.glass, .5f) else base
    }.copy(alpha = .58f)
    val sheetGlass = PageGlass(sheetBackdrop, sheetSurface, glass.settings)
    ModalBottomSheet(onDismissRequest, modifier, sheetState, shape = shape,
        containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp, scrimColor = Color.Black.copy(alpha = .22f), dragHandle = null,
        properties = properties) {
        Column(Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp).fillMaxWidth()
            .drawBackdrop(
                backdrop = glass.backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    lens(lerp(8f, 44f, glass.settings.height).dp.toPx(),
                        lerp(16f, 132f, glass.settings.amount).dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = glass.settings.chromatic > 0.05f)
                },
                highlight = { GlassRim.Light },
                exportedBackdrop = sheetBackdrop,
                onDrawSurface = { drawRect(sheetSurface) })
            ) {
            CompositionLocalProvider(LocalPageGlass provides sheetGlass) {
                BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
                content()
            }
        }
    }
}
