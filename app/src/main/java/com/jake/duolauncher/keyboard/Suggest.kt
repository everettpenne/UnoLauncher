package com.jake.duolauncher.keyboard

/** What the text just before the cursor says about the word being typed. */
internal data class WordContext(
    /** The letters (and apostrophes) typed since the last space or punctuation; empty between words. */
    val current: String,
    /** The word before it in the same sentence, or null at a sentence or line start. */
    val previous: String?,
    /** The word before [previous] in the same sentence, or null. A cheap stand-in for trigram context. */
    val previous2: String?,
    /** False inside things that are not prose: addresses, handles, hashtags, anything with digits or dots in the token. */
    val plainWord: Boolean,
    val atSentenceStart: Boolean,
)

/** What a slot in the strip holds: the word as typed, the correction the space bar will make, a completion, a likely next word, an emoji
 * for the word, the word an autocorrection replaced (to put it back), another reading of a swipe, or the latest thing copied.
 */
internal enum class SuggestionKind { TYPED, CORRECTION, COMPLETION, NEXT, EMOJI, REVERT, GESTURE, CLIP }

internal data class Suggestion(val text: String, val kind: SuggestionKind)

internal object Suggest {
    private fun isWordChar(c: Char) = c.isLetter() || c == '\'' || c == '’'
    private val OPENERS = setOf('(', '"', '“', '[', '‘', '*')

    fun parse(before: CharSequence): WordContext {
        val text = before.takeLast(80).toString()
        var end = text.length
        var start = end
        while (start > 0 && isWordChar(text[start - 1])) start--
        // Leading apostrophes belong to quotation, not to the word.
        while (start < end && (text[start] == '\'' || text[start] == '’')) start++
        val current = text.substring(start, end).replace('’', '\'')
        // Plain prose only: the character before the word must be a space, a line break, an opener, or nothing.
        val beforeWord = text.getOrNull(start - 1)
        val plain = beforeWord == null || beforeWord.isWhitespace() || beforeWord in OPENERS
        // The previous word counts only when separated by plain spaces, so a full stop, comma or new line starts afresh.
        var i = start
        while (i > 0 && text[i - 1] == ' ') i--
        var previous: String? = null
        var previous2: String? = null
        var atStart = i == 0 || text[i - 1] in ".!?\n"
        if (i < start && i > 0 && isWordChar(text[i - 1])) {
            var ps = i
            while (ps > 0 && isWordChar(text[ps - 1])) ps--
            previous = text.substring(ps, i).replace('’', '\'').trim('\'')
            atStart = false
            // The word before that, for a two-word context: also only across plain spaces, so punctuation still ends it.
            var q = ps
            while (q > 0 && text[q - 1] == ' ') q--
            if (q > 0 && isWordChar(text[q - 1])) {
                var qs = q
                while (qs > 0 && isWordChar(text[qs - 1])) qs--
                previous2 = text.substring(qs, q).replace('’', '\'').trim('\'')
            }
        }
        return WordContext(current, previous?.takeIf { it.isNotEmpty() }, previous2?.takeIf { it.isNotEmpty() }, plain, atStart)
    }

    /** The corrected text to put in place of [ctx]'s current word, or null to leave it. [ignored] holds words the person
     * has undone a correction of this session. [capitalise] puts a capital on the word where one belongs (a sentence start in
     * a field with sentence capitalisation), even when it was typed in lower case.
     */
    fun autocorrect(engine: WordEngine, ctx: WordContext, ignored: Set<String>, capitalise: Boolean = false, touches: List<Pt?>? = null,
        geometry: KeyGeometry? = null): String? {
        val typed = ctx.current
        if (!ctx.plainWord || typed.isEmpty()) return null
        if (typed.lowercase() in ignored) return null
        // A learned word written with its own capitals ("Everett", "iPhone") gets them back when typed in lower case.
        // Never for a dictionary word: "Will" typed as a name must not capitalise every "will".
        if (typed.none { it.isUpperCase() } && !engine.inDictionary(typed)) engine.personal?.formOf(typed)?.let { form ->
            val cased = if (capitalise) form.replaceFirstChar { it.uppercase() } else form
            return cased.takeIf { it != typed }
        }
        // Names and brands like "iPhone" or "McDonald" and acronyms typed deliberately are left alone; plain Title or ALL CAPS get fixed.
        val interior = typed.drop(1)
        if (interior.any { it.isUpperCase() } && !typed.all { it.isUpperCase() || !it.isLetter() }) return null
        // A capitalised word in the middle of a sentence that is not in the word list is most likely a name or a place, not a slip.
        val midSentenceName = typed.first().isUpperCase() && typed.length > 1 && typed.drop(1).none { it.isUpperCase() } &&
            !ctx.atSentenceStart && ctx.previous != null && !engine.contains(typed.lowercase())
        if (midSentenceName) return null
        val fix = engine.correction(typed, ctx.previous, ctx.previous2, touches, geometry) ?: return null
        var cased = WordEngine.matchCase(typed, fix)
        if (capitalise && cased.isNotEmpty()) cased = cased.replaceFirstChar { it.uppercase() }
        return cased.takeIf { it != typed }
    }

    /** Up to three suggestions: the word as typed, the best correction or completion, and an alternative. Between words,
     * likely next words. Capitalised if [capitalise] (a sentence start or shift).
     */
    fun suggestions(engine: WordEngine, ctx: WordContext, ignored: Set<String>, capitalise: Boolean,
        fix: String? = if (ctx.current.isEmpty()) null else autocorrect(engine, ctx, ignored)): List<Suggestion> {
        if (!ctx.plainWord) return emptyList()
        val cap = { w: String -> if (capitalise) w.replaceFirstChar { it.uppercase() } else w }
        if (ctx.current.isEmpty()) return engine.nextWords(ctx.previous, 3, ctx.previous2).map { Suggestion(cap(it), SuggestionKind.NEXT) }
        val typed = ctx.current
        val out = ArrayList<Suggestion>(3)
        out += Suggestion(typed, SuggestionKind.TYPED)
        if (fix != null) out += Suggestion(fix, SuggestionKind.CORRECTION)
        for (c in engine.completionsAfter(typed.lowercase(), ctx.previous, previous2 = ctx.previous2, limit = 4)) {
            if (out.size >= 3) break
            val shown = WordEngine.matchCase(typed, c)
            if (out.none { it.text.equals(shown, ignoreCase = true) }) out += Suggestion(shown, SuggestionKind.COMPLETION)
        }
        // With a correction, the typed word moves to the left and the correction sits in the middle, where the eye expects it.
        return out
    }

    /** Puts an emoji for the word in the last slot (the word being typed, or the one just finished), as Gboard and iOS do. */
    fun withEmoji(list: List<Suggestion>, ctx: WordContext, index: EmojiIndex?): List<Suggestion> {
        val word = ctx.current.ifEmpty { ctx.previous ?: return list }
        val emoji = index?.forWord(word)?.firstOrNull() ?: return list
        return list.take(2) + Suggestion(emoji, SuggestionKind.EMOJI)
    }
}
