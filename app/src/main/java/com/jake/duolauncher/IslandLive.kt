package com.jake.duolauncher

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Things that can be live on the collapsed island at once. Privacy indicators come first and are glyph-only; the rest are in
 * the order of how much the user needs to see them. A flash event or a ringing timer is not in here: those take the whole pill.
 */
internal enum class LiveKind(val privacy: Boolean = false) { CAMERA(true), MIC(true), RECORDING(true), CALL, TIMER, STOPWATCH, UPDATE, MEDIA }

/** What a collapsed-pill slot shows for a kind: its small glyph (an icon or artwork), or its detail (the text or bars). */
internal enum class SlotShow { GLYPH, DETAIL }

internal data class SlotPlan(val leading: Pair<LiveKind, SlotShow>?, val trailing: Pair<LiveKind, SlotShow>?)

/** The two-slot layout, as iOS does it: with one live activity, its glyph sits on one side of the camera and its detail on
 * the other; with two, each side carries one (the left one as a glyph, the right one in full).
 */
internal object IslandLive {
    fun active(camera: Boolean, mic: Boolean, call: Boolean, timer: Boolean, stopwatch: Boolean, media: Boolean,
        update: Boolean = false, recording: Boolean = false): List<LiveKind> =
        buildList {
            if (camera) add(LiveKind.CAMERA)
            if (mic) add(LiveKind.MIC)
            if (recording) add(LiveKind.RECORDING)
            if (call) add(LiveKind.CALL)
            if (timer) add(LiveKind.TIMER)
            if (stopwatch) add(LiveKind.STOPWATCH)
            if (update) add(LiveKind.UPDATE)
            if (media) add(LiveKind.MEDIA)
        }

    /** The list turned [shift] places (a flick across the island swaps which activity is in front). */
    fun rotated(kinds: List<LiveKind>, shift: Int): List<LiveKind> {
        if (kinds.size < 2) return kinds
        val by = ((shift % kinds.size) + kinds.size) % kinds.size
        return kinds.drop(by) + kinds.take(by)
    }

    fun plan(kinds: List<LiveKind>): SlotPlan = when {
        kinds.isEmpty() -> SlotPlan(null, null)
        kinds.size == 1 -> SlotPlan(kinds[0] to SlotShow.GLYPH, kinds[0] to SlotShow.DETAIL)
        else -> SlotPlan(kinds[1] to SlotShow.GLYPH,
            kinds[0] to if (kinds[0].privacy) SlotShow.GLYPH else SlotShow.DETAIL)
    }
}

/** Pop-out bubbles: as on newer dynamic islands, live activities beyond the two the pill can carry come out as small round
 * bubbles beside it. Tapping one brings its activity to the front of the pill. Pure, so the rules are unit-tested.
 */
internal object IslandBubbles {
    const val MAX = 2
    const val SIZE_DP = 30f
    const val GAP_DP = 6f

    /** The activities shown as bubbles: those after the pill's two, at most [MAX]. */
    fun extras(kinds: List<LiveKind>): List<LiveKind> = kinds.drop(2).take(MAX)

    /** How far to turn the order so the tapped bubble's activity is in front of the pill. */
    fun shiftFor(kinds: List<LiveKind>, bubbleIndex: Int): Int =
        if (bubbleIndex in extras(kinds).indices) 2 + bubbleIndex else 0

    /** How much room to the right of the pill the bubbles need, in dp (0 with none). */
    fun reserveDp(count: Int): Float = if (count <= 0) 0f else GAP_DP + count * (SIZE_DP + GAP_DP)
}

/** The colour the island borrows from album artwork: the most common vivid hue in a small copy of the picture, lifted so it
 * stays readable on black. Works on raw ARGB pixels so it can be tested without Android.
 */
internal object ArtTint {
    private const val BINS = 12
    private const val MIN_WEIGHT = 0.4f

    /** The tint as ARGB, or null when the picture has no real colour (grey, black or white). */
    fun fromPixels(pixels: IntArray): Int? {
        val weight = FloatArray(BINS); val r = FloatArray(BINS); val g = FloatArray(BINS); val b = FloatArray(BINS)
        for (p in pixels) {
            val pr = (p shr 16 and 0xFF) / 255f; val pg = (p shr 8 and 0xFF) / 255f; val pb = (p and 0xFF) / 255f
            val hi = max(pr, max(pg, pb)); val lo = min(pr, min(pg, pb))
            val value = hi; val sat = if (hi == 0f) 0f else (hi - lo) / hi
            val w = sat * sat * value            // vivid and bright pixels count most; greys and darks count nothing
            if (w < 0.02f) continue
            val bin = (hue(pr, pg, pb, hi, lo) / 360f * BINS).toInt().coerceIn(0, BINS - 1)
            weight[bin] += w; r[bin] += pr * w; g[bin] += pg * w; b[bin] += pb * w
        }
        val best = weight.indices.maxByOrNull { weight[it] } ?: return null
        if (weight[best] < MIN_WEIGHT) return null
        return lift(r[best] / weight[best], g[best] / weight[best], b[best] / weight[best])
    }

    private fun hue(r: Float, g: Float, b: Float, hi: Float, lo: Float): Float {
        val d = hi - lo
        if (d == 0f) return 0f
        val h = when (hi) { r -> ((g - b) / d) % 6f; g -> (b - r) / d + 2f; else -> (r - g) / d + 4f } * 60f
        return if (h < 0f) h + 360f else h
    }

    /** Raise saturation and brightness to a floor so a dark cover still gives a legible, colourful tint. */
    private fun lift(r: Float, g: Float, b: Float): Int {
        val hi = max(r, max(g, b)); val lo = min(r, min(g, b))
        val h = hue(r, g, b, hi, lo)
        val s = max(if (hi == 0f) 0f else (hi - lo) / hi, 0.55f)
        val v = max(hi, 0.85f)
        val c = v * s; val x = c * (1f - abs((h / 60f) % 2f - 1f)); val m = v - c
        val (rr, gg, bb) = when ((h / 60f).toInt()) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        fun ch(f: Float) = ((f + m) * 255f + .5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(rr) shl 16) or (ch(gg) shl 8) or ch(bb)
    }
}
