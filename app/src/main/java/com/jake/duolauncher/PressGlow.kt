package com.jake.duolauncher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlinx.coroutines.flow.collectLatest

/** Springy 0..1 press progress driven by an [InteractionSource]. One progress
 * value powers both the scale and the glow so they always stay in sync.
 */
@Composable
internal fun rememberPressProgress(interactionSource: InteractionSource): State<Float> {
    val progress = remember { Animatable(0f, 0.001f) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction: Interaction ->
            when (interaction) {
                is PressInteraction.Press -> progress.animateTo(1f, spring(0.45f, 260f))
                is PressInteraction.Release -> progress.animateTo(0f, spring(0.45f, 260f))
                is PressInteraction.Cancel -> progress.snapTo(0f)
            }
        }
    }
    return progress.asState()
}

/** Draws a soft white bloom over the content while [progress] is above zero. */
internal fun Modifier.pressGlow(progress: Float, shape: Shape): Modifier =
    clip(shape).drawWithContent {
        drawContent()
        if (progress > 0.001f) {
            drawRect(
                color = Color.White.copy(alpha = .18f * progress),
                topLeft = androidx.compose.ui.geometry.Offset.Zero,
                size = Size(size.width, size.height),
                blendMode = BlendMode.Plus,
            )
        }
    }
