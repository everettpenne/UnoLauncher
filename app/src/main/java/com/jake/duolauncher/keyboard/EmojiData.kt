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

/** The emoji used this session, most recent first. It lives in memory only and is gone when the keyboard process is: nothing
 * you type is kept, which matters more here than a shortcut.
 */
internal class EmojiRecents(private val cap: Int = 32) {
    private val recent = ArrayList<String>()
    fun add(emoji: String) { recent.remove(emoji); recent.add(0, emoji); while (recent.size > cap) recent.removeAt(recent.lastIndex) }
    fun list(): List<String> = recent.toList()
    val isEmpty get() = recent.isEmpty()
}
