package com.jake.duolauncher.appearance

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * "Dune glass": the dune scene drawn as a lit, hazy, grainy landscape rather than flat bands, so the liquid glass has crisp crests,
 * a bright sun and soft gradients to bend.
 *
 * Seven dune layers, far to near. Each has an asymmetric profile (a gentle windward slope, a steeper lee face) and a crisp crest.
 * The face turned to the light is warm and bright, the lee face is cool and dark, and the contrast fades into a soft base tone a
 * little way below the crest, along a slanted line rather than a vertical seam. Distant layers melt into a haze colour (depth), each
 * crest catches a thin rim of light, each layer shades the one behind it where they meet, and the sand carries faint wind streaks.
 * A sun (or moon) with a bloom sits behind the ridges, partly hidden by the farthest one. A film of grain is added when it is drawn.
 *
 * It is pure arithmetic on an array of ARGB ints, so it is deterministic and unit-testable; [DuneGlassCache] turns it into a bitmap
 * once per size and keeps it.
 */
internal object DuneGlass {
    private class Palette(
        val sky: List<Pair<Float, Int>>, val orbColor: Int, val orbGlow: Int, val orbX: Float, val orbY: Float, val orbRadius: Float,
        val haze: Int, val lit: IntArray, val shade: IntArray, val rim: Int, val bloom: Float, val rimGain: Float, val mist: Float, val vignette: Float,
    )

    private val day = Palette(
        sky = listOf(0f to 0x244E73, .22f to 0x4F83A0, .38f to 0x9DBFC4, .50f to 0xF3D5B1, 1f to 0xF3D5B1),
        orbColor = 0xFFF1CF, orbGlow = 0xFFC98A, orbX = .72f, orbY = .355f, orbRadius = .065f, haze = 0xE8CFA6,
        lit = intArrayOf(0xF6E2B4, 0xF2D197, 0xEDBB7D, 0xE6A468, 0xDB8E58, 0xCC7A4C, 0xB96742),
        shade = intArrayOf(0xB59FAE, 0xA5879D, 0x946E88, 0x815778, 0x6C4568, 0x573858, 0x422B49),
        rim = 0xFFF6E0, bloom = .50f, rimGain = .46f, mist = .05f, vignette = .16f,
    )
    private val night = Palette(
        sky = listOf(0f to 0x060A16, .25f to 0x101A38, .40f to 0x25295A, .50f to 0x4B3F78, 1f to 0x4B3F78),
        orbColor = 0xCFE3F2, orbGlow = 0x7C8FE0, orbX = .72f, orbY = .37f, orbRadius = .052f, haze = 0x3A3566,
        lit = intArrayOf(0x7C8FB3, 0x7585AB, 0x6A79A0, 0x5C6A92, 0x4D5A82, 0x3F4A72, 0x333C60),
        shade = intArrayOf(0x1F2447, 0x1B2040, 0x161A38, 0x121530, 0x0E1128, 0x0B0D20, 0x080A18),
        rim = 0xC9DAF0, bloom = .55f, rimGain = .42f, mist = .07f, vignette = .30f,
    )

    private val baseY = floatArrayOf(.40f, .485f, .57f, .655f, .745f, .84f, .945f)
    private val amp = floatArrayOf(.050f, .060f, .072f, .086f, .100f, .115f, .130f)
    private val freq = floatArrayOf(1.15f, 1.35f, 1.0f, 1.25f, 0.95f, 1.2f, 0.85f)
    private val phase = floatArrayOf(.10f, .55f, .30f, .80f, .05f, .62f, .35f)
    private val lean = floatArrayOf(.38f, .62f, .35f, .60f, .40f, .64f, .36f)

    private fun smooth(x: Float): Float { val t = if (x < 0f) 0f else if (x > 1f) 1f else x; return t * t * (3f - 2f * t) }
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun r(c: Int) = ((c shr 16) and 255) / 255f
    private fun g(c: Int) = ((c shr 8) and 255) / 255f
    private fun b(c: Int) = (c and 255) / 255f

    /** The profile of one dune across the screen, -1..1: a gentle rise, then a steeper fall after the crest. */
    internal fun profile(x: Float, frequency: Float, phase: Float, lean: Float): Float {
        val u = (((x * frequency + phase) % 1f) + 1f) % 1f
        val p = if (u < lean) smooth(u / lean) else 1f - Math.pow(((u - lean) / (1f - lean)).toDouble(), 1.6).toFloat()
        return p * 2f - 1f
    }

    /** The crest of layer [i] as a fraction of the height, for a column at [x] (0..1). */
    internal fun crestFraction(i: Int, x: Float): Float {
        val prof = profile(x, freq[i], phase[i], lean[i])
        val prof2 = sin(x * 2f * PI.toFloat() * (0.6f + 0.3f * i) + phase[i] * 7f) * .25f
        val tilt = .045f * (if (i % 2 == 1) 1f else -1f) * (0.4f + i * .1f)
        return baseY[i] + amp[i] * (prof * .8f + prof2) + tilt * (x - .5f)
    }

    val layers get() = baseY.size

    /** A smooth noise field from a coarse grid of values, sampled with a smooth interpolation. */
    private class Field(private val cols: Int, private val rows: Int, seed: Long) {
        private val v = FloatArray((cols + 2) * (rows + 2))
        init { val rnd = java.util.Random(seed); for (i in v.indices) v[i] = rnd.nextFloat() }
        fun at(u: Float, w: Float): Float {              // u, w in 0..1
            val x = u * cols; val y = w * rows
            val xi = floor(x).toInt(); val yi = floor(y).toInt()
            val fx = smooth(x - xi); val fy = smooth(y - yi)
            val i = yi * (cols + 2) + xi
            val a = lerp(v[i], v[i + 1], fx); val c = lerp(v[i + cols + 2], v[i + cols + 3], fx)
            return lerp(a, c, fy)
        }
    }

    /** The scene as ARGB pixels, [width] by [height]. */
    fun render(width: Int, height: Int, dark: Boolean): IntArray {
        val w = width.coerceAtLeast(8); val h = height.coerceAtLeast(8)
        val p = if (dark) night else day
        val out = IntArray(w * h)
        val s = h / 2340f                                  // the pixel constants below were tuned at 2340 px tall
        val orbC = p.orbColor; val glow = p.orbGlow; val hazeC = p.haze
        val mistA = Field(3, 6, 11); val mistB = Field(6, 12, 12)
        val streakA = Field(w / 28 + 2, h / 6 + 2, 5); val streakB = Field(w / 9 + 2, h / 9 + 2, 6)

        // Per-layer, per-column geometry.
        val crestPx = Array(layers) { i -> FloatArray(w) { x -> crestFraction(i, x / (w - 1f)) * h } }
        val face = Array(layers) { i ->
            val c = crestPx[i]
            FloatArray(w) { x ->
                val a = c[(x - 1).coerceAtLeast(0)]; val bb = c[(x + 1).coerceAtMost(w - 1)]
                val slope = (bb - a) / (if (x == 0 || x == w - 1) 1f else 2f)
                tanh(-slope * 3.2f)
            }
        }
        // Lookup tables by integer pixels below (or from) a crest, so the inner loop has no exp().
        val tableLen = h + 2
        val fadeTab = Array(layers) { i -> FloatArray(tableLen) { d -> exp(-d / ((170f + 110f * i / (layers - 1f)) * s)) } }
        val rimTab = Array(layers) { i -> FloatArray(tableLen) { d -> val k = d / ((2f + 2.5f * i / (layers - 1f)) * maxOf(s, .5f)); exp(-k * k) } }
        val shadowTab = Array(layers) { i -> FloatArray(tableLen) { d -> exp(-d / ((14f + 22f * i / (layers - 1f)) * s)) } }

        val skyR = FloatArray(h); val skyG = FloatArray(h); val skyB = FloatArray(h)
        for (y in 0 until h) {
            val f = y / (h - 1f)
            var k = 0
            while (k < p.sky.size - 2 && f > p.sky[k + 1].first) k++
            val (f0, c0) = p.sky[k]; val (f1, c1) = p.sky[k + 1]
            val t = ((f - f0) / (f1 - f0)).coerceIn(0f, 1f)
            skyR[y] = lerp(r(c0), r(c1), t); skyG[y] = lerp(g(c0), g(c1), t); skyB[y] = lerp(b(c0), b(c1), t)
        }

        val rad = p.orbRadius * w
        val ox = p.orbX * w; val oy = p.orbY * h
        val glowR = r(glow); val glowG = g(glow); val glowB = b(glow)
        val orbR = r(orbC); val orbG = g(orbC); val orbB = b(orbC)
        val rimR = r(p.rim); val rimG = g(p.rim); val rimB = b(p.rim)
        val hazeR = r(hazeC); val hazeG = g(hazeC); val hazeB = b(hazeC)

        for (y in 0 until h) {
            val yf = y / (h - 1f)
            var rr: Float; var gg: Float; var bb: Float
            for (x in 0 until w) {
                val xf = x / (w - 1f)
                // Sky: gradient, mist, then the sun or moon and its bloom.
                val mist = ((mistA.at(xf, yf) * .65f + mistB.at(xf, yf) * .35f) - .5f) * p.mist
                rr = skyR[y] + mist; gg = skyG[y] + mist; bb = skyB[y] + mist * 1.05f
                val dx = x - ox; val dy = y - oy
                val d2 = dx * dx + dy * dy
                val bloomReach = rad * 3.2f * 2.6f
                if (d2 < bloomReach * bloomReach) {
                    val d = sqrt(d2)
                    val bl = exp(-(d / (rad * 3.2f)).let { it * it })
                    rr += bl * glowR * p.bloom; gg += bl * glowG * p.bloom; bb += bl * glowB * p.bloom
                    val disc = smooth((rad - d) / (rad * .12f)) * (if (dark) .95f else .9f)
                    rr = lerp(rr, orbR, disc); gg = lerp(gg, orbG, disc); bb = lerp(bb, orbB, disc)
                }
                // Dune layers, far to near.
                for (i in 0 until layers) {
                    val depth = i / (layers - 1f)
                    val crest = crestPx[i][x]
                    val below = y - crest
                    if (below < -3f * maxOf(s, .5f) * 40f) {
                        // Far above this crest: only its soft shadow on whatever is behind (not on the sky).
                        if (i > 0) {
                            val up = (crest - y).toInt().coerceIn(0, tableLen - 1)
                            val cs = shadowTab[i][up] * .30f * .9f
                            if (cs > .002f) { val k = 1f - cs; rr *= k; gg *= k; bb *= k }
                        }
                        continue
                    }
                    val edge = 2.2f * maxOf(s, .5f)
                    val inside = smooth(below / edge)
                    if (inside <= 0f) {
                        if (i > 0) {
                            val up = (crest - y).toInt().coerceIn(0, tableLen - 1)
                            val cs = shadowTab[i][up] * .30f * .9f
                            if (cs > .002f) { val k = 1f - cs; rr *= k; gg *= k; bb *= k }
                        }
                        continue
                    }
                    val belowPx = if (below > 0f) below else 0f
                    val slant = if (i % 2 == 1) .55f else -.55f
                    val col = (x - belowPx * slant).toInt().coerceIn(0, w - 1)
                    val fade = fadeTab[i][belowPx.toInt().coerceIn(0, tableLen - 1)]
                    val fc = face[i][col] * fade
                    val bodyBelow = (below / h / (amp[i] * 1.6f + .06f)).coerceIn(0f, 1f)
                    val t = smooth(fc * .5f + .5f)
                    val litC = p.lit[i]; val shC = p.shade[i]
                    val litK = lerp(1f, .82f, bodyBelow); val shK = lerp(1.15f, .8f, bodyBelow)
                    var cr = lerp(r(shC) * shK, r(litC) * litK, t)
                    var cg = lerp(g(shC) * shK, g(litC) * litK, t)
                    var cb = lerp(b(shC) * shK, b(litC) * litK, t)
                    // Wind streaks in the sand.
                    val st = ((streakA.at(xf, yf) * .6f + streakB.at(xf, yf) * .4f) - .5f) * .05f * (.4f + depth * .8f)
                    cr += st; cg += st; cb += st
                    // Haze: distant layers melt into it.
                    val hz = Math.pow((1f - depth).toDouble(), 1.6).toFloat() * .62f
                    cr = lerp(cr, hazeR, hz); cg = lerp(cg, hazeG, hz); cb = lerp(cb, hazeB, hz)
                    // A rim of light on the crest, strongest on the lit face.
                    val dist = abs(below).toInt().coerceIn(0, tableLen - 1)
                    val rimK = rimTab[i][dist] * (smooth(fc * .5f + .5f) * .85f + .15f) * p.rimGain * (.55f + .45f * depth) * inside
                    cr += rimR * rimK; cg += rimG * rimK; cb += rimB * rimK
                    rr = lerp(rr, cr, inside); gg = lerp(gg, cg, inside); bb = lerp(bb, cb, inside)
                }
                // Vignette, then a little dithering so the gradients do not band.
                val vx = xf - .5f; val vy = yf - .45f
                val v = sqrt(vx * vx + vy * vy * .55f)
                val vk = 1f - smooth((v - .30f) / .55f) * p.vignette
                val n = (((x * 73856093) xor (y * 19349663)) and 255) / 255f - .5f
                val dither = n * (1f / 255f)
                val R = ((rr * vk + dither).coerceIn(0f, 1f) * 255f + .5f).toInt()
                val G = ((gg * vk + dither).coerceIn(0f, 1f) * 255f + .5f).toInt()
                val B = ((bb * vk + dither).coerceIn(0f, 1f) * 255f + .5f).toInt()
                out[y * w + x] = (0xFF shl 24) or (R shl 16) or (G shl 8) or B
            }
        }
        return out
    }
}
