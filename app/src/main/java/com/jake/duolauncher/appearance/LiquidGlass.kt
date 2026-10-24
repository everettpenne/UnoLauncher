package com.jake.duolauncher.appearance

import com.jake.duolauncher.island.*

import com.jake.duolauncher.*
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
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.BackdropEffectScope
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
    /** Light direction in degrees (45 is the upper left). Moved by [TiltHighlight] when tilt-following is on; it is
     * snapshot state, so only the drawing of glass surfaces re-runs when it changes, not their composition.
     */
    private val angleState = androidx.compose.runtime.mutableFloatStateOf(TiltMath.BASE_ANGLE)
    var angle: Float
        get() = angleState.floatValue
        set(value) { angleState.floatValue = value }

    /** 0..1: how much stronger and thicker the rim is made while tilt-following is on, so the movement is easy to
     * see. 0 leaves the stock rim. Set from the tilt strength setting.
     */
    private val boostState = androidx.compose.runtime.mutableFloatStateOf(0f)
    var boost: Float
        get() = boostState.floatValue
        set(value) { boostState.floatValue = value }

    /** Motion readings received since tilt-following started; the settings page uses it to say whether the sensor
     * is reaching the launcher at all. Kept apart from [angle] so counting never redraws the glass.
     */
    val samples = androidx.compose.runtime.mutableIntStateOf(0)

    val Light: Highlight
        get() {
            val b = boost
            // More tilt strength sharpens the falloff rather than brightening the whole rim: a rim that is bright on
            // every edge hides which way the light is, so the lit edge gets brighter while the others fade out.
            return Highlight.Default.copy(
                width = (0.5f + 0.6f * b).dp,
                style = com.kyant.backdrop.highlight.HighlightStyle.Default(
                    intensity = 0.5f + 0.4f * b, angle = angle, falloff = 1f + 8f * b))
        }
}

/**
 * Home's glass finish (see [AppearanceState.clearGlass]). Clear glass is iOS's Liquid Glass: the surface takes almost no tint, so the wallpaper
 * shows through bent but uncoloured, and its edge catches the light, brighter and a little wider than the frosted rim, with a soft sheen
 * along the top. Only light surfaces (a tint of [LIGHT_TINT_MAX] alpha or less: the dock, the rail, widgets, page dots, the search pill)
 * turn clear; a sheet or panel with text on a heavier tint keeps it. Snapshot state, so a change redraws glass without recomposing it.
 */
internal object GlassFinish {
    private val clearState = androidx.compose.runtime.mutableStateOf(true)
    var clear: Boolean
        get() = clearState.value
        set(value) { clearState.value = value }

    /** The heaviest tint that still counts as a light surface. */
    const val LIGHT_TINT_MAX = .2f
    /** What is left of a light surface's tint when the glass is clear: enough to hold its ink, not enough to colour it. */
    const val CLEAR_TINT_SHARE = .25f

    /** The tint [tint] is drawn with under the current finish. Pure, so it is unit-tested. */
    fun tint(tint: Color, clear: Boolean = this.clear): Color =
        if (clear && tint.alpha <= LIGHT_TINT_MAX) tint.copy(alpha = tint.alpha * CLEAR_TINT_SHARE) else tint

    /** Whether a surface tinted [tint] gets the clear edge. */
    fun clearEdge(tint: Color, clear: Boolean = this.clear) = clear && tint.alpha <= LIGHT_TINT_MAX

    /** The edge of clear glass: a fine line of light, bright where it faces the light and fading quickly away from it, as Liquid Glass's
     * is. It was 1.1 dp at full strength with a slow fall-off, which read as a drawn outline round the dock and the search pill. */
    val Edge: Highlight
        get() {
            val b = GlassRim.boost
            return Highlight.Default.copy(width = (.75f + .4f * b).dp,
                style = com.kyant.backdrop.highlight.HighlightStyle.Default(intensity = .8f, angle = GlassRim.angle, falloff = 2.5f + 8f * b))
        }

    /** The sheen along the top of clear glass: white fading out over the top 40 % of the surface. */
    val Sheen = androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.White.copy(alpha = .10f), .35f to Color.Transparent)
}

/**
 * The depth every glass surface shares: the library's own default shadow (a soft 24 dp shadow, nudged down), which is what its catalog
 * uses under its panels, so the surface sits on the page as a thick piece of glass and not a flat tinted pane. The library draws an
 * inner shadow only on a held control (see the switch and slider thumbs), never on a resting panel, so this adds none. One definition,
 * so panels, docks and widgets agree; change it here to retune them all.
 */
internal object GlassDepth {
    fun shadow() = com.kyant.backdrop.shadow.Shadow.Default
}

/** How much a glass surface lifts or dims, and flattens, the picture behind it so its ink stays readable. */
internal data class GlassTone(val brightness: Float, val contrast: Float) {
    companion object { val Neutral = GlassTone(0f, 1f) }
}

/**
 * Adapts glass to the wallpaper, as the Backdrop library's adaptive-luminance example does. Our glass text is dark in the light theme and
 * light in the dark theme, so glass only needs help when the picture behind it is the wrong way round: a dark wallpaper under dark ink
 * (light theme), or a bright one under light ink (dark theme). Then the picture under the glass is lifted or dimmed and its contrast
 * eased, a frosted look that holds the text up; on a wallpaper that already suits the theme nothing changes. Pure, so it is unit-tested.
 */
internal object BackdropTone {
    private const val SIDE = 5

    /** [luminance] is the wallpaper's average relative luminance, 0 (black) to 1 (white). */
    fun tone(luminance: Float, darkTheme: Boolean): GlassTone {
        val wrongWay = (if (darkTheme) luminance - .45f else .55f - luminance).coerceAtLeast(0f)
        val strength = (wrongWay / .45f).coerceIn(0f, 1f)
        return GlassTone(brightness = (if (darkTheme) -1f else 1f) * .20f * strength + 0f, contrast = 1f - .15f * strength)
    }

    /** The average luminance of [bitmap], from a 5 x 5 shrink of it. */
    fun luminance(bitmap: Bitmap): Float {
        val small = Bitmap.createScaledBitmap(bitmap, SIDE, SIDE, true)
        var sum = 0.0
        for (y in 0 until SIDE) for (x in 0 until SIDE) {
            val c = small.getPixel(x, y)
            sum += .2126 * android.graphics.Color.red(c) / 255.0 + .7152 * android.graphics.Color.green(c) / 255.0 +
                .0722 * android.graphics.Color.blue(c) / 255.0
        }
        if (small !== bitmap) small.recycle()
        return (sum / (SIDE * SIDE)).toFloat()
    }

    /** The built-in scenes are sky and sand by day and deep blue by night; this is how bright each reads on average. */
    fun builtinLuminance(dark: Boolean): Float = if (dark) .22f else .62f

    private var cachedIdentity: String? = null
    private var cachedLuminance = .5f
    fun photoLuminance(bitmap: Bitmap, identity: String?): Float {
        if (identity != null && identity == cachedIdentity) return cachedLuminance
        return luminance(bitmap).also { cachedIdentity = identity; cachedLuminance = it }
    }
}

/** The tone glass should take now, from the wallpaper (the photo, or the built-in scene) and the theme. Recomputed only when either changes. */
@Composable
internal fun rememberGlassTone(): GlassTone {
    val context = LocalContext.current
    val revision = LauncherBackgroundCache.revision.intValue
    val dark = DuoAppearanceRuntime.dark
    return remember(revision, dark) {
        val photo = cachedLauncherBackground(context)
        val luminance = if (photo != null) BackdropTone.photoLuminance(photo, LauncherBackgroundCache.identity)
            else BackdropTone.builtinLuminance(dark)
        BackdropTone.tone(luminance, dark)
    }
}

/** Vibrancy (the library's 1.5 saturation) together with the wallpaper-adaptive [tone]. */
internal fun BackdropEffectScope.glassColor(tone: GlassTone) =
    colorControls(brightness = tone.brightness, contrast = tone.contrast, saturation = 1.5f)

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
    /** The shared shadow (see [GlassDepth]); off for tiny controls that draw their own. */
    depth: Boolean = true,
): Modifier {
    val tone = rememberGlassTone()
    return drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        glassColor(tone)
        blur(blurRadius.dp.toPx())
        lens(
            refractionHeight = lerp(8f, 44f, settings.height).coerceAtMost(72f).dp.toPx(),
            refractionAmount = lerp(16f, 132f, settings.amount).dp.toPx(),
            depthEffect = true,
            chromaticAberration = settings.chromatic > 0.05f,
        )
    },
    highlight = { if (GlassFinish.clearEdge(tint)) GlassFinish.Edge else GlassRim.Light },
    shadow = { if (depth) GlassDepth.shadow() else null },
    onDrawSurface = {
        drawRect(GlassFinish.tint(tint))
        if (GlassFinish.clearEdge(tint)) drawRect(GlassFinish.Sheen)
    },
    )
}

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
    val sheetTone = rememberGlassTone()
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
                    glassColor(sheetTone)
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

/**
 * Keeps a sheet's content from closing the sheet. A modal bottom sheet takes whatever downward scroll its content has left over and
 * drags itself down with it, so scrolling a settings page up past its top (or flinging it) closed the sheet, and it was easy to do by
 * accident. This connection, placed around the content, consumes that leftover downward scroll and fling, so only the sheet's handle,
 * the Back gesture, a tap outside or a Done/Close button dismiss it. Scrolling the content itself is untouched.
 *
 * The one exception is a decisive downward *flick* (at least [flingDismissVelocity], px/s): that still dismisses the sheet, matching
 * iOS's "a downward flick with sufficient velocity dismisses even from a small drag". Slow leftover scroll stays absorbed.
 */
internal class SheetDismissGuard(private val flingDismissVelocity: Float) : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
    override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset,
        source: androidx.compose.ui.input.nestedscroll.NestedScrollSource) =
        if (available.y > 0f) androidx.compose.ui.geometry.Offset(0f, available.y) else androidx.compose.ui.geometry.Offset.Zero

    override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity) =
        if (available.y > 0f && available.y < flingDismissVelocity)
            androidx.compose.ui.unit.Velocity(0f, available.y) else androidx.compose.ui.unit.Velocity.Zero
}
