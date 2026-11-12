package com.jake.duolauncher.search

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * How Ask Qwen's answer arrives, in the manner of Google's: whole words only (a half-written word would wrap, then jump back), each fading
 * in as it drifts down a few dp into its place, eased to land softly; words that arrive together follow one another by [STAGGER_MS]. A
 * landing word is tinted from a soft rainbow that runs across the card by position ([tint]), one colour per word that never changes, and
 * settles from it into the ink as it lands, so the tint reads as light moving through the text rather than as flicker. While the answer is
 * written a faint glow slowly turns round the card's edge. The sums are pure, so they are unit-tested.
 */
internal object AnswerReveal {
    const val FADE_MS = 520L
    const val STAGGER_MS = 45L
    /** How far above its place a word starts, in dp. */
    const val FALL_DP = 7f
    /** One slow trip round the card for the glow. */
    const val GLOW_TURN_MS = 6000

    /** The glow's soft colours, as light rather than as colour. */
    val glow = listOf(Color(0xFF8AB4FF), Color(0xFFB98CFF), Color(0xFFFF8AC8), Color(0xFFFFB86B), Color(0xFF7DE3C8), Color(0xFF8AB4FF))

    /**
     * A landing word's tint: the soft rainbow ([glow]) read at the word's place across the card, [x] from 0 (left) to 1 (right), moved on a
     * little each [line] so the lines don't all start blue. Position only, never time or character, so a word keeps one colour.
     */
    fun tint(x: Float, line: Int): Color {
        val stops = glow.size - 1
        val t = ((x.coerceIn(0f, 1f) + line * .23f) % 1f) * stops
        val i = t.toInt().coerceIn(0, stops - 1)
        return androidx.compose.ui.graphics.lerp(glow[i], glow[i + 1], t - i)
    }

    /** A landing word's colour at [progress]: its [tint] settling into [ink]. */
    fun color(tint: Color, progress: Float, ink: Color): Color = androidx.compose.ui.graphics.lerp(tint, ink, progress.coerceIn(0f, 1f))

    /** How far a word that began landing [ageMs] ago has got, 0 to 1: a decelerating ease, so it slows into place. */
    fun progress(ageMs: Long): Float {
        val t = (ageMs.toFloat() / FADE_MS).coerceIn(0f, 1f)
        val u = 1f - t
        return 1f - u * u * u
    }

    /** What is shown while the answer is still being written: only up to the last complete word, so nothing reflows mid-word. */
    fun wholeWords(text: String, streaming: Boolean): String {
        if (!streaming) return text
        val end = text.indexOfLast { it.isWhitespace() }
        return if (end < 0) "" else text.substring(0, end).trimEnd()
    }

    /** The words in [text] from [from] on, as (start, end) ranges. */
    fun words(text: String, from: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        var i = from.coerceIn(0, text.length)
        while (i < text.length) {
            while (i < text.length && text[i].isWhitespace()) i++
            val s = i
            while (i < text.length && !text[i].isWhitespace()) i++
            if (i > s) out += s to i
        }
        return out
    }
}

private class Landing(val start: Int, val end: Int, val at: Long)

/**
 * [text] as it is written ([streaming]), each new word falling into place (see [AnswerReveal]). The settled text is ordinary text; only
 * the words still landing are drawn, in the draw phase, each clipped to its own place and moved down into it, so the animation recomposes
 * nothing per frame and its clock stops as soon as every word has landed.
 */
@Composable
internal fun RevealText(text: String, streaming: Boolean, ink: Color, style: TextStyle, animate: Boolean, modifier: Modifier = Modifier) {
    val shown = AnswerReveal.wholeWords(text, streaming)
    if (!animate) { Text(shown, modifier, color = ink, style = style); return }
    val landing = remember { mutableStateListOf<Landing>() }
    var known by remember { mutableIntStateOf(0) }
    var clock by remember { mutableLongStateOf(0L) }
    if (shown.length < known) { landing.clear(); known = 0 }
    LaunchedEffect(shown) {
        if (shown.length > known) {
            val now = withFrameNanos { it } / 1_000_000
            val after = landing.lastOrNull()?.at ?: Long.MIN_VALUE / 2
            AnswerReveal.words(shown, known).forEachIndexed { i, (s, e) ->
                landing += Landing(s, e, maxOf(now + i * AnswerReveal.STAGGER_MS, after + (i + 1) * AnswerReveal.STAGGER_MS))
            }
            known = shown.length
            clock = now
        }
        while (landing.isNotEmpty()) {
            clock = withFrameNanos { it } / 1_000_000
            // A landed word becomes ordinary text (a change to the list, so a recomposition only as each one lands, not every frame).
            if (landing.any { clock - it.at >= AnswerReveal.FADE_MS }) landing.removeAll { clock - it.at >= AnswerReveal.FADE_MS }
        }
    }
    // The text with the words still landing left out (transparent, so the lines are laid out exactly as they will be).
    val ranges = landing.map { it.start to it.end }
    val annotated = remember(shown, ranges) {
        buildAnnotatedString { append(shown); ranges.forEach { (s, e) -> if (e <= shown.length) addStyle(SpanStyle(color = Color.Transparent), s, e) } }
    }
    val measurer = rememberTextMeasurer()
    val laidOut = remember { arrayOfNulls<TextLayoutResult>(1) }
    val plain = remember { arrayOfNulls<Pair<Pair<String, Int>, TextLayoutResult>>(1) }
    BasicText(annotated, modifier.drawWithContent {
        drawContent()
        val layout = laidOut[0] ?: return@drawWithContent
        if (landing.isEmpty()) return@drawWithContent
        // The same text, fully drawn, laid out once per text and width: each landing word is drawn from it, clipped to that word.
        val key = shown to layout.size.width
        val full = plain[0]?.takeIf { it.first == key }?.second
            ?: measurer.measure(AnnotatedString(shown), style.copy(color = ink), constraints = Constraints.fixedWidth(layout.size.width))
                .also { plain[0] = key to it }
        val fall = AnswerReveal.FALL_DP.dp.toPx()
        val pad = 2.dp.toPx()
        for (word in landing) {
            val p = AnswerReveal.progress(clock - word.at)
            if (p <= 0f || word.end > shown.length) continue
            val first = full.getLineForOffset(word.start)
            val last = full.getLineForOffset((word.end - 1).coerceAtLeast(word.start))
            for (line in first..last) {
                val from = maxOf(word.start, full.getLineStart(line))
                val to = minOf(word.end, full.getLineEnd(line, visibleEnd = true))
                if (to <= from) continue
                val a = full.getHorizontalPosition(from, true); val b = full.getHorizontalPosition(to, true)
                val tint = AnswerReveal.tint(((a + b) / 2f) / layout.size.width.coerceAtLeast(1), line)
                clipRect(minOf(a, b) - pad, full.getLineTop(line) - fall - pad, maxOf(a, b) + pad, full.getLineBottom(line) + pad) {
                    translate(top = -(1f - p) * fall) { drawText(full, color = AnswerReveal.color(tint, p, ink), alpha = p) }
                }
            }
        }
    }, style = style.copy(color = ink), onTextLayout = { laidOut[0] = it })
}

/**
 * A faint glow on the answer card's edge while the answer is written ([glowing]): a thin line and a wider, fainter halo just inside the
 * card's [shape], turning slowly, fading in as writing starts and out once it is done. Drawn over the card, inside its clip.
 */
@Composable
internal fun Modifier.answerGlow(glowing: Boolean, shape: Shape, animate: Boolean): Modifier {
    if (!animate) return this
    val strength by animateFloatAsState(if (glowing) 1f else 0f, tween(if (glowing) 500 else 1200), label = "answer glow")
    if (strength <= 0f) return this
    // An infinite transition, so it runs only while the glow shows, and tests can treat it as the endless animation it is. Read only while
    // drawing, so the turning redraws the edge and recomposes nothing.
    val turnState = rememberInfiniteTransition(label = "answer glow turn").animateFloat(0f, 1f,
        infiniteRepeatable(tween<Float>(AnswerReveal.GLOW_TURN_MS, easing = LinearEasing)), label = "turn")
    return drawWithContent {
        drawContent()
        val outline = shape.createOutline(size, layoutDirection, this)
        val shader = android.graphics.SweepGradient(size.width / 2f, size.height / 2f, AnswerReveal.glow.map { it.toArgb() }.toIntArray(), null)
        shader.setLocalMatrix(android.graphics.Matrix().apply { setRotate(turnState.value * 360f, size.width / 2f, size.height / 2f) })
        val brush = androidx.compose.ui.graphics.ShaderBrush(shader)
        drawOutline(outline, brush, alpha = .09f * strength, style = Stroke(width = 8.dp.toPx()))
        drawOutline(outline, brush, alpha = .32f * strength, style = Stroke(width = 1.dp.toPx()))
    }
}
