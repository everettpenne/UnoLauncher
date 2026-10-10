package com.jake.duolauncher

/** What the right-hand downward swipe opens. */
internal enum class RightSwipe { PANEL, SYSTEM }

/** What pulling down on Home does. SMART is the default and works like iOS: a pull that starts at the top edge opens
 * notifications (left) or the control panel (right), and a pull that starts anywhere lower opens search.
 */
internal enum class PullDown { SMART, NOTIFICATIONS, SEARCH }

/** Where a pull-down on Home should go. */
internal enum class PullRoute { NOTIFICATIONS, QUICK_SETTINGS, SEARCH }

internal object PullDownRouting {
    /** A pull that begins in the top part of the gesture area counts as "from the top edge". */
    const val TOP_ZONE = .22f

    fun route(mode: PullDown, panel: ShadePanel, startFraction: Float): PullRoute {
        val byColumn = if (panel == ShadePanel.QUICK_SETTINGS) PullRoute.QUICK_SETTINGS else PullRoute.NOTIFICATIONS
        return when (mode) {
            PullDown.NOTIFICATIONS -> byColumn
            // Search replaces the notifications side only; the right-hand panel stays reachable.
            PullDown.SEARCH -> if (byColumn == PullRoute.NOTIFICATIONS) PullRoute.SEARCH else byColumn
            PullDown.SMART -> if (startFraction <= TOP_ZONE) byColumn else PullRoute.SEARCH
        }
    }

    /** Old installs stored LeftSwipe; keep a choice of Search, otherwise start on the new default. */
    fun migrate(newValue: String?, oldValue: String?): PullDown =
        runCatching { PullDown.valueOf(newValue!!) }.getOrNull() ?: if (oldValue == "SEARCH") PullDown.SEARCH else PullDown.SMART
}

/**
 * How app icons are drawn: as shipped, recoloured to the launcher's palette where the app allows (THEMED), or the same
 * recolouring always taken from the wallpaper's own colour (TINTED), whether or not "Color from wallpaper" recolours the rest
 * of the interface.
 */
internal enum class IconStyle {
    ORIGINAL, THEMED, TINTED;

    /** Whether icons are redrawn at all (so the light/dark palette matters). */
    val recolours: Boolean get() = this != ORIGINAL
    /** Whether the wallpaper's colours go into the icons, given the "Color from wallpaper" switch. */
    fun usesWallpaper(wallpaperColor: Boolean): Boolean = this == TINTED || (this == THEMED && wallpaperColor)

    companion object { fun fromPreference(raw: String?): IconStyle = entries.firstOrNull { it.name == raw } ?: ORIGINAL }
}

/** Every tile the control panel can show. The order here is the default order. */
internal enum class PanelTile(val label: String) {
    MEDIA("Media"),
    VOLUME("Volume"),
    BRIGHTNESS("Brightness"),
    RINGER("Ringer"),
    FLASHLIGHT("Flashlight"),
    FOCUS("Focus"),
    SHORTCUTS("Shortcuts"),
    SYSTEM("System settings"),
}

/** Ordering, visibility and row-grouping rules for the control panel's tiles. Pure, so it is unit-tested. */
internal object PanelLayout {
    val DEFAULT_ORDER: List<PanelTile> = PanelTile.entries.toList()
    const val MAX_SHORTCUTS = 5

    /** Reads a saved order. Unknown names are dropped and any tile a newer build added is appended, so an
     * old preference never hides a tile the user has never seen.
     */
    fun parseOrder(raw: String?): List<PanelTile> {
        val saved = raw.orEmpty().split(',').mapNotNull { name -> PanelTile.entries.firstOrNull { it.name == name.trim() } }.distinct()
        return saved + DEFAULT_ORDER.filterNot { it in saved }
    }

    fun parseSet(raw: String?): Set<PanelTile> =
        raw.orEmpty().split(',').mapNotNull { name -> PanelTile.entries.firstOrNull { it.name == name.trim() } }.toSet()

    fun serialize(tiles: Collection<PanelTile>): String = tiles.joinToString(",") { it.name }

    /** Moves [tile] by [delta] places, stopping at the ends. */
    fun move(order: List<PanelTile>, tile: PanelTile, delta: Int): List<PanelTile> {
        val from = order.indexOf(tile)
        if (from < 0) return order
        val to = (from + delta).coerceIn(0, order.lastIndex)
        if (to == from) return order
        return order.toMutableList().also { it.add(to, it.removeAt(from)) }
    }

    /** The tiles to draw. Never empty: with everything hidden the panel would be a blank card with no way
     * to reach Quick Settings, so System settings comes back.
     */
    fun visible(order: List<PanelTile>, hidden: Set<PanelTile>): List<PanelTile> =
        order.filter { it !in hidden }.ifEmpty { listOf(PanelTile.SYSTEM) }

    /** Rows for drawing: Ringer and Flashlight share a row when they are next to each other. */
    fun rows(visible: List<PanelTile>): List<List<PanelTile>> {
        val rows = mutableListOf<List<PanelTile>>()
        var i = 0
        while (i < visible.size) {
            val a = visible[i]
            val b = visible.getOrNull(i + 1)
            if (b != null && setOf(a, b) == setOf(PanelTile.RINGER, PanelTile.FLASHLIGHT)) { rows += listOf(a, b); i += 2 }
            else { rows += listOf(a); i += 1 }
        }
        return rows
    }

    fun parseIds(raw: String?): List<String> = raw.orEmpty().split('\n').filter { it.isNotBlank() }
    fun serializeIds(ids: List<String>): String = ids.joinToString("\n")
    fun toggleShortcut(ids: List<String>, id: String): List<String> =
        if (id in ids) ids - id else (ids + id).takeLast(MAX_SHORTCUTS)
}

/** Focus: while it is on, the chosen apps are left out of Home and All apps. Nothing is uninstalled or
 * unpinned; the saved layout is untouched and the apps return the moment Focus ends.
 */
internal object Focus {
    fun <T> filter(items: List<T>, id: (T) -> String, on: Boolean, hidden: Set<String>): List<T> =
        if (!on || hidden.isEmpty()) items else items.filterNot { id(it) in hidden }
}

/** Counts for icon badges, from what the notification listener reports. */
internal data class ListedNotification(val packageName: String, val ongoing: Boolean, val groupSummary: Boolean)

internal object BadgeLogic {
    /** One badge count per package. Ongoing notifications (a running timer, a media player) aren't
     * "unread", and a group summary stands in for its children, so counting it as well would double up.
     */
    fun counts(notifications: List<ListedNotification>): Map<String, Int> =
        notifications.filter { !it.ongoing && !it.groupSummary }
            .groupingBy { it.packageName }.eachCount()

    fun label(count: Int): String = if (count > 9) "9+" else count.toString()
}

/** Matching for contact search results in All apps. */
internal object ContactMatch {
    const val MIN_QUERY = 2
    const val MAX_RESULTS = 5

    /** Every word of [query] must start a word of [name], so "ann sm" finds "Anna Smith" but not "Joanna". */
    fun matches(name: String, query: String): Boolean {
        val wanted = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        if (wanted.size == 0) return false
        val words = name.lowercase().split(' ', '-', '.').filter { it.isNotEmpty() }
        return wanted.all { w -> words.any { it.startsWith(w) } }
    }

    fun filter(names: List<String>, query: String): List<String> =
        if (query.trim().length < MIN_QUERY) emptyList() else names.filter { matches(it, query) }.take(MAX_RESULTS)
}

/** Island timer and stopwatch arithmetic. */
internal object IslandClock {
    fun remainingMs(nowMs: Long, endAtMs: Long): Long = (endAtMs - nowMs).coerceAtLeast(0L)

    /** m:ss, or h:mm:ss from an hour. Rounds a partial second up so a countdown never shows 0:00 while running. */
    fun countdown(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) + 999L) / 1000L
        val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** m:ss.t for a stopwatch (tenths), or h:mm:ss from an hour. */
    fun stopwatch(ms: Long): String {
        val v = ms.coerceAtLeast(0L)
        val tenths = (v % 1000) / 100
        val total = v / 1000
        val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d.%d".format(m, s, tenths)
    }

    /** Progress 0..1 for a running timer, for a ring or bar. */
    fun progress(nowMs: Long, endAtMs: Long, totalMs: Long): Float =
        if (totalMs <= 0L) 1f else (1f - remainingMs(nowMs, endAtMs).toFloat() / totalMs).coerceIn(0f, 1f)
}
