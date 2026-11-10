package com.jake.duolauncher.appearance

import android.graphics.Bitmap
import android.graphics.ColorFilter
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.effects.ImageLens
import com.kyant.backdrop.effects.lensCornerRadii
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The wallpaper, blurred once, for clear glass that sits on it (the dock and the widgets). Live, each of those re-blurred and bent the wallpaper in an
 * offscreen pass of its own on every frame it moved, so the cost rose with every widget on the page. Now the blur is done once, and each surface is one
 * ordinary draw: the shared picture, through the shared lens program ([ImageLens]), at the surface's place. That is the way iOS's glass is thought to save work.
 *
 * Only for surfaces that sample [source]; anything sampling another backdrop (the pages, a sheet) stays live, since it can show what moves.
 */
@Stable
internal class SharedWallpaper(val source: Backdrop) {
    var image by mutableStateOf<Bitmap?>(null)
        internal set
    /** The size of the wallpaper the picture was made from, in pixels. */
    internal var artWidth = 0
    internal var artHeight = 0
    /** Where the wallpaper has drifted to as the pages move (see WallpaperDepth); null when it does not. Read while drawing. */
    internal var drift: (widthPx: Float) -> WallpaperDepth.Drift? = { null }
    private val lens by lazy { ImageLens() }

    /** Paints the picture through the lens over a [size] surface whose top left is at ([surfaceX], [surfaceY]) in the window. */
    fun DrawScope.drawLens(
        image: Bitmap, surfaceX: Float, surfaceY: Float, radii: FloatArray, refractionHeight: Float, refractionAmount: Float, chromatic: Boolean, colorFilter: ColorFilter?,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || artWidth <= 0) return
        val drift = drift(artWidth.toFloat())
        val place = SharedWallpaperMap.place(image.width, artWidth, artHeight, drift?.scale ?: 1f, drift?.translationX ?: 0f, surfaceX, surfaceY)
        drawIntoCanvas { canvas ->
            lens.draw(canvas.nativeCanvas, size.width, size.height, image, place.scale, place.translateX, place.translateY, radii, refractionHeight, refractionAmount,
                depthEffect = true, chromaticAberration = chromatic, colorFilter = colorFilter)
        }
    }
}

/** The one Home made, for [liquidGlass] to find, and for the island's overlay window, which has no backdrop of its own (see [SharedWallpaperBackdrop]). */
internal object SharedWallpaperRuntime {
    var current by mutableStateOf<SharedWallpaper?>(null)
    /** Home's glass settings, so glass drawn in another window bends the same way. */
    var settings by mutableStateOf(GlassSettings.Default)
}

/**
 * [shared]'s picture as a backdrop, for glass in another window of this app over Home: the island's overlay, which sits above the status bar in a window of its
 * own and so cannot sample Home's. It places the wallpaper by where the surface is on the screen, Home's window filling the screen. It shows the wallpaper only,
 * not the icons and widgets on it; the open island's glass is a band at its bottom edge, over the top of the page.
 */
internal class SharedWallpaperBackdrop(private val shared: SharedWallpaper) : Backdrop {
    override val isCoordinatesDependent: Boolean = true
    private var made: Pair<Bitmap, androidx.compose.ui.graphics.ImageBitmap>? = null

    override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?, layerBlock: (GraphicsLayerScope.() -> Unit)?) {
        val image = shared.image ?: return
        val at = coordinates?.positionOnScreen() ?: return
        if (shared.artWidth <= 0) return
        val bitmap = made?.takeIf { it.first === image }?.second ?: image.asImageBitmap().also { made = image to it }
        val drift = shared.drift(shared.artWidth.toFloat())
        val place = SharedWallpaperMap.place(image.width, shared.artWidth, shared.artHeight, drift?.scale ?: 1f, drift?.translationX ?: 0f, at.x, at.y)
        withTransform({ translate(place.translateX, place.translateY); scale(place.scale, place.scale, androidx.compose.ui.geometry.Offset.Zero) }) {
            drawImage(bitmap, filterQuality = androidx.compose.ui.graphics.FilterQuality.Low)
        }
    }
}

/** The sums behind the shared picture. Pure, so they are unit-tested. */
internal object SharedWallpaperMap {
    /** How much smaller than the screen the picture is. Clear glass blurs only a little, so this is as far as it can shrink and still look the same. */
    const val SHRINK = 3

    class Place(val scale: Float, val translateX: Float, val translateY: Float)

    /**
     * Where the picture goes, in a surface's own pixels: the picture is [imageWidth] wide, made from wallpaper [artWidth] by [artHeight]; the wallpaper is
     * scaled [driftScale] about its centre and moved [driftX] (as Home draws it); and the surface's top left is at ([surfaceX], [surfaceY]).
     */
    fun place(imageWidth: Int, artWidth: Int, artHeight: Int, driftScale: Float, driftX: Float, surfaceX: Float, surfaceY: Float): Place {
        val cx = artWidth / 2f; val cy = artHeight / 2f
        return Place(artWidth.toFloat() / imageWidth * driftScale, cx * (1f - driftScale) + driftX - surfaceX, cy * (1f - driftScale) - surfaceY)
    }

    /**
     * The box-blur radius, on a picture [shrink] times smaller, that matches a live blur of [blurDp]. Android turns a blur radius r into a Gaussian of sigma
     * 0.57735 r + 0.5, and two box passes of radius b have a variance of 2/3 b (b + 1). Never less than 1, so the picture's own pixels cannot be seen.
     */
    fun boxRadius(blurDp: Float, density: Float, shrink: Int): Int {
        val sigma = (blurDp * density * .57735f + .5f) / shrink
        return ((sqrt(1f + 6f * sigma * sigma) - 1f) / 2f).roundToInt().coerceAtLeast(1)
    }
}

/** Remembers a surface's corner radii, worked out again only when its size changes. */
internal class GlassCorners {
    private var size = Size.Unspecified
    private var radii: FloatArray? = null
    fun of(shape: Shape, size: Size, density: Density, direction: LayoutDirection): FloatArray? {
        if (size != this.size) { this.size = size; radii = lensCornerRadii(shape, size, density, direction) }
        return radii
    }
}

/** Makes the picture from [art] (the wallpaper as drawn before it drifts) whenever [revision] changes; until it is ready, the glass stays live. */
@Composable
internal fun rememberSharedWallpaper(source: Backdrop, art: GraphicsLayer, revision: Any, drift: (Float) -> WallpaperDepth.Drift?): SharedWallpaper {
    val shared = remember(source) { SharedWallpaper(source) }
    val density = LocalDensity.current.density
    SideEffect { shared.drift = drift }
    DisposableEffect(shared) {
        SharedWallpaperRuntime.current = shared
        onDispose { if (SharedWallpaperRuntime.current === shared) SharedWallpaperRuntime.current = null }
    }
    LaunchedEffect(art, revision) {
        shared.image = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        delay(450L)   // the layer is recorded as Home first draws; wait for a complete frame of it
        val full = runCatching { art.toImageBitmap() }.getOrNull() ?: return@LaunchedEffect
        if (full.width < 8 || full.height < 8) return@LaunchedEffect
        val radius = SharedWallpaperMap.boxRadius(GlassKind.CLEAR.blurDp, density, SharedWallpaperMap.SHRINK)
        val blurred = withContext(Dispatchers.Default) { FrostedBlur.blurred(full.asAndroidBitmap(), SharedWallpaperMap.SHRINK, radius) }
        shared.artWidth = full.width; shared.artHeight = full.height
        shared.image = blurred
    }
    return shared
}
