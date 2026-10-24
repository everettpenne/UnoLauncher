package com.jake.duolauncher

import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.island.*
import com.jake.duolauncher.feed.*
import com.jake.duolauncher.panel.*
import com.jake.duolauncher.appearance.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal enum class CustomizationPage { OVERVIEW, APPEARANCE, HOME, GESTURES, SEARCH, ISLAND, KEYBOARD, PRIVACY, FEED, BACKUP, DEBUG, STATUS_GRID, ISLAND_STYLE }

/** Where the back arrow goes from a page: a sub-page goes back to its parent, every other page to the overview. */
internal fun CustomizationPage.parent(): CustomizationPage = when (this) {
    CustomizationPage.STATUS_GRID, CustomizationPage.ISLAND_STYLE -> CustomizationPage.ISLAND
    else -> CustomizationPage.OVERVIEW
}

@Composable
internal fun CustomizationSheet(state: LauncherState, initiallyWide: Boolean, model: LauncherModel,
    isDefaultHome: Boolean, page: CustomizationPage, onPage: (CustomizationPage) -> Unit,
    onMakeDefault: () -> Unit, onClose: () -> Unit, onEditPins: () -> Unit, onWidget: (Int) -> Unit,
    onAddWidget: (Int) -> Unit, onRemoveWidget: (Int) -> Unit, onWallpaperPreview: () -> Unit,
    onExportLayout: () -> Unit, onImportLayout: () -> Unit,
    appearance: AppearanceState, onAppearanceMode: (AppearanceMode) -> Unit,
    onAppearanceManual: (String, Double, Double) -> Unit, onAppearanceDeviceLocation: () -> Unit,
    onAppearanceCancelLocation: () -> Unit = {},
    onAppearanceClear: () -> Unit, backgrounds: LauncherBackgroundController, homePage: Int = 0,
    onShadeSetup: () -> Unit = {},
    extras: ExtrasActions? = null,
    feed: FeedState = FeedState(),
    onFeedRefresh: () -> Unit = {},
    onAddFeed: (String, (FeedAddResult) -> Unit) -> Unit = { _, _ -> },
    onRemoveFeed: (String) -> Unit = {},
    onSourceEnabled: (String, Boolean) -> Unit = { _, _ -> },
    onImportOpml: (String) -> FeedOpmlResult = { _ -> FeedOpmlResult(0, 0) },
    onKeepPerFeed: (Int) -> Unit = {},
    glassBackdrop: com.kyant.backdrop.Backdrop? = null,
    glassTint: Color = Glass.copy(alpha = .62f),
    settings: GlassSettings = GlassSettings.Default,
    onGlass: (GlassChoice) -> Unit = {},
    onWallpaperColor: (Boolean) -> Unit = {},
    onWallpaperDepth: (Boolean) -> Unit = {},
    onHomeStyle: (HomeStyle) -> Unit = {},
    onTiltHighlight: (Boolean) -> Unit = {},
    onIsland: (Boolean) -> Unit = {},
    islandEverywhere: Boolean = false,
    onIslandEverywhere: (Boolean) -> Unit = {},
    updates: UpdateState = UpdateState(),
    onCheckUpdates: () -> Unit = {},
    onInstallRelease: (String) -> Unit = {},
    onAutoUpdate: (Boolean) -> Unit = {},
) {
    var wide by rememberSaveable { mutableStateOf(initiallyWide) }
    val title = SettingsSearch.pageName(page)
    val bodyScroll = rememberScrollState()
    val apps = model.state.collectAsState().value.apps
    LaunchedEffect(page) { bodyScroll.scrollTo(0) }
    // A photo or a built-in scene waiting to be applied opens the full-screen editor, whichever page is showing.
    SideEffect { backgrounds.dark = appearance.dark }
    WallpaperEditor(backgrounds, state, appearance.dark)
    Column(Modifier.fillMaxWidth().fillMaxHeight(.92f)
        .then(if (glassBackdrop != null) Modifier.liquidGlass(glassBackdrop, Corner.xlarge,
            glassTint, kind = GlassKind.REGULAR, settings = settings) else Modifier)
        .padding(horizontal = SHEET_INSET).padding(bottom = 12.dp)) {
        // iOS's navigation bar: the way back on the left, the page's name in the middle, and Done on the right.
        Box(Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            if (page != CustomizationPage.OVERVIEW) Row(Modifier.align(Alignment.CenterStart).clip(Corner.pill)
                .clickable(role = androidx.compose.ui.semantics.Role.Button) { onPage(page.parent()) }
                .padding(end = 8.dp, top = 8.dp, bottom = 8.dp).testTag("customization-back")
                .semantics(mergeDescendants = true) { contentDescription = "Back" }, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, Modifier.size(30.dp), tint = SettingsAccent)
                Text(SettingsSearch.pageName(page.parent()).let { if (page.parent() == CustomizationPage.OVERVIEW) "Settings" else it },
                    color = SettingsAccent, maxLines = 1)
            }
            Text(title, Modifier.align(Alignment.Center).padding(horizontal = 96.dp), style = UnoType.headline, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(stringResource(com.jake.duolauncher.R.string.wallpaper_done), Modifier.align(Alignment.CenterEnd).clip(Corner.pill).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClose)
                .padding(8.dp).semantics { contentDescription = "Close settings" }, color = SettingsAccent,
                style = UnoType.body.copy(fontWeight = FontWeight.SemiBold))
        }
        // The island page keeps a live copy of the island above its settings, so a change can be watched as it is made.
        val islandPreview = remember { com.jake.duolauncher.island.IslandPreviewController() }
        if ((page == CustomizationPage.ISLAND || page == CustomizationPage.ISLAND_STYLE) && appearance.island && extras != null) {
            val es = extras.store.state
            com.jake.duolauncher.island.IslandSettingsPreview(islandPreview, com.jake.duolauncher.island.PreviewSettings(appearance.islandScale, es.islandWideMode,
                es.liveCompactLevel, es.peekLevel, es.peekActions, es.islandShortcuts, es.privacyIndicators, es.liveUpdates), Modifier.padding(bottom = 8.dp))
        }
        CompositionLocalProvider(com.jake.duolauncher.island.LocalIslandPreview provides islandPreview) {
        Column(Modifier.weight(1f).verticalScroll(bodyScroll).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(Space.L.dp)) {
            when (page) {
                CustomizationPage.OVERVIEW -> OverviewPage(state, model, isDefaultHome, onMakeDefault, onPage, onClose)
                CustomizationPage.APPEARANCE -> {
                    WallpaperGroups(backgrounds, state, appearance.dark, onWallpaperPreview)
                    AppearanceSettings(appearance, onAppearanceMode, onAppearanceManual, onAppearanceDeviceLocation, onAppearanceClear, onAppearanceCancelLocation)
                    GlassGroups(appearance, onGlass, onTiltHighlight, onWallpaperColor, onWallpaperDepth)
                    if (extras != null) ExtrasSettingsPage(extras, apps, ExtrasSection.ICONS)
                }
                CustomizationPage.HOME -> HomeLayoutSettings(state, wide, { wide = it }, model, homePage,
                    onEditPins, onWidget, onAddWidget, onRemoveWidget, appearance.homeStyle, onHomeStyle)
                CustomizationPage.GESTURES -> {
                    if (extras != null) ExtrasSettingsPage(extras, apps, ExtrasSection.GESTURES)
                    // The status rail belongs to the side dock; the iPhone layout shows the system status bar instead.
                    if (appearance.homeStyle == HomeStyle.SIDE || wide) SettingsGroup(footer = "Battery, time and signal in a glass column above the side dock.") {
                        row { SettingsSwitch("Status at Upper Right", state.verticalStatus, model::setVerticalStatus, "status-switch") }
                    }
                    SettingsGroup(footer = "Swipe sideways on Home to change pages, and up anywhere to open these settings.") {}
                }
                CustomizationPage.SEARCH -> {
                    SettingsGroup(footer = "The search pill opens Google's own search page in your browser. All Apps always searches your apps on the phone.") {
                        row { SettingsSwitch("Search Button Opens Google", state.googleSearch, model::setGoogleSearch, "google-search-switch") }
                    }
                    if (extras != null) ExtrasSettingsPage(extras, apps, ExtrasSection.SEARCH)
                }
                CustomizationPage.ISLAND -> IslandPage(appearance, extras, apps, islandEverywhere, onIsland, onIslandEverywhere, onPage)
                CustomizationPage.ISLAND_STYLE -> SettingsGroup { row { IslandStyleSection(IslandThemeController.current) } }
                CustomizationPage.KEYBOARD -> if (extras != null) ExtrasSettingsPage(extras, apps, ExtrasSection.KEYBOARD)
                CustomizationPage.PRIVACY -> {
                    if (extras != null) ExtrasSettingsPage(extras, apps, ExtrasSection.PRIVACY)
                    val hiddenState = model.state.collectAsState().value
                    SettingsGroup { row { Column(Modifier.padding(vertical = Space.M.dp)) {
                        HiddenAppsSetting(hiddenState.apps.filter { it.id in hiddenState.hiddenApps }) { model.unhideApp(it.id) } } } }
                    if (extras != null) SettingsGroup { row { Column(Modifier.padding(vertical = Space.M.dp)) { PermissionLedgerSection(extras) } } }
                }
                CustomizationPage.DEBUG -> IslandDebugSettings()
                CustomizationPage.STATUS_GRID -> StatusBarCalibrationSettings()
                CustomizationPage.FEED -> FeedSettings(feed, onFeedRefresh, onAddFeed, onRemoveFeed, onSourceEnabled, onImportOpml, onKeepPerFeed)
                CustomizationPage.BACKUP -> {
                    SettingsGroup(header = "Backup", footer = "Saves Home, folders, widgets and your Uno settings to a file. Restore shows a review before anything changes.") {
                        row { ActionRow("Save Backup…", "layout-export", onClick = onExportLayout) }
                        row { ActionRow("Restore Backup…", "layout-import", onClick = onImportLayout) }
                    }
                    SettingsGroup(header = "Updates") { row { Column(Modifier.padding(vertical = Space.M.dp), verticalArrangement = Arrangement.spacedBy(Space.S.dp)) {
                        UpdatesPanel(updates, onCheckUpdates, onInstallRelease, onAutoUpdate) } } }
                    LauncherHelp(isDefaultHome = isDefaultHome, onHomeSettings = onMakeDefault, onAddWidget = { onAddWidget(homePage) }, onShadeSetup = onShadeSetup)
                }
            }
        }
        }
    }
}

/** The first page: search, then the pages in iOS's grouped rows, each with its coloured icon tile. */
@Composable
private fun OverviewPage(state: LauncherState, model: LauncherModel, isDefaultHome: Boolean, onMakeDefault: () -> Unit,
    onPage: (CustomizationPage) -> Unit, onClose: () -> Unit) {
    // Search every setting by name, and go straight to its page.
    var settingsQuery by rememberSaveable { mutableStateOf("") }
    GlassTextField(settingsQuery, { settingsQuery = it }, Modifier.fillMaxWidth(), placeholder = stringResource(com.jake.duolauncher.R.string.search_settings),
        singleLine = true, minHeight = 40.dp, tag = "settings-search")
    val found = SettingsSearch.search(settingsQuery)
    if (settingsQuery.isNotBlank()) {
        SettingsGroup(footer = if (found.isEmpty()) stringResource(com.jake.duolauncher.R.string.no_settings_match) else null) {
            found.take(12).forEach { e -> row { NavRow(e.title, "settings-result", value = SettingsSearch.pageName(e.page)) { settingsQuery = ""; onPage(e.page) } } }
        }
        return
    }
    if (!isDefaultHome || state.canUndoEdit) SettingsGroup {
        if (!isDefaultHome) row { ActionRow(stringResource(com.jake.duolauncher.R.string.set_as_home_app), "default-home-settings", onClick = onMakeDefault) }
        if (state.canUndoEdit) row { ActionRow(stringResource(com.jake.duolauncher.R.string.undo_last_layout_change), "undo-layout") { model.undoEdit(); onClose() } }
    }
    SettingsGroup {
        row { NavRow("Appearance", "customization-wallpaper", icon = Icons.Rounded.Wallpaper, iconTint = Color(0xFF0A84FF)) { onPage(CustomizationPage.APPEARANCE) } }
        row { NavRow("Home Screen", "customization-home", icon = Icons.Rounded.GridView, iconTint = Color(0xFF5E5CE6)) { onPage(CustomizationPage.HOME) } }
        row { NavRow("Island", "customization-island", icon = IslandPillIcon, iconTint = Color(0xFF1C1C1E)) { onPage(CustomizationPage.ISLAND) } }
    }
    SettingsGroup {
        row { NavRow("Notifications & Privacy", "customization-privacy", icon = Icons.Rounded.Notifications, iconTint = Color(0xFFFF3B30)) { onPage(CustomizationPage.PRIVACY) } }
        row { NavRow("Gestures", "customization-gestures", icon = Icons.Rounded.Gesture, iconTint = Color(0xFF8E8E93)) { onPage(CustomizationPage.GESTURES) } }
        row { NavRow("Search", "customization-search", icon = Icons.Rounded.Search, iconTint = Color(0xFF8E8E93)) { onPage(CustomizationPage.SEARCH) } }
        row { NavRow("Keyboard", "customization-keyboard", icon = Icons.Rounded.Keyboard, iconTint = Color(0xFF8E8E93)) { onPage(CustomizationPage.KEYBOARD) } }
        row { NavRow("News Feed", "customization-feed", icon = Icons.Rounded.RssFeed, iconTint = IosOrange) { onPage(CustomizationPage.FEED) } }
    }
    val debuggable = androidx.compose.ui.platform.LocalContext.current.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    SettingsGroup {
        row { NavRow("General", "customization-backup", icon = Icons.Rounded.Settings, iconTint = Color(0xFF8E8E93)) { onPage(CustomizationPage.BACKUP) } }
        // Only a debug build has the island debug page; a release build neither shows it nor reads anything it saved.
        if (debuggable) row { NavRow("Island Debug", "customization-debug", icon = Icons.Rounded.BugReport, iconTint = Color(0xFF30D158)) { onPage(CustomizationPage.DEBUG) } }
    }
    if (isDefaultHome) SettingsGroup { row { ActionRow(stringResource(com.jake.duolauncher.R.string.change_home_app), "default-home-settings", onClick = onMakeDefault) } }
}

/** Glass, accent and depth: Appearance's groups below the wallpaper and light and dark. */
@Composable
private fun GlassGroups(appearance: AppearanceState, onGlass: (GlassChoice) -> Unit, onTiltHighlight: (Boolean) -> Unit,
    onWallpaperColor: (Boolean) -> Unit, onWallpaperDepth: (Boolean) -> Unit) {
    val samples = GlassRim.samples.intValue
    val context = androidx.compose.ui.platform.LocalContext.current
    // Read when the page opens and when the switch changes; reopening the page after allowing Sensors clears the note.
    val sensorsAllowed = remember(appearance.tiltHighlight) { GrapheneSensors.allowed(context) }
    SettingsGroup(header = "Glass", footer = buildString {
        append("Clear is iOS's Liquid Glass: see-through, with a bright edge. Frosted keeps a soft tint behind text. Sheets stay frosted either way.")
        if (appearance.liquidGlass) append(" Tilt Highlight moves the edge light as you tilt the phone.")
        if (appearance.liquidGlass && appearance.tiltHighlight) when {
            // GrapheneOS answers a denied app with readings that never change, not an error, so the grant itself is the reliable sign.
            sensorsAllowed == false -> append(" Sensors is off for Uno Launcher, so the highlight stays still. Allow it in App info › Permissions.")
            samples == 0 -> append(" No motion readings yet.")
        }
    }) {
        row { SegmentRow(GlassChoice.entries, appearance.glassKind, { when (it) { GlassChoice.CLEAR -> "Clear"; GlassChoice.FROSTED -> "Frosted"; GlassChoice.OFF -> "Off" } },
            { "glass-${it.name.lowercase()}" }, onGlass) }
        if (appearance.liquidGlass) row { SettingsSwitch("Tilt Highlight", appearance.tiltHighlight, onTiltHighlight, "tilt-highlight-switch") }
    }
    SettingsGroup(header = "Accent", footer = "The colour of switches, sliders, check marks and highlights. From Wallpaper picks one colour from your background and keeps text readable.") {
        row { CheckRow("Uno Orange", !appearance.wallpaperColor, "accent-orange") { onWallpaperColor(false) } }
        row { CheckRow("From Wallpaper", appearance.wallpaperColor, "accent-wallpaper") { onWallpaperColor(true) } }
    }
    SettingsGroup(footer = "The wallpaper drifts a little against the pages as you swipe. It stays still when Android's animations are off.") {
        row { SettingsSwitch("Wallpaper Depth", appearance.wallpaperDepth, onWallpaperDepth, "wallpaper-depth-switch") }
    }
}

/** The island's page: on and where, a way to line it up, what it shows, notifications, and its style a page further in. */
@Composable
private fun IslandPage(appearance: AppearanceState, extras: ExtrasActions?, apps: List<AppEntry>, islandEverywhere: Boolean,
    onIsland: (Boolean) -> Unit, onIslandEverywhere: (Boolean) -> Unit, onPage: (CustomizationPage) -> Unit) {
    val greyOverlay = islandEverywhere && !SystemShadeAccessibilityService.isConnected()
    SettingsGroup(footer = buildString {
        append("A capsule at the camera that shows the time, music, timers and what apps are doing, and opens with a tap.")
        append(" Over Other Apps keeps it there in every app.")
        if (greyOverlay) append(" Without Uno's accessibility service Android draws it slightly see-through over apps, so it looks grey; turn the service on for a solid island that also covers the status bar.")
        else append(" With Uno's accessibility service on, the Home island stays still at the camera while Android animates the launcher.")
    }) {
        row { SettingsSwitch("Island", appearance.island, onIsland, "island-switch") }
        row { SettingsSwitch("Over Other Apps", islandEverywhere, onIslandEverywhere, "island-everywhere-switch") }
        if (appearance.island) row { NavRow("Line Up with Status Bar", "customization-status-grid") { onPage(CustomizationPage.STATUS_GRID) } }
        if (appearance.island) row { NavRow("Style", "customization-island-style", value = IslandThemes.active.name) { onPage(CustomizationPage.ISLAND_STYLE) } }
    }
    if (extras != null) IslandSettingsGroups(extras, apps)
}

@Composable
private fun LauncherHelp(
    isDefaultHome: Boolean,
    onHomeSettings: () -> Unit,
    onAddWidget: () -> Unit,
    onShadeSetup: () -> Unit,
) {
    SettingsGroup(header = "Help", footer = "Swipe up anywhere on Home to open these settings, or long-press empty space. If a page is full, long-press the slim area at its left edge.") {
        row { HelpSection(Icons.Rounded.Home, "Home app",
            if (isDefaultHome) "Uno Launcher is your Home app. You can switch launchers in Android’s Home settings."
            else "Choose Uno Launcher in Android’s Home settings to use it when you press Home.") }
        row { ActionRow(if (isDefaultHome) "Change Home App" else "Set Uno Launcher as Home", "help-home-settings", onClick = onHomeSettings) }
        row { HelpSection(Icons.Rounded.Widgets, "Widgets", "Add Android widgets to empty Home cells. Hold a widget to move or remove it.") }
        row { ActionRow("Add Widget to This Page", "help-add-widget", onClick = onAddWidget) }
        row { HelpSection(Icons.Rounded.SwipeDown, "Notifications and Quick Settings",
            "Optional. Swiping down on Home can open the system panels, but Android only allows that through an accessibility service you turn on yourself. It can’t read your screen or see other apps, and you can turn it off any time in Settings › Accessibility. If you decline, swiping down on Home just does nothing.") }
        row { ActionRow("Set Up Shade Gestures", "help-shade-setup", onClick = onShadeSetup) }
        row { HelpSection(Icons.Rounded.Explore, "Discover",
            "Swipe right from the first Home page. If Google can't provide the feed, Uno Launcher keeps a Home return and recovery actions available. Add your own feeds under News Feed to fill this slot without Google.") }
    }
}

@Composable
private fun HelpSection(icon: ImageVector, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.M.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = 2.dp).size(22.dp), tint = SettingsAccent)
        Spacer(Modifier.width(Space.M.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = UnoType.body.copy(fontWeight = FontWeight.SemiBold))
            Text(detail, style = UnoType.footnote, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A small Home screen: the background (the photo being previewed, or [builtin] when a built-in scene is being looked at, otherwise the current
 * background) under the person's own icons and dock. [aspect] is width over height; the photo editor passes the phone's own, so the preview is
 * cropped exactly as the screen will be. [modifier] goes on the picture, for the editor's drag and pinch.
 */
@Composable private fun MiniHomePreview(stagedBitmap: android.graphics.Bitmap?, state: LauncherState,
    previewHeight: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, builtin: BuiltinBackground? = null, dark: Boolean = false,
    aspect: Float = .632f) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val backgroundRevision = LauncherBackgroundCache.revision.intValue
    val committedBitmap = remember(backgroundRevision) { cachedLauncherBackground(context) }
    val bitmap = stagedBitmap ?: committedBitmap.takeIf { builtin == null }
    val apps = remember(state.apps) { state.apps.associateBy { it.id } }
    val homeIcons = state.homeSlots.mapNotNull { id -> id?.let(apps::get) }.take(8)
    val dockIcons = state.dock.mapNotNull { id -> id?.let(apps::get) }.take(5)
    val scale = previewHeight.value * aspect / 250f
    fun unit(value: Float) = (value * scale).dp
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.height(previewHeight).width(previewHeight * aspect).clip(RoundedCornerShape(unit(24f)))
            .testTag("customization-home-preview").then(modifier)) {
            if (builtin != null) BuiltinPreview(builtin, dark) else DuneWallpaper()
            bitmap?.let { Image(it.asImageBitmap(), null, Modifier.matchParentSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
            Column(Modifier.fillMaxSize().padding(start = unit(16f), top = unit(18f), end = unit(54f)),
                verticalArrangement = Arrangement.spacedBy(unit(10f))) {
                Box(Modifier.fillMaxWidth().height(unit(42f)).background(MaterialTheme.colorScheme.surface.copy(alpha = .38f), RoundedCornerShape(unit(12f))))
                homeIcons.chunked(4).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { app -> Image(app.image, null, Modifier.size(unit(24f)).clip(Corner.icon)) }
                } }
            }
            Column(Modifier.align(Alignment.CenterEnd).padding(end = unit(10f)).width(unit(36f))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = .42f), RoundedCornerShape(unit(18f)))
                .padding(vertical = unit(8f)), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(unit(8f))) {
                dockIcons.forEach { app -> Image(app.image, null, Modifier.size(unit(22f)).clip(Corner.icon)) }
            }
        }
    }
}

/**
 * Appearance's wallpaper groups: a phone-shaped preview of Home (tap it to edit the photo), then choosing a photo or a built-in scene, and
 * Android's own wallpaper. Choosing either opens the full-screen editor ([WallpaperEditor]); nothing changes until Done there.
 */
@Composable private fun WallpaperGroups(backgrounds: LauncherBackgroundController, state: LauncherState, dark: Boolean, onWallpaperPreview: () -> Unit) {
    MiniHomePreview(null, state, 228.dp, dark = dark, modifier = Modifier.clickable(enabled = backgrounds.photoSelected && !backgrounds.loading,
        role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Edit photo", onClick = backgrounds::adjustCurrent))
    if (backgrounds.loading && !backgrounds.previewPending) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("background-loading"), color = SettingsAccent)
    (backgrounds.errorMessage ?: backgrounds.successMessage)?.takeIf { !backgrounds.previewPending }?.let { message ->
        GlassButton(onClick = backgrounds::clearMessage, modifier = Modifier.fillMaxWidth().testTag("background-message")) { Text(message) }
    }
    SettingsGroup(header = "Wallpaper", footer = "Swipe to try styles and pinch with two fingers to frame the photo before you tap Done.") {
        row { ActionRow(if (backgrounds.photoSelected) stringResource(com.jake.duolauncher.R.string.wallpaper_choose_different) else stringResource(com.jake.duolauncher.R.string.wallpaper_choose_photo),
            "background-choose", enabled = !backgrounds.loading, onClick = backgrounds::choosePhoto) }
        if (backgrounds.photoSelected) row { ActionRow("Edit Photo", "background-adjust", enabled = !backgrounds.loading, onClick = backgrounds::adjustCurrent) }
        if (backgrounds.photoSelected) row { ActionRow("Remove Photo", "background-reset", destructive = true, onClick = backgrounds::reset) }
    }
    SettingsGroup(header = "Built-in", footer = "The scenes follow light and dark.") {
        row { Box(Modifier.padding(vertical = Space.M.dp)) { BuiltInBackgroundPicker(backgrounds.builtin.takeIf { !backgrounds.photoSelected }, backgrounds::chooseBuiltin, dark) } }
    }
    SettingsGroup(footer = "Android Wallpaper Too also sets the phone's own Home and lock screen to the picture you choose here, replacing what is there. Nothing leaves the phone.") {
        row { SettingsSwitch("Android Wallpaper Too", backgrounds.systemSync, backgrounds::chooseSystemSync, "system-wallpaper-sync-switch") }
        row { ActionRow("Open Android Wallpaper", "wallpaper-preview", onClick = onWallpaperPreview) }
    }
}

/** The built-in scenes, three to a row; [selected] (null while a photo is the background) is ringed in the accent. Tiles nest concentrically:
 *  a small-cornered tile, and the scene inside it inset 6 dp with its radius 6 dp less. */
@Composable private fun BuiltInBackgroundPicker(selected: BuiltinBackground?, onSelect: (BuiltinBackground) -> Unit, dark: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Three to a row, each tall enough to show the scene itself.
        BuiltinBackground.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { builtin ->
                    val active = builtin == selected
                    // The chosen wallpaper wears the launcher's one accent (it was Material's lavender container and a teal ring).
                    val accent = SettingsAccent
                    Surface(onClick = { onSelect(builtin) },
                        modifier = Modifier.weight(1f).heightIn(min = 148.dp).testTag("builtin-${builtin.name.lowercase()}"),
                        shape = Corner.small,
                        color = if (active) accent.copy(alpha = .16f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = .08f),
                        border = if (active) BorderStroke(2.dp, accent) else null) {
                        Column(Modifier.padding(6.dp)) {
                            Box(Modifier.fillMaxWidth().height(112.dp).clip(Corner.inside(12.dp, 6.dp))) { BuiltinPreview(builtin, dark) }
                            // Two lines, so "Dune glass" and "Dune range" stay distinct at large text sizes.
                            Text(builtin.label, Modifier.fillMaxWidth().padding(top = 4.dp), maxLines = 2,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                // Keeps a short last row's cards the same width as the others.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable private fun BuiltinPreview(builtin: BuiltinBackground, dark: Boolean, modifier: Modifier = Modifier) {
    // Fills the card: a Canvas with no size draws nothing, which left every thumbnail empty.
    Canvas(modifier.fillMaxSize()) { drawBuiltin(builtin, dark) }
}

/**
 * Home Screen: the layout (Uno's side dock, which suits foldables and wide screens, or the iPhone's), the pinned apps and labels, the side
 * dock's measurements when it is in use, and the widgets on this page.
 */
@Composable private fun HomeLayoutSettings(state: LauncherState, wide: Boolean, onWide: (Boolean) -> Unit,
    model: LauncherModel, homePage: Int, onEditPins: () -> Unit, onWidget: (Int) -> Unit,
    onAddWidget: (Int) -> Unit, onRemoveWidget: (Int) -> Unit, homeStyle: HomeStyle = HomeStyle.SIDE, onHomeStyle: (HomeStyle) -> Unit = {}) {
    val p = if (wide) state.expanded else state.compact
    SettingsGroup(header = "Screen", footer = "A foldable has a layout for each screen. The inner screen always uses the side dock.") {
        row { SegmentRow(listOf(false, true), wide, { if (it) "Inner" else "Cover" }, { if (it) "home-screen-inner" else "home-screen-cover" }, onWide) }
    }
    // The phone screen's layout: Uno's side dock, or the iPhone's (see HomeStyle). The inner screen always has the side dock.
    if (!wide) SettingsGroup(header = "Layout", footer = if (homeStyle == HomeStyle.IOS)
            "iPhone: a strict iOS grid with Small, Medium and Large widgets, and the dock along the bottom with Search above it. The status bar shows instead of the status rail."
            else "Side Dock: four apps in a column on the right, under the status rail. Good on foldables and wide screens.") {
        row { CheckRow("Side Dock", homeStyle == HomeStyle.SIDE, "home-layout-side") { onHomeStyle(HomeStyle.SIDE) } }
        row { CheckRow("iPhone", homeStyle == HomeStyle.IOS, "home-layout-ios") { onHomeStyle(HomeStyle.IOS) } }
    }
    SettingsGroup {
        row { NavRow("Pinned Apps", "home-pinned-apps", onClick = onEditPins) }
        row { SettingsSwitch("Show App Names", state.labels, model::setLabels, "label-switch") }
    }
    // The iOS grid sets its own icon size, spacing and dock; these are for the side layout.
    if (homeStyle != HomeStyle.IOS || wide) SettingsGroup(header = "Side Dock") {
        row { CustomizationSlider("Icon Size", "${p.iconSize.toInt()} dp", p.iconSize, 40f..68f) { model.setPreset(wide, p.copy(iconSize = it)) } }
        row { CustomizationSlider("Row Spacing", "${p.rowGap.toInt()} dp", p.rowGap, 0f..28f) { model.setPreset(wide, p.copy(rowGap = it)) } }
        row { CustomizationSlider("Dock Width", "${p.dockWidth.toInt()} dp", p.dockWidth, 56f..84f) { model.setPreset(wide, p.copy(dockWidth = it)) } }
        row { SettingsSwitch("Align Dock with Rows", p.dockAlignToGrid, { model.setPreset(wide, p.copy(dockAlignToGrid = it)) }) }
        if (!p.dockAlignToGrid) row { CustomizationSlider("Dock Position", "${(p.dockPosition * 100).toInt()}%", p.dockPosition, .25f.. .75f) { model.setPreset(wide, p.copy(dockPosition = it)) } }
        row { ActionRow("Reset Layout", "home-reset-layout") { model.setPreset(wide, LayoutPreset()) } }
    }
    val placements = state.widgetPlacements.filter { it.page == homePage || (wide && it.page == -1) }
    SettingsGroup(header = "Widgets · Page ${homePage + 1}") {
        placements.forEach { placement -> row {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (placement.page == -1) "Wide-screen page" else "${placement.spanX} × ${placement.spanY} · Row ${placement.row + 1}", Modifier.weight(1f))
                Text(stringResource(com.jake.duolauncher.R.string.replace), Modifier.clip(Corner.small).clickable { onWidget(placement.slot) }.padding(8.dp),
                    color = SettingsAccent)
                IconButton(onClick = { onRemoveWidget(placement.slot) }, modifier = Modifier.semantics { contentDescription = if (placement.page == -1) "Remove widget from Wide-screen page" else "Remove widget" }) {
                    Icon(Icons.Rounded.RemoveCircle, null, tint = Color(0xFFFF3B30)) }
            }
        } }
        row { ActionRow("Add Widget", "home-add-widget") { onAddWidget(homePage) } }
    }
}

@Composable internal fun SettingsSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit, tag: String? = null) {
    val glass = LocalPageGlass.current
    // The label and the switch are one control for a screen reader ("label, on, switch"), and the switch has a 48 dp touch target.
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        LiquidSwitchControl(checked, onChecked, glass, Modifier.then(if (tag != null) Modifier.testTag(tag) else Modifier))
    }
}

@Composable internal fun CustomizationSlider(label: String, valueLabel: String, value: Float,
    range: ClosedFloatingPointRange<Float>, tag: String? = null, onChange: (Float) -> Unit) {
    val glass = LocalPageGlass.current
    Column(Modifier.padding(top = Space.M.dp, bottom = Space.XS.dp).then(if (tag != null) Modifier.testTag(tag) else Modifier)) {
        Row { Text(label, Modifier.weight(1f)); Text(valueLabel, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        LiquidSliderControl(value, range, onChange, glass, Modifier.semantics { contentDescription = label })
    }
}


/** The Dynamic island's own shape (a capsule), for its row in settings: a plain circle said nothing about it. */
private val IslandPillIcon: ImageVector = ImageVector.Builder("IslandPill", 24.dp, 24.dp, 24f, 24f).path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
    moveTo(8f, 8.5f); horizontalLineTo(16f)
    arcTo(3.5f, 3.5f, 0f, false, true, 16f, 15.5f)
    horizontalLineTo(8f)
    arcTo(3.5f, 3.5f, 0f, false, true, 8f, 8.5f)
    close()
}.build()
