package com.jake.duolauncher

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

/** The "Control panel & extras" page of the Customize sheet. */
@Composable
internal fun ExtrasSettingsPage(actions: ExtrasActions, apps: List<AppEntry>) {
    val store = actions.store
    val s = store.state
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val note = MaterialTheme.typography.bodySmall

    Text("Uno Keyboard", style = MaterialTheme.typography.titleMedium)
    val keyboardContext = androidx.compose.ui.platform.LocalContext.current
    val keyboardId = "${keyboardContext.packageName}/${keyboardContext.packageName}.keyboard.UnoKeyboardService"
    val enabledKeyboards = android.provider.Settings.Secure.getString(keyboardContext.contentResolver, android.provider.Settings.Secure.ENABLED_INPUT_METHODS).orEmpty()
    val keyboardEnabled = enabledKeyboards.split(':').any { it.startsWith(keyboardContext.packageName + "/") }
    val keyboardSelected = android.provider.Settings.Secure.getString(keyboardContext.contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD)
        ?.startsWith(keyboardContext.packageName + "/") == true
    Text(when { keyboardSelected -> "Uno Keyboard is your keyboard."; keyboardEnabled -> "Enabled, but another keyboard is selected."; else -> "Off. Android asks you to turn a keyboard on before it can be used." },
        style = note, color = muted, modifier = Modifier.testTag("keyboard-status"))
    if (!keyboardEnabled) OutlinedButton(onClick = { keyboardContext.startActivity(android.content.Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("keyboard-enable")) { Text("Turn on Uno Keyboard") }
    else if (!keyboardSelected) OutlinedButton(onClick = { keyboardContext.getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.showInputMethodPicker() },
        Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("keyboard-switch")) { Text("Switch to Uno Keyboard") }
    Text("A glass-styled keyboard with key previews, accents on long-press, caps lock, a numbers page and a field-aware Return key. It is strictly local: nothing you type is stored, learned, logged or sent, and its code contains no network access (a test enforces that). It has no autocorrect, prediction or swipe typing yet, so it types exactly what you press.",
        style = note, color = muted)
    HorizontalDivider(Modifier.padding(vertical = 6.dp))

    Text("Feel and sound", style = MaterialTheme.typography.titleMedium)
    SettingsSwitch("Haptics", s.haptics, store::setHaptics, "haptics-switch")
    SettingsSwitch("Sounds", s.sounds, store::setSounds, "sounds-switch")
    Text("Haptics tick on page changes, sliders, the stack rail and the control panel. Sounds are Android's own touch sounds: no audio files are bundled, they follow your system Touch sounds setting and volume, and they stay silent in silent mode. Sounds are off by default.",
        style = note, color = muted)
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Text("Pulling down on Home", style = MaterialTheme.typography.titleMedium)
    ChoiceRow("Like iOS (recommended)", "From the top edge: notifications on the left, the control panel on the right. From anywhere lower: search",
        s.pullDown == PullDown.SMART, "pull-smart") { store.setPullDown(PullDown.SMART) }
    ChoiceRow("Notifications on the left", "The left side always opens notifications; search is on the island's button",
        s.pullDown == PullDown.NOTIFICATIONS, "pull-notifications") { store.setPullDown(PullDown.NOTIFICATIONS) }
    ChoiceRow("Search on the left", "The left side always opens search, even from the top",
        s.pullDown == PullDown.SEARCH, "pull-search") { store.setPullDown(PullDown.SEARCH) }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Text("Right-hand swipe down", style = MaterialTheme.typography.titleMedium)
    ChoiceRow("Control panel", "The launcher's own panel; needs no accessibility service",
        s.rightSwipe == RightSwipe.PANEL, "swipe-panel") { store.setRightSwipe(RightSwipe.PANEL) }
    ChoiceRow("Android Quick Settings", "Needs the optional shade accessibility service",
        s.rightSwipe == RightSwipe.SYSTEM, "swipe-system") { store.setRightSwipe(RightSwipe.SYSTEM) }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))

    Text("Control panel tiles", style = MaterialTheme.typography.titleMedium)
    s.tileOrder.forEachIndexed { index, tile ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = tile !in s.hiddenTiles, onCheckedChange = { store.setTileHidden(tile, !it) },
                Modifier.testTag("tile-switch-${tile.name}"), colors = IosSwitchColors)
            Text(tile.label, Modifier.weight(1f).padding(start = 12.dp))
            IconButton(onClick = { store.moveTile(tile, -1) }, enabled = index > 0, modifier = Modifier.testTag("tile-up-${tile.name}")) {
                Icon(Icons.Rounded.KeyboardArrowUp, "Move ${tile.label} up") }
            IconButton(onClick = { store.moveTile(tile, 1) }, enabled = index < s.tileOrder.lastIndex, modifier = Modifier.testTag("tile-down-${tile.name}")) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Move ${tile.label} down") }
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))

    Text("Shortcuts in the panel", style = MaterialTheme.typography.titleMedium)
    Text("Up to ${PanelLayout.MAX_SHORTCUTS} apps; choosing a sixth replaces the oldest.", style = note, color = muted)
    AppChecklist(apps, s.shortcuts.toSet(), "shortcut") { store.toggleShortcut(it) }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))

    Text("Focus", style = MaterialTheme.typography.titleMedium)
    SettingsSwitch("Focus on", s.focusOn, { actions.toggleFocus() }, "focus-switch")
    SettingsSwitch("Also set the ringer to vibrate", s.focusVibrate, store::setFocusVibrate, "focus-vibrate-switch")
    Text("Hides the apps you pick from Home and All apps until Focus is off. Nothing is uninstalled, and your layout is not changed; hidden apps leave an empty spot.",
        style = note, color = muted)
    AppChecklist(apps, s.focusHidden, "focus") { store.toggleFocusHidden(it) }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))

    Text("App icons", style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(s.iconStyle == IconStyle.ORIGINAL, { store.setIconStyle(IconStyle.ORIGINAL) }, { Text("Original") },
            Modifier.testTag("icons-original"))
        FilterChip(s.iconStyle == IconStyle.THEMED, { store.setIconStyle(IconStyle.THEMED) }, { Text("Themed") },
            Modifier.testTag("icons-themed"))
    }
    Text("Themed uses each app's own single-colour icon layer where it ships one (Android 13+), in the launcher's colours; other apps are washed to match.",
        style = note, color = muted)
    HorizontalDivider(Modifier.padding(vertical = 6.dp))

    Text("Island timer", style = MaterialTheme.typography.titleMedium)
    val context = androidx.compose.ui.platform.LocalContext.current
    val exact = IslandTools.exactAlarmsAllowed(context)
    Text(if (exact) "Exact alarms are allowed, so the timer ends on time even with Home closed."
        else "The timer ends on time while the launcher is running, and within a minute or so otherwise. Allow exact alarms for to-the-second timing.",
        style = note, color = muted)
    if (!exact) OutlinedButton(onClick = {
        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            android.net.Uri.parse("package:${context.packageName}")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("exact-alarm")) { Text("Allow exact alarms") }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))

    Text("Search suggestions", style = MaterialTheme.typography.titleMedium)
    val resolver = rememberHandoffResolver(s)
    SettingsSwitch("Suggest other apps and the web", s.handoff, store::setHandoff, "handoff-switch")
    Text("When no installed app matches a search, offer to search an app store or the web. Uno only opens the app you choose with your words in it; it never contacts anything itself, and the web search uses that browser's own search engine.",
        style = note, color = muted)
    if (s.handoff) {
        Text("App stores", style = MaterialTheme.typography.titleSmall)
        HandoffLogic.KNOWN_STORES.forEach { storeInfo ->
            val present = resolver.isStoreInstalled(storeInfo)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                .clickable(enabled = present) { store.toggleHandoffStore(storeInfo.id) }.testTag("handoff-store-${storeInfo.id}"),
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(storeInfo.id in s.handoffStores, { store.toggleHandoffStore(storeInfo.id) }, enabled = present)
                Column(Modifier.padding(start = 8.dp)) {
                    Text(storeInfo.label)
                    if (!present) Text("Not installed", style = note, color = muted)
                }
            }
        }
        Text("Web search app", style = MaterialTheme.typography.titleSmall)
        ChoiceRow("Automatic", "Vanadium if installed, otherwise any browser", s.webPackage == null, "web-auto") { store.setWebPackage(null) }
        resolver.installedBrowsers().forEach { (pkg, label) ->
            ChoiceRow(label, pkg, s.webPackage == pkg, "web-$pkg") { store.setWebPackage(pkg) }
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))

    Text("Notifications (optional)", style = MaterialTheme.typography.titleMedium)
    val accessOn = actions.hasNotificationAccess()
    Text(if (accessOn) "Notification access is on." else "Notification access is off. The launcher reads nothing until you turn it on in Android's settings, and you can turn it off there at any time.",
        style = note, color = muted)
    OutlinedButton(onClick = actions.openNotificationAccess, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("notification-access")) {
        Text(if (accessOn) "Manage notification access" else "Open notification access settings")
    }
    SettingsSwitch("Badges on app icons", s.badges, store::setBadges, "badges-switch")
    SettingsSwitch("Track title and artwork in media", s.mediaDetails, store::setMediaDetails, "media-details-switch")
    SettingsSwitch("Show new notifications in the island", s.notificationPeek, store::setNotificationPeek, "peek-switch")
    Text("Everything stays on this device. Badges count unread notifications per app; the island shows the app's name only, never the message.",
        style = note, color = muted)
    HorizontalDivider(Modifier.padding(vertical = 6.dp))

    Text("Contacts in search (optional)", style = MaterialTheme.typography.titleMedium)
    SettingsSwitch("Search contacts from All apps", s.contactSearch, { on ->
        if (on) { store.setContactSearch(true); if (!actions.hasContactsPermission()) actions.requestContacts() }
        else store.setContactSearch(false)
    }, "contact-search-switch")
    Text("Asks Android's contacts permission. Names are matched on this device and shown below the app results; nothing is stored or sent.",
        style = note, color = muted)
}

@Composable
private fun ChoiceRow(title: String, subtitle: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).testTag(tag),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick)
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
    OutlinedButton(onClick = { open = !open }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("$tag-choose")) {
        Text(if (open) "Done" else "Choose apps (${selected.size} selected)")
    }
    if (open) apps.forEach { app ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable { onToggle(app.id) }
            .testTag("$tag-app-${app.id}"), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(app.id in selected, { onToggle(app.id) })
            Image(app.icon.asImageBitmap(), null, Modifier.size(32.dp).clip(Corner.icon))
            Text(app.label, Modifier.padding(start = 12.dp), maxLines = 1)
        }
    }
}
