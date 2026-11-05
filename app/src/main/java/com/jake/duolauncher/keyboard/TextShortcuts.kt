package com.jake.duolauncher.keyboard

/**
 * Text Replacement, as iOS has it: a short thing typed ("omw") becomes a phrase ("On my way!") when the space bar ends it. The phrase is
 * offered in the lane while the shortcut is being typed, the space bar puts it in, and Delete straight after puts the shortcut back, as it
 * does for an autocorrection. Kept on the phone in the keyboard's settings ("kbShortcuts"), one `shortcut<TAB>phrase` a line. Pure Kotlin.
 */
internal object TextShortcuts {
    const val PREF = "kbShortcuts"
    /** What a new keyboard starts with, as iOS does. */
    val DEFAULTS = linkedMapOf("omw" to "On my way!")

    /** The shortcuts in [stored], or [DEFAULTS] when nothing has been stored yet (an empty string is a deliberately empty list). */
    fun parse(stored: String?): LinkedHashMap<String, String> {
        if (stored == null) return LinkedHashMap(DEFAULTS)
        val out = LinkedHashMap<String, String>()
        for (line in stored.split('\n')) {
            val tab = line.indexOf('\t')
            if (tab <= 0) continue
            val key = clean(line.substring(0, tab)); val phrase = line.substring(tab + 1).trim()
            if (key.isNotEmpty() && phrase.isNotEmpty()) out[key] = phrase
        }
        return out
    }

    fun serialize(shortcuts: Map<String, String>): String = shortcuts.entries.joinToString("\n") { (k, v) -> "$k\t${v.replace('\n', ' ').replace('\t', ' ')}" }

    /** A shortcut is matched without regard to case and must be one word of letters (and apostrophes), as the keyboard reads words. */
    fun clean(shortcut: String): String = shortcut.trim().lowercase().replace('’', '\'').filter { it.isLetter() || it == '\'' }

    /** The phrase [typed] stands for, or null. A shortcut typed with a capital (at a sentence start) gives the phrase a capital too. */
    fun expand(typed: String, shortcuts: Map<String, String>): String? {
        if (typed.isEmpty()) return null
        val phrase = shortcuts[typed.lowercase().replace('’', '\'')] ?: return null
        return if (typed[0].isUpperCase() && phrase[0].isLowerCase()) phrase.replaceFirstChar { it.uppercase() } else phrase
    }
}
