package com.jake.duolauncher

import android.content.Context
import androidx.compose.runtime.*

/** Everything the "extras" features remember, in one preference file. Every grant-backed feature
 * defaults to off: badges, media details and contact search do nothing until the user turns them on
 * (and then grants Android's own permission), so a fresh install asks for nothing.
 */
internal data class ExtrasState(
    val rightSwipe: RightSwipe = RightSwipe.PANEL,
    val tileOrder: List<PanelTile> = PanelLayout.DEFAULT_ORDER,
    val hiddenTiles: Set<PanelTile> = emptySet(),
    val shortcuts: List<String> = emptyList(),
    val iconStyle: IconStyle = IconStyle.ORIGINAL,
    val focusOn: Boolean = false,
    val focusHidden: Set<String> = emptySet(),
    val focusVibrate: Boolean = false,
    val badges: Boolean = false,
    val mediaDetails: Boolean = false,
    val notificationPeek: Boolean = false,
    val contactSearch: Boolean = false,
)

internal class ExtrasStore(context: Context) {
    private val prefs = context.getSharedPreferences("extras", Context.MODE_PRIVATE)
    var state by mutableStateOf(load()); private set

    private fun load() = ExtrasState(
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
        notificationPeek = prefs.getBoolean("notificationPeek", false),
        contactSearch = prefs.getBoolean("contactSearch", false),
    )

    private fun save(next: ExtrasState) {
        prefs.edit()
            .putString("rightSwipe", next.rightSwipe.name)
            .putString("tileOrder", PanelLayout.serialize(next.tileOrder))
            .putString("hiddenTiles", PanelLayout.serialize(next.hiddenTiles))
            .putString("shortcuts", PanelLayout.serializeIds(next.shortcuts))
            .putString("iconStyle", next.iconStyle.name)
            .putBoolean("focusOn", next.focusOn)
            .putString("focusHidden", PanelLayout.serializeIds(next.focusHidden.toList()))
            .putBoolean("focusVibrate", next.focusVibrate)
            .putBoolean("badges", next.badges)
            .putBoolean("mediaDetails", next.mediaDetails)
            .putBoolean("notificationPeek", next.notificationPeek)
            .putBoolean("contactSearch", next.contactSearch)
            .apply()
        state = next
    }

    fun setRightSwipe(value: RightSwipe) = save(state.copy(rightSwipe = value))
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
    fun setNotificationPeek(value: Boolean) = save(state.copy(notificationPeek = value))
    fun setContactSearch(value: Boolean) = save(state.copy(contactSearch = value))
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
)
