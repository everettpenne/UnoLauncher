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
import com.kyant.backdrop.LobedRefractionShaderString
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

/**
 * Uno: the lens for a lobed outline, a circle whose radius swells and dips [lobes] times between [inner] (0..1, the dips' radius as a share of
 * the tips') and the shorter half-side: Material's cookies and Sunny. [phase] is the angle, in radians, a tip points at (0 is right, -pi/2 is
 * up); [sharp] takes the lobes from a cosine (0) toward a pointed triangle wave (1). [centerX] and [centerY] place the shape's centre in the
 * surface (0..1) and [outer] is its tips' radius as a share of the shorter side, for a shape that does not sit dead centre in its box (an odd
 * number of lobes). The surface is still clipped by the shape given to
 * `drawBackdrop`; this only bends what is behind it along that outline. Does nothing below Android 13, as [lens].
 */
fun BackdropEffectScope.lobedLens(
    @FloatRange(from = 0.0) refractionHeight: Float,
    @FloatRange(from = 0.0) refractionAmount: Float,
    lobes: Int,
    @FloatRange(from = 0.0, to = 1.0) inner: Float,
    phase: Float = 0f,
    @FloatRange(from = 0.0, to = 1.0) sharp: Float = 0f,
    centerX: Float = .5f,
    centerY: Float = .5f,
    outer: Float = .5f,
    depthEffect: Boolean = false,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    if (refractionHeight <= 0f || refractionAmount <= 0f || lobes < 2) return

    if (padding > 0f) {
        padding = (padding - refractionHeight).fastCoerceAtLeast(0f)
    }

    val shader = obtainRuntimeShader("LobedRefraction", LobedRefractionShaderString).apply {
        val s = renderScale
        setFloatUniform("size", size.width * s, size.height * s)
        setFloatUniform("offset", -padding * s, -padding * s)
        setFloatUniform("center", centerX, centerY)
        setFloatUniform("outer", outer)
        setFloatUniform("lobes", lobes.toFloat())
        setFloatUniform("inner", inner)
        setFloatUniform("phase", phase)
        setFloatUniform("sharp", sharp)
        setFloatUniform("refractionHeight", refractionHeight * s)
        setFloatUniform("refractionAmount", -refractionAmount * s)
        setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
    }
    effect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
}
