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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * How Ask Qwen's answer arrives: a cascade that runs through it letter by letter at a reading pace. Each letter fades in over [IN_MS] as it
 * drops [FALL_DP] into place, arriving in the rainbow, and then takes [SETTLE_MS] to settle into the ink, so the colour trails behind the
 * cascade across a few dozen letters, fading as it goes, rather than sitting on one word. The cascade keeps up with the model: it runs at
 * [BASE_CPS] letters a second and speeds up when it falls behind ([speed]), which also reveals an answer that arrived whole (reopened from
 * the island) as a short, graceful run rather than all at once. While the answer is still being written only whole words are released to
 * it, so a half-written word never wraps and jumps back. Each letter's colour depends only on its place in the text and its age, never on
 * other letters, so nothing flickers. The sums are pure, so they are unit-tested.
 */
internal object AnswerReveal {
    const val IN_MS = 300L
    const val SETTLE_MS = 1400L
    /** How far above its place a letter starts, in dp. */
    const val FALL_DP = 3f
    /** The glow's blur at the cascade's leading edge, in dp. */
    const val GLOW_DP = 8f
    /** The cascade's reading pace, and how much faster it runs for each letter it is behind. */
    const val BASE_CPS = 60f
    const val CATCH_UP_PER_LETTER = 2.5f
    const val MAX_CPS = 600f
    /** One slow trip round the card for the edge glow. */
    const val GLOW_TURN_MS = 6000

    /** The rainbow the leading letters arrive in. */
    val rainbow = listOf(Color(0xFFFF5E62), Color(0xFFFF9966), Color(0xFF38EF7D), Color(0xFF00F2FE), Color(0xFFA855F7), Color(0xFFEC4899))
    /** The card edge's softer colours. */
    val edge = listOf(Color(0xFF8AB4FF), Color(0xFFB98CFF), Color(0xFFFF8AC8), Color(0xFFFFB86B), Color(0xFF7DE3C8), Color(0xFF8AB4FF))

    /** The cascade's speed, letters a second, when [behind] letters are waiting for it. */
    fun speed(behind: Int): Float = (BASE_CPS + behind.coerceAtLeast(0) * CATCH_UP_PER_LETTER).coerceAtMost(MAX_CPS)

    /** A letter coming in, [ageMs] after the cascade reached it: 0 to 1, decelerating so it lands softly. */
    fun fadeIn(ageMs: Long): Float = ease((ageMs.toFloat() / IN_MS).coerceIn(0f, 1f))

    /** A letter leaving the rainbow, [ageMs] after the cascade reached it: 0 (rainbow) to 1 (ink), slow and soft, so the colour trails. */
    fun settle(ageMs: Long): Float {
        val t = (ageMs.toFloat() / SETTLE_MS).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** A fast-out, slow-in curve: most of the change early, a long soft finish. */
    private fun ease(t: Float): Float { val u = 1f - t; return 1f - u * u * u * u }

    /** The rainbow at letter [index]: a slow sweep along the text, so neighbouring letters are neighbouring colours. */
    fun hue(index: Int): Color {
        val stops = rainbow.size - 1
        val t = ((index % 48) / 48f) * stops
        val i = t.toInt().coerceIn(0, stops - 1)
        return androidx.compose.ui.graphics.lerp(rainbow[i], rainbow[i + 1], t - i)
    }

    /** Letter [index]'s colour, [ageMs] after the cascade reached it: its rainbow settling into [ink], fading in as it arrives. */
    fun color(index: Int, ageMs: Long, ink: Color): Color {
        val c = androidx.compose.ui.graphics.lerp(hue(index), ink, settle(ageMs))
        return c.copy(alpha = c.alpha * fadeIn(ageMs))
    }

    /** What is released to the cascade while the answer is still being written: only up to the last complete word, so nothing reflows. */
    fun wholeWords(text: String, streaming: Boolean): String {
        if (!streaming) return text
        val end = text.indexOfLast { it.isWhitespace() }
        return if (end < 0) "" else text.substring(0, end).trimEnd()
    }
}

/** How often the plain text catches up with the settled letters: each time re-lays out the text, so not every frame. */
private const val SETTLED_STEP = 16

/**
 * [text] revealed by the letter cascade (see [AnswerReveal]). Behind it is ordinary text, kept [SETTLED_STEP] letters at a time; the letters
 * still arriving or settling are drawn on top from the same layout, in the draw phase: the freshest few one by one (they drop into place),
 * the rest as one stretch per line whose gradient gives each letter its own colour. So a frame recomposes nothing, the text re-lays out
 * only a few times a second, and the clock stops when every letter has settled.
 */
@Composable
internal fun RevealText(text: String, streaming: Boolean, ink: Color, style: TextStyle, animate: Boolean, modifier: Modifier = Modifier) {
    val shown = AnswerReveal.wholeWords(text, streaming)
    if (!animate) { Text(shown, modifier, color = ink, style = style); return }
    // When the cascade reached each letter (ms); the cascade's position (fractional letters); and the plain text's settled end.
    val reached = remember { LongArray(4096) }
    val head = remember { FloatArray(1) }
    var settledEnd by remember { mutableIntStateOf(0) }
    var clock by remember { mutableLongStateOf(0L) }
    if (head[0] > shown.length) { head[0] = 0f; settledEnd = 0 }
    LaunchedEffect(shown) {
        var last = withFrameNanos { it } / 1_000_000
        clock = last
        while (true) {
            val now = withFrameNanos { it } / 1_000_000
            val dt = (now - last).coerceIn(0L, 64L) / 1000f
            last = now
            val before = head[0]
            val target = minOf(shown.length, reached.size).toFloat()
            if (before < target) {
                val cps = AnswerReveal.speed((target - before).toInt())
                head[0] = minOf(target, before + cps * dt)
                // Each letter passed this frame gets the moment the cascade really passed it, so a fast run still cascades smoothly.
                for (i in before.toInt() until head[0].toInt()) reached[i] = now - (((head[0] - (i + 1)) / cps) * 1000f).toLong().coerceAtLeast(0L)
            }
            clock = now
            // The plain text catches up in steps, once letters have settled.
            var settled = settledEnd
            while (settled < head[0].toInt() && now - reached[settled] >= AnswerReveal.SETTLE_MS) settled++
            if (settled >= settledEnd + SETTLED_STEP || (settled == head[0].toInt() && settled > settledEnd)) settledEnd = settled
            // Done once the cascade has caught up and everything it passed has settled; the next text starts it again.
            if (head[0] >= target && settledEnd >= target.toInt()) break
        }
    }
    val end = settledEnd.coerceAtMost(shown.length)
    // Read in composition as well as in draw: in search's own window over an app, a value read only in draw did not redraw the text, so
    // a reopened answer stayed blank until its letters settled and then appeared at once. Reading it here redraws every frame of the run
    // (and only while it runs: the clock stops once every letter has settled).
    val now = clock
    // Ordinary text up to the settled end; the rest left out (transparent, so every line is laid out exactly as it will be).
    val annotated = remember(shown, end) {
        buildAnnotatedString { append(shown); if (end < shown.length) addStyle(SpanStyle(color = Color.Transparent), end, shown.length) }
    }
    val measurer = rememberTextMeasurer()
    val laidOut = remember { arrayOfNulls<TextLayoutResult>(1) }
    val plain = remember { arrayOfNulls<Pair<Pair<String, Int>, TextLayoutResult>>(1) }
    BasicText(annotated, modifier.drawWithContent {
        drawContent()
        val layout = laidOut[0] ?: return@drawWithContent
        val upTo = minOf(head[0].toInt(), shown.length)
        if (upTo <= end) return@drawWithContent
        val key = shown to layout.size.width
        val full = plain[0]?.takeIf { it.first == key }?.second
            ?: measurer.measure(shown, style.copy(color = ink), constraints = Constraints.fixedWidth(layout.size.width)).also { plain[0] = key to it }
        drawCascade(full, shown, end, upTo, reached, now, ink)
    }, style = style.copy(color = ink), onTextLayout = { laidOut[0] = it })
}

/** Draws letters [from] to [to] of [full] as the cascade has them at [now]. */
private fun DrawScope.drawCascade(full: TextLayoutResult, text: String, from: Int, to: Int, reached: LongArray, now: Long, ink: Color) {
    val fall = AnswerReveal.FALL_DP.dp.toPx()
    val pad = 1.dp.toPx()
    // The freshest letters, still coming in, one by one so each can drop into place; the glow sits on the very newest.
    var fresh = to
    while (fresh > from && now - reached[fresh - 1] < AnswerReveal.IN_MS) fresh--
    var line = -1; var top = 0f; var bottom = 0f
    for (i in from until to) {
        val ln = full.getLineForOffset(i)
        if (ln != line) { line = ln; top = full.getLineTop(ln); bottom = full.getLineBottom(ln) }
        if (i < fresh) continue
        if (text[i].isWhitespace()) continue
        val box = full.getBoundingBox(i)
        val age = now - reached[i]
        val p = AnswerReveal.fadeIn(age)
        val c = AnswerReveal.color(i, age, ink)
        clipRect(box.left - pad, top - fall - pad, box.right + pad, bottom + pad) {
            translate(top = -(1f - p) * fall) {
                drawText(full, color = c.copy(alpha = 1f), alpha = c.alpha,
                    shadow = if (age < AnswerReveal.IN_MS) Shadow(AnswerReveal.hue(i).copy(alpha = .55f * (1f - p)), Offset.Zero, AnswerReveal.GLOW_DP.dp.toPx()) else null)
            }
        }
    }
    // The letters settling: one stretch per line, its gradient giving each letter its colour and opacity.
    var i = from
    while (i < fresh) {
        val ln = full.getLineForOffset(i)
        val lineEnd = minOf(fresh, full.getLineEnd(ln, visibleEnd = true).coerceAtLeast(i + 1))
        val left = full.getHorizontalPosition(i, true); val right = full.getHorizontalPosition(lineEnd, true)
        if (right > left + 1f) {
            val stops = ArrayList<Pair<Float, Color>>()
            for (k in i until lineEnd) {
                val b = full.getBoundingBox(k)
                val mid = ((b.left + b.right) / 2f - left) / (right - left)
                stops += mid.coerceIn(0f, 1f) to AnswerReveal.color(k, now - reached[k], ink)
            }
            if (stops.size == 1) stops += 1f to stops[0].second
            val brush = Brush.horizontalGradient(*stops.sortedBy { it.first }.toTypedArray(), startX = left, endX = right)
            clipRect(left - pad, full.getLineTop(ln), right + pad, full.getLineBottom(ln)) { drawText(full, brush = brush) }
        }
        i = maxOf(lineEnd, i + 1)
    }
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
