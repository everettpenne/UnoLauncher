package com.jake.duolauncher.search

/** A page of Android's own settings that Spotlight can open: what to call it, what else to type for it, and the intent action. */
internal class SettingsShortcut(val label: String, val keywords: List<String>, val action: String)

/** A quick action typed into Spotlight, such as "timer 5". [run] says what it does; [label] and [detail] are what the row shows. */
internal sealed class QuickAction(val label: String, val detail: String) {
    class Timer(val seconds: Long, label: String) : QuickAction(label, "Starts the timer in the island")
    class Stopwatch : QuickAction("Start or stop the stopwatch", "Uses the island's stopwatch")
}

/**
 * Extra things Spotlight finds besides apps, contacts and saved headlines: Android's settings pages, and a few quick actions
 * you type. All of it is decided on the device from the words typed; nothing is read from other apps and nothing is sent anywhere.
 * Pure, so the matching is unit-tested.
 */
internal object SpotlightSources {
    const val MAX_SETTINGS = 3
    const val MAX_TIMER_SECONDS = 24 * 3600L

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

    /** Settings pages whose name or alternative words start with what was typed (three letters or more), best matches first. */
    fun settings(query: String): List<SettingsShortcut> {
        val q = squash(query)
        if (q.length < 3) return emptyList()
        fun score(shortcut: SettingsShortcut): Int {
            val label = squash(shortcut.label)
            return when {
                label.startsWith(q) -> 0
                shortcut.keywords.any { squash(it).startsWith(q) } -> 1
                // A typed phrase such as "screen lock" or "dark mode" matches as words, not as a run of letters.
                shortcut.keywords.any { squash(it).contains(q) && q.length >= 4 } -> 2
                else -> 3
            }
        }
        return settings.map { it to score(it) }.filter { it.second < 3 }.sortedBy { it.second }.map { it.first }.take(MAX_SETTINGS)
    }

    private val unit = """(s|sec|secs|seconds?|m|min|mins|minutes?|h|hr|hrs|hours?)"""
    private val number = """(\d+(?:\.\d+)?)"""
    private val timerFirst = Regex("""^(?:set\s+)?(?:a\s+)?timer(?:\s+(?:for|of))?\s+$number\s*$unit?$""", RegexOption.IGNORE_CASE)
    private val timerLast = Regex("""^(?:set\s+)?(?:a\s+)?$number\s*$unit\s+timer$""", RegexOption.IGNORE_CASE)

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

    /** The quick action a query asks for, if any. */
    fun quickAction(query: String): QuickAction? {
        val q = query.trim().lowercase()
        return timer(query) ?: if (q == "stopwatch" || q == "start stopwatch" || q == "stop stopwatch") QuickAction.Stopwatch() else null
    }
}
