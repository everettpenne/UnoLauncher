package com.jake.duolauncher.search.agent

import java.time.Duration
import java.time.LocalDateTime

/** Something in the calendar, as far as a tool needs it: what it is called and when it starts. */
internal data class CalendarEvent(val title: String, val start: LocalDateTime, val allDay: Boolean = false)

/**
 * A time given by something in the calendar: "30 minutes before my first meeting tomorrow", "an hour before the dentist".
 *
 * This is the launcher looking something up for a plan, where the planner cannot: the model plans once and writes the person's own
 * words for the time, and a model this size does not work a time out from the facts beside a request (tried: it copies the words
 * whether or not the calendar is in front of it). So the words are read here, the calendar is read only when they point at it, and
 * what was found is shown before anything is set. Pure, so it is unit-tested.
 */
internal object EventTime {
    enum class Which { FIRST, NEXT, NAMED }
    enum class Day { TODAY, TOMORROW }

    /** [minutes] before the [which] event (of [day], when one was said); [words] name it when it is [Which.NAMED]. */
    data class Ref(val minutes: Int, val which: Which, val words: String, val day: Day?)

    sealed interface Found {
        data class At(val event: CalendarEvent, val alarm: LocalDateTime) : Found
        data class None(val why: String) : Found
    }

    private val shape = Regex("""^(.+?)\s+(?:before|ahead of|prior to)\s+(.+)$""")
    private val owner = Regex("""^(?:my|the|our)\s+""")
    private val tomorrow = Regex("""\btomorrow(?:'s)?\b""")
    private val today = Regex("""\b(?:today(?:'s)?|tonight(?:'s)?|this (?:morning|afternoon|evening))\b""")
    private val order = Regex("""^(first|1st|next|upcoming)\s+""")
    private val filler = Regex("""\b(?:of the day|of the morning|in my calendar|on my calendar|in the calendar|on the calendar|starts?|begins?)\b""")
    private val plain = setOf("meeting", "meetings", "event", "events", "appointment", "appointments", "thing", "one", "calendar")
    private val word = Regex("""[\p{L}\p{N}']+""")

    /** Reads "<how long> before <which event>"; null for anything else ("7:30", "in 20 minutes", "before noon"). */
    fun parse(text: String): Ref? {
        val m = shape.matchEntire(SpokenTime.digits(text).trim().trimEnd('.', '!', '?')) ?: return null
        val seconds = SpokenTime.seconds(m.groupValues[1]) ?: return null
        val minutes = ((seconds + 59) / 60).toInt().takeIf { it in 1..720 } ?: return null
        var rest = m.groupValues[2]
        val day = when { tomorrow.containsMatchIn(rest) -> Day.TOMORROW; today.containsMatchIn(rest) -> Day.TODAY; else -> null }
        rest = filler.replace(today.replace(tomorrow.replace(rest, " "), " "), " ").replace(Regex("""\s+"""), " ").trim()
        rest = owner.replace(rest, "")
        val first = order.find(rest)?.groupValues?.get(1)
        rest = order.replace(rest, "")
        val names = word.findAll(rest).map { it.value }.filter { it !in plain }.toList()
        // A clock time is not an event: "10 minutes before 7" is a sum, and is left to be asked about.
        if (SpokenTime.clock(rest) != null || (names.isNotEmpty() && names.all { n -> n.all { it.isDigit() } })) return null
        if (names.isEmpty() && word.findAll(rest).none()) return null
        val which = when { names.isNotEmpty() -> Which.NAMED; first == "first" || first == "1st" -> Which.FIRST; else -> Which.NEXT }
        return Ref(minutes, which, names.joinToString(" "), day)
    }

    /** Whether the person's own words hold that reference: a time nobody asked for is not worked out from their calendar. */
    fun said(said: String, ref: Ref): Boolean = said.lineSequence().any { line ->
        val text = SpokenTime.digits(line)
        // Anywhere in the line: "wake me 30 minutes before my first meeting tomorrow".
        word.findAll(text).map { it.range.first }.any { from -> parse(text.substring(from)) == ref }
    }

    private val little = setOf("with", "the", "a", "an", "my", "our", "at", "for", "and", "of", "in", "on", "to")

    /** Whether [words] name the event called [title]: every word of them that says something is a word of the title, whole or as
     *  the start of one ("dentist" for "Dentist appointment", "priya" for "Lunch with Priya"). One shared "with" is not a match. */
    fun named(words: String, title: String): Boolean {
        val asked = word.findAll(words.lowercase()).map { it.value }.filter { it !in little }.toList()
        val has = word.findAll(title.lowercase()).map { it.value }.toList()
        return asked.isNotEmpty() && asked.all { a -> has.any { it == a || (a.length >= 3 && it.startsWith(a)) } }
    }

    /** The event [ref] means among [events], and when the alarm would be. An alarm is a clock time with no date, so it has to fall
     *  within the next day; what cannot be found or set is said in words for the person. */
    fun resolve(ref: Ref, events: List<CalendarEvent>, now: LocalDateTime): Found {
        val ahead = events.filter { !it.allDay && it.start.isAfter(now) }.sortedBy { it.start }
        val onDay = when (ref.day) {
            Day.TOMORROW -> ahead.filter { it.start.toLocalDate() == now.toLocalDate().plusDays(1) }
            Day.TODAY -> ahead.filter { it.start.toLocalDate() == now.toLocalDate() }
            null -> ahead
        }
        val dayWords = when (ref.day) { Day.TOMORROW -> " tomorrow"; Day.TODAY -> " later today"; null -> " coming up" }
        val event = (if (ref.which == Which.NAMED) onDay.filter { named(ref.words, it.title) } else onDay).firstOrNull()
            ?: return Found.None(if (ref.which == Which.NAMED) "I don't see “${ref.words}” in your calendar$dayWords." else "I don't see anything in your calendar$dayWords.")
        val alarm = event.start.minusMinutes(ref.minutes.toLong())
        val title = ReplyLogic.clean(event.title).ifEmpty { "that" }
        val starts = Speak.dayTime(event.start, now)
        return when {
            !alarm.isAfter(now) -> Found.None("“$title” starts $starts, which is less than ${Speak.duration(ref.minutes * 60)} away.")
            Duration.between(now, alarm).toHours() >= 24 -> Found.None("“$title” starts $starts. I can only set an alarm for the next 24 hours.")
            else -> Found.At(event.copy(title = title), alarm)
        }
    }
}
