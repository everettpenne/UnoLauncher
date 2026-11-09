// Added for Uno Launcher: see third_party/backdrop/README.md. Apache 2.0, as the rest of this library.
package com.kyant.backdrop.effects

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import com.kyant.backdrop.RoundedRectRefractionShaderString
import com.kyant.backdrop.RoundedRectRefractionWithDispersionShaderString

/**
 * The lens, painted straight from one image: a single draw with the refraction shader as its paint, instead of a backdrop recorded into an offscreen
 * layer and filtered there. One instance can serve every surface in a window (it is used from the UI thread only), so the shader program is compiled
 * once and not per surface.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class ImageLens {
    private val plain by lazy { RuntimeShader(RoundedRectRefractionShaderString) }
    private val dispersed by lazy { RuntimeShader(RoundedRectRefractionWithDispersionShaderString) }
    private val paint = Paint()
    private val matrix = Matrix()
    private var image: Bitmap? = null
    private var imageShader: BitmapShader? = null

    /**
     * Fills [width] by [height] at the canvas's origin. A point of the image lands at (x * [imageScale] + [translateX], y * [imageScale] + [translateY]).
     * [cornerRadii] is clockwise from the top left (see [lensCornerRadii]); the sizes are the same pixels [lens] takes. The caller clips to the shape.
     */
    fun draw(
        canvas: Canvas, width: Float, height: Float, image: Bitmap, imageScale: Float, translateX: Float, translateY: Float,
        cornerRadii: FloatArray, refractionHeight: Float, refractionAmount: Float, depthEffect: Boolean, chromaticAberration: Boolean,
        colorFilter: ColorFilter?,
    ) {
        val source = imageShader?.takeIf { this.image === image }
            ?: BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also {
                it.filterMode = BitmapShader.FILTER_MODE_LINEAR
                imageShader = it
                this.image = image
            }
        matrix.setScale(imageScale, imageScale)
        matrix.postTranslate(translateX, translateY)
        source.setLocalMatrix(matrix)

        val shader = if (chromaticAberration) dispersed else plain
        shader.setInputShader("content", source)
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("offset", 0f, 0f)
        shader.setFloatUniform("cornerRadii", cornerRadii)
        shader.setFloatUniform("refractionHeight", refractionHeight)
        shader.setFloatUniform("refractionAmount", -refractionAmount)
        shader.setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
        if (chromaticAberration) shader.setFloatUniform("chromaticAberration", 1f)

        paint.shader = shader
        paint.colorFilter = colorFilter
        canvas.drawRect(0f, 0f, width, height, paint)
    }
}
