package com.jake.duolauncher

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.cos

/** The pure parts of the built-in widget library, kept apart from the drawing so each rule is unit-tested. */

/** A month as a grid of weeks. */
internal object MonthGrid {
    /** The month's days laid out in rows of seven from [firstDay], with null for the empty cells before the 1st and after the
     * last day. The length is always a whole number of weeks.
     */
    fun cells(month: YearMonth, firstDay: DayOfWeek): List<Int?> {
        val lead = Math.floorMod(month.atDay(1).dayOfWeek.value - firstDay.value, 7)
        val days: List<Int?> = (1..month.lengthOfMonth()).toList()
        val padded = List<Int?>(lead) { null } + days
        return padded + List<Int?>(Math.floorMod(-padded.size, 7)) { null }
    }

    /** The seven weekday initials in display order. */
    fun headers(firstDay: DayOfWeek): List<DayOfWeek> = List(7) { firstDay.plus(it.toLong()) }
}

/** How far through the day, month and year it is. */
internal data class TimeProgress(val day: Float, val month: Float, val year: Float, val dayOfYear: Int, val daysInYear: Int) {
    val daysLeftInYear get() = daysInYear - dayOfYear

    companion object {
        fun of(now: LocalDateTime): TimeProgress {
            val date = now.toLocalDate()
            val secondOfDay = now.toLocalTime().toSecondOfDay() + now.nano / 1e9
            val day = (secondOfDay / 86_400.0).toFloat()
            val monthDays = date.lengthOfMonth()
            val month = ((date.dayOfMonth - 1 + day) / monthDays).coerceIn(0f, 1f)
            val yearDays = date.lengthOfYear()
            val year = ((date.dayOfYear - 1 + day) / yearDays).coerceIn(0f, 1f)
            return TimeProgress(day, month, year, date.dayOfYear, yearDays)
        }
    }
}

/** The moon's phase from the date alone (no sensors, no network): the mean synodic month counted from a known new moon. */
internal object MoonPhase {
    private const val SYNODIC_DAYS = 29.530588853
    private val REFERENCE_NEW_MOON = ZonedDateTime.of(2000, 1, 6, 18, 14, 0, 0, ZoneOffset.UTC)

    /** Days since the last new moon, 0 until 29.53. */
    fun ageDays(at: ZonedDateTime): Double {
        val days = ChronoUnit.SECONDS.between(REFERENCE_NEW_MOON, at) / 86_400.0
        return ((days % SYNODIC_DAYS) + SYNODIC_DAYS) % SYNODIC_DAYS
    }

    /** 0 new, 0.5 full, back to 1 at the next new moon. */
    fun cycle(at: ZonedDateTime): Double = ageDays(at) / SYNODIC_DAYS

    /** The lit fraction of the disc, 0..1. */
    fun illumination(at: ZonedDateTime): Double = (1.0 - cos(2.0 * PI * cycle(at))) / 2.0

    fun name(cycle: Double): String = when {
        cycle < .03 || cycle >= .97 -> "New Moon"
        cycle < .22 -> "Waxing Crescent"
        cycle < .28 -> "First Quarter"
        cycle < .47 -> "Waxing Gibbous"
        cycle < .53 -> "Full Moon"
        cycle < .72 -> "Waning Gibbous"
        cycle < .78 -> "Last Quarter"
        else -> "Waning Crescent"
    }

    /** True while the lit side grows (it is on the right in the northern hemisphere). */
    fun waxing(cycle: Double) = cycle < .5
}

/** Wording for a countdown to a date. */
internal object Countdown {
    fun daysUntil(today: LocalDate, target: LocalDate): Long = ChronoUnit.DAYS.between(today, target)

    /** The big number and its unit: "12" "days", "Today", "3" "days ago". */
    fun label(today: LocalDate, target: LocalDate): Pair<String, String> {
        val days = daysUntil(today, target)
        return when {
            days == 0L -> "Today" to ""
            days == 1L -> "1" to "day"
            days > 1L -> days.toString() to "days"
            days == -1L -> "1" to "day ago"
            else -> (-days).toString() to "days ago"
        }
    }

    fun parse(raw: String?): LocalDate? = runCatching { LocalDate.parse(raw) }.getOrNull()
}

/** Wording for the sun widget. */
internal object SunText {
    /** "13 h 4 min" between sunrise and sunset. */
    fun dayLength(sunrise: ZonedDateTime, sunset: ZonedDateTime): String {
        val minutes = ChronoUnit.MINUTES.between(sunrise, sunset).coerceAtLeast(0)
        return "${minutes / 60} h ${minutes % 60} min"
    }
}

/** Counter and note limits. */
internal object WidgetText {
    const val MAX_NOTE = 600
    const val MAX_NAME = 24
    const val MAX_COUNT = 999_999

    fun clampCount(value: Long) = value.coerceIn(0L, MAX_COUNT.toLong()).toInt()
    fun note(text: String) = text.take(MAX_NOTE)
    fun name(text: String) = text.take(MAX_NAME)
}
