package com.jake.duolauncher.appearance

import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.repeatOnLifecycle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.effect
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Hyper glass cards (Appearance › Glass › Hyper Glass Cards, off unless chosen): the panels that hold text (the feed, the App Library, menus,
 * the search results) as a nearly clear pane with film grain, in place of the frosted tint. What makes it:
 *
 *  - the wallpaper behind, well blurred, with almost no fill over it ([FILL] of white, where regular glass paints 44 %);
 *  - a fixed grain over the whole pane, in screen pixels, so it is the same size on every card: two tiles of noise of different sizes, one
 *    turned, so the pattern does not come round again within a screen;
 *  - chromatic aberration, as a lens has: everything on the card (its words, its icons, the grain, the picture behind) has its red and
 *    blue read a little to either side of its green, by nothing at the card's middle and by [SPLIT_DP] at its edge, so a word near the
 *    edge has a fringe of colour and one in the middle is clean. The border is drawn over that, plain, so there is no coloured rim;
 *  - a slow drift of the picture behind ([DRIFT] of the card, round a loop every [PERIOD_MS]), stepped a few times a second, and only
 *    while a card is on screen;
 *  - a kick as Home changes page: the colours part further while the pages move ([SPLIT_KICKED_DP]), and come back as they land
 *    ([KICK_MS]).
 *
 * The numbers and the clock's sums are pure, so they are unit-tested.
 */
internal object HyperGlassCards {
    /** Whether the cards are drawn this way now: the setting, with glass on, on Android 13 or later (the shader needs it). */
    var on by mutableStateOf(false)

    const val FILL = .08f
    const val BORDER = .12f
    /** The blur of the live backdrop under a menu or the search results, in dp. 30 px (11 dp) left the icons behind a menu as plain
     *  shapes that its words had to be read across, so it is a little more. */
    const val BLUR_DP = 18f
    /** How far red and blue sit from green at the card's edge, at rest and at the height of a kick, in dp. Nothing at the middle. It was
     *  .0008 of the card's width, read from the blurred picture alone: under a pixel, of something with no edges, so it was never seen. */
    const val SPLIT_DP = 1.1f
    const val SPLIT_KICKED_DP = 4f
    /** A little of the split is sideways everywhere, so the middle of a wide card is not left wholly without it. */
    const val SPLIT_FLOOR = .22f
    const val DRIFT = .003f
    const val PERIOD_MS = 20_000L
    /** How often the drift is stepped. It moves a few pixels over seconds, so more often would only cost frames. */
    const val STEP_MS = 66L
    const val KICK_MS = 420
    const val KICK_IN_MS = 90
    /** How strongly the grain is laid over the pane. */
    const val GRAIN = .4f
    /** How much of a panel's own tint is kept under the grain, so its ink still reads on a wallpaper that is the wrong way round for it. */
    const val SHADE_SHARE = .35f
    /** The same for a card over Home's icons (a menu), which has more behind it to be read across. */
    const val LIVE_SHADE_SHARE = .6f

    /** Where the drift is round its loop, in radians: there is no seam where the loop closes. */
    var phase by mutableFloatStateOf(0f)
        private set
    /** The kick, 1 while Home's pages move and falling to 0 once they land. Read as a card is drawn, so it recomposes nothing. */
    val kick = Animatable(0f)
    /** How many cards are on screen. The clock only runs while there is one. */
    var showing by mutableIntStateOf(0)

    fun phaseAt(ms: Long): Float = ((ms % PERIOD_MS + PERIOD_MS) % PERIOD_MS).toFloat() / PERIOD_MS * (2f * Math.PI.toFloat())
    fun splitDp(kick: Float): Float = SPLIT_DP + (SPLIT_KICKED_DP - SPLIT_DP) * kick.coerceIn(0f, 1f)

    internal fun step(ms: Long) { phase = phaseAt(ms) }

    /** Home's pages are [moving] or have come to rest: the colours part as a swipe begins and come back once it lands. */
    suspend fun kicked(moving: Boolean) {
        if (!on && kick.value == 0f) return
        if (moving) kick.animateTo(1f, tween(KICK_IN_MS, easing = LinearEasing)) else kick.animateTo(0f, tween(KICK_MS, easing = LinearEasing))
    }

    /** The sides of the two grain tiles, in pixels: primes, so laid over one another they line up again only every [GRAIN_A] x [GRAIN_B]
     *  pixels, far more than a screen. One tile of 128 repeated showed its pattern across a card. */
    const val GRAIN_A = 181
    const val GRAIN_B = 149
    /** The second tile is turned, so its rows do not run along the first's. */
    const val GRAIN_TURN = 31f

    private fun grainTile(side: Int, seed: Long, turn: Float): ShaderBrush {
        val random = java.util.Random(seed)
        val pixels = IntArray(side * side) { val v = (128 + random.nextGaussian() * 46).toInt().coerceIn(0, 255); (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        val tile = Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
        val shader = android.graphics.BitmapShader(tile, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
        if (turn != 0f) shader.setLocalMatrix(android.graphics.Matrix().apply { setRotate(turn) })
        return ShaderBrush(shader)
    }

    /** The grain: two tiles of grey noise of different sizes, one turned, each laid over at [GRAIN] / sqrt 2 so together they are as strong
     *  as one was. The same every time (fixed seeds), so a screenshot test sees the same picture twice. */
    val grain: List<ShaderBrush> by lazy { listOf(grainTile(GRAIN_A, 7, 0f), grainTile(GRAIN_B, 11, GRAIN_TURN)) }
}

/** The picture behind a card, drifted a little. */
private const val CARD_AGSL = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float phase;
uniform float drift;

half4 main(float2 coord) {
    // The picture may be bigger than the card by a margin all round (the room a blur needs): offset is where the card starts in it.
    float2 uv = (coord + offset) / size;
    uv += float2(sin(uv.y * 2.0 + phase), cos(uv.x * 2.0 + phase)) * drift;
    float2 lo = float2(0.5);
    float2 hi = size - offset * 2.0 - float2(0.5);
    return content.eval(clamp(uv * size - offset, lo, hi));
}
"""

/**
 * The aberration, over the whole card: red is read from a little further out from the card's middle than green and blue from a little
 * further in, by an amount that grows from the middle to the edge, as through a simple lens. Where a reading falls off the card (past its
 * edge, or outside a rounded corner) the middle one stands in, so the card's own outline gains no colour.
 */
private const val SPLIT_AGSL = """
uniform shader content;
uniform float2 size;
uniform float split;
uniform float side;

half4 main(float2 coord) {
    float2 q = (coord - size * 0.5) / (max(size.x, size.y) * 0.5);
    float2 apart = q * split + float2(split * side, 0.0);
    float2 lo = float2(0.5);
    float2 hi = size - float2(0.5);
    half4 g = content.eval(coord);
    half4 r = content.eval(clamp(coord + apart, lo, hi));
    half4 b = content.eval(clamp(coord - apart, lo, hi));
    half red = r.r + (1.0 - r.a) * g.r;
    half blue = b.b + (1.0 - b.a) * g.b;
    return half4(min(red, g.a), g.g, min(blue, g.a), g.a);
}
"""

/** The aberration as a layer's effect, made again only when its amount changes (a render effect keeps the numbers it was made with). */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class SplitEffect {
    private val shader = android.graphics.RuntimeShader(SPLIT_AGSL)
    private var made: androidx.compose.ui.graphics.RenderEffect? = null
    private var w = 0f; private var h = 0f; private var amount = -1f

    fun of(width: Float, height: Float, splitPx: Float): androidx.compose.ui.graphics.RenderEffect? {
        if (width <= 0f || height <= 0f) return null
        made?.let { if (width == w && height == h && splitPx == amount) return it }
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("split", splitPx)
        shader.setFloatUniform("side", HyperGlassCards.SPLIT_FLOOR)
        w = width; h = height; amount = splitPx
        return android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect().also { made = it }
    }
}

/** Everything drawn after this in the chain, and the card's own content, through the aberration. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun Modifier.hyperSplit(): Modifier {
    val effect = remember { SplitEffect() }
    return graphicsLayer {
        renderEffect = effect.of(size.width, size.height, HyperGlassCards.splitDp(HyperGlassCards.kick.value).dp.toPx())
    }
}

/** The card's fine border, plain white, drawn over the content and outside the aberration. */
private fun Modifier.hyperBorder(shape: Shape): Modifier = drawWithContent {
    drawContent()
    drawOutline(shape.createOutline(size, layoutDirection, this), Color.White.copy(alpha = HyperGlassCards.BORDER + .1f * HyperGlassCards.kick.value),
        style = Stroke(1.dp.toPx().coerceAtLeast(1f)))
}

/** What is drawn over the picture behind, the same on every card: the fill and the grain. */
private fun DrawScope.hyperCardFace(outline: Outline, clipped: Boolean) {
    fun face() {
        drawRect(Color.White.copy(alpha = HyperGlassCards.FILL))
        HyperGlassCards.grain.forEach { drawRect(it, alpha = HyperGlassCards.GRAIN * .7071f, blendMode = BlendMode.Overlay) }
    }
    if (clipped) face() else clipPath(Path().apply { addOutline(outline) }) { face() }
}

/** Counts this card as on screen while [shown] is last told so, and not once it has left the composition. */
private class CardPresence {
    private var shown = false
    fun set(value: Boolean) { if (value != shown) { shown = value; HyperGlassCards.showing += if (value) 1 else -1 } }
}

@Composable
private fun rememberCardPresence(): CardPresence {
    val presence = remember { CardPresence() }
    DisposableEffect(presence) { onDispose { presence.set(false) } }
    return presence
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class FrostedCardShader {
    private val shader = android.graphics.RuntimeShader(CARD_AGSL)
    private val paint = android.graphics.Paint()
    private val matrix = android.graphics.Matrix()
    private var picture: android.graphics.BitmapShader? = null
    private var pictureOf: ImageBitmap? = null

    fun draw(scope: DrawScope, image: ImageBitmap, origin: Offset, rootWidth: Int, rootHeight: Int, filter: android.graphics.ColorFilter?, path: Path) {
        if (pictureOf !== image) {
            picture = android.graphics.BitmapShader(image.asAndroidBitmap(), android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
                .apply { filterMode = android.graphics.BitmapShader.FILTER_MODE_LINEAR }
            pictureOf = image
        }
        val source = picture ?: return
        // The picture is the whole screen's wallpaper: placed at the screen's own place, so it lies where the real wallpaper does under the card.
        matrix.setScale(rootWidth.toFloat() / image.width, rootHeight.toFloat() / image.height)
        matrix.postTranslate(-origin.x, -origin.y)
        source.setLocalMatrix(matrix)
        shader.setInputShader("content", source)
        shader.setFloatUniform("size", scope.size.width, scope.size.height)
        shader.setFloatUniform("offset", 0f, 0f)
        shader.setFloatUniform("phase", HyperGlassCards.phase)
        shader.setFloatUniform("drift", HyperGlassCards.DRIFT)
        paint.shader = shader
        paint.colorFilter = filter
        with(scope) { clipPath(path) { drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) } } }
    }
}

/**
 * A hyper glass card over the wallpaper alone (the feed, the App Library): the wallpaper blurred once ([image], see [FrostedWallpaper]),
 * so the card costs one draw while its page moves. [shade] is the little of the panel's own tint that is kept, to hold its ink up.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun Modifier.hyperFrostedCard(shape: Shape, image: ImageBitmap, shade: Color, elevated: Boolean): Modifier {
    val root = LocalWindowInfo.current.containerSize
    var origin by remember { mutableStateOf(Offset.Zero) }
    val presence = rememberCardPresence()
    val shader = remember { FrostedCardShader() }
    val tone = rememberGlassTone()
    val filter = remember(tone) { com.kyant.backdrop.effects.colorControlsColorFilter(tone.brightness, tone.contrast, 1.45f) }
    val dark = DuoAppearanceRuntime.dark
    val path = remember { Path() }
    return this
        .then(if (!elevated) Modifier else Modifier.shadow(16.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = if (dark) .26f else .10f),
            spotColor = Color.Black.copy(alpha = if (dark) .34f else .16f)))
        .hyperBorder(shape)
        .hyperSplit()
        .onGloballyPositioned {
            origin = it.positionInRoot()
            presence.set(origin.x < root.width && origin.x + it.size.width > 0f && origin.y < root.height && origin.y + it.size.height > 0f)
        }
        .drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            path.rewind(); path.addOutline(outline)
            shader.draw(this, image, origin, root.width, root.height, filter, path)
            clipPath(path) { drawRect(shade) }
            hyperCardFace(outline, clipped = false)
        }
}

/**
 * A hyper glass card over whatever is behind it (a menu over Home's icons, the search results): the live [backdrop], blurred, through the
 * same drift and split, with the same face.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun Modifier.hyperLiveCard(backdrop: Backdrop, shape: Shape, shade: Color, depth: Boolean, exportedBackdrop: LayerBackdrop?): Modifier {
    val tone = rememberGlassTone()
    val presence = rememberCardPresence()
    LaunchedEffect(presence) { presence.set(true) }
    val dark = DuoAppearanceRuntime.dark
    // The shadow is drawn here, outside the aberration's layer, which holds only what is inside the card.
    return this
        .then(if (!depth) Modifier else Modifier.shadow(16.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = if (dark) .26f else .10f),
            spotColor = Color.Black.copy(alpha = if (dark) .34f else .16f)))
        .hyperBorder(shape)
        .hyperSplit()
        .drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            glassColor(tone)
            blur(HyperGlassCards.BLUR_DP.dp.toPx())
            val s = renderScale
            val shader = obtainRuntimeShader("HyperGlassCard", CARD_AGSL)
            shader.setFloatUniform("size", size.width * s, size.height * s)
            // The blur before this one has asked for a margin round the card (see the library's lens, which does the same).
            shader.setFloatUniform("offset", -padding * s, -padding * s)
            shader.setFloatUniform("phase", HyperGlassCards.phase)
            shader.setFloatUniform("drift", HyperGlassCards.DRIFT)
            effect(android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content"))
        },
        highlight = { null },
        shadow = { null },
        exportedBackdrop = exportedBackdrop,
        onDrawSurface = {
            drawRect(shade)
            hyperCardFace(shape.createOutline(size, layoutDirection, this), clipped = true)
        },
        renderScale = GlassKind.REGULAR.renderScale,
    )
}

/**
 * The drift's clock, for the screen that shows the cards: stepped a few times a second while the setting is on, a card is on screen, the
 * screen is in front and Android's animations are on. Otherwise nothing runs and the cards are still.
 */
@Composable
internal fun HyperGlassCardClock() {
    val context = LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            val animations = android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
            if (!animations) return@repeatOnLifecycle
            snapshotFlow { HyperGlassCards.on && HyperGlassCards.showing > 0 }.collectLatest { run ->
                while (run) { HyperGlassCards.step(android.os.SystemClock.uptimeMillis()); delay(HyperGlassCards.STEP_MS) }
            }
        }
    }
}
