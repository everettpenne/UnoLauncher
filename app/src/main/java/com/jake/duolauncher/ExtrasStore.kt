package com.jake.duolauncher

import android.content.Context
import androidx.compose.runtime.*

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
    val focusOn: Boolean = false,
    val focusHidden: Set<String> = emptySet(),
    val focusVibrate: Boolean = false,
    val badges: Boolean = false,
    val mediaDetails: Boolean = false,
    val callDetails: Boolean = true,
    val privacyIndicators: Boolean = true,
    val securityAlerts: Boolean = true,
    val liveUpdates: Boolean = true,
    val islandEverywhere: Boolean = false,
    val notificationPeek: Boolean = false,
    val contactSearch: Boolean = false,
    val kbAutocorrect: Boolean = true,
    val kbSuggestions: Boolean = true,
    val kbNumberRow: Boolean = false,
    val kbOneHanded: Int = 0,
    val kbSpaceCursor: Boolean = true,
    val kbHapticStrength: Float = com.jake.duolauncher.keyboard.HapticProfile.DEFAULT_STRENGTH,
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

    private fun load() = ExtrasState(
        // A new key, so everyone gets the new default rather than a value saved by an earlier version.
        pullDown = PullDownRouting.migrate(prefs.getString("pullDown", null), prefs.getString("leftSwipe", null)),
        rightSwipe = runCatching { RightSwipe.valueOf(prefs.getString("rightSwipe", null)!!) }.getOrDefault(RightSwipe.PANEL),
        tileOrder = PanelLayout.parseOrder(prefs.getString("tileOrder", null)),
        hiddenTiles = PanelLayout.parseSet(prefs.getString("hiddenTiles", null)),
        shortcuts = PanelLayout.parseIds(prefs.getString("shortcuts", null)),
        iconStyle = IconStyle.fromPreference(prefs.getString("iconStyle", null)),
        focusOn = prefs.getBoolean("focusOn", false),
        focusHidden = PanelLayout.parseIds(prefs.getString("focusHidden", null)).toSet(),
        focusVibrate = prefs.getBoolean("focusVibrate", false),
        badges = prefs.getBoolean("badges", false),
        mediaDetails = prefs.getBoolean("mediaDetails", false),
        callDetails = prefs.getBoolean("callDetails", true),
        privacyIndicators = prefs.getBoolean("privacyIndicators", true),
        securityAlerts = prefs.getBoolean("securityAlerts", true),
        liveUpdates = prefs.getBoolean("liveUpdates", true),
        islandEverywhere = prefs.getBoolean("islandEverywhere", false),
        notificationPeek = prefs.getBoolean("notificationPeek", false),
        contactSearch = prefs.getBoolean("contactSearch", false),
        kbAutocorrect = prefs.getBoolean("kbAutocorrect", true),
        kbSuggestions = prefs.getBoolean("kbSuggestions", true),
        kbNumberRow = prefs.getBoolean("kbNumberRow", false),
        kbOneHanded = prefs.getInt("kbOneHanded", 0).coerceIn(0, 2),
        kbSpaceCursor = prefs.getBoolean("kbSpaceCursor", true),
        kbHapticStrength = prefs.getFloat("kbHapticStrength", com.jake.duolauncher.keyboard.HapticProfile.DEFAULT_STRENGTH).coerceIn(0f, 1f),
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
            .putBoolean("focusOn", next.focusOn)
            .putString("focusHidden", PanelLayout.serializeIds(next.focusHidden.toList()))
            .putBoolean("focusVibrate", next.focusVibrate)
            .putBoolean("badges", next.badges)
            .putBoolean("mediaDetails", next.mediaDetails)
            .putBoolean("callDetails", next.callDetails)
            .putBoolean("privacyIndicators", next.privacyIndicators)
            .putBoolean("securityAlerts", next.securityAlerts)
            .putBoolean("liveUpdates", next.liveUpdates)
            .putBoolean("islandEverywhere", next.islandEverywhere)
            .putBoolean("notificationPeek", next.notificationPeek)
            .putBoolean("contactSearch", next.contactSearch)
            .putBoolean("kbAutocorrect", next.kbAutocorrect)
            .putBoolean("kbSuggestions", next.kbSuggestions)
            .putBoolean("kbNumberRow", next.kbNumberRow)
            .putInt("kbOneHanded", next.kbOneHanded)
            .putBoolean("kbSpaceCursor", next.kbSpaceCursor)
            .putFloat("kbHapticStrength", next.kbHapticStrength)
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
    fun setFocusOn(value: Boolean) = save(state.copy(focusOn = value))
    fun toggleFocusHidden(id: String) =
        save(state.copy(focusHidden = if (id in state.focusHidden) state.focusHidden - id else state.focusHidden + id))
    fun setFocusVibrate(value: Boolean) = save(state.copy(focusVibrate = value))
    fun setBadges(value: Boolean) = save(state.copy(badges = value))
    fun setMediaDetails(value: Boolean) = save(state.copy(mediaDetails = value))
    fun setCallDetails(value: Boolean) = save(state.copy(callDetails = value))
    fun setPrivacyIndicators(value: Boolean) = save(state.copy(privacyIndicators = value))
    fun setSecurityAlerts(value: Boolean) = save(state.copy(securityAlerts = value))
    fun setLiveUpdates(value: Boolean) = save(state.copy(liveUpdates = value))
    fun setIslandEverywhere(value: Boolean) = save(state.copy(islandEverywhere = value))
    fun setNotificationPeek(value: Boolean) = save(state.copy(notificationPeek = value))
    fun setContactSearch(value: Boolean) = save(state.copy(contactSearch = value))
    fun setKbAutocorrect(value: Boolean) = save(state.copy(kbAutocorrect = value))
    fun setKbSuggestions(value: Boolean) = save(state.copy(kbSuggestions = value))
    fun setKbNumberRow(value: Boolean) = save(state.copy(kbNumberRow = value))
    fun setKbOneHanded(value: Int) = save(state.copy(kbOneHanded = value.coerceIn(0, 2)))
    fun setKbSpaceCursor(value: Boolean) = save(state.copy(kbSpaceCursor = value))
    fun setKbHapticStrength(value: Float) = save(state.copy(kbHapticStrength = value.coerceIn(0f, 1f)))
    fun setHandoff(value: Boolean) = save(state.copy(handoff = value))
    fun setWebPackage(value: String?) = save(state.copy(webPackage = value))
    fun toggleHandoffStore(id: String) =
        save(state.copy(handoffStores = if (id in state.handoffStores) state.handoffStores - id else state.handoffStores + id))
    fun setHaptics(value: Boolean) = save(state.copy(haptics = value))
    fun setSounds(value: Boolean) = save(state.copy(sounds = value))
}

/** What the extras settings page and the control panel need from the activity: the store, plus the
 * actions that have to go through Android (permission prompts, settings screens, Focus's ringer).
 */
internal class ExtrasActions(
    val store: ExtrasStore,
    val requestContacts: () -> Unit = {},
    val hasContactsPermission: () -> Boolean = { false },
    val openNotificationAccess: () -> Unit = {},
    val hasNotificationAccess: () -> Boolean = { false },
    val toggleFocus: () -> Unit = {},
    val islandWidget: IslandWidgetController? = null,
)
