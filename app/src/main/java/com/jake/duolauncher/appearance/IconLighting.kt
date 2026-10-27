package com.jake.duolauncher.appearance

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.jake.duolauncher.*

/**
 * The raised, lit look of an iOS app icon, painted over a finished icon of [size] pixels (already cut to the icon silhouette): a light
 * along the top edge and a fainter one along the bottom, a soft glow spilling down from the top, and a gentle darkening toward the lower
 * edge, so the artwork seems to sit on a slightly domed surface lit from above. Everything is clipped to the silhouette, so the
 * transparent corners stay transparent, and it is subtle on purpose: it should never hide or recolour the app's own artwork.
 *
 * The strengths are in [IconLighting] so they can be tuned in one place.
 */
internal fun drawIconLighting(canvas: Canvas, size: Float) {
    val shape = Path().also { Squircle.build(size, size, size * Corner.ICON_FRACTION, AndroidPathSink(it)) }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun white(alpha: Float) = ((alpha * 255f).toInt().coerceIn(0, 255) shl 24) or 0xFFFFFF
    fun black(alpha: Float) = ((alpha * 255f).toInt().coerceIn(0, 255) shl 24)
    canvas.save()
    canvas.clipPath(shape)

    // 1. Shading across the whole icon: lit at the top, clear through the middle, a little darker at the bottom.
    paint.shader = LinearGradient(0f, 0f, 0f, size,
        intArrayOf(white(IconLighting.TOP_GLOW), white(0f), black(0f), black(IconLighting.BOTTOM_SHADE)),
        floatArrayOf(0f, .42f, .62f, 1f), Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, size, size, paint)

    // 2. Light spilling over the icon from above and a little to the left, like a lamp over the top edge.
    paint.shader = RadialGradient(size * .42f, -size * .08f, size * .95f,
        intArrayOf(white(IconLighting.SPILL), white(0f)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, size, size, paint)
    paint.shader = null
    canvas.restore()

    // 3. The edge light: bright along the top, fading down the sides, and a fainter glint at the bottom where light is caught from below.
    val inset = size * .012f
    val rim = Path().also {
        val m = android.graphics.Matrix(); val s = (size - 2 * inset) / size
        m.setScale(s, s, size / 2f, size / 2f); shape.transform(m, it)
    }
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = size * .010f
    paint.shader = LinearGradient(0f, 0f, 0f, size,
        intArrayOf(white(IconLighting.TOP_EDGE), white(IconLighting.TOP_EDGE * .35f), white(0f), white(IconLighting.BOTTOM_EDGE * .6f), white(IconLighting.BOTTOM_EDGE)),
        floatArrayOf(0f, .16f, .5f, .86f, 1f), Shader.TileMode.CLAMP)
    canvas.save(); canvas.clipPath(shape)
    canvas.drawPath(rim, paint)
    canvas.restore()
    paint.shader = null
    // 4. A hairline of shade right at the silhouette's edge, so the icon is defined on any wallpaper.
    paint.strokeWidth = size * .006f
    paint.color = black(IconLighting.HAIRLINE)
    canvas.save(); canvas.clipPath(shape)
    canvas.drawPath(shape, paint)
    canvas.restore()
}

/** How strong each part of the lighting is, as white or black opacity. */
internal object IconLighting {
    // iOS 26's icons are nearly flat: the colour is the icon, with only a faint sheen and a fine edge of light. The older, domed look (a strong
    // glow from the top, a darker bottom, a bright rim) reads as iOS 7 to 15, which is what made these look dated.
    const val TOP_GLOW = .06f
    const val SPILL = .07f
    const val BOTTOM_SHADE = .04f
    const val TOP_EDGE = .40f
    const val BOTTOM_EDGE = .10f
    /** The fine dark line just inside the edge that keeps a pale icon from melting into a pale wallpaper. */
    const val HAIRLINE = .07f
}
