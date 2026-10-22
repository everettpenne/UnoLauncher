package com.jake.duolauncher.keyboard

/** One arrangement of the letter keys. [rows] are the three letter rows, top first; the third row sits between Shift and Delete.
 * [english] says the bundled English word list fits it, which is what turns on autocorrect, completions from the dictionary and swipe
 * typing; on any other layout the keyboard still suggests the words it has learned from you, and fixes nothing on its own.
 * [label] is the short name the space bar shows when more than one layout is turned on.
 */
internal data class KeyLayout(val id: String, val name: String, val label: String, val rows: List<String>, val english: Boolean = false) {
    /** Every letter on the layout, lower case. */
    val letters: Set<Char> by lazy { rows.joinToString("").filter { it.isLetter() }.toSet() }
}

/** The layouts the keyboard offers (Fossify Keyboard's Latin ones and its commonest others), and the key-neighbour map each implies.
 * Pure Kotlin, no Android classes, so it is unit-tested.
 */
internal object KeyLayouts {
    val QWERTY = KeyLayout("qwerty", "English (QWERTY)", "EN", listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"), english = true)
    val ALL: List<KeyLayout> = listOf(
        QWERTY,
        KeyLayout("dvorak", "English (Dvorak)", "DV", listOf("',.pyfgcrl", "aoeuidhtns", "qjkxbmwvz"), english = true),
        KeyLayout("colemak", "English (Colemak)", "CM", listOf("qwfpgjluy", "arstdhneio", "zxcvbkm"), english = true),
        KeyLayout("colemak_dh", "English (Colemak-DH)", "DH", listOf("qwfpbjluy", "arstgmneio", "zxcdvkh"), english = true),
        KeyLayout("workman", "English (Workman)", "WM", listOf("qdrwbjfup", "ashtgyneoi", "zxmcvkl"), english = true),
        KeyLayout("qwertz", "Deutsch (QWERTZ)", "DE", listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnm")),
        KeyLayout("azerty", "Français (AZERTY)", "FR", listOf("azertyuiop", "qsdfghjklm", "wxcvbn'")),
        KeyLayout("spanish", "Español", "ES", listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm")),
        KeyLayout("portuguese", "Português", "PT", listOf("qwertyuiop", "asdfghjklç", "zxcvbnm")),
        KeyLayout("italian", "Italiano", "IT", listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")),
        KeyLayout("swedish", "Svenska / Suomi", "SV", listOf("qwertyuiopå", "asdfghjklöä", "zxcvbnm")),
        KeyLayout("danish", "Dansk / Norsk", "DA", listOf("qwertyuiopå", "asdfghjklæø", "zxcvbnm")),
        KeyLayout("turkish", "Türkçe (Q)", "TR", listOf("qwertyuıopğü", "asdfghjklşi", "zxcvbnmöç")),
        KeyLayout("polish", "Polski", "PL", listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")),
        KeyLayout("russian", "Русский", "RU", listOf("йцукенгшщзх", "фывапролджэ", "ячсмитьбю")),
        KeyLayout("ukrainian", "Українська", "UK", listOf("йцукенгшщзх", "фівапролджє", "ячсмитьбю")),
        KeyLayout("greek", "Ελληνικά", "EL", listOf("ςερτυθιοπ", "ασδφγηξκλ", "ζχψωβνμ")),
    )

    fun byId(id: String): KeyLayout? = ALL.firstOrNull { it.id == id }

    /** The layouts named in a stored setting (comma separated ids), in that order; QWERTY when none is recognised. */
    fun parse(setting: String?): List<KeyLayout> =
        setting.orEmpty().split(',').mapNotNull { byId(it.trim()) }.distinct().ifEmpty { listOf(QWERTY) }

    fun encode(layouts: List<KeyLayout>): String = layouts.joinToString(",") { it.id }

    /** Which keys sit next to which: the keys either side in a row, and (rows being offset by about half a key) the two nearest above and
     * below. Rows of different lengths are centred, as they are drawn.
     */
    fun neighbours(layout: KeyLayout): Map<Char, Set<Char>> = buildMap {
        val rows = layout.rows
        fun centre(r: Int, c: Int) = c + (rows.maxOf { it.length } - rows[r].length) / 2f
        rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, ch ->
                val near = mutableSetOf<Char>()
                row.getOrNull(c - 1)?.let { near += it }; row.getOrNull(c + 1)?.let { near += it }
                for (dr in listOf(-1, 1)) {
                    val other = rows.getOrNull(r + dr) ?: continue
                    other.forEachIndexed { oc, o -> if (kotlin.math.abs(centre(r + dr, oc) - centre(r, c)) <= 1f) near += o }
                }
                put(ch, near)
            }
        }
    }

    /** What long-pressing a key in the top letter row offers first: its digit, as on Fossify Keyboard, so a number never needs the
     * numbers page. The tenth key and beyond have none past 0.
     */
    fun digitFor(layout: KeyLayout, letter: Char): String? {
        val i = layout.rows.first().indexOf(letter.lowercaseChar())
        return if (i in 0..9) ((i + 1) % 10).toString() else null
    }
}
