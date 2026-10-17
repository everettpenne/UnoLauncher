package com.jake.duolauncher.search

/** A page of Android's own settings that Spotlight can open: what to call it, what else to type for it, and the intent action. */
internal class SettingsShortcut(val label: String, val keywords: List<String>, val action: String)

/** A quick action typed into Spotlight, such as "timer 5". [label] and [detail] are what the row shows. */
internal sealed class QuickAction(val label: String, val detail: String) {
    class Timer(val seconds: Long, label: String) : QuickAction(label, "Starts the timer in the island")
    class Stopwatch : QuickAction("Start or stop the stopwatch", "Uses the island's stopwatch")
    class Note(val text: String, label: String) : QuickAction(label, "Saves it to the Note widget on Home")
    class Alarm(val target: AlarmTarget, label: String) : QuickAction(label, "Opens your clock app to set the alarm")
    class Torch : QuickAction("Turn the torch on or off", "Uses the island's flashlight")
    class Focus(val desired: Boolean?, label: String, detail: String) : QuickAction(label, detail)
    class Call(val name: String, label: String) : QuickAction(label, "Shows the dialer with their number — no call is placed")
    class Text(val name: String, label: String) : QuickAction(label, "Opens your messages app to text them")
}

/** What an alarm action asks for: a clock time, or a number of minutes from now. */
internal sealed interface AlarmTarget {
    data class In(val minutes: Int) : AlarmTarget
    data class At(val hour: Int, val minute: Int) : AlarmTarget
}

/**
 * Extra things Spotlight finds besides apps, contacts and saved headlines: Android's settings pages, and a few quick actions
 * you type. All of it is decided on the device from the words typed; nothing is read from other apps and nothing is sent anywhere.
 * Pure, so the matching is unit-tested.
 */
internal object SpotlightSources {
    const val MAX_SETTINGS = 3
    const val MAX_TIMER_SECONDS = 24 * 3600L
    const val MAX_ALARM_MINUTES = 24 * 60

    // The action strings are Android's Settings.ACTION_* constants, written out so this stays testable without Android.
    val settings: List<SettingsShortcut> = listOf(
        SettingsShortcut("Wi-Fi", listOf("wifi", "wireless", "network", "internet"), "android.settings.WIFI_SETTINGS"),
        SettingsShortcut("Bluetooth", listOf("pair", "headphones", "earbuds"), "android.settings.BLUETOOTH_SETTINGS"),
        SettingsShortcut("Display", listOf("brightness", "screen", "dark mode", "font size", "timeout"), "android.settings.DISPLAY_SETTINGS"),
        SettingsShortcut("Sound", listOf("volume", "ringtone", "vibration", "silent"), "android.settings.SOUND_SETTINGS"),
        SettingsShortcut("Battery", listOf("power", "saver", "usage"), "android.intent.action.POWER_USAGE_SUMMARY"),
        SettingsShortcut("Location", listOf("gps", "maps"), "android.settings.LOCATION_SOURCE_SETTINGS"),
        SettingsShortcut("Storage", listOf("space", "memory", "files"), "android.settings.INTERNAL_STORAGE_SETTINGS"),
        SettingsShortcut("Apps", listOf("permissions", "applications", "default apps"), "android.settings.APPLICATION_SETTINGS"),
        SettingsShortcut("Default apps", listOf("browser", "home app", "launcher"), "android.settings.MANAGE_DEFAULT_APPS_SETTINGS"),
        SettingsShortcut("Airplane mode", listOf("flight", "aeroplane"), "android.settings.AIRPLANE_MODE_SETTINGS"),
        SettingsShortcut("Date and time", listOf("clock", "timezone", "time zone"), "android.settings.DATE_SETTINGS"),
        SettingsShortcut("Language", listOf("locale", "keyboard language", "region"), "android.settings.LOCALE_SETTINGS"),
        SettingsShortcut("Accessibility", listOf("talkback", "magnification", "captions"), "android.settings.ACCESSIBILITY_SETTINGS"),
        SettingsShortcut("Security", listOf("lock", "screen lock", "fingerprint", "pin", "password"), "android.settings.SECURITY_SETTINGS"),
        SettingsShortcut("Privacy", listOf("permissions", "camera access", "microphone access"), "android.settings.PRIVACY_SETTINGS"),
        SettingsShortcut("VPN", listOf("tunnel", "proxy"), "android.settings.VPN_SETTINGS"),
        SettingsShortcut("NFC", listOf("tap to pay", "contactless"), "android.settings.NFC_SETTINGS"),
        SettingsShortcut("Mobile data", listOf("cellular", "data usage", "roaming", "sim"), "android.settings.DATA_USAGE_SETTINGS"),
    )

    private fun squash(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

    /** Settings pages whose name or alternative words match what was typed (three letters or more), best matches first.
     * Forgiving about how people phrase it: "wifi settings" finds Wi-Fi, "screen brightness" finds Display. */
    fun settings(query: String): List<SettingsShortcut> {
        val raw = query.trim().lowercase()
        val q = squash(raw)
        if (q.length < 3) return emptyList()
        // People append "settings"/"options" to a page name; drop the suffix so "wifi settings" finds Wi-Fi.
        val core = q.removeSuffix("settings").removeSuffix("setting").removeSuffix("options")
            .removeSuffix("preferences").removeSuffix("panel").removeSuffix("page")
        if (core.length < 3) return emptyList()
        val coreWords = raw.split(' ', '-').filter { it.length >= 2 }
        fun score(shortcut: SettingsShortcut): Int {
            val label = squash(shortcut.label)
            val keywords = shortcut.keywords.map(::squash)
            return when {
                label.startsWith(core) -> 0
                keywords.any { it.startsWith(core) } -> 1
                // Every word of the query is a prefix of the name or one of its words ("screen brightness" -> Display).
                coreWords.size >= 2 && coreWords.all { w -> label.startsWith(w) || keywords.any { it.startsWith(w) } } -> 1
                // A typed phrase such as "screen lock" or "dark mode" matches as words, not as a run of letters.
                keywords.any { it.contains(q) && q.length >= 4 } -> 2
                else -> 3
            }
        }
        return settings.map { it to score(it) }.filter { it.second < 3 }.sortedBy { it.second }.map { it.first }.take(MAX_SETTINGS)
    }

    private val unit = """(s|sec|secs|seconds?|m|min|mins|minutes?|h|hr|hrs|hours?)"""
    private val number = """(\d+(?:\.\d+)?)"""
    private val timerFirst = Regex("""^(?:(?:set|start|begin)\s+)?(?:a\s+)?(?:timer|countdown)(?:\s+(?:for|of))?\s+$number\s*$unit?$""", RegexOption.IGNORE_CASE)
    private val timerLast = Regex("""^(?:(?:set|start|begin)\s+)?(?:a\s+)?$number\s*$unit\s+(?:timer|countdown)$""", RegexOption.IGNORE_CASE)

    /** "timer 5", "set a timer for 90 s", "10 min timer": minutes unless a unit is given. Null for anything else, or a length that makes no sense. */
    fun timer(query: String): QuickAction.Timer? {
        val q = query.trim()
        val m = timerFirst.find(q) ?: timerLast.find(q) ?: return null
        val amount = m.groupValues[1].toDoubleOrNull() ?: return null
        val unitText = m.groupValues.getOrNull(2).orEmpty().lowercase()
        val seconds = (amount * when {
            unitText.startsWith("h") -> 3600.0
            unitText.startsWith("s") -> 1.0
            else -> 60.0
        }).toLong()
        if (seconds < 1 || seconds > MAX_TIMER_SECONDS) return null
        val label = when {
            seconds % 3600 == 0L -> "${seconds / 3600} h timer"
            seconds % 60 == 0L -> "${seconds / 60} min timer"
            else -> "$seconds s timer"
        }
        return QuickAction.Timer(seconds, label)
    }

    // "note buy milk", "note remember to call Sam": everything after "note" is the note text.
    private val notePattern = Regex("""^note\s+(.+)$""", RegexOption.IGNORE_CASE)

    /** "note <text>" saves the text into the Note widget. Null for a bare "note" (no text) or anything else. */
    fun note(query: String): QuickAction.Note? {
        val text = notePattern.matchEntire(query.trim())?.groupValues?.get(1)?.trim().orEmpty()
        if (text.isEmpty()) return null
        return QuickAction.Note(text, "Save note")
    }

    // "alarm in 20" (minutes, with an optional unit), or "alarm 7:30", "alarm 7am", "alarm 19:00".
    private val alarmInPattern = Regex("""^alarm\s+in\s+(\d+)\s*(min|mins|minute|minutes|m|hr|hrs|hour|hours|h)?$""", RegexOption.IGNORE_CASE)
    private val alarmAtPattern = Regex("""^alarm\s+(\d{1,2})(?::(\d{1,2}))?\s*(am|pm|a\.m\.|p\.m\.)?$""", RegexOption.IGNORE_CASE)

    /** "alarm in 20", "alarm 7:30", "alarm 7am": a clock time or a delay, turned into the clock app's SET_ALARM fields. */
    fun alarm(query: String): QuickAction.Alarm? {
        val q = query.trim()
        alarmInPattern.matchEntire(q)?.let { m ->
            val amount = m.groupValues[1].toIntOrNull() ?: return null
            val unit = m.groupValues[2].lowercase()
            val minutes = if (unit.startsWith("h")) amount * 60 else amount
            if (minutes !in 1..MAX_ALARM_MINUTES) return null
            return QuickAction.Alarm(AlarmTarget.In(minutes), alarmInLabel(minutes))
        }
        return alarmAtPattern.matchEntire(q)?.let(::alarmAt)
    }

    private fun alarmAt(m: MatchResult): QuickAction.Alarm? {
        val hourRaw = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        val meridiem = m.groupValues[3].lowercase().firstOrNull() // 'a', 'p' or null
        if (minute !in 0..59) return null
        val hour = when (meridiem) {
            null -> hourRaw
            'p' -> when (hourRaw) { in 1..11 -> hourRaw + 12; 12 -> 12; else -> return null }
            else -> when (hourRaw) { 12 -> 0; in 1..11 -> hourRaw; else -> return null }
        }
        if (hour !in 0..23) return null
        return QuickAction.Alarm(AlarmTarget.At(hour, minute), alarmClockLabel(hour, minute))
    }

    private fun alarmInLabel(minutes: Int): String = when {
        minutes % 60 == 0 -> "Alarm in ${minutes / 60} h"
        minutes >= 60 -> "Alarm in ${minutes / 60} h ${minutes % 60} min"
        else -> "Alarm in $minutes min"
    }

    private fun alarmClockLabel(hour: Int, minute: Int): String {
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        val meridiem = if (hour < 12) "AM" else "PM"
        return "Alarm at $h12:%02d $meridiem".format(minute)
    }

    /** "torch" or "flashlight": toggle the island's flashlight. */
    fun torch(query: String): QuickAction.Torch? {
        val q = query.trim().lowercase()
        return if (q == "torch" || q == "flashlight") QuickAction.Torch() else null
    }

    /** "focus", "focus on", "focus off": turn Focus (the apps it hides) on, off, or toggle it. */
    fun focus(query: String): QuickAction.Focus? = when (query.trim().lowercase()) {
        "focus" -> QuickAction.Focus(null, "Toggle Focus", "Hides or shows the apps you picked")
        "focus on", "turn focus on" -> QuickAction.Focus(true, "Turn Focus on", "Hides the apps you picked")
        "focus off", "turn focus off" -> QuickAction.Focus(false, "Turn Focus off", "Shows the apps Focus hid")
        else -> null
    }

    private val callPattern = Regex("""^call\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val textPattern = Regex("""^(?:text|message|sms)\s+(.+)$""", RegexOption.IGNORE_CASE)

    /** "call <name>": show the dialer for a matching contact. Resolved to a number only while contacts are on. */
    fun call(query: String): QuickAction.Call? {
        val name = callPattern.matchEntire(query.trim())?.groupValues?.get(1)?.trim().orEmpty()
        if (name.isEmpty()) return null
        return QuickAction.Call(name, "Call $name")
    }

    /** "text <name>", "message <name>", "sms <name>": open messages for a matching contact. */
    fun text(query: String): QuickAction.Text? {
        val name = textPattern.matchEntire(query.trim())?.groupValues?.get(1)?.trim().orEmpty()
        if (name.isEmpty()) return null
        return QuickAction.Text(name, "Text $name")
    }

    /** The quick action a query asks for, if any. */
    fun quickAction(query: String): QuickAction? {
        val q = query.trim().lowercase()
        return timer(query)
            ?: alarm(query)
            ?: note(query)
            ?: torch(q)
            ?: focus(q)
            ?: call(query)
            ?: text(query)
            ?: if (q == "stopwatch" || q == "start stopwatch" || q == "stop stopwatch") QuickAction.Stopwatch() else null
    }
}
