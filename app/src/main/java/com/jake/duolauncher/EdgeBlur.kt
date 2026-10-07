package com.jake.duolauncher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur

/** One band of the progressive blur: [radiusDp] of blur, over the [coverage] fraction of the strip
 * nearest the screen edge, fading to nothing at its inner boundary.
 */
internal data class EdgeBlurBand(val radiusDp: Float, val coverage: Float)

internal object EdgeBlur {
    /** Soft near the content, strong at the very edge. Stacking a few bands of increasing radius
     * and shrinking coverage approximates a variable-radius blur using only ordinary blurs.
     */
    val bands = listOf(EdgeBlurBand(2f, 1f), EdgeBlurBand(6f, .7f), EdgeBlurBand(14f, .4f))

    /** Extra height beyond the system inset that the top strip covers. Home content starts well below this. */
    val TOP_EXTRA: Dp = 24.dp
}

/** iOS 26's "scroll edge effect": content near the top or bottom of the screen softens into a blur
 * instead of being cut off or fighting the floating glass above it (the island, the page dots).
 * It samples the recorded Home backdrop, so it blurs whatever scrolls beneath it, and sits below
 * the island, dock and dots so those stay sharp. It draws only; it never takes touches.
 */
@Composable
internal fun ScrollEdgeBlur(backdrop: Backdrop, atTop: Boolean, height: Dp, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(height)) {
        EdgeBlur.bands.forEach { band ->
            Box(Modifier.fillMaxWidth().height(height * band.coverage)
                .align(if (atTop) androidx.compose.ui.Alignment.TopStart else androidx.compose.ui.Alignment.BottomStart)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // Opaque at the screen edge, transparent toward the content.
                    drawRect(
                        Brush.verticalGradient(
                            if (atTop) listOf(Color.Black, Color.Transparent) else listOf(Color.Transparent, Color.Black)),
                        blendMode = BlendMode.DstIn)
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = { blur(band.radiusDp.dp.toPx()) },
                    highlight = null,
                    shadow = null))
        }
    }
}
