package com.jake.duolauncher.widgets

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** The World clock widget: its face (the drawing) and its own helpers. Registered in [UnoWidgets]. */

@Composable
internal fun WorldClockFace(slot: Int, onOptions: () -> Unit) {
    val now = rememberNow(60_000).atZone(ZoneId.systemDefault())
    val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val zones = WorldClock.parseZones(WidgetData.get(slot, "zones"))
    var editing by remember { mutableStateOf(false) }
    GlassCard(onClick = { editing = true }, modifier = Modifier.testTag("uno-world-clock-$slot")) {
        val ink = LocalGlassInk.current
        CardTitle(Icons.Rounded.Public, "World clock")
        if (zones.isEmpty()) Text(stringResource(com.jake.duolauncher.R.string.tap_to_add_a_place), color = ink.soft(), fontSize = 13.sp)
        else Column(Modifier.weight(1f).fillMaxWidth().padding(top = 2.dp), verticalArrangement = Arrangement.Center) {
            zones.forEach { zoneId ->
                val row = WorldClock.row(requireNotNull(WorldClock.cityFor(zoneId)), now, is24Hour)
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).testTag("world-clock-row-${row.tag}"),
                    verticalAlignment = Alignment.CenterVertically) {
                    // A small sun or moon: is it day or night there?
                    Box(Modifier.padding(end = 7.dp).size(8.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (row.daytime) widgetAccent(WidgetColors.Gold) else WidgetColors.Violet.copy(alpha = .85f)))
                    Text(row.label, color = ink.primary.copy(alpha = .85f), fontSize = 12.sp, maxLines = 1, softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    // "+1" or "-1": the place is a day ahead of, or behind, this phone (the full words took the room the name needs).
                    row.dayNote?.let { Text(if (it == "Tomorrow") "+1" else "\u22121", color = ink.soft(.75f), fontSize = 10.sp, maxLines = 1,
                        modifier = Modifier.padding(end = 5.dp).semantics { contentDescription = it }) }
                    Text(row.time, color = ink.primary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
    if (editing) {
        var draft by remember { mutableStateOf(zones) }
        var query by remember { mutableStateOf("") }
        GlassDialog(onDismissRequest = { editing = false }, title = "World clock", actions = {
            GlassButton("Cancel", { editing = false })
            GlassButton("Save", {
                WidgetData.set(slot, "zones", WorldClock.serializeZones(draft))
                editing = false
            }, prominent = true, tag = "world-clock-save")
        }) {
            if (draft.isEmpty()) GlassNote("Add up to ${WorldClock.MAX_CITIES} places; each shows its time here, and tomorrow or yesterday when it is on another day.")
            draft.forEachIndexed { index, zoneId ->
                GlassRow(WorldClock.cityFor(zoneId)?.label ?: zoneId, onClick = { draft = WorldClock.removeAt(draft, index) },
                    modifier = Modifier.testTag("world-clock-remove-$index"),
                    trailing = { Icon(Icons.Rounded.Close, "Remove ${WorldClock.cityFor(zoneId)?.label ?: zoneId}") })
            }
            if (draft.size < WorldClock.MAX_CITIES) {
                GlassTextField(query, { query = it }, placeholder = "Add a place", singleLine = true, tag = "world-clock-search")
                WorldClock.search(query, draft).forEach { city ->
                    GlassRow(city.label, onClick = { draft = WorldClock.add(draft, city.zoneId); query = "" },
                        modifier = Modifier.testTag("world-clock-add-${city.tag}"),
                        trailing = { GlassNote(WorldClock.offsetLabel(city.zoneId, Instant.now())) })
                }
            } else GlassNote("${WorldClock.MAX_CITIES} places at most; remove one to add another.")
        }
    }
}

/** The World clock's rules, kept apart from the drawing so each is unit-tested. */
internal object WorldClock {
    const val MAX_CITIES = 4
    const val MAX_RESULTS = 8

    /** A place the widget can show: its display name and its time zone. */
    data class City(val label: String, val zoneId: String) {
        /** A test-tag-safe form of the zone id. */
        val tag: String get() = zoneId.replace('/', '_')
    }

    /** What one row of the face shows: the place, its time, and a note when it is on another day than this phone. */
    data class Row(val label: String, val time: String, val dayNote: String?, val tag: String, val daytime: Boolean = true)

    /** Well-known places across the zones, so a place can be picked by name and daylight saving stays Android's job. */
    val cities: List<City> = listOf(
        City("Honolulu", "Pacific/Honolulu"),
        City("Anchorage", "America/Anchorage"),
        City("Vancouver", "America/Vancouver"),
        City("Los Angeles", "America/Los_Angeles"),
        City("Phoenix", "America/Phoenix"),
        City("Denver", "America/Denver"),
        City("Chicago", "America/Chicago"),
        City("Mexico City", "America/Mexico_City"),
        City("New York", "America/New_York"),
        City("Toronto", "America/Toronto"),
        City("Santiago", "America/Santiago"),
        City("São Paulo", "America/Sao_Paulo"),
        City("Reykjavik", "Atlantic/Reykjavik"),
        City("London", "Europe/London"),
        City("Dublin", "Europe/Dublin"),
        City("Lisbon", "Europe/Lisbon"),
        City("Paris", "Europe/Paris"),
        City("Madrid", "Europe/Madrid"),
        City("Berlin", "Europe/Berlin"),
        City("Rome", "Europe/Rome"),
        City("Amsterdam", "Europe/Amsterdam"),
        City("Stockholm", "Europe/Stockholm"),
        City("Athens", "Europe/Athens"),
        City("Helsinki", "Europe/Helsinki"),
        City("Kyiv", "Europe/Kyiv"),
        City("Istanbul", "Europe/Istanbul"),
        City("Moscow", "Europe/Moscow"),
        City("Cairo", "Africa/Cairo"),
        City("Lagos", "Africa/Lagos"),
        City("Nairobi", "Africa/Nairobi"),
        City("Johannesburg", "Africa/Johannesburg"),
        City("Dubai", "Asia/Dubai"),
        City("Karachi", "Asia/Karachi"),
        City("Delhi", "Asia/Kolkata"),
        City("Dhaka", "Asia/Dhaka"),
        City("Bangkok", "Asia/Bangkok"),
        City("Jakarta", "Asia/Jakarta"),
        City("Singapore", "Asia/Singapore"),
        City("Hong Kong", "Asia/Hong_Kong"),
        City("Shanghai", "Asia/Shanghai"),
        City("Taipei", "Asia/Taipei"),
        City("Seoul", "Asia/Seoul"),
        City("Tokyo", "Asia/Tokyo"),
        City("Perth", "Australia/Perth"),
        City("Adelaide", "Australia/Adelaide"),
        City("Sydney", "Australia/Sydney"),
        City("Auckland", "Pacific/Auckland"),
        City("Fiji", "Pacific/Fiji"),
    )

    fun cityFor(zoneId: String): City? = cities.firstOrNull { it.zoneId == zoneId }

    /** "Europe/London,Asia/Tokyo" style storage. Unknown or repeated ids are skipped, never fatal (a list from another
     *  version may name a place this one does not know), and the list never holds more than [MAX_CITIES]. */
    fun parseZones(raw: String?): List<String> = raw.orEmpty().split(',').map { it.trim() }
        .filter { zone -> cities.any { it.zoneId == zone } }.distinct().take(MAX_CITIES)

    fun serializeZones(zones: List<String>): String = zones.joinToString(",")

    fun add(zones: List<String>, zoneId: String): List<String> =
        if (zoneId in zones || zones.size >= MAX_CITIES || cityFor(zoneId) == null) zones else zones + zoneId

    fun removeAt(zones: List<String>, index: Int): List<String> =
        if (index in zones.indices) zones.toMutableList().also { it.removeAt(index) } else zones

    /** Places matching [query] (case-insensitive) that are not already chosen; with an empty query, the first few. */
    fun search(query: String, chosen: List<String>): List<City> {
        val q = query.trim().lowercase()
        return cities.filter { it.zoneId !in chosen && (q.isEmpty() || it.label.lowercase().contains(q)) }.take(MAX_RESULTS)
    }

    /** One face row for [city] at [deviceNow]: its local time in the phone's 12/24-hour style, and Tomorrow or
     *  Yesterday when its date is not the phone's. */
    fun row(city: City, deviceNow: ZonedDateTime, is24Hour: Boolean): Row {
        val cityNow = deviceNow.withZoneSameInstant(ZoneId.of(city.zoneId))
        val time = cityNow.format(DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a"))
        return Row(city.label, time, dayNote(deviceNow.toLocalDate(), cityNow.toLocalDate()), city.tag, daytime = cityNow.hour in 6..17)
    }

    /** A city can only ever be a day ahead of or behind the phone. */
    fun dayNote(deviceDate: LocalDate, cityDate: LocalDate): String? =
        when (ChronoUnit.DAYS.between(deviceDate, cityDate)) {
            1L -> "Tomorrow"
            -1L -> "Yesterday"
            else -> null
        }

    /** "GMT", "GMT+9" or "GMT+5:30" for [zoneId] at [at]; empty for an unknown zone. */
    fun offsetLabel(zoneId: String, at: Instant): String {
        val offset = runCatching { ZoneId.of(zoneId).rules.getOffset(at) }.getOrNull() ?: return ""
        val seconds = offset.totalSeconds
        if (seconds == 0) return "GMT"
        val sign = if (seconds < 0) "-" else "+"
        val abs = kotlin.math.abs(seconds)
        val hours = abs / 3600
        val minutes = (abs % 3600) / 60
        return if (minutes == 0) "GMT$sign$hours" else "GMT$sign$hours:%02d".format(minutes)
    }
}
