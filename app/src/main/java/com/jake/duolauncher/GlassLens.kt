package com.jake.duolauncher

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight

/** iOS 26's selection lens: a small clear glass shape that refracts at its rim, magnifies
 * what is under it by [magnification], and lifts a little while [lift] (0..1) is held, like a
 * finger resting on the glass. Unlike [liquidGlass] it is nearly untinted, so the thing it sits
 * on stays legible; it only reads as a lens because of the bend, the zoom and the rim light.
 *
 * [backdrop] should include whatever the lens is meant to magnify: record the controls into a
 * layer backdrop and combine it with the Home backdrop, or the lens will only zoom the wallpaper.
 * [shape] must be a rounded rectangle (the glass library throws on continuous-curve shapes).
 */
@Composable
internal fun Modifier.glassLens(
    backdrop: Backdrop,
    shape: Shape,
    magnification: Float = 1.18f,
    lift: Float = 0f,
    tint: Color = Color.White.copy(alpha = .10f),
    settings: GlassSettings = GlassSettings.Default,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        // A lens this small bends over a few dp, not the 8-44 dp band of a panel; the settings knobs
        // still scale it so the whole interface shares one strength.
        lens(
            refractionHeight = lerp(3f, 10f, settings.height).dp.toPx(),
            refractionAmount = lerp(6f, 24f, settings.amount).dp.toPx(),
            depthEffect = true,
            chromaticAberration = settings.chromatic > 0.05f,
        )
    },
    highlight = { GlassRim.Light },
    layerBlock = {
        val grown = 1f + .14f * lift.coerceIn(0f, 1f)
        scaleX = grown
        scaleY = grown
    },
    onDrawBackdrop = { drawBackdrop ->
        // Zoom the sampled content about the lens centre; the shape clips it back to the lens.
        scale(magnification, pivot = center) { drawBackdrop() }
    },
    onDrawSurface = { drawRect(tint) },
)
