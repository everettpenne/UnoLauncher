package com.jake.duolauncher.keyboard

/** What the text just before the cursor says about the word being typed. */
internal data class WordContext(
    /** The letters (and apostrophes) typed since the last space or punctuation; empty between words. */
    val current: String,
    /** The word before it in the same sentence, or null at a sentence or line start. */
    val previous: String?,
    /** False inside things that are not prose: addresses, handles, hashtags, anything with digits or dots in the token. */
    val plainWord: Boolean,
    val atSentenceStart: Boolean,
)

internal enum class SuggestionKind { TYPED, CORRECTION, COMPLETION, NEXT }

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
        var atStart = i == 0 || text[i - 1] in ".!?\n"
        if (i < start && i > 0 && isWordChar(text[i - 1])) {
            var ps = i
            while (ps > 0 && isWordChar(text[ps - 1])) ps--
            previous = text.substring(ps, i).replace('’', '\'').trim('\'')
            atStart = false
            if (ps > 0 && text[ps - 1] !in " \n(\"") { /* previous word is part of something like an address: still a word to us */ }
        }
        return WordContext(current, previous?.takeIf { it.isNotEmpty() }, plain, atStart)
    }

    /** The corrected text to put in place of [ctx]'s current word, or null to leave it. [ignored] holds words the person
     * has undone a correction of this session.
     */
    fun autocorrect(engine: WordEngine, ctx: WordContext, ignored: Set<String>): String? {
        val typed = ctx.current
        if (!ctx.plainWord || typed.isEmpty()) return null
        if (typed.lowercase() in ignored) return null
        // Names and brands like "iPhone" or "McDonald" and acronyms typed deliberately are left alone; plain Title or ALL CAPS get fixed.
        val interior = typed.drop(1)
        if (interior.any { it.isUpperCase() } && !typed.all { it.isUpperCase() || !it.isLetter() }) return null
        val fix = engine.correction(typed) ?: return null
        val cased = WordEngine.matchCase(typed, fix)
        return cased.takeIf { it != typed }
    }

    /** Up to three suggestions: the word as typed, the best correction or completion, and an alternative. Between words,
     * likely next words. Capitalised if [capitalise] (a sentence start or shift).
     */
    fun suggestions(engine: WordEngine, ctx: WordContext, ignored: Set<String>, capitalise: Boolean): List<Suggestion> {
        if (!ctx.plainWord) return emptyList()
        val cap = { w: String -> if (capitalise) w.replaceFirstChar { it.uppercase() } else w }
        if (ctx.current.isEmpty()) return engine.nextWords(ctx.previous, 3).map { Suggestion(cap(it), SuggestionKind.NEXT) }
        val typed = ctx.current
        val out = ArrayList<Suggestion>(3)
        val fix = autocorrect(engine, ctx, ignored)
        out += Suggestion(typed, SuggestionKind.TYPED)
        if (fix != null) out += Suggestion(fix, SuggestionKind.CORRECTION)
        for (c in engine.completions(typed.lowercase(), 4)) {
            if (out.size >= 3) break
            val shown = WordEngine.matchCase(typed, c)
            if (out.none { it.text.equals(shown, ignoreCase = true) }) out += Suggestion(shown, SuggestionKind.COMPLETION)
        }
        // With a correction, the typed word moves to the left and the correction sits in the middle, where the eye expects it.
        return out
    }
}
