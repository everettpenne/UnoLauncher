package com.jake.duolauncher.search

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jake.duolauncher.search.agent.AgentSessionHolder
import kotlinx.coroutines.launch

/**
 * The moment the assistant finishes: one wave of split colour crosses the whole screen, out from the island, and is gone in under a
 * second. Along the wave's front the picture is bent a little and its red and blue are pulled apart (chromatic aberration, as light
 * through thick glass), and a fainter split follows it out to the screen's corners. It fires once, on the turn ending well: never on a
 * failure, and never for an answer that was already finished when the screen was drawn (reopened from the island). The sums are pure,
 * so they are unit-tested.
 */
internal object AnswerAberrationLogic {
    const val DURATION_MS = 950
    /** How far red and blue part at the wave's front at its strongest, in dp, and how far behind it out at the screen's far corner. */
    const val SPLIT_DP = 22f
    const val LENS_DP = 10f
    /** How far the picture is pushed along the wave's front, in dp, and how wide the front is. */
    const val BEND_DP = 7f
    const val FRONT_DP = 120f
    /** The part of the run the wave takes to come up to full strength. */
    private const val RISE = .14f

    /** Whether the turn has just ended well: the model was working ([was]) and is now done ([now]). */
    fun fires(was: LightMood, now: LightMood): Boolean = (was == LightMood.THINKING || was == LightMood.WRITING) && now == LightMood.READY

    /** How strong the split is, [t] (0..1) into the run: up quickly, then a long soft fall to nothing. */
    fun strength(t: Float): Float = when {
        t <= 0f || t >= 1f -> 0f
        t < RISE -> { val u = t / RISE; u * u * (3f - 2f * u) }
        else -> { val u = (1f - t) / (1f - RISE); u * u }
    }

    /** How far the wave's front has got, [t] into the run, as a share of the way to the farthest corner (and a little past it, so the
     *  front has left the screen before the run ends): fast out, slowing. */
    fun front(t: Float): Float { val u = 1f - t.coerceIn(0f, 1f); return 1.15f * (1f - u * u) }
}

private const val ABERRATION_AGSL = """
uniform shader content;
uniform float2 size;
uniform float2 origin;
uniform float reach;
uniform float front;
uniform float width;
uniform float split;
uniform float lens;
uniform float bend;

half4 main(float2 coord) {
    float2 v = coord - origin;
    float d = length(v);
    float2 dir = d > 0.5 ? v / d : float2(0.0);
    float x = (d - front) / width;
    float wave = exp(-x * x);
    float far = clamp(d / reach, 0.0, 1.0);
    float2 base = coord - dir * (bend * wave);
    float2 apart = dir * (split * wave + lens * far * far);
    // Held inside the picture: past its edge there is nothing, which would show as a line of one colour along the screen's edge.
    float2 lo = float2(0.5);
    float2 hi = size - float2(0.5);
    half4 r = content.eval(clamp(base - apart, lo, hi));
    half4 g = content.eval(clamp(base, lo, hi));
    half4 b = content.eval(clamp(base + apart, lo, hi));
    half4 ro = content.eval(clamp(base - apart * 0.5, lo, hi));
    half4 bo = content.eval(clamp(base + apart * 0.5, lo, hi));
    return half4(r.r * 0.6 + ro.r * 0.4, g.g, b.b * 0.6 + bo.b * 0.4, max(g.a, max(max(r.a, ro.a), max(b.a, bo.a))));
}
"""

/** The shader and the numbers it is given, kept apart so nothing here is loaded below Android 13. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AberrationShader {
    private val shader = android.graphics.RuntimeShader(ABERRATION_AGSL)

    fun effect(t: Float, w: Float, h: Float, density: Float): androidx.compose.ui.graphics.RenderEffect {
        // Out from the island: the top of the screen, in the middle.
        val ox = w / 2f; val oy = 24f * density
        val reach = kotlin.math.hypot(maxOf(ox, w - ox), h - oy)
        val strength = AnswerAberrationLogic.strength(t)
        shader.setFloatUniform("size", w, h)
        shader.setFloatUniform("origin", ox, oy)
        shader.setFloatUniform("reach", reach)
        shader.setFloatUniform("front", AnswerAberrationLogic.front(t) * reach)
        shader.setFloatUniform("width", AnswerAberrationLogic.FRONT_DP * density)
        shader.setFloatUniform("split", AnswerAberrationLogic.SPLIT_DP * density * strength)
        shader.setFloatUniform("lens", AnswerAberrationLogic.LENS_DP * density * strength)
        shader.setFloatUniform("bend", AnswerAberrationLogic.BEND_DP * density * strength)
        return android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}

/**
 * A whole screen of [content] that the finished-answer wave crosses (see [AnswerAberrationLogic]). At rest it adds nothing: the layer and
 * its shader exist only while a wave runs, and the run's clock is read as the layer is drawn, so a frame recomposes nothing. No wave on
 * Android 12 (no runtime shaders there) or with Android's animations off. [testProgress], for pictures in tests, holds the wave at one
 * moment of its run.
 */
@Composable
internal fun AnswerAberration(testProgress: Float? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val animations = remember {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !animations) { Box(Modifier.fillMaxSize()) { content() }; return }
    // The mood without the count of letters written, so the screen is not recomposed as the answer streams: it changes a few times a turn.
    val run = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        // The mood without the count of letters written (it changes a few times a turn), watched as it changes and not as the screen is
        // next drawn: a screen that is not showing (Home behind another app) lets the finish go by, so coming back to it later shows
        // no wave for an answer that finished a while ago.
        var was = AssistantLightLogic.mood(AgentSessionHolder.current()?.state, AssistantSession.state, 0)
        snapshotFlow { AssistantLightLogic.mood(AgentSessionHolder.current()?.state, AssistantSession.state, 0) }.collect { now ->
            val fire = AnswerAberrationLogic.fires(was, now) && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
            was = now
            // In the screen's own scope, so the wave runs out even if the mood moves on (the answer cleared, the next question asked).
            if (fire) scope.launch {
                run.snapTo(0f)
                run.animateTo(1f, tween(AnswerAberrationLogic.DURATION_MS, easing = LinearEasing))
            }
        }
    }
    val running = testProgress != null || run.isRunning
    Box(Modifier.fillMaxSize().then(if (running) Modifier.testTag("answer-aberration").aberration { testProgress ?: run.value } else Modifier)) { content() }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun Modifier.aberration(progress: () -> Float): Modifier {
    val shader = remember { AberrationShader() }
    return graphicsLayer {
        val t = progress()
        renderEffect = if (t <= 0f || t >= 1f || size.width <= 0f) null else shader.effect(t, size.width, size.height, density)
        clip = false
    }
}
