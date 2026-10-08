package com.jake.duolauncher.keyboard

/** Everything about how the keyboard types, with no Android classes, so it is unit-tested. The keyboard code in this
 * package never touches the network: a test (`KeyboardPrivacyTest`) fails the build if it ever imports anything that
 * could.
 */
internal enum class KeyPage { LETTERS, NUMBERS, SYMBOLS }

internal enum class ShiftState { OFF, ONCE, LOCKED }

internal sealed interface Key {
    /** A key that types [output] (and is labelled with [label]). */
    data class Char(val label: String, val output: String = label, val weight: Float = 1f) : Key
    data object Shift : Key
    data object Backspace : Key
    data class Page(val target: KeyPage, val label: String) : Key
    data object Space : Key
    data object Enter : Key
    data object Globe : Key
}

/** What the Enter key should do and say, from the field's IME action. */
internal enum class EnterKind(val label: String, val sendsAction: Boolean) {
    RETURN("return", false), GO("Go", true), SEARCH("Search", true), SEND("Send", true), NEXT("Next", true), DONE("Done", true)
}

internal object KeyboardModel {
    private const val IME_MASK = 0xff
    // android.view.inputmethod.EditorInfo values, repeated so this file stays free of Android classes.
    private const val IME_ACTION_GO = 2; private const val IME_ACTION_SEARCH = 3; private const val IME_ACTION_SEND = 4
    private const val IME_ACTION_NEXT = 5; private const val IME_ACTION_DONE = 6; private const val IME_FLAG_NO_ENTER_ACTION = 0x40000000

    const val TYPE_MASK_CLASS = 0xf; const val TYPE_CLASS_NUMBER = 2; const val TYPE_CLASS_PHONE = 3; const val TYPE_CLASS_TEXT = 1
    private const val TYPE_MASK_VARIATION = 0xff0
    private val PASSWORD_VARIATIONS = setOf(0x80, 0x90, 0xe0)  // password, visible password, web password
    private const val NUMBER_PASSWORD = 0x10

    fun enterKind(imeOptions: Int): EnterKind {
        if (imeOptions and IME_FLAG_NO_ENTER_ACTION != 0) return EnterKind.RETURN
        return when (imeOptions and IME_MASK) {
            IME_ACTION_GO -> EnterKind.GO
            IME_ACTION_SEARCH -> EnterKind.SEARCH
            IME_ACTION_SEND -> EnterKind.SEND
            IME_ACTION_NEXT -> EnterKind.NEXT
            IME_ACTION_DONE -> EnterKind.DONE
            else -> EnterKind.RETURN
        }
    }

    /** Numbers and phone fields open straight on the number page. */
    fun startPage(inputType: Int): KeyPage = when (inputType and TYPE_MASK_CLASS) {
        TYPE_CLASS_NUMBER, TYPE_CLASS_PHONE -> KeyPage.NUMBERS
        else -> KeyPage.LETTERS
    }

    /** Password fields must never show a magnified preview of the key just pressed (someone watching sees it). */
    fun isPassword(inputType: Int): Boolean {
        val cls = inputType and TYPE_MASK_CLASS
        val variation = inputType and TYPE_MASK_VARIATION
        return (cls == TYPE_CLASS_TEXT && variation in PASSWORD_VARIATIONS) || (cls == TYPE_CLASS_NUMBER && variation == NUMBER_PASSWORD)
    }

    private val letterRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val numberRows = listOf("1234567890", "-/:;()$&@\"", ".,?!'")
    private val symbolRows = listOf("[]{}#%^*+=", "_\\|~<>€£¥•", ".,?!'")

    private fun chars(s: String, upper: Boolean = false) = s.map { Key.Char(if (upper) it.uppercase() else it.toString()) }

    /** The four rows for [page]. Letters uppercase while shift is on. */
    fun rows(page: KeyPage, shift: ShiftState): List<List<Key>> = when (page) {
        KeyPage.LETTERS -> {
            val up = shift != ShiftState.OFF
            listOf(chars(letterRows[0], up), chars(letterRows[1], up),
                listOf<Key>(Key.Shift) + chars(letterRows[2], up) + Key.Backspace)
        }
        KeyPage.NUMBERS -> listOf(chars(numberRows[0]), chars(numberRows[1]),
            listOf<Key>(Key.Page(KeyPage.SYMBOLS, "#+=")) + chars(numberRows[2]) + Key.Backspace)
        KeyPage.SYMBOLS -> listOf(chars(symbolRows[0]), chars(symbolRows[1]),
            listOf<Key>(Key.Page(KeyPage.NUMBERS, "123")) + chars(symbolRows[2]) + Key.Backspace)
    } + listOf(bottomRow(page))

    private fun bottomRow(page: KeyPage): List<Key> = listOf(
        if (page == KeyPage.LETTERS) Key.Page(KeyPage.NUMBERS, "123") else Key.Page(KeyPage.LETTERS, "ABC"),
        Key.Globe, Key.Space, Key.Enter)

    /** Shift after tapping the shift key: off to once to locked and back. A quick second tap locks caps. */
    fun tapShift(state: ShiftState, doubleTap: Boolean): ShiftState = when {
        state == ShiftState.LOCKED -> ShiftState.OFF
        doubleTap -> ShiftState.LOCKED
        state == ShiftState.OFF -> ShiftState.ONCE
        else -> ShiftState.OFF
    }

    /** After typing a letter a one-shot shift ends; caps lock stays. */
    fun afterLetter(state: ShiftState) = if (state == ShiftState.ONCE) ShiftState.OFF else state

    /** Shift for a new sentence or field. [capsMode] is the editor's `getCursorCapsMode` answer (non-zero means capitalise). */
    fun shiftFor(capsMode: Int, current: ShiftState): ShiftState =
        if (current == ShiftState.LOCKED) current else if (capsMode != 0) ShiftState.ONCE else ShiftState.OFF

    /** Two spaces after a word become ". ", as on iOS. Returns how many characters to delete first and what to type, or
     * null to type a normal space.
     */
    fun doubleSpace(textBeforeCursor: CharSequence): Pair<Int, String>? {
        val t = textBeforeCursor
        if (t.length < 2 || t.last() != ' ') return null
        val before = t[t.length - 2]
        return if (before.isLetterOrDigit() || before == ')' || before == '"') 1 to ". " else null
    }

    /** Accent choices for a long-press on a letter; none for letters that have none. */
    fun alternates(letter: String): List<String> = ALTERNATES[letter.lowercase()]?.let { alts ->
        if (letter.first().isUpperCase()) alts.map { it.uppercase() } else alts
    } ?: emptyList()

    private val ALTERNATES = mapOf(
        "a" to listOf("à", "á", "â", "ä", "ã", "å", "æ"), "e" to listOf("è", "é", "ê", "ë", "ē"),
        "i" to listOf("ì", "í", "î", "ï"), "o" to listOf("ò", "ó", "ô", "ö", "õ", "ø", "œ"),
        "u" to listOf("ù", "ú", "û", "ü"), "c" to listOf("ç", "ć", "č"), "n" to listOf("ñ", "ń"),
        "s" to listOf("ß", "ś", "š"), "y" to listOf("ý", "ÿ"), "z" to listOf("ž", "ź", "ż"),
        "l" to listOf("ł"), "d" to listOf("ð"), "t" to listOf("þ"))
}
