package com.jake.duolauncher.appearance

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/** Small helpers the dune scenes share: colour maths on 0..1 floats, smooth steps, ramps, and a deterministic grain. */
internal object SceneMath {
    fun smooth(x: Float): Float { val t = if (x < 0f) 0f else if (x > 1f) 1f else x; return t * t * (3f - 2f * t) }
    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    fun r(c: Int) = ((c shr 16) and 255) / 255f
    fun g(c: Int) = ((c shr 8) and 255) / 255f
    fun b(c: Int) = (c and 255) / 255f

    /** A colour ramp: [positions] ascending 0..1 with a colour for each; writes the colour at [t] into [out] (r, g, b). */
    fun ramp(t: Float, positions: FloatArray, colors: IntArray, out: FloatArray) {
        val tt = if (t < 0f) 0f else if (t > 1f) 1f else t
        var k = 0
        while (k < positions.size - 2 && tt > positions[k + 1]) k++
        val u = ((tt - positions[k]) / (positions[k + 1] - positions[k])).coerceIn(0f, 1f)
        out[0] = lerp(r(colors[k]), r(colors[k + 1]), u); out[1] = lerp(g(colors[k]), g(colors[k + 1]), u); out[2] = lerp(b(colors[k]), b(colors[k + 1]), u)
    }

    /** Roughly normal noise (mean 0, spread about 1) for a pixel, from a hash; the same pixel always gives the same value. */
    fun grain(x: Int, y: Int, seed: Int): Float {
        fun h(a: Int, b: Int, c: Int): Float {
            var n = a * 374761393 + b * 668265263 + c * 1274126177 + seed * 362437
            n = (n xor (n ushr 13)) * 1274126177
            n = n xor (n ushr 16)
            return (n and 0xFFFF) / 65535f
        }
        return (h(x, y, 1) + h(x, y, 2) + h(x, y, 3) - 1.5f) * 2f
    }

    private fun lattice(i: Int, j: Int, seed: Int): Float {
        var n = i * 374761393 + j * 668265263 + seed * 1274126177
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        return (n and 0xFFFF) / 65535f
    }

    /** Smooth value noise at [x], [y] (in lattice units), 0..1. */
    fun noise2(x: Float, y: Float, seed: Int): Float {
        val xi = floor(x).toInt(); val yi = floor(y).toInt()
        val fx = smooth(x - xi); val fy = smooth(y - yi)
        val a = lerp(lattice(xi, yi, seed), lattice(xi + 1, yi, seed), fx)
        val c = lerp(lattice(xi, yi + 1, seed), lattice(xi + 1, yi + 1, seed), fx)
        return lerp(a, c, fy)
    }

    /** A jagged ridge profile: [octaves] of value noise doubling in frequency, 0..1 along [t] (0..1). */
    fun fractal1(t: Float, seed: Int, octaves: Int = 8, persistence: Float = .55f): Float {
        var sum = 0f; var amp = 1f; var total = 0f
        for (o in 0 until octaves) {
            val k = 3 * (1 shl o)
            sum += amp * noise2(t * k, 0f, seed + o * 31); total += amp; amp *= persistence
        }
        return sum / total
    }

    /** How far down the very top of a night scene is pure black ([BLACK_TOP]) and how far below that it takes to reach the scene's own colours. */
    const val BLACK_TOP = .05f
    const val BLACK_FADE = .17f

    /** The multiplier for a row at [yf] (0 top .. 1 bottom): 0 across the top, so the camera cutout and the island's black pill vanish into
     * it, then a smooth rise to 1. */
    fun topBlackMask(yf: Float): Float = smooth((yf - BLACK_TOP) / BLACK_FADE)

    /** Fades the top of a night scene to the deepest black, in place. */
    fun applyTopBlack(pixels: IntArray, width: Int, height: Int) {
        val limit = ((BLACK_TOP + BLACK_FADE) * height).toInt().coerceAtMost(height)
        for (y in 0 until limit) {
            val m = topBlackMask(y / (height - 1f))
            val row = y * width
            for (x in 0 until width) {
                val c = pixels[row + x]
                val r = (((c shr 16) and 255) * m + .5f).toInt(); val g = (((c shr 8) and 255) * m + .5f).toInt(); val b = ((c and 255) * m + .5f).toInt()
                pixels[row + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    fun pack(rr: Float, gg: Float, bb: Float): Int {
        val R = (rr.coerceIn(0f, 1f) * 255f + .5f).toInt(); val G = (gg.coerceIn(0f, 1f) * 255f + .5f).toInt(); val B = (bb.coerceIn(0f, 1f) * 255f + .5f).toInt()
        return (0xFF shl 24) or (R shl 16) or (G shl 8) or B
    }
}

/**
 * "Dune flow": the dunes as long exposures of light. Each ridge is a bright ribbon (white core, then apricot and orange), falling away
 * into a dark void and then a deep blue pool before the next ridge, with the heavy, coarse film grain of a pushed photograph. By night
 * it is black, ember orange and electric blue; by day it is cream, apricot and cobalt.
 */
internal object DuneFlow {
    private class Palette(val positions: FloatArray, val colors: IntArray, val skyPositions: FloatArray, val skyColors: IntArray, val void: Int, val grain: Float, val dark: Boolean)
    private val night = Palette(
        floatArrayOf(0f, .05f, .12f, .22f, .36f, .55f, .78f, 1f),
        intArrayOf(0xFFFFFF, 0xFFE2B8, 0xFF8A3D, 0xD2430F, 0x4A1A18, 0x0A0A14, 0x0B2C86, 0x2E7BE0),
        floatArrayOf(0f, .5f, .8f, 1f), intArrayOf(0x06070E, 0x07103A, 0x0B35A8, 0x4FA6F0), 0x06070E, .085f, true)
    private val day = Palette(
        floatArrayOf(0f, .05f, .12f, .24f, .42f, .62f, .82f, 1f),
        intArrayOf(0x0E3A9C, 0x2D6FE0, 0xFFAE62, 0xFBD9AE, 0xF6EBDD, 0xD2E4F6, 0x9CC0EE, 0xF2A97E),
        floatArrayOf(0f, .5f, .8f, 1f), intArrayOf(0x0F3A9A, 0x5C98E6, 0xBFD9F2, 0xF4EAD9), 0xEDE7DA, .055f, false)

    private val base = floatArrayOf(.22f, .40f, .58f, .76f, .95f)
    private val amp = floatArrayOf(.05f, .07f, .08f, .09f, .09f)
    private val freq = floatArrayOf(.55f, .45f, .5f, .42f, .5f)
    private val phase = floatArrayOf(.3f, 1.7f, 3.0f, 4.4f, 5.6f)
    private val tilt = floatArrayOf(-.16f, -.22f, -.18f, -.25f, -.2f)
    val layers get() = base.size

    internal fun crestFraction(i: Int, x: Float): Float {
        val a = 2f * PI.toFloat() * freq[i]
        return base[i] + amp[i] * sin(x * a + phase[i]) + .35f * amp[i] * sin(x * a * 2.1f + phase[i] * 1.7f) + tilt[i] * (x - .5f)
    }

    fun render(width: Int, height: Int, dark: Boolean): IntArray {
        val w = width.coerceAtLeast(8); val h = height.coerceAtLeast(8)
        val p = if (dark) night else day
        val out = IntArray(w * h)
        val s = maxOf(h / 2340f, .5f)
        val grainScale = (w / 720f).coerceIn(.3f, 1f)
        val crest = Array(layers) { i -> FloatArray(w) { x -> crestFraction(i, x / (w - 1f)) } }
        val col = FloatArray(3); val under = FloatArray(3)
        val voidR = SceneMath.r(p.void); val voidG = SceneMath.g(p.void); val voidB = SceneMath.b(p.void)
        for (y in 0 until h) {
            val yf = y / (h - 1f)
            for (x in 0 until w) {
                // Which ridge is this pixel below? The nearest one above it (the last whose crest is at or above the pixel).
                var top = -1
                for (i in layers - 1 downTo 0) { if (y - crest[i][x] * h > -3f * s) { top = i; break } }
                var rr: Float; var gg: Float; var bb: Float
                if (top < 0) {
                    val c0 = crest[0][x]
                    val above = ((c0 - yf) / (c0 + 1e-3f)).coerceIn(0f, 1f)
                    SceneMath.ramp(1f - Math.pow(above.toDouble(), .8).toFloat(), p.skyPositions, p.skyColors, col)
                    rr = col[0]; gg = col[1]; bb = col[2]
                } else {
                    // Behind this ridge: the previous ridge's band, or the sky.
                    if (top > 0) bandColor(p, crest, top - 1, x, yf, under, voidR, voidG, voidB, col)
                    else {
                        val c0 = crest[0][x]
                        val above = ((c0 - yf) / (c0 + 1e-3f)).coerceIn(0f, 1f)
                        SceneMath.ramp(1f - Math.pow(above.toDouble(), .8).toFloat(), p.skyPositions, p.skyColors, under)
                    }
                    bandColor(p, crest, top, x, yf, col, voidR, voidG, voidB, col)
                    val inside = SceneMath.smooth((y - crest[top][x] * h) / (3f * s))
                    rr = SceneMath.lerp(if (top > 0) under[0] else under[0], col[0], inside)
                    gg = SceneMath.lerp(under[1], col[1], inside); bb = SceneMath.lerp(under[2], col[2], inside)
                }
                // Coarse film grain, heaviest in the mid-tones.
                val lum = (rr + gg + bb) / 3f
                val mid = .55f + .45f * (1f - Math.abs(lum - .5f) * 2f)
                val gr = SceneMath.grain(x / 2, y / 2, 7) * p.grain * mid * grainScale
                out[y * w + x] = SceneMath.pack(rr + gr, gg + gr, bb + gr)
            }
        }
        return out
    }

    private fun bandColor(p: Palette, crest: Array<FloatArray>, i: Int, x: Int, yf: Float, into: FloatArray, vr: Float, vg: Float, vb: Float, scratch: FloatArray) {
        val c = crest[i][x]
        val next = if (i + 1 < layers) crest[i + 1][x] else c + .35f
        val band = ((yf - c) / (next - c + 1e-4f)).coerceIn(0f, 1f)
        SceneMath.ramp(Math.pow(band.toDouble(), .9).toFloat(), p.positions, p.colors, into)
        val k = .55f + .45f * (i / (layers - 1f))
        into[0] = vr * (1 - k) + into[0] * k; into[1] = vg * (1 - k) + into[1] * k; into[2] = vb * (1 - k) + into[2] * k
    }
}

/**
 * "Dune bloom": the dunes as glossy, rounded pebbles in soft focus. Far ones sit high and small, near ones are large and blurred, each
 * with a gradient from warm to cool, a thin rim of light on its lower edge and a soft shadow, over a cobalt ground with a wide beam of
 * light and a sun (or moon) behind the far ridge.
 */
internal object DuneBloom {
    private class Pebble(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val rot: Float,
        val c1: Int, val c2: Int, val c3: Int, val rim: Int, val blur: Float, val shadow: Float)
    private class Palette(val top: Int, val bottom: Int, val pool: Int, val pebbles: List<Pebble>, val orb: Int, val orbX: Float, val orbY: Float,
        val orbRadius: Float, val orbCore: Float, val orbHalo: Float, val grain: Float)

    private val night = Palette(0x0A1030, 0x1B2C8C, 0x3A63D8, listOf(
        Pebble(.18f, .50f, .46f, .15f, .06f, 0x6B63D8, 0x4E6BE0, 0x3250C4, 0xFFB86B, 20f, 0f),
        Pebble(.56f, .61f, .56f, .19f, -.10f, 0xE0527F, 0x7F77E8, 0x3C73F0, 0xFFB86B, 2.5f, .55f),
        Pebble(.96f, .71f, .30f, .17f, .15f, 0x0F8F6B, 0x137A9C, 0x2D5FD0, 0x8FE8D2, 12f, .30f),
        Pebble(.08f, .92f, .46f, .18f, .10f, 0x0A6B3D, 0x1D7E88, 0x5A8CDC, 0x9ED6FF, 12f, .50f),
        Pebble(.86f, .99f, .40f, .15f, -.18f, 0x0B5C38, 0x1B6F86, 0x4A78D0, 0x8FD0FF, 22f, .50f)),
        0xBFD2FF, .72f, .34f, .07f, .7f, .16f, .024f)
    private val day = Palette(0x2556C9, 0x1848C0, 0x6A9BF0, listOf(
        Pebble(.18f, .50f, .46f, .15f, .06f, 0xB9B2F0, 0x8DA6F4, 0x5F8DEB, 0xFFD9B0, 20f, 0f),
        Pebble(.56f, .61f, .56f, .19f, -.10f, 0xFF7F96, 0xB794DA, 0x6E9BF4, 0xFFB45E, 2.5f, .55f),
        Pebble(.96f, .71f, .30f, .17f, .15f, 0x1FA070, 0x1D8AA4, 0x4A86E6, 0xBFF3E2, 12f, .30f),
        Pebble(.08f, .92f, .46f, .18f, .10f, 0x0A6A38, 0x2D8189, 0x70A0E8, 0xB6DCFF, 12f, .55f),
        Pebble(.86f, .99f, .40f, .15f, -.18f, 0x0E7A44, 0x2C8C95, 0x6F9FE6, 0xB6DCFF, 22f, .55f)),
        0xFFE3B8, .72f, .34f, .07f, .85f, .22f, .020f)

    fun render(width: Int, height: Int, dark: Boolean): IntArray {
        val w = width.coerceAtLeast(8); val h = height.coerceAtLeast(8)
        val p = if (dark) night else day
        val out = IntArray(w * h)
        val scale = w / 1080f
        val grainScale = (w / 720f).coerceIn(.3f, 1f)
        val topR = SceneMath.r(p.top); val topG = SceneMath.g(p.top); val topB = SceneMath.b(p.top)
        val botR = SceneMath.r(p.bottom); val botG = SceneMath.g(p.bottom); val botB = SceneMath.b(p.bottom)
        val poolR = SceneMath.r(p.pool); val poolG = SceneMath.g(p.pool); val poolB = SceneMath.b(p.pool)
        val orbR = SceneMath.r(p.orb); val orbG = SceneMath.g(p.orb); val orbB = SceneMath.b(p.orb)
        val ox = p.orbX * w; val oy = p.orbY * h
        val n = p.pebbles.size
        val cxA = FloatArray(n) { p.pebbles[it].cx * w }; val cyA = FloatArray(n) { p.pebbles[it].cy * h }
        val rxA = FloatArray(n) { p.pebbles[it].rx * w }; val ryA = FloatArray(n) { p.pebbles[it].ry * h }
        val cosA = FloatArray(n) { cos(p.pebbles[it].rot) }; val sinA = FloatArray(n) { sin(p.pebbles[it].rot) }
        val blurA = FloatArray(n) { p.pebbles[it].blur * scale }
        for (y in 0 until h) {
            val yf = y / h.toFloat()
            val ground = Math.pow(yf.toDouble(), 1.1).toFloat()
            val beamCentre = .62f * h
            for (x in 0 until w) {
                var rr = SceneMath.lerp(topR, botR, ground); var gg = SceneMath.lerp(topG, botG, ground); var bb = SceneMath.lerp(topB, botB, ground)
                val beam = exp(-(((y - (beamCentre + (x - w / 2f) * .12f)) / (.16f * h)).let { it * it })) * .75f
                rr = SceneMath.lerp(rr, poolR, beam); gg = SceneMath.lerp(gg, poolG, beam); bb = SceneMath.lerp(bb, poolB, beam)
                // Sun or moon, with a halo.
                val od = sqrt((x - ox) * (x - ox) + (y - oy) * (y - oy)) / w
                val core = exp(-(od / (p.orbRadius * 1.5f)).let { it * it }) * p.orbCore
                val halo = exp(-(od / (p.orbRadius * 4.2f)).let { it * it }) * p.orbHalo
                rr = SceneMath.lerp(rr, orbR, core) + orbR * halo; gg = SceneMath.lerp(gg, orbG, core) + orbG * halo; bb = SceneMath.lerp(bb, orbB, core) + orbB * halo
                for (k in 0 until n) {
                    val pb = p.pebbles[k]
                    val dx = x - cxA[k]; val dy = y - cyA[k]
                    val u = (dx * cosA[k] + dy * sinA[k]) / rxA[k]; val v = (-dx * sinA[k] + dy * cosA[k]) / ryA[k]
                    val f = sqrt(u * u + v * v)
                    val sd = (f - 1f) * minOf(rxA[k], ryA[k])
                    val bl = blurA[k]
                    val reach = (60f + bl * 3f) * scale * 5f
                    if (sd > reach) continue
                    val cov = 1f - SceneMath.smooth((sd + bl) / (2f * bl + 1e-3f))
                    if (pb.shadow > 0f) {
                        val shd = exp(-(if (sd > 0f) sd else 0f) / ((60f + bl * 3f) * scale)) * (1f - cov).coerceIn(0f, 1f) * pb.shadow
                        val dim = 1f - shd * .35f
                        rr *= dim; gg *= dim; bb *= dim
                    }
                    if (cov <= 0f) continue
                    val t = ((v + 1f) / 2f).coerceIn(0f, 1f)
                    var cr: Float; var cg: Float; var cb: Float
                    if (t < .5f) { val q = t / .5f
                        cr = SceneMath.lerp(SceneMath.r(pb.c1), SceneMath.r(pb.c2), q); cg = SceneMath.lerp(SceneMath.g(pb.c1), SceneMath.g(pb.c2), q); cb = SceneMath.lerp(SceneMath.b(pb.c1), SceneMath.b(pb.c2), q)
                    } else { val q = (t - .5f) / .5f
                        cr = SceneMath.lerp(SceneMath.r(pb.c2), SceneMath.r(pb.c3), q); cg = SceneMath.lerp(SceneMath.g(pb.c2), SceneMath.g(pb.c3), q); cb = SceneMath.lerp(SceneMath.b(pb.c2), SceneMath.b(pb.c3), q)
                    }
                    val face = ((.6f * u - .8f * v) / (f + 1e-4f)).coerceIn(0f, 1f)
                    val inner = if (sd < 0f) -sd else 0f
                    val rimW = (2f + bl * .5f) * scale
                    val rimK = exp(-((inner / rimW).let { it * it })) * Math.pow(face.toDouble(), 1.5).toFloat() * (if (bl < 8f * scale) 1f else .45f)
                    cr += SceneMath.r(pb.rim) * rimK * .85f; cg += SceneMath.g(pb.rim) * rimK * .85f; cb += SceneMath.b(pb.rim) * rimK * .85f
                    val shade = 1f - .22f * SceneMath.smooth((f - .55f) / .45f) * (1f - face)
                    cr *= shade; cg *= shade; cb *= shade
                    rr = SceneMath.lerp(rr, cr, cov); gg = SceneMath.lerp(gg, cg, cov); bb = SceneMath.lerp(bb, cb, cov)
                }
                val gr = SceneMath.grain(x, y, 3) * p.grain * grainScale
                out[y * w + x] = SceneMath.pack(rr + gr, gg + gr, bb + gr)
            }
        }
        return out
    }
}

/**
 * The shared renderer for the two photographic-leaning dune scenes, [DuneDeep] and [DuneRange]. A scene is: a sky (a gradient, a slow mist and
 * at night sparse stars), optionally some distant jagged ranges (each hazier than the one before), and dunes drawn far to near. Each dune
 * falls from a lit crest into shadow along a colour ramp, melts into haze with distance, carries fine wind ripples and a soft rim of
 * light along its crest, and spills a little light onto what is behind it. A vignette and film grain finish it.
 */
internal object DuneLandscape {
    class Palette(val sky: IntArray, val skyPos: FloatArray, val band: IntArray, val bandPos: FloatArray, val rim: Int, val glow: Int, val haze: Int,
        val rangeBase: Int, val rangeLit: Int, val glowGain: Float, val rimGain: Float, val grain: Float, val vignette: Float, val mist: Float, val stars: Boolean)

    /** [crest] gives dune i's crest as a fraction of the height at column fraction x; [ranges] says whether jagged ranges stand behind. */
    class Config(val dunes: Int, val crest: (Int, Float) -> Float, val day: Palette, val night: Palette, val ranges: Boolean, val ripple: Float,
        val rangeBase: FloatArray = floatArrayOf(), val rangeHeight: FloatArray = floatArrayOf(), val rangeHaze: FloatArray = floatArrayOf(), val rangeSeed: IntArray = intArrayOf())

    /** A star in the pixel's cell, if the cell has one: its brightness at this pixel (0 when it has none or the pixel is far from it). */
    private fun starAt(x: Int, y: Int, cell: Int, h: Int): Float {
        val cx = x / cell; val cy = y / cell
        val hsh = SceneMath.grain(cx, cy, 91)           // roughly normal; only a small tail of cells hold a star
        if (hsh < 1.55f) return 0f
        val px = cx * cell + ((SceneMath.grain(cx, cy, 92) * .5f + .5f).coerceIn(.1f, .9f)) * cell
        val py = cy * cell + ((SceneMath.grain(cx, cy, 93) * .5f + .5f).coerceIn(.1f, .9f)) * cell
        val dx = x - px; val dy = y - py
        val bright = (.35f + (hsh - 1.55f).coerceIn(0f, 1f) * .65f) * (1f - (y / (.55f * h)).coerceIn(0f, 1f) * .5f)
        return bright * exp(-(dx * dx + dy * dy) / 1.1f)
    }

    fun render(width: Int, height: Int, dark: Boolean, cfg: Config): IntArray {
        val w = width.coerceAtLeast(8); val h = height.coerceAtLeast(8)
        val p = if (dark) cfg.night else cfg.day
        val out = IntArray(w * h)
        val s = maxOf(h / 2340f, .5f)
        val grainScale = (w / 720f).coerceIn(.3f, 1f)
        val layers = cfg.dunes
        val crest = Array(layers) { i -> FloatArray(w) { x -> cfg.crest(i, x / (w - 1f)) * h } }
        val ranges = if (cfg.ranges) cfg.rangeBase.size else 0
        val ridge = Array(ranges) { r ->
            val n = FloatArray(w) { x -> SceneMath.fractal1(x / (w - 1f), cfg.rangeSeed[r]) }
            var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
            for (v in n) { if (v < lo) lo = v; if (v > hi) hi = v }
            FloatArray(w) { x -> (cfg.rangeBase[r] - cfg.rangeHeight[r] * .5f + cfg.rangeHeight[r] * (n[x] - lo) / (hi - lo + 1e-6f)) * h }
        }
        // Which way each range's rock faces, smoothed along the ridge so it does not stripe.
        val facing = Array(ranges) { r ->
            val c = ridge[r]
            val raw = FloatArray(w) { x -> (c[(x + 1).coerceAtMost(w - 1)] - c[(x - 1).coerceAtLeast(0)]) / 2f }
            val half = maxOf(2, (30 * w / 1080f).toInt())
            val smoothed = FloatArray(w)
            var run = 0f; var count = 0
            for (x in -half..half) { if (x in 0 until w) { run += raw[x]; count++ } }
            for (x in 0 until w) {
                smoothed[x] = run / count
                val add = x + half + 1; val drop = x - half
                if (add < w) { run += raw[add]; count++ }
                if (drop >= 0) { run -= raw[drop]; count-- }
            }
            FloatArray(w) { x -> tanh(-smoothed[x] / (w / 1080f) * 3.0).toFloat() }
        }
        val col = FloatArray(3); val sky = FloatArray(3)
        val rimR = SceneMath.r(p.rim); val rimG = SceneMath.g(p.rim); val rimB = SceneMath.b(p.rim)
        val glR = SceneMath.r(p.glow); val glG = SceneMath.g(p.glow); val glB = SceneMath.b(p.glow)
        val hzR = SceneMath.r(p.haze); val hzG = SceneMath.g(p.haze); val hzB = SceneMath.b(p.haze)
        val rbR = SceneMath.r(p.rangeBase); val rbG = SceneMath.g(p.rangeBase); val rbB = SceneMath.b(p.rangeBase)
        val rlR = SceneMath.r(p.rangeLit); val rlG = SceneMath.g(p.rangeLit); val rlB = SceneMath.b(p.rangeLit)
        val starCell = maxOf(6, (22 * s).toInt())
        for (y in 0 until h) {
            val yf = y / (h - 1f)
            SceneMath.ramp(yf, p.skyPos, p.sky, sky)
            for (x in 0 until w) {
                val xf = x / (w - 1f)
                val mist = (SceneMath.noise2(xf * 3f, yf * 5f, 5) - .5f) * p.mist
                var rr = sky[0] + mist; var gg = sky[1] + mist; var bb = sky[2] + mist
                if (p.stars && y < h * .5f) { val st = starAt(x, y, starCell, h) * .9f; rr += st * .95f; gg += st * .97f; bb += st }
                for (r in 0 until ranges) {
                    val d = y - ridge[r][x]
                    if (d < -2f) continue
                    val inside = SceneMath.smooth(d / (1.6f * s))
                    val t = SceneMath.smooth(facing[r][x] * .5f + .5f) * exp(-d / (h * .035f))
                    var cr = SceneMath.lerp(rbR, rlR, t); var cg = SceneMath.lerp(rbG, rlG, t); var cb = SceneMath.lerp(rbB, rlB, t)
                    val mott = (SceneMath.noise2(x / (14f * s), y / (14f * s), 40 + r) - .5f) * (if (dark) .30f else .16f)
                    cr *= 1f + mott; cg *= 1f + mott; cb *= 1f + mott
                    val hz = cfg.rangeHaze[r] * (1f - d / (h * .09f) * .3f).coerceIn(.6f, 1f)
                    cr = SceneMath.lerp(cr, hzR, hz); cg = SceneMath.lerp(cg, hzG, hz); cb = SceneMath.lerp(cb, hzB, hz)
                    rr = SceneMath.lerp(rr, cr, inside); gg = SceneMath.lerp(gg, cg, inside); bb = SceneMath.lerp(bb, cb, inside)
                }
                for (i in 0 until layers) {
                    val depth = if (layers > 1) i / (layers - 1f) else 1f
                    val d = y - crest[i][x]
                    val inside = SceneMath.smooth(d / (3f * s))
                    if (d < 0f) {
                        val g = exp(-(-d) / ((46f + 60f * (1f - depth)) * s)) * (1f - inside) * p.glowGain
                        if (g > .003f) { rr += glR * g; gg += glG * g; bb += glB * g }
                        continue
                    }
                    val nextCrest = if (i + 1 < layers) crest[i + 1][x] else crest[i][x] + h * .28f
                    val tband = (d / (nextCrest - crest[i][x] + 1f)).coerceIn(0f, 1f)
                    SceneMath.ramp(Math.pow(tband.toDouble(), .85).toFloat(), p.bandPos, p.band, col)
                    val hz = Math.pow((1f - depth).toDouble(), 1.5).toFloat() * .38f
                    var cr = SceneMath.lerp(col[0], hzR, hz); var cg = SceneMath.lerp(col[1], hzG, hz); var cb = SceneMath.lerp(col[2], hzB, hz)
                    // Fine wind ripples that follow the slope, clearer on the near dunes and near the crest.
                    val warp = sin(xf * 2f * PI.toFloat() * 3f + i) * 5f + sin(xf * 2f * PI.toFloat() * 9f + i * 2f) * 1.6f
                    val rp = sin(d * (.95f - .25f * depth) / s + warp + x * (1080f / w) * .045f * (if (i % 2 == 1) 1f else -1f))
                    val lit = (1f - d / (h * .10f)).coerceIn(.15f, 1f)
                    val ripple = rp * (.012f + .020f * depth) * lit * inside * cfg.ripple
                    cr += ripple; cg += ripple; cb += ripple
                    rr = SceneMath.lerp(rr, cr, inside); gg = SceneMath.lerp(gg, cg, inside); bb = SceneMath.lerp(bb, cb, inside)
                    val k = exp(-((Math.abs(d) / ((2.2f + 1.6f * depth) * s)).let { it * it })) * (.5f + .5f * depth) * p.rimGain
                    rr += rimR * k; gg += rimG * k; bb += rimB * k
                }
                val vx = xf - .5f; val vy = (yf - .5f)
                val v = sqrt(vx * vx + vy * vy * .6f)
                val vk = 1f - SceneMath.smooth((v - .35f) / .5f) * p.vignette
                val lum = (rr + gg + bb) / 3f
                val mid = .55f + .45f * (1f - Math.abs(lum - .5f) * 2f)
                val gr = SceneMath.grain(x / 2, y / 2, 11) * p.grain * mid * grainScale
                out[y * w + x] = SceneMath.pack(rr * vk + gr, gg * vk + gr, bb * vk + gr)
            }
        }
        return out
    }
}

/**
 * "Deep dunes": the original Uno dunes (the same three sweeping dunes and the same sand palette) given the depth of the glass aesthetic:
 * each ridge glows where the light catches it and spills a little light onto what is behind it, the sand falls from a bright crest into a
 * warm shadow, the far dune sits in haze, and a film of grain covers it all. The geometry is the original's, to the pixel.
 */
internal object DuneDeep {
    private val start = floatArrayOf(.57f, .72f, .85f)
    private val swing = floatArrayOf(.17f, .12f, .19f)
    val layers get() = start.size

    /** The crest of dune [i] at column fraction [x], as a fraction of the height: the original's cubic curve. */
    internal fun crestFraction(i: Int, x: Float): Float {
        // Invert x(t) = 3(1-t)^2 t .3 + 3(1-t) t^2 .6 + t^3 by bisection (it rises steadily from 0 to 1), then read y(t).
        var lo = 0f; var hi = 1f
        repeat(24) {
            val t = (lo + hi) / 2f; val u = 1f - t
            val xt = 3f * u * u * t * .3f + 3f * u * t * t * .6f + t * t * t
            if (xt < x) lo = t else hi = t
        }
        val t = (lo + hi) / 2f; val u = 1f - t
        val y = start[i]; val c = swing[i]
        return u * u * u * y + 3f * u * u * t * (y - c) + 3f * u * t * t * (y + c) + t * t * t * (y - c * .35f)
    }

    private val day = DuneLandscape.Palette(
        sky = intArrayOf(0x365E78, 0x7FA2B0, 0xCFD7CB, 0xF1DDB6, 0xF1DDB6), skyPos = floatArrayOf(0f, .40f, .58f, .70f, 1f),
        band = intArrayOf(0xFFF4D8, 0xF1D7A4, 0xD8B27C, 0xB38C5E, 0x8A6A48, 0x6B5238), bandPos = floatArrayOf(0f, .08f, .25f, .55f, .85f, 1f),
        rim = 0xFFF7E2, glow = 0xFFD9A0, haze = 0xDCCDAE, rangeBase = 0, rangeLit = 0,
        glowGain = .26f, rimGain = .38f, grain = .06f, vignette = .07f, mist = .05f, stars = false)
    private val night = DuneLandscape.Palette(
        sky = intArrayOf(0x0C1A22, 0x1D3440, 0x3A4A52, 0x463F35), skyPos = floatArrayOf(0f, .45f, .62f, 1f),
        band = intArrayOf(0xBFD3DC, 0x8497A0, 0x56625F, 0x2E302B, 0x15130F, 0x0B0A08), bandPos = floatArrayOf(0f, .07f, .2f, .45f, .75f, 1f),
        rim = 0xD2E6F2, glow = 0x8DB6D2, haze = 0x2B4350, rangeBase = 0, rangeLit = 0,
        glowGain = .22f, rimGain = .42f, grain = .075f, vignette = .28f, mist = .06f, stars = false)
    private val config = DuneLandscape.Config(3, ::crestFraction, day, night, ranges = false, ripple = .6f)

    fun render(width: Int, height: Int, dark: Boolean): IntArray = DuneLandscape.render(width, height, dark, config)
}

/**
 * "Dune range": closer to a photograph of a desert at dusk. A few big, soft dune slopes in front (not the original's three curves), jagged
 * ranges standing behind them and fading into haze, a sky from deep navy through a rose horizon, sparse stars at night, fine wind ripples on
 * the sand, and a light film of grain. Calmer than the other scenes: the glass has gradients and a skyline to bend, not neon.
 */
internal object DuneRange {
    val layers get() = 3

    /** The crest of dune [i] (far, middle, near) at column fraction [x]: soft sums of sines, so the slopes are broad and smooth. */
    internal fun crestFraction(i: Int, x: Float): Float {
        val tau = 2f * PI.toFloat()
        return when (i) {
            0 -> .585f + .010f * sin(x * tau * 1.3f + .8f) + .006f * sin(x * tau * 3.1f + 2f) - .02f * (x - .5f)
            1 -> .625f + .020f * sin(x * tau * .9f + 2.4f) + .008f * sin(x * tau * 2.3f + .5f) + .035f * (x - .5f)
            else -> .700f + .055f * cos(x * PI.toFloat() * 1.15f + .25f) + .012f * sin(x * tau * 2.2f + 1f) - .05f * (x - .5f)
        }
    }

    private val day = DuneLandscape.Palette(
        sky = intArrayOf(0x365E78, 0x7FA2B0, 0xCFD7CB, 0xF1DDB6, 0xF1DDB6), skyPos = floatArrayOf(0f, .38f, .50f, .56f, 1f),
        band = intArrayOf(0xFFF4D8, 0xF1D7A4, 0xD8B27C, 0xB38C5E, 0x8A6A48, 0x6B5238), bandPos = floatArrayOf(0f, .08f, .25f, .55f, .85f, 1f),
        rim = 0xFFF7E2, glow = 0xFFD9A0, haze = 0xDCCDAE, rangeBase = 0x6F8290, rangeLit = 0x9DB0BA,
        glowGain = .10f, rimGain = .10f, grain = .035f, vignette = .07f, mist = .05f, stars = false)
    private val night = DuneLandscape.Palette(
        sky = intArrayOf(0x07142A, 0x0E2140, 0x2A3354, 0x6A5266, 0x84606E, 0x463F35), skyPos = floatArrayOf(0f, .30f, .44f, .505f, .56f, 1f),
        band = intArrayOf(0x8F8C9E, 0x716E80, 0x57546A, 0x3E3B4E, 0x2A2837, 0x201E2B), bandPos = floatArrayOf(0f, .08f, .25f, .55f, .85f, 1f),
        rim = 0xC7D3EA, glow = 0x9A86A8, haze = 0x4C4862, rangeBase = 0x1B1C26, rangeLit = 0x34343F,
        glowGain = .05f, rimGain = .09f, grain = .04f, vignette = .28f, mist = .06f, stars = true)
    private val config = DuneLandscape.Config(3, ::crestFraction, day, night, ranges = true, ripple = 1f,
        rangeBase = floatArrayOf(.440f, .480f, .515f), rangeHeight = floatArrayOf(.085f, .095f, .090f),
        rangeHaze = floatArrayOf(.80f, .60f, .42f), rangeSeed = intArrayOf(21, 22, 23))

    fun render(width: Int, height: Int, dark: Boolean): IntArray = DuneLandscape.render(width, height, dark, config)
}
