package com.jake.duolauncher

/** One thing Launcher settings can change, as search finds it: the words on the page, other words people might type, and the page it is on. */
internal data class SettingEntry(val title: String, val page: CustomizationPage, val keywords: String = "")

/**
 * Search for Launcher settings: every setting (and the choices worth finding by name), with the page it lives on. Pure, so the matching is
 * unit-tested. When a setting is added to a page, add it here too (the test checks the pages' own labels are all findable).
 */
internal object SettingsSearch {
    val entries: List<SettingEntry> = listOf(
        // Appearance
        SettingEntry("Wallpaper", CustomizationPage.APPEARANCE, "background photo image lock screen"),
        SettingEntry("Also set home and lock screen", CustomizationPage.APPEARANCE, "wallpaper system"),
        SettingEntry("Liquid glass", CustomizationPage.APPEARANCE, "blur frosted dock material"),
        SettingEntry("Refraction height", CustomizationPage.APPEARANCE, "glass lens rim"),
        SettingEntry("Refraction amount", CustomizationPage.APPEARANCE, "glass lens bend"),
        SettingEntry("Chromatic aberration", CustomizationPage.APPEARANCE, "glass colour fringe rainbow"),
        SettingEntry("Tilt-following highlight", CustomizationPage.APPEARANCE, "glass shine light gyroscope"),
        SettingEntry("Tilt strength", CustomizationPage.APPEARANCE, "glass shine light"),
        SettingEntry("Wallpaper depth", CustomizationPage.APPEARANCE, "parallax 3d"),
        SettingEntry("Material You colors", CustomizationPage.APPEARANCE, "theme dynamic colour accent"),
        SettingEntry("Color from wallpaper", CustomizationPage.APPEARANCE, "theme colour accent"),
        SettingEntry("Light and dark", CustomizationPage.APPEARANCE, "dark mode night theme sunset sunrise appearance"),
        SettingEntry("App icons", CustomizationPage.APPEARANCE, "icon pack shape themed"),
        // Home layout
        SettingEntry("App icon size", CustomizationPage.HOME, "bigger smaller grid"),
        SettingEntry("Space between rows", CustomizationPage.HOME, "grid spacing gap"),
        SettingEntry("Dock width", CustomizationPage.HOME, "rail side"),
        SettingEntry("Align dock with app rows", CustomizationPage.HOME, "rail grid"),
        SettingEntry("Dock height on screen", CustomizationPage.HOME, "rail position"),
        SettingEntry("Widgets", CustomizationPage.HOME, "add widget weather calendar photos"),
        SettingEntry("Pinned apps", CustomizationPage.HOME, "dock favourites favorites"),
        // The island
        SettingEntry("Dynamic island", CustomizationPage.ISLAND, "pill camera notch"),
        SettingEntry("Island everywhere", CustomizationPage.ISLAND, "over other apps overlay accessibility"),
        SettingEntry("Island size", CustomizationPage.ISLAND, "bigger smaller"),
        SettingEntry("Keep the Home island still", CustomizationPage.ISLAND, "animation unlock moves flies jumps overlay"),
        SettingEntry("Island width: Off, Wide or Extra wide", CustomizationPage.ISLAND, "wide extra keep wide idle"),
        SettingEntry("Island style", CustomizationPage.ISLAND, "theme colours spring motion"),
        SettingEntry("Camera and microphone indicators", CustomizationPage.ISLAND, "privacy dots recording"),
        SettingEntry("VPN and USB data alerts", CustomizationPage.ISLAND, "events"),
        SettingEntry("App shortcuts in the island", CustomizationPage.ISLAND, "quick apps"),
        SettingEntry("Headphone and Bluetooth audio alerts", CustomizationPage.ISLAND, "earbuds connect"),
        SettingEntry("Bluetooth device alerts", CustomizationPage.ISLAND, "connect battery"),
        SettingEntry("Live Updates in the island", CustomizationPage.ISLAND, "ride delivery timer status android 16"),
        SettingEntry("Live Update text: Glyph only, Short status or Title", CustomizationPage.ISLAND, "compact"),
        SettingEntry("Downloads and other progress", CustomizationPage.ISLAND, "upload install"),
        SettingEntry("Navigation in the island", CustomizationPage.ISLAND, "maps directions driving turn"),
        SettingEntry("Drop down for each turn", CustomizationPage.ISLAND, "navigation maps"),
        SettingEntry("Show calls in the island", CustomizationPage.ISLAND, "phone call"),
        SettingEntry("Line up with the status bar", CustomizationPage.STATUS_GRID, "island icons clipped cut calibrate align gaps"),
        // Notifications & privacy
        SettingEntry("Badges on app icons", CustomizationPage.PRIVACY, "notification dot count"),
        SettingEntry("Badge style: Count or Dot", CustomizationPage.PRIVACY, "notification number"),
        SettingEntry("Track title and artwork in media", CustomizationPage.PRIVACY, "music song"),
        SettingEntry("Show new notifications in the island", CustomizationPage.PRIVACY, "card peek"),
        SettingEntry("Action buttons on the card", CustomizationPage.PRIVACY, "reply notification"),
        SettingEntry("What a notification card shows", CustomizationPage.PRIVACY, "app name title text privacy"),
        SettingEntry("Focus on", CustomizationPage.PRIVACY, "do not disturb dnd quiet"),
        SettingEntry("Also set the ringer to vibrate", CustomizationPage.PRIVACY, "focus silent"),
        SettingEntry("Hidden apps", CustomizationPage.PRIVACY, "hide unhide"),
        SettingEntry("Permission ledger", CustomizationPage.PRIVACY, "permissions privacy access log"),
        // Gestures & feel
        SettingEntry("Show app names", CustomizationPage.GESTURES, "labels icons"),
        SettingEntry("Show status at upper right", CustomizationPage.GESTURES, "battery clock rail"),
        SettingEntry("Haptics", CustomizationPage.GESTURES, "vibration feedback"),
        SettingEntry("Sounds", CustomizationPage.GESTURES, "clicks audio"),
        SettingEntry("Swipe down layout", CustomizationPage.GESTURES, "like ios notifications search control panel left"),
        SettingEntry("Android mode", CustomizationPage.GESTURES, "swipe down shade"),
        SettingEntry("Double-tap to lock", CustomizationPage.GESTURES, "screen off sleep"),
        // Search
        SettingEntry("Search button opens Google", CustomizationPage.SEARCH, "web"),
        SettingEntry("Suggest other apps and the web", CustomizationPage.SEARCH, "spotlight suggestions"),
        SettingEntry("Search contacts from All apps", CustomizationPage.SEARCH, "people phone"),
        // News feed
        SettingEntry("News feed", CustomizationPage.FEED, "rss headlines discover articles opml"),
        // Keyboard
        SettingEntry("Uno Keyboard", CustomizationPage.KEYBOARD, "typing ime turn on"),
        SettingEntry("Suggestions", CustomizationPage.KEYBOARD, "keyboard words predictions"),
        SettingEntry("Autocorrect", CustomizationPage.KEYBOARD, "keyboard typo spelling"),
        SettingEntry("Number row", CustomizationPage.KEYBOARD, "keyboard digits"),
        SettingEntry("Double-space to type a period", CustomizationPage.KEYBOARD, "keyboard full stop"),
        SettingEntry("Slide on space bar to move the cursor", CustomizationPage.KEYBOARD, "keyboard trackpad"),
        SettingEntry("One-handed keyboard: Off, Left or Right", CustomizationPage.KEYBOARD, "one hand"),
        // Backup & about
        SettingEntry("Backup", CustomizationPage.BACKUP, "save restore layout export import"),
        SettingEntry("Updates", CustomizationPage.BACKUP, "version new release"),
        SettingEntry("Auto-update", CustomizationPage.BACKUP, "updates automatic"),
        SettingEntry("Help & setup", CustomizationPage.BACKUP, "tour guide first run"),
    )

    /** The settings matching [query], best first: a title that starts with it, then one that contains it, then a keyword that does. Every
     * word typed must appear somewhere in the title or keywords. An empty query matches nothing. */
    fun search(query: String, all: List<SettingEntry> = entries): List<SettingEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val words = q.split(Regex("\\s+"))
        return all.filter { e -> val hay = "${e.title} ${e.keywords} ${pageName(e.page)}".lowercase(); words.all { it in hay } }
            .sortedBy { e ->
                val t = e.title.lowercase()
                when { t.startsWith(q) -> 0; t.contains(q) -> 1; words.all { it in t } -> 2; else -> 3 }
            }
    }

    /** The page's name as Launcher settings shows it. */
    fun pageName(page: CustomizationPage): String = when (page) {
        CustomizationPage.OVERVIEW -> "Launcher settings"
        CustomizationPage.APPEARANCE -> "Appearance"
        CustomizationPage.HOME -> "Home layout"
        CustomizationPage.GESTURES -> "Gestures & feel"
        CustomizationPage.SEARCH -> "Search"
        CustomizationPage.ISLAND -> "The island"
        CustomizationPage.KEYBOARD -> "Keyboard"
        CustomizationPage.PRIVACY -> "Notifications & privacy"
        CustomizationPage.FEED -> "News feed"
        CustomizationPage.BACKUP -> "Backup & about"
        CustomizationPage.DEBUG -> "Island debug"
        CustomizationPage.STATUS_GRID -> "Line up with the status bar"
    }
}
