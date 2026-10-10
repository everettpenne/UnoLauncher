package com.jake.duolauncher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal enum class CustomizationPage { OVERVIEW, WALLPAPER, ICONS, HOME, GESTURES, SEARCH, PANEL, ISLAND, KEYBOARD, PRIVACY, FEED, UPDATES, BACKUP, HELP }

@Composable
internal fun CustomizationSheet(state: LauncherState, initiallyWide: Boolean, model: LauncherModel,
    isDefaultHome: Boolean, page: CustomizationPage, onPage: (CustomizationPage) -> Unit,
    onMakeDefault: () -> Unit, onClose: () -> Unit, onEditPins: () -> Unit, onWidget: (Int) -> Unit,
    onAddWidget: (Int) -> Unit, onRemoveWidget: (Int) -> Unit, onWallpaperPreview: () -> Unit,
    onExportLayout: () -> Unit, onImportLayout: () -> Unit,
    appearance: AppearanceState, onAppearanceMode: (AppearanceMode) -> Unit,
    onAppearanceManual: (String, Double, Double) -> Unit, onAppearanceDeviceLocation: () -> Unit,
    onAppearanceClear: () -> Unit, backgrounds: LauncherBackgroundController, homePage: Int = 0,
    onShadeSetup: () -> Unit = {},
    extras: ExtrasActions? = null,
    feed: FeedState = FeedState(),
    onFeedRefresh: () -> Unit = {},
    onAddFeed: (String, (FeedAddResult) -> Unit) -> Unit = { _, _ -> },
    onRemoveFeed: (String) -> Unit = {},
    onSourceEnabled: (String, Boolean) -> Unit = { _, _ -> },
    glassBackdrop: com.kyant.backdrop.Backdrop? = null,
    glassTint: Color = Glass.copy(alpha = .62f),
    settings: GlassSettings = GlassSettings.Default,
    onLiquidGlass: (Boolean) -> Unit = {},
    onWallpaperColor: (Boolean) -> Unit = {},
    onTiltHighlight: (Boolean) -> Unit = {},
    onTiltStrength: (Float) -> Unit = {},
    onRefractionHeight: (Float) -> Unit = {},
    onIsland: (Boolean) -> Unit = {},
    onIslandScale: (Float) -> Unit = {},
    islandEverywhere: Boolean = false,
    onIslandEverywhere: (Boolean) -> Unit = {},
    updates: UpdateState = UpdateState(),
    onCheckUpdates: () -> Unit = {},
    onInstallRelease: (String) -> Unit = {},
    onAutoUpdate: (Boolean) -> Unit = {},
    onRefractionAmount: (Float) -> Unit = {},
    onRefractionChroma: (Float) -> Unit = {},
) {
    var wide by rememberSaveable { mutableStateOf(initiallyWide) }
    val title = when (page) {
        CustomizationPage.OVERVIEW -> "Launcher settings"
        CustomizationPage.WALLPAPER -> "Wallpaper & glass"
        CustomizationPage.ICONS -> "App icons"
        CustomizationPage.HOME -> "Home layout"
        CustomizationPage.GESTURES -> "Gestures & feel"
        CustomizationPage.SEARCH -> "Search"
        CustomizationPage.PANEL -> "Control panel & Focus"
        CustomizationPage.ISLAND -> "Dynamic island"
        CustomizationPage.KEYBOARD -> "Keyboard"
        CustomizationPage.PRIVACY -> "Notifications & privacy"
        CustomizationPage.FEED -> "News feed"
        CustomizationPage.UPDATES -> "Updates"
        CustomizationPage.BACKUP -> "Backup"
        CustomizationPage.HELP -> "Help & setup"
    }
    val bodyScroll = rememberScrollState()
    LaunchedEffect(page) { bodyScroll.scrollTo(0) }
    Column(Modifier.fillMaxWidth().fillMaxHeight(.92f)
        .then(if (glassBackdrop != null) Modifier.liquidGlass(glassBackdrop, Corner.xlarge,
            glassTint, blurRadius = 4f, settings = settings) else Modifier)
        .padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (page != CustomizationPage.OVERVIEW) IconButton(onClick = { onPage(CustomizationPage.OVERVIEW) },
                Modifier.testTag("customization-back")) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close customization") }
        }
        Column(Modifier.weight(1f).verticalScroll(bodyScroll).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (page) {
                CustomizationPage.OVERVIEW -> {
                    if (!isDefaultHome) Button(onClick = onMakeDefault, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("default-home-settings")) { Text("Set as home app") }
                    if (state.canUndoEdit) OutlinedButton(onClick = { model.undoEdit(); onClose() },
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Undo last layout change") }
                    MiniHomePreview(backgrounds.previewBitmap, state, 176.dp)
                    SettingsGroupLabel("Look")
                    CustomizationDestination(Icons.Rounded.Wallpaper, "Wallpaper & glass",
                        if (backgrounds.previewPending) "Photo ready to review" else "Background, liquid glass, light and dark",
                        "customization-wallpaper") { onPage(CustomizationPage.WALLPAPER) }
                    if (extras != null) CustomizationDestination(Icons.Rounded.Apps, "App icons",
                        "Original, themed or tinted by your wallpaper", "customization-icons") { onPage(CustomizationPage.ICONS) }
                    SettingsGroupLabel("Home")
                    CustomizationDestination(Icons.Rounded.GridView, "Home layout",
                        "Icons, spacing, dock, and widgets", "customization-home") { onPage(CustomizationPage.HOME) }
                    CustomizationDestination(Icons.Rounded.Gesture, "Gestures & feel",
                        "Labels, status, swipes, haptics and sounds", "customization-gestures") { onPage(CustomizationPage.GESTURES) }
                    if (extras != null) CustomizationDestination(Icons.Rounded.Search, "Search",
                        "Google button, suggestions, contacts", "customization-search") { onPage(CustomizationPage.SEARCH) }
                    if (extras != null) CustomizationDestination(Icons.Rounded.Tune, "Control panel & Focus",
                        "Panel tiles, shortcuts, Focus", "customization-panel") { onPage(CustomizationPage.PANEL) }
                    SettingsGroupLabel("Island & keyboard")
                    if (extras != null) CustomizationDestination(Icons.Rounded.Circle, "Dynamic island",
                        "Size, live activities, bubbles, widget, timer", "customization-island") { onPage(CustomizationPage.ISLAND) }
                    if (extras != null) CustomizationDestination(Icons.Rounded.Keyboard, "Keyboard",
                        "Uno Keyboard: suggestions, autocorrect, one-handed", "customization-keyboard") { onPage(CustomizationPage.KEYBOARD) }
                    SettingsGroupLabel("Privacy")
                    if (extras != null) CustomizationDestination(Icons.Rounded.Lock, "Notifications & privacy",
                        "Notification access, badges, what each permission can see", "customization-privacy") { onPage(CustomizationPage.PRIVACY) }
                    SettingsGroupLabel("More")
                    CustomizationDestination(Icons.Rounded.RssFeed, "News feed",
                        "Your own headlines in place of Discover", "customization-feed") { onPage(CustomizationPage.FEED) }
                    CustomizationDestination(Icons.Rounded.Save, "Backup",
                        "Save or restore your layout and settings", "customization-backup") { onPage(CustomizationPage.BACKUP) }
                    CustomizationDestination(Icons.Rounded.SystemUpdateAlt, "Updates",
                        "Check for releases or update automatically", "customization-updates") { onPage(CustomizationPage.UPDATES) }
                    CustomizationDestination(Icons.AutoMirrored.Rounded.HelpOutline, "Help & setup",
                        "Home app, widgets, gestures, and Discover", "customization-help") {
                        onPage(CustomizationPage.HELP)
                    }
                    if (isDefaultHome) TextButton(onClick = onMakeDefault, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("default-home-settings")) { Text("Change home app") }
                }
                CustomizationPage.WALLPAPER -> {
                    MiniHomePreview(backgrounds.previewBitmap, state, 228.dp)
                    Text("Launcher background", style = MaterialTheme.typography.titleMedium)
                    Text("Changes the image behind Uno Launcher’s Home screens.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = backgrounds::choosePhoto, enabled = !backgrounds.loading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("background-choose")) {
                        Text(if (backgrounds.previewPending) "Choose a different photo" else "Choose a photo")
                    }
                    if (backgrounds.previewPending) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = backgrounds::cancelPreview, Modifier.weight(1f).heightIn(min = 48.dp)
                            .testTag("background-preview-cancel")) { Text("Cancel") }
                        Button(onClick = backgrounds::applyPreview, enabled = backgrounds.previewBitmap != null,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("background-preview-apply")) { Text("Apply") }
                    }
                    if (backgrounds.photoSelected && !backgrounds.previewPending) OutlinedButton(onClick = backgrounds::reset,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("background-reset")) { Text("Reset to Uno dunes") }
                    if (backgrounds.loading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("background-loading"))
                    (backgrounds.errorMessage ?: backgrounds.successMessage)?.let { message ->
                        TextButton(onClick = backgrounds::clearMessage, Modifier.fillMaxWidth().testTag("background-message")) { Text(message) }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text("Android wallpaper", style = MaterialTheme.typography.titleMedium)
                    Text("Opens Android’s preview to change the phone wallpaper. It does not change Duo’s launcher background.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onWallpaperPreview, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("wallpaper-preview")) { Icon(Icons.Rounded.Wallpaper, null); Spacer(Modifier.width(8.dp)); Text("Preview Android wallpaper") }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    SettingsSwitch("Liquid glass", appearance.liquidGlass, onLiquidGlass, "liquid-glass-switch")
                    if (appearance.liquidGlass) {
                        CustomizationSlider("Refraction height", "${(appearance.refractionHeight * 100).toInt()}%",
                            appearance.refractionHeight, 0f..1f, tag = "refraction-height-slider") { onRefractionHeight(it) }
                        CustomizationSlider("Refraction amount", "${(appearance.refractionAmount * 100).toInt()}%",
                            appearance.refractionAmount, 0f..1f, tag = "refraction-amount-slider") { onRefractionAmount(it) }
                        CustomizationSlider("Chromatic aberration", "${(appearance.refractionChroma * 100).toInt()}%",
                            appearance.refractionChroma, 0f..1f, tag = "chromatic-slider") { onRefractionChroma(it) }
                    }
                    if (appearance.liquidGlass) {
                        SettingsSwitch("Tilt-following highlight", appearance.tiltHighlight, onTiltHighlight, "tilt-highlight-switch")
                        Text("The glass edge light stays put in the room as you tilt the phone, so the shine moves across the glass. It reads the motion sensor only while Home is on screen. Off by default.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (appearance.tiltHighlight) {
                            CustomizationSlider("Tilt strength", "${(appearance.tiltStrength * 100).toInt()}%",
                                appearance.tiltStrength, 0f..1f, tag = "tilt-strength-slider") { onTiltStrength(it) }
                            Text("Higher swings the light further for each degree you tilt and makes the rim brighter and thicker.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            // Live readout, so you can tell whether the sensor reaches the launcher at all.
                            val samples = GlassRim.samples.intValue
                            Text(if (samples == 0) "No motion readings yet. If you use GrapheneOS, allow Sensors for Uno Launcher in its app settings."
                                else "Motion sensor working. Light angle ${GlassRim.angle.toInt()}\u00B0 (45\u00B0 when upright).",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("tilt-readout"))
                        }
                    }
                    SettingsSwitch("Color from wallpaper", appearance.wallpaperColor, onWallpaperColor, "wallpaper-color-switch")
                    Text("Takes one color from your wallpaper and uses it to tint the glass, and for sliders, switches and themed icons. It adapts to light and dark, and keeps text readable. Works with liquid glass on or off.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Height widens the glass rim the lens bends; amount sets how far the view behind is displaced; chromatic adds the color fringe at the edges. Turn off liquid glass for a flat look or to save battery.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    AppearanceSettings(appearance, onAppearanceMode, onAppearanceManual, onAppearanceDeviceLocation, onAppearanceClear)
                }
                CustomizationPage.HOME -> HomeLayoutSettings(state, wide, { wide = it }, model, homePage,
                    onEditPins, onWidget, onAddWidget, onRemoveWidget)
                CustomizationPage.GESTURES -> {
                    SettingsSwitch("Show app names", state.labels, model::setLabels, "label-switch")
                    SettingsSwitch("Show status at upper right", state.verticalStatus, model::setVerticalStatus, "status-switch")
                    Text("Swipe sideways anywhere on Home to change pages. Swipe down on the left for notifications, and on the right for the control panel. Swipe up anywhere to open these settings.",
                        style = MaterialTheme.typography.bodyMedium)
                    if (extras != null) ExtrasSettingsPage(extras, model.state.collectAsState().value.apps, ExtrasSection.GESTURES)
                }
                CustomizationPage.ICONS -> if (extras != null) ExtrasSettingsPage(extras, model.state.collectAsState().value.apps, ExtrasSection.ICONS)
                CustomizationPage.SEARCH -> {
                    SettingsSwitch("Search button opens Google", state.googleSearch, model::setGoogleSearch, "google-search-switch")
                    Text("All apps always keeps local app search.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (extras != null) ExtrasSettingsPage(extras, model.state.collectAsState().value.apps, ExtrasSection.SEARCH)
                }
                CustomizationPage.PANEL -> if (extras != null) ExtrasSettingsPage(extras, model.state.collectAsState().value.apps, ExtrasSection.PANEL)
                CustomizationPage.ISLAND -> {
                    SettingsSwitch("Dynamic island", appearance.island, onIsland, "island-switch")
                    SettingsSwitch("Island everywhere", islandEverywhere, onIslandEverywhere, "island-everywhere-switch")
                    Text("Shows the island above other apps with Android's \"display over other apps\" permission. The overlay window is exactly the island's size, never draws on the lock screen or when the screen is off, and touches outside it pass through.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (appearance.island) CustomizationSlider("Island size",
                        "${(appearance.islandScale * 100).toInt()}%", appearance.islandScale, 0f..1f,
                        tag = "island-scale-slider") { onIslandScale(it) }
                    Text("A liquid capsule at the camera cutout: the time, then a tap expands battery, the top feed headline, and quick actions. It flashes app launches and charging and never overlays other apps.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (extras != null) ExtrasSettingsPage(extras, model.state.collectAsState().value.apps, ExtrasSection.ISLAND)
                }
                CustomizationPage.KEYBOARD -> if (extras != null) ExtrasSettingsPage(extras, model.state.collectAsState().value.apps, ExtrasSection.KEYBOARD)
                CustomizationPage.PRIVACY -> if (extras != null) ExtrasSettingsPage(extras, model.state.collectAsState().value.apps, ExtrasSection.PRIVACY)
                CustomizationPage.FEED -> FeedSettings(feed, onFeedRefresh, onAddFeed, onRemoveFeed, onSourceEnabled)
                CustomizationPage.UPDATES -> UpdatesPanel(updates, onCheckUpdates, onInstallRelease, onAutoUpdate)
                CustomizationPage.BACKUP -> {
                    Text("Save the current Home layout, folders, widgets, layout settings, and your Uno settings.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onExportLayout, Modifier.weight(1f).heightIn(min = 48.dp).testTag("layout-export")) { Text("Save") }
                        Button(onClick = onImportLayout, Modifier.weight(1f).heightIn(min = 48.dp).testTag("layout-import")) { Text("Restore") }
                    }
                    Text("Restore shows a review before changing Home.", style = MaterialTheme.typography.bodySmall)
                }
                CustomizationPage.HELP -> LauncherHelp(
                    isDefaultHome = isDefaultHome,
                    onHomeSettings = onMakeDefault,
                    onAddWidget = { onAddWidget(homePage) },
                    onShadeSetup = onShadeSetup,
                )
            }
        }
    }
}

@Composable
private fun LauncherHelp(
    isDefaultHome: Boolean,
    onHomeSettings: () -> Unit,
    onAddWidget: () -> Unit,
    onShadeSetup: () -> Unit,
) {
    HelpSection(Icons.Rounded.Home, "Home app",
        if (isDefaultHome) "Uno Launcher is your Home app. You can switch launchers in Android’s Home settings."
        else "Choose Uno Launcher in Android’s Home settings to use it when you press Home.")
    Button(onClick = onHomeSettings, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("help-home-settings")) {
        Text(if (isDefaultHome) "Change home app" else "Set Uno Launcher as Home")
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    HelpSection(Icons.Rounded.TouchApp, "Customize any page",
        "Swipe up anywhere on Home to open these settings, or long-press empty space and choose Customize launcher. If a page is full, long-press the slim area at its left edge.")
    HelpSection(Icons.Rounded.Widgets, "Widgets",
        "Add Android widgets to empty Home cells. Hold a widget to move or remove it.")
    OutlinedButton(onClick = onAddWidget, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("help-add-widget")) {
        Text("Add widget to this page")
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    HelpSection(Icons.Rounded.SwipeDown, "Notifications and quick settings",
        "Optional. Swiping down on Home can open the system panels, but Android only allows that through an Accessibility service you turn on yourself. It can’t read your screen or see other apps, it stays off until you enable it, and you can turn it off any time in Settings → Accessibility. If you decline, swiping down on Home just does nothing.")
    TextButton(onClick = onShadeSetup, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("help-shade-setup")) {
        Text("Set up shade gestures")
    }
    HelpSection(Icons.Rounded.Explore, "Discover",
        "Swipe right from the first Home page. If Google can't provide the feed, Uno Launcher keeps a Home return and recovery actions available. Add your own feeds in News feed customization to fill this slot without Google.")
}

@Composable
private fun HelpSection(icon: ImageVector, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = 2.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The small grey heading above a group of rows on the settings overview, as in iOS Settings. */
@Composable private fun SettingsGroupLabel(text: String) {
    Text(text.uppercase(), Modifier.padding(start = 6.dp, top = 10.dp), style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun CustomizationDestination(icon: ImageVector, title: String, detail: String, tag: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(tag),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .52f), shape = Corner.medium) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(detail,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

@Composable private fun MiniHomePreview(stagedBitmap: android.graphics.Bitmap?, state: LauncherState,
    previewHeight: androidx.compose.ui.unit.Dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val backgroundRevision = LauncherBackgroundCache.revision.intValue
    val committedBitmap = remember(backgroundRevision) { cachedLauncherBackground(context) }
    val bitmap = stagedBitmap ?: committedBitmap
    val apps = remember(state.apps) { state.apps.associateBy { it.id } }
    val homeIcons = state.homeSlots.mapNotNull { id -> id?.let(apps::get) }.take(8)
    val dockIcons = state.dock.mapNotNull { id -> id?.let(apps::get) }.take(5)
    val scale = previewHeight.value * .632f / 250f
    fun unit(value: Float) = (value * scale).dp
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.height(previewHeight).width(previewHeight * .632f).clip(RoundedCornerShape(unit(24f)))
            .testTag("customization-home-preview")) {
            DuneWallpaper()
            bitmap?.let { Image(it.asImageBitmap(), null, Modifier.matchParentSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
            Column(Modifier.fillMaxSize().padding(start = unit(16f), top = unit(18f), end = unit(54f)),
                verticalArrangement = Arrangement.spacedBy(unit(10f))) {
                Box(Modifier.fillMaxWidth().height(unit(42f)).background(MaterialTheme.colorScheme.surface.copy(alpha = .38f), RoundedCornerShape(unit(12f))))
                homeIcons.chunked(4).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { app -> Image(app.icon.asImageBitmap(), null, Modifier.size(unit(24f)).clip(Corner.icon)) }
                } }
            }
            Column(Modifier.align(Alignment.CenterEnd).padding(end = unit(10f)).width(unit(36f))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = .42f), RoundedCornerShape(unit(18f)))
                .padding(vertical = unit(8f)), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(unit(8f))) {
                dockIcons.forEach { app -> Image(app.icon.asImageBitmap(), null, Modifier.size(unit(22f)).clip(Corner.icon)) }
            }
        }
    }
}

@Composable private fun HomeLayoutSettings(state: LauncherState, wide: Boolean, onWide: (Boolean) -> Unit,
    model: LauncherModel, homePage: Int, onEditPins: () -> Unit, onWidget: (Int) -> Unit,
    onAddWidget: (Int) -> Unit, onRemoveWidget: (Int) -> Unit) {
    val p = if (wide) state.expanded else state.compact
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!wide, { onWide(false) }, label = { Text("Cover") })
        FilterChip(wide, { onWide(true) }, label = { Text("Inner") })
    }
    OutlinedButton(onClick = onEditPins, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Choose Home apps") }
    CustomizationSlider("App icon size", "${p.iconSize.toInt()} dp", p.iconSize, 40f..68f) { model.setPreset(wide, p.copy(iconSize = it)) }
    CustomizationSlider("Space between rows", "${p.rowGap.toInt()} dp", p.rowGap, 0f..28f) { model.setPreset(wide, p.copy(rowGap = it)) }
    CustomizationSlider("Dock width", "${p.dockWidth.toInt()} dp", p.dockWidth, 56f..84f) { model.setPreset(wide, p.copy(dockWidth = it)) }
    SettingsSwitch("Align dock with app rows", p.dockAlignToGrid, { model.setPreset(wide, p.copy(dockAlignToGrid = it)) })
    if (!p.dockAlignToGrid) CustomizationSlider("Dock height on screen", "${(p.dockPosition * 100).toInt()}%", p.dockPosition, .25f.. .75f) { model.setPreset(wide, p.copy(dockPosition = it)) }
    TextButton(onClick = { model.setPreset(wide, LayoutPreset()) }, Modifier.fillMaxWidth()) { Text("Reset this layout") }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Text("Widgets · Page ${homePage + 1}", style = MaterialTheme.typography.titleMedium)
    state.widgetPlacements.filter { it.page == homePage || (wide && it.page == -1) }.forEach { placement ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (placement.page == -1) "Unfolded-only page" else "${placement.spanX} × ${placement.spanY} widget · row ${placement.row + 1}", Modifier.weight(1f))
            IconButton(onClick = { onRemoveWidget(placement.slot) }, modifier = Modifier.semantics { contentDescription = if (placement.page == -1) "Remove widget from Unfolded-only page" else "Remove widget" }) { Icon(Icons.Rounded.DeleteOutline, null) }
            TextButton(onClick = { onWidget(placement.slot) }) { Text("Replace") }
        }
    }
    TextButton(onClick = { onAddWidget(homePage) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Add widget to this page") }
}

@Composable internal fun SettingsSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit, tag: String? = null) {
    val glass = LocalPageGlass.current
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        LiquidSwitchControl(checked, onChecked, glass, Modifier.then(if (tag != null) Modifier.testTag(tag) else Modifier))
    }
}

@Composable private fun CustomizationSlider(label: String, valueLabel: String, value: Float,
    range: ClosedFloatingPointRange<Float>, tag: String? = null, onChange: (Float) -> Unit) {
    val glass = LocalPageGlass.current
    Column(Modifier.then(if (tag != null) Modifier.testTag(tag) else Modifier)) {
        Row { Text(label, Modifier.weight(1f)); Text(valueLabel, color = if (glass != null) (LocalWallpaperAccent.current?.accent ?: IosOrange) else MaterialTheme.colorScheme.primary) }
        LiquidSliderControl(value, range, onChange, glass, Modifier.semantics { contentDescription = label })
    }
}
