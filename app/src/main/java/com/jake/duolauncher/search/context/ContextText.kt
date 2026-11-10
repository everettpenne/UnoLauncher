package com.jake.duolauncher.search.context

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** The lines each context source writes, from plain values, so every one is unit-tested. Short and literal: the model reads every character. */
internal object ContextText {
    fun dateTime(now: ZonedDateTime, locale: Locale, is24Hour: Boolean, firstDay: DayOfWeek): String {
        val date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH))
        val time = now.format(DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", Locale.ENGLISH))
        val offset = now.offset.id.let { if (it == "Z") "UTC" else "UTC$it" }
        return "Today: $date, $time ($offset, ${now.zone.id})\n" +
            "Language and region: ${locale.toLanguageTag()}; ${if (is24Hour) "24-hour" else "12-hour"} clock; weeks start on " +
            firstDay.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    }

    fun battery(level: Int?, charging: Boolean, toFull: String?): String? {
        if (level == null) return null
        return "Battery: $level%" + when {
            charging && toFull != null -> ", charging, $toFull"
            charging -> ", charging"
            else -> ", not charging"
        }
    }

    fun alarm(time: String?, day: String?, remaining: String?): String =
        if (time == null) "Next alarm: none set" else "Next alarm: $time ${day.orEmpty().lowercase()}".trimEnd() + (remaining?.let { " ($it)" } ?: "")

    fun quiet(focusOn: Boolean, dnd: Boolean?, ringer: String?): String = buildList {
        add("Focus ${if (focusOn) "on" else "off"}")
        dnd?.let { add("Do Not Disturb ${if (it) "on" else "off"}") }
        ringer?.let { add("ringer $it") }
    }.joinToString(", ")

    fun timer(remainingMs: Long, totalMs: Long, ringing: Boolean): String =
        if (ringing) "Timer: finished, ringing now" else "Timer: ${duration(remainingMs)} left of ${duration(totalMs)}"

    fun stopwatch(elapsedMs: Long, running: Boolean): String = "Stopwatch: ${duration(elapsedMs)}${if (running) ", running" else ", paused"}"

    fun nowPlaying(title: String?, artist: String?, playing: Boolean, app: String?): String? {
        if (title.isNullOrBlank()) return null
        return (if (playing) "Playing now: " else "Paused: ") + "\"$title\"" + (artist?.takeIf { it.isNotBlank() }?.let { " by $it" } ?: "") +
            (app?.let { " in $it" } ?: "")
    }

    /** "1 h 05 min", "12 min", "45 s". */
    fun duration(ms: Long): String {
        val s = (ms.coerceAtLeast(0) + 999) / 1000
        val h = s / 3600; val m = (s % 3600) / 60
        return when { h > 0 -> "$h h ${"%02d".format(m)} min"; m > 0 -> "$m min"; else -> "$s s" }
    }

    fun bytes(n: Long): String = when {
        n >= 1_000_000_000L -> "%.1f GB".format(Locale.ROOT, n / 1e9)
        n >= 1_000_000L -> "%.0f MB".format(Locale.ROOT, n / 1e6)
        else -> "${n / 1000} KB"
    }

    fun storage(free: Long, total: Long): String = "Storage: ${bytes(free)} free of ${bytes(total)}"

    fun list(label: String, items: List<String>, max: Int): String? {
        val shown = items.filter { it.isNotBlank() }.distinct()
        if (shown.isEmpty()) return null
        val more = shown.size - max
        return "$label: " + shown.take(max).joinToString(", ") + if (more > 0) " and $more more" else ""
    }

    fun countdown(name: String, today: LocalDate, target: LocalDate): String {
        val days = ChronoUnit.DAYS.between(today, target)
        val date = target.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH))
        return when {
            days > 0 -> "Countdown \"$name\": $days days, until $date"
            days == 0L -> "Countdown \"$name\": today"
            else -> "Countdown \"$name\": ${-days} days ago, on $date"
        }
    }

    fun checklist(items: List<Pair<String, Boolean>>): String? {
        if (items.isEmpty()) return null
        val open = items.filter { !it.second }.map { it.first }
        return "Checklist: ${items.size - open.size} of ${items.size} done" + if (open.isNotEmpty()) "; still to do: " + open.joinToString(", ") else ""
    }

    fun notification(app: String, title: String?, text: String?, minutesAgo: Long): String =
        "$app, ${if (minutesAgo < 1) "just now" else "$minutesAgo min ago"}: " + listOfNotNull(title?.takeIf { it.isNotBlank() }, text?.takeIf { it.isNotBlank() })
            .joinToString(": ") { "\"" + it.replace('\n', ' ').take(120) + "\"" }
}

/** Public holidays for a few countries, worked out from the year (no data file): fixed dates, "the nth weekday", and Easter. Pure. */
internal object Holidays {
    fun easter(year: Int): LocalDate {
        // Anonymous Gregorian algorithm.
        val a = year % 19; val b = year / 100; val c = year % 100; val d = b / 4; val e = b % 4
        val f = (b + 8) / 25; val g = (b - f + 1) / 3; val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4; val k = c % 4; val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31; val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }

    private fun nth(year: Int, month: Int, dow: DayOfWeek, n: Int) = LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, dow))
    private fun last(year: Int, month: Int, dow: DayOfWeek) = LocalDate.of(year, month, 1).with(TemporalAdjusters.lastInMonth(dow))
    private fun d(year: Int, month: Int, day: Int) = LocalDate.of(year, month, day)

    /** [country] is an ISO code ("US"); null when there is no table for it. Dates are the holidays themselves, not days off in lieu. */
    fun forYear(country: String, year: Int): List<Pair<LocalDate, String>>? {
        val e = easter(year)
        return when (country.uppercase()) {
            "US" -> listOf(d(year, 1, 1) to "New Year's Day", nth(year, 1, DayOfWeek.MONDAY, 3) to "Martin Luther King Jr. Day",
                nth(year, 2, DayOfWeek.MONDAY, 3) to "Presidents' Day", last(year, 5, DayOfWeek.MONDAY) to "Memorial Day",
                d(year, 6, 19) to "Juneteenth", d(year, 7, 4) to "Independence Day", nth(year, 9, DayOfWeek.MONDAY, 1) to "Labor Day",
                nth(year, 10, DayOfWeek.MONDAY, 2) to "Columbus Day", d(year, 11, 11) to "Veterans Day",
                nth(year, 11, DayOfWeek.THURSDAY, 4) to "Thanksgiving", d(year, 12, 25) to "Christmas Day")
            "GB" -> listOf(d(year, 1, 1) to "New Year's Day", e.minusDays(2) to "Good Friday", e.plusDays(1) to "Easter Monday",
                nth(year, 5, DayOfWeek.MONDAY, 1) to "Early May bank holiday", last(year, 5, DayOfWeek.MONDAY) to "Spring bank holiday",
                last(year, 8, DayOfWeek.MONDAY) to "Summer bank holiday", d(year, 12, 25) to "Christmas Day", d(year, 12, 26) to "Boxing Day")
            "CA" -> listOf(d(year, 1, 1) to "New Year's Day", e.minusDays(2) to "Good Friday",
                d(year, 5, 24).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) to "Victoria Day", d(year, 7, 1) to "Canada Day",
                nth(year, 9, DayOfWeek.MONDAY, 1) to "Labour Day", nth(year, 10, DayOfWeek.MONDAY, 2) to "Thanksgiving",
                d(year, 11, 11) to "Remembrance Day", d(year, 12, 25) to "Christmas Day", d(year, 12, 26) to "Boxing Day")
            "AU" -> listOf(d(year, 1, 1) to "New Year's Day", d(year, 1, 26) to "Australia Day", e.minusDays(2) to "Good Friday",
                e.plusDays(1) to "Easter Monday", d(year, 4, 25) to "Anzac Day", d(year, 12, 25) to "Christmas Day", d(year, 12, 26) to "Boxing Day")
            "DE" -> listOf(d(year, 1, 1) to "Neujahr", e.minusDays(2) to "Karfreitag", e.plusDays(1) to "Ostermontag", d(year, 5, 1) to "Tag der Arbeit",
                e.plusDays(39) to "Christi Himmelfahrt", e.plusDays(50) to "Pfingstmontag", d(year, 10, 3) to "Tag der Deutschen Einheit",
                d(year, 12, 25) to "1. Weihnachtstag", d(year, 12, 26) to "2. Weihnachtstag")
            "FR" -> listOf(d(year, 1, 1) to "Jour de l'an", e.plusDays(1) to "Lundi de Pâques", d(year, 5, 1) to "Fête du Travail",
                d(year, 5, 8) to "Victoire 1945", e.plusDays(39) to "Ascension", e.plusDays(50) to "Lundi de Pentecôte", d(year, 7, 14) to "Fête nationale",
                d(year, 8, 15) to "Assomption", d(year, 11, 1) to "Toussaint", d(year, 11, 11) to "Armistice", d(year, 12, 25) to "Noël")
            else -> null
        }?.sortedBy { it.first }
    }

    /** The next [count] holidays from [today] (today included). */
    fun upcoming(country: String, today: LocalDate, count: Int = 3): List<Pair<LocalDate, String>>? {
        val year = forYear(country, today.year) ?: return null
        return (year + forYear(country, today.year + 1).orEmpty()).filter { !it.first.isBefore(today) }.take(count)
    }

    fun line(country: String, today: LocalDate): String? = upcoming(country, today)?.let { list ->
        "Next public holidays ($country): " + list.joinToString(", ") { (date, name) ->
            "$name ${date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))}"
        }
    }
}

/**
 * The last few notifications, in memory only and never written to storage, so Qwen can answer "who messaged me". Kept for [KEEP_MS] at most,
 * [CAP] of them; ongoing ones, group summaries and Uno's own are never recorded, and one containing a blocked word is recorded without its words.
 */
internal object RecentNotifications {
    const val CAP = 30
    const val KEEP_MS = 6 * 60 * 60 * 1000L

    data class Entry(val packageName: String, val app: String, val title: String?, val text: String?, val at: Long)

    private val entries = ArrayDeque<Entry>()

    @Synchronized fun record(entry: Entry) {
        entries.removeAll { it.packageName == entry.packageName && it.title == entry.title && it.text == entry.text }
        entries.addFirst(entry)
        while (entries.size > CAP) entries.removeLast()
    }

    @Synchronized fun recent(now: Long, max: Int = 8): List<Entry> {
        entries.removeAll { now - it.at > KEEP_MS }
        return entries.take(max)
    }

    @Synchronized fun clear() = entries.clear()
}
