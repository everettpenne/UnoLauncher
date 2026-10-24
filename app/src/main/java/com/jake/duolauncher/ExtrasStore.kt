package com.jake.duolauncher

import com.jake.duolauncher.island.*
import com.jake.duolauncher.search.*
import com.jake.duolauncher.appearance.*

import android.content.Context
import androidx.compose.runtime.*

/**
 * How wide the island's collapsed pill stays when idle, and how wide its "wide" form is. Three states, stored as an int
 * (`islandWideMode` 0/1/2); the old `islandWide` boolean is migrated: `true` becomes [WIDE]. [EXTRA_WIDE] keeps the pill
 * wide everywhere the [WIDE] state does and makes the wide pill 50% wider (see IslandGeometry's `wideFactor`).
 */
internal enum class IslandWideMode(val keepWide: Boolean, val wideFactor: Float) {
    OFF(false, 1f), WIDE(true, 1f), EXTRA_WIDE(true, 1.5f);

    companion object {
        fun fromInt(value: Int): IslandWideMode = entries.getOrNull(value) ?: OFF

        /** Reads the new int pref ([intValue], null when absent), falling back to the old boolean ([oldBoolean]): true → [WIDE]. */
        fun read(intValue: Int?, oldBoolean: Boolean): IslandWideMode = when {
            intValue != null -> fromInt(intValue)
            oldBoolean -> WIDE
            else -> OFF
        }
    }
}

/** Everything the "extras" features remember, in one preference file. Every grant-backed feature
 * defaults to off: badges, media details and contact search do nothing until the user turns them on
 * (and then grants Android's own permission), so a fresh install asks for nothing.
 */
internal data class ExtrasState(
    val rightSwipe: RightSwipe = RightSwipe.PANEL,
    val pullDown: PullDown = PullDown.SMART,
    val tileOrder: List<PanelTile> = PanelLayout.DEFAULT_ORDER,
    val hiddenTiles: Set<PanelTile> = emptySet(),
    val shortcuts: List<String> = emptyList(),
    val iconStyle: IconStyle = IconStyle.ORIGINAL,
    /** Light and shade painted over the original icons, so they look raised like iOS's; themed icons have their own lighting. */
    val iconLighting: Boolean = true,
    /** The icon pack chosen in Icons, as its package name; empty for none. */
    val iconPack: String = "",
    val focusOn: Boolean = false,
    val focusHidden: Set<String> = emptySet(),
    /** Packages switched off for the island (Live Updates and notification peeks); see IslandLinks. */
    val islandMuted: Set<String> = emptySet(),
    val focusVibrate: Boolean = false,
    /** Focus turns on and off with Android's Do Not Disturb. */
    val focusWithDnd: Boolean = false,
    /** Hide the island over the apps in [islandHideOver] (needs the accessibility service, which then learns which app is in front). */
    val islandHideOverOn: Boolean = false,
    /** Package names the island is hidden over. */
    val islandHideOver: Set<String> = emptySet(),
    val badges: Boolean = false,
    /** Badges as a plain dot instead of a count. */
    val badgeDots: Boolean = false,
    /** Packages whose icons never get a badge, comma-separated (see [BadgeMute]). */
    val badgeMuted: String = "",
    val mediaDetails: Boolean = false,
    val callDetails: Boolean = true,
    val privacyIndicators: Boolean = true,
    val securityAlerts: Boolean = true,
    val audioAlerts: Boolean = true,
    /** Double-tapping empty Home space locks the screen (through the optional accessibility service, which can do nothing else with it). */
    val doubleTapLock: Boolean = false,
    /** Bluetooth devices connecting (a watch, a keyboard, a car) as island events. Off until the person allows the Nearby devices permission. */
    val bluetoothAlerts: Boolean = false,
    /** Action buttons (Reply, Archive...) on the notification card, taken from the notification itself. */
    val peekActions: Boolean = true,
    val islandShortcuts: Boolean = true,
    /** Whether the island's pill stays wide when idle over other apps (Wide / Extra wide), and how wide that pill is. */
    val islandWideMode: IslandWideMode = IslandWideMode.OFF,
    /** Minutes that paused media worth returning to stays on the open island (and glows round the camera): 1, 2, 10 or 30. */
    val mediaResumeMinutes: Int = 2,
    val progressTasks: Boolean = true,
    val navigationTasks: Boolean = true,
    /** The island drops down briefly for each turn instruction (a new arrow or street, or the turn coming close). */
    val navigationCues: Boolean = true,
    /** How much of a new notification the island's card shows: 0 name only, 1 plus title, 2 plus text (see PeekLevel). */
    val peekLevel: Int = 0,
    /** Words that keep a notification's text off the island, comma separated. */
    val peekBlocked: String = "",
    /** Per-app seconds a notification card stays (see PeekTimeouts). */
    val peekTimeouts: String = "",
    val liveUpdates: Boolean = true,
    /** How much the collapsed pill writes beside a Live Update's glyph: 0 glyph only, 1 the short status, 2 the title (see LiveCompactText). */
    val liveCompactLevel: Int = 1,
    val islandEverywhere: Boolean = false,
    val notificationPeek: Boolean = false,
    val contactSearch: Boolean = false,
    val kbAutocorrect: Boolean = true,
    val kbSuggestions: Boolean = true,
    val kbNumberRow: Boolean = false,
    val kbDoubleSpace: Boolean = true,
    val kbOneHanded: Int = 0,
    val kbSpaceCursor: Boolean = true,
    val kbHapticStrength: Float = com.jake.duolauncher.keyboard.HapticProfile.DEFAULT_STRENGTH,
    /** 0 soft, 1 crisp, 2 firm (see keyboard.HapticFeel). */
    val kbHapticFeel: Int = 1,
    /** The keyboard layouts turned on, comma separated ids (see keyboard.KeyLayouts). */
    val kbLayouts: String = "qwerty",
    /** Key height as a fraction of the standard, 0.8 to 1.3. */
    val kbHeight: Float = 1f,
    val kbKeyPreview: Boolean = true,
    val kbSound: Boolean = false,
    val kbEmojiKey: Boolean = true,
    val kbSwipe: Boolean = true,
    val kbLearn: Boolean = true,
    val kbClipHistory: Boolean = true,
    val kbEmojiSuggestions: Boolean = true,
    /** Blur the app behind the keyboard (cross-window blur). Off by default: see UnoKeyboardService.setUpGlassWindow. */
    val kbBlur: Boolean = false,
    val kbAutoCaps: Boolean = true,
    /** 0 liquid glass, 1 the flat look of Uno 0.97 (see keyboard.KeyboardUi's KeyPalette). */
    val kbStyle: Int = 0,
    val handoff: Boolean = true,
    val webPackage: String? = null,
    val handoffStores: Set<String> = HandoffLogic.DEFAULT_STORES,
    val haptics: Boolean = true,
    val sounds: Boolean = false,
)

internal class ExtrasStore(context: Context) {
    private val prefs = context.getSharedPreferences("extras", Context.MODE_PRIVATE)
    var state by mutableStateOf(load()); private set

    // The keyboard can change one-handed mode itself (its button to give the keys their full width back), so the store follows the
    // preference; otherwise the next save of anything else would write the old value back over it.
    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "kbOneHanded") {
            val now = prefs.getInt("kbOneHanded", 0).coerceIn(0, 2)
            if (now != state.kbOneHanded) state = state.copy(kbOneHanded = now)
        }
    }
    init { prefs.registerOnSharedPreferenceChangeListener(prefListener) }

    /** Re-reads everything from the preferences (after a settings restore). */
    fun reload() { state = load() }

    private fun load(): ExtrasState { fixRetiredSettings(prefs); return read() }

    private fun read() = ExtrasState(
        // A new key, so everyone gets the new default rather than a value saved by an earlier version.
        pullDown = PullDownRouting.migrate(prefs.getString("pullDown", null), prefs.getString("leftSwipe", null)),
        rightSwipe = runCatching { RightSwipe.valueOf(prefs.getString("rightSwipe", null)!!) }.getOrDefault(RightSwipe.PANEL),
        tileOrder = PanelLayout.parseOrder(prefs.getString("tileOrder", null)),
        hiddenTiles = PanelLayout.parseSet(prefs.getString("hiddenTiles", null)),
        shortcuts = PanelLayout.parseIds(prefs.getString("shortcuts", null)),
        iconStyle = IconStyle.fromPreference(prefs.getString("iconStyle", null)),
        iconLighting = prefs.getBoolean("iconLighting", true),
        iconPack = prefs.getString("iconPack", "") ?: "",
        focusOn = prefs.getBoolean("focusOn", false),
        focusHidden = PanelLayout.parseIds(prefs.getString("focusHidden", null)).toSet(),
        islandMuted = IslandLinks.parse(prefs.getString("islandMuted", null)),
        focusVibrate = prefs.getBoolean("focusVibrate", false),
        focusWithDnd = prefs.getBoolean("focusWithDnd", false),
        islandHideOverOn = prefs.getBoolean("islandHideOverOn", false),
        islandHideOver = IslandLinks.parse(prefs.getString("islandHideOver", null)),
        badges = prefs.getBoolean("badges", false),
        badgeDots = prefs.getBoolean("badgeDots", false),
        badgeMuted = prefs.getString("badgeMuted", "") ?: "",
        mediaDetails = prefs.getBoolean("mediaDetails", false),
        callDetails = prefs.getBoolean("callDetails", true),
        privacyIndicators = prefs.getBoolean("privacyIndicators", true),
        securityAlerts = prefs.getBoolean("securityAlerts", true),
        audioAlerts = prefs.getBoolean("audioAlerts", true),
        doubleTapLock = prefs.getBoolean("doubleTapLock", false),
        bluetoothAlerts = prefs.getBoolean("bluetoothAlerts", false),
        peekActions = prefs.getBoolean("peekActions", true),
        islandShortcuts = prefs.getBoolean("islandShortcuts", true),
        islandWideMode = IslandWideMode.read(
            prefs.getInt("islandWideMode", 0).takeIf { prefs.contains("islandWideMode") },
            prefs.getBoolean("islandWide", false)),
        mediaResumeMinutes = prefs.getInt("mediaResumeMinutes", 2),
        progressTasks = prefs.getBoolean("progressTasks", true),
        navigationTasks = prefs.getBoolean("navigationTasks", true),
        navigationCues = prefs.getBoolean("navigationCues", true),
        peekLevel = prefs.getInt("peekLevel", 0).coerceIn(0, 2),
        peekBlocked = prefs.getString("peekBlocked", "") ?: "",
        peekTimeouts = prefs.getString("peekTimeouts", "") ?: "",
        liveUpdates = prefs.getBoolean("liveUpdates", true),
        liveCompactLevel = prefs.getInt("liveCompactLevel", 1).coerceIn(0, 2),
        islandEverywhere = prefs.getBoolean("islandEverywhere", false),
        notificationPeek = prefs.getBoolean("notificationPeek", false),
        contactSearch = prefs.getBoolean("contactSearch", false),
        kbAutocorrect = prefs.getBoolean("kbAutocorrect", true),
        kbSuggestions = prefs.getBoolean("kbSuggestions", true),
        kbNumberRow = prefs.getBoolean("kbNumberRow", false),
        kbDoubleSpace = prefs.getBoolean("kbDoubleSpace", true),
        kbOneHanded = prefs.getInt("kbOneHanded", 0).coerceIn(0, 2),
        kbSpaceCursor = prefs.getBoolean("kbSpaceCursor", true),
        kbHapticStrength = prefs.lenientFloat("kbHapticStrength", com.jake.duolauncher.keyboard.HapticProfile.DEFAULT_STRENGTH).coerceIn(0f, 1f),
        kbHapticFeel = prefs.getInt("kbHapticFeel", 1).coerceIn(0, 2),
        kbLayouts = prefs.getString("kbLayouts", null)?.takeIf { it.isNotBlank() } ?: "qwerty",
        kbHeight = prefs.lenientFloat("kbHeight", 1f).coerceIn(.8f, 1.3f),
        kbKeyPreview = prefs.getBoolean("kbKeyPreview", true),
        kbSound = prefs.getBoolean("kbSound", prefs.getBoolean("sounds", false)),
        kbEmojiKey = prefs.getBoolean("kbEmojiKey", true),
        kbSwipe = prefs.getBoolean("kbSwipe", true),
        kbLearn = prefs.getBoolean("kbLearn", true),
        kbClipHistory = prefs.getBoolean("kbClipHistory", true),
        kbEmojiSuggestions = prefs.getBoolean("kbEmojiSuggestions", true),
        kbBlur = prefs.getBoolean("kbBlur", false),
        kbAutoCaps = prefs.getBoolean("kbAutoCaps", true),
        kbStyle = prefs.getInt("kbStyle", 0).coerceIn(0, 1),
        handoff = prefs.getBoolean("handoff", true),
        webPackage = prefs.getString("webPackage", null)?.takeIf { it.isNotBlank() },
        handoffStores = prefs.getString("handoffStores", null)?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: HandoffLogic.DEFAULT_STORES,
        haptics = prefs.getBoolean("haptics", true),
        sounds = prefs.getBoolean("sounds", false),
    )

    private fun save(next: ExtrasState) {
        prefs.edit()
            .putString("rightSwipe", next.rightSwipe.name)
            .putString("pullDown", next.pullDown.name)
            .putString("tileOrder", PanelLayout.serialize(next.tileOrder))
            .putString("hiddenTiles", PanelLayout.serialize(next.hiddenTiles))
            .putString("shortcuts", PanelLayout.serializeIds(next.shortcuts))
            .putString("iconStyle", next.iconStyle.name)
            .putBoolean("iconLighting", next.iconLighting)
            .putString("iconPack", next.iconPack)
            .putBoolean("focusOn", next.focusOn)
            .putString("focusHidden", PanelLayout.serializeIds(next.focusHidden.toList()))
            .putString("islandMuted", IslandLinks.serialize(next.islandMuted))
            .putBoolean("focusVibrate", next.focusVibrate)
            .putBoolean("focusWithDnd", next.focusWithDnd)
            .putBoolean("islandHideOverOn", next.islandHideOverOn)
            .putString("islandHideOver", IslandLinks.serialize(next.islandHideOver))
            .putBoolean("badges", next.badges)
            .putBoolean("badgeDots", next.badgeDots)
            .putString("badgeMuted", next.badgeMuted)
            .putBoolean("mediaDetails", next.mediaDetails)
            .putBoolean("callDetails", next.callDetails)
            .putBoolean("privacyIndicators", next.privacyIndicators)
            .putBoolean("securityAlerts", next.securityAlerts)
            .putBoolean("audioAlerts", next.audioAlerts)
            .putBoolean("doubleTapLock", next.doubleTapLock)
            .putBoolean("bluetoothAlerts", next.bluetoothAlerts)
            .putBoolean("peekActions", next.peekActions)
            .putBoolean("islandShortcuts", next.islandShortcuts)
            .putInt("islandWideMode", next.islandWideMode.ordinal)
            .remove("islandWide")
            .putInt("mediaResumeMinutes", next.mediaResumeMinutes)
            .putBoolean("progressTasks", next.progressTasks)
            .putBoolean("navigationTasks", next.navigationTasks)
            .putBoolean("navigationCues", next.navigationCues)
            .putInt("peekLevel", next.peekLevel)
            .putString("peekBlocked", next.peekBlocked)
            .putString("peekTimeouts", next.peekTimeouts)
            .putBoolean("liveUpdates", next.liveUpdates)
            .putInt("liveCompactLevel", next.liveCompactLevel)
            .putBoolean("islandEverywhere", next.islandEverywhere)
            .putBoolean("notificationPeek", next.notificationPeek)
            .putBoolean("contactSearch", next.contactSearch)
            .putBoolean("kbAutocorrect", next.kbAutocorrect)
            .putBoolean("kbSuggestions", next.kbSuggestions)
            .putBoolean("kbNumberRow", next.kbNumberRow)
            .putBoolean("kbDoubleSpace", next.kbDoubleSpace)
            .putInt("kbOneHanded", next.kbOneHanded)
            .putBoolean("kbSpaceCursor", next.kbSpaceCursor)
            .putFloat("kbHapticStrength", next.kbHapticStrength)
            .putInt("kbHapticFeel", next.kbHapticFeel)
            .putString("kbLayouts", next.kbLayouts)
            .putFloat("kbHeight", next.kbHeight)
            .putBoolean("kbKeyPreview", next.kbKeyPreview)
            .putBoolean("kbSound", next.kbSound)
            .putBoolean("kbEmojiKey", next.kbEmojiKey)
            .putBoolean("kbSwipe", next.kbSwipe)
            .putBoolean("kbLearn", next.kbLearn)
            .putBoolean("kbClipHistory", next.kbClipHistory)
            .putBoolean("kbEmojiSuggestions", next.kbEmojiSuggestions)
            .putBoolean("kbBlur", next.kbBlur)
            .putBoolean("kbAutoCaps", next.kbAutoCaps)
            .putInt("kbStyle", next.kbStyle)
            .putBoolean("handoff", next.handoff)
            .putString("webPackage", next.webPackage ?: "")
            .putString("handoffStores", next.handoffStores.joinToString(","))
            .putBoolean("haptics", next.haptics)
            .putBoolean("sounds", next.sounds)
            .apply()
        state = next
    }

    fun setRightSwipe(value: RightSwipe) = save(state.copy(rightSwipe = value))
    fun setPullDown(value: PullDown) = save(state.copy(pullDown = value))
    fun moveTile(tile: PanelTile, delta: Int) = save(state.copy(tileOrder = PanelLayout.move(state.tileOrder, tile, delta)))
    fun setTileHidden(tile: PanelTile, hidden: Boolean) =
        save(state.copy(hiddenTiles = if (hidden) state.hiddenTiles + tile else state.hiddenTiles - tile))
    fun toggleShortcut(id: String) = save(state.copy(shortcuts = PanelLayout.toggleShortcut(state.shortcuts, id)))
    fun setIconStyle(value: IconStyle) = save(state.copy(iconStyle = value))
    fun setIconLighting(value: Boolean) = save(state.copy(iconLighting = value))
    fun setIconPack(value: String) = save(state.copy(iconPack = value.take(200)))
    fun setFocusOn(value: Boolean) = save(state.copy(focusOn = value))
    fun toggleFocusHidden(id: String) =
        save(state.copy(focusHidden = if (id in state.focusHidden) state.focusHidden - id else state.focusHidden + id))
    fun toggleIslandLink(packageName: String) = save(state.copy(islandMuted = IslandLinks.toggle(state.islandMuted, packageName)))
    fun setFocusVibrate(value: Boolean) = save(state.copy(focusVibrate = value))
    fun setFocusWithDnd(value: Boolean) = save(state.copy(focusWithDnd = value))
    fun setIslandHideOverOn(value: Boolean) = save(state.copy(islandHideOverOn = value))
    fun toggleIslandHideOver(packageName: String) = save(state.copy(islandHideOver = IslandLinks.toggle(state.islandHideOver, packageName)))
    fun setBadges(value: Boolean) = save(state.copy(badges = value))
    fun setBadgeDots(value: Boolean) = save(state.copy(badgeDots = value))
    /** Turns badges off (or back on) for one app. */
    fun setBadgeMuted(packageName: String, muted: Boolean) = save(state.copy(badgeMuted = BadgeMute.encode(BadgeMute.with(BadgeMute.parse(state.badgeMuted), packageName, muted))))
    fun setMediaDetails(value: Boolean) = save(state.copy(mediaDetails = value))
    fun setCallDetails(value: Boolean) = save(state.copy(callDetails = value))
    fun setIslandWideMode(value: IslandWideMode) = save(state.copy(islandWideMode = value))
    fun setMediaResumeMinutes(value: Int) = save(state.copy(mediaResumeMinutes = value))
    fun setProgressTasks(value: Boolean) = save(state.copy(progressTasks = value))
    fun setNavigationTasks(value: Boolean) = save(state.copy(navigationTasks = value))
    fun setNavigationCues(value: Boolean) = save(state.copy(navigationCues = value))
    fun setPeekLevel(value: Int) = save(state.copy(peekLevel = value.coerceIn(0, 2)))
    fun setPeekBlocked(value: String) = save(state.copy(peekBlocked = value.take(600)))
    fun cyclePeekTimeout(packageName: String) =
        save(state.copy(peekTimeouts = PeekTimeouts.serialize(PeekTimeouts.cycle(PeekTimeouts.parse(state.peekTimeouts), packageName))))
    fun setIslandShortcuts(value: Boolean) = save(state.copy(islandShortcuts = value))
    fun setAudioAlerts(value: Boolean) = save(state.copy(audioAlerts = value))
    fun setDoubleTapLock(value: Boolean) = save(state.copy(doubleTapLock = value))
    fun setBluetoothAlerts(value: Boolean) = save(state.copy(bluetoothAlerts = value))
    fun setPeekActions(value: Boolean) = save(state.copy(peekActions = value))
    fun setPrivacyIndicators(value: Boolean) = save(state.copy(privacyIndicators = value))
    fun setSecurityAlerts(value: Boolean) = save(state.copy(securityAlerts = value))
    fun setLiveUpdates(value: Boolean) = save(state.copy(liveUpdates = value))
    fun setLiveCompactLevel(value: Int) = save(state.copy(liveCompactLevel = value.coerceIn(0, 2)))
    fun setIslandEverywhere(value: Boolean) = save(state.copy(islandEverywhere = value))
    fun setNotificationPeek(value: Boolean) = save(state.copy(notificationPeek = value))
    fun setContactSearch(value: Boolean) = save(state.copy(contactSearch = value))
    fun setKbAutocorrect(value: Boolean) = save(state.copy(kbAutocorrect = value))
    fun setKbSuggestions(value: Boolean) = save(state.copy(kbSuggestions = value))
    fun setKbNumberRow(value: Boolean) = save(state.copy(kbNumberRow = value))
    fun setKbDoubleSpace(value: Boolean) = save(state.copy(kbDoubleSpace = value))
    fun setKbOneHanded(value: Int) = save(state.copy(kbOneHanded = value.coerceIn(0, 2)))
    fun setKbSpaceCursor(value: Boolean) = save(state.copy(kbSpaceCursor = value))
    fun setKbHapticStrength(value: Float) = save(state.copy(kbHapticStrength = value.coerceIn(0f, 1f)))
    fun setKbHapticFeel(value: Int) = save(state.copy(kbHapticFeel = value.coerceIn(0, 2)))
    /** Turns a layout on or off; the last one left cannot be turned off. */
    fun toggleKbLayout(id: String) {
        val on = com.jake.duolauncher.keyboard.KeyLayouts.parse(state.kbLayouts).map { it.id }
        val next = if (id in on) on - id else on + id
        if (next.isNotEmpty()) save(state.copy(kbLayouts = next.joinToString(",")))
    }
    fun setKbHeight(value: Float) = save(state.copy(kbHeight = value.coerceIn(.8f, 1.3f)))
    fun setKbKeyPreview(value: Boolean) = save(state.copy(kbKeyPreview = value))
    fun setKbSound(value: Boolean) = save(state.copy(kbSound = value))
    fun setKbEmojiKey(value: Boolean) = save(state.copy(kbEmojiKey = value))
    fun setKbSwipe(value: Boolean) = save(state.copy(kbSwipe = value))
    fun setKbLearn(value: Boolean) = save(state.copy(kbLearn = value))
    fun setKbClipHistory(value: Boolean) = save(state.copy(kbClipHistory = value))
    fun setKbEmojiSuggestions(value: Boolean) = save(state.copy(kbEmojiSuggestions = value))
    fun setKbBlur(value: Boolean) = save(state.copy(kbBlur = value))
    fun setKbAutoCaps(value: Boolean) = save(state.copy(kbAutoCaps = value))
    fun setKbStyle(value: Int) = save(state.copy(kbStyle = value.coerceIn(0, 1)))
    fun setHandoff(value: Boolean) = save(state.copy(handoff = value))
    fun setWebPackage(value: String?) = save(state.copy(webPackage = value))
    fun toggleHandoffStore(id: String) =
        save(state.copy(handoffStores = if (id in state.handoffStores) state.handoffStores - id else state.handoffStores + id))
    fun setHaptics(value: Boolean) = save(state.copy(haptics = value))
    fun setSounds(value: Boolean) = save(state.copy(sounds = value))
}

/**
 * Settings that 0.104 took out of Launcher settings, pinned to the one value each now has. Many are read straight from the preferences by
 * the island overlay, the keyboard service and the notification listener, so the value is written to the preference itself; done on every
 * load, so a settings backup from an older version cannot bring a retired value back.
 * - The island's idle width is the plain pill (Off); the Wide and Extra wide modes are gone. Live Updates show their short status.
 * - "Keep the Home island still" is automatic: whenever the optional accessibility service is on, it draws the Home island.
 * - Turn-by-turn cards follow the Navigation switch; the audio-device alerts follow the one System alerts switch.
 * - The keyboard never blurs the app behind it, and always has the emoji key, magnified keys and the space-bar cursor.
 */
internal fun fixRetiredSettings(prefs: android.content.SharedPreferences) {
    prefs.edit()
        .putInt("islandWideMode", IslandWideMode.OFF.ordinal).remove("islandWide")
        .putInt("liveCompactLevel", 1)
        .putBoolean("homeIslandOverlay", true)
        .putBoolean("navigationCues", prefs.getBoolean("navigationTasks", true))
        .putBoolean("audioAlerts", prefs.getBoolean("securityAlerts", true))
        .putBoolean("kbBlur", false).putBoolean("kbEmojiKey", true).putBoolean("kbKeyPreview", true).putBoolean("kbSpaceCursor", true)
        .apply()
}

/** What the extras settings page and the control panel need from the activity: the store, plus the
 * actions that have to go through Android (permission prompts, settings screens, Focus's ringer).
 */
internal class ExtrasActions(
    val store: ExtrasStore,
    val requestContacts: () -> Unit = {},
    val hasContactsPermission: () -> Boolean = { false },
    val requestBluetooth: () -> Unit = {},
    val hasBluetoothPermission: () -> Boolean = { false },
    val openNotificationAccess: () -> Unit = {},
    val hasNotificationAccess: () -> Boolean = { false },
    val toggleFocus: () -> Unit = {},
    val islandWidget: IslandWidgetController? = null,
)
