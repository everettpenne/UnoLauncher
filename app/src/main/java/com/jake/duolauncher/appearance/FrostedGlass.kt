package com.jake.duolauncher.appearance

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The wallpaper, blurred once into a small picture. A big panel that moves with a page (the App Library, the feed) used to be live liquid glass:
 * its blur and its lens redone over the backdrop every frame of a swipe, which on a Pixel 10a cost a quarter of the frames. Frosted glass reads the
 * same at that size, since the lens only shows at a panel's rim, but it is one picture drawn at the panel's place plus a tint, a sheen and a rim,
 * which costs close to nothing while the page moves.
 */
@Stable
internal class FrostedWallpaper {
    var image by mutableStateOf<ImageBitmap?>(null)
        internal set
}

internal val LocalFrostedWallpaper = compositionLocalOf<FrostedWallpaper?> { null }

/** Blurs [layer] (the wallpaper as Home draws it) whenever [revision] changes: a new picture, light or dark, a new screen size. */
@Composable
internal fun rememberFrostedWallpaper(layer: GraphicsLayer, revision: Any): FrostedWallpaper {
    val frosted = remember { FrostedWallpaper() }
    LaunchedEffect(layer, revision) {
        delay(450L)   // the wallpaper layer is recorded as Home first draws; wait for a complete frame of it
        val full = runCatching { layer.toImageBitmap() }.getOrNull() ?: return@LaunchedEffect
        if (full.width < 8 || full.height < 8) return@LaunchedEffect
        frosted.image = withContext(Dispatchers.Default) { FrostedBlur.blurred(full.asAndroidBitmap()) }.asImageBitmap()
    }
    return frosted
}

/** The blur itself, on a picture a twentieth the size: shrink with smoothing, then two box-blur passes, so what is drawn back at full size is soft and has no blocks. */
internal object FrostedBlur {
    const val SHRINK = 20
    const val RADIUS = 2

    fun blurred(source: Bitmap): Bitmap {
        val w = (source.width / SHRINK).coerceAtLeast(8)
        val h = (source.height / SHRINK).coerceAtLeast(8)
        val small = Bitmap.createScaledBitmap(source.copy(Bitmap.Config.ARGB_8888, false), w, h, true)
        val pixels = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
        val out = boxBlur(boxBlur(pixels, w, h, RADIUS), w, h, RADIUS)
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** A box blur of [radius] pixels, horizontal then vertical, with the picture's edges held (so the rim does not darken). Pure, so it is unit-tested. */
    fun boxBlur(pixels: IntArray, w: Int, h: Int, radius: Int): IntArray {
        fun pass(input: IntArray, horizontal: Boolean): IntArray {
            val output = IntArray(input.size)
            val lines = if (horizontal) h else w
            val length = if (horizontal) w else h
            val window = 2 * radius + 1
            for (line in 0 until lines) {
                for (i in 0 until length) {
                    var a = 0; var r = 0; var g = 0; var b = 0
                    for (k in -radius..radius) {
                        val j = (i + k).coerceIn(0, length - 1)
                        val p = if (horizontal) input[line * w + j] else input[j * w + line]
                        a += p ushr 24; r += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255
                    }
                    val value = ((a / window) shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
                    if (horizontal) output[line * w + i] = value else output[i * w + line] = value
                }
            }
            return output
        }
        return pass(pass(pixels, true), false)
    }
}

/**
 * Frosted glass for a big panel that moves: the blurred wallpaper under it (as the real wallpaper lies, whatever the panel's place), the glass's own
 * tint, a sheen along the top and a rim that is bright at the top edge and fades down the sides, with a soft shadow. Where the blurred picture is not
 * ready yet, or glass is off, it is [fallback] (the live glass it replaces).
 */
@Composable
internal fun Modifier.frostedGlass(shape: Shape, tint: Color, fallback: @Composable Modifier.() -> Modifier): Modifier {
    val frosted = LocalFrostedWallpaper.current
    val image = frosted?.image ?: return fallback()
    val root = LocalWindowInfo.current.containerSize
    var origin by remember { mutableStateOf(Offset.Zero) }
    val dark = DuoAppearanceRuntime.dark
    val saturation = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.45f) }) }
    return this
        .shadow(16.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = if (dark) .26f else .10f),
            spotColor = Color.Black.copy(alpha = if (dark) .34f else .16f))
        .onGloballyPositioned { origin = it.positionInRoot() }
        .drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path().apply { addOutline(outline) }
            clipPath(path) {
                // The picture is the whole screen's wallpaper: drawn at the screen's own place, so it lies where the real wallpaper does under the panel.
                drawImage(image, srcOffset = IntOffset.Zero, srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset((-origin.x).roundToInt(), (-origin.y).roundToInt()), dstSize = root,
                    filterQuality = FilterQuality.High, colorFilter = saturation)
                drawRect(tint)
                // A sheen: light along the top, gone by a third of the way down.
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .22f), Color.White.copy(alpha = .05f), Color.Transparent),
                    startY = 0f, endY = size.height * .38f))
            }
            drawOutline(outline, Brush.verticalGradient(listOf(Color.White.copy(alpha = .75f), Color.White.copy(alpha = .20f), Color.White.copy(alpha = .08f)),
                startY = 0f, endY = size.height), style = Stroke(1.2.dp.toPx()))
        }
}
