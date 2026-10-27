package com.jake.duolauncher

import com.jake.duolauncher.island.PreviewScene

import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.island.*
import com.jake.duolauncher.search.*
import com.jake.duolauncher.panel.*
import com.jake.duolauncher.appearance.*

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Which part of the extras a Customize page shows (each is its own page, reached from the overview). */
internal enum class ExtrasSection { KEYBOARD, GESTURES, ICONS, SEARCH, PRIVACY }

/** One of the extras pages of the Customize sheet, as grouped lists (see SettingsGroup): short names, the detail in each group's footer. */
@Composable
internal fun ExtrasSettingsPage(actions: ExtrasActions, apps: List<AppEntry>, section: ExtrasSection) {
    val store = actions.store
    val s = store.state
    when (section) {
        ExtrasSection.KEYBOARD -> KeyboardPage(store, s)
        ExtrasSection.GESTURES -> GesturesGroups(store, s, apps)
        ExtrasSection.ICONS -> IconGroups(store, s)
        ExtrasSection.SEARCH -> SearchGroups(actions, store, s)
        ExtrasSection.PRIVACY -> PrivacyGroups(actions, store, s, apps)
    }
}

@Composable
private fun GesturesGroups(store: ExtrasStore, s: ExtrasState, apps: List<AppEntry>) {
    SettingsGroup(header = stringResource(com.jake.duolauncher.R.string.pulling_down_on_home),
        footer = "Like iOS: from the top edge, notifications on the left and the control panel on the right; from lower down, search.") {
        row { CheckRow("Like iOS", s.pullDown == PullDown.SMART, "pull-smart") { store.setPullDown(PullDown.SMART) } }
        row { CheckRow("Notifications on the Left", s.pullDown == PullDown.NOTIFICATIONS, "pull-notifications") { store.setPullDown(PullDown.NOTIFICATIONS) } }
        row { CheckRow("Search on the Left", s.pullDown == PullDown.SEARCH, "pull-search") { store.setPullDown(PullDown.SEARCH) } }
    }
    SettingsGroup(header = "Right Side",
        footer = "Android Mode opens Android's own Quick Settings in place of Uno's control panel. It needs the optional shade accessibility service.") {
        row { CheckRow("Control Panel", s.rightSwipe == RightSwipe.PANEL, "swipe-panel") { store.setRightSwipe(RightSwipe.PANEL) } }
        row { CheckRow("Android Mode", s.rightSwipe == RightSwipe.SYSTEM, "swipe-system") { store.setRightSwipe(RightSwipe.SYSTEM) } }
    }
    SettingsGroup(footer = "Double-tap an empty spot on Home to lock the screen. Android allows this only through Uno's accessibility service, which you turn on yourself.") {
        row { SettingsSwitch("Double-Tap to Lock", s.doubleTapLock, store::setDoubleTapLock, "double-tap-lock-switch") }
    }
    SettingsGroup(header = stringResource(com.jake.duolauncher.R.string.feel_and_sound),
        footer = "Haptics tick on page changes, sliders and the control panel. Sounds are Android's own touch sounds and follow its volume and silent mode.") {
        row { SettingsSwitch("Haptics", s.haptics, store::setHaptics, "haptics-switch") }
        row { SettingsSwitch("Sounds", s.sounds, store::setSounds, "sounds-switch") }
    }
    SettingsGroup(header = stringResource(com.jake.duolauncher.R.string.control_panel_tiles), footer = "Switch a tile off to hide it; the arrows change the order.") {
        s.tileOrder.forEachIndexed { index, tile -> row {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tile.label, Modifier.weight(1f))
                IconButton(onClick = { store.moveTile(tile, -1) }, enabled = index > 0, modifier = Modifier.testTag("tile-up-${tile.name}")) {
                    Icon(Icons.Rounded.KeyboardArrowUp, "Move ${tile.label} up") }
                IconButton(onClick = { store.moveTile(tile, 1) }, enabled = index < s.tileOrder.lastIndex, modifier = Modifier.testTag("tile-down-${tile.name}")) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Move ${tile.label} down") }
                LiquidSwitchControl(tile !in s.hiddenTiles, { store.setTileHidden(tile, !it) }, LocalPageGlass.current,
                    Modifier.testTag("tile-switch-${tile.name}"))
            }
        } }
    }
    val shortcutsOpen = rememberChecklistOpen("shortcut")
    SettingsGroup(header = stringResource(com.jake.duolauncher.R.string.shortcuts_in_the_panel),
        footer = "Up to ${PanelLayout.MAX_SHORTCUTS} apps; choosing another replaces the oldest.") {
        appChecklist(shortcutsOpen, apps, s.shortcuts.toSet(), "shortcut") { store.toggleShortcut(it.id) }
    }
}

@Composable
private fun IconGroups(store: ExtrasStore, s: ExtrasState) {
    SettingsGroup(header = "App Icons", footer = "Themed uses each app's own one-colour icon where it has one. Tinted takes its colour from your wallpaper. Dark puts each app's colours on a near-black tile, and Auto does that while Uno is dark. Icon Lighting gives original icons a soft lit edge, like iOS.") {
        row { SegmentRow(IconStyle.entries, s.iconStyle, { when (it) { IconStyle.ORIGINAL -> "Original"; IconStyle.THEMED -> "Themed"; IconStyle.TINTED -> "Tinted"; IconStyle.DARK -> "Dark"; IconStyle.AUTOMATIC -> "Auto" } },
            { when (it) { IconStyle.ORIGINAL -> "icons-original"; IconStyle.THEMED -> "icons-themed"; IconStyle.TINTED -> "icons-tinted"; IconStyle.DARK -> "icons-dark"; IconStyle.AUTOMATIC -> "icons-auto" } },
            store::setIconStyle) }
        row { SettingsSwitch("Icon Lighting", s.iconLighting, store::setIconLighting, "icons-lit") }
    }
    IconPackSettings(s.iconPack, store::setIconPack)
}

@Composable
private fun SearchGroups(actions: ExtrasActions, store: ExtrasStore, s: ExtrasState) {
    val resolver = rememberHandoffResolver(s)
    SettingsGroup(footer = "When no app on the phone matches, search offers app stores and a web search.") {
        row { SettingsSwitch("App & Web Suggestions", s.handoff, store::setHandoff, "handoff-switch") }
    }
    if (s.handoff) {
        SettingsGroup(header = stringResource(com.jake.duolauncher.R.string.app_stores)) {
            HandoffLogic.KNOWN_STORES.forEach { storeInfo -> row {
                val present = resolver.isStoreInstalled(storeInfo)
                ToggleCheckRow(storeInfo.label, storeInfo.id in s.handoffStores, "handoff-store-${storeInfo.id}", enabled = present,
                    detail = if (present) null else stringResource(com.jake.duolauncher.R.string.not_installed)) { store.toggleHandoffStore(storeInfo.id) }
            } }
        }
        SettingsGroup(header = "Web Search", footer = "Automatic uses Vanadium if it is installed, otherwise any browser.") {
            row { CheckRow("Automatic", s.webPackage == null, "web-auto") { store.setWebPackage(null) } }
            resolver.installedBrowsers().forEach { (pkg, label) -> row { CheckRow(label, s.webPackage == pkg, "web-$pkg") { store.setWebPackage(pkg) } } }
        }
    }
    SettingsGroup(footer = "The App Library also finds people. Asks for Android's contacts permission; names stay on the phone.") {
        row { SettingsSwitch("Contacts", s.contactSearch, { on ->
            if (on) { store.setContactSearch(true); if (!actions.hasContactsPermission()) actions.requestContacts() }
            else store.setContactSearch(false)
        }, "contact-search-switch") }
    }
}

@Composable
private fun PrivacyGroups(actions: ExtrasActions, store: ExtrasStore, s: ExtrasState, apps: List<AppEntry>) {
    val accessOn = actions.hasNotificationAccess()
    SettingsGroup(header = "Notifications", footer = (if (accessOn) "Notification access is on." else "Notification access is off: Uno reads nothing until you turn it on in Android's settings.") +
        " Everything stays on this phone. Badges count unread notifications per app. Long-press an app to turn its badges off.") {
        row { ActionRow(if (accessOn) "Notification Access Settings" else "Turn On Notification Access", "notification-access", onClick = actions.openNotificationAccess) }
        row { SettingsSwitch("Badges", s.badges, store::setBadges, "badges-switch") }
        if (s.badges) {
            row { CheckRow("Count", !s.badgeDots, "badge-style-count") { store.setBadgeDots(false) } }
            row { CheckRow("Dot", s.badgeDots, "badge-style-dot") { store.setBadgeDots(true) } }
        }
        row { SettingsSwitch("Media Details", s.mediaDetails, store::setMediaDetails, "media-details-switch") }
    }
    SensorLogGroup(store, s)
    val focusOpen = rememberChecklistOpen("focus")
    SettingsGroup(header = stringResource(com.jake.duolauncher.R.string.focus),
        footer = "Focus hides the apps you pick from Home and the App Library until it is off. Nothing is uninstalled and your layout does not change. With Do Not Disturb, it turns on and off with Android's Do Not Disturb (and its schedules).") {
        row { SettingsSwitch("Focus", s.focusOn, { actions.toggleFocus() }, "focus-switch") }
        row { SettingsSwitch("With Do Not Disturb", s.focusWithDnd, store::setFocusWithDnd, "focus-dnd-switch") }
        row { SettingsSwitch("Vibrate Only", s.focusVibrate, store::setFocusVibrate, "focus-vibrate-switch") }
        appChecklist(focusOpen, apps, s.focusHidden, "focus") { store.toggleFocusHidden(it.id) }
    }
}

/**
 * The island's groups below its on/off switches: what it shows, notifications, paused media, its widget, the apps allowed to use it and the
 * timer. A row that changes how the island looks asks the preview at the top of the page ([LocalIslandPreview]) to play the situation that
 * shows it, so the change can be seen before leaving the page.
 */
@Composable
internal fun IslandSettingsGroups(actions: ExtrasActions, apps: List<AppEntry>) {
    val store = actions.store
    val s = store.state
    val preview = com.jake.duolauncher.island.LocalIslandPreview.current
    fun demo(scene: PreviewScene) { preview?.show(scene) }
    val systemAlerts = s.securityAlerts
    SettingsGroup(header = "Shows",
        footer = "Live Updates are Android 16's ongoing notifications (a ride, a delivery). Navigation drops the island down for each turn. System Alerts flash for VPN, USB data, headphones and Bluetooth devices; Bluetooth names need Android's Nearby devices permission.") {
        row { SettingsSwitch("Live Updates", s.liveUpdates, { store.setLiveUpdates(it); demo(PreviewScene.LIVE) }, "live-updates-switch") }
        row { SettingsSwitch("Downloads & Progress", s.progressTasks, store::setProgressTasks, "progress-tasks-switch") }
        row { SettingsSwitch("Navigation", s.navigationTasks, { store.setNavigationTasks(it); store.setNavigationCues(it) }, "navigation-tasks-switch") }
        row { SettingsSwitch("Calls", s.callDetails, store::setCallDetails, "call-details-switch") }
        row { SettingsSwitch("Camera & Mic Indicators", s.privacyIndicators, { store.setPrivacyIndicators(it); demo(PreviewScene.PRIVACY) }, "privacy-indicators-switch") }
        row { SettingsSwitch("System Alerts", systemAlerts, { on ->
            store.setSecurityAlerts(on); store.setAudioAlerts(on); store.setBluetoothAlerts(on)
            if (on && !actions.hasBluetoothPermission()) actions.requestBluetooth()
        }, "security-alerts-switch") }
        row { SettingsSwitch("App Shortcuts", s.islandShortcuts, { store.setIslandShortcuts(it); demo(PreviewScene.OPEN) }, "island-shortcuts-switch") }
    }
    // Without notification access nothing below can happen, and the switches look like they work: say so here, with the way to fix it.
    val accessOff = !actions.hasNotificationAccess()
    SettingsGroup(header = "Notifications", footer = (if (accessOff) "Notification access is off, so nothing can drop down yet. Turning it on in Android's settings is the only step. " else "") + "How much a new notification shows when the island drops down for it. To show the text, Uno reads it and keeps nothing. People Only lets just messages from people drop it down. A chat that floods is quieted for a couple of minutes, and an app whose drop-downs you never open stops dropping down; it is listed below to bring back.") {
        if (accessOff) row { ActionRow("Turn On Notification Access", "island-notification-access", onClick = actions.openNotificationAccess) }
        row { SettingsSwitch("Show Notifications", s.notificationPeek, { store.setNotificationPeek(it); demo(PreviewScene.NOTIFICATION) }, "peek-switch") }
        if (s.notificationPeek) {
            row { SettingsSwitch("People Only", s.peekPeopleOnly, store::setPeekPeopleOnly, "peek-people-switch") }
            row { CheckRow("App Name", s.peekLevel == 0, "peek-level-name") { store.setPeekLevel(0); demo(PreviewScene.NOTIFICATION) } }
            row { CheckRow("Name & Title", s.peekLevel == 1, "peek-level-title") { store.setPeekLevel(1); demo(PreviewScene.NOTIFICATION) } }
            row { CheckRow("Name, Title & Text", s.peekLevel == 2, "peek-level-text") { store.setPeekLevel(2); demo(PreviewScene.NOTIFICATION) } }
            row { SettingsSwitch("Action Buttons", s.peekActions, { store.setPeekActions(it); demo(PreviewScene.NOTIFICATION) }, "peek-actions-switch") }
        }
    }
    // Apps quieted for never being opened (PeekSmarts): each can be brought back, which forgets what was learned about it.
    if (s.notificationPeek) {
        val context = androidx.compose.ui.platform.LocalContext.current
        var quieted by remember { mutableStateOf(PeekLearning.quieted(context)) }
        if (quieted.isNotEmpty()) SettingsGroup(header = "Quieted Automatically", footer = "These apps' drop-downs were never opened, so they stopped. Tap one to bring it back.") {
            quieted.sorted().forEach { pkg -> row {
                val label = apps.firstOrNull { it.packageName == pkg }?.label ?: pkg
                ActionRow("Bring Back $label", "peek-quieted-$pkg") { PeekLearning.opened(context, pkg); quieted = PeekLearning.quieted(context) }
            } }
        }
    }
    if (s.notificationPeek) SettingsGroup(footer = stringResource(com.jake.duolauncher.R.string.separate_with_commas_a_match_shows)) {
        row { Box(Modifier.padding(vertical = Space.M.dp)) {
            GlassField(s.peekBlocked, { store.setPeekBlocked(it) }, Modifier.fillMaxWidth().testTag("peek-blocked"), minLines = 1, maxLines = 3,
                placeholder = { Text(stringResource(com.jake.duolauncher.R.string.island_hide_words)) })
        } }
    }
    SettingsGroup(header = "Paused Media", footer = "How long paused music or a podcast stays in the island.") {
        IslandPlayback.RESUME_CHOICES_MIN.forEach { minutes -> row {
            CheckRow(if (minutes == 1) "1 Minute" else "$minutes Minutes", s.mediaResumeMinutes == minutes, "media-resume-$minutes") { store.setMediaResumeMinutes(minutes) }
        } }
    }
    IslandWidgetSetting(actions.islandWidget)
    IslandLinksSetting(actions, apps)
    if (s.islandEverywhere) {
        val hideOpen = rememberChecklistOpen("island-hide-over")
        val serviceOn = SystemShadeAccessibilityService.isConnected()
        SettingsGroup(header = "Hide Over Apps", footer = buildString {
            append("Step Aside in Its App takes an activity off the island while its own app is in front, as on iPhone: music while you are in the player, a ride while you are in its app. ")
            append("Hide Over Chosen Apps takes the whole island away while one of the apps you pick is in front. ")
            append("For either, Uno's accessibility service is told the name of each app that comes to the front, and nothing else: not what is in it or on screen. It keeps only the latest name, in memory, and stops being told when both are off.")
            if (!serviceOn) append(" Needs Uno's accessibility service, which is off.")
        }) {
            row { SettingsSwitch("Step Aside in Its App", s.islandHideInOwnApp, store::setIslandHideInOwnApp, "island-own-app-switch") }
            row { SettingsSwitch("Hide Over Chosen Apps", s.islandHideOverOn, store::setIslandHideOverOn, "island-hide-over-switch") }
            if (s.islandHideOverOn) appChecklist(hideOpen, apps.filter { it.user == android.os.Process.myUserHandle() }.distinctBy { it.packageName },
                emptySet(), "island-hide-over", count = s.islandHideOver.size, isOn = { it.packageName in s.islandHideOver }) {
                store.toggleIslandHideOver(it.packageName)
            }
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val exact = IslandTools.exactAlarmsAllowed(context)
    SettingsGroup(header = "Timer", footer = if (exact) "Exact alarms are allowed, so the timer ends on time even with Home closed."
        else "The timer ends on time while the launcher is running, and within a minute or so otherwise. Allow exact alarms for to-the-second timing.") {
        if (!exact) row { ActionRow(stringResource(com.jake.duolauncher.R.string.allow_exact_alarms), "exact-alarm") {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                android.net.Uri.parse("package:${context.packageName}")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } }
    }
    SettingsGroup(header = stringResource(com.jake.duolauncher.R.string.what_shows_where), footer = listOf(
        com.jake.duolauncher.R.string.what_shows_where_intro, com.jake.duolauncher.R.string.what_shows_where_ongoing,
        com.jake.duolauncher.R.string.what_shows_where_notification, com.jake.duolauncher.R.string.what_shows_where_system,
        com.jake.duolauncher.R.string.what_shows_where_privacy, com.jake.duolauncher.R.string.what_shows_where_time).map { stringResource(it) }.joinToString("\n")) {}
    // Developer HUD: only offered by debuggable builds, so a release build has neither the switch nor the overlay.
    if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
        val hudPrefs = remember { context.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE) }
        var hudOn by remember { mutableStateOf(hudPrefs.getBoolean("debugHud", false)) }
        SettingsGroup(footer = stringResource(com.jake.duolauncher.R.string.shows_frame_times_jank_the_island)) {
            row { SettingsSwitch("Developer HUD (debug build)", hudOn, { hudOn = it; hudPrefs.edit().putBoolean("debugHud", it).apply() }, "debug-hud-switch") }
        }
    }
}

/**
 * A collapsed list of every app, each checked or not; "Choose Apps" opens it, so a long list isn't drawn until wanted. Its rows join the
 * group it is in. [open] is held by the page, so the group knows which rows to show.
 */
private fun SettingsRows.appChecklist(open: MutableState<Boolean>, apps: List<AppEntry>, selected: Set<String>, tag: String,
    count: Int = selected.size, isOn: (AppEntry) -> Boolean = { it.id in selected }, trailing: (@Composable (AppEntry) -> Unit)? = null,
    onToggle: (AppEntry) -> Unit) {
    row {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = androidx.compose.ui.semantics.Role.Button) { open.value = !open.value }
            .testTag("$tag-choose"), verticalAlignment = Alignment.CenterVertically) {
            Text(if (open.value) "Done" else "Choose Apps", Modifier.weight(1f), color = SettingsAccent)
            Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(if (open.value) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (open.value) apps.forEach { app -> row {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                ToggleCheckRow(app.label, isOn(app), "$tag-app-${app.id}", leading = { Image(app.image, null, Modifier.size(30.dp).clip(Corner.icon)) }) { onToggle(app) }
            }
            trailing?.invoke(app)
        }
    } }
}

/** The page's own state for an [appChecklist]. */
@Composable private fun rememberChecklistOpen(tag: String): MutableState<Boolean> = rememberSaveable(tag) { mutableStateOf(false) }

/** Choose, change or remove the one widget the open island shows. */
@Composable
private fun IslandWidgetSetting(controller: IslandWidgetController?) {
    if (controller == null) return
    var picking by remember { mutableStateOf(false) }
    val id = IslandWidgetState.id
    SettingsGroup(header = stringResource(com.jake.duolauncher.R.string.widget_in_the_island),
        footer = controller.message ?: "The open island shows this widget below its controls.") {
        row { ValueRow("Widget", if (id >= 0) controller.currentLabel() ?: "Unavailable" else "None", "island-widget-current") }
        row { ActionRow(if (id >= 0) "Change Widget" else "Choose Widget", "island-widget-choose") { picking = true } }
        if (id >= 0) row { ActionRow(stringResource(com.jake.duolauncher.R.string.remove), "island-widget-remove", destructive = true, onClick = controller::remove) }
    }
    if (picking) {
        val providers = remember { controller.providers() }
        GlassAlertDialog(onDismissRequest = { picking = false }, title = { Text(stringResource(com.jake.duolauncher.R.string.widget_in_the_island)) }, text = {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(providers.size) { index ->
                    val provider = providers[index]
                    GlassButton(onClick = { picking = false; controller.start(provider) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(controller.label(provider), Modifier.fillMaxWidth())
                    }
                }
            }
        }, confirmButton = { GlassButton(onClick = { picking = false }) { Text(stringResource(com.jake.duolauncher.R.string.cancel)) } })
    }
}

/** Per-app control of the island, like the app list in HyperOS's island settings: switch an app off, or choose how long its notification
 * cards stay (Auto is 6 seconds). Tap the time to change it.
 */
@Composable
private fun IslandLinksSetting(actions: ExtrasActions, apps: List<AppEntry>) {
    val s = actions.store.state
    val open = rememberChecklistOpen("island-link")
    val timeouts = PeekTimeouts.parse(s.peekTimeouts)
    SettingsGroup(header = "Apps", footer = "Every app can use the island until you uncheck it. The time is how long its notification cards stay; tap it to change.") {
        appChecklist(open, apps, emptySet(), "island-link", count = apps.count { IslandLinks.allowed(it.packageName, s.islandMuted) },
            isOn = { IslandLinks.allowed(it.packageName, s.islandMuted) },
            trailing = { app -> Text(PeekTimeouts.label(timeouts, app.packageName), Modifier.clip(Corner.small)
                .clickable { actions.store.cyclePeekTimeout(app.packageName) }.padding(8.dp).testTag("island-timeout-${app.id}"), color = SettingsAccent) }) {
            actions.store.toggleIslandLink(it.packageName)
        }
    }
}

/** Choosing an installed icon pack. A pack replaces the icons it covers; every other app keeps the icon style above. */
@Composable
private fun IconPackSettings(selected: String, onSelect: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val packs = remember { IconPacks.installed(context) }
    if (packs.isEmpty()) {
        SettingsGroup(header = stringResource(com.jake.duolauncher.R.string.icon_pack), footer = stringResource(com.jake.duolauncher.R.string.icon_pack_none_installed)) {
            if (selected.isNotEmpty()) row { ActionRow(stringResource(com.jake.duolauncher.R.string.icon_pack_stop_using), "icon-pack-none") { onSelect("") } }
        }
        return
    }
    val options = listOf(IconPackInfo("", stringResource(com.jake.duolauncher.R.string.icon_pack_none))) + packs
    SettingsGroup(header = stringResource(com.jake.duolauncher.R.string.icon_pack), footer = stringResource(com.jake.duolauncher.R.string.icon_pack_note)) {
        options.forEach { pack -> row {
            CheckRow(pack.label, pack.packageName == selected, "icon-pack-${pack.packageName.ifEmpty { "none" }}") { onSelect(pack.packageName) }
        } }
    }
}

/**
 * The keyboard page: whether Uno Keyboard is on, a live keyboard to see and feel each change, then about a dozen settings in iOS's order.
 * The emoji key, magnified keys and the space-bar cursor are always on, and the keyboard never blurs the app behind it (see
 * fixRetiredSettings).
 */
@Composable
private fun KeyboardPage(store: ExtrasStore, s: ExtrasState) {
    val keyboardContext = androidx.compose.ui.platform.LocalContext.current
    // Android 14+ forbids reading the enabled/selected IME settings for targetSdk > 33, so the
    // enabled list comes from the public InputMethodManager API and the selected state degrades
    // to "unknown" where the secure setting can't be read.
    val enabledKeyboards = runCatching {
        keyboardContext.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            ?.enabledInputMethodList.orEmpty()
    }.getOrDefault(emptyList())
    val keyboardEnabled = enabledKeyboards.any { it.packageName == keyboardContext.packageName }
    val keyboardSelected = runCatching {
        android.provider.Settings.Secure.getString(keyboardContext.contentResolver,
            android.provider.Settings.Secure.DEFAULT_INPUT_METHOD)
    }.getOrNull()?.startsWith(keyboardContext.packageName + "/")
    SettingsGroup(footer = when {
        keyboardSelected == true -> "Uno Keyboard is your keyboard."
        keyboardEnabled && keyboardSelected == null -> "Enabled. Android no longer tells apps which keyboard is selected, so check with the switcher."
        keyboardEnabled -> "Enabled, but another keyboard is selected."
        else -> "Off. Android asks you to turn a keyboard on before it can be used."
    }, tag = "keyboard-status") {
        if (!keyboardEnabled) row { ActionRow(stringResource(com.jake.duolauncher.R.string.turn_on_uno_keyboard), "keyboard-enable") {
            keyboardContext.startActivity(android.content.Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        else if (keyboardSelected != true) row { ActionRow(stringResource(com.jake.duolauncher.R.string.switch_to_uno_keyboard), "keyboard-switch") {
            keyboardContext.getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.showInputMethodPicker() } }
    }
    KeyboardSettingsBody(store, s, keyboardContext)
}

/** The keyboard page below its on/off status: a live keyboard to see and feel each change, then the settings in groups. */
@Composable
private fun KeyboardSettingsBody(store: ExtrasStore, s: ExtrasState, keyboardContext: android.content.Context) {
    fun haptics(strength: Float = s.kbHapticStrength, feel: Int = s.kbHapticFeel) =
        com.jake.duolauncher.keyboard.KeyHaptics.configure(keyboardContext, if (s.haptics) strength else 0f, feel)
    LaunchedEffect(s.haptics, s.kbHapticStrength, s.kbHapticFeel) { haptics() }

    com.jake.duolauncher.keyboard.KeyboardSettingsPreview(com.jake.duolauncher.keyboard.KeyboardLook(s.kbLayouts, s.kbHeight, s.kbNumberRow, true,
        true, false, s.kbOneHanded, true, classic = s.kbStyle == 1), Modifier.fillMaxWidth())

    SettingsGroup(header = stringResource(R.string.kb_group_typing),
        footer = "Double-tap the space bar to type a period. Slide on the space bar to move the cursor.") {
        row { SettingsSwitch("Predictive", s.kbSuggestions, store::setKbSuggestions, "kb-suggestions-switch") }
        row { SettingsSwitch("Autocorrect", s.kbAutocorrect, store::setKbAutocorrect, "kb-autocorrect-switch") }
        row { SettingsSwitch("Slide to Type", s.kbSwipe, store::setKbSwipe, "kb-swipe-switch") }
        row { SettingsSwitch("Auto-Capitalization", s.kbAutoCaps, store::setKbAutoCaps, "kb-auto-caps-switch") }
        row { SettingsSwitch("“.” Shortcut", s.kbDoubleSpace, store::setKbDoubleSpace, "kb-double-space-switch") }
        row { SettingsSwitch("Emoji Suggestions", s.kbEmojiSuggestions, store::setKbEmojiSuggestions, "kb-emoji-suggestions-switch") }
        row { SettingsSwitch("Clipboard History", s.kbClipHistory, store::setKbClipHistory, "kb-clip-history-switch") }
    }
    PersonalWords(store, s.kbLearn)

    SettingsGroup(header = "Look", footer = "Classic is the flat look of Uno 0.97; everything new still works with it.") {
        row { CheckRow(stringResource(R.string.kb_style_glass), s.kbStyle == 0, "kb-style-glass") { store.setKbStyle(0) } }
        row { CheckRow("Classic", s.kbStyle == 1, "kb-style-classic") { store.setKbStyle(1) } }
        row { SettingsSwitch("Number Row", s.kbNumberRow, store::setKbNumberRow, "kb-number-row-switch") }
        row { CustomizationSlider("Height", "${(s.kbHeight * 100).toInt()}%", s.kbHeight, .8f..1.3f, tag = "kb-height-slider") { store.setKbHeight(it) } }
    }

    // Off, or one of three feels at the standard strength; choosing a feel plays it, so the choice is felt at once.
    val feel = if (s.kbHapticStrength <= .01f) -1 else s.kbHapticFeel
    val feelNames = listOf("Off", stringResource(R.string.kb_feel_soft), stringResource(R.string.kb_feel_crisp), stringResource(R.string.kb_feel_firm))
    SettingsGroup(header = "Feedback", footer = "A light tick as each key goes down, firmer for space and Return. It follows Android's touch feedback setting and Gestures › Haptics.") {
        row { SettingsSwitch("Key Sounds", s.kbSound, store::setKbSound, "kb-sound-switch") }
        row { SegmentRow(listOf(-1, 0, 1, 2), feel, { feelNames[it + 1] },
            { "kb-feel-$it" }) { choice ->
            if (choice < 0) { store.setKbHapticStrength(0f); haptics(strength = 0f) }
            else {
                val strength = com.jake.duolauncher.keyboard.HapticProfile.DEFAULT_STRENGTH
                store.setKbHapticStrength(strength); store.setKbHapticFeel(choice); haptics(strength, choice)
                val h = android.os.Handler(android.os.Looper.getMainLooper())
                listOf(com.jake.duolauncher.keyboard.HapticKind.LETTER, com.jake.duolauncher.keyboard.HapticKind.LETTER, com.jake.duolauncher.keyboard.HapticKind.SPACE)
                    .forEachIndexed { n, k -> h.postDelayed({ com.jake.duolauncher.keyboard.KeyHaptics.fire(k) }, 60L + n * 130L) }
            }
        } }
    }

    SettingsGroup(header = "One-Handed") {
        row { CheckRow("Off", s.kbOneHanded == 0, "kb-one-handed-off") { store.setKbOneHanded(0) } }
        row { CheckRow("Left", s.kbOneHanded == 1, "kb-one-handed-left") { store.setKbOneHanded(1) } }
        row { CheckRow("Right", s.kbOneHanded == 2, "kb-one-handed-right") { store.setKbOneHanded(2) } }
    }

    val on = com.jake.duolauncher.keyboard.KeyLayouts.parse(s.kbLayouts).map { it.id }
    SettingsGroup(header = stringResource(R.string.kb_group_layouts), footer = stringResource(R.string.kb_layouts_note)) {
        com.jake.duolauncher.keyboard.KeyLayouts.ALL.forEach { layout -> row {
            ToggleCheckRow(layout.name, layout.id in on, "kb-layout-${layout.id}", detail = layout.rows.first().uppercase()) { store.toggleKbLayout(layout.id) }
        } }
    }
    SettingsGroup(footer = stringResource(R.string.a_glass_styled_keyboard_with_key)) {}
}

/** Learned words in one group: learning on or off, the words it has learned (each can be forgotten, one can be added), and forgetting all. */
@Composable
private fun PersonalWords(store: ExtrasStore, learning: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(com.jake.duolauncher.keyboard.PersonalStore.loaded) }
    LaunchedEffect(Unit) {
        if (!loaded) com.jake.duolauncher.keyboard.PersonalStore.load(context) { android.os.Handler(android.os.Looper.getMainLooper()).post { loaded = true } }
    }
    val words = remember(version, loaded) { com.jake.duolauncher.keyboard.PersonalStore.model.list() }
    var open by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var forgotten by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    SettingsGroup(header = "Learned Words", footer = (if (forgotten) stringResource(R.string.kb_forgotten) + " " else "") + stringResource(R.string.kb_learn_note)) {
        row { SettingsSwitch("Learn New Words", learning, store::setKbLearn, "kb-learn-switch") }
        row {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { open = !open }.testTag("kb-your-words"), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.kb_your_words), Modifier.weight(1f))
                Text("${words.size}", color = muted)
                Icon(if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, tint = muted)
            }
        }
        if (open) {
            if (words.isEmpty()) row { Text(stringResource(R.string.kb_no_words), Modifier.padding(vertical = Space.M.dp), style = UnoType.footnote, color = muted) }
            words.take(200).forEach { w -> row {
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(w, Modifier.weight(1f))
                    IconButton(onClick = { com.jake.duolauncher.keyboard.PersonalStore.model.forget(w); com.jake.duolauncher.keyboard.PersonalStore.saveSoon(); version++ },
                        modifier = Modifier.testTag("kb-forget-$w")) { Icon(Icons.Rounded.RemoveCircle, "Forget $w", tint = Color(0xFFFF3B30)) }
                }
            } }
            if (learning) row {
                Row(Modifier.fillMaxWidth().padding(vertical = Space.S.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S.dp)) {
                    GlassField(draft, { draft = it }, Modifier.weight(1f).testTag("kb-add-word"), minLines = 1, maxLines = 1,
                        placeholder = { Text(stringResource(R.string.kb_add_word)) })
                    Text(stringResource(R.string.kb_add), Modifier.clip(Corner.small).clickable {
                        com.jake.duolauncher.keyboard.PersonalStore.model.add(draft, (System.currentTimeMillis() / 86_400_000L).toInt())
                        com.jake.duolauncher.keyboard.PersonalStore.saveSoon(); draft = ""; version++
                    }.padding(Space.S.dp).testTag("kb-add-word-button"), color = SettingsAccent)
                }
            }
        }
        row { ActionRow("Forget All Learned Words", "kb-forget-everything", destructive = true) {
            com.jake.duolauncher.keyboard.PersonalStore.forgetEverything(); forgotten = true; version++ } }
    }
}

/** The optional record of camera, microphone and screen-recording use: off by default, on this phone only, with no app names. */
@Composable
private fun SensorLogGroup(store: ExtrasStore, s: ExtrasState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var revision by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val entries = remember(s.sensorLog, revision) { if (s.sensorLog) com.jake.duolauncher.island.SensorLog.entries(context).take(12) else emptyList() }
    SettingsGroup(header = "Camera & Microphone",
        footer = "Keeps a list of when the camera, the microphone or screen recording was in use and for how long, so you can look back. Android does not say which app used them, so none is named. It stays on this phone, holds the last 60 uses, and does nothing while this is off.") {
        row { SettingsSwitch("Keep a Camera & Mic Log", s.sensorLog, { store.setSensorLog(it); if (!it) { com.jake.duolauncher.island.SensorLog.clear(context); revision++ } }, "sensor-log-switch") }
        if (s.sensorLog && entries.isEmpty()) row { Text(stringResource(com.jake.duolauncher.R.string.sensor_log_empty), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = Space.M.dp)) }
        entries.forEach { e ->
            row { Column(Modifier.padding(vertical = Space.S.dp)) {
                Text(com.jake.duolauncher.island.SensorLog.label(e.kind), style = MaterialTheme.typography.bodyLarge)
                Text(android.text.format.DateFormat.format("MMM d, h:mm a", e.startMs).toString() + " · " + com.jake.duolauncher.island.SensorLog.length(e.durationMs),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        }
        if (entries.isNotEmpty()) row { ActionRow("Clear Log", "sensor-log-clear", onClick = { com.jake.duolauncher.island.SensorLog.clear(context); revision++ }) }
    }
}
