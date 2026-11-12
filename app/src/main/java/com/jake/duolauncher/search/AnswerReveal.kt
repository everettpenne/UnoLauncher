package com.jake.duolauncher.search

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp

/**
 * How Ask Qwen's answer arrives: each new stretch of text fades in over [FADE_MS], starting in a soft rainbow tint and settling into the
 * ink, so the leading edge of the answer shimmers as it is written; and while it is being written a thin rainbow glow slowly circles the
 * card's edge, fading away once the answer is done. The colour sums are pure, so they are unit-tested.
 */
internal object AnswerReveal {
    const val FADE_MS = 380L
    /** One trip round the card for the glow. */
    const val GLOW_TURN_MS = 3600

    /** The rainbow the glow and the fresh text share: soft, so it reads as light rather than as colour. */
    val rainbow = listOf(Color(0xFF8AB4FF), Color(0xFFB98CFF), Color(0xFFFF8AC8), Color(0xFFFFB86B), Color(0xFF7DE3C8), Color(0xFF8AB4FF))

    /** How far a stretch that arrived [ageMs] ago has faded in, 0 to 1, eased out so it lands softly. */
    fun progress(ageMs: Long): Float {
        val t = (ageMs.toFloat() / FADE_MS).coerceIn(0f, 1f)
        return 1f - (1f - t) * (1f - t)
    }

    /** The colour of a character [index] of a stretch at [progress]: the rainbow, by position, fading into [ink]. */
    fun color(index: Int, progress: Float, ink: Color): Color {
        val tint = rainbow[(index / 3) % (rainbow.size - 1)]
        val p = progress.coerceIn(0f, 1f)
        return androidx.compose.ui.graphics.lerp(tint, ink, p).copy(alpha = ink.alpha * p)
    }
}

/**
 * [text] as it arrives: what is new since the last frame fades in from the rainbow (see [AnswerReveal]); what has settled is plain ink.
 * Only this text redraws while anything is still fading, and its frame loop stops as soon as nothing is.
 */
@Composable
internal fun RevealText(text: String, ink: Color, style: TextStyle, animate: Boolean, modifier: Modifier = Modifier) {
    if (!animate) { Text(text, modifier, color = ink, style = style); return }
    // Where each stretch of text begins, and when it arrived.
    val starts = remember { mutableStateListOf<Pair<Int, Long>>() }
    var now by remember { mutableLongStateOf(0L) }
    var known by remember { mutableStateOf(0) }
    if (text.length < known) { starts.clear(); known = 0 }
    LaunchedEffect(text.length) {
        if (text.length > known) {
            val at = withFrameNanos { it } / 1_000_000
            starts += known to at
            known = text.length
            now = at
        }
        // Run the clock only while something is still fading.
        while (starts.isNotEmpty() && now - starts.last().second < AnswerReveal.FADE_MS) now = withFrameNanos { it } / 1_000_000
    }
    val shown: AnnotatedString = buildAnnotatedString {
        append(text)
        starts.forEachIndexed { i, (from, at) ->
            val to = if (i + 1 < starts.size) starts[i + 1].first else text.length
            val p = AnswerReveal.progress(now - at)
            if (p >= 1f || from >= to) return@forEachIndexed
            for (c in from until to.coerceAtMost(text.length)) addStyle(SpanStyle(color = AnswerReveal.color(c, p, ink)), c, c + 1)
        }
    }
    BasicText(shown, modifier, style = style.copy(color = ink))
}

/**
 * The faint rainbow edge on the answer card while the answer is being written ([glowing]): a thin line and a wider, fainter halo just inside
 * the card's [shape], slowly turning, fading in as writing starts and out once it is done. Drawn over the card's content, inside its clip.
 */
@Composable
internal fun Modifier.answerGlow(glowing: Boolean, shape: Shape, animate: Boolean): Modifier {
    if (!animate) return this
    val strength by animateFloatAsState(if (glowing) 1f else 0f, tween(if (glowing) 300 else 900), label = "answer glow")
    if (strength <= 0f) return this
    // An infinite transition (not a hand-made frame loop), so it runs only while the glow shows, and tests can treat it as the endless
    // animation it is. Read only while drawing, so the turning redraws the edge and recomposes nothing.
    val turnState = rememberInfiniteTransition(label = "answer glow turn").animateFloat(0f, 1f,
        infiniteRepeatable(tween<Float>(AnswerReveal.GLOW_TURN_MS, easing = LinearEasing)), label = "turn")
    return drawWithContent {
        drawContent()
        val outline = shape.createOutline(size, layoutDirection, this)
        // One sweep of the rainbow round the card's centre, turned smoothly by its matrix (the outline itself stays put).
        val shader = android.graphics.SweepGradient(size.width / 2f, size.height / 2f,
            AnswerReveal.rainbow.map { it.toArgb() }.toIntArray(), null)
        shader.setLocalMatrix(android.graphics.Matrix().apply { setRotate(turnState.value * 360f, size.width / 2f, size.height / 2f) })
        val brush = androidx.compose.ui.graphics.ShaderBrush(shader)
        drawOutline(outline, brush, alpha = .16f * strength, style = Stroke(width = 7.dp.toPx()))
        drawOutline(outline, brush, alpha = .55f * strength, style = Stroke(width = 1.5.dp.toPx()))
    }
}
