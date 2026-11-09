// Modified for Uno Launcher: see third_party/backdrop/README.md.
package com.kyant.backdrop.effects

import android.graphics.RenderEffect
import android.os.Build
import androidx.annotation.FloatRange
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.ui.util.fastCoerceAtMost
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.RoundedRectRefractionShaderString
import com.kyant.backdrop.RoundedRectRefractionWithDispersionShaderString

fun BackdropEffectScope.lens(
    @FloatRange(from = 0.0) refractionHeight: Float,
    @FloatRange(from = 0.0) refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Boolean = false
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    if (refractionHeight <= 0f || refractionAmount <= 0f) return

    if (padding > 0f) {
        padding = (padding - refractionHeight).fastCoerceAtLeast(0f)
    }

    val cornerRadii = lensCornerRadii(shape, size, this, layoutDirection)
    val effect =
        if (cornerRadii != null) {
            val shader =
                if (!chromaticAberration) {
                    obtainRuntimeShader(
                        "Refraction",
                        RoundedRectRefractionShaderString
                    )
                } else {
                    obtainRuntimeShader(
                        "RefractionWithDispersion",
                        RoundedRectRefractionWithDispersionShaderString
                    )
                }
            shader.apply {
                val s = renderScale
                setFloatUniform("size", size.width * s, size.height * s)
                setFloatUniform("offset", -padding * s, -padding * s)
                setFloatUniform("cornerRadii", floatArrayOf(cornerRadii[0] * s, cornerRadii[1] * s, cornerRadii[2] * s, cornerRadii[3] * s))
                setFloatUniform("refractionHeight", refractionHeight * s)
                setFloatUniform("refractionAmount", -refractionAmount * s)
                setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
                if (chromaticAberration) {
                    setFloatUniform("chromaticAberration", 1f)
                }
            }
            RenderEffect.createRuntimeShaderEffect(shader, "content")
        } else {
            throwUnsupportedSDFException()
        }
    effect(effect)
}

/** The corner radii of [shape] at [size], clockwise from the top left, or null when the lens cannot be drawn in it. */
fun lensCornerRadii(shape: Shape, size: Size, density: Density, layoutDirection: LayoutDirection): FloatArray? {
    val cornerShape = shape as? CornerBasedShape ?: return null
    val maxRadius = size.minDimension / 2f
    val isLtr = layoutDirection == LayoutDirection.Ltr
    val topLeft =
        if (isLtr) cornerShape.topStart.toPx(size, density)
        else cornerShape.topEnd.toPx(size, density)
    val topRight =
        if (isLtr) cornerShape.topEnd.toPx(size, density)
        else cornerShape.topStart.toPx(size, density)
    val bottomRight =
        if (isLtr) cornerShape.bottomEnd.toPx(size, density)
        else cornerShape.bottomStart.toPx(size, density)
    val bottomLeft =
        if (isLtr) cornerShape.bottomStart.toPx(size, density)
        else cornerShape.bottomEnd.toPx(size, density)
    return floatArrayOf(
        topLeft.fastCoerceAtMost(maxRadius),
        topRight.fastCoerceAtMost(maxRadius),
        bottomRight.fastCoerceAtMost(maxRadius),
        bottomLeft.fastCoerceAtMost(maxRadius)
    )
}

private fun throwUnsupportedSDFException(): Nothing {
    throw UnsupportedOperationException("Only CornerBasedShape is supported in lens effects.")
}
