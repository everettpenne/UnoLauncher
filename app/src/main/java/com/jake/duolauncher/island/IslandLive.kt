package com.jake.duolauncher.island

import com.jake.duolauncher.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Things that can be live on the collapsed island at once. Privacy indicators come first and are glyph-only; the rest are in
 * the order of how much the user needs to see them. A flash event or a ringing timer is not in here: those take the whole pill.
 */
internal enum class LiveKind(val privacy: Boolean = false) { CAMERA(true), MIC(true), RECORDING(true), CALL, TIMER, STOPWATCH, UPDATE, MEDIA }

/** What a collapsed-pill slot shows for a kind: its small glyph (an icon or artwork), or its detail (the text or bars). */
internal enum class SlotShow { GLYPH, DETAIL }

/** The collapsed island's plan, as Apple does it: the *leading* activity is the one the main pill carries (its glyph on one
 * side of the camera, its detail on the other), and the *trailing* activity is a separate detached capsule to the right of
 * the pill. More activities beyond those two are also detached capsules (see [IslandBubbles]).
 */
internal data class SlotPlan(
    /** The leading activity the pill carries, glyph one side + detail the other; null when nothing is live. */
    val leading: LiveKind?,
    /** The trailing activity, a detached capsule to the right of the pill; null with one or no live activity. */
    val trailing: LiveKind?,
)

/** The live-activity plan: the leading activity the pill carries and the trailing activity shown as its own detached capsule. */
internal object IslandLive {
    /** The live activities, most important first: the first is the pill's leading, the second its trailing capsule, and any
     * further ones the extra capsules. Camera, microphone and screen recording are not among them: they are privacy marks,
     * shown as small dots beside the island whatever else is going on (see [privacy]), so a video call keeps its timer on the
     * pill instead of being pushed out by the camera and microphone.
     */
    fun active(call: Boolean, timer: Boolean, stopwatch: Boolean, media: Boolean, update: Boolean = false): List<LiveKind> =
        buildList {
            if (call) add(LiveKind.CALL)
            if (timer) add(LiveKind.TIMER)
            if (stopwatch) add(LiveKind.STOPWATCH)
            if (update) add(LiveKind.UPDATE)
            if (media) add(LiveKind.MEDIA)
        }

    /** The privacy marks to show as dots, in a fixed order (camera, microphone, screen recording). */
    fun privacy(camera: Boolean, mic: Boolean, recording: Boolean): List<LiveKind> = buildList {
        if (camera) add(LiveKind.CAMERA)
        if (mic) add(LiveKind.MIC)
        if (recording) add(LiveKind.RECORDING)
    }

    /** The list turned [shift] places (a flick across the island swaps which activity is in front). */
    fun rotated(kinds: List<LiveKind>, shift: Int): List<LiveKind> {
        if (kinds.size < 2) return kinds
        val by = ((shift % kinds.size) + kinds.size) % kinds.size
        return kinds.drop(by) + kinds.take(by)
    }

    /** [kinds] with [front] moved to the head (the rest keep their order, so the old leading activity becomes the trailing capsule).
     * Unchanged when [front] is null or not live. Pinning by *kind* rather than by a turn count means that when some other
     * activity starts or ends, the one the person chose stays in front instead of a different one jumping forward.
     */
    fun withFront(kinds: List<LiveKind>, front: LiveKind?): List<LiveKind> =
        if (front == null || front !in kinds || kinds.first() == front) kinds else listOf(front) + (kinds - front)

    /** The activity a flick brings forward: the one after the current leading in the fixed order, wrapping round. Null with fewer than two. */
    fun nextFront(canonical: List<LiveKind>, front: LiveKind?): LiveKind? {
        if (canonical.size < 2) return null
        val at = canonical.indexOf(front ?: canonical.first()).coerceAtLeast(0)
        return canonical[(at + 1) % canonical.size]
    }

    /** A spoken name for an activity, for the capsule's accessibility label. */
    fun label(kind: LiveKind): String = when (kind) {
        LiveKind.CAMERA -> "camera"; LiveKind.MIC -> "microphone"; LiveKind.RECORDING -> "screen recording"
        LiveKind.CALL -> "call"; LiveKind.TIMER -> "timer"; LiveKind.STOPWATCH -> "stopwatch"
        LiveKind.UPDATE -> "live update"; LiveKind.MEDIA -> "music"
    }

    fun plan(kinds: List<LiveKind>): SlotPlan = when (kinds.size) {
        0 -> SlotPlan(null, null)
        1 -> SlotPlan(kinds[0], null)
        else -> SlotPlan(kinds[0], kinds[1])
    }
}

/** One thing the island is showing, or is still finishing leaving: [index] is where it sat (kept while it [leaving], so its exit starts from there). */
internal data class Held<T>(val item: T, val index: Int, val leaving: Boolean)

/** Keeps a thing that has just ended or moved on drawn until its exit animation is done, instead of vanishing the moment it leaves the list. Pure, so the rules are unit-tested. */
internal object Lingering {
    /** The things [current] now, in order, then the ones from [held] no longer in it, marked leaving and keeping the place they last had. */
    fun <T> merge(held: List<Held<T>>, current: List<T>): List<Held<T>> =
        current.mapIndexed { i, item -> Held(item, i, false) } +
            held.filter { it.item !in current }.map { it.copy(leaving = true) }

    /** [held] without the leaving [item], once its exit has finished. */
    fun <T> drop(held: List<Held<T>>, item: T): List<Held<T>> = held.filterNot { it.item == item && it.leaving }
}

/** What text the collapsed pill writes beside a Live Update's glyph, at the level the person chose: 0 nothing (the glyph alone, so
 *  the slot stays small), 1 the app's own short status (the pill's behaviour today), 2 the title instead. Pure, so the rule is unit-tested.
 */
internal object LiveCompactText {
    /** The text to show for [level] (0..2): null for none, [shortStatus] for 1, [title] for 2. Out-of-range levels are clamped. */
    fun choose(level: Int, shortStatus: String?, title: String): String? = when (level.coerceIn(0, 2)) {
        0 -> null
        2 -> title
        else -> shortStatus
    }
}

/** A detached capsule: which activity, which side of the pill it is on, and its place along that side (0 is next to the pill). Equal when
 * the activity is the same, so an activity that moves sides keeps its capsule (and its exit animation, if it ends).
 */
internal class Capsule(val kind: LiveKind, val left: Boolean, val slot: Int) {
    override fun equals(other: Any?) = other is Capsule && other.kind == kind
    override fun hashCode() = kind.hashCode()
}

/** Detached capsules to the right of the pill: the trailing activity first (glyph + short status), then the extras (glyph only).
 * These are Apple's trailing capsule plus Uno's extra "bubbles", restyled as the same capsule: a black rounded body with a
 * hairline rim. Every capsule is as tall as the pill (so its glyph sits concentrically), the trailing one is wide (glyph +
 * status), and an extra one is a circle the pill's height. Tapping one brings its activity to the front. Pure, so the rules
 * are unit-tested.
 */
internal object IslandBubbles {
    /** The extra activities shown after the pill's leading and its trailing capsule, at most [MAX]. */
    const val MAX = 2
    /** The gap between the pill and the first capsule, and between capsules, in dp. */
    const val GAP_DP = 6f
    /** The room kept clear of the screen's edge, in dp. */
    const val EDGE_MARGIN_DP = 8f

    /** Where each activity beyond the pill's goes. With two live, the second is a capsule to the right. With three or more, the second is a
     * capsule to the LEFT of the pill and the rest go to its right, the third next to the pill and any more after it, at most [MAX] more.
     */
    fun layout(kinds: List<LiveKind>): List<Capsule> = when {
        kinds.size < 2 -> emptyList()
        kinds.size == 2 -> listOf(Capsule(kinds[1], false, 0))
        else -> listOf(Capsule(kinds[1], true, 0)) + kinds.drop(2).take(1 + MAX).mapIndexed { i, k -> Capsule(k, false, i) }
    }

    /** The debug adjustment that applies to [capsule]: the left island's, the right island's for the one next to the pill, else the extras'. */
    fun groupFor(capsule: Capsule, left: DebugGroup, right: DebugGroup, extra: DebugGroup): DebugGroup =
        if (capsule.left) left else if (capsule.slot == 0) right else extra

    /** How far from the pill's right edge to the left edge of the right-hand capsule in [slot], in dp: past the privacy dots, then each gap
     * and each earlier capsule's width, with the debug adjustments added.
     */
    fun rightOffsetDp(slot: Int, heightDp: Float, dotsDp: Float, right: DebugGroup, extra: DebugGroup): Float {
        var x = dotsDp
        for (s in 0..slot) {
            val g = if (s == 0) right else extra
            x += GAP_DP + g.dx
            if (s < slot) x += heightDp + g.dw
        }
        return x
    }

    /** How far from the pill's left edge to the right edge of the left-hand capsule, in dp. */
    fun leftOffsetDp(left: DebugGroup): Float = GAP_DP + left.dx

    /** The activities shown as extra capsules: those after the pill's leading and its trailing capsule, at most [MAX]. */
    fun extras(kinds: List<LiveKind>): List<LiveKind> = kinds.drop(2).take(MAX)

    /** How far to turn the order so the capsule at [index] (0 is the trailing capsule, then the extras) is in front of the pill. */
    fun shiftFor(index: Int): Int = index + 1

    /** The width of the capsule at [index] (0 is the trailing capsule, then the extras), in dp. [heightDp] is the capsule/pill
     * height: an extra capsule is a circle of that height, so its glyph fits with a ring round it.
     */
    fun widthDp(index: Int, heightDp: Float): Float = heightDp

    /** How many capsules fit between the pill and the screen's edge: [roomDp] is what is left to the right of the pill, [dotsDp] what the privacy
     * dots take, and a small margin is kept clear of the edge. At most the trailing capsule plus [MAX] extras; the rest stay reachable by a flick.
     */
    fun capacity(roomDp: Float, heightDp: Float, dotsDp: Float): Int =
        ((roomDp - EDGE_MARGIN_DP - dotsDp) / (heightDp + GAP_DP)).toInt().coerceIn(0, 1 + MAX)

    /** How much room the detached capsules need to the right of the pill, in dp (0 with none). [count] is the total number of
     * capsules (the trailing capsule, then the extras) and [heightDp] the pill's collapsed height.
     */
    fun reserveDp(count: Int, heightDp: Float): Float {
        if (count <= 0) return 0f
        var widths = 0f
        for (i in 0 until count) widths += widthDp(i, heightDp)
        return count * GAP_DP + widths
    }
}

/** Where the privacy dots sit beside the pill, and how much room they need. */
internal object IslandPrivacy {
    const val DOT_DP = 9f
    const val GAP_DP = 6f
    const val STEP_DP = 13f
    fun widthDp(count: Int): Float = if (count <= 0) 0f else GAP_DP + count * STEP_DP
}

/** The fade of the playing track's colour across the island, as in HyperOS: strongest while music plays, faint while it is paused, and
 * none whenever something else has the island (an event flash, a notification card, a ringing timer) or music is not one of the things
 * it is showing.
 */
internal object IslandGlow {
    const val PLAYING = .40f
    const val PAUSED = .16f
    fun target(mediaShown: Boolean, playing: Boolean, somethingElseHasIt: Boolean): Float =
        if (!mediaShown || somethingElseHasIt) 0f else if (playing) PLAYING else PAUSED
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
