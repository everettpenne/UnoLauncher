package com.jake.duolauncher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier


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
                is PressInteraction.Press -> progress.animateTo(1f, spring(0.6f, 520f))
                is PressInteraction.Release -> progress.animateTo(0f, spring(0.6f, 520f))
                is PressInteraction.Cancel -> progress.snapTo(0f)
            }
        }
    }
    return progress.asState()
}

/** Used to draw a soft white bloom over a pressed icon, which read as a shadow flashing over the Home screen on every tap.
 * Pressing is now shown by the springy scale alone, as on iOS; the function stays so every press site keeps its modifier.
 */
@Suppress("UNUSED_PARAMETER")
internal fun Modifier.pressGlow(progress: Float, shape: Shape): Modifier = this

/** No ripple or highlight on press anywhere in the launcher: the Material ripple drew a grey shadow under every tap on Home. */
internal object NoIndication : androidx.compose.foundation.IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): androidx.compose.ui.node.DelegatableNode =
        object : Modifier.Node() {}
    override fun equals(other: Any?) = other === this
    override fun hashCode() = -1
}
