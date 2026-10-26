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

/**
 * Fits a flash of the form "<name> connected" to the room the pill has. A long device name used to run off the end and the cut fell on the
 * verb ("OpenRun Pro 2 by Shokz con…"), which loses what happened; the name is shortened instead ("OpenRun Pro 2 by Sh… connected"). Text
 * without such a verb is cut at its end. [fits] says whether a candidate is narrow enough; pure, so it is unit-tested.
 */
internal object FlashFit {
    private val VERB = Regex("""^(.+?)\s+(disconnected|connected)$""", RegexOption.IGNORE_CASE)
    private const val MIN_NAME = 3

    fun fit(text: String, fits: (String) -> Boolean): String {
        if (fits(text)) return text
        val m = VERB.find(text)
        val name = m?.groupValues?.get(1) ?: text
        val verb = m?.groupValues?.get(2)
        var keep = name.length - 1
        while (keep >= MIN_NAME) {
            val cut = name.take(keep).trimEnd() + "…"
            val candidate = if (verb != null) "$cut $verb" else cut
            if (fits(candidate)) return candidate
            keep--
        }
        return if (verb != null) name.take(MIN_NAME).trimEnd() + "… $verb" else name.take(MIN_NAME) + "…"
    }
}
