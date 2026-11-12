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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/**
 * How Ask Qwen's answer arrives, after Google's on-device assistant: whole words only (a half-written word would wrap, then jump back).
 * The newest word fades in over [IN_MS] as it drops a few dp into place and grows from 97 %, painted with a rainbow gradient and a soft
 * glow; when the next word arrives it cross-fades from the rainbow into the ink over [SETTLE_MS]. So the colour rides the leading edge of
 * the answer, one word at a time, and everything behind it is ordinary text. Each word's gradient is fixed (its phase comes from its
 * place in the answer), so nothing flickers. While the answer is written a faint glow slowly turns round the card's edge. The sums are
 * pure, so they are unit-tested.
 */
internal object AnswerReveal {
    const val IN_MS = 240L
    const val SETTLE_MS = 380L
    /** Words that arrive together follow one another by this much. */
    const val STAGGER_MS = 45L
    /** How far above its place a new word starts, in dp. */
    const val FALL_DP = 3f
    /** The glow's blur, in dp. */
    const val GLOW_DP = 9f
    /** One slow trip round the card for the edge glow. */
    const val GLOW_TURN_MS = 6000

    /** The leading word's rainbow. */
    val rainbow = listOf(Color(0xFFFF5E62), Color(0xFFFF9966), Color(0xFF38EF7D), Color(0xFF00F2FE), Color(0xFFA855F7), Color(0xFFEC4899))
    /** The card edge's softer colours. */
    val edge = listOf(Color(0xFF8AB4FF), Color(0xFFB98CFF), Color(0xFFFF8AC8), Color(0xFFFFB86B), Color(0xFF7DE3C8), Color(0xFF8AB4FF))

    /** A word coming in, [ageMs] after it arrived: 0 to 1, decelerating so it lands softly. */
    fun fadeIn(ageMs: Long): Float = ease((ageMs.toFloat() / IN_MS).coerceIn(0f, 1f))

    /** A word leaving the rainbow, [ageMs] after the next word arrived (or the answer finished): 0 (rainbow) to 1 (ink). */
    fun settle(ageMs: Long): Float = ease((ageMs.toFloat() / SETTLE_MS).coerceIn(0f, 1f))

    /** A fast-out, slow-in curve (as cubic-bezier(.22, 1, .36, 1)): most of the change early, a long soft finish. */
    private fun ease(t: Float): Float { val u = 1f - t; return 1f - u * u * u * u }

    /** Where a word's rainbow starts, 0 to 1 of the gradient: shifted on for each word, so neighbours differ but each keeps its own. */
    fun phase(wordIndex: Int): Float = ((wordIndex * 23) % 100) / 100f

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

/** A word still arriving or settling: its range, its place among the words, when it arrived, and when it began to settle. */
private class Landing(val start: Int, val end: Int, val index: Int, val arrived: Long) {
    var settleFrom: Long? = null
}

/**
 * [text] as it is written ([streaming]), its leading word in a rainbow that settles into [ink] as the next arrives (see [AnswerReveal]).
 * The settled text is ordinary text; the words still arriving or settling are drawn on their own, in the draw phase, from their own small
 * layouts placed exactly where the full text puts them, so the animation recomposes nothing per frame, the glow touches no other word,
 * and the clock stops once every word has settled.
 */
@Composable
internal fun RevealText(text: String, streaming: Boolean, ink: Color, style: TextStyle, animate: Boolean, modifier: Modifier = Modifier) {
    val shown = AnswerReveal.wholeWords(text, streaming)
    if (!animate) { Text(shown, modifier, color = ink, style = style); return }
    val landing = remember { mutableStateListOf<Landing>() }
    var known by remember { mutableIntStateOf(0) }
    var count by remember { mutableIntStateOf(0) }
    var clock by remember { mutableLongStateOf(0L) }
    if (shown.length < known) { landing.clear(); known = 0; count = 0 }
    LaunchedEffect(shown, streaming) {
        val now = withFrameNanos { it } / 1_000_000
        clock = now
        if (shown.length > known) {
            val fresh = AnswerReveal.words(shown, known)
            if (known == 0 && !streaming) {
                // A finished answer shown whole (one from earlier, or a reply from the table): it simply appears, settled.
                count = fresh.size
            } else fresh.forEach { (s, e) ->
                val last = landing.lastOrNull()
                val at = maxOf(now, (last?.arrived ?: Long.MIN_VALUE / 2) + AnswerReveal.STAGGER_MS)
                // The word before it starts leaving the rainbow as this one arrives.
                last?.let { if (it.settleFrom == null) it.settleFrom = at }
                landing += Landing(s, e, count++, at)
            }
            known = shown.length
        }
        // Done: the last word settles too, once it has finished coming in.
        if (!streaming) landing.lastOrNull()?.let { if (it.settleFrom == null) it.settleFrom = maxOf(now, it.arrived + AnswerReveal.IN_MS) }
        // Only while something moves: a word coming in, or one settling. The newest word, once in, waits in its rainbow without a clock
        // (nothing redraws) until the next word arrives or the answer finishes, which starts this again.
        while (landing.any { clock - it.arrived < AnswerReveal.IN_MS || it.settleFrom != null }) {
            clock = withFrameNanos { it } / 1_000_000
            // A settled word becomes ordinary text (a change to the list, so a recomposition only as each one settles, not every frame).
            val gone = landing.filter { w -> w.settleFrom?.let { clock - it >= AnswerReveal.SETTLE_MS } == true }
            if (gone.isNotEmpty()) landing.removeAll(gone)
        }
    }
    // The words this frame's text leaves out (transparent, so the lines are laid out exactly as they will be), and the same words are the
    // ones drawn on top. One list for both: a word that finished settling must stay drawn on top until the text that leaves it out is
    // rebuilt (the next composition), or it vanished for a frame, which read as a flicker.
    val drawn = landing.toList()
    val ranges = drawn.map { it.start to it.end }
    // Text not yet taken in by the animation (it arrived this frame; the effect above picks it up next) is left out too, or a new word
    // showed in plain ink for a frame before fading in from the rainbow: a flash.
    val pending = known.coerceAtMost(shown.length)
    val annotated = remember(shown, ranges, pending) {
        buildAnnotatedString {
            append(shown)
            ranges.forEach { (s, e) -> if (e <= shown.length) addStyle(SpanStyle(color = Color.Transparent), s, e) }
            if (pending < shown.length) addStyle(SpanStyle(color = Color.Transparent), pending, shown.length)
        }
    }
    val measurer = rememberTextMeasurer()
    val laidOut = remember { arrayOfNulls<TextLayoutResult>(1) }
    val words = remember { HashMap<String, TextLayoutResult>() }
    BasicText(annotated, modifier.drawWithContent {
        drawContent()
        val layout = laidOut[0] ?: return@drawWithContent
        if (drawn.isEmpty()) return@drawWithContent
        val fall = AnswerReveal.FALL_DP.dp.toPx()
        val glow = AnswerReveal.GLOW_DP.dp.toPx()
        for (word in drawn) {
            if (word.end > shown.length || word.end > layout.layoutInput.text.length) continue
            val p = AnswerReveal.fadeIn(clock - word.arrived)
            if (p <= 0f) continue
            val s = word.settleFrom?.let { AnswerReveal.settle(clock - it) } ?: 0f
            val piece = shown.substring(word.start, word.end)
            val own = words.getOrPut(piece) { measurer.measure(piece, style.copy(color = ink)) }
            val line = layout.getLineForOffset(word.start)
            val x = layout.getHorizontalPosition(word.start, true)
            val y = layout.getLineBaseline(line) - own.firstBaseline
            val w = own.size.width.toFloat(); val h = own.size.height.toFloat()
            withTransform({
                translate(x, y - (1f - p) * fall)
                val k = .97f + .03f * p
                scale(k, k, Offset(w / 2f, h / 2f))
            }) {
                if (s < 1f) {
                    // The rainbow: one gradient across the word, its start shifted per word; and its glow, a blur of the same colours.
                    val span = maxOf(w, 64.dp.toPx()) * 2.2f
                    val shift = AnswerReveal.phase(word.index) * span * .5f
                    val brush = Brush.linearGradient(AnswerReveal.rainbow, Offset(-shift, 0f), Offset(-shift + span, span * .36f))
                    val tint = AnswerReveal.rainbow[word.index % AnswerReveal.rainbow.size]
                    drawText(own, brush = brush, alpha = p * (1f - s),
                        shadow = Shadow(tint.copy(alpha = .75f * p * (1f - s)), Offset.Zero, glow))
                }
                if (s > 0f) drawText(own, color = ink, alpha = p * s)
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
        val shader = android.graphics.SweepGradient(size.width / 2f, size.height / 2f, AnswerReveal.edge.map { it.toArgb() }.toIntArray(), null)
        shader.setLocalMatrix(android.graphics.Matrix().apply { setRotate(turnState.value * 360f, size.width / 2f, size.height / 2f) })
        val brush = androidx.compose.ui.graphics.ShaderBrush(shader)
        drawOutline(outline, brush, alpha = .09f * strength, style = Stroke(width = 8.dp.toPx()))
        drawOutline(outline, brush, alpha = .32f * strength, style = Stroke(width = 1.dp.toPx()))
    }
}
