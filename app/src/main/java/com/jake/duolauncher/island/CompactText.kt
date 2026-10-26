package com.jake.duolauncher.island

/**
 * A shorter way to say the same thing on the pill, for a busy status bar where the full words would make the pill cut an icon: units to
 * their letters ("12 min" -> "12m", "1 hr 5 min" -> "1h 5m"), and a level after a word to the level alone ("Charging 84%" -> "84%").
 * Text with nothing to shorten comes back unchanged. Pure, so it is unit-tested.
 */
internal object CompactText {
    private val UNITS = listOf(
        Regex("""(\d)\s*(hours?|hrs?)\b""", RegexOption.IGNORE_CASE) to "$1h",
        Regex("""(\d)\s*(minutes?|mins?)\b""", RegexOption.IGNORE_CASE) to "$1m",
        Regex("""(\d)\s*(seconds?|secs?)\b""", RegexOption.IGNORE_CASE) to "$1s",
        Regex("""(\d)\s*(kilometres?|kilometers?|km)\b""", RegexOption.IGNORE_CASE) to "$1km",
        Regex("""(\d)\s*(miles?|mi)\b""", RegexOption.IGNORE_CASE) to "$1mi",
        Regex("""(\d)\s*(metres?|meters?|m)\b""", RegexOption.IGNORE_CASE) to "$1m",
    )
    private val WORD_THEN_PERCENT = Regex("""^\p{L}[\p{L} ]*\s+(\d{1,3}\s?%)$""")

    fun shorten(text: String): String {
        WORD_THEN_PERCENT.find(text.trim())?.let { return it.groupValues[1].replace(" ", "") }
        return UNITS.fold(text) { t, (re, to) -> re.replace(t, to) }
    }
}
