package com.jake.duolauncher.search

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jake.duolauncher.appearance.ComposePathSink
import com.jake.duolauncher.island.IslandMorph
import com.jake.duolauncher.search.agent.AgentSessionHolder
import com.jake.duolauncher.search.agent.AgentState

/** What the assistant is doing, as its light shows it. */
internal enum class LightMood { OFF, THINKING, WRITING, WAITING, READY }

/**
 * The assistant's light: one moving band of colour that says the model is working, wherever the assistant is on screen (round the island's
 * pill, round search's field). Thinking and writing it travels in the answer's own colours and beats as words arrive; waiting on the
 * person it holds a steady amber; when the turn ends it settles to one calm colour and goes. The rules are pure, so they are unit-tested.
 */
internal object AssistantLightLogic {
    const val TURN_MS = 4200
    const val PULSE_MS = 480
    val amber = Color(0xFFFFB84D)
    val calm = Color(0xFF7DE3C8)

    /** A task's turn is the assistant's state while there is one; otherwise the answer's. */
    fun mood(agent: AgentState?, answer: AssistantSession.State, written: Int): LightMood = when (agent) {
        is AgentState.Planning, is AgentState.Acting -> LightMood.THINKING
        is AgentState.Confirming, is AgentState.Asking -> LightMood.WAITING
        is AgentState.Done -> LightMood.READY
        is AgentState.Failed -> LightMood.OFF
        AgentState.Idle, null -> when (answer) {
            is AssistantSession.State.Running -> if (written > 0) LightMood.WRITING else LightMood.THINKING
            is AssistantSession.State.Ready -> LightMood.READY
            else -> LightMood.OFF
        }
    }

    /** How bright the light is heading for: full while the model works, a little lower while it waits, and out once the turn is over. */
    fun strength(mood: LightMood): Float = when (mood) {
        LightMood.THINKING, LightMood.WRITING -> 1f
        LightMood.WAITING -> .8f
        LightMood.READY, LightMood.OFF -> 0f
    }

    /** How long it takes to get there: quick to come, slow to settle after an answer, so "ready" is seen as it goes. */
    fun fadeMs(mood: LightMood): Int = when (mood) {
        LightMood.THINKING, LightMood.WRITING, LightMood.WAITING -> 420
        LightMood.READY -> 1800
        LightMood.OFF -> 500
    }

    /** How far the colours have gone from the answer's rainbow to one colour (0 the rainbow, 1 that colour), and which. */
    fun tone(mood: LightMood): Pair<Float, Color> = when (mood) {
        LightMood.WAITING -> 1f to amber
        LightMood.READY -> 1f to calm
        else -> 0f to calm
    }

    /** The slow breath of a light that is waiting on the person, from the turn's [phase] (0..1): between .7 and 1. */
    fun breath(mood: LightMood, phase: Float): Float =
        if (mood == LightMood.WAITING) .85f + .15f * kotlin.math.sin(phase * 2f * Math.PI.toFloat() * 2f) else 1f

    /** The colours round the band, each drawn [toward] [tint]. The first and last are the same, so the turn has no seam. */
    fun palette(toward: Float, tint: Color): IntArray = AnswerReveal.edge.map { lerp(it, tint, toward.coerceIn(0f, 1f)).toArgb() }.toIntArray()
}

/** The assistant's mood now, from the turn in flight. Read where it is drawn; it changes a few times a turn. */
@Composable
internal fun rememberAssistantMood(): LightMood =
    AssistantLightLogic.mood(AgentSessionHolder.current()?.state, AssistantSession.state, AssistantSession.streamed.length)

@Composable
private fun animationsOn(): Boolean {
    val context = LocalContext.current
    return remember { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
}

/** What both lights share: how bright, which colours, where the band has turned to, and the beat of words arriving. */
private class LightFrame(val strength: () -> Float, val toward: () -> Float, val tint: Color, val phase: () -> Float, val beat: () -> Float, val mood: LightMood) {
    private var shader: android.graphics.SweepGradient? = null
    private var builtFor = Triple(-1f, -1f, -1f)
    private val matrix = android.graphics.Matrix()

    /** The turning band as a brush over a [w] by [h] box. The gradient is made again only when the box or the colours change. */
    fun brush(w: Float, h: Float): ShaderBrush {
        val t = toward()
        if (shader == null || builtFor != Triple(w, h, t)) {
            shader = android.graphics.SweepGradient(w / 2f, h / 2f, AssistantLightLogic.palette(t, tint), null)
            builtFor = Triple(w, h, t)
        }
        matrix.setRotate(phase() * 360f, w / 2f, h / 2f)
        shader!!.setLocalMatrix(matrix)
        return ShaderBrush(shader!!)
    }

    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE }
    private var blurFor = -1f

    /** The halo: the band's colours along [path] as one blurred stroke, so the light falls away smoothly with no steps in it. */
    fun glow(scope: androidx.compose.ui.graphics.drawscope.DrawScope, path: Path, w: Float, h: Float, widthPx: Float, blurPx: Float, alpha: Float) {
        brush(w, h)
        if (blurFor != blurPx) { paint.maskFilter = android.graphics.BlurMaskFilter(blurPx, android.graphics.BlurMaskFilter.Blur.NORMAL); blurFor = blurPx }
        paint.shader = shader
        paint.strokeWidth = widthPx
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        scope.drawContext.canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
    }

    fun level(): Float = strength() * AssistantLightLogic.breath(mood, phase()) * (1f + .7f * beat())
}

@Composable
private fun lightFrame(mood: LightMood, beats: Boolean): LightFrame? {
    val strength = animateFloatAsState(AssistantLightLogic.strength(mood), tween(AssistantLightLogic.fadeMs(mood)), label = "assistant light")
    val (towardTarget, tint) = AssistantLightLogic.tone(mood)
    val toward = animateFloatAsState(towardTarget, tween(400), label = "assistant light tone")
    // Nothing runs, and nothing is drawn, while there is no light.
    if (strength.value <= 0f && AssistantLightLogic.strength(mood) <= 0f) return null
    val phase = rememberInfiniteTransition(label = "assistant light turn").animateFloat(0f, 1f,
        infiniteRepeatable(tween<Float>(AssistantLightLogic.TURN_MS, easing = LinearEasing)), label = "turn")
    val beat = remember { Animatable(0f) }
    // Each run of words the model writes lifts the light for a moment, so it is seen to follow the writing.
    val written = if (beats && mood == LightMood.WRITING) AssistantSession.streamed.length / 12 else 0
    LaunchedEffect(written) {
        if (written > 0) { beat.snapTo(1f); beat.animateTo(0f, tween(AssistantLightLogic.PULSE_MS)) }
    }
    return remember(mood, tint) { LightFrame({ strength.value }, { toward.value }, tint, { phase.value }, { beat.value }, mood) }
}

/**
 * The assistant's light just inside the edge of [shape] (search's field): a thin bright line and a wider, fainter halo, turning. Drawn over
 * the content, inside its bounds. With Android's animations off there is no light.
 */
@Composable
internal fun Modifier.assistantLight(mood: LightMood, shape: Shape): Modifier {
    if (!animationsOn()) return this
    val frame = lightFrame(mood, beats = true) ?: return this
    return drawWithContent {
        drawContent()
        val level = frame.level()
        if (level <= .001f) return@drawWithContent
        val outline = shape.createOutline(size, layoutDirection, this)
        val brush = frame.brush(size.width, size.height)
        frame.glow(this, androidx.compose.ui.graphics.Path().apply { addOutline(outline) }, size.width, size.height, 5.dp.toPx(), 7.dp.toPx(), .5f * level)
        drawOutline(outline, brush, alpha = (.6f * level).coerceAtMost(1f), style = Stroke(width = 1.5.dp.toPx()))
    }
}

/**
 * The assistant's light round the island's pill: drawn under the body and outside its outline ([IslandMorph.outline]), so the pill stays
 * whole and black with the colour moving round it. [shown] is how far the island is shut (1 the resting pill): the light is the pill's, and
 * goes as the island opens. [cornerPx] is read as it draws, so it follows the morph without recomposing.
 */
@Composable
internal fun Modifier.islandAssistantLight(mood: LightMood, cornerPx: () -> Float, shown: () -> Float): Modifier {
    if (!animationsOn()) return this
    val frame = lightFrame(mood, beats = false) ?: return this
    val outline = remember { Path() }
    val sink = remember { ComposePathSink(outline) }
    val built = remember { floatArrayOf(-1f, -1f, -1f) }
    return drawWithContent {
        val w = size.width; val h = size.height
        val level = frame.level() * shown().coerceIn(0f, 1f)
        if (w <= 0f || h <= 0f || level <= .001f) { drawContent(); return@drawWithContent }
        val r = cornerPx().coerceIn(0f, minOf(w, h) / 2f)
        if (built[0] != w || built[1] != h || built[2] != r) {
            outline.rewind()
            IslandMorph.outline(w, h, r, density, sink)
            built[0] = w; built[1] = h; built[2] = r
        }
        val brush = frame.brush(w, h)
        // A stroke centred on the edge with the inside cut away: only the half outside the pill shows.
        clipPath(outline, ClipOp.Difference) {
            frame.glow(this, outline, w, h, 8.dp.toPx(), 9.dp.toPx(), .75f * level)
            drawPath(outline, brush, alpha = (.95f * level).coerceAtMost(1f), style = Stroke(3.dp.toPx()))
        }
        drawContent()
    }
}

/**
 * The assistant arriving over another app: light comes in from the edges of the screen and is gone in under a second, as search grows out of
 * the island. Once per opening; nothing with Android's animations off.
 */
@Composable
internal fun AssistArrival(open: Boolean) {
    if (!animationsOn()) return
    val glow = remember { Animatable(0f) }
    LaunchedEffect(open) {
        if (open) { glow.snapTo(0f); glow.animateTo(1f, tween(160)); glow.animateTo(0f, tween(760)) }
    }
    val colours = remember { AssistantLightLogic.palette(0f, AssistantLightLogic.calm) }
    val blur = with(androidx.compose.ui.platform.LocalDensity.current) { 30.dp.toPx() }
    val paint = remember(blur) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            maskFilter = android.graphics.BlurMaskFilter(blur, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val level = glow.value
        if (level <= .001f) return@Canvas
        val shader = android.graphics.SweepGradient(size.width / 2f, size.height / 2f, colours, null)
        val brush = ShaderBrush(shader)
        // One wide blurred stroke along the screen's edge (half of it falls off screen), and a fine bright line on the edge itself.
        val outline = com.jake.duolauncher.appearance.ContinuousShape(36.dp).createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        paint.shader = shader
        paint.strokeWidth = 34.dp.toPx()
        paint.alpha = (.55f * level * 255).toInt()
        drawContext.canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
        drawOutline(outline, brush, alpha = .7f * level, style = Stroke(2.dp.toPx()))
    }
}
