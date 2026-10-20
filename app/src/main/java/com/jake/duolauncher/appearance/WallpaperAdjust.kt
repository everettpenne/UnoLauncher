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
) {
    val isNeutral: Boolean get() = this == WallpaperAdjust()
    val framingChanged: Boolean get() = zoom != 1f || panX != 0f || panY != 0f
    val toneChanged: Boolean get() = brightness != 0f || blur != 0f || saturation != 1f || warmth != 0f

    /** Clamped into range, so a stored value from a future version or a stray gesture can never produce a broken picture. */
    fun clamped(): WallpaperAdjust = WallpaperAdjust(zoom.coerceIn(1f, MAX_ZOOM), panX.coerceIn(-1f, 1f), panY.coerceIn(-1f, 1f),
        brightness.coerceIn(-1f, 1f), blur.coerceIn(0f, 1f), saturation.coerceIn(0f, 2f), warmth.coerceIn(-1f, 1f))

    /** Seven numbers, comma separated, for keeping in preferences. */
    fun encode(): String = listOf(zoom, panX, panY, brightness, blur, saturation, warmth).joinToString(",")

    companion object {
        const val MAX_ZOOM = 4f

        fun decode(text: String?): WallpaperAdjust {
            val v = text?.split(',')?.mapNotNull { it.trim().toFloatOrNull() } ?: return WallpaperAdjust()
            if (v.size != 7 || v.any { it.isNaN() || it.isInfinite() }) return WallpaperAdjust()
            return WallpaperAdjust(v[0], v[1], v[2], v[3], v[4], v[5], v[6]).clamped()
        }
    }
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
        val m = ColorMatrix().apply { setSaturation(a.saturation) }
        val w = a.warmth * .12f
        m.postConcat(ColorMatrix(floatArrayOf(1f + w, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f - w, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
        val scale = if (a.brightness < 0f) 1f + a.brightness * .7f else 1f + a.brightness * .35f
        val lift = if (a.brightness > 0f) a.brightness * 18f else 0f
        m.postConcat(ColorMatrix(floatArrayOf(scale, 0f, 0f, 0f, lift, 0f, scale, 0f, 0f, lift, 0f, 0f, scale, 0f, lift, 0f, 0f, 0f, 1f, 0f)))
        return m.array.copyOf()
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
