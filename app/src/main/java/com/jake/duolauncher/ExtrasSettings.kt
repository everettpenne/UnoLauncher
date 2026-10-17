package com.jake.duolauncher

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.island.*
import com.jake.duolauncher.search.*
import com.jake.duolauncher.panel.*
import com.jake.duolauncher.appearance.*

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Which part of the extras a Customize page shows (each is its own page, reached from the overview). */
internal enum class ExtrasSection { KEYBOARD, GESTURES, PANEL, ICONS, ISLAND, SEARCH, PRIVACY }

/** One of the extras pages of the Customize sheet. */
@Composable
internal fun ExtrasSettingsPage(actions: ExtrasActions, apps: List<AppEntry>, section: ExtrasSection) {
    val store = actions.store
    val s = store.state
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val note = MaterialTheme.typography.bodySmall

    if (section == ExtrasSection.KEYBOARD) {
    Text(stringResource(com.jake.duolauncher.R.string.uno_keyboard), style = MaterialTheme.typography.titleMedium)
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
    Text(when {
        keyboardSelected == true -> "Uno Keyboard is your keyboard."
        keyboardEnabled && keyboardSelected == null -> "Enabled. Android no longer tells apps which keyboard is selected, so check with the switcher below."
        keyboardEnabled -> "Enabled, but another keyboard is selected."
        else -> "Off. Android asks you to turn a keyboard on before it can be used."
    },
        style = note, color = muted, modifier = Modifier.testTag("keyboard-status"))
    if (!keyboardEnabled) GlassButton(onClick = { keyboardContext.startActivity(android.content.Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("keyboard-enable")) { Text(stringResource(com.jake.duolauncher.R.string.turn_on_uno_keyboard)) }
    else if (keyboardSelected != true) GlassButton(onClick = { keyboardContext.getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.showInputMethodPicker() },
        Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("keyboard-switch")) { Text(stringResource(com.jake.duolauncher.R.string.switch_to_uno_keyboard)) }
    Text(stringResource(com.jake.duolauncher.R.string.suggestions_use_a_fixed_word_list), style = note, color = muted)
    SettingsSwitch("Suggestions", s.kbSuggestions, store::setKbSuggestions, "kb-suggestions-switch")
    SettingsSwitch("Autocorrect", s.kbAutocorrect, store::setKbAutocorrect, "kb-autocorrect-switch")
    SettingsSwitch("Number row", s.kbNumberRow, store::setKbNumberRow, "kb-number-row-switch")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(com.jake.duolauncher.R.string.key_haptics), Modifier.weight(1f))
        Text(if (s.kbHapticStrength <= .01f) "Off" else "${(s.kbHapticStrength * 100).toInt()}%", color = muted)
    }
    LiquidSliderControl(s.kbHapticStrength, 0f..1f, { value ->
        store.setKbHapticStrength(value)
        com.jake.duolauncher.keyboard.KeyHaptics.configure(keyboardContext, if (s.haptics) value else 0f)
    }, LocalPageGlass.current, Modifier.testTag("kb-haptic-slider"))
    GlassButton(onClick = {
        // A short run of what typing feels like, so you can set the strength without opening a text field.
        com.jake.duolauncher.keyboard.KeyHaptics.configure(keyboardContext, if (s.haptics) s.kbHapticStrength else 0f)
        val kinds = listOf(com.jake.duolauncher.keyboard.HapticKind.LETTER, com.jake.duolauncher.keyboard.HapticKind.LETTER,
            com.jake.duolauncher.keyboard.HapticKind.SPACE, com.jake.duolauncher.keyboard.HapticKind.DELETE, com.jake.duolauncher.keyboard.HapticKind.RETURN)
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        kinds.forEachIndexed { i, k -> h.postDelayed({ com.jake.duolauncher.keyboard.KeyHaptics.fire(k) }, i * 140L) }
    }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("kb-haptic-try")) { Text(stringResource(com.jake.duolauncher.R.string.feel_it)) }
    Text(stringResource(com.jake.duolauncher.R.string.a_light_tick_on_touch_down),
        style = note, color = muted)
    SettingsSwitch("Slide on space bar to move the cursor", s.kbSpaceCursor, store::setKbSpaceCursor, "kb-space-cursor-switch")
    Text(stringResource(com.jake.duolauncher.R.string.one_handed_keyboard), style = MaterialTheme.typography.bodyLarge)
    ChoiceRow("Off", "The keys use the full width", s.kbOneHanded == 0, "kb-one-handed-off") { store.setKbOneHanded(0) }
    ChoiceRow("Left", "The keys sit against the left edge, a little narrower", s.kbOneHanded == 1, "kb-one-handed-left") { store.setKbOneHanded(1) }
    ChoiceRow("Right", "The keys sit against the right edge, a little narrower", s.kbOneHanded == 2, "kb-one-handed-right") { store.setKbOneHanded(2) }
    Text(stringResource(com.jake.duolauncher.R.string.a_glass_styled_keyboard_with_key),
        style = note, color = muted)
    }

    if (section == ExtrasSection.GESTURES) {
    Text(stringResource(com.jake.duolauncher.R.string.feel_and_sound), style = MaterialTheme.typography.titleMedium)
    SettingsSwitch("Haptics", s.haptics, store::setHaptics, "haptics-switch")
    SettingsSwitch("Sounds", s.sounds, store::setSounds, "sounds-switch")
    Text(stringResource(com.jake.duolauncher.R.string.haptics_tick_on_page_changes_sliders),
        style = note, color = muted)
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Text(stringResource(com.jake.duolauncher.R.string.pulling_down_on_home), style = MaterialTheme.typography.titleMedium)
    ChoiceRow("Like iOS (recommended)", "From the top edge: notifications on the left, the control panel on the right. From anywhere lower: search",
        s.pullDown == PullDown.SMART, "pull-smart") { store.setPullDown(PullDown.SMART) }
    ChoiceRow("Notifications on the left", "The left side always opens notifications; search is on the island's button",
        s.pullDown == PullDown.NOTIFICATIONS, "pull-notifications") { store.setPullDown(PullDown.NOTIFICATIONS) }
    ChoiceRow("Search on the left", "The left side always opens search, even from the top",
        s.pullDown == PullDown.SEARCH, "pull-search") { store.setPullDown(PullDown.SEARCH) }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Text(stringResource(com.jake.duolauncher.R.string.right_hand_swipe_down), style = MaterialTheme.typography.titleMedium)
    ChoiceRow("Control panel", "The launcher's own panel; needs no accessibility service",
        s.rightSwipe == RightSwipe.PANEL, "swipe-panel") { store.setRightSwipe(RightSwipe.PANEL) }
    ChoiceRow("Android mode", "Opens Android's own Quick Settings instead; needs the optional shade accessibility service",
        s.rightSwipe == RightSwipe.SYSTEM, "swipe-system") { store.setRightSwipe(RightSwipe.SYSTEM) }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    SettingsSwitch("Double-tap to lock", s.doubleTapLock, store::setDoubleTapLock, "double-tap-lock-switch")
    Text(stringResource(com.jake.duolauncher.R.string.double_tap_lock_note), style = note, color = muted)
    }

    if (section == ExtrasSection.PANEL) {
    Text(stringResource(com.jake.duolauncher.R.string.control_panel_tiles), style = MaterialTheme.typography.titleMedium)
    s.tileOrder.forEachIndexed { index, tile ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            LiquidSwitchControl(tile !in s.hiddenTiles, { store.setTileHidden(tile, !it) }, LocalPageGlass.current,
                Modifier.testTag("tile-switch-${tile.name}"))
            Text(tile.label, Modifier.weight(1f).padding(start = 12.dp))
            IconButton(onClick = { store.moveTile(tile, -1) }, enabled = index > 0, modifier = Modifier.testTag("tile-up-${tile.name}")) {
                Icon(Icons.Rounded.KeyboardArrowUp, "Move ${tile.label} up") }
            IconButton(onClick = { store.moveTile(tile, 1) }, enabled = index < s.tileOrder.lastIndex, modifier = Modifier.testTag("tile-down-${tile.name}")) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Move ${tile.label} down") }
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))

    Text(stringResource(com.jake.duolauncher.R.string.shortcuts_in_the_panel), style = MaterialTheme.typography.titleMedium)
    Text("Up to ${PanelLayout.MAX_SHORTCUTS} apps; choosing a sixth replaces the oldest.", style = note, color = muted)
    AppChecklist(apps, s.shortcuts.toSet(), "shortcut") { store.toggleShortcut(it) }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))

    Text(stringResource(com.jake.duolauncher.R.string.focus), style = MaterialTheme.typography.titleMedium)
    SettingsSwitch("Focus on", s.focusOn, { actions.toggleFocus() }, "focus-switch")
    SettingsSwitch("Also set the ringer to vibrate", s.focusVibrate, store::setFocusVibrate, "focus-vibrate-switch")
    Text(stringResource(com.jake.duolauncher.R.string.hides_the_apps_you_pick_from),
        style = note, color = muted)
    AppChecklist(apps, s.focusHidden, "focus") { store.toggleFocusHidden(it) }
    }

    if (section == ExtrasSection.ICONS) {
    Text(stringResource(com.jake.duolauncher.R.string.icon_style), style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassChip(s.iconStyle == IconStyle.ORIGINAL, { store.setIconStyle(IconStyle.ORIGINAL) }, { Text(stringResource(com.jake.duolauncher.R.string.original)) },
            Modifier.testTag("icons-original"))
        GlassChip(s.iconStyle == IconStyle.THEMED, { store.setIconStyle(IconStyle.THEMED) }, { Text(stringResource(com.jake.duolauncher.R.string.themed)) },
            Modifier.testTag("icons-themed"))
        GlassChip(s.iconStyle == IconStyle.TINTED, { store.setIconStyle(IconStyle.TINTED) }, { Text(stringResource(com.jake.duolauncher.R.string.tinted)) },
            Modifier.testTag("icons-tinted"))
    }
    Text(stringResource(com.jake.duolauncher.R.string.tinted_takes_its_colour_from_your),
        style = note, color = muted)
    Text(stringResource(com.jake.duolauncher.R.string.icon_lighting), style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassChip(s.iconLighting, { store.setIconLighting(true) }, { Text(stringResource(com.jake.duolauncher.R.string.icon_lighting_on)) },
            Modifier.testTag("icons-lit"))
        GlassChip(!s.iconLighting, { store.setIconLighting(false) }, { Text(stringResource(com.jake.duolauncher.R.string.icon_lighting_off)) },
            Modifier.testTag("icons-flat"))
    }
    Text(stringResource(com.jake.duolauncher.R.string.icon_lighting_note), style = note, color = muted)
    Text(stringResource(com.jake.duolauncher.R.string.themed_uses_each_app_s_own),
        style = note, color = muted)
    IconPackSettings(s.iconPack, store::setIconPack)
    }

    if (section == ExtrasSection.ISLAND) {
    Text(stringResource(com.jake.duolauncher.R.string.island_timer), style = MaterialTheme.typography.titleMedium)
    val context = androidx.compose.ui.platform.LocalContext.current
    val exact = IslandTools.exactAlarmsAllowed(context)
    Text(if (exact) "Exact alarms are allowed, so the timer ends on time even with Home closed."
        else "The timer ends on time while the launcher is running, and within a minute or so otherwise. Allow exact alarms for to-the-second timing.",
        style = note, color = muted)
    if (!exact) GlassButton(onClick = {
        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            android.net.Uri.parse("package:${context.packageName}")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("exact-alarm")) { Text(stringResource(com.jake.duolauncher.R.string.allow_exact_alarms)) }
    }

    if (section == ExtrasSection.SEARCH) {
    Text(stringResource(com.jake.duolauncher.R.string.search_suggestions), style = MaterialTheme.typography.titleMedium)
    val resolver = rememberHandoffResolver(s)
    SettingsSwitch("Suggest other apps and the web", s.handoff, store::setHandoff, "handoff-switch")
    Text(stringResource(com.jake.duolauncher.R.string.when_no_installed_app_matches_a),
        style = note, color = muted)
    if (s.handoff) {
        Text(stringResource(com.jake.duolauncher.R.string.app_stores), style = MaterialTheme.typography.titleSmall)
        HandoffLogic.KNOWN_STORES.forEach { storeInfo ->
            val present = resolver.isStoreInstalled(storeInfo)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                .toggleable(value = storeInfo.id in s.handoffStores, enabled = present, role = androidx.compose.ui.semantics.Role.Checkbox) {
                    store.toggleHandoffStore(storeInfo.id) }.testTag("handoff-store-${storeInfo.id}"),
                verticalAlignment = Alignment.CenterVertically) {
                GlassCheckbox(storeInfo.id in s.handoffStores, null, enabled = present)
                Column(Modifier.padding(start = 8.dp)) {
                    Text(storeInfo.label)
                    if (!present) Text(stringResource(com.jake.duolauncher.R.string.not_installed), style = note, color = muted)
                }
            }
        }
        Text(stringResource(com.jake.duolauncher.R.string.web_search_app), style = MaterialTheme.typography.titleSmall)
        ChoiceRow("Automatic", "Vanadium if installed, otherwise any browser", s.webPackage == null, "web-auto") { store.setWebPackage(null) }
        resolver.installedBrowsers().forEach { (pkg, label) ->
            ChoiceRow(label, pkg, s.webPackage == pkg, "web-$pkg") { store.setWebPackage(pkg) }
        }
    }
    }

    if (section == ExtrasSection.PRIVACY) {
    Text(stringResource(com.jake.duolauncher.R.string.notifications_optional), style = MaterialTheme.typography.titleMedium)
    val accessOn = actions.hasNotificationAccess()
    Text(if (accessOn) "Notification access is on." else "Notification access is off. The launcher reads nothing until you turn it on in Android's settings, and you can turn it off there at any time.",
        style = note, color = muted)
    GlassButton(onClick = actions.openNotificationAccess, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("notification-access")) {
        Text(if (accessOn) "Manage notification access" else "Open notification access settings")
    }
    SettingsSwitch("Badges on app icons", s.badges, store::setBadges, "badges-switch")
    if (s.badges) {
        ChoiceRow("Count", "The number of unread notifications", !s.badgeDots, "badge-style-count") { store.setBadgeDots(false) }
        ChoiceRow("Dot", "A plain dot, no number", s.badgeDots, "badge-style-dot") { store.setBadgeDots(true) }
        Text(stringResource(com.jake.duolauncher.R.string.badge_mute_note), style = note, color = muted)
    }
    SettingsSwitch("Track title and artwork in media", s.mediaDetails, store::setMediaDetails, "media-details-switch")
    }
    if (section == ExtrasSection.ISLAND) {
    SettingsSwitch("Camera and microphone indicators", s.privacyIndicators, store::setPrivacyIndicators, "privacy-indicators-switch")
    Text(stringResource(com.jake.duolauncher.R.string.the_island_shows_a_green_camera),
        style = note, color = muted)
    SettingsSwitch("VPN and USB data alerts", s.securityAlerts, store::setSecurityAlerts, "security-alerts-switch")
    Text(stringResource(com.jake.duolauncher.R.string.the_island_flashes_when_a_vpn),
        style = note, color = muted)
    Text(stringResource(com.jake.duolauncher.R.string.paused_music_and_podcasts), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(com.jake.duolauncher.R.string.the_wide_pill_is_for_sound),
        style = note, color = muted)
    IslandPlayback.RESUME_CHOICES_MIN.forEach { minutes ->
        ChoiceRow(if (minutes == 1) "1 minute" else "$minutes minutes", if (minutes == 2) "The default" else "",
            s.mediaResumeMinutes == minutes, "media-resume-$minutes") { store.setMediaResumeMinutes(minutes) }
    }
    SettingsSwitch("Permanent wide island", s.islandWide, store::setIslandWide, "island-wide-switch")
    Text(stringResource(com.jake.duolauncher.R.string.over_other_apps_the_island_normally),
        style = note, color = muted)
    SettingsSwitch("App shortcuts in the island", s.islandShortcuts, store::setIslandShortcuts, "island-shortcuts-switch")
    Text(stringResource(com.jake.duolauncher.R.string.the_open_island_shows_the_apps),
        style = note, color = muted)
    SettingsSwitch("Headphone and Bluetooth audio alerts", s.audioAlerts, store::setAudioAlerts, "audio-alerts-switch")
    Text(stringResource(com.jake.duolauncher.R.string.the_island_flashes_the_name_of),
        style = note, color = muted)
    SettingsSwitch("Bluetooth device alerts", s.bluetoothAlerts, { on ->
        if (on) { store.setBluetoothAlerts(true); if (!actions.hasBluetoothPermission()) actions.requestBluetooth() }
        else store.setBluetoothAlerts(false)
    }, "bluetooth-alerts-switch")
    Text(stringResource(com.jake.duolauncher.R.string.bluetooth_alerts_note), style = note, color = muted)
    SettingsSwitch("Live Updates in the island", s.liveUpdates, store::setLiveUpdates, "live-updates-switch")
    SettingsSwitch("Downloads and other progress", s.progressTasks, store::setProgressTasks, "progress-tasks-switch")
    Text(stringResource(com.jake.duolauncher.R.string.a_download_upload_or_install_with),
        style = note, color = muted)
    SettingsSwitch("Navigation in the island", s.navigationTasks, store::setNavigationTasks, "navigation-tasks-switch")
    if (s.navigationTasks) {
        SettingsSwitch("Drop down for each turn", s.navigationCues, store::setNavigationCues, "navigation-cues-switch")
        Text(stringResource(com.jake.duolauncher.R.string.navigation_cues_note), style = note, color = muted)
    }
    Text(stringResource(com.jake.duolauncher.R.string.while_a_maps_app_shows_turn),
        style = note, color = muted)
    Text(stringResource(com.jake.duolauncher.R.string.android_16_apps_can_post_a),
        style = note, color = muted)
    IslandWidgetSetting(actions.islandWidget, note, muted)
    IslandLinksSetting(actions, apps, note, muted)
    SettingsSwitch("Show calls in the island", s.callDetails, store::setCallDetails, "call-details-switch")
    Text(stringResource(com.jake.duolauncher.R.string.while_a_call_is_active_the),
        style = note, color = muted)
    SettingsSwitch("Show new notifications in the island", s.notificationPeek, store::setNotificationPeek, "peek-switch")
    Text(stringResource(com.jake.duolauncher.R.string.how_much_a_new_notification_shows), style = note, color = muted)
    if (s.notificationPeek) {
        SettingsSwitch("Action buttons on the card", s.peekActions, store::setPeekActions, "peek-actions-switch")
        Text(stringResource(com.jake.duolauncher.R.string.peek_actions_note), style = note, color = muted)
    }
    ChoiceRow("App name only", "A flash of the app's icon and name, never what it says", s.peekLevel == 0, "peek-level-name") { store.setPeekLevel(0) }
    ChoiceRow("Name and title", "A card with the sender or subject; tap it to open the notification", s.peekLevel == 1, "peek-level-title") { store.setPeekLevel(1) }
    ChoiceRow("Name, title and text", "The card also shows the first lines of the text. Uno reads that text to show it, and keeps nothing",
        s.peekLevel == 2, "peek-level-text") { store.setPeekLevel(2) }
    GlassField(s.peekBlocked, { store.setPeekBlocked(it) }, Modifier.fillMaxWidth().testTag("peek-blocked"), minLines = 1, maxLines = 3,
        label = { Text(stringResource(com.jake.duolauncher.R.string.hide_notifications_containing_these_words)) }, supportingText = { Text(stringResource(com.jake.duolauncher.R.string.separate_with_commas_a_match_shows)) })
    Text(stringResource(com.jake.duolauncher.R.string.everything_stays_on_this_device_badges),
        style = note, color = muted)
    }

    if (section == ExtrasSection.ISLAND) {
    // Developer HUD: only offered by debuggable builds, so a release build has neither the switch nor the overlay.
    val hudContext = androidx.compose.ui.platform.LocalContext.current
    if (hudContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
        val hudPrefs = remember { hudContext.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE) }
        var hudOn by remember { mutableStateOf(hudPrefs.getBoolean("debugHud", false)) }
        SettingsSwitch("Developer HUD (debug build)", hudOn, { hudOn = it; hudPrefs.edit().putBoolean("debugHud", it).apply() }, "debug-hud-switch")
        Text(stringResource(com.jake.duolauncher.R.string.shows_frame_times_jank_the_island),
            style = note, color = muted)
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
    }
    }

    if (section == ExtrasSection.PRIVACY) {
    PermissionLedgerSection(actions)
    }

    if (section == ExtrasSection.SEARCH) {
    Text(stringResource(com.jake.duolauncher.R.string.contacts_in_search_optional), style = MaterialTheme.typography.titleMedium)
    SettingsSwitch("Search contacts from All apps", s.contactSearch, { on ->
        if (on) { store.setContactSearch(true); if (!actions.hasContactsPermission()) actions.requestContacts() }
        else store.setContactSearch(false)
    }, "contact-search-switch")
    Text(stringResource(com.jake.duolauncher.R.string.asks_android_s_contacts_permission_names),
        style = note, color = muted)
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
        .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick).testTag(tag),
        verticalAlignment = Alignment.CenterVertically) {
        GlassRadio(selected, null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A collapsed list of every app with a checkbox; expands on demand so a long list isn't drawn until wanted. */
@Composable
private fun AppChecklist(apps: List<AppEntry>, selected: Set<String>, tag: String, onToggle: (String) -> Unit) {
    var open by rememberSaveable(tag) { mutableStateOf(false) }
    GlassButton(onClick = { open = !open }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("$tag-choose")) {
        Text(if (open) "Done" else "Choose apps (${selected.size} selected)")
    }
    if (open) apps.forEach { app ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
            .toggleable(value = app.id in selected, role = androidx.compose.ui.semantics.Role.Checkbox) { onToggle(app.id) }
            .testTag("$tag-app-${app.id}"), verticalAlignment = Alignment.CenterVertically) {
            GlassCheckbox(app.id in selected, null)
            Image(app.image, null, Modifier.size(32.dp).clip(Corner.icon))
            Text(app.label, Modifier.padding(start = 12.dp), maxLines = 1)
        }
    }
}

/** Choose, change or remove the one widget the open island shows. */
@Composable
private fun IslandWidgetSetting(controller: IslandWidgetController?, note: androidx.compose.ui.text.TextStyle, muted: androidx.compose.ui.graphics.Color) {
    if (controller == null) return
    var picking by remember { mutableStateOf(false) }
    val id = IslandWidgetState.id
    Text(stringResource(com.jake.duolauncher.R.string.widget_in_the_island), style = MaterialTheme.typography.titleMedium)
    Text(if (id >= 0) "Showing: ${controller.currentLabel() ?: "an unavailable widget"}" else "None chosen",
        modifier = Modifier.testTag("island-widget-current"))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassButton(onClick = { picking = true }, Modifier.weight(1f).heightIn(min = 48.dp).testTag("island-widget-choose")) {
            Text(if (id >= 0) "Change" else "Choose widget")
        }
        if (id >= 0) GlassButton(onClick = controller::remove, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("island-widget-remove")) { Text(stringResource(com.jake.duolauncher.R.string.remove)) }
    }
    controller.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = note) }
    Text(stringResource(com.jake.duolauncher.R.string.the_open_island_shows_this_widget),
        style = note, color = muted)
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
private fun IslandLinksSetting(actions: ExtrasActions, apps: List<AppEntry>, note: androidx.compose.ui.text.TextStyle, muted: androidx.compose.ui.graphics.Color) {
    val s = actions.store.state
    var open by rememberSaveable { mutableStateOf(false) }
    val timeouts = PeekTimeouts.parse(s.peekTimeouts)
    Text(stringResource(com.jake.duolauncher.R.string.apps_that_can_use_the_island), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(com.jake.duolauncher.R.string.every_app_is_linked_until_you),
        style = note, color = muted)
    GlassButton(onClick = { open = !open }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("island-link-choose")) {
        Text(if (open) "Done" else "Choose apps (${apps.count { IslandLinks.allowed(it.packageName, s.islandMuted) }} linked)")
    }
    if (open) apps.forEach { app ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
            .toggleable(value = IslandLinks.allowed(app.packageName, s.islandMuted), role = androidx.compose.ui.semantics.Role.Checkbox) { actions.store.toggleIslandLink(app.packageName) }
            .testTag("island-link-app-${app.id}"), verticalAlignment = Alignment.CenterVertically) {
            GlassCheckbox(IslandLinks.allowed(app.packageName, s.islandMuted), null)
            Image(app.image, null, Modifier.size(32.dp).clip(Corner.icon))
            Text(app.label, Modifier.padding(start = 12.dp).weight(1f), maxLines = 1)
            GlassButton(onClick = { actions.store.cyclePeekTimeout(app.packageName) }, modifier = Modifier.testTag("island-timeout-${app.id}")) {
                Text(PeekTimeouts.label(timeouts, app.packageName))
            }
        }
    }
}

/** Choosing an installed icon pack. A pack replaces the icons it covers; every other app keeps the icon style above. */
@Composable
private fun IconPackSettings(selected: String, onSelect: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val packs = remember { IconPacks.installed(context) }
    Text(stringResource(com.jake.duolauncher.R.string.icon_pack), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
    if (packs.isEmpty()) {
        Text(stringResource(com.jake.duolauncher.R.string.icon_pack_none_installed), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (selected.isNotEmpty()) GlassButton(onClick = { onSelect("") }, modifier = Modifier.testTag("icon-pack-none")) {
            Text(stringResource(com.jake.duolauncher.R.string.icon_pack_stop_using)) }
        return
    }
    val options = listOf(IconPackInfo("", stringResource(com.jake.duolauncher.R.string.icon_pack_none))) + packs
    options.forEach { pack ->
        ChoiceRow(pack.label, if (pack.packageName.isEmpty()) stringResource(com.jake.duolauncher.R.string.icon_pack_none_detail) else pack.packageName,
            selected = pack.packageName == selected, tag = "icon-pack-${pack.packageName.ifEmpty { "none" }}") { onSelect(pack.packageName) }
    }
    Text(stringResource(com.jake.duolauncher.R.string.icon_pack_note), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
