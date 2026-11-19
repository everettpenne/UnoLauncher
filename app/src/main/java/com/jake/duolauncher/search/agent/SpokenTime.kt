package com.jake.duolauncher.search.agent

import com.jake.duolauncher.search.SpotlightSources
import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * Times and lengths as they are said, read by the launcher. The planner copies the person's words ("an hour and a half", "tomorrow 3pm")
 * and this turns them into numbers: on the phone a 2B model made "in an hour" one minute and could not be trusted with 90 × 60.
 * Pure, so every phrasing is unit-tested.
 */
internal object SpokenTime {
    private val ones = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
        "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
    private val tens = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90)
    private val tensAndOnes = Regex("""\b(${tens.keys.joinToString("|")})[ -](${ones.subList(1, 10).joinToString("|")})\b""")
    private val tensAlone = Regex("""\b(${tens.keys.joinToString("|")})\b""")
    private val onesAlone = Regex("""\b(${ones.drop(1).joinToString("|")})\b""")

    /** [text] in lower case with its number words as digits: "forty five minutes" is "45 minutes". "a" and "an" are left as they are. */
    fun digits(text: String): String {
        var t = text.lowercase().replace("a couple of", "2").replace("a couple", "2")
        t = tensAndOnes.replace(t) { (tens.getValue(it.groupValues[1]) + ones.indexOf(it.groupValues[2])).toString() }
        t = tensAlone.replace(t) { tens.getValue(it.groupValues[1]).toString() }
        // "one" is a number before a unit or a clock word, and not in "the last one".
        t = onesAlone.replace(t) { m -> if (m.value == "one" && !Regex("""^\s*(hours?|minutes?|seconds?|h|m|s|am|pm|o'?clock|thirty|\d)""").containsMatchIn(t.substring(m.range.last + 1))) m.value else ones.indexOf(m.value).toString() }
        return t
    }

    private val lead = Regex("""^(?:for|in|of|about|around|the next|another)\s+""")
    private val hourAnd = Regex("""^(?:an?|1)\s*(?:hour|hr|h)\s+(?:and\s+)?(\d+)\s*(?:m|min|mins|minutes?)?$""")
    private val quarter = Regex("""^(?:a\s+)?(three\s+quarters|3\s+quarters|quarter)\s+of\s+an\s+hour$""")

    /** A length in seconds: "20 minutes", "1h 30m", "an hour and a half", "ninety seconds", "for half an hour". Null when it is not one. */
    fun seconds(text: String): Long? {
        var t = digits(text).trim().trimEnd('.', '!', ',')
        while (true) { val cut = lead.replace(t, ""); if (cut == t) break; t = cut }
        SpotlightSources.spokenSeconds(t)?.let { return it.takeIf { s -> s > 0 } }
        hourAnd.matchEntire(t)?.let { return 3600L + (it.groupValues[1].toLongOrNull() ?: return null) * 60 }
        quarter.matchEntire(t)?.let { return if (it.groupValues[1] == "quarter") 900L else 2700L }
        return null
    }

    private const val DAY_PART = """today|tomorrow|tonight|this\s+morning|this\s+afternoon|this\s+evening|in\s+the\s+morning|in\s+the\s+afternoon|in\s+the\s+evening|at\s+night|morning|afternoon|evening|night"""
    private val dayPart = Regex("""\b($DAY_PART)\b""")
    private val later = Regex("""\b(tonight|afternoon|evening|night)\b""")
    private val clockLead = Regex("""^(?:at|for|by|around|about)\s+""")
    private val clockTime = Regex("""^(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)?(?:\s*o'?clock)?$""")
    private val pastTo = Regex("""^(half|quarter|a quarter|\d{1,2})(?:\s+minutes?)?\s+(past|to|after|before)\s+(\d{1,2})\s*(am|pm)?$""")

    /** A clock time as hour (0–23) and minute: "6:45", "7am", "19:00", "7 tonight", "half past six", "noon". Null when it is not one. */
    fun clock(text: String): Pair<Int, Int>? {
        val whole = digits(text).trim().trimEnd('.', '!', ',')
        val evening = later.containsMatchIn(whole)
        var t = dayPart.replace(whole, " ").replace(Regex("""\s+"""), " ").trim()
        while (true) { val cut = clockLead.replace(t, ""); if (cut == t) break; t = cut }
        if (t == "noon" || t == "midday") return 12 to 0
        if (t == "midnight") return 0 to 0
        fun hourOf(raw: Int, meridiem: Char?): Int? {
            val hour = when (meridiem) {
                null -> if (evening && raw in 1..11) raw + 12 else raw
                'p' -> when (raw) { in 1..11 -> raw + 12; 12 -> 12; else -> return null }
                else -> when (raw) { 12 -> 0; in 1..11 -> raw; else -> return null }
            }
            return hour.takeIf { it in 0..23 }
        }
        pastTo.matchEntire(t)?.let { m ->
            val amount = when (m.groupValues[1]) { "half" -> 30; "quarter", "a quarter" -> 15; else -> m.groupValues[1].toInt() }
            if (amount !in 1..59) return null
            val base = hourOf(m.groupValues[3].toInt(), m.groupValues[4].firstOrNull()) ?: return null
            val after = m.groupValues[2] == "past" || m.groupValues[2] == "after"
            return if (after) base to amount else ((base + 23) % 24) to (60 - amount)
        }
        val m = clockTime.matchEntire(t) ?: return null
        val minute = m.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0
        if (minute !in 0..59) return null
        val hour = hourOf(m.groupValues[1].toInt(), m.groupValues[3].firstOrNull()) ?: return null
        return hour to minute
    }

    sealed interface Alarm {
        data class At(val hour: Int, val minute: Int) : Alarm
        data class In(val minutes: Int) : Alarm
    }

    /** When an alarm is for: a clock time, or "in 45 minutes". Null when it is neither, or further off than a day. */
    fun alarm(text: String): Alarm? {
        val t = digits(text).trim()
        if (t.startsWith("in ")) {
            val seconds = seconds(t) ?: return null
            val minutes = ((seconds + 59) / 60).toInt()
            return Alarm.In(minutes).takeIf { minutes in 1..1440 }
        }
        return clock(t)?.let { Alarm.At(it.first, it.second) }
    }

    private val weekdays = mapOf("monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY,
        "tues" to DayOfWeek.TUESDAY, "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY, "thursday" to DayOfWeek.THURSDAY,
        "thu" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY, "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY, "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY)
    private val weekday = Regex("""\b(?:(?:on|next|this|coming)\s+)*(${weekdays.keys.joinToString("|")})\b""")
    private val dayAfter = Regex("""\b(?:the\s+)?day\s+after\s+tomorrow\b""")

    /**
     * When something starts: "now", "in 2 hours", "at 3pm", "tomorrow 3pm", "friday at 09:30", "tonight at 8". A time with no day is
     * today's, or tomorrow's once it has passed. A day with no time is null: the person is asked rather than given midnight.
     */
    fun start(text: String, now: LocalDateTime): LocalDateTime? {
        var t = digits(text).trim().trimEnd('.', '!', ',')
        if (t.isEmpty()) return null
        if (t == "now" || t == "right now") return now
        if (t.startsWith("in ")) return seconds(t)?.let { now.plusSeconds(it) }
        var days: Long? = null
        dayAfter.find(t)?.let { days = 2; t = t.removeRange(it.range) }
        if (days == null) weekday.find(t)?.let { m ->
            val want = weekdays.getValue(m.groupValues[1])
            val ahead = ((want.value - now.dayOfWeek.value) + 7) % 7
            days = if (ahead == 0) 7 else ahead.toLong()
            t = t.removeRange(m.range)
        }
        if (days == null && Regex("""\btomorrow\b""").containsMatchIn(t)) days = 1
        val today = Regex("""\b(today|tonight|this\s+(?:morning|afternoon|evening))\b""").containsMatchIn(t)
        val (saidHour, minute) = clock(t.replace(Regex("""\s+"""), " ").trim()) ?: return null
        // "dentist at 3" is the afternoon's 3: an event's bare small hour is not the middle of the night.
        val marked = Regex("""\b(am|pm|a\.m\.|p\.m\.|morning|noon|midnight)\b|\d\s*(am|pm)\b""").containsMatchIn(t)
        val hour = if (!marked && saidHour in 1..7) saidHour + 12 else saidHour
        val at = now.toLocalDate().atTime(hour, minute)
        return when {
            days != null -> at.plusDays(days!!)
            today || at.isAfter(now) -> at
            else -> at.plusDays(1)
        }
    }

    private val wordRun = Regex("""\S+""")

    /** Whether some run of up to [MAX_RUN] words of [said] reads, through [parse], as [value]: the check that a time or a length a plan
     *  carries is one the person gave, however the planner wrote it ("90 minutes" for "an hour and a half"). */
    fun <T : Any> saidAs(said: String, value: T, parse: (String) -> T?): Boolean {
        // Line by line: a request and the answer or follow-up under it are separate things said, never one phrase.
        for (line in said.lineSequence()) {
            val words = wordRun.findAll(line).map { it.value }.toList()
            for (from in words.indices) for (to in from + 1..minOf(words.size, from + MAX_RUN)) {
                if (parse(words.subList(from, to).joinToString(" ")) == value) return true
            }
        }
        return false
    }

    /**
     * Every time or length the person said, each read from the longest run of words that says it: "tomorrow at 3pm" is tomorrow's 3pm,
     * and the "3pm" inside it is not a second, different time. A run counts only when no longer run that contains it also reads.
     */
    fun <T : Any> spoken(said: String, parse: (String) -> T?): List<Pair<String, T>> =
        // Line by line: "10 minutes" in a request and "15 minutes" in the follow-up under it are two lengths, not twenty-five minutes.
        said.lineSequence().flatMap { spokenInLine(it, parse) }.toList()

    private fun <T : Any> spokenInLine(said: String, parse: (String) -> T?): List<Pair<String, T>> {
        val words = wordRun.findAll(said).map { it.value }.toList()
        val found = ArrayList<Triple<Int, Int, T>>()
        for (from in words.indices) for (to in from + 1..minOf(words.size, from + MAX_RUN)) {
            parse(words.subList(from, to).joinToString(" "))?.let { found += Triple(from, to, it) }
        }
        return found.filter { (from, to, _) -> found.none { (f, t, _) -> f <= from && t >= to && (t - f) > (to - from) } }
            .map { (from, to, value) -> words.subList(from, to).joinToString(" ") to value }
    }

    /** Whether [value] is one of the things the person said, read whole ([spoken]): a time is not theirs if it drops the day they gave. */
    fun <T : Any> saidWhole(said: String, value: T, parse: (String) -> T?): Boolean = spoken(said, parse).any { it.second == value }

    /** The one thing of this kind the person said, in their words, or null when they said none or more than one. Where they said
     *  several, one introduced by [lead] ("for an hour") is the one meant, if there is exactly one such. */
    fun <T : Any> theOne(said: String, lead: String? = null, parse: (String) -> T?): String? {
        val all = spoken(said, parse).distinctBy { it.second }
        if (all.size == 1) return all.single().first
        return lead?.let { l -> all.filter { it.first.startsWith("$l ", ignoreCase = true) }.singleOrNull()?.first }
    }

    private const val MAX_RUN = 7
}
