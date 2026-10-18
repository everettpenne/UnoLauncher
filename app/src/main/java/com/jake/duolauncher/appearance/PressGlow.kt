package com.jake.duolauncher.appearance

import com.jake.duolauncher.island.*

import com.jake.duolauncher.*
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

/**
 * The launcher's one press spring. Every press site on Home reads from here so a tap feels the same
 * everywhere: slightly under-damped (damping below 1), so a press settles with a single quick bounce,
 * the iOS press feel. [PressGlow] and [GlassPress] both use it; the island themes its own press feel
 * from [com.jake.duolauncher.island.IslandTheme], a separate motion system.
 */
internal object PressSpring {
    const val DAMPING_RATIO = 0.45f
    const val STIFFNESS = 260f

    /** The shared spec for a [Float] press progress (0..1), tuned to the Animatable's own 0.001 threshold. */
    fun float(visibilityThreshold: Float = 0.001f) = spring(DAMPING_RATIO, STIFFNESS, visibilityThreshold)
}

/** Springy 0..1 press progress driven by an [InteractionSource]. One progress
 * value powers both the scale and the glow so they always stay in sync.
 */
@Composable
internal fun rememberPressProgress(interactionSource: InteractionSource): State<Float> {
    val progress = remember { Animatable(0f, 0.001f) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction: Interaction ->
            when (interaction) {
                is PressInteraction.Press -> progress.animateTo(1f, PressSpring.float())
                is PressInteraction.Release -> progress.animateTo(0f, PressSpring.float())
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
