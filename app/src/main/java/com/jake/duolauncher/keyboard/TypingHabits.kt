package com.jake.duolauncher.keyboard

import kotlin.math.hypot

/** How the person types, beyond which words: where on each key their taps really land, how often they undo autocorrect, which layout
 * they use in which app, and the emoji they put after a word. It is what lets the keyboard fit a hand instead of an average one.
 *
 * Pure Kotlin and thread-safe (every method locks), like [PersonalModel], and kept the same way: by [PersonalStore], on the phone only, in
 * storage no backup or settings export includes, wiped by "Forget everything". Nothing here holds what was typed: tap offsets are
 * averages per key, the undo rate is one number, and the per-app memory is a layout name for an app's package.
 */
internal class TypingHabits {
    private class Offset(var dx: Float, var dy: Float, var n: Int)

    /** For each letter, the average of where taps meant for it landed, from its key's centre, in key widths. */
    private val offsets = HashMap<Char, Offset>()
    /** The share of recent autocorrections the person undid (an exponentially weighted average), and how many it has seen. */
    private var undoRate = 0.0
    private var corrections = 0
    /** The layout last used in each app, by package name. */
    private val layouts = LinkedHashMap<String, String>(16, .75f, true)
    /** The emoji the person typed after a word, and how often. */
    private val emoji = HashMap<String, HashMap<String, Int>>()
    @Volatile var changed = false; private set

    @Synchronized fun isEmpty() = offsets.isEmpty() && corrections == 0 && layouts.isEmpty() && emoji.isEmpty()

    // ---- where the taps land ----------------------------------------------------------------------------------------------------

    /** Learns from a word the person kept as typed: each letter's tap in [touches] (one per letter of [word], null when unknown) against
     * where its key is in [geometry]. A tap well away from its key is a slip that happened to be right, not a habit, and is skipped.
     */
    @Synchronized fun learnTaps(word: String, touches: List<Pt?>, geometry: KeyGeometry) {
        if (!geometry.usable || touches.size != word.length) return
        val aspect = geometry.keyWidth / geometry.keyHeight.coerceAtLeast(1f)
        for ((i, ch) in word.lowercase().withIndex()) {
            val t = touches[i] ?: continue
            val c = geometry.centres[ch] ?: continue
            val dx = (t.x - c.x) / geometry.keyWidth
            val dy = (t.y - c.y) * aspect / geometry.keyWidth
            if (hypot(dx, dy) > MAX_TAP_OFFSET) continue
            val o = offsets.getOrPut(ch) { Offset(0f, 0f, 0) }
            // A running mean for the first taps, then a moving one, so the keyboard follows a grip that changes (a case, a new phone).
            val w = 1f / minOf(o.n + 1, TAP_WINDOW)
            o.dx += (dx - o.dx) * w; o.dy += (dy - o.dy) * w; o.n = (o.n + 1).coerceAtMost(MAX_COUNT)
            changed = true
        }
    }

    /** [geometry] with each key's centre moved to where this person's taps for it land, once it has seen enough of them. */
    @Synchronized fun adjust(geometry: KeyGeometry): KeyGeometry {
        if (!geometry.usable || offsets.none { it.value.n >= MIN_TAPS }) return geometry
        val h = geometry.keyHeight.coerceAtLeast(1f) / geometry.keyWidth
        val moved = geometry.centres.mapValues { (ch, c) ->
            val o = offsets[ch]?.takeIf { it.n >= MIN_TAPS } ?: return@mapValues c
            Pt(c.x + o.dx.coerceIn(-MAX_SHIFT, MAX_SHIFT) * geometry.keyWidth, c.y + o.dy.coerceIn(-MAX_SHIFT, MAX_SHIFT) * geometry.keyWidth * h)
        }
        return KeyGeometry(moved, geometry.keyWidth, geometry.keyHeight)
    }

    /** How far, in key widths, the person's taps for [key] land from its centre, once known (for tests and the settings page). */
    @Synchronized fun offsetOf(key: Char): Pair<Float, Float>? = offsets[key]?.takeIf { it.n >= MIN_TAPS }?.let { it.dx to it.dy }

    // ---- how much autocorrect is wanted --------------------------------------------------------------------------------------------

    /** An autocorrection was made ([undone] false) or undone ([undone] true). */
    @Synchronized fun correction(undone: Boolean) {
        if (undone) {
            // The correction was counted as kept when it was made; undoing it turns that one sample from kept to refused.
            undoRate += UNDO_WEIGHT
        } else {
            undoRate += (0.0 - undoRate) * UNDO_WEIGHT; corrections = (corrections + 1).coerceAtMost(MAX_COUNT)
        }
        undoRate = undoRate.coerceIn(0.0, 1.0)
        changed = true
    }

    /** How much more careful autocorrect should be for this person, from 0 (as built) to [MAX_CAUTION]: someone who undoes many of its
     * corrections gets fewer, surer ones. Nothing changes until it has made enough corrections to judge.
     */
    @Synchronized fun caution(): Double =
        if (corrections < MIN_CORRECTIONS) 0.0 else ((undoRate - CALM_UNDO_RATE) * 1.5).coerceIn(0.0, MAX_CAUTION)

    @Synchronized fun undoRate(): Double = undoRate

    // ---- layouts per app ------------------------------------------------------------------------------------------------------------

    @Synchronized fun rememberLayout(app: String, layout: String) {
        if (app.isBlank() || layouts[app] == layout) return
        layouts[app] = layout
        if (layouts.size > MAX_APPS) layouts.keys.iterator().let { it.next(); it.remove() }
        changed = true
    }

    @Synchronized fun layoutFor(app: String): String? = layouts[app]

    // ---- emoji after words ----------------------------------------------------------------------------------------------------------

    @Synchronized fun learnEmoji(word: String, emoji: String) {
        val w = PersonalModel.clean(word)?.lowercase() ?: return
        if (emoji.isBlank() || emoji.length > 16) return
        this.emoji.getOrPut(w) { HashMap() }.merge(emoji, 1) { a, b -> (a + b).coerceAtMost(MAX_COUNT) }
        if (this.emoji.size > MAX_EMOJI_WORDS) this.emoji.entries.sortedBy { it.value.values.sum() }
            .take(this.emoji.size - MAX_EMOJI_WORDS * 9 / 10).forEach { this.emoji.remove(it.key) }
        changed = true
    }

    /** The emoji the person has put after [word] at least twice, most used first. */
    @Synchronized fun emojiFor(word: String): List<String> =
        emoji[word.lowercase().trim('\'')]?.entries?.filter { it.value >= 2 }?.sortedByDescending { it.value }?.map { it.key }.orEmpty()

    /** What has been learned, for the settings page: keys fitted to the person's taps, and apps with a remembered layout. */
    @Synchronized fun fittedKeys(): Int = offsets.values.count { it.n >= MIN_TAPS }
    @Synchronized fun appsWithLayouts(): Int = layouts.size

    @Synchronized fun clear() { offsets.clear(); undoRate = 0.0; corrections = 0; layouts.clear(); emoji.clear(); changed = true }

    // ---- keeping it -----------------------------------------------------------------------------------------------------------------

    /** As lines: `k<TAB>key<TAB>dx<TAB>dy<TAB>n`, `u<TAB>rate<TAB>corrections`, `l<TAB>app<TAB>layout`, `e<TAB>word<TAB>emoji<TAB>count`. */
    @Synchronized fun encode(): String = buildString {
        append(HEADER).append('\n')
        for ((k, o) in offsets) append("k\t").append(k).append('\t').append(o.dx).append('\t').append(o.dy).append('\t').append(o.n).append('\n')
        append("u\t").append(undoRate).append('\t').append(corrections).append('\n')
        for ((app, l) in layouts) append("l\t").append(app).append('\t').append(l).append('\n')
        for ((w, m) in emoji) for ((e, c) in m) append("e\t").append(w).append('\t').append(e).append('\t').append(c).append('\n')
        changed = false
    }

    @Synchronized fun decode(text: String) {
        offsets.clear(); layouts.clear(); emoji.clear(); undoRate = 0.0; corrections = 0
        for (line in text.lineSequence()) {
            val f = line.split('\t')
            when (f.firstOrNull()) {
                "k" -> if (f.size >= 5 && f[1].length == 1) {
                    val dx = f[2].toFloatOrNull(); val dy = f[3].toFloatOrNull(); val n = f[4].toIntOrNull()
                    if (dx != null && dy != null && n != null && dx.isFinite() && dy.isFinite()) offsets[f[1][0]] = Offset(dx, dy, n)
                }
                "u" -> if (f.size >= 3) { undoRate = (f[1].toDoubleOrNull() ?: 0.0).coerceIn(0.0, 1.0); corrections = f[2].toIntOrNull() ?: 0 }
                "l" -> if (f.size >= 3) layouts[f[1]] = f[2]
                "e" -> if (f.size >= 4) emoji.getOrPut(f[1]) { HashMap() }[f[2]] = f[3].toIntOrNull() ?: 1
            }
        }
        changed = false
    }

    companion object {
        const val HEADER = "# uno keyboard habits 1"
        /** Taps for a key before its learned centre is used. */
        const val MIN_TAPS = 12
        private const val TAP_WINDOW = 200
        /** A tap further than this from its key (in key widths) is not used to learn where that key is. */
        private const val MAX_TAP_OFFSET = .8f
        /** The furthest a key's learned centre moves from its drawn one, in key widths. */
        private const val MAX_SHIFT = .35f
        private const val UNDO_WEIGHT = .05
        /** Undoing up to about one correction in ten is normal and changes nothing. */
        private const val CALM_UNDO_RATE = .12
        const val MAX_CAUTION = .35
        private const val MIN_CORRECTIONS = 20
        private const val MAX_APPS = 200
        private const val MAX_EMOJI_WORDS = 500
        private const val MAX_COUNT = 1_000_000
    }
}
