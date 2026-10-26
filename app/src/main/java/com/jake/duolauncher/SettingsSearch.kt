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
        SettingEntry("Wallpaper", CustomizationPage.APPEARANCE, "background photo image choose edit styles duotone black white blur crop"),
        SettingEntry("Edit Photo", CustomizationPage.APPEARANCE, "wallpaper adjust crop zoom brightness warmth style"),
        SettingEntry("Android Wallpaper Too", CustomizationPage.APPEARANCE, "wallpaper system lock screen home also set"),
        SettingEntry("Light & Dark", CustomizationPage.APPEARANCE, "dark mode night theme sunset sunrise appearance automatic"),
        SettingEntry("Glass: Clear, Frosted or Off", CustomizationPage.APPEARANCE, "liquid glass blur frosted transparent ios edge tint dock material"),
        SettingEntry("Tilt Highlight", CustomizationPage.APPEARANCE, "glass shine light gyroscope motion sensor"),
        SettingEntry("Accent: Uno Orange or From Wallpaper", CustomizationPage.APPEARANCE, "theme colour color material you dynamic orange"),
        SettingEntry("Wallpaper Depth", CustomizationPage.APPEARANCE, "parallax 3d"),
        SettingEntry("App Icons", CustomizationPage.APPEARANCE, "icon style original themed tinted dark"),
        SettingEntry("Icon Lighting", CustomizationPage.APPEARANCE, "icons lit flat glass shine"),
        SettingEntry("Icon Pack", CustomizationPage.APPEARANCE, "icons adw nova lawnchair"),
        // Home Screen
        SettingEntry("Home Layout: Side Dock or iPhone", CustomizationPage.HOME, "ios iphone dock bottom side grid foldable"),
        SettingEntry("Pinned Apps", CustomizationPage.HOME, "dock favourites favorites home apps"),
        SettingEntry("Show App Names", CustomizationPage.HOME, "labels icons"),
        SettingEntry("Icon Size", CustomizationPage.HOME, "bigger smaller grid app"),
        SettingEntry("Row Spacing", CustomizationPage.HOME, "grid spacing gap rows"),
        SettingEntry("Dock Width", CustomizationPage.HOME, "rail side"),
        SettingEntry("Align Dock with Rows", CustomizationPage.HOME, "rail grid"),
        SettingEntry("Dock Position", CustomizationPage.HOME, "rail height"),
        SettingEntry("Widgets", CustomizationPage.HOME, "add widget weather calendar photos"),
        // Island
        SettingEntry("Island", CustomizationPage.ISLAND, "dynamic island pill camera notch"),
        SettingEntry("Over Other Apps", CustomizationPage.ISLAND, "island everywhere overlay accessibility"),
        SettingEntry("Height", CustomizationPage.ISLAND, "island size tall thin camera pill"),
        SettingEntry("Island Style", CustomizationPage.ISLAND_STYLE, "theme colours colors spring motion panel shape"),
        SettingEntry("Live Updates", CustomizationPage.ISLAND, "island ride delivery timer status android 16"),
        SettingEntry("Downloads & Progress", CustomizationPage.ISLAND, "island upload install"),
        SettingEntry("Navigation", CustomizationPage.ISLAND, "island maps directions driving turn"),
        SettingEntry("Calls", CustomizationPage.ISLAND, "island phone call"),
        SettingEntry("Camera & Mic Indicators", CustomizationPage.ISLAND, "island privacy dots recording microphone"),
        SettingEntry("System Alerts", CustomizationPage.ISLAND, "island vpn usb headphones bluetooth earbuds connect"),
        SettingEntry("App Shortcuts", CustomizationPage.ISLAND, "island quick apps"),
        SettingEntry("Show Notifications", CustomizationPage.ISLAND, "island card peek"),
        SettingEntry("Action Buttons", CustomizationPage.ISLAND, "island reply notification"),
        SettingEntry("What a Notification Card Shows", CustomizationPage.ISLAND, "island app name title text privacy"),
        SettingEntry("Hide Words", CustomizationPage.ISLAND, "island block filter peek notifications containing"),
        SettingEntry("Paused Media", CustomizationPage.ISLAND, "island resume minutes music podcasts"),
        SettingEntry("Island Widget", CustomizationPage.ISLAND, "widget in the island"),
        SettingEntry("Island Apps", CustomizationPage.ISLAND, "apps allowed linked timeout"),
        SettingEntry("Island Preview", CustomizationPage.ISLAND, "demo try see watch example"),
        SettingEntry("Line Up with Status Bar", CustomizationPage.STATUS_GRID, "island icons clipped cut calibrate align gaps"),
        SettingEntry("Tidy the Status Bar", CustomizationPage.STATUS_GRID, "hide icons adb icon_blacklist alarm bluetooth nfc rotate"),
        // Notifications & Privacy
        SettingEntry("Notification Access", CustomizationPage.PRIVACY, "notifications listener permission"),
        SettingEntry("Badges", CustomizationPage.PRIVACY, "notification dot count number"),
        SettingEntry("Media Details", CustomizationPage.PRIVACY, "music song title artwork track"),
        SettingEntry("Focus", CustomizationPage.PRIVACY, "do not disturb dnd quiet hide apps"),
        SettingEntry("Vibrate Only", CustomizationPage.PRIVACY, "focus silent ringer"),
        SettingEntry("With Do Not Disturb", CustomizationPage.PRIVACY, "focus dnd schedule automatic"),
        SettingEntry("Hide Over Chosen Apps", CustomizationPage.ISLAND, "hide island apps banking games"),
        SettingEntry("People Only", CustomizationPage.ISLAND, "notifications messages people conversations drop down"),
        SettingEntry("Step Aside in Its App", CustomizationPage.ISLAND, "hide music player activity own app"),
        SettingEntry("Hidden Apps", CustomizationPage.PRIVACY, "hide unhide"),
        SettingEntry("Permission Ledger", CustomizationPage.PRIVACY, "permissions privacy access log"),
        // Gestures
        SettingEntry("Status at Upper Right", CustomizationPage.GESTURES, "battery clock rail status"),
        SettingEntry("Haptics", CustomizationPage.GESTURES, "vibration feedback"),
        SettingEntry("Sounds", CustomizationPage.GESTURES, "clicks audio"),
        SettingEntry("Pull Down on Home", CustomizationPage.GESTURES, "swipe like ios notifications search control panel left"),
        SettingEntry("Android Mode", CustomizationPage.GESTURES, "swipe down shade quick settings right"),
        SettingEntry("Double-Tap to Lock", CustomizationPage.GESTURES, "screen off sleep"),
        SettingEntry("Control Panel Tiles", CustomizationPage.GESTURES, "wifi bluetooth torch order"),
        // Search
        SettingEntry("Search Button Opens Google", CustomizationPage.SEARCH, "web google"),
        SettingEntry("App & Web Suggestions", CustomizationPage.SEARCH, "spotlight suggestions stores browser"),
        SettingEntry("Contacts", CustomizationPage.SEARCH, "people phone all apps"),
        // News feed
        SettingEntry("News Feed", CustomizationPage.FEED, "rss headlines discover articles opml"),
        // Keyboard
        SettingEntry("Uno Keyboard", CustomizationPage.KEYBOARD, "typing ime turn on"),
        SettingEntry("Predictive", CustomizationPage.KEYBOARD, "keyboard suggestions words predictions"),
        SettingEntry("Autocorrect", CustomizationPage.KEYBOARD, "keyboard typo spelling"),
        SettingEntry("Slide to Type", CustomizationPage.KEYBOARD, "keyboard swipe gesture glide typing"),
        SettingEntry("Auto-Capitalization", CustomizationPage.KEYBOARD, "keyboard auto caps shift sentences"),
        SettingEntry("“.” Shortcut", CustomizationPage.KEYBOARD, "keyboard double space period full stop"),
        SettingEntry("Emoji Suggestions", CustomizationPage.KEYBOARD, "keyboard emoji predictions"),
        SettingEntry("Clipboard History", CustomizationPage.KEYBOARD, "keyboard clips pin copy paste"),
        SettingEntry("Learn New Words", CustomizationPage.KEYBOARD, "keyboard learned personal dictionary predictions learning your words forget"),
        SettingEntry("Number Row", CustomizationPage.KEYBOARD, "keyboard digits"),
        SettingEntry("Keyboard Look: Liquid Glass or Classic", CustomizationPage.KEYBOARD, "keyboard style key old 0.97 flat theme"),
        SettingEntry("Height", CustomizationPage.KEYBOARD, "keyboard size taller shorter"),
        SettingEntry("Key Sounds", CustomizationPage.KEYBOARD, "keyboard click audio"),
        SettingEntry("Key Haptics: Off, Soft, Crisp or Firm", CustomizationPage.KEYBOARD, "keyboard vibration feel feedback"),
        SettingEntry("One-Handed", CustomizationPage.KEYBOARD, "keyboard one hand left right"),
        SettingEntry("Keyboard Layouts", CustomizationPage.KEYBOARD, "keyboard language qwertz azerty dvorak colemak workman russian greek"),
        // General
        SettingEntry("Backup", CustomizationPage.BACKUP, "save restore layout export import"),
        SettingEntry("Updates", CustomizationPage.BACKUP, "version new release"),
        SettingEntry("Automatic Updates", CustomizationPage.BACKUP, "updates auto"),
        SettingEntry("Help", CustomizationPage.BACKUP, "tour guide first run setup"),
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
        CustomizationPage.OVERVIEW -> "Settings"
        CustomizationPage.APPEARANCE -> "Appearance"
        CustomizationPage.HOME -> "Home Screen"
        CustomizationPage.GESTURES -> "Gestures"
        CustomizationPage.SEARCH -> "Search"
        CustomizationPage.ISLAND -> "Island"
        CustomizationPage.KEYBOARD -> "Keyboard"
        CustomizationPage.PRIVACY -> "Notifications & Privacy"
        CustomizationPage.FEED -> "News Feed"
        CustomizationPage.BACKUP -> "General"
        CustomizationPage.DEBUG -> "Island Debug"
        CustomizationPage.STATUS_GRID -> "Line Up with Status Bar"
        CustomizationPage.ISLAND_STYLE -> "Island Style"
    }
}
