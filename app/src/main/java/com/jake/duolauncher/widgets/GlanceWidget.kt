package com.jake.duolauncher.widgets

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import android.app.AlarmManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** The Glance widget: its face (the drawing) and its own helpers. Registered in [UnoWidgets].
 *
 * A quiet at-a-glance of today, built entirely from what the phone already knows: the time, the date, the next alarm in
 * the clock app (a public call that needs no permission), and today's sunrise and sunset for the place set in Appearance.
 * It deliberately shows no weather and no calendar events, because those would need network access or a calendar
 * permission; those belong to a future launcher-lane decision, not this widget.
 */
@Composable
internal fun GlanceFace(slot: Int, onOptions: () -> Unit) {
    val context = LocalContext.current
    val now = rememberNow(60_000).atZone(ZoneId.systemDefault())
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    // Re-read the next alarm on the same minute tick; Android announces a change, and a minute is plenty of freshness.
    val previewing = LocalWidgetPreview.current
    val alarm = remember(now, previewing) { if (previewing) null else readNextAlarm(context) }
    val sampleAlarm = if (previewing) now.plusDays(1).withHour(7).withMinute(30) else null
    val schedule = remember(now.toLocalDate(), previewing) {
        val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val lat = if (previewing) PreviewSamples.SAMPLE_LATITUDE else prefs.getString("lat", null)?.toDoubleOrNull()
        val lon = if (previewing) PreviewSamples.SAMPLE_LONGITUDE else prefs.getString("lon", null)?.toDoubleOrNull()
        if (lat == null || lon == null) null
        else runCatching { solarSchedule(now.toLocalDate(), lat, lon, ZoneId.systemDefault()) }.getOrNull()
    }
    GlassCard(onClick = onOptions, modifier = Modifier.testTag("uno-glance-$slot")) {
        val ink = LocalGlassInk.current
        Text(now.format(DateTimeFormatter.ofPattern(Glance.timePattern(is24))), color = ink.primary,
            style = WidgetType.number(34.sp), maxLines = 1, modifier = Modifier.testTag("glance-time"))
        Text(now.format(DateTimeFormatter.ofPattern("EEE, MMM d")), color = ink.soft(), style = UnoType.caption1, maxLines = 1)
        if (alarm != null || sampleAlarm != null) Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(widgetAccent(WidgetColors.Warm).copy(alpha = .22f))
                .padding(horizontal = 7.dp, vertical = 3.dp).testTag("glance-alarm")) {
            Icon(Icons.Rounded.Alarm, null, tint = ink.soft(), modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text(Glance.alarmLine(now, sampleAlarm ?: Instant.ofEpochMilli(requireNotNull(alarm).triggerTime).atZone(now.zone), is24),
                color = ink.primary, style = UnoType.caption2, maxLines = 1)
        }
        if (schedule != null) Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(ink.soft(.14f))
                .padding(horizontal = 8.dp, vertical = 3.dp).testTag("glance-sun")) {
            Icon(Icons.Rounded.WbSunny, null, tint = ink.soft(), modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text(Glance.sunLine(schedule, is24), color = ink.primary.copy(alpha = .85f), style = UnoType.caption2, maxLines = 1)
        }
    }
}

private fun readNextAlarm(context: Context): AlarmManager.AlarmClockInfo? =
    runCatching { context.getSystemService(AlarmManager::class.java)?.nextAlarmClock }.getOrNull()

/** The Glance widget's rules, kept apart from the drawing so each is unit-tested. */
internal object Glance {
    private fun shortPattern(is24: Boolean) = DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm")

    /** The clock style the launcher uses elsewhere: 24-hour, or 12-hour with a meridiem. */
    fun timePattern(is24: Boolean): String = if (is24) "HH:mm" else "h:mm a"

    /** "7:30 AM", with the day appended when the alarm is not today: "Tomorrow", then the weekday up to six days out ("Mon"), then the
     *  date. Root cause of the old line reading "7:30 AM" for an alarm days away: only tomorrow and yesterday were named, so a
     *  weekday-only alarm set on a Friday night looked like it would ring tomorrow morning. */
    fun alarmLine(now: ZonedDateTime, alarmTrigger: ZonedDateTime, is24: Boolean, locale: java.util.Locale = java.util.Locale.getDefault()): String {
        val time = alarmTrigger.format(DateTimeFormatter.ofPattern(timePattern(is24), locale))
        val days = ChronoUnit.DAYS.between(now.toLocalDate(), alarmTrigger.toLocalDate())
        return when {
            days == 1L -> "$time · Tomorrow"
            days == -1L -> "$time · Yesterday"
            days in 2L..6L -> "$time · " + alarmTrigger.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale)
            days >= 7L -> "$time · " + alarmTrigger.format(DateTimeFormatter.ofPattern("MMM d", locale))
            else -> time
        }
    }

    /** "↑ 6:58 AM   ↓ 7:02 PM", or the plain truth when the sun does not rise or set at all. */
    fun sunLine(schedule: SolarSchedule, is24: Boolean): String {
        val pattern = DateTimeFormatter.ofPattern(timePattern(is24))
        return when (schedule.polar) {
            PolarDaylight.DAY -> "The sun does not set today"
            PolarDaylight.NIGHT -> "The sun does not rise today"
            // No AM or PM here: a sunrise is in the morning and a sunset in the evening, and the room is better spent on the numbers.
            else -> "↑ ${requireNotNull(schedule.sunrise).format(shortPattern(is24))}  ↓ ${requireNotNull(schedule.sunset).format(shortPattern(is24))}"
        }
    }
}
