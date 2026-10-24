package com.jake.duolauncher.appearance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How a chosen photo is fitted and toned before it becomes the launcher's background: where it sits and how far in it is zoomed ([zoom],
 * [panX], [panY]), and four tone controls. The preview draws these live with the same arithmetic ([cropRect], [colorMatrix], [blurFraction])
 * that [bake] uses to make the saved picture; the preview is in fact [bake] run at a small size, so what is applied is what was shown.
 *
 * [panX] and [panY] run from -1 (the photo's left or top edge in view) to 1 (its right or bottom edge), 0 being centred, as fractions of how
 * far the crop can move at this zoom; [zoom] is 1 (the photo just fills the screen) to [MAX_ZOOM].
 */
data class WallpaperAdjust(
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    /** -1 darkest .. 0 as taken .. 1 brightest. */
    val brightness: Float = 0f,
    /** 0 sharp .. 1 the softest blur. */
    val blur: Float = 0f,
    /** 0 black and white .. 1 as taken .. 2 vivid. */
    val saturation: Float = 1f,
    /** -1 cool .. 0 as taken .. 1 warm. */
    val warmth: Float = 0f,
    /** A look over the whole photo, chosen by swiping in the editor as on iOS's wallpaper screen; the four tone controls apply on top. */
    val style: WallpaperStyle = WallpaperStyle.NATURAL,
) {
    val isNeutral: Boolean get() = this == WallpaperAdjust()
    val framingChanged: Boolean get() = zoom != 1f || panX != 0f || panY != 0f
    val toneChanged: Boolean get() = brightness != 0f || blur != 0f || saturation != 1f || warmth != 0f || style != WallpaperStyle.NATURAL

    /** Clamped into range, so a stored value from a future version or a stray gesture can never produce a broken picture. */
    fun clamped(): WallpaperAdjust = WallpaperAdjust(zoom.coerceIn(1f, MAX_ZOOM), panX.coerceIn(-1f, 1f), panY.coerceIn(-1f, 1f),
        brightness.coerceIn(-1f, 1f), blur.coerceIn(0f, 1f), saturation.coerceIn(0f, 2f), warmth.coerceIn(-1f, 1f), style)

    /** Seven numbers and the style's index, comma separated, for keeping in preferences. */
    fun encode(): String = (listOf(zoom, panX, panY, brightness, blur, saturation, warmth) + style.ordinal.toFloat()).joinToString(",")

    companion object {
        const val MAX_ZOOM = 4f

        fun decode(text: String?): WallpaperAdjust {
            val v = text?.split(',')?.mapNotNull { it.trim().toFloatOrNull() } ?: return WallpaperAdjust()
            // Seven numbers were saved before styles existed (0.104); those photos are Natural.
            if ((v.size != 7 && v.size != 8) || v.any { it.isNaN() || it.isInfinite() }) return WallpaperAdjust()
            val style = WallpaperStyle.entries.getOrNull(v.getOrNull(7)?.toInt() ?: 0) ?: WallpaperStyle.NATURAL
            return WallpaperAdjust(v[0], v[1], v[2], v[3], v[4], v[5], v[6], style).clamped()
        }
    }
}

/**
 * The photo styles of the wallpaper editor, in swipe order, after iOS's: each is a colour matrix (see [WallpaperAdjustMath.styleMatrix]), so
 * it costs nothing extra to preview or bake. Duotone and Color Wash use fixed colours (a deep blue under a warm orange, and Uno's orange),
 * not the accent, so a wallpaper does not change when the accent does.
 */
enum class WallpaperStyle(val label: String) {
    NATURAL("Natural"), VIVID("Vivid"), MONO("Black & White"), DUOTONE("Duotone"), WASH("Color Wash"), DIM("Studio Dim")
}

/** A rectangle in photo pixels (plain numbers, so the framing arithmetic is testable off the device). */
internal data class Crop(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

internal object WallpaperAdjustMath {
    /**
     * The part of a [srcW]×[srcH] photo that shows on a screen of [targetAspect] (width over height): the largest centred rectangle of that shape,
     * shrunk by the zoom and moved by the pan. Always inside the photo.
     */
    fun cropRect(srcW: Int, srcH: Int, targetAspect: Float, adjust: WallpaperAdjust): Crop {
        val a = adjust.clamped()
        val w0: Float; val h0: Float
        if (srcW.toFloat() / srcH > targetAspect) { h0 = srcH.toFloat(); w0 = h0 * targetAspect } else { w0 = srcW.toFloat(); h0 = w0 / targetAspect }
        val w = w0 / a.zoom; val h = h0 / a.zoom
        val slackX = (srcW - w) / 2f; val slackY = (srcH - h) / 2f
        val left = slackX + a.panX * slackX; val top = slackY + a.panY * slackY
        return Crop(left, top, left + w, top + h)
    }

    /**
     * The tone controls as one 4×5 colour matrix (Android's layout): saturation first, then warmth (red up and blue down, or the reverse), then
     * brightness as a scale with a small lift so the darkest setting is dim rather than black.
     */
    fun colorMatrix(adjust: WallpaperAdjust): FloatArray {
        val a = adjust.clamped()
        val m = ColorMatrix(styleMatrix(a.style))
        m.postConcat(ColorMatrix().apply { setSaturation(a.saturation) })
        val w = a.warmth * .12f
        m.postConcat(ColorMatrix(floatArrayOf(1f + w, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f - w, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
        val scale = if (a.brightness < 0f) 1f + a.brightness * .7f else 1f + a.brightness * .35f
        val lift = if (a.brightness > 0f) a.brightness * 18f else 0f
        m.postConcat(ColorMatrix(floatArrayOf(scale, 0f, 0f, 0f, lift, 0f, scale, 0f, 0f, lift, 0f, 0f, scale, 0f, lift, 0f, 0f, 0f, 1f, 0f)))
        return m.array.copyOf()
    }

    /** One style as a 4×5 colour matrix. The identity for [WallpaperStyle.NATURAL]. */
    fun styleMatrix(style: WallpaperStyle): FloatArray {
        // Rec. 709 luma weights, for the styles that start from the photo's brightness alone.
        val lr = .2126f; val lg = .7152f; val lb = .0722f
        fun ramp(dark: Int, light: Int, mix: Float = 1f): FloatArray {
            // Each channel goes from the dark colour at black to the light colour at white, mixed [mix] of the way from the photo itself.
            fun ch(c: Int, shift: Int) = (c shr shift and 0xFF).toFloat()
            val rows = listOf(16, 8, 0).mapIndexed { i, shift ->
                val d = ch(dark, shift); val span = (ch(light, shift) - d) / 255f
                val own = FloatArray(3).also { it[i] = 1f - mix }
                floatArrayOf(own[0] + mix * span * lr, own[1] + mix * span * lg, own[2] + mix * span * lb, 0f, mix * d)
            }
            return rows[0] + rows[1] + rows[2] + floatArrayOf(0f, 0f, 0f, 1f, 0f)
        }
        return when (style) {
            WallpaperStyle.NATURAL -> ColorMatrix().array.copyOf()
            WallpaperStyle.VIVID -> ColorMatrix().apply {
                setSaturation(1.35f)
                // a little more contrast about the middle grey
                postConcat(ColorMatrix(floatArrayOf(1.08f, 0f, 0f, 0f, -10f, 0f, 1.08f, 0f, 0f, -10f, 0f, 0f, 1.08f, 0f, -10f, 0f, 0f, 0f, 1f, 0f)))
            }.array.copyOf()
            WallpaperStyle.MONO -> ColorMatrix().apply {
                setSaturation(0f)
                postConcat(ColorMatrix(floatArrayOf(1.12f, 0f, 0f, 0f, -14f, 0f, 1.12f, 0f, 0f, -14f, 0f, 0f, 1.12f, 0f, -14f, 0f, 0f, 0f, 1f, 0f)))
            }.array.copyOf()
            WallpaperStyle.DUOTONE -> ramp(0xFF1B2A5C.toInt(), 0xFFFFC27A.toInt())
            WallpaperStyle.WASH -> ramp(0xFF6A2A00.toInt(), 0xFFFFD9A8.toInt(), mix = .55f)
            WallpaperStyle.DIM -> ColorMatrix(floatArrayOf(.62f, 0f, 0f, 0f, 0f, 0f, .62f, 0f, 0f, 0f, 0f, 0f, .62f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
                .apply { postConcat(ColorMatrix().apply { setSaturation(.85f) }) }.array.copyOf()
        }
    }

    /** The blur radius as a fraction of the picture's shorter side, so the preview (small) and the saved picture (large) blur alike. */
    fun blurFraction(adjust: WallpaperAdjust): Float = adjust.clamped().blur * .08f

    /**
     * Makes the picture to save: the crop at up to [maxEdge] on its longer side (never enlarged), toned, and blurred. The blur is a
     * scale-down-and-up, done twice, which is smooth, quick and needs nothing newer than the platform's own bitmap scaling.
     */
    fun bake(source: Bitmap, targetAspect: Float, adjust: WallpaperAdjust, maxEdge: Int = 2048): Bitmap {
        val a = adjust.clamped()
        val crop = cropRect(source.width, source.height, targetAspect, a)
        val k = min(1f, maxEdge / max(crop.width, crop.height))
        val outW = max(1, (crop.width * k).roundToInt()); val outH = max(1, (crop.height * k).roundToInt())
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            if (a.toneChanged) colorFilter = ColorMatrixColorFilter(colorMatrix(a))
        }
        Canvas(out).drawBitmap(source, Rect(crop.left.roundToInt(), crop.top.roundToInt(), crop.right.roundToInt(), crop.bottom.roundToInt()),
            Rect(0, 0, outW, outH), paint)
        val radius = blurFraction(a) * min(outW, outH)
        if (radius < 1f) return out
        var blurred = out
        repeat(2) {
            val f = (1f / (1f + radius / 2f)).coerceIn(.02f, 1f)
            val small = Bitmap.createScaledBitmap(blurred, max(1, (outW * f).roundToInt()), max(1, (outH * f).roundToInt()), true)
            val back = Bitmap.createScaledBitmap(small, outW, outH, true)
            if (small !== back) small.recycle()
            if (blurred !== out) blurred.recycle()
            blurred = back
        }
        out.recycle()
        return blurred
    }
}
