package com.jake.duolauncher.keyboard

/** Everything about how the keyboard types, with no Android classes, so it is unit-tested. The keyboard code in this
 * package never touches the network: a test (`KeyboardPrivacyTest`) fails the build if it ever imports anything that
 * could.
 */
internal enum class KeyPage { LETTERS, NUMBERS, SYMBOLS, EMOJI }

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
    data object Emoji : Key
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

    private const val FLAG_NO_SUGGESTIONS = 0x80000
    private const val TYPE_MASK_FLAGS = 0xfff000
    // Variations (android.text.InputType): uri, email, person name, postal address, web edit text, web email, and passwords.
    private val NO_SUGGESTION_VARIATIONS = setOf(0x10, 0x20, 0xd0, 0x80, 0x90, 0xe0)
    private val NO_AUTOCORRECT_VARIATIONS = NO_SUGGESTION_VARIATIONS + setOf(0x60, 0x70, 0xa0)

    /** Suggestions appear in ordinary text fields, never where the text is an address, a name or a secret. */
    fun allowsSuggestions(inputType: Int): Boolean =
        inputType and TYPE_MASK_CLASS == TYPE_CLASS_TEXT && inputType and FLAG_NO_SUGGESTIONS == 0 &&
            (inputType and TYPE_MASK_VARIATION) !in NO_SUGGESTION_VARIATIONS

    /** Autocorrect is stricter: also off in name, postal address and web form fields. */
    fun allowsAutocorrect(inputType: Int): Boolean =
        allowsSuggestions(inputType) && (inputType and TYPE_MASK_VARIATION) !in NO_AUTOCORRECT_VARIATIONS

    private val letterRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val numberRows = listOf("1234567890", "-/:;()$&@\"", ".,?!'")
    private val symbolRows = listOf("[]{}#%^*+=", "_\\|~<>€£¥•", ".,?!'")

    private fun chars(s: String, upper: Boolean = false) = s.map { Key.Char(if (upper) it.uppercase() else it.toString()) }

    /** The four rows for [page]. Letters uppercase while shift is on. */
    fun rows(page: KeyPage, shift: ShiftState, numberRow: Boolean = false, globe: Boolean = true): List<List<Key>> =
        if (page == KeyPage.EMOJI) emptyList() else (if (numberRow && page == KeyPage.LETTERS) listOf(chars("1234567890")) else emptyList()) + when (page) {
        KeyPage.LETTERS -> {
            val up = shift != ShiftState.OFF
            listOf(chars(letterRows[0], up), chars(letterRows[1], up),
                listOf<Key>(Key.Shift) + chars(letterRows[2], up) + Key.Backspace)
        }
        KeyPage.NUMBERS -> listOf(chars(numberRows[0]), chars(numberRows[1]),
            listOf<Key>(Key.Page(KeyPage.SYMBOLS, "#+=")) + chars(numberRows[2]) + Key.Backspace)
        KeyPage.SYMBOLS -> listOf(chars(symbolRows[0]), chars(symbolRows[1]),
            listOf<Key>(Key.Page(KeyPage.NUMBERS, "123")) + chars(symbolRows[2]) + Key.Backspace)
        KeyPage.EMOJI -> emptyList()
    } + listOf(bottomRow(page, globe))

    /** The bottom row: the page key, the emoji key, the keyboard switcher only when Android says there is another keyboard to
     * switch to (a globe that goes nowhere is just a dead key), the space bar and Return.
     */
    fun bottomRow(page: KeyPage, globe: Boolean): List<Key> = listOfNotNull(
        if (page == KeyPage.LETTERS) Key.Page(KeyPage.NUMBERS, "123") else Key.Page(KeyPage.LETTERS, "ABC"),
        Key.Emoji, if (globe) Key.Globe else null, Key.Space, Key.Enter)

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

    private const val CAP_CHARACTERS = 0x1000; private const val CAP_WORDS = 0x2000; private const val CAP_SENTENCES = 0x4000

    /** Whether the next letter should be capital, worked out from the text before the cursor so no call to the app is
     * needed. Mirrors Android's own rules for sentence, word and all-characters capitalisation.
     */
    fun capsFromText(before: CharSequence, inputType: Int): Boolean {
        if (inputType and TYPE_MASK_CLASS != TYPE_CLASS_TEXT) return false
        if (inputType and CAP_CHARACTERS != 0) return true
        val t = before.toString()
        if (inputType and CAP_WORDS != 0) return t.isEmpty() || t.last().isWhitespace()
        if (inputType and CAP_SENTENCES == 0) return false
        if (t.isEmpty() || t.last() == '\n') return true
        if (!t.last().isWhitespace()) return false
        val trimmed = t.trimEnd()
        return trimmed.isEmpty() || trimmed.last() in ".!?" || t.contains('\n') && t.substringAfterLast('\n').isBlank()
    }

    /** Two spaces after a word become ". ", as on iOS. Returns how many characters to delete first and what to type, or
     * null to type a normal space.
     */
    fun doubleSpace(textBeforeCursor: CharSequence): Pair<Int, String>? {
        val t = textBeforeCursor
        if (t.length < 2 || t.last() != ' ') return null
        val before = t[t.length - 2]
        return if (before.isLetterOrDigit() || before == ')' || before == '"') 1 to ". " else null
    }

    /** How many characters a held backspace deletes in one step: one at first, and a whole word once it has been held a while,
     * as on iOS. [text] is what is before the cursor; it never deletes more than there is, and never reaches past a line break.
     */
    fun deleteStep(text: CharSequence, held: Int): Int {
        if (text.isEmpty()) return 0
        if (held < WORD_DELETE_AFTER) return lastClusterLength(text)
        var i = text.length
        while (i > 0 && text[i - 1] == ' ') i--                              // spaces right before the cursor
        if (i == 0 || text[i - 1] == '\n') return (text.length - i).coerceAtLeast(1)
        while (i > 0 && !text[i - 1].isWhitespace()) i--                    // then the word itself
        return (text.length - i).coerceAtLeast(1)
    }

    /** How many UTF-16 units the last character on screen takes. Deleting one unit of an emoji leaves half a surrogate pair, which
     * shows as a broken box, so a pair goes whole; so does a flag (two regional indicators) and a symbol with its variation selector.
     */
    fun lastClusterLength(text: CharSequence): Int {
        var end = text.length
        if (end == 0) return 0
        fun cpStart(e: Int): Int = if (e >= 2 && Character.isLowSurrogate(text[e - 1]) && Character.isHighSurrogate(text[e - 2])) e - 2 else e - 1
        if (text[end - 1] == '\uFE0F' && end >= 2) end -= 1
        var start = cpStart(end)
        val cp = Character.codePointAt(text, start)
        if (cp in 0x1F1E6..0x1F1FF && start >= 2) {
            val prev = cpStart(start)
            if (Character.codePointAt(text, prev) in 0x1F1E6..0x1F1FF) start = prev
        }
        return (text.length - start).coerceAtLeast(1)
    }

    /** The hold count (repeats) after which backspace starts taking whole words. */
    const val WORD_DELETE_AFTER = 14

    /** Accent choices for a long-press on a letter, symbol or digit; none where there are none. */
    fun alternates(letter: String): List<String> = ALTERNATES[letter.lowercase()]?.let { alts ->
        if (letter.first().isUpperCase()) alts.map { it.uppercase() } else alts
    } ?: emptyList()

    private val ALTERNATES = mapOf(
        "a" to listOf("à", "á", "â", "ä", "ã", "å", "æ"), "e" to listOf("è", "é", "ê", "ë", "ē"),
        "i" to listOf("ì", "í", "î", "ï"), "o" to listOf("ò", "ó", "ô", "ö", "õ", "ø", "œ"),
        "u" to listOf("ù", "ú", "û", "ü"), "c" to listOf("ç", "ć", "č"), "n" to listOf("ñ", "ń"),
        "s" to listOf("ß", "ś", "š"), "y" to listOf("ý", "ÿ"), "z" to listOf("ž", "ź", "ż"),
        "l" to listOf("ł"), "d" to listOf("ð"), "t" to listOf("þ"),
        // Symbols and digits, as on iOS.
        "-" to listOf("–", "—", "•"), "/" to listOf("\\"), "." to listOf("…"), "?" to listOf("¿"), "!" to listOf("¡"),
        "'" to listOf("‘", "’", "`"), "\"" to listOf("“", "”", "„", "«", "»"), "$" to listOf("¢", "€", "£", "¥", "₹"),
        "&" to listOf("§"), "%" to listOf("‰"), "0" to listOf("°"), "=" to listOf("≠", "≈"), "+" to listOf("±"),
        "<" to listOf("≤", "«"), ">" to listOf("≥", "»"), "(" to listOf("[", "{"), ")" to listOf("]", "}"),
        "#" to listOf("№"), "*" to listOf("†", "‡", "★"))
}
