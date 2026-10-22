package com.jake.duolauncher.keyboard

import kotlin.math.ln

/** What the keyboard has learned from the person typing: the words they use that the dictionary lacks (names, places, slang, their
 * own spellings), how they capitalise them, and which words they put after which. It is what makes predictions personal, keeps
 * autocorrect off a name it has seen you type, and offers your own words as completions.
 *
 * Pure Kotlin and thread-safe (every method locks), so the suggestion worker can read it while the main thread teaches it. It lives
 * on the phone only: [PersonalStore] keeps it in the app's no-backup storage, and nothing here is ever sent anywhere. It is never
 * taught from a password, email, web address or number field, nor from any field that asks keyboards not to learn (incognito
 * tabs); see [LearningPolicy].
 *
 * A word the dictionary lacks is only *active* (suggested, and protected from autocorrect) once it has been typed twice, or once
 * if it was meant (an undone correction, or the typed word picked in the strip), so a single slip is never learned.
 */
internal class PersonalModel {
    private class Entry(var form: String, var count: Int, var day: Int, var explicit: Boolean)

    private val words = HashMap<String, Entry>()
    private val pairs = HashMap<String, HashMap<String, Int>>()
    private val triples = HashMap<String, HashMap<String, Int>>()
    @Volatile var changed = false; private set

    @Synchronized fun isEmpty() = words.isEmpty() && pairs.isEmpty()

    /** Records that [word] was typed (and kept) on [day] (days since 1970). [inDictionary] words are counted for prediction but need no
     * activating. Returns true when this made a new word active.
     */
    @Synchronized fun learnWord(word: String, day: Int, inDictionary: Boolean, explicit: Boolean = false): Boolean {
        val w = clean(word) ?: return false
        val key = w.lowercase()
        val e = words[key]
        val wasActive = e != null && active(e, inDictionary)
        if (e == null) words[key] = Entry(w, 1, day, explicit)
        else {
            e.count = (e.count + 1).coerceAtMost(MAX_COUNT); e.day = day; e.explicit = e.explicit || explicit
            // The way it is written follows the latest use once it has been seen a few times: "Everett" stays capitalised, "ok" is not.
            if (w != e.form && (w.first().isLowerCase() || e.count <= 3)) e.form = w
        }
        changed = true
        trim()
        return !wasActive && active(words[key]!!, inDictionary)
    }

    /** Records that [word] followed [previous] (and [previous2] before that). */
    @Synchronized fun learnSequence(previous2: String?, previous: String?, word: String) {
        val w = clean(word)?.lowercase() ?: return
        val p = previous?.let(::clean)?.lowercase() ?: return
        pairs.getOrPut(p) { HashMap() }.merge(w, 1) { a, b -> (a + b).coerceAtMost(MAX_COUNT) }
        previous2?.let(::clean)?.lowercase()?.let { p2 -> triples.getOrPut("$p2 $p") { HashMap() }.merge(w, 1) { a, b -> (a + b).coerceAtMost(MAX_COUNT) } }
        changed = true
        if (pairs.size > MAX_PAIR_HEADS || triples.size > MAX_PAIR_HEADS) trimPairs()
    }

    /** Whether [word] is one of the person's own words, active and so never to be "corrected". */
    @Synchronized fun knows(word: String, inDictionary: Boolean = false): Boolean = words[word.lowercase()]?.let { active(it, inDictionary) } == true

    /** How [word] is usually written, when it is a learned word with its own capitals ("Everett", "iPhone"); otherwise null. */
    @Synchronized fun formOf(word: String): String? = words[word.lowercase()]?.takeIf { it.count >= 2 || it.explicit }?.form?.takeIf { it != it.lowercase() }

    /** The person's active words beginning with [prefix] (lower case), with a score on the dictionary's scale, best first. */
    @Synchronized fun completions(prefix: String, isDictionaryWord: (String) -> Boolean, limit: Int = 4): List<Pair<String, Int>> =
        words.entries.asSequence().filter { (k, e) -> k.startsWith(prefix) && k != prefix && active(e, isDictionaryWord(k)) }
            .map { (_, e) -> e.form to scoreOf(e) }.sortedByDescending { it.second }.take(limit).toList()

    /** Every active word, for the correction scan, with its score. */
    @Synchronized fun activeWords(isDictionaryWord: (String) -> Boolean): List<Pair<String, Int>> =
        words.entries.filter { (k, e) -> !isDictionaryWord(k) && active(e, false) }.map { (k, e) -> k to scoreOf(e) }

    /** The words the person most often puts after [previous] (and after [previous2] [previous]), most used first. Only sequences seen
     * at least twice count, so one odd sentence does not become a prediction.
     */
    @Synchronized fun next(previous2: String?, previous: String?, limit: Int = 3): List<String> {
        val p = previous?.lowercase() ?: return emptyList()
        val tri = previous2?.lowercase()?.let { triples["$it $p"] }.orEmpty().filterValues { it >= 2 }
        val bi = pairs[p].orEmpty().filterValues { it >= 2 }
        return (tri.entries.sortedByDescending { it.value }.map { it.key } + bi.entries.sortedByDescending { it.value }.map { it.key })
            .distinct().take(limit).map { words[it]?.form ?: it }
    }

    /** The person's own words, for the settings page: active ones, most used first. */
    @Synchronized fun list(): List<String> = words.values.filter { it.explicit || it.count >= 2 }.sortedByDescending { it.count }.map { it.form }

    /** Adds [word] as one of the person's own, at once (from the settings page). */
    @Synchronized fun add(word: String, day: Int) {
        val w = clean(word) ?: return
        words[w.lowercase()] = Entry(w, maxOf(words[w.lowercase()]?.count ?: 0, 2), day, explicit = true); changed = true
    }

    /** Forgets [word] and every sequence it is part of. */
    @Synchronized fun forget(word: String) {
        val k = word.lowercase()
        words.remove(k); pairs.remove(k); pairs.values.forEach { it.remove(k) }
        triples.keys.filter { it.split(' ').contains(k) }.forEach { triples.remove(it) }; triples.values.forEach { it.remove(k) }
        changed = true
    }

    @Synchronized fun clear() { words.clear(); pairs.clear(); triples.clear(); changed = true }

    // ---- keeping it -----------------------------------------------------------------------------------------------------------

    /** The model as lines of text: `w<TAB>form<TAB>count<TAB>day<TAB>explicit`, `b<TAB>previous<TAB>word<TAB>count` and
     * `t<TAB>previous2 previous<TAB>word<TAB>count`. Clears [changed].
     */
    @Synchronized fun encode(): String = buildString {
        append(HEADER).append('\n')
        for (e in words.values) append("w\t").append(e.form).append('\t').append(e.count).append('\t').append(e.day).append('\t').append(if (e.explicit) 1 else 0).append('\n')
        for ((p, m) in pairs) for ((w, c) in m) append("b\t").append(p).append('\t').append(w).append('\t').append(c).append('\n')
        for ((p, m) in triples) for ((w, c) in m) append("t\t").append(p).append('\t').append(w).append('\t').append(c).append('\n')
        changed = false
    }

    /** Replaces the model with what [text] holds. Lines it does not understand are skipped. */
    @Synchronized fun decode(text: String) {
        words.clear(); pairs.clear(); triples.clear()
        for (line in text.lineSequence()) {
            val f = line.split('\t')
            when (f.firstOrNull()) {
                "w" -> if (f.size >= 5) clean(f[1])?.let { w -> words[w.lowercase()] = Entry(w, f[2].toIntOrNull() ?: 1, f[3].toIntOrNull() ?: 0, f[4] == "1") }
                "b" -> if (f.size >= 4) pairs.getOrPut(f[1]) { HashMap() }[f[2]] = f[3].toIntOrNull() ?: 1
                "t" -> if (f.size >= 4) triples.getOrPut(f[1]) { HashMap() }[f[2]] = f[3].toIntOrNull() ?: 1
            }
        }
        trim(); changed = false
    }

    private fun active(e: Entry, inDictionary: Boolean) = inDictionary || e.explicit || e.count >= 2

    /** A learned word's commonness on the dictionary's scale (ten times a natural log): a word used often ranks with everyday words. */
    private fun scoreOf(e: Entry): Int = (BASE_SCORE + 12 * ln(e.count + 1.0)).toInt()

    /** Over the limit, the least used and longest unused words go first. */
    private fun trim() {
        if (words.size <= MAX_WORDS) return
        words.entries.sortedWith(compareBy({ it.value.explicit }, { it.value.count }, { it.value.day })).take(words.size - MAX_WORDS * 9 / 10)
            .forEach { words.remove(it.key) }
    }

    private fun trimPairs() {
        for (map in listOf(pairs, triples)) if (map.size > MAX_PAIR_HEADS)
            map.entries.sortedBy { it.value.values.sum() }.take(map.size - MAX_PAIR_HEADS * 9 / 10).forEach { map.remove(it.key) }
    }

    companion object {
        const val HEADER = "# uno keyboard personal model 1"
        private const val MAX_WORDS = 5000
        private const val MAX_PAIR_HEADS = 4000
        private const val MAX_COUNT = 10_000
        private const val BASE_SCORE = 150

        /** A word worth learning: letters (any script) with inner apostrophes or hyphens, 2 to 32 long, and not a number or code. */
        fun clean(word: String): String? {
            val w = word.trim().trim('\'', '’', '-').replace('’', '\'')
            if (w.length < 2 || w.length > 32) return null
            if (!w.all { it.isLetter() || it == '\'' || it == '-' }) return null
            return w
        }
    }
}

/** When the keyboard may learn from a field. Pure, from the field's input type and options. */
internal object LearningPolicy {
    /** EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING: incognito tabs and other private fields ask for it. */
    const val IME_FLAG_NO_PERSONALIZED_LEARNING = 0x1000000

    fun allowed(inputType: Int, imeOptions: Int, learningOn: Boolean): Boolean =
        learningOn && imeOptions and IME_FLAG_NO_PERSONALIZED_LEARNING == 0 && KeyboardModel.allowsSuggestions(inputType) && !KeyboardModel.isPassword(inputType)
}
