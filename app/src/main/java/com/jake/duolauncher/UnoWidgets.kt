package com.jake.duolauncher

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The library of Uno's own widgets. Each is a built-in widget (a negative id) drawn by the launcher itself: no provider app, no
 * permission, nothing read beyond the date, the battery level and what you type into it, and nothing sent anywhere. They are placed,
 * moved, resized and removed like any widget; the ones with something to type (countdown, note, counter) keep it in
 * [WidgetData], in the app's private storage.
 */
internal data class UnoWidgetDef(
    val id: Int, val label: String, val blurb: String, val spanX: Int, val spanY: Int,
    val face: @Composable (slot: Int, onOptions: () -> Unit) -> Unit,
)

internal object UnoWidgets {
    const val BATTERY = -20
    const val MONTH = -21
    const val PROGRESS = -22
    const val MOON = -23
    const val COUNTDOWN = -24
    const val NOTE = -25
    const val COUNTER = -26
    const val TIMERS = -27
    const val SUN = -28

    val all: List<UnoWidgetDef> = listOf(
        UnoWidgetDef(BATTERY, "Battery", "A ring for the charge level, and time to full while charging.", 2, 2) { s, o -> BatteryFace(s, o) },
        UnoWidgetDef(MONTH, "Month", "This month's calendar, with today marked. Reads no calendar app.", 2, 2) { s, o -> MonthFace(s, o) },
        UnoWidgetDef(PROGRESS, "Day, month and year", "How far through each you are, and the days left in the year.", 2, 2) { s, o -> ProgressFace(s, o) },
        UnoWidgetDef(MOON, "Moon phase", "Today's moon, drawn from the date alone.", 2, 2) { s, o -> MoonFace(s, o) },
        UnoWidgetDef(COUNTDOWN, "Countdown", "Days until (or since) a date and a name you choose. Tap to set it.", 2, 2) { s, o -> CountdownFace(s, o) },
        UnoWidgetDef(NOTE, "Note", "A short note kept on this phone. Tap to write.", 2, 2) { s, o -> NoteFace(s, o) },
        UnoWidgetDef(COUNTER, "Counter", "Tap to add one; a small button takes one away. Tap the name to rename or reset.", 2, 2) { s, o -> CounterFace(s, o) },
        UnoWidgetDef(TIMERS, "Timers", "One tap starts a 5, 10, 25 or 45 minute timer in the island.", 2, 2) { s, o -> TimersFace(s, o) },
        UnoWidgetDef(SUN, "Sunrise and sunset", "Today's times for the place you set in Appearance. Worked out on the phone.", 2, 2) { s, o -> SunFace(s, o) },
    )

    fun byId(id: Int): UnoWidgetDef? = all.firstOrNull { it.id == id }
}

/** What the typed widgets remember, per Home slot, in private storage. */
internal object WidgetData {
    private var prefs: android.content.SharedPreferences? = null
    var values by mutableStateOf<Map<String, String>>(emptyMap()); private set

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("uno_widget_data", Context.MODE_PRIVATE)
        reload()
    }

    /** Re-reads everything (after a settings restore). */
    fun reload() { values = prefs?.all?.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }?.toMap().orEmpty() }

    private fun key(slot: Int, field: String) = "${slot}_$field"
    fun get(slot: Int, field: String, default: String = ""): String = values[key(slot, field)] ?: default
    fun getInt(slot: Int, field: String): Int = get(slot, field, "0").toIntOrNull() ?: 0

    fun set(slot: Int, field: String, value: String) {
        values = values + (key(slot, field) to value)
        prefs?.edit()?.putString(key(slot, field), value)?.apply()
    }

    /** Forgets the data of widgets that are no longer on Home, so a reused slot never inherits an old note. */
    fun prune(placedSlots: Set<Int>) {
        val keep = values.filterKeys { k -> k.substringBefore('_').toIntOrNull() in placedSlots }
        if (keep.size != values.size) {
            val editor = prefs?.edit()
            values.keys.filterNot { it in keep }.forEach { editor?.remove(it) }
            editor?.apply()
            values = keep
        }
    }
}

// ---- shared drawing helpers ------------------------------------------------------------------------------------------------

@Composable
private fun rememberNow(periodMs: Long): LocalDateTime {
    val now by produceState(LocalDateTime.now(), periodMs) { while (true) { value = LocalDateTime.now(); delay(periodMs) } }
    return now
}

private fun onInk(ink: GlassInk) = if (ink.primary.luminance() > .5f) Color.Black else Color.White

@Composable
private fun CardTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val ink = LocalGlassInk.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(icon, null, tint = ink.soft(), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, color = ink.soft(), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---- Battery ----------------------------------------------------------------------------------------------------------------

private data class BatteryReading(val level: Int, val charging: Boolean, val toFull: String?)

private fun readBattery(context: Context): BatteryReading {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
    val toFull = if (charging && status != BatteryManager.BATTERY_STATUS_FULL)
        runCatching { ChargeText.toFull(context.getSystemService(BatteryManager::class.java).computeChargeTimeRemaining()) }.getOrNull() else null
    return BatteryReading(percent, charging, toFull)
}

@Composable
private fun BatteryFace(slot: Int, onOptions: () -> Unit) {
    val context = LocalContext.current
    val reading by produceState(BatteryReading(-1, false, null)) {
        while (true) { value = withContext(Dispatchers.Default) { readBattery(context) }; delay(30_000) }
    }
    GlassCard(onClick = onOptions, modifier = Modifier.testTag("uno-battery-$slot")) {
        val ink = LocalGlassInk.current
        CardTitle(Icons.Rounded.BatteryChargingFull, "Battery")
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            val tint = when { reading.charging -> IosGreen; reading.level in 0..20 -> IosOrange; else -> ink.primary }
            Canvas(Modifier.fillMaxHeight().aspectRatio(1f)) {
                val stroke = 5.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(ink.soft(.22f), -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                if (reading.level >= 0) drawArc(tint, -90f, 360f * reading.level / 100f, false, Offset(inset, inset), arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Text(if (reading.level >= 0) "${reading.level}%" else "—", color = ink.primary, fontSize = 13.sp, fontWeight = FontWeight.Normal)
        }
        Text(reading.toFull ?: if (reading.charging) "Charging" else "On battery", color = ink.soft(), fontSize = 11.sp, maxLines = 1,
            modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

// ---- Month ------------------------------------------------------------------------------------------------------------------

@Composable
private fun MonthFace(slot: Int, onOptions: () -> Unit) {
    val today = rememberNow(60_000).toLocalDate()
    val month = YearMonth.from(today)
    val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val cells = remember(month, first) { MonthGrid.cells(month, first) }
    GlassCard(onClick = onOptions, padding = 10.dp, modifier = Modifier.testTag("uno-month-$slot")) {
        val ink = LocalGlassInk.current
        Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), color = ink.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            MonthGrid.headers(first).forEach { day ->
                Text(day.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()), color = ink.soft(.6f), fontSize = 9.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth()) {
            cells.chunked(7).forEach { week ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    week.forEach { day ->
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            if (day != null) {
                                val isToday = day == today.dayOfMonth
                                Box(Modifier.size(15.dp).then(if (isToday) Modifier.background(ink.primary, CircleShape) else Modifier),
                                    contentAlignment = Alignment.Center) {
                                    Text(day.toString(), color = if (isToday) onInk(ink) else ink.primary, fontSize = 9.sp, lineHeight = 10.sp,
                                        fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Day, month and year ----------------------------------------------------------------------------------------------------

@Composable
private fun ProgressFace(slot: Int, onOptions: () -> Unit) {
    val progress = TimeProgress.of(rememberNow(60_000))
    GlassCard(onClick = onOptions, modifier = Modifier.testTag("uno-progress-$slot")) {
        val ink = LocalGlassInk.current
        CardTitle(Icons.Rounded.Timelapse, "${LocalDate.now().year} · ${progress.daysLeftInYear} days left")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f).padding(top = 6.dp)) {
            listOf("Day" to progress.day, "Month" to progress.month, "Year" to progress.year).forEach { (label, fraction) ->
                Column {
                    Row { Text(label, color = ink.primary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text("${(fraction * 100).toInt()}%", color = ink.soft(), fontSize = 11.sp) }
                    LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                        color = ink.primary, trackColor = ink.soft(.22f), gapSize = 0.dp, drawStopIndicator = {})
                }
            }
        }
    }
}

// ---- Moon -------------------------------------------------------------------------------------------------------------------

@Composable
private fun MoonFace(slot: Int, onOptions: () -> Unit) {
    val now = rememberNow(10 * 60_000L)
    val at = now.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC)
    val cycle = MoonPhase.cycle(at)
    GlassCard(onClick = onOptions, modifier = Modifier.testTag("uno-moon-$slot")) {
        val ink = LocalGlassInk.current
        CardTitle(Icons.Rounded.Bedtime, "Moon")
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxHeight().aspectRatio(1f)) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(ink.soft(.28f), r, c)
                drawCircle(ink.soft(.5f), r, c, style = Stroke(1.dp.toPx()))
                val k = cos(2.0 * Math.PI * cycle).toFloat()
                val rx = r * kotlin.math.abs(k)
                val lit = Path().apply {
                    arcTo(Rect(c.x - r, c.y - r, c.x + r, c.y + r), -90f, 180f, true)
                    arcTo(Rect(c.x - rx, c.y - r, c.x + rx, c.y + r), 90f, if (k > 0f) -180f else 180f, false)
                    close()
                }
                // The lit side is on the right while the moon waxes and on the left while it wanes.
                scale(if (MoonPhase.waxing(cycle)) 1f else -1f, 1f, pivot = c) { drawPath(lit, Color(0xFFF4F1DE), style = Fill) }
            }
        }
        Text(MoonPhase.name(cycle), color = ink.primary, fontSize = 12.sp, maxLines = 1, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("${(MoonPhase.illumination(at) * 100).toInt()}% lit", color = ink.soft(), fontSize = 11.sp, maxLines = 1,
            modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

private fun cos(x: Double) = kotlin.math.cos(x)

// ---- Countdown --------------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountdownFace(slot: Int, onOptions: () -> Unit) {
    val today = rememberNow(60_000).toLocalDate()
    val name = WidgetData.get(slot, "name", "Countdown")
    val target = Countdown.parse(WidgetData.get(slot, "date"))
    var editing by remember { mutableStateOf(false) }
    GlassCard(onClick = { editing = true }, modifier = Modifier.testTag("uno-countdown-$slot")) {
        val ink = LocalGlassInk.current
        CardTitle(Icons.Rounded.Event, name)
        if (target == null) Text("Tap to set a date", color = ink.soft(), fontSize = 13.sp)
        else {
            val (big, unit) = Countdown.label(today, target)
            Column {
                Text(big, color = ink.primary, fontSize = 38.sp, fontWeight = FontWeight.Light, lineHeight = 40.sp, maxLines = 1)
                if (unit.isNotEmpty()) Text(unit, color = ink.soft(), fontSize = 13.sp)
            }
            Text(target.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")), color = ink.soft(), fontSize = 11.sp, maxLines = 1)
        }
    }
    if (editing) {
        var draftName by remember { mutableStateOf(name) }
        var draftDate by remember { mutableStateOf(target) }
        var picking by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { editing = false }, title = { Text("Countdown") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(draftName, { draftName = WidgetText.name(it) }, Modifier.fillMaxWidth().testTag("countdown-name"),
                    singleLine = true, label = { Text("Name") })
                OutlinedButton(onClick = { picking = true }, Modifier.fillMaxWidth().testTag("countdown-date")) {
                    Text(draftDate?.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")) ?: "Choose a date")
                }
            }
        }, confirmButton = { TextButton(onClick = {
            WidgetData.set(slot, "name", draftName.ifBlank { "Countdown" })
            WidgetData.set(slot, "date", draftDate?.toString().orEmpty())
            editing = false
        }, Modifier.testTag("countdown-save")) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } })
        if (picking) {
            val state = rememberDatePickerState(initialSelectedDateMillis = (draftDate ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            DatePickerDialog(onDismissRequest = { picking = false }, confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { draftDate = java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                picking = false
            }) { Text("OK") } }, dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } }) { DatePicker(state) }
        }
    }
}

// ---- Note -------------------------------------------------------------------------------------------------------------------

@Composable
private fun NoteFace(slot: Int, onOptions: () -> Unit) {
    val note = WidgetData.get(slot, "note")
    var editing by remember { mutableStateOf(false) }
    GlassCard(onClick = { editing = true }, modifier = Modifier.testTag("uno-note-$slot")) {
        val ink = LocalGlassInk.current
        CardTitle(Icons.Rounded.EditNote, "Note")
        Text(note.ifBlank { "Tap to write a note" }, color = if (note.isBlank()) ink.soft() else ink.primary, fontSize = 13.sp,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp))
    }
    if (editing) {
        var draft by remember { mutableStateOf(note) }
        AlertDialog(onDismissRequest = { editing = false }, title = { Text("Note") }, text = {
            Column {
                OutlinedTextField(draft, { draft = WidgetText.note(it) }, Modifier.fillMaxWidth().heightIn(min = 140.dp).testTag("note-field"), maxLines = 10)
                Text("${draft.length} / ${WidgetText.MAX_NOTE}. Kept on this phone only.", fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }, confirmButton = { TextButton(onClick = { WidgetData.set(slot, "note", draft); editing = false }, Modifier.testTag("note-save")) { Text("Done") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } })
    }
}

// ---- Counter ----------------------------------------------------------------------------------------------------------------

@Composable
private fun CounterFace(slot: Int, onOptions: () -> Unit) {
    val count = WidgetData.getInt(slot, "count")
    val name = WidgetData.get(slot, "name", "Counter")
    var editing by remember { mutableStateOf(false) }
    GlassCard(onClick = { WidgetData.set(slot, "count", WidgetText.clampCount(count + 1L).toString()) },
        modifier = Modifier.testTag("uno-counter-$slot")) {
        val ink = LocalGlassInk.current
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { editing = true }.testTag("counter-name")) { CardTitle(Icons.Rounded.Add, name) }
            Box(Modifier.size(28.dp).clip(CircleShape).background(ink.soft(.18f))
                .clickable { WidgetData.set(slot, "count", WidgetText.clampCount(count - 1L).toString()) }.testTag("counter-minus"),
                contentAlignment = Alignment.Center) { Text("−", color = ink.primary, fontSize = 16.sp) }
        }
        Text(count.toString(), color = ink.primary, fontSize = 44.sp, fontWeight = FontWeight.Light, maxLines = 1,
            modifier = Modifier.align(Alignment.CenterHorizontally).testTag("counter-value"))
        Text("Tap to add one", color = ink.soft(), fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
    if (editing) {
        var draft by remember { mutableStateOf(name) }
        AlertDialog(onDismissRequest = { editing = false }, title = { Text("Counter") }, text = {
            OutlinedTextField(draft, { draft = WidgetText.name(it) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name") })
        }, confirmButton = { TextButton(onClick = { WidgetData.set(slot, "name", draft.ifBlank { "Counter" }); editing = false }) { Text("Save") } },
            dismissButton = { Row { TextButton(onClick = { WidgetData.set(slot, "count", "0"); editing = false }) { Text("Reset to 0") }
                TextButton(onClick = { editing = false }) { Text("Cancel") } } })
    }
}

// ---- Timers -----------------------------------------------------------------------------------------------------------------

@Composable
private fun TimersFace(slot: Int, onOptions: () -> Unit) {
    val context = LocalContext.current
    val now by produceState(System.currentTimeMillis(), IslandTools.timerActive) {
        while (true) { value = System.currentTimeMillis(); delay(500) }
    }
    GlassCard(onClick = onOptions, modifier = Modifier.testTag("uno-timers-$slot")) {
        val ink = LocalGlassInk.current
        CardTitle(Icons.Rounded.Timer, "Timer")
        if (IslandTools.timerActive) {
            Text(IslandClock.countdown(IslandClock.remainingMs(now, IslandTools.timerEndAt)), color = ink.primary, fontSize = 34.sp,
                fontWeight = FontWeight.Light, maxLines = 1, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("timer-remaining"))
            Box(Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(50)).background(ink.soft(.2f))
                .clickable { IslandTools.cancelTimer(context) }.padding(horizontal = 18.dp, vertical = 6.dp).testTag("timer-stop")) {
                Text("Stop", color = ink.primary, fontSize = 13.sp)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(listOf(5, 10), listOf(25, 45)).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { minutes ->
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(ink.soft(.18f))
                                .clickable { IslandTools.startTimer(context, minutes * 60_000L) }.padding(vertical = 8.dp)
                                .testTag("timer-start-$minutes"), contentAlignment = Alignment.Center) {
                                Text("$minutes min", color = ink.primary, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Sunrise and sunset -----------------------------------------------------------------------------------------------------

@Composable
private fun SunFace(slot: Int, onOptions: () -> Unit) {
    val context = LocalContext.current
    val today = rememberNow(10 * 60_000L).toLocalDate()
    val schedule = remember(today) {
        val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val lat = prefs.getString("lat", null)?.toDoubleOrNull(); val lon = prefs.getString("lon", null)?.toDoubleOrNull()
        if (lat == null || lon == null) null else runCatching { solarSchedule(today, lat, lon, ZoneId.systemDefault()) }.getOrNull()
    }
    val format = DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a")
    GlassCard(onClick = onOptions, modifier = Modifier.testTag("uno-sun-$slot")) {
        val ink = LocalGlassInk.current
        CardTitle(Icons.Rounded.WbSunny, "Sun")
        when {
            schedule == null -> Text("Set a place in Wallpaper & glass, Appearance, to see sunrise and sunset.", color = ink.soft(), fontSize = 12.sp)
            schedule.polar == PolarDaylight.DAY -> Text("The sun does not set today.", color = ink.primary, fontSize = 14.sp)
            schedule.polar == PolarDaylight.NIGHT -> Text("The sun does not rise today.", color = ink.primary, fontSize = 14.sp)
            else -> {
                val rise = requireNotNull(schedule.sunrise); val set = requireNotNull(schedule.sunset)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Sunrise", color = ink.soft(), fontSize = 11.sp)
                    Text(rise.format(format), color = ink.primary, fontSize = 22.sp, fontWeight = FontWeight.Light, maxLines = 1)
                    Text("Sunset", color = ink.soft(), fontSize = 11.sp)
                    Text(set.format(format), color = ink.primary, fontSize = 22.sp, fontWeight = FontWeight.Light, maxLines = 1)
                }
                Text(SunText.dayLength(rise, set), color = ink.soft(), fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
