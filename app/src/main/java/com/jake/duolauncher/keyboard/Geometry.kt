package com.jake.duolauncher.keyboard

import kotlin.math.hypot
import kotlin.math.min

/** A point on the keyboard, in window pixels. Plain numbers so the touch arithmetic is unit-tested. */
internal data class Pt(val x: Float, val y: Float)

/** Where each letter key is on screen: its centre, and the size of a key. The keys report their own positions as they are laid out. */
internal class KeyGeometry(val centres: Map<Char, Pt>, val keyWidth: Float, val keyHeight: Float) {
    val usable get() = keyWidth > 0f && centres.size >= 10

    /** How far [p] is from [key]'s centre, in key widths (rows are scaled so a key's height counts as its width). */
    fun distance(p: Pt, key: Char): Float? {
        val c = centres[key] ?: return null
        val dy = (p.y - c.y) * (keyWidth / keyHeight.coerceAtLeast(1f))
        return hypot(p.x - c.x, dy) / keyWidth
    }

    /** The letter keys within [radius] key widths of [p], nearest first. */
    fun near(p: Pt, radius: Float): List<Char> = centres.keys.mapNotNull { k -> distance(p, k)?.takeIf { it <= radius }?.let { k to it } }
        .sortedBy { it.second }.map { it.first }

    companion object { val EMPTY = KeyGeometry(emptyMap(), 0f, 0f) }
}

/** The touch model behind autocorrect: a tap that lands near the edge of a key is likelier to have been meant for the key beside it.
 * Modern keyboards correct with where each tap actually fell, not just which key took it, and this is that: the cost of reading a
 * typed letter as another is small when the tap was close to the other key and large when it was not.
 */
internal object TouchModel {
    /** The cost of reading the tap at [touch] as [key]: about 0.6 for a tap in the middle of the key next door (the old fixed neighbour
     * cost), less as the tap nears that key's edge, up to the cost of an unrelated letter for a key well away. Null when unknown.
     */
    fun substitutionCost(geometry: KeyGeometry, touch: Pt?, key: Char, far: Double): Double? {
        if (touch == null || !geometry.usable) return null
        val d = geometry.distance(touch, key) ?: return null
        return min(far, .15 + .45 * d * d)
    }
}

/** Decodes a swipe across the letters into words (shape writing, as Gboard and SwiftKey do it): every word whose keys lie along the
 * path is compared with it, by how far the path strays from the line through the word's keys, and the closest common words win.
 * Pure Kotlin; runs on the suggestion worker.
 */
internal object GestureDecoder {
    private const val SAMPLES = 32

    /** The best words for [path] (best first, up to [limit]). [words] lists the candidate words with their commonness; [bonus] gives a word
     * extra points (it often follows the previous word, or it is one of the person's own words).
     */
    fun decode(path: List<Pt>, geometry: KeyGeometry, words: (Char) -> Sequence<Pair<String, Int>>, bonus: (String) -> Double = { 0.0 },
        limit: Int = 4): List<String> {
        if (path.size < 2 || !geometry.usable) return emptyList()
        val starts = geometry.near(path.first(), 1.1f).take(3)
        val ends = geometry.near(path.last(), 1.1f).take(3).toSet()
        if (starts.isEmpty() || ends.isEmpty()) return emptyList()
        val sampled = resample(path, SAMPLES)
        val pathLength = length(path) / geometry.keyWidth
        val scored = ArrayList<Pair<String, Double>>()
        for (start in starts) for ((word, score) in words(start)) {
            if (word.length < 2 || word.last() !in ends) continue
            val ideal = idealPath(word, geometry) ?: continue
            // A path much shorter or longer than the word's own cannot be it; this skips the shape comparison for most of the list.
            val idealLength = length(ideal) / geometry.keyWidth
            if (pathLength < idealLength * .55f - .6f || pathLength > idealLength * 1.9f + 1.5f) continue
            val miss = lettersMiss(word, sampled, geometry) ?: continue
            val shape = shapeDistance(sampled, resample(ideal, SAMPLES)) / geometry.keyWidth
            // Where a swipe starts and, above all, where it is lifted are the most deliberate points of it, so they weigh most.
            val value = score / 10.0 - 12.0 * shape - 3.0 * miss - 3.0 * (geometry.distance(path.first(), word[0]) ?: 1f) -
                4.0 * (geometry.distance(path.last(), word.last()) ?: 1f) + bonus(word)
            scored += word to value
        }
        return scored.sortedByDescending { it.second }.map { it.first }.distinct().take(limit)
    }

    /** The line through [word]'s keys, a doubled letter counted once. Null if a letter is not on the keyboard. */
    fun idealPath(word: String, geometry: KeyGeometry): List<Pt>? {
        val out = ArrayList<Pt>(word.length)
        var last: Char? = null
        for (ch in word) {
            if (ch == last) continue
            out += geometry.centres[ch] ?: return null
            last = ch
        }
        if (out.size == 1) out += out[0]
        return out
    }

    /** How far the path passes from the word's keys, in order: for each key the nearest the path comes to it (after the point that
     * served the key before), beyond half a key, summed. A thumb cuts corners, so a key it skirted costs a little; a key it never came
     * near (over [MAX_MISS] key widths) means it is not this word, and gives null.
     */
    private fun lettersMiss(word: String, sampled: List<Pt>, geometry: KeyGeometry): Double? {
        // The keys are matched to points along the path in order (an alignment, worked out row by row with a running minimum), so a
        // word that comes back to a key ("that") matches each visit to its own part of the path.
        val aspect = geometry.keyWidth / geometry.keyHeight.coerceAtLeast(1f)
        val n = sampled.size
        var prev = DoubleArray(n) { 0.0 }
        var first = true
        for (ch in word) {
            val c = geometry.centres[ch] ?: return null
            val cur = DoubleArray(n)
            var runMin = Double.POSITIVE_INFINITY
            for (i in 0 until n) {
                runMin = minOf(runMin, prev[i])
                val d = hypot(sampled[i].x - c.x, (sampled[i].y - c.y) * aspect) / geometry.keyWidth
                cur[i] = if (d > MAX_MISS) Double.POSITIVE_INFINITY else (if (first) 0.0 else runMin) + (d - .5f).coerceAtLeast(0f)
            }
            prev = cur; first = false
        }
        val best = prev.min()
        return if (best.isInfinite()) null else best
    }

    private const val MAX_MISS = 1.6f

    private fun shapeDistance(a: List<Pt>, b: List<Pt>): Double {
        var sum = 0.0
        for (i in a.indices) sum += hypot((a[i].x - b[i].x).toDouble(), (a[i].y - b[i].y).toDouble())
        return sum / a.size
    }

    fun length(p: List<Pt>): Float { var l = 0f; for (i in 1 until p.size) l += hypot(p[i].x - p[i - 1].x, p[i].y - p[i - 1].y); return l }

    /** [n] points evenly spaced along the path. */
    fun resample(p: List<Pt>, n: Int): List<Pt> {
        val total = length(p)
        if (total <= 0f) return List(n) { p.first() }
        val step = total / (n - 1)
        val out = ArrayList<Pt>(n).apply { add(p.first()) }
        var acc = 0f; var prev = p.first(); var i = 1
        while (i < p.size && out.size < n) {
            val cur = p[i]
            val d = hypot(cur.x - prev.x, cur.y - prev.y)
            if (d > 0f && acc + d >= step) {
                val t = (step - acc) / d
                val q = Pt(prev.x + t * (cur.x - prev.x), prev.y + t * (cur.y - prev.y))
                out += q; prev = q; acc = 0f
            } else { acc += d; prev = cur; i++ }
        }
        while (out.size < n) out += p.last()
        return out
    }

    /** Whether a finger's movement on a letter key is a swipe across the keys rather than a tap or a flick: it has crossed at least two
     * keys and travelled more than [minTravel] key widths (a short flick up or down for an apostrophe or comma does not). Pure, for tests.
     */
    fun isSwipe(path: List<Pt>, geometry: KeyGeometry, minTravel: Float = 1.6f): Boolean {
        if (!geometry.usable || path.size < 3) return false
        val keys = path.mapNotNull { p -> geometry.near(p, 0.75f).firstOrNull() }.distinct()
        val travel = length(path) / geometry.keyWidth
        return keys.size >= 3 || (keys.size >= 2 && travel >= minTravel)
    }
}
