package com.jake.duolauncher.island

/** One side of a score: the team (or player) as the notification names it, and its points. */
internal data class SportsSide(val name: String, val score: Int) {
    /** The name short enough for a scoreboard: an abbreviation as given ("LAL"), initials ("FCB"), or its first three letters ("ARS"). */
    val short: String get() = SportsLogic.short(name)
}

/** A live score read from a sports app's notification: the two sides in the order the app gave them, and where the game is
 *  ("67'", "Q3 4:21", "Final"), when it said. */
internal data class SportsScore(val left: SportsSide, val right: SportsSide, val status: String?) {
    /** The colour of each side's mark, as a hue: each team's own steady one ([SportsLogic.hue]), with the second moved round the
     *  wheel when the two would be too alike to tell the sides apart at a glance. */
    val leftHue: Float get() = SportsLogic.hue(left.name)
    val rightHue: Float get() {
        val own = SportsLogic.hue(right.name)
        val apart = kotlin.math.abs(own - leftHue).let { if (it > 180f) 360f - it else it }
        return if (apart < SportsLogic.MARKS_APART) (leftHue + 150f) % 360f else own
    }

    /** What fits beside the camera on the pill: each side's short name by its score ("LAK 98–101 CEL"), or the two scores alone when
     *  that is too long to sit there. The full names are on the card. */
    val glance: String get() {
        val scores = "${left.score}${SportsLogic.BETWEEN}${right.score}"
        return "${left.short} $scores ${right.short}".takeIf { it.length <= SportsLogic.PILL_CHARS } ?: scores
    }
}

/**
 * Recognising a score in a notification's title and text, with no Android classes, so it is unit-tested. Sports apps say a live score
 * in a handful of ways ("Arsenal 2 - 1 Chelsea", "LAL 98, BOS 101", "Lakers 98 @ Celtics 101 · Q3 4:21"), and these are the ones
 * read. Anything else is not a score: a wrong match would put a scoreboard on a download.
 */
internal object SportsLogic {
    /** What stands between two scores. */
    const val BETWEEN = "–"
    /** The longest a score may be on the pill with its names. */
    const val PILL_CHARS = 16
    /** Two sides' marks are at least this far apart in hue, in degrees. */
    const val MARKS_APART = 60f
    private const val NAME = """([\p{L}0-9][\p{L}0-9 .&'’/-]{0,26}[\p{L}0-9.])"""
    private const val STATUS = """(?:\s*(?:[(\[]|[·|•,]|\s[-–—]\s)\s*([^)\]]{1,26}?)[)\]]?)?"""
    // Two things keep a clock or a gate from reading as a score. A colon stands between two scores only with a space each side
    // ("0 : 0"): "5:42" is a time. And two names each followed by a number need something between the pairs (a comma, "@", "vs"):
    // "Flight 204 Gate 12" is not a game.
    /** "Arsenal 2 - 1 Chelsea", "Arsenal 2–1 Chelsea (67')", "Real Madrid 0 : 0 Barcelona". */
    private val between = Regex("""^$NAME\s+(\d{1,3})(?:\s*[-–—]\s*|\s+:\s+)(\d{1,3})\s+$NAME$STATUS$""")
    /** "LAL 98, BOS 101", "Lakers 98 @ Celtics 101 · Q3 4:21", "India 245 vs Australia 198". */
    private val each = Regex("""^$NAME\s+(\d{1,3})\s*(?:[,·|@]|\s(?:at|vs?\.?)\s|\s[-–—]\s)\s*$NAME\s+(\d{1,3})$STATUS$""", RegexOption.IGNORE_CASE)
    /** Words that say the numbers are not points: a transfer, a list, a place in something. */
    private val notATeam = Regex("""(?i)\b(download\w*|upload\w*|copy\w*|install\w*|updat\w*|sync\w*|files?|photos?|items?|messages?|step|page|part|track|episode|chapter|level|version|of|mb|gb|kb|min|mins|minutes?|hours?|sec|seconds?|left|remaining|battery|percent|am|pm|gate|terminal|flight|platform|seat|row|eta|arriving|arrives?|recording|order|room|table)\b|%""")

    /** Capitals that are a unit or a time, never a team. */
    private val NOT_ABBREVIATIONS = setOf("MB", "GB", "KB", "AM", "PM", "ETA", "OF")

    private fun side(name: String, score: String): SportsSide? {
        val n = name.trim().trim('-', '–', ',', '.').trim()
        // A team has letters in its name (at least two), and is not a count of something.
        // A scoreboard's own abbreviation is a team even when it spells a word that counts something: "MIN" is Minnesota, not minutes.
        val abbreviation = n.length in 2..4 && n.all { it in 'A'..'Z' } && n !in NOT_ABBREVIATIONS
        if (n.count { it.isLetter() } < 2 || (!abbreviation && notATeam.containsMatchIn(n))) return null
        return SportsSide(n, score.toIntOrNull()?.takeIf { it in 0..999 } ?: return null)
    }

    private fun read(line: String): SportsScore? {
        val t = line.trim().replace(Regex("""\s+"""), " ")
        if (t.length !in 7..90) return null
        between.matchEntire(t)?.let { m ->
            // "49ers 21 - 17 Seahawks - Final": a dash set off by spaces ends the second name and starts where the game is.
            val (rightName, tail) = m.groupValues[4].split(Regex("""\s[-–—]\s"""), limit = 2).let { it[0] to it.getOrNull(1) }
            val l = side(m.groupValues[1], m.groupValues[2]) ?: return@let
            val r = side(rightName, m.groupValues[3]) ?: return@let
            return SportsScore(l, r, (m.groupValues[5].trim().ifEmpty { null } ?: tail?.trim()?.takeIf { it.length in 1..26 }))
        }
        each.matchEntire(t)?.let { m ->
            val l = side(m.groupValues[1], m.groupValues[2]) ?: return@let
            val r = side(m.groupValues[3], m.groupValues[4]) ?: return@let
            return SportsScore(l, r, m.groupValues[5].trim().ifEmpty { null })
        }
        return null
    }

    /**
     * The score in a notification with this [title] and [text], or null when neither says one. The title is tried first, then the
     * text's first line. When the title is the score and the text is a few words ("67'", "Half time"), those are where the game is.
     */
    fun parse(title: String?, text: String?): SportsScore? {
        val firstLine = text?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        read(title.orEmpty())?.let { score ->
            return if (score.status == null && firstLine.length in 1..26 && read(firstLine) == null) score.copy(status = firstLine) else score
        }
        return read(firstLine)
    }

    /**
     * The score in a notification the app drew itself, from the words on it in the order they are laid out. A sports app's live card is
     * often its own drawing with no title or text at all (theScore's is "MIL", "19", "1st", "5:37", "TOR", "18"), so the words on it
     * are all there is to read. It is a score when exactly two of the words are whole numbers and each has a team's name beside it,
     * both names on the same side of their numbers. What is left over ("1st", "5:37") is where the game is.
     */
    fun fromWords(words: List<String>): SportsScore? {
        val w = words.map { it.trim().replace(Regex("""\s+"""), " ") }.filter { it.isNotEmpty() }
        if (w.size !in 4..CARD_WORDS) return null
        val numbers = w.indices.filter { i -> w[i].length <= 3 && w[i].all { it in '0'..'9' } }
        if (numbers.size != 2) return null
        fun name(i: Int): String? = w.getOrNull(i)?.takeIf { t -> i !in numbers && t.length <= 28 && t.none { it.isDigit() } && t.count { it.isLetter() } >= 2 }
        // The name before each number ("MIL 19"), else after each ("19 MIL"); never one of each, which would be two numbers side by side.
        val names = listOf(-1, 1).firstNotNullOfOrNull { step ->
            val (a, b) = numbers.map { it + step }
            if (a != b && name(a) != null && name(b) != null) a to b else null
        } ?: return null
        val left = side(w[names.first], w[numbers[0]]) ?: return null
        val right = side(w[names.second], w[numbers[1]]) ?: return null
        val rest = w.indices.filter { it !in numbers && it != names.first && it != names.second }.joinToString(" ") { w[it] }
        return SportsScore(left, right, rest.takeIf { it.length in 1..26 })
    }

    /** The teams the person follows, from what they typed (Island › Live Scores): names or scoreboard abbreviations, set apart by commas. */
    fun favourites(typed: String?): List<String> =
        typed.orEmpty().split(',', '\n', ';').map { it.trim().lowercase() }.filter { it.length >= 2 }.distinct().take(FAVOURITES)

    /**
     * Whether either side of [score] is one of [favourites]. A scoreboard often writes only an abbreviation ("MIN"), so a favourite
     * matches a side when it is that side's name or short name, a whole word of its name ("timberwolves" in "Minnesota Timberwolves"),
     * or a name the abbreviation begins ("minnesota" for "MIN").
     */
    fun follows(score: SportsScore, favourites: List<String>): Boolean = favourites.any { f ->
        listOf(score.left, score.right).any { side ->
            val name = side.name.trim().lowercase()
            name == f || side.short.lowercase() == f || name.split(' ', '-').any { it == f } || (name.length in 2..4 && f.length > name.length && f.startsWith(name))
        }
    }

    /** The most teams that can be followed. */
    const val FAVOURITES = 24

    /** The most words a drawn card may have and still be read as a score: a scoreboard is a few words, not a paragraph. */
    const val CARD_WORDS = 8

    /** Whether a notification may be looked at for a score at all: one that stays up while something is going on, that is not a
     *  conversation, a call or a transfer with a progress bar. Only such a notification has its title and text read for this. */
    fun mayBeScore(category: String?, ongoing: Boolean, progressMax: Int): Boolean =
        ongoing && progressMax <= 0 && (category == null || category !in NOT_SCORES)

    private val NOT_SCORES = setOf("call", "msg", "email", "social", "transport", "navigation", "alarm", "reminder", "progress", "sys", "err", "service")

    /**
     * A team's colour for its mark, from its name: the same team is the same colour every time, and two teams are seldom alike. It is
     * not the club's own colour (the launcher carries no table of clubs, and no logos: those are theirs), only a steady one, dark
     * enough that white letters read on it.
     */
    fun hue(name: String): Float {
        var h = 0
        for (ch in name.trim().lowercase()) h = h * 31 + ch.code
        return ((h % 360) + 360) % 360f
    }

    fun short(name: String): String {
        val n = name.trim()
        val words = n.split(' ', '-').filter { it.isNotBlank() }
        return when {
            n.length <= 4 -> n.uppercase()
            words.size >= 2 -> words.take(3).joinToString("") { it.first().uppercase() }
            else -> n.take(3).uppercase()
        }
    }
}
