package com.jake.duolauncher.keyboard

import kotlin.math.abs
import kotlin.math.min

/** Spelling correction and next-word suggestions from a bundled word list. Pure Kotlin: no Android classes, no
 * storage, no network (see `KeyboardPrivacyTest`). It never learns: nothing you type is added to the dictionary or
 * remembered after the session, so there is no typing history anywhere to leak.
 *
 * [scores] is each word's commonness (higher is more common, roughly ten times its natural log of its frequency).
 * [followers] maps a word to the words that most often come after it, best first.
 */
internal class WordEngine(scores: Map<String, Int>, private val followers: Map<String, List<String>>) {
    private val sorted: Array<String> = scores.keys.sorted().toTypedArray()
    private val score: Map<String, Int> = scores
    /** Words by first letter, for the correction scan. */
    private val byFirst: Map<Char, List<String>> = sorted.groupBy { it[0] }

    fun contains(word: String) = word in score || word in CONTRACTION_WORDS
    fun scoreOf(word: String) = score[word] ?: 0

    /** The most common words that start with [prefix] (not including [prefix] itself), best first. */
    fun completions(prefix: String, limit: Int = 3): List<String> {
        if (prefix.isEmpty()) return emptyList()
        // A short prefix matches thousands of words and is typed over and over, so its best few are kept: the next keystroke that
        // reaches the same prefix answers from memory instead of scanning and sorting them all again.
        if (prefix.length > CACHED_PREFIX || limit > CACHED_LIMIT) return scan(prefix, limit)
        val best = synchronized(cache) { cache.getOrPut(prefix) { scan(prefix, CACHED_LIMIT) } }
        return if (best.size <= limit) best else best.take(limit)
    }

    private val cache = object : LinkedHashMap<String, List<String>>(256, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?) = size > 600
    }

    /** The same answer without the cache, for tests that check the cache changes nothing. */
    internal fun completionsUncached(prefix: String, limit: Int): List<String> = scan(prefix, limit)

    private fun scan(prefix: String, limit: Int): List<String> {
        var i = sorted.binarySearch(prefix).let { if (it < 0) -it - 1 else it }
        val found = ArrayList<String>()
        while (i < sorted.size && sorted[i].startsWith(prefix)) { if (sorted[i] != prefix) found += sorted[i]; i++ }
        return found.sortedByDescending { score[it] ?: 0 }.take(limit)
    }

    /** What most often follows [previous]; the commonest sentence openers when there is no usable previous word. */
    fun nextWords(previous: String?, limit: Int = 3): List<String> {
        val own = previous?.lowercase()?.let { followers[it] }.orEmpty()
        return (own + STARTERS).distinct().take(limit)
    }

    /** The word [typed] should be corrected to, or null to leave it alone. Case is not handled here (see [matchCase]). */
    fun correction(typed: String): String? {
        val t = typed.lowercase()
        CONTRACTIONS[t]?.let { return it }
        if (t.length < 2 || t in score) return null
        val maxCost = when { t.length <= 3 -> 1.0; t.length <= 5 -> 1.7; else -> 2.4 }
        val buckets = listOfNotNull(byFirst[t[0]], if (t.length > 1) byFirst[t[1]] else null).distinct()
        var best: String? = null
        var bestValue = Double.NEGATIVE_INFINITY
        for (bucket in buckets) for (cand in bucket) {
            if (abs(cand.length - t.length) > 2) continue
            val cost = editCost(t, cand, maxCost)
            if (cost > maxCost) continue
            // Commoner words win, and every bit of edit cost counts against a candidate.
            val value = (score[cand] ?: 0) / 10.0 - COST_WEIGHT * cost
            if (value > bestValue) { bestValue = value; best = cand }
        }
        return best
    }

    /** Damerau-Levenshtein distance where a slip to a neighbouring key and a swap of two letters are cheaper than a
     * random change. Returns something greater than [limit] as soon as it cannot finish within it.
     */
    internal fun editCost(a: String, b: String, limit: Double): Double {
        val n = a.length; val m = b.length
        if (abs(n - m) > limit) return limit + 1
        var prevPrev = DoubleArray(m + 1); var prev = DoubleArray(m + 1) { it.toDouble() }; var cur = DoubleArray(m + 1)
        for (i in 1..n) {
            cur[0] = i.toDouble()
            var rowMin = cur[0]
            for (j in 1..m) {
                val sub = if (a[i - 1] == b[j - 1]) 0.0 else if (NEIGHBOURS[a[i - 1]]?.contains(b[j - 1]) == true) 0.6 else FAR_SUBSTITUTION
                // A letter missing from the typed word that doubles one next to it ("helo" for "hello") is the commonest
                // slip of all, so it costs less than a missing letter elsewhere.
                val missing = if ((j >= 2 && b[j - 1] == b[j - 2]) || (j < m && b[j - 1] == b[j])) 0.5 else 1.0
                var v = min(min(prev[j] + 1.0, cur[j - 1] + missing), prev[j - 1] + sub)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = min(v, prevPrev[j - 2] + 0.8)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > limit) return limit + 1
            val t = prevPrev; prevPrev = prev; prev = cur; cur = t
        }
        return prev[m]
    }

    companion object {
        private const val COST_WEIGHT = 4.0
        private const val CACHED_PREFIX = 3
        private const val CACHED_LIMIT = 4
        /** Swapping a letter for one far away on the keyboard is a less likely slip than leaving a letter out, so it costs more. */
        private const val FAR_SUBSTITUTION = 1.3

        /** Missing apostrophes are the commonest slip, and "its"/"ill"/"were"/"well" are real words, so those are left alone. */
        val CONTRACTIONS = mapOf(
            "dont" to "don't", "doesnt" to "doesn't", "didnt" to "didn't", "cant" to "can't", "wont" to "won't",
            "isnt" to "isn't", "arent" to "aren't", "wasnt" to "wasn't", "werent" to "weren't", "couldnt" to "couldn't",
            "wouldnt" to "wouldn't", "shouldnt" to "shouldn't", "hasnt" to "hasn't", "havent" to "haven't", "hadnt" to "hadn't",
            "im" to "I'm", "ive" to "I've", "youre" to "you're", "theyre" to "they're", "thats" to "that's", "whats" to "what's",
            "youve" to "you've", "weve" to "we've", "theyve" to "they've", "hes" to "he's", "shes" to "she's", "i" to "I",
            "id" to "I'd")

        private val CONTRACTION_WORDS: Set<String> = CONTRACTIONS.values.mapTo(HashSet()) { it.lowercase() }

        private val STARTERS = listOf("I", "The", "It", "You", "We", "What", "Thanks", "Hey")

        private val ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        /** Which keys sit next to which on a QWERTY phone keyboard. */
        val NEIGHBOURS: Map<Char, Set<Char>> = buildMap {
            ROWS.forEachIndexed { r, row ->
                row.forEachIndexed { c, ch ->
                    val near = mutableSetOf<Char>()
                    if (c > 0) near += row[c - 1]; if (c < row.lastIndex) near += row[c + 1]
                    // Rows are offset by half a key: a key touches columns c and c+1 of the row above it, and c-1 and c below it.
                    ROWS.getOrNull(r - 1)?.let { above -> for (dc in 0..1) above.getOrNull(c + dc)?.let { near += it } }
                    ROWS.getOrNull(r + 1)?.let { below -> for (dc in -1..0) below.getOrNull(c + dc)?.let { near += it } }
                    put(ch, near)
                }
            }
        }

        /** Parses `word score` lines. Bad lines are skipped. */
        fun parseWords(lines: Sequence<String>): Map<String, Int> {
            val out = HashMap<String, Int>(100_000)
            for (line in lines) {
                val sp = line.indexOf(' ')
                if (sp <= 0) continue
                val s = line.substring(sp + 1).trim().toIntOrNull() ?: continue
                out[line.substring(0, sp)] = s
            }
            return out
        }

        /** Parses `word<TAB>next1 next2 ...` lines. */
        fun parseFollowers(lines: Sequence<String>): Map<String, List<String>> {
            val out = HashMap<String, List<String>>(10_000)
            for (line in lines) {
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                out[line.substring(0, tab)] = line.substring(tab + 1).split(' ').filter { it.isNotBlank() }
            }
            return out
        }

        /** Gives [fix] the capitalisation pattern of [typed]: "Teh" gives "The", "TEH" gives "THE". */
        fun matchCase(typed: String, fix: String): String = when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } && typed.any { it.isLetter() } -> fix.uppercase()
            typed.firstOrNull()?.isUpperCase() == true -> fix.replaceFirstChar { it.uppercase() }
            else -> fix
        }
    }
}
