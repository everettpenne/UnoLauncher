package com.jake.duolauncher.keyboard

/** One page of the emoji panel: a name, the emoji shown on its tab, and the emoji. */
internal data class EmojiCategory(val name: String, val tab: String, val items: List<String>)

/** The emoji panel's data. Pure Kotlin, parsed from the bundled `keyboard/emoji.txt`; nothing is fetched, nothing is learned. */
internal object EmojiData {
    /** Lines are either a header `# Name|tab` or emoji separated by spaces. Other `#` lines and blank lines are comments. */
    fun parse(lines: Sequence<String>): List<EmojiCategory> {
        val out = ArrayList<EmojiCategory>()
        var name: String? = null; var tab = ""; var items = ArrayList<String>()
        fun flush() { name?.let { if (items.isNotEmpty()) out += EmojiCategory(it, tab, items) }; items = ArrayList() }
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#")) {
                val header = line.removePrefix("#").trim()
                val bar = header.indexOf('|')
                if (bar > 0) { flush(); name = header.substring(0, bar).trim(); tab = header.substring(bar + 1).trim() }
                continue
            }
            if (name != null) items += line.split(' ').filter { it.isNotBlank() }
        }
        flush()
        return out
    }
}

/** The emoji used lately, most recent first. Kept on the phone with the learned words (and so only while learning is on); see
 * [PersonalStore].
 */
internal class EmojiRecents(private val cap: Int = 32) {
    private val recent = ArrayList<String>()
    fun add(emoji: String) { recent.remove(emoji); recent.add(0, emoji); while (recent.size > cap) recent.removeAt(recent.lastIndex) }
    fun list(): List<String> = recent.toList()
    val isEmpty get() = recent.isEmpty()
    fun restore(list: List<String>) { recent.clear(); recent += list.take(cap) }
    fun clear() = recent.clear()
}

/** An emoji's name and the words people use for it (Unicode CLDR's English annotations), and whether it takes a skin tone. */
internal data class EmojiInfo(val emoji: String, val name: String, val keywords: List<String>, val toneable: Boolean)

/** Emoji by word: what the strip offers after "pizza" or "love", what the emoji panel's search finds, and which emoji take a skin
 * tone. Parsed from the bundled `keyboard/emoji_words.txt`. Pure Kotlin.
 */
internal class EmojiIndex(val all: List<EmojiInfo>) {
    private val byEmoji = all.associateBy { it.emoji }
    /** Word to emoji, for suggestions. A word shared by many emoji ("face", "hand") says too little to suggest any of them, so it is left out. */
    private val byWord: Map<String, List<String>> = run {
        val m = HashMap<String, MutableList<String>>()
        for (e in all) {
            val nameWords = e.name.lowercase().split(' ', '-').filter { it.length >= 3 }
            // The name's own words come first, so "heart" suggests the red heart before the many hearts that mention it.
            for (w in nameWords + e.keywords) if (w.length >= 3) m.getOrPut(w) { ArrayList() }.let { if (e.emoji !in it) it += e.emoji }
        }
        m.filterValues { it.size <= MAX_PER_WORD }
    }

    /** Emoji for a typed [word] (up to [limit]), or none. */
    fun forWord(word: String, limit: Int = 1): List<String> = byWord[word.lowercase().trim('\'')].orEmpty().take(limit)

    /** Emoji whose name or keywords start with every word of [query], names first: the emoji panel's search. */
    fun search(query: String, limit: Int = 48): List<String> {
        val terms = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        fun matches(e: EmojiInfo, t: String) = e.name.lowercase().split(' ', '-').any { it.startsWith(t) } || e.keywords.any { it.startsWith(t) }
        return all.filter { e -> terms.all { matches(e, it) } }
            .sortedByDescending { e -> e.name.lowercase().split(' ').count { w -> terms.any { w.startsWith(it) } } }
            .take(limit).map { it.emoji }
    }

    fun toneable(emoji: String) = byEmoji[emoji]?.toneable == true || byEmoji[emoji.replace("️", "")]?.toneable == true

    companion object {
        private const val MAX_PER_WORD = 6
        /** The five Fitzpatrick modifiers, light to dark. */
        val TONES = listOf("🏻", "🏼", "🏽", "🏾", "🏿")

        /** [emoji] with skin [tone] (one of [TONES]) after its first character, the way Unicode composes them; a variation selector goes. */
        fun withTone(emoji: String, tone: String): String {
            val base = emoji.replace("️", "")
            if (base.isEmpty()) return emoji
            val first = Character.charCount(base.codePointAt(0))
            return base.substring(0, first) + tone + base.substring(first)
        }

        /** Lines of `emoji<TAB>name<TAB>keywords<TAB>t`; `#` lines are comments. */
        fun parse(lines: Sequence<String>): EmojiIndex = EmojiIndex(lines.filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 3) null else EmojiInfo(f[0], f[1], f[2].split(' ').filter { it.isNotBlank() }, f.getOrNull(3) == "t")
        }.toList())
    }
}
