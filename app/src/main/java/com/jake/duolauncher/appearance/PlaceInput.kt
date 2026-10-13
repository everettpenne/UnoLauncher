package com.jake.duolauncher.appearance

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Reading a place typed or pasted by hand, and describing a saved place back to the person. Kept pure so it is tested.
 *
 * Root cause of "entering a place by hand never works" (reported on the phone): the fields accepted only a dot decimal and a
 * plain minus, so "40,7128", a pasted "40.7128, -74.0060" or Android's typographic minus all failed, and the failure message
 * could sit under the keyboard; and a success changed nothing visible (sunrise/sunset mode only flips the theme at the next
 * sunrise or sunset), so the person could not tell the place had been saved. This accepts the common ways people write
 * coordinates and the page shows what was saved, with today's sunrise and sunset, as soon as there is a place.
 */
internal object PlaceInput {
    sealed interface Result {
        data class Ok(val latitude: Double, val longitude: Double) : Result
        data class Bad(val message: String) : Result
    }

    const val RANGE_MESSAGE = "Enter a latitude from −90 to 90 and a longitude from −180 to 180, for example 40.7128 and −74.0060."

    /** [latitudeField] and [longitudeField] as typed. A pair pasted into either field ("40.7128, -74.0060") fills both. */
    fun parse(latitudeField: String, longitudeField: String): Result {
        var lat = latitudeField.trim(); var lon = longitudeField.trim()
        // A pair pasted into either field wins over what the other field held: editing a saved place leaves the old value
        // in the other field, and a pasted pair is the person's newest intent. (A pair needs two real numbers, so "33.8 S"
        // is a latitude with a hemisphere letter, not a pair.)
        (pairIn(lat) ?: pairIn(lon))?.let { (a, b) -> lat = a; lon = b }
        val la = number(lat, "NS"); val lo = number(lon, "EW")
        if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0) return Result.Bad(RANGE_MESSAGE)
        return Result.Ok(la, lo)
    }

    private fun pairIn(text: String): Pair<String, String>? =
        splitPair(text)?.takeIf { (a, b) -> number(a, "NS") != null && number(b, "EW") != null }

    /** "40.7128, -74.0060", "40.7128 -74.0060", "40,7128; -74,0060", "40.7128,-74.0060": two numbers in one string, else null. */
    internal fun splitPair(text: String): Pair<String, String>? {
        val t = normalise(text)
        Regex("""^(.+?)\s*[;/]\s*(.+)$""").find(t)?.let { return it.groupValues[1] to it.groupValues[2] }
        // A comma followed by a space ends the first number; so does a comma straight before a sign.
        Regex("""^(.+?),\s+(.+)$""").find(t)?.let { return it.groupValues[1] to it.groupValues[2] }
        Regex("""^([^,\s]+),([-+].+)$""").find(t)?.let { return it.groupValues[1] to it.groupValues[2] }
        // A comma between two dotted numbers ("40.7,-74.0" handled above; "40.7128,74.0060" has a dot on each side).
        Regex("""^(-?\d+\.\d+),(-?\d+\.\d+)$""").find(t)?.let { return it.groupValues[1] to it.groupValues[2] }
        val parts = t.split(Regex("""\s+"""))
        return if (parts.size == 2) parts[0] to parts[1] else null
    }

    private fun normalise(text: String) = text.trim().replace('−', '-').replace('–', '-').replace('—', '-')
        .replace("°", "").replace("º", "")

    /** One coordinate: a dot or comma decimal, an optional sign, and an optional hemisphere letter from [axis] (S and W are negative). */
    internal fun number(text: String, axis: String): Double? {
        var t = normalise(text).uppercase().replace(" ", "")
        if (t.isEmpty()) return null
        var sign = 1.0
        t.lastOrNull()?.takeIf { it in axis }?.let { if (it == axis[1]) sign = -1.0; t = t.dropLast(1) }
        t.firstOrNull()?.takeIf { it in axis }?.let { if (it == axis[1]) sign = -1.0; t = t.drop(1) }
        if (t.count { it == ',' } == 1 && '.' !in t) t = t.replace(',', '.')
        if (!Regex("""^[-+]?\d+(\.\d+)?$""").matches(t) && !Regex("""^[-+]?\.\d+$""").matches(t)) return null
        val v = t.toDoubleOrNull() ?: return null
        return if (v.isFinite()) sign * v else null
    }

    /** "40.7° N, 74.0° W". */
    fun coordinates(latitude: Double, longitude: Double): String =
        "%.1f° %s, %.1f° %s".format(abs(latitude), if (latitude >= 0) "N" else "S", abs(longitude), if (longitude >= 0) "E" else "W")

    /** What the page shows under a saved place: its name, where it is, and today's sunrise and sunset there. */
    fun describe(place: String, latitude: Double, longitude: Double, today: LocalDate, zone: ZoneId, is24Hour: Boolean): String {
        val name = place.trim().ifEmpty { "Your place" }
        val where = coordinates(latitude, longitude)
        val format = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a")
        val schedule = runCatching { solarSchedule(today, latitude, longitude, zone) }.getOrNull()
            ?: return "Saved: $name, $where."
        return when (schedule.polar) {
            PolarDaylight.DAY -> "Saved: $name, $where. The sun does not set today."
            PolarDaylight.NIGHT -> "Saved: $name, $where. The sun does not rise today."
            PolarDaylight.NORMAL -> "Saved: $name, $where. Sunrise ${schedule.sunrise?.format(format)}, sunset ${schedule.sunset?.format(format)} today."
        }
    }
}
