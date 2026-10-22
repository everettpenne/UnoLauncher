package com.jake.duolauncher.keyboard

import kotlin.math.abs
import kotlin.math.min

/** Spelling correction and next-word suggestions from a bundled word list, blended with what the person's own [personal] model has
 * learned. Pure Kotlin: no Android classes, no storage, no network (see `KeyboardPrivacyTest`); the learned model is kept, on the phone
 * only, by [PersonalStore].
 *
 * [scores] is each word's commonness (higher is more common, roughly ten times its natural log of its frequency).
 * [followers] maps a word to the words that most often come after it, best first.
 */
internal class WordEngine(scores: Map<String, Int>, private val followers: Map<String, List<String>>) {
    private val sorted: Array<String> = scores.keys.sorted().toTypedArray()
    private val score: Map<String, Int> = scores
    /** Words by first letter, for the correction scan. */
    private val byFirst: Map<Char, List<String>> = sorted.groupBy { it[0] }

    /** The person's learned words, when learning is on. Read on the suggestion worker; its methods lock. */
    @Volatile var personal: PersonalModel? = null
    /** Which keys neighbour which on the layout in use (QWERTY's by default), for the edit cost when there is no touch to go by. */
    @Volatile var neighbours: Map<Char, Set<Char>> = NEIGHBOURS

    fun inDictionary(word: String) = word in score
    fun contains(word: String) = word in score || word in CONTRACTION_WORDS || word in CONTRACTIONS || personal?.knows(word) == true
    fun scoreOf(word: String) = score[word] ?: 0

    /** The most common words that start with [prefix] (not including [prefix] itself), best first; the person's own words rank by how
     * much they use them, written the way they write them.
     */
    fun completions(prefix: String, limit: Int = 3): List<String> {
        val own = personal?.completions(prefix, { it in score }, limit).orEmpty()
        if (own.isEmpty()) return dictionaryCompletions(prefix, limit)
        val ranked = LinkedHashMap<String, Pair<String, Int>>()
        for (w in dictionaryCompletions(prefix, limit + 2)) ranked[w.lowercase()] = w to (score[w] ?: if ('\'' in w) CONTRACTION_SCORE else 0)
        for ((form, s) in own) { val k = form.lowercase(); val was = ranked[k]; ranked[k] = form to maxOf(s, was?.second ?: 0) }
        return ranked.values.sortedByDescending { it.second }.map { it.first }.take(limit)
    }

    /** Dictionary words (and their candidates for swipe typing) starting with [first], with their commonness: the person's own words too. */
    fun wordsStartingWith(first: Char): Sequence<Pair<String, Int>> {
        val dict = byFirst[first].orEmpty().asSequence().map { it to (score[it] ?: 0) }
        val own = personal?.activeWords { it in score }.orEmpty().asSequence().filter { it.first.firstOrNull() == first }
        return dict + own
    }

    private fun dictionaryCompletions(prefix: String, limit: Int): List<String> {
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
        // A contraction also completes what was typed: "don'" (the apostrophe flicked up) completes to "don't", and the bare
        // letters "doesnt"/"were" complete to "doesn't"/"we're", so the contraction is one tap away in either direction.
        found += contractionCompletions(prefix)
        return found.sortedByDescending { c -> score[c] ?: if ('\'' in c) CONTRACTION_SCORE else 0 }.take(limit)
    }

    /** The contractions that extend [prefix]. With the apostrophe already typed ("don'"), the contraction form must literally
     * start with it; without an apostrophe, the whole bare form must match ("doesnt", "were"), so a partial prefix like "don"
     * or "we" never drags in a contraction on its own.
     */
    private fun contractionCompletions(prefix: String): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val out = ArrayList<String>(2)
        for ((bare, form) in CONTRACTIONS) {
            val lower = form.lowercase()
            if (lower == prefix) continue   // already the whole word: nothing to complete
            if ('\'' in prefix) { if (lower.startsWith(prefix)) out += form }
            else if (bare == prefix) out += form
        }
        return out
    }

    /** What most often follows [previous]: first what the person themselves usually puts there, then the dictionary's commonest; the
     * commonest sentence openers when there is no usable previous word.
     */
    fun nextWords(previous: String?, limit: Int = 3, previous2: String? = null): List<String> {
        val mine = personal?.next(previous2, previous, limit).orEmpty()
        val own = previous?.lowercase()?.let { followers[it] }.orEmpty()
        return (mine + own + STARTERS).distinctBy { it.lowercase() }.take(limit)
    }

    /** Completions of [prefix] with the ones that often follow [previous] put first (and then the ones that follow [previous2]):
     * "see you at the sta" offers "station" before "state".
     */
    fun completionsAfter(prefix: String, previous: String?, limit: Int = 3, previous2: String? = null): List<String> {
        val follow = previous?.lowercase()?.let { followers[it] }.orEmpty()
        val follow2 = previous2?.lowercase()?.let { followers[it] }.orEmpty()
        if (follow.isEmpty() && follow2.isEmpty()) return completions(prefix, limit)
        val pool = completions(prefix, limit + 6)
        val inFollow = pool.filter { it in follow }.sortedBy { follow.indexOf(it) }
        val inFollow2 = pool.filter { it !in follow && it in follow2 }.sortedBy { follow2.indexOf(it) }
        val rest = pool.filter { it !in follow && it !in follow2 }
        return (inFollow + inFollow2 + rest).take(limit)
    }

    /** The word [typed] should be corrected to, or null to leave it alone. Case is not handled here (see [matchCase]).
     * [previous] is the word before it in the sentence, if any: a candidate that often follows it is preferred, which settles
     * "form"/"from" style ties by what the sentence is about, and a rare real word that is one slip from a word that fits
     * is fixed too ("i went to hte" is caught by the first rule; "ro the" by this one). [previous2] is the word before that,
     * a cheap trigram signal from the two bigram lists together (no extra dictionary data is needed).
     */
    fun correction(typed: String, previous: String? = null, previous2: String? = null, touches: List<Pt?>? = null,
        geometry: KeyGeometry? = null): String? {
        val t = typed.lowercase()
        if (t in INFORMAL) return null
        // One of the person's own words (a name, a place, their slang) is never corrected.
        if (t !in score && personal?.knows(t) == true) return null
        // A word already written with its apostrophe is correct: "don't" must never be "fixed" into something else.
        if ('\'' in t && t in CONTRACTION_WORDS) return null
        // Missing apostrophes are the commonest slip and are fixed outright - but only when the bare letters are not a real
        // word the person could have meant. "were", "lets", "its", "ill", "well", "wed", "shed" and "id" are words, so they
        // complete to the contraction in the lane instead of being replaced by it; a handful of rare real words ("cant" as
        // jargon, "wont" meaning accustomed) still correct because the contraction is what was meant.
        CONTRACTIONS[t]?.let { if (t !in score || t in CONTRACTION_OVERRIDES) return it }
        MISSPELLINGS[t]?.let { return it }
        val follow = previous?.lowercase()?.let { followers[it] }.orEmpty()
        val follow2 = previous2?.lowercase()?.let { followers[it] }.orEmpty()
        if (t in score) return contextFix(t, follow)
        if (t.length < 2) return null
        val maxCost = when { t.length <= 3 -> 1.0; t.length <= 5 -> 1.7; else -> 2.4 }
        // Where each letter was touched, when known: a tap near a key's edge makes the key beside it a cheap reading of it.
        val spatial: ((Int, Char) -> Double?)? = if (touches != null && geometry != null && geometry.usable && touches.size == t.length)
            { i, c -> TouchModel.substitutionCost(geometry, touches[i], c, FAR_SUBSTITUTION) } else null
        val firstLetters = buildSet {
            add(t[0]); if (t.length > 1) add(t[1])
            // A first letter that was touched near another key may have been meant for it.
            if (spatial != null) touches?.firstOrNull()?.let { p -> geometry!!.near(p, .9f).take(4).forEach(::add) }
        }
        val own = personal?.activeWords { it in score }.orEmpty()
        val buckets = firstLetters.mapNotNull { byFirst[it]?.asSequence()?.map { w -> w to (score[w] ?: 0) } } +
            listOf(own.asSequence().filter { it.first.firstOrNull() in firstLetters })
        var best: String? = null
        var bestValue = Double.NEGATIVE_INFINITY
        for (bucket in buckets) for ((cand, candScore) in bucket) {
            if (abs(cand.length - t.length) > 2) continue
            val cost = editCost(t, cand, maxCost, spatial)
            if (cost > maxCost) continue
            // Commoner words win, and every bit of edit cost counts against a candidate. A word that often follows the previous one
            // gets a bonus that is larger the nearer the top of that list it is; following both previous words earns a small extra.
            val rank = follow.indexOf(cand)
            val rank2 = follow2.indexOf(cand)
            // Two slips in one short word are much rarer than one, so cost counts for more the larger the share of the word it is:
            // "brwn" is "brown" (one vowel dropped), not "been" (half its letters wrong), however common "been" is.
            val weighted = cost * (1 + cost / t.length)
            val value = candScore / 10.0 - COST_WEIGHT * weighted +
                (if (rank >= 0) BIGRAM_BONUS - rank * .4 else 0.0) +
                (if (rank2 >= 0) TRIGRAM_BONUS - rank2 * .3 else 0.0)
            if (value > bestValue) { bestValue = value; best = cand }
        }
        // Two words run together ("ofthe"), or a space bar missed for a key just above it ("thisnis"): split, when that reads better.
        splitFix(t)?.let { (split, value) -> if (value > bestValue) return split }
        return best
    }

    /** The best way to read [t] as two common words, with its value on [correction]'s scale, or null when there is none. */
    private fun splitFix(t: String): Pair<String, Double>? {
        if (t.length < 4) return null
        fun ok(w: String) = (w.length >= 2 || w == "a" || w == "i") && (score[w] ?: 0) >= SPLIT_MIN_SCORE
        fun shown(w: String) = if (w == "i") "I" else w
        var best: Pair<String, Double>? = null
        fun consider(a: String, b: String, cost: Double) {
            if (!ok(a) || !ok(b)) return
            val bonus = if (followers[a]?.contains(b) == true) BIGRAM_BONUS else 0.0
            val value = minOf(score[a] ?: 0, score[b] ?: 0) / 10.0 - COST_WEIGHT * cost + bonus
            if (best == null || value > best!!.second) best = "${shown(a)} ${shown(b)}" to value
        }
        for (i in 1 until t.length) consider(t.substring(0, i), t.substring(i), .9)
        for (i in 1 until t.length - 1) if (t[i] in SPACE_NEIGHBOURS) consider(t.substring(0, i), t.substring(i + 1), .8)
        return best
    }

    /** A real word that is a single slip from a word that both fits the sentence and is far commoner: "ro the" for "to the". Never fires
     * without a previous word, and only on slips of a neighbouring key, a swap or a doubled letter, so rare real words stay as typed.
     */
    private fun contextFix(t: String, follow: List<String>): String? {
        if (follow.isEmpty() || t in follow || t.length > 6) return null
        val own = score[t] ?: 0
        return follow.firstOrNull { cand ->
            cand != t && (score[cand] ?: 0) >= own + REAL_WORD_GAP && abs(cand.length - t.length) <= 1 && editCost(t, cand, .9) <= .9
        }
    }

    /** Damerau-Levenshtein distance where a slip to a neighbouring key and a swap of two letters are cheaper than a
     * random change. Returns something greater than [limit] as soon as it cannot finish within it.
     */
    internal fun editCost(a: String, b: String, limit: Double, spatial: ((Int, Char) -> Double?)? = null): Double {
        val neighbours = neighbours
        val n = a.length; val m = b.length
        if (abs(n - m) > limit) return limit + 1
        var prevPrev = DoubleArray(m + 1); var prev = DoubleArray(m + 1) { it.toDouble() }; var cur = DoubleArray(m + 1)
        for (i in 1..n) {
            cur[0] = i.toDouble()
            var rowMin = cur[0]
            for (j in 1..m) {
                val sub = if (a[i - 1] == b[j - 1]) 0.0 else spatial?.invoke(i - 1, b[j - 1])
                    ?: if (neighbours[a[i - 1]]?.contains(b[j - 1]) == true) 0.6 else FAR_SUBSTITUTION
                // A letter missing from the typed word that doubles one next to it ("helo" for "hello") is the commonest
                // slip of all, so it costs less than a missing letter elsewhere.
                // So is a vowel left out ("brwn", "ovr", "peple"): fast thumbs skip them, and the consonants still spell the word.
                val missing = if ((j >= 2 && b[j - 1] == b[j - 2]) || (j < m && b[j - 1] == b[j])) 0.5 else if (b[j - 1] in VOWELS) 0.55 else 1.0
                // A letter typed that was not meant: a finger that touched two keys at once (it sits next to the letter before or after it)
                // or a doubled letter is a likelier slip than any other extra letter.
                val extra = if (a[i - 1] == a.getOrNull(i - 2) || a[i - 1] == a.getOrNull(i)) .5
                    else if ((i > 1 && neighbours[a[i - 1]]?.contains(a[i - 2]) == true) || (i < n && neighbours[a[i - 1]]?.contains(a[i]) == true)) .6
                    else 1.0
                var v = min(min(prev[j] + extra, cur[j - 1] + missing), prev[j - 1] + sub)
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
        /** What a word that follows the previous one is worth, in the same units as the score divided by ten (the first of five is worth most). */
        private const val BIGRAM_BONUS = 2.4
        /** The smaller extra a word earns for following the word before the previous one too (a two-word context, no new data). */
        private const val TRIGRAM_BONUS = 1.2
        /** How much commoner (in score points, tenths of a log) a word must be to replace a real word that does not fit the sentence. */
        private const val REAL_WORD_GAP = 35
        /** How common (in score points) both halves of a split must be: everyday words only, so a rare word is never cut in two. */
        private const val SPLIT_MIN_SCORE = 150
        /** The keys just above the space bar, which a thumb aiming for it can catch instead. */
        private const val SPACE_NEIGHBOURS = "cvbnm"
        private const val VOWELS = "aeiou"
        private const val CACHED_PREFIX = 3
        private const val CACHED_LIMIT = 4
        /** Swapping a letter for one far away on the keyboard is a less likely slip than leaving a letter out, so it costs more. */
        private const val FAR_SUBSTITUTION = 1.3

        /** Every common English contraction: the bare letters typed without the apostrophe to the form with it put back. This
         * table is the single source of truth. Autocorrect reads it (a bare form that is not a real word is fixed outright), the
         * completion lane reads it ("don'" completes to "don't", and "doesnt"/"were" complete to "doesn't"/"we're"), and `contains`
         * reads it so a contraction is a known word in both directions. Bare forms that are real, common words are never
         * auto-replaced - the word the person typed is kept and the contraction is offered in the lane instead (see
         * [CONTRACTION_OVERRIDES] for the few rare words that still correct).
         */
        val CONTRACTIONS = mapOf(
            // "-n't": the missing apostrophe is the commonest slip of all.
            "dont" to "don't", "doesnt" to "doesn't", "didnt" to "didn't", "cant" to "can't", "wont" to "won't",
            "isnt" to "isn't", "arent" to "aren't", "wasnt" to "wasn't", "werent" to "weren't", "couldnt" to "couldn't",
            "wouldnt" to "wouldn't", "shouldnt" to "shouldn't", "hasnt" to "hasn't", "havent" to "haven't", "hadnt" to "hadn't",
            "mustnt" to "mustn't", "neednt" to "needn't", "shant" to "shan't", "aint" to "ain't", "mightnt" to "mightn't",
            "oughtnt" to "oughtn't", "darent" to "daren't",
            // Pronoun + am/is/are/have/had/will/would.
            "im" to "I'm", "ive" to "I've",
            "youre" to "you're", "youve" to "you've", "youll" to "you'll", "youd" to "you'd",
            "hes" to "he's", "shes" to "she's", "hed" to "he'd",
            "weve" to "we've",
            "theyre" to "they're", "theyve" to "they've", "theyd" to "they'd", "theyll" to "they'll",
            "itll" to "it'll", "itd" to "it'd",
            "thats" to "that's", "thatll" to "that'll", "thatd" to "that'd",
            "whats" to "what's", "whatre" to "what're", "whatll" to "what'll", "whatd" to "what'd",
            "whos" to "who's", "wholl" to "who'll", "whove" to "who've", "whod" to "who'd",
            "hows" to "how's", "howre" to "how're",
            "theres" to "there's", "heres" to "here's", "wheres" to "where's", "therell" to "there'll",
            // "... have" shortened to "'ve".
            "wouldve" to "would've", "couldve" to "could've", "shouldve" to "should've", "mightve" to "might've", "mustve" to "must've",
            // People and phrases.
            "yall" to "y'all", "oclock" to "o'clock", "maam" to "ma'am",
            "whyre" to "why're",
            // The pronoun capitalised on its own.
            "i" to "I",
            // The bare form is a real, common word: never auto-replaced, only offered as a completion so the word is not stolen.
            "id" to "I'd", "its" to "it's", "ill" to "I'll", "were" to "we're", "well" to "we'll",
            "lets" to "let's", "wed" to "we'd", "shed" to "she'd")

        /** Bare forms that are real dictionary words but so rare that the contraction is what was meant ("cant" as jargon,
         * "wont" meaning accustomed, "whats"/"hows"/"wheres" as the plural nouns): these are still auto-replaced. Every other
         * dictionary-word bare form above ("id", "its", "ill", "were", "well", "lets", "wed", "shed") is only offered as a
         * completion, so a real word the person typed is never stolen.
         */
        private val CONTRACTION_OVERRIDES = setOf("i", "cant", "wont", "hes", "shes", "whats", "wheres", "hows")

        /** How common a contraction is treated as for ranking completions: comparable to "here"/"does" (192), a very common
         * word, so "don'" offers "don't" ahead of anything rarer that happens to start the same way.
         */
        private const val CONTRACTION_SCORE = 190

        /** Slips that edit distance cannot reach, or reaches the wrong word for, and the habitual misspellings that are two or three keys
         * off. These are fixed outright; they are all words nobody types on purpose.
         */
        val MISSPELLINGS = mapOf(
            "alot" to "a lot", "tommorow" to "tomorrow", "tomorow" to "tomorrow", "tommorrow" to "tomorrow", "untill" to "until", "wierd" to "weird",
            "adress" to "address", "beleive" to "believe", "calender" to "calendar", "collegue" to "colleague", "comming" to "coming",
            "concious" to "conscious", "definatly" to "definitely", "definitly" to "definitely", "diffrent" to "different",
            "enviroment" to "environment", "excercise" to "exercise", "existance" to "existence", "foriegn" to "foreign", "goverment" to "government",
            "gaurd" to "guard", "happend" to "happened", "immediatly" to "immediately", "independant" to "independent", "knowlege" to "knowledge",
            "libary" to "library", "lisence" to "license", "maintainance" to "maintenance", "neccessary" to "necessary", "necesary" to "necessary",
            "noticable" to "noticeable", "occassion" to "occasion", "ocurred" to "occurred", "persistant" to "persistent", "posession" to "possession",
            "prefered" to "preferred", "publically" to "publicly", "realy" to "really", "reccomend" to "recommend", "recomend" to "recommend",
            "refered" to "referred", "relevent" to "relevant", "resturant" to "restaurant", "rythm" to "rhythm", "scedule" to "schedule",
            "sieze" to "seize", "succesful" to "successful", "sucessful" to "successful", "supress" to "suppress", "tendancy" to "tendency",
            "truely" to "truly", "usualy" to "usually", "wether" to "whether", "whereever" to "wherever", "writting" to "writing", "yeild" to "yield",
            "thankyou" to "thank you", "infact" to "in fact", "incase" to "in case", "aswell" to "as well", "eachother" to "each other", "atleast" to "at least")

        /** Informal words people type on purpose that the word list may not have; never "corrected" into something else. */
        val INFORMAL: Set<String> = setOf("lol", "omg", "tbh", "idk", "btw", "imo", "imho", "thx", "pls", "plz", "ok", "okay", "yeah", "yep", "yup", "nope",
            "hmm", "wow", "ugh", "ya", "gonna", "wanna", "gotta", "kinda", "sorta", "lemme", "gimme", "dunno", "ty", "np", "brb", "irl", "fyi", "asap",
            "wtf", "lmao", "rofl", "smh", "ikr", "afaik", "eta", "diy", "faq", "pov", "rn", "ofc", "nvm", "jk", "bff", "yw")

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
