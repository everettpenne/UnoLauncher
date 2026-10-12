package com.jake.duolauncher.widgets

import com.jake.duolauncher.island.*

import com.jake.duolauncher.*
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

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
    const val ANALOG_CLOCK = -29
    const val NEXT_ALARM = -30
    const val WORLD_CLOCK = -31
    const val PHOTOS = -32

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
        UnoWidgetDef(ANALOG_CLOCK, "Analog clock", "A dial with hour and minute hands, and today's date. Moves once a minute.", 2, 2) { s, o -> AnalogClockFace(s, o) },
        UnoWidgetDef(NEXT_ALARM, "Next alarm", "When your next clock-app alarm rings, and how long until it. Tap to open the clock app.", 2, 2) { s, o -> NextAlarmFace(s, o) },
        UnoWidgetDef(WORLD_CLOCK, "World clock", "The time in up to four places you choose. Tap to pick them.", 2, 2) { s, o -> WorldClockFace(s, o) },
        UnoWidgetDef(PHOTOS, "Photos", "Your photos or an album, changing on a schedule you set. Tap to preview.", 2, 2) { s, o -> PhotosFace(s, o) },
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
