@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.jake.duolauncher

import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.island.*
import com.jake.duolauncher.feed.*
import com.jake.duolauncher.search.*
import com.jake.duolauncher.panel.*
import com.jake.duolauncher.appearance.*
import com.jake.duolauncher.folders.*
import com.jake.duolauncher.widgets.*
import android.appwidget.AppWidgetProviderInfo
import android.os.UserManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val Ink: Color
    @Composable get() = LocalDuoPalette.current.ink
internal val Glass: Color
    @Composable get() = LocalDuoPalette.current.glass

/**
 * How far (in px, negative = up) the floating bottom glass must translate so its bottom edge rests
 * just above the keyboard's top edge, while still clearing the navigation bar when the keyboard is
 * closed. The glass already sits above the navigation bar (the screen content is padded for the
 * system bars), so only the part of the IME inset that exceeds the navigation bar lifts it. On
 * API 30+ the IME inset is measured from the very bottom of the window, so it already includes the
 * navigation bar region; subtracting it avoids double-counting.
 */
internal fun imeLiftPx(imeBottom: Int, navBarBottom: Int): Int =
    -(imeBottom - navBarBottom).coerceAtLeast(0)

private fun findFreeWidgetIndex(layout: HomeLayout, page: Int, spanX: Int, spanY: Int): Int? {
    val blocked = layout.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() }
    for (row in 0..GRID_ROWS - spanY) for (column in 0..GRID_COLUMNS - spanX) {
        val cells = buildList {
            repeat(spanY) { y -> repeat(spanX) { x -> add(homeCellIndex(page, (row + y) * GRID_COLUMNS + column + x)) } }
        }
        if (cells.none { it in blocked || layout.slotAt(it) != null }) return cells.first()
    }
    return null
}

@Composable
internal fun LauncherScreen(
    state: LauncherState, model: LauncherModel, widgets: WidgetController, homeRequests: Int,
    onLaunch: (AppEntry) -> Unit, onMakeDefault: () -> Unit, onAppInfo: (AppEntry) -> Unit,
    isDefaultHome: Boolean, deviceStatus: DeviceStatus, onStatusMode: (Boolean) -> Unit, onWallpaperPreview: () -> Unit,
    onDiscover: () -> Unit = {}, searchRequests: Int = 0, customizeRequests: Int = 0,
    onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit = { app, _ -> onLaunch(app) },
    onGoogleSearch: (android.graphics.Rect?) -> Boolean = { false },
    appearance: AppearanceState = AppearanceState(),
    onAppearanceMode: (AppearanceMode) -> Unit = {},
    onAppearanceManual: (String, Double, Double) -> Unit = { _, _, _ -> },
    onAppearanceDeviceLocation: () -> Unit = {},
    onAppearanceCancelLocation: () -> Unit = {},
    onAppearanceClear: () -> Unit = {},
    showFirstRun: Boolean = false,
    showWhatsNew: Boolean = false,
    onWhatsNewDone: () -> Unit = {},
    onFinishFirstRun: () -> Unit = {},
    onShadeSetup: () -> Unit = {},
    extras: ExtrasActions? = null,
    feed: FeedState = FeedState(),
    feedSetupRequests: Int = 0,
    onFeedRefresh: () -> Unit = {},
    onFeedOpenEntry: (String) -> Unit = {},
    onFeedToggleSave: (String) -> Unit = {},
    onFeedVisible: () -> Unit = {},
    onAddFeed: (String, (FeedAddResult) -> Unit) -> Unit = { _, _ -> },
    onRemoveFeed: (String) -> Unit = {},
    onSourceEnabled: (String, Boolean) -> Unit = { _, _ -> },
    onImportOpml: (String) -> FeedOpmlResult = { _ -> FeedOpmlResult(0, 0) },
    onKeepPerFeed: (Int) -> Unit = {},
    onLiquidGlass: (Boolean) -> Unit = {},
    onWallpaperColor: (Boolean) -> Unit = {},
    onMaterialYou: (Boolean) -> Unit = {},
    onWallpaperDepth: (Boolean) -> Unit = {},
    onTiltHighlight: (Boolean) -> Unit = {},
    onTiltStrength: (Float) -> Unit = {},
    island: IslandState = IslandState(),
    onIsland: (Boolean) -> Unit = {},
    onIslandScale: (Float) -> Unit = {},
    islandEverywhere: Boolean = false,
    onIslandEverywhere: (Boolean) -> Unit = {},
    updates: UpdateState = UpdateState(),
    onCheckUpdates: () -> Unit = {},
    onInstallRelease: (String) -> Unit = {},
    onAutoUpdate: (Boolean) -> Unit = {},
    onRefractionHeight: (Float) -> Unit = {},
    onRefractionAmount: (Float) -> Unit = {},
    onRefractionChroma: (Float) -> Unit = {},
) {
    var sheet by rememberSaveable { mutableStateOf("") }
    var dockSlot by rememberSaveable { mutableIntStateOf(0) }
    var widgetSlot by rememberSaveable { mutableIntStateOf(0) }
    var widgetTargetIndex by rememberSaveable { mutableIntStateOf(Int.MIN_VALUE) }
    var widgetExactTarget by rememberSaveable { mutableStateOf(false) }
    var widgetPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var widgetProfileSerial by rememberSaveable { mutableStateOf<Long?>(null) }
    var widgetSession by remember { mutableStateOf<WidgetPickerSession?>(null) }
    var widgetPlacementMessage by remember { mutableStateOf<String?>(null) }
    var emptyCellIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var resizeSlot by remember { mutableStateOf<Int?>(null) }
    var resizeWidth by rememberSaveable { mutableIntStateOf(1) }
    var resizeHeight by rememberSaveable { mutableIntStateOf(1) }
    var resizeConstraints by remember { mutableStateOf<WidgetSpanConstraints?>(null) }
    var resizePitchX by remember { mutableFloatStateOf(1f) }
    var resizePitchY by remember { mutableFloatStateOf(1f) }
    var resizeTopPitch by remember { mutableFloatStateOf(1f) }
    var resizeAppPitch by remember { mutableFloatStateOf(1f) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var appMoveMenu by rememberSaveable { mutableStateOf(false) }
    // Edit Home: icons wiggle and can be dragged at once, removed with their badge, and an + adds apps or a widget.
    var homeEditing by rememberSaveable { mutableStateOf(false) }
    var customizationPage by rememberSaveable { mutableStateOf(CustomizationPage.OVERVIEW) }
    LaunchedEffect(selectedId) { if (selectedId == null) appMoveMenu = false }
    // The settings page is deliberately kept when the sheet closes: swiping up again reopens the page you were on.
    var openFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    // The app a split screen is being set up for; the picker then chooses the one beside it.
    var splitFirstId by rememberSaveable { mutableStateOf<String?>(null) }
    var createFolderFirstId by rememberSaveable { mutableStateOf<String?>(null) }
    /** The folder that was only just made, opened with its title ready to type over (iOS opens a new folder the same way). */
    var newFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var savedPage by rememberSaveable { mutableIntStateOf(0) }
    var lastHomePage by rememberSaveable { mutableIntStateOf(0) }
    var libraryQuery by rememberSaveable { mutableStateOf("") }
    val contactsOn = extras?.store?.state?.contactSearch == true
    var contactResults by remember { mutableStateOf(emptyList<ContactResult>()) }
    val handoffResolver = rememberHandoffResolver(extras?.store?.state ?: ExtrasState())
    var pinQuery by rememberSaveable { mutableStateOf("") }
    val launcherActivity = androidx.activity.compose.LocalActivity.current as MainActivity
    val launcherRootView = LocalView.current.rootView
    LaunchedEffect(libraryQuery, contactsOn) {
        if (!contactsOn || libraryQuery.trim().length < ContactMatch.MIN_QUERY) { contactResults = emptyList(); return@LaunchedEffect }
        delay(180)
        contactResults = withContext(Dispatchers.IO) { ContactsSearch.search(launcherActivity, libraryQuery) }
    }
    DisposableEffect(sheet == "widgets") {
        val active = sheet == "widgets"
        if (active) LiveDiscover.setExternalResultPending(launcherActivity, "main", "widget-picker", true)
        onDispose { if (active) LiveDiscover.setExternalResultPending(launcherActivity, "main", "widget-picker", false) }
    }
    val appsById = remember(state.apps) { state.apps.associateBy { it.id } }
    SideEffect { HomeAppsBridge.apps = appsById; HomeAppsBridge.launch = onLaunchFrom }
    // A large folder whose Home widget is gone takes its contents with it.
    LaunchedEffect(state.widgetPlacements, WidgetStacks.stacks) {
        val placedSlots = state.widgetPlacements.map { it.slot }.toSet()
        WidgetData.prune(state.widgetPlacements.filter { UnoWidgets.byId(it.id) != null }.map { it.slot }.toSet() +
            WidgetStacks.unoMemberSlots(placedSlots))
        LargeFolders.prune(state.widgetPlacements.filter { it.id == FOLDER_WIDGET }.map { it.slot }.toSet()) }
    val drag = remember { HomeDragState() }
    val folderOwnsInput = openFolderId != null || drag.source?.folderId != null
    DisposableEffect(folderOwnsInput) {
        if (folderOwnsInput) LiveDiscover.setExternalResultPending(launcherActivity, "main", "folder-panel", true)
        onDispose { if (folderOwnsInput) LiveDiscover.setExternalResultPending(launcherActivity, "main", "folder-panel", false) }
    }
    val haptic = LocalHapticFeedback.current
    val homePages = state.homePages
    val pendingNewPage = widgets.pendingPlacement?.page == homePages
    val visibleHomePages = homePages + if (drag.active || widgetSession != null || pendingNewPage) 1 else 0
    var expandedWorkspace by remember { mutableStateOf(false) }
    // The leading slot always exists. It hosts Google Discover only where both Window
    // extensions and the Google app are present (on GrapheneOS the latter usually isn't); the
    // local news feed owns it everywhere else, and wherever a configured feed is preferred.
    // With nothing configured the feed page is the one-tap, opt-in GrapheneOS suggestion page.
    val context = LocalContext.current
    val googleInstalled = remember(context) {
        context.packageManager.getLaunchIntentForPackage(DiscoverClient.GOOGLE_PACKAGE) != null
    }
    val discoverAvailable = DiscoverBounds.available && googleInstalled
    // Google Discover is no longer a default or an option: the feed page always owns the leading slot.
    val feedVisible = true
    val firstHome = 1
    val pageCount = visibleHomePages + 1
    val nativePager = rememberPagerState(initialPage = savedPage.coerceIn(-firstHome, pageCount - 1) + firstHome, pageCount = { pageCount + firstHome })
    val pager = remember(nativePager, firstHome) { LauncherPager(nativePager, firstHome) }
    // The feed page and Google's window must never own the slot at the same time.
    LaunchedEffect(feedVisible) { LiveDiscover.setFeedOwnsSlot(feedVisible) }
    LaunchedEffect(feed.configured, feedVisible) {
        // The leading page no longer appears when the first feed is added, so there is no page
        // shift to compensate for: following a suggestion leaves the user on the feed page.
        if (feedVisible && pager.settledPage == -1) onFeedVisible()
    }
    LaunchedEffect(feedSetupRequests) {
        if (feedSetupRequests > 0) { customizationPage = CustomizationPage.FEED; sheet = "settings" }
    }
    val feedVisibleState = rememberUpdatedState(feedVisible)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect {
            if (it == -1 && feedVisibleState.value) onFeedVisible()
        }
    }
    fun leaveTemporaryWidgetPage() {
        val persistedPages = model.state.value.homePages
        if (pager.currentPage >= persistedPages)
            pager.requestScrollToPage((persistedPages - 1).coerceAtLeast(0))
    }
    var priorPendingPlacement by remember { mutableStateOf<WidgetPlacement?>(null) }
    LaunchedEffect(widgets.pendingPlacement, state.layout) {
        val pending = widgets.pendingPlacement
        if (pending != null) priorPendingPlacement = pending
        else priorPendingPlacement?.let { prior ->
            if (model.placement(prior.slot) == null && prior.page >= homePages) leaveTemporaryWidgetPage()
            priorPendingPlacement = null
        }
    }
    val pageGestures = remember(nativePager) { PageGestureLimits(nativePager) }
    SideEffect { pageGestures.editing = drag.active || widgetSession != null || resizeSlot != null; LiveDiscover.allowNativeOpen = !feedVisible && pager.currentPage == 0 && !drag.active && widgetSession == null && resizeSlot == null }
    val pageFling = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(nativePager, pagerSnapDistance = pageGestures)
    var nativeMotion by remember { mutableStateOf(false) }
    DisposableEffect(nativePager) {
        val callback: (Float) -> Unit = { progress ->
            val scrolling = nativePager.isScrollInProgress
            if (DuoMotionTrace.enabled) DuoMotionTrace.event("native_callback_received",
                "progress=$progress scrolling=$scrolling nativeMotion=$nativeMotion current=${nativePager.currentPage} offset=${nativePager.currentPageOffsetFraction}")
            if (!scrolling || nativeMotion) {
                val priorNativeMotion = nativeMotion
                nativeMotion = progress > 0f && progress < 1f
                val position = 1f - progress
                val page = position.roundToInt()
                if (DuoMotionTrace.enabled) DuoMotionTrace.event("native_callback_accepted",
                    "progress=$progress nativeMotion=$priorNativeMotion->$nativeMotion requestPage=$page requestOffset=${position - page}")
                nativePager.requestScrollToPage(page, position - page)
            } else if (DuoMotionTrace.enabled) DuoMotionTrace.event("native_callback_rejected",
                "progress=$progress reason=compose_scrolling nativeMotion=$nativeMotion")
        }
        LiveDiscover.onNativeProgress = callback
        onDispose { if (LiveDiscover.onNativeProgress === callback) LiveDiscover.onNativeProgress = null }
    }
    LaunchedEffect(nativePager) {
        snapshotFlow { Triple((1f - nativePager.currentPage - nativePager.currentPageOffsetFraction).coerceIn(0f, 1f), nativePager.isScrollInProgress, nativeMotion) to (nativePager.targetPage < firstHome) }
            .collect { (motion, towardFeed) ->
                val (progress, scrolling, native) = motion
                if (firstHome > 0 && !feedVisible) {
                    if (DuoMotionTrace.enabled) DuoMotionTrace.event("pager_observer",
                        "progress=$progress scrolling=$scrolling nativeMotion=$native towardFeed=$towardFeed")
                    if (scrolling) {
                        if (nativeMotion && DuoMotionTrace.enabled) DuoMotionTrace.event("native_owner_cleared",
                            "reason=compose_scrolling progress=$progress")
                        nativeMotion = false
                        LiveDiscover.page(progress, true, towardFeed)
                    } else if (!native) LiveDiscover.page(progress, false)
                }
            }
    }
    val scope = rememberCoroutineScope()
    DisposableEffect(pager) {
        val callback = { scope.launch { pager.animateScrollToPage(0) }; Unit }
        LiveDiscover.onHomeRequest = callback
        onDispose { if (LiveDiscover.onHomeRequest === callback) LiveDiscover.onHomeRequest = null }
    }
    var previousHomePages by remember { mutableIntStateOf(homePages) }
    var previousEditRevision by remember { mutableIntStateOf(state.editRevision) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(pager, homePages) {
        snapshotFlow { pager.settledPage to drag.active }.distinctUntilChanged().collect { (page, moving) ->
            if (!moving) { savedPage = page; if (page in 0 until homePages) lastHomePage = page }
        }
    }
    LaunchedEffect(homePages, state.editRevision) {
        if (homePages != previousHomePages && !drag.active) {
            // Pin edits in the library keep the library selected; a completed drop stays on home.
            if (state.editRevision == previousEditRevision) {
                if (pager.currentPage == previousHomePages) pager.scrollToPage(homePages)
                else if (pager.currentPage >= pageCount) pager.scrollToPage(homePages - 1)
            } else if (pager.currentPage >= homePages) pager.scrollToPage(homePages - 1)
        }
        previousHomePages = homePages
        previousEditRevision = state.editRevision
    }
    LaunchedEffect(pager.settledPage) { if (pager.settledPage != homePages) focus.clearFocus() }
    LaunchedEffect(state.verticalStatus) { onStatusMode(state.verticalStatus) }
    LaunchedEffect(homeRequests) { if (homeRequests > 0) {
        // An app can pause Home after the destination is visible but before its settle completes.
        val page = pager.currentPage.takeIf { it in 0 until homePages }
            ?: lastHomePage.coerceIn(0, homePages - 1)
        drag.clear(); widgetSession = null; resizeSlot = null; sheet = ""; widgetPackage = null
        widgetExactTarget = false; widgetPlacementMessage = null; selectedId = null; appMoveMenu = false
        openFolderId = null; createFolderFirstId = null; emptyCellIndex = null
        focus.clearFocus(); keyboard?.hide()
        pager.animateScrollToPage(page)
    } }
    LaunchedEffect(customizeRequests) { if (customizeRequests > 0) { island.collapse(); customizationPage = CustomizationPage.OVERVIEW; sheet = "settings" } }
    LaunchedEffect(searchRequests) { if (searchRequests > 0) { drag.clear(); widgetSession = null; resizeSlot = null; sheet = ""; widgetPackage = null; widgetExactTarget = false; selectedId = null
        if (!state.googleSearch || !onGoogleSearch(null)) pager.animateScrollToPage(homePages)
    } }
    val widgetPickerBack = {
        if (widgetSession != null) {
            leaveTemporaryWidgetPage(); widgetSession = null; widgetPlacementMessage = null
        } else {
            sheet = ""; widgetPackage = null; widgetExactTarget = false; widgetPlacementMessage = null
        }
    }
    var controlPanelOpen by remember { mutableStateOf(false) }
    var spotlightOpen by remember { mutableStateOf(false) }
    // The island reads this process-wide flag (through IslandEventArbiter) so a notification never auto-expands it
    // while Spotlight is open; see the "no auto-expand" rule in docs/design.md's Metaphor policy.
    LaunchedEffect(spotlightOpen) { IslandRuntime.spotlightOpen = spotlightOpen }
    BackHandler(enabled = spotlightOpen) { spotlightOpen = false }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { spotlightOpen = false }
    // A soft tick as each page settles (not on the first composition).
    LaunchedEffect(Unit) { snapshotFlow { pager.settledPage }.drop(1).collect { UnoFeedback.play(Cue.PAGE, haptic) } }
    // Themed icons are baked into bitmaps, so a style change (or, while themed, a light/dark flip) rebuilds them.
    val iconStyleNow = extras?.store?.state?.iconStyle ?: IconStyle.ORIGINAL
    val themedDarkNow = iconStyleNow.recolours && appearance.dark
    var iconsSeeded by remember { mutableStateOf(false) }
    val accentKey = if (iconStyleNow.usesWallpaper(appearance.wallpaperColor)) LauncherBackgroundCache.revision.intValue else -1
    val iconPackNow = extras?.store?.state?.iconPack.orEmpty()
    val iconLightingNow = extras?.store?.state?.iconLighting ?: true
    LaunchedEffect(iconStyleNow, themedDarkNow, accentKey, iconPackNow, iconLightingNow) { if (iconsSeeded) model.refresh() else iconsSeeded = true }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { controlPanelOpen = false }
    BackHandler(enabled = controlPanelOpen) { controlPanelOpen = false }
    BackHandler(enabled = sheet == "widgets") { widgetPickerBack() }
    BackHandler(enabled = sheet.isEmpty()) { if (resizeSlot != null) resizeSlot = null else if (drag.active) {
        val destination = if (drag.source?.target is DropTarget.Library) homePages else drag.originPage.coerceAtMost(homePages - 1)
        drag.clear(); scope.launch { pager.scrollToPage(destination) }
    } else if (selectedId != null) selectedId = null else { focus.clearFocus(); scope.launch { pager.animateScrollToPage(0) } } }
    val editActions = remember(model) { HomeEditActions { app -> model.setPinned(app.id, false) } }
    BackHandler(enabled = homeEditing && sheet.isEmpty() && !drag.active && selectedId == null && resizeSlot == null) { homeEditing = false }
    val openDiscover = { if (firstHome > 0) scope.launch { pager.animateScrollToPage(-1) } else onDiscover(); Unit }
    val openLibrary = { scope.launch { pager.animateScrollToPage(homePages) }; Unit }

    val dragWindowPage = if (expandedWorkspace && (drag.active || widgetSession != null)) pager.settledPage else pager.currentPage
    val eligibleDragPages = remember(expandedWorkspace, dragWindowPage, visibleHomePages) {
        if (expandedWorkspace && dragWindowPage in 0 until visibleHomePages) {
            setOfNotNull((dragWindowPage - 1).takeIf { it >= -1 }, dragWindowPage)
        } else setOf(dragWindowPage)
    }
    // Everything that follows the finger. The pointer moves on every touch event, and reading it directly here made this whole screen
    // recompose for each one while an app was being dragged. Reading it inside derivedStateOf means the screen is recomposed only when
    // the answer (what is under the finger, and whether a drop would make a folder) changes.
    val eligiblePagesNow = rememberUpdatedState(eligibleDragPages)
    val dragTargets by remember { derivedStateOf {
        val active = drag.active
        val region = if (active) drag.destination(drag.pointer, eligiblePagesNow.value) else null
        val raw = region?.target
        val adjusted = if (raw is DropTarget.Home && drag.source?.target is DropTarget.Widget) {
            val slot = (drag.source!!.target as DropTarget.Widget).index
            model.placement(slot)?.let {
                DropTarget.Home(adjustedWidgetDropIndex(raw.index, it, drag.source!!.bounds, drag.origin))
            } ?: raw
        } else raw
        val folderApp = if (active && drag.moved) folderDropTarget(region, drag.pointer, drag.source, ::isFolderId) else null
        // A widget held over the middle of another widget would stack onto it.
        val stackSlot = if (active && drag.moved) (drag.source?.target as? DropTarget.Widget)?.let { src ->
            drag.regions.values.firstOrNull { r ->
                val t = r.target as? DropTarget.Widget
                t != null && t.index != src.index && r.page in eligiblePagesNow.value && widgetCentre(r.bounds).contains(drag.pointer) &&
                    canStackWidget(model.placement(src.index), model.placement(t.index), widgets)
            }?.let { (it.target as DropTarget.Widget).index }
        } else null
        DragTargets(raw, adjusted, folderApp, stackSlot)
    } }
    val rawTarget = dragTargets.raw
    val target = dragTargets.target
    // A tick each time the dragged icon crosses into a different Home cell, the reorder feel of iOS. The drag must
    // actually be moving (not the initial placement), and only a Home-cell change fires, so a drag within one cell stays quiet.
    val dragCell = (target as? DropTarget.Home)?.index
    LaunchedEffect(dragCell) {
        if (drag.active && drag.moved && dragCell != null) UnoFeedback.play(Cue.DETENT, haptic)
    }
    val blockedDock = drag.moved && target is DropTarget.Dock &&
        if (drag.source?.folderId != null) state.dock.none { it == null }
        else drag.source?.appId?.let { !canPlaceInDock(state.layout, it) } == true
    // Held over the middle of another app: the drop makes a folder, so nothing reflows out of the way.
    val folderTargetApp = dragTargets.folderApp
    SideEffect { drag.folderIntentApp = folderTargetApp }
    // Held there for a moment, the widget will stack (as on iOS): a quick pass over a widget just moves it as before.
    var stackIntent by remember { mutableStateOf<Int?>(null) }
    val stackCandidate = dragTargets.stackSlot
    LaunchedEffect(stackCandidate) {
        stackIntent = null
        if (stackCandidate != null) {
            delay(STACK_DWELL_MS)
            stackIntent = stackCandidate
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    SideEffect { drag.stackIntentSlot = stackIntent }
    val insertionTarget = target.takeIf { drag.moved && !blockedDock && folderTargetApp == null && stackIntent == null }
    val widgetRawTarget = widgetSession?.let { session -> drag.regions.values.firstOrNull {
        it.target is DropTarget.Home && it.page in eligibleDragPages && it.bounds.contains(session.pointer)
    }?.target as? DropTarget.Home }
    val widgetDraft = widgetSession?.let { session -> session.candidate ?: widgetRawTarget?.let { cell ->
        widgetCandidate(state.layout, session.slot, session.targetIndex ?: cell.index, session.span.width, session.span.height)
    } ?: session.targetIndex?.let { widgetCandidate(state.layout, session.slot, it, session.span.width, session.span.height) } }
    val dropHomePage = if (pager.currentPage >= visibleHomePages)
        lastHomePage.coerceIn(0, homePages - 1) else pager.currentPage.coerceIn(0, homePages)
    val previewLayout = remember(state.layout, drag.source, insertionTarget, drag.moved) {
        val id = drag.source?.appId
        when {
            id != null && insertionTarget is DropTarget.Home -> dropApp(state.layout, id, insertionTarget)
            id != null && insertionTarget is DropTarget.Dock -> dropApp(state.layout, id, insertionTarget)
            drag.source?.target is DropTarget.Widget && insertionTarget is DropTarget.Home ->
                moveWidget(state.layout, (drag.source!!.target as DropTarget.Widget).index, insertionTarget.index)
            else -> state.layout
        }
    }
    val edgeWidthNow = rememberUpdatedState(with(LocalDensity.current) { 30.dp.toPx() })
    val edgeActive = (drag.active && drag.moved) || widgetSession?.dragging == true
    // Which screen edge the finger is on (turns the page after a hold); derived for the same reason as dragTargets above.
    val edgeNow by remember { derivedStateOf {
        val session = widgetSession
        val active = (drag.active && drag.moved) || session?.dragging == true
        if (!active) 0 else dragEdgeDirection(session?.takeIf { it.dragging }?.pointer ?: drag.pointer, drag.rootBounds, edgeWidthNow.value)
    } }
    val edge = edgeNow
    LaunchedEffect(edgeActive, edge) {
        if (edge != 0) while (drag.active || widgetSession?.dragging == true) {
            delay(650)
            val next = (pager.currentPage + edge).coerceIn(0, homePages)
            if ((!drag.active && widgetSession?.dragging != true) || next == pager.currentPage) break
            // Do not key this effect on currentPage: it changes halfway through the
            // animation and would cancel the turn before the inner grid is visible.
            // Once the hold commits a turn, finish its animation while the finger moves
            // into the incoming page. Leaving the edge cancels only the next hold timer.
            scope.launch { pager.animateScrollToPage(next) }.join()
        }
    }
    // Folds the widget in [otherSlot] into the stack behind [baseSlot]. An Android widget is remembered as a member before its grid spot is
    // removed (so it is not unbound); an Uno widget joins under a code that keeps its slot, so its saved data goes with it.
    fun mergeWidgetIntoStack(baseSlot: Int, otherSlot: Int): Boolean {
        val other = model.placement(otherSlot) ?: return false
        return if (other.id >= 0) WidgetStacks.add(baseSlot, other.id).also { if (it) widgets.remove(other.slot) }
        else WidgetStacks.add(baseSlot, StackRules.unoCode(other.id, other.slot)).also { if (it) model.removePlacement(DropTarget.Widget(other.slot)) }
    }
    fun finishDrag(cancelled: Boolean) {
        val source = drag.source ?: return
        val stackOnto = if (drag.moved && !cancelled && source.target is DropTarget.Widget) drag.stackIntentSlot else null
        val moved = drag.moved
        val rawDestination = if (moved && !cancelled) drag.destination(drag.pointer, eligibleDragPages)?.target else null
        val destination = if (rawDestination is DropTarget.Home && source.target is DropTarget.Widget) {
            model.placement(source.target.index)?.let {
                DropTarget.Home(adjustedWidgetDropIndex(rawDestination.index, it, source.bounds, drag.origin))
            }
                ?: rawDestination
        } else rawDestination
        // An app dropped on a large folder goes into it (and leaves its Home or dock spot, as it would into an ordinary folder).
        val largeFolderSlot = if (moved && !cancelled && source.appId != null && !isFolderId(source.appId) && source.folderId == null &&
            (source.target is DropTarget.Home || source.target is DropTarget.Dock || source.target is DropTarget.Library))
            drag.regions.values.firstOrNull { it.target is DropTarget.Widget && it.widgetId == FOLDER_WIDGET && it.page in eligibleDragPages &&
                it.bounds.contains(drag.pointer) }?.let { (it.target as DropTarget.Widget).index } else null
        var madeFolderId: String? = null
        val folderOntoApp = if (moved && !cancelled && destination is DropTarget.Home)
            folderDropTarget(drag.destination(drag.pointer, eligibleDragPages), drag.pointer, source, ::isFolderId) else null
        val changed = when {
            stackOnto != null && source.target is DropTarget.Widget -> mergeWidgetIntoStack(stackOnto, source.target.index)
            largeFolderSlot != null && source.appId != null && LargeFolders.add(largeFolderSlot, source.appId) -> {
                if (source.target is DropTarget.Home || source.target is DropTarget.Dock) model.removePlacement(source.target)
                true
            }
            folderOntoApp != null && destination is DropTarget.Home && source.folderId == null -> {
                val name = suggestedFolderName(context, listOfNotNull(appsById[folderOntoApp], appsById[source.appId]))
                madeFolderId = model.createFolder(folderOntoApp, source.appId ?: "", destination.index, name)
                madeFolderId != null
            }
            source.folderId != null && destination is DropTarget.Folder ->
                model.addAppToFolder(destination.id, source.appId ?: "")
            source.folderId != null && destination != null && source.appId != null ->
                model.removeAppFromFolder(source.folderId, source.appId, destination)
            destination == DropTarget.Remove -> model.removePlacement(source.target)
            destination is DropTarget.Home && source.target is DropTarget.Widget -> model.moveWidgetTo(source.target.index, destination.index)
            destination != null && source.appId != null -> model.applyDrop(source.appId, destination)
            else -> false
        }
        val returnToLibrary = source.target is DropTarget.Library && source.folderId == null && !changed
        val destinationHomePage = (destination as? DropTarget.Home)?.index?.let(::homeCellPage)
        val currentWindow = pager.settledPage.coerceIn(0, visibleHomePages - 1)
        val page = when (destination) {
            is DropTarget.Home -> if (expandedWorkspace && homeCellPage(destination.index) in eligibleDragPages) currentWindow else destinationHomePage!!
            is DropTarget.Dock -> dropHomePage
            is DropTarget.Widget -> 0
            else -> if (source.target is DropTarget.Library) pager.currentPage else drag.originPage
        }
        scope.launch {
            // Let a new home page compose before removing the temporary drop page.
            withFrameNanos { }
            drag.clear()
            withFrameNanos { }
            pager.scrollToPage(if (returnToLibrary) model.state.value.homePages else page.coerceIn(0, model.state.value.homePages - 1))
            if (moved && !cancelled && madeFolderId != null) { newFolderId = madeFolderId; openFolderId = madeFolderId }
            if (!moved && !cancelled) {
                if (source.target is DropTarget.Dock) { dockSlot = source.target.index; sheet = "dock" }
                else if (source.target is DropTarget.Widget) { widgetSlot = source.target.index; sheet = "widgetActions" }
                else if (source.appId?.let(::isFolderId) == true) openFolderId = source.appId
                else if (source.folderId == null) selectedId = source.appId
            }
        }
    }

    val homeLayer = rememberGraphicsLayer()
    DisposableEffect(homeLayer) {
        homeLayer.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
        LiveDiscover.homeLayer = homeLayer
        onDispose { if (LiveDiscover.homeLayer === homeLayer) LiveDiscover.homeLayer = null }
    }
    Box(Modifier.fillMaxSize().graphicsLayer {
        // The feed frame reuses the pager's render nodes in another window. Give Main
        // a complete render target so cross-window damage cannot erase stationary controls.
        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
    }.onSizeChanged { LiveDiscover.fullSize = androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }.testTag("launcher-root").homeDragInput(drag,
        enabled = sheet.isEmpty() && !showFirstRun && selectedId == null && resizeSlot == null && pager.currentPage >= 0,
        page = pager.currentPage, eligiblePages = eligibleDragPages, instant = homeEditing, onStart = {
            focus.clearFocus(); keyboard?.hide(); UnoFeedback.play(Cue.LONG_PRESS, haptic)
            if (drag.source?.folderId != null) openFolderId = null
            if (drag.source?.target is DropTarget.Library) scope.launch {
                withFrameNanos { }
                pager.scrollToPage(lastHomePage.coerceIn(0, homePages - 1))
            }
        },
        onFinish = { cancelled -> finishDrag(cancelled) })) {
        val homeBackdrop = rememberHomeBackdrop()
        val glassEnabled = appearance.liquidGlass
        val glassSettings = GlassSettings(appearance.refractionHeight, appearance.refractionAmount, appearance.refractionChroma)
        // With "Color from wallpaper" on, the glass takes the wallpaper's hue as well as the muted palette tint.
        val wallpaperAccent = LocalWallpaperAccent.current
        val glassTint = (if (glassEnabled) rememberGlassTint(Glass) else Glass).let { base ->
            if (wallpaperAccent != null) androidx.compose.ui.graphics.lerp(base, wallpaperAccent.glass, .6f) else base
        }
        val pageGlass = remember(glassEnabled, homeBackdrop, glassTint, glassSettings) {
            if (glassEnabled) PageGlass(homeBackdrop.wallpaper, glassTint, glassSettings) else null
        }
        // Controls stacked over the pager (search, back to Home) refract the whole Home view.
        val controlGlass = remember(glassEnabled, homeBackdrop, glassTint, glassSettings) {
            if (glassEnabled) PageGlass(homeBackdrop.combined, glassTint, glassSettings) else null
        }
        // Depth: the wallpaper is drawn a little larger than the screen and drifts the other way as the pages scroll, so it sits behind
        // them. It is recorded for the glass after the drift, so the glass bends what is really on screen.
        val animationsOn = remember { android.provider.Settings.Global.getFloat(launcherActivity.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
        val driftOn = appearance.wallpaperDepth && animationsOn
        Box(Modifier.matchParentSize().then(if (glassEnabled) Modifier.recordBackdrop(homeBackdrop.wallpaper) else Modifier)) {
            Box(Modifier.matchParentSize().graphicsLayer {
                if (driftOn) {
                    val pages = nativePager.pageCount
                    val position = nativePager.currentPage + nativePager.currentPageOffsetFraction
                    val drift = WallpaperDepth.drift(position, pages, size.width)
                    scaleX = drift.scale; scaleY = drift.scale; translationX = drift.translationX
                }
            }) { DuneWallpaper() }
        }
        // Home is deliberately IME-agnostic: it pads for the system bars and the display cutout
        // only, never for the soft keyboard. Spotlight's panel and the bottom glass consume the
        // IME themselves (and track it smoothly), so a keyboard never reflows the pager, dock,
        // rail, icons or wallpaper.
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
            val wide = maxWidth.value >= 650f
            val preset = if (wide) state.expanded else state.compact
            val density = LocalDensity.current
            val inLibrary = pager.currentPage == visibleHomePages
            var statusHeight by remember { mutableFloatStateOf(0f) }
            val geometry = homeGeometry(maxWidth.value, maxHeight.value, preset, state.labels,
                statusHeight = if (state.verticalStatus) statusHeight + 22f else 0f,
                labelHeight = with(density) { 14.sp.toDp().value } + 6f, inLibrary = inLibrary,
                homeBottomSpace = PageIndicatorLayout.reserveDp(isDefaultHome))
            SideEffect {
                resizePitchX = with(density) { (geometry.gridWidth / GRID_COLUMNS).dp.toPx() }
                resizePitchY = with(density) { minOf((geometry.widgetHeight + 18f) / 2f, geometry.rowHeight).dp.toPx() }
                resizeTopPitch = with(density) { ((geometry.widgetHeight + 18f) / 2f).dp.toPx() }
                resizeAppPitch = with(density) { geometry.rowHeight.dp.toPx() }
            }
            LaunchedEffect(geometry.gridWidth, geometry.widgetHeight, geometry.rowHeight) { resizeSlot = null }
            SideEffect { expandedWorkspace = geometry.expanded }
            LaunchedEffect(geometry.expanded) {
                if (!geometry.expanded) {
                    val sessionTargetsLeading = widgetSession?.let { session ->
                        session.candidate?.page == -1 || session.targetIndex?.let(::homeCellPage) == -1
                    } == true
                    val savedTargetLeading = widgetTargetIndex != Int.MIN_VALUE && homeCellPage(widgetTargetIndex) == -1
                    if (sessionTargetsLeading || savedTargetLeading) {
                        widgetSession = null
                        widgetTargetIndex = Int.MIN_VALUE
                        widgetExactTarget = false
                        widgetPackage = null
                        widgetProfileSerial = null
                        widgetPlacementMessage = null
                        sheet = ""
                    }
                    val dragTouchesLeading = drag.source?.page == -1 ||
                        ((target as? DropTarget.Home)?.index?.let(::homeCellPage) == -1)
                    if (dragTouchesLeading) {
                        drag.clear()
                    }
                }
            }
            val contentHeight = maxHeight
            val panelWidth = maxWidth - geometry.homeWidth.dp
            val pagerWidth = maxWidth - preset.dockWidth.dp - 28.dp
            // Home runs the pager under the dock and status rail, iOS-style, so page content
            // slides beneath the glass during swipes and the lens has edges to bend. Compact
            // pages inset their content by this much, so the resting layout is unchanged.
            // Expanded widens only the workspace's clip: its gesture pager and
            // WorkspacePageMotion stay on pagerWidth, and panes keep their pagerWidth sizes.
            val pagerEndInset = maxWidth - pagerWidth
            val leftColumnOrigin = (maxWidth / 2f - geometry.gridWidth.dp) / 2f - 16.dp
            val homeStride = panelWidth - leftColumnOrigin
            val bottomSpace = PageIndicatorLayout.reserveDp(isDefaultHome).dp
            val workspaceMotion = if (geometry.expanded) remember(firstHome, visibleHomePages, pagerWidth, homeStride, density) {
                WorkspacePageMotion(firstHome, visibleHomePages, with(density) { pagerWidth.toPx() }, with(density) { homeStride.toPx() })
            } else null
            val dockScroll = rememberScrollState()
            var gestureOriginInRoot by remember { mutableStateOf(Offset.Zero) }
            // Where the page-dots strip sits, so a press on it scrubs pages instead of dragging the pager.
            var pageStripBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
            var gestureOriginInWindow by remember { mutableStateOf(Offset.Zero) }
            val pagerInputEnabled = pager.currentPage in -firstHome..visibleHomePages && !drag.active &&
                widgetSession == null && resizeSlot == null && sheet.isEmpty() && !showFirstRun && selectedId == null &&
                openFolderId == null && emptyCellIndex == null && createFolderFirstId == null &&
                launcherActivity.backups.preview == null && !launcherActivity.backups.pickerPending &&
                !launcherActivity.backgrounds.pickerPending && widgets.setupStatus == null &&
                widgets.reconfigureWidgetId == null && !controlPanelOpen && !spotlightOpen
            Box(Modifier.fillMaxSize().onGloballyPositioned {
                gestureOriginInRoot = it.boundsInRoot().topLeft
                gestureOriginInWindow = it.boundsInWindow().topLeft
            }.onePageGestures(
                nativePager,
                pageGestures,
                motion = workspaceMotion,
                enabled = pagerInputEnabled,
                // Positive IDs are provider-owned Android views. Leave their vertical
                // stream untouched so scrollable widgets retain native gesture handling.
                // A dock that is already scrolled also gets first use of a downward drag.
                canStartDownwardSwipe = { point ->
                    if (pager.currentPage !in 0 until visibleHomePages) false else {
                        val region = drag.hit(point + gestureOriginInRoot, eligibleDragPages)
                        val rootOnScreen = IntArray(2).also(launcherRootView::getLocationOnScreen)
                        val screenPoint = point + gestureOriginInWindow +
                            Offset(rootOnScreen[0].toFloat(), rootOnScreen[1].toFloat())
                        val windowPoint = point + gestureOriginInWindow
                        !(region?.target is DropTarget.Dock && dockScroll.value > 0) &&
                            // A stack takes a vertical swipe that starts on it (it switches widgets); sideways swipes still turn pages.
                            StackBodyRegistry.bounds.values.none { it.contains(point + gestureOriginInRoot) } &&
                            !nativeWidgetConsumesVerticalGesture(launcherRootView, screenPoint) &&
                            // A downward drag that starts on the island pill is the island's pull (the peek) alone, never also a
                            // launcher shade/panel/search pull (docs/design.md: one owner per event).
                            !IslandEventArbiter.pressOnIslandPill(IslandRuntime.state.restPill, windowPoint.x, windowPoint.y)
                    }
                },
                // The right-hand swipe opens the launcher's own control panel; Notifications still use the system shade.
                onDownwardSwipeAt = { panel, startFraction ->
                    // A detent tick the moment the pull crosses its activation threshold, before the panel opens.
                    UnoFeedback.play(Cue.DETENT, haptic)
                    when (PullDownRouting.route(extras?.store?.state?.pullDown ?: PullDown.SMART, panel, startFraction)) {
                        PullRoute.SEARCH -> { UnoFeedback.play(Cue.OPEN, haptic); spotlightOpen = true }
                        PullRoute.QUICK_SETTINGS ->
                            if ((extras?.store?.state?.rightSwipe ?: RightSwipe.PANEL) == RightSwipe.PANEL) { UnoFeedback.play(Cue.OPEN, haptic); controlPanelOpen = true }
                            else launcherActivity.openSystemShade(ShadePanel.QUICK_SETTINGS)
                        PullRoute.NOTIFICATIONS -> launcherActivity.openSystemShade(ShadePanel.NOTIFICATIONS)
                    }
                },
                onUpwardSwipe = { UnoFeedback.play(Cue.OPEN, haptic); island.collapse(); sheet = "settings" },
                onLeadingOverscroll = if (firstHome == 0) onDiscover else null,
                ignorePress = { point -> pageStripBounds.contains(point + gestureOriginInRoot) ||
                    StackRailRegistry.bounds.values.any { it.contains(point + gestureOriginInRoot) } ||
                    HorizontalScrollerRegistry.bounds.values.any { it.contains(point + gestureOriginInRoot) } },
            )) {
            val pagerModifier = Modifier.fillMaxHeight().width(pagerWidth + pagerEndInset)
                .drawWithContent {
                    homeLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(homeLayer)
                    LiveDiscover.host.get()?.invalidateFrame()
                }.testTag("app-pager")
                .then(if (glassEnabled) Modifier.recordBackdrop(homeBackdrop.pages) else Modifier)
                .discoverSwipe(firstHome == 0 && pager.currentPage == 0 && !drag.active && sheet.isEmpty() &&
                    !showFirstRun && selectedId == null, onDiscover)
                .onGloballyPositioned {
                    if (firstHome > 0 && !feedVisible) {
                        val bounds = it.boundsInWindow()
                        LiveDiscover.pagerOrigin = bounds.topLeft
                        val padding = 32 * density.density
                        // Discover's frame matches the visible page area, not the under-dock run.
                        val inset = with(density) { pagerEndInset.toPx() }
                        LiveDiscover.prepare(launcherActivity,
                            android.graphics.Rect((bounds.left + padding).toInt(), (bounds.top + padding).toInt(),
                                (bounds.right - inset - 16 * density.density).toInt(),
                                // Leave the page-dots strip clear, like every other page does.
                                (bounds.bottom - with(density) { bottomSpace.toPx() }).toInt()), bounds.width - inset)
                    }
                }
                .semantics { stateDescription = if (pager.currentPage == -1) if (feedVisible) "Feed" else "Discover" else if (pager.currentPage == visibleHomePages) "All apps" else "Home page ${pager.currentPage + 1} of $visibleHomePages" }
            if (geometry.expanded) {
                Box(pagerModifier) {
                    // PagerState remains the source of truth for native Discover progress,
                    // snapping, accessibility state, and programmatic page requests.
                    HorizontalPager(nativePager, Modifier.fillMaxHeight().width(pagerWidth), userScrollEnabled = false,
                        key = { if (it < firstHome) "discover" else if (it - firstHome == visibleHomePages) "library" else "home-${it - firstHome}" }) { }
                    CompositionLocalProvider(LocalPageGlass provides pageGlass, LocalHomeEditing provides homeEditing, LocalHomeEditActions provides editActions) { ExpandedWorkspace(
                        nativePager = nativePager, motion = workspaceMotion!!, firstHome = firstHome,
                        viewportWidth = pagerWidth, trailingOverscan = pagerEndInset,
                        visibleHomePages = visibleHomePages, panelWidth = panelWidth,
                        contentHeight = contentHeight, bottomSpace = bottomSpace, geometry = geometry,
                        state = state, previewSlots = previewLayout.slots, previewLeadingSlots = previewLayout.leadingSlots,
                        previewWidgetPlacements = previewLayout.widgetPlacements, appsById = appsById,
                        widgets = widgets, drag = drag, target = target, insertionTarget = insertionTarget,
                        feed = feed, feedVisible = feedVisible,
                        onFeedRefresh = onFeedRefresh, onFeedOpenEntry = onFeedOpenEntry,
                        onFeedToggleSave = onFeedToggleSave,
                        onFeedAdd = { customizationPage = CustomizationPage.FEED; sheet = "settings" },
                        glassBackdrop = homeBackdrop.wallpaper.takeIf { glassEnabled },
                        glassTint = glassTint.copy(alpha = .55f),
                        settings = glassSettings,
                        libraryQuery = libraryQuery, onLibraryQuery = { libraryQuery = it },
                        contacts = contactResults, onContact = { ContactsSearch.open(launcherActivity, it) }, handoff = handoffResolver,
                        onLaunch = onLaunch, onLaunchFrom = onLaunchFrom, onPinned = model::setPinned,
                        onTurnOnWork = { model.turnOnWork(it) },
                        onActions = { selectedId = it.id }, onWidget = { widgetSlot = it; sheet = "widgetActions" },
                        onFolder = { openFolderId = it },
                        onEmptyWidget = { emptyCellIndex = it },
                        onRefresh = model::refresh,
                    ) }
                }
            } else {
                HorizontalPager(nativePager, pagerModifier,
                    // Keep adjacent Home panes attached so ordinary back-and-forth paging does
                    // not synchronously inflate provider RemoteViews inside the gesture frame.
                    // Discover is two physical positions before Home 2. Retain both Home
                    // neighbors to avoid reinflating Home 2's RemoteViews during native exit.
                    beyondViewportPageCount = if (firstHome > 0) 2 else 1,
                    userScrollEnabled = !drag.active && resizeSlot == null, flingBehavior = pageFling,
                    key = { if (it < firstHome) "discover" else if (it - firstHome == visibleHomePages) "library" else "home-${it - firstHome}" }) { physicalPage ->
                    val page = physicalPage - firstHome
                    Box(Modifier.fillMaxSize().padding(end = pagerEndInset)) {
                    CompositionLocalProvider(LocalPageGlass provides pageGlass, LocalHomeEditing provides homeEditing, LocalHomeEditActions provides editActions) {
                    if (page == -1) {
                        DiscoverContent(Modifier.fillMaxSize().padding(start = 16.dp, top = 16.dp, bottom = bottomSpace),
                            feed, feedVisible, onFeedRefresh, onFeedOpenEntry,
                            onFeedToggleSave = onFeedToggleSave,
                            onAddFeed = { customizationPage = CustomizationPage.FEED; sheet = "settings" },
                            glassBackdrop = homeBackdrop.wallpaper.takeIf { glassEnabled },
                            glassTint = glassTint.copy(alpha = .55f),
                            settings = glassSettings)
                    } else if (page == visibleHomePages) {
                        AppLibrary(state, libraryQuery, { libraryQuery = it }, onLaunch, model::setPinned,
                            onActions = { selectedId = it.id }, modifier = Modifier.fillMaxSize().padding(start = 16.dp, top = 16.dp, bottom = bottomSpace).testTag("library-page"),
                            drag = drag, page = visibleHomePages, onLaunchFrom = onLaunchFrom, onTurnOnWork = { model.turnOnWork(it) },
                            contacts = contactResults, onContact = { ContactsSearch.open(launcherActivity, it) }, handoff = handoffResolver)
                    } else {
                        Row(Modifier.fillMaxSize().testTag("home-surface")) {
                            HomePagePane(page, state, previewLayout.slots, previewLayout.leadingSlots, previewLayout.widgetPlacements, appsById, geometry, contentHeight,
                                bottomSpace, widgets, drag, target, insertionTarget, showLargeWidget = false,
                                onLaunch = onLaunchFrom, onActions = { selectedId = it.id },
                                onWidget = { widgetSlot = it; sheet = "widgetActions" },
                                onFolder = { openFolderId = it },
                                onEmptyWidget = { emptyCellIndex = it },
                                onRefresh = model::refresh,
                                onDoubleTapEmpty = if (extras?.store?.state?.doubleTapLock == true) ({ lockFromHome(context) }) else null)
                        }
                    }
                    }
                    }
                }
            }
            // Soft blur toward the top and bottom edges, beneath the island, rail, dock and page dots.
            if (glassEnabled) {
                // The edge blur hugs the screen edges (system bars + cutout), never the keyboard.
                val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
                val topInset = insets.calculateTopPadding()
                val bottomInset = insets.calculateBottomPadding()
                ScrollEdgeBlur(homeBackdrop.combined, atTop = true, height = topInset + EdgeBlur.TOP_EXTRA,
                    modifier = Modifier.align(Alignment.TopStart).offset(y = -topInset))
                ScrollEdgeBlur(homeBackdrop.combined, atTop = false,
                    height = bottomInset + PageIndicatorLayout.reserveDp(isDefaultHome).dp,
                    modifier = Modifier.align(Alignment.BottomStart).offset(y = bottomInset))
            }
            if (state.verticalStatus) {
                val railInk = rememberAdaptiveInk(glassTint, if (glassEnabled) .12f else .0f)
                if (glassEnabled) Box(Modifier.align(Alignment.TopEnd).padding(end = 16.dp).offset(y = geometry.contentTop.dp)
                    .width(preset.dockWidth.dp).then(railInk.track)
                    .liquidGlass(homeBackdrop.combined, Corner.xlarge, glassTint.copy(alpha = .12f), blurRadius = .75f, settings = glassSettings)
                    .padding(vertical = 8.dp)) {
                    StatusRail(deviceStatus, Modifier.fillMaxWidth().onSizeChanged {
                        statusHeight = (with(density) { it.height.toDp().value } -
                            if (contentHeight < 500.dp) 0f else 23f).coerceAtLeast(0f)
                    }, compact = contentHeight < 500.dp, iconSize = dockIconSize(geometry.iconSize).dp, ink = railInk.color)
                } else StatusRail(deviceStatus,
                    Modifier.align(Alignment.TopEnd).padding(end = 16.dp).offset(y = geometry.contentTop.dp)
                        .width(preset.dockWidth.dp).then(railInk.track).onSizeChanged {
                            // The normal rail's 20dp location slot and 3dp gap do not move the dock.
                            statusHeight = (with(density) { it.height.toDp().value } -
                                if (contentHeight < 500.dp) 0f else 23f).coerceAtLeast(0f)
                        },
                    compact = contentHeight < 500.dp, iconSize = dockIconSize(geometry.iconSize).dp, ink = railInk.color)
            }
            if (glassEnabled) Box(Modifier.align(Alignment.TopEnd).padding(end = 16.dp).offset(y = geometry.dockTop.dp)
                .width(preset.dockWidth.dp).height(geometry.dockHeight.dp)
                .liquidGlass(homeBackdrop.combined, Corner.xlarge, glassTint.copy(alpha = .12f), blurRadius = .75f, settings = glassSettings)
                .graphicsLayer {
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                }.testTag("dock")) {
                Column(Modifier.padding(vertical = 8.dp).verticalScroll(dockScroll)) {
                    DockAppColumn(state.dock, previewLayout.dock, appsById, geometry.dockRowHeight,
                        dockIconSize(geometry.iconSize), drag, insertionTarget,
                        onLaunch = onLaunchFrom, onChoose = { dockSlot = it; sheet = "dock" }, glass = controlGlass)
                }
            } else Surface(Modifier.align(Alignment.TopEnd).padding(end = 16.dp).offset(y = geometry.dockTop.dp)
                .width(preset.dockWidth.dp).height(geometry.dockHeight.dp).graphicsLayer {
                    // Composite the stationary dock independently of the shared pager layer.
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                }.testTag("dock"),
                shape = Corner.xlarge, color = Glass.copy(alpha = .32f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .3f))) {
                Column(Modifier.padding(vertical = 8.dp).verticalScroll(dockScroll)) {
                    DockAppColumn(state.dock, previewLayout.dock, appsById, geometry.dockRowHeight,
                        dockIconSize(geometry.iconSize), drag, insertionTarget,
                        onLaunch = onLaunchFrom, onChoose = { dockSlot = it; sheet = "dock" })
                }
            }
            // The floating bottom glass tracks the keyboard's top edge frame-by-frame. The inset is
            // read inside each offset lambda (re-evaluated every frame as the IME slides), instead
            // of imePadding(), which only updates at the discrete start/end of the animation.
            val imeInset = WindowInsets.ime
            val navBarInset = WindowInsets.navigationBars
            Column(Modifier.align(Alignment.BottomStart).width(pagerWidth).offset {
                IntOffset(0, imeLiftPx(imeInset.getBottom(this), navBarInset.getBottom(this)))
            }.padding(start = 16.dp, bottom = PageIndicatorLayout.BOTTOM_MARGIN_DP.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!isDefaultHome) FilledTonalButton(onClick = { sheet = ""; onMakeDefault() }, Modifier.heightIn(min = 48.dp).testTag("home-setup")) {
                    Icon(Icons.Rounded.Home, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(com.jake.duolauncher.R.string.set_as_home_app))
                }
                // The page dots ride in a small glass capsule, like iOS's page indicator, with a
                // selection lens that follows the pager and the finger.
                PageStrip(pager, homePages, visibleHomePages, showCompass = !drag.active, glass = controlGlass,
                    onDiscover = openDiscover, onLibrary = openLibrary, onBounds = { pageStripBounds = it })
            }
            if (!inLibrary && !drag.active) Column(Modifier.align(Alignment.BottomEnd).offset {
                IntOffset(0, imeLiftPx(imeInset.getBottom(this), navBarInset.getBottom(this)))
            }.padding(end = 16.dp, bottom = 8.dp)
                .width(preset.dockWidth.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val controlSize = dockIconSize(geometry.iconSize).dp
                if (pager.currentPage == -1) CircleControl(Icons.AutoMirrored.Rounded.ArrowForward, "Back to home", "discover-home", controlSize, controlGlass) { scope.launch { pager.animateScrollToPage(0) } }
                val searchBounds = remember { android.graphics.Rect() }
                Box(Modifier.onGloballyPositioned { searchBounds.set(it.boundsInWindow().toAndroidBounds()) }) {
                    CircleControl(Icons.Rounded.Search, if (state.googleSearch) "Search Google" else "Search apps", "search", controlSize, controlGlass) {
                        if (!state.googleSearch || !onGoogleSearch(searchBounds)) openLibrary()
                    }
                }
            }
            if (homeEditing && sheet.isEmpty() && !drag.active && !controlPanelOpen && !spotlightOpen && !showFirstRun) {
                HomeEditBar(controlGlass,
                    onAddApps = { homeEditing = false; sheet = "pins" },
                    onAddWidget = {
                        val page = pager.currentPage.coerceIn(0, homePages - 1)
                        homeEditing = false; widgetSlot = model.nextWidgetSlot(); widgetTargetIndex = page * HOME_CELLS
                        widgetPackage = null; widgetProfileSerial = null; widgetExactTarget = false; sheet = "widgets"
                    },
                    onAddPage = { if (model.addHomePage()) scope.launch { pager.animateScrollToPage(model.state.value.homePages - 1) } },
                    onRemovePage = pager.currentPage.takeIf { it in 1 until homePages && state.layout.pageIsEmpty(it) }?.let { page ->
                        { model.removeHomePage(page); Unit }
                    },
                    onMovePageEarlier = pager.currentPage.takeIf { it in 1 until homePages }?.let { page ->
                        { if (model.moveHomePage(page, -1)) scope.launch { pager.animateScrollToPage(page - 1) }; Unit }
                    },
                    onMovePageLater = pager.currentPage.takeIf { it in 0 until homePages - 1 }?.let { page ->
                        { if (model.moveHomePage(page, 1)) scope.launch { pager.animateScrollToPage(page + 1) }; Unit }
                    },
                    onDone = { homeEditing = false }, modifier = Modifier.align(Alignment.TopCenter))
            }
            // The island is not composed while Spotlight is open, so a tap on the camera cutout can never expand it
            // underneath the search: the Spotlight scrim/panel owns that touch (it does nothing there).
            if (appearance.island && !islandEverywhere && sheet.isEmpty() && !showFirstRun && !drag.active && !controlPanelOpen && !spotlightOpen) {
                DynamicIsland(island, controlGlass, deviceStatus,
                    feedHeadline = feed.shownEntries.firstOrNull()?.title,
                    sizeScale = appearance.islandScale,
                    wideFactor = extras?.store?.state?.islandWideMode?.wideFactor ?: 1f,
                    belowStatusBar = true,
                    statusRailShown = state.verticalStatus,
                    resumeWindowMs = IslandPlayback.resumeWindowMs(extras?.store?.state?.mediaResumeMinutes ?: 2),
                    dockWidthPx = with(density) { preset.dockWidth.dp.toPx() },
                    onSearch = { island.collapse(); spotlightOpen = true },
                    onWebSearch = { query -> island.collapse(); handoffResolver.searchWeb(query) },
                    onOpenFeed = {
                        island.collapse()
                        if (pager.currentPage != -1) scope.launch { pager.animateScrollToPage(-1) }
                    },
                    onCustomize = { island.collapse(); customizationPage = CustomizationPage.OVERVIEW; sheet = "settings" })
                // Charging transitions flash through the island.
                LaunchedEffect(deviceStatus.charging) {
                    if (deviceStatus.charging == true) island.showCharging(deviceStatus.battery)
                }
            }
            if (sheet.isNotEmpty() && sheet != "widgets") {
                val activeCustomizationPage = if (sheet == "settings:wallpaper") CustomizationPage.APPEARANCE else customizationPage
                GlassModalSheet(controlGlass, onDismissRequest = {
                    sheet = ""; widgetPackage = null; widgetExactTarget = false
                }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
                    containerColor = MaterialTheme.colorScheme.surface) {
                    ModalDialogBackHandler {
                        if ((sheet == "settings" || sheet == "settings:wallpaper") &&
                            activeCustomizationPage != CustomizationPage.OVERVIEW) {
                            customizationPage = CustomizationPage.OVERVIEW
                            sheet = "settings"
                        } else {
                            sheet = ""; widgetPackage = null; widgetExactTarget = false
                        }
                    }
                    when (sheet) {
                        "dock" -> AppPicker(state.visibleApps, dockSlot,
                            onSelect = {
                                if (canPlaceInDock(state.layout, it.id)) {
                                    model.applyDrop(it.id, DropTarget.Dock(dockSlot)); sheet = ""
                                }
                            },
                            onClear = { model.removePlacement(DropTarget.Dock(dockSlot)) },
                            onLongClick = { selectedId = it.id; sheet = "" },
                            canSelect = { canPlaceInDock(state.layout, it.id) },
                            blockedHint = if (state.dock.none { it == null }) "Dock full • Move an app out first" else null)
                        "split" -> {
                            val first = splitFirstId?.let(appsById::get)
                            if (first == null) LaunchedEffect(Unit) { sheet = "" }
                            else AppPicker(state.visibleApps.filter { it.id != first.id }, null,
                                onSelect = { second -> sheet = ""; splitFirstId = null; launcherActivity.launchSplit(first, second) },
                                onClear = {}, onLongClick = {},
                                title = "Open ${first.label} with…")
                        }
                        "pins" -> Column(Modifier.fillMaxHeight(.9f).imePadding()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { sheet = "" }) { Text(stringResource(com.jake.duolauncher.R.string.done)) }
                            }
                            AppLibrary(state, pinQuery, { pinQuery = it }, onLaunch, model::setPinned,
                                onActions = { selectedId = it.id; sheet = "" }, editing = true, modifier = Modifier.weight(1f).fillMaxWidth(),
                                onTurnOnWork = { model.turnOnWork(it) })
                        }
                        "settings", "settings:wallpaper" -> Box(Modifier.nestedScroll(
                            SheetDismissGuard(with(LocalDensity.current) { SheetMotion.FLING_DISMISS_VELOCITY_DP.dp.toPx() })
                        )) { CustomizationSheet(state, wide, model, isDefaultHome,
                            page = activeCustomizationPage, onPage = { customizationPage = it; sheet = "settings" },
                            onMakeDefault = { sheet = ""; onMakeDefault() },
                            onClose = { sheet = "" }, onEditPins = { sheet = "pins" },
                            onWidget = { widgetSlot = it; widgetPackage = null; widgetProfileSerial = null; widgetExactTarget = false; sheet = "widgets" },
                            onAddWidget = { page -> widgetSlot = model.nextWidgetSlot(); widgetTargetIndex = page * HOME_CELLS; widgetPackage = null; widgetProfileSerial = null; widgetExactTarget = false; sheet = "widgets" },
                            onRemoveWidget = widgets::remove,
                            onExportLayout = { sheet = ""; launcherActivity.backups.startExport() },
                            onImportLayout = { sheet = ""; launcherActivity.backups.startImport() },
                            appearance = appearance, onAppearanceMode = onAppearanceMode,
                            onAppearanceManual = onAppearanceManual, onAppearanceDeviceLocation = onAppearanceDeviceLocation, onAppearanceCancelLocation = onAppearanceCancelLocation,
                            onAppearanceClear = onAppearanceClear,
                            onShadeSetup = { sheet = ""; onShadeSetup() },
                            extras = extras,
                            onWallpaperColor = onWallpaperColor,
                            onMaterialYou = onMaterialYou,
                            onWallpaperDepth = onWallpaperDepth,
                            onTiltHighlight = onTiltHighlight,
                            onTiltStrength = onTiltStrength,
                            backgrounds = launcherActivity.backgrounds,
                            onWallpaperPreview = { sheet = ""; onWallpaperPreview() }, homePage = pager.currentPage.coerceIn(0, homePages - 1),
                            feed = feed, onFeedRefresh = onFeedRefresh,
                            onAddFeed = onAddFeed, onRemoveFeed = onRemoveFeed, onSourceEnabled = onSourceEnabled,
                            onImportOpml = onImportOpml,
                            onKeepPerFeed = onKeepPerFeed,
                            // The glass sheet already supplies the surface; no second glass layer.
                            glassBackdrop = null,
                            glassTint = glassTint.copy(alpha = .62f),
                            settings = glassSettings,
                            onLiquidGlass = onLiquidGlass,
                            onRefractionHeight = onRefractionHeight,
                            onRefractionAmount = onRefractionAmount,
                            onRefractionChroma = onRefractionChroma,
                            onIsland = onIsland,
                            onIslandScale = onIslandScale,
                            islandEverywhere = islandEverywhere,
                            onIslandEverywhere = onIslandEverywhere,
                            updates = updates,
                            onCheckUpdates = onCheckUpdates,
                            onInstallRelease = onInstallRelease,
                            onAutoUpdate = onAutoUpdate) }
                        "widgetActions" -> model.placement(widgetSlot)?.let { placement ->
                            val topPitch = (geometry.widgetHeight + 18f) / 2f
                            val gridSizing = WidgetGridSizing(GRID_COLUMNS, GRID_ROWS, geometry.gridWidth / GRID_COLUMNS,
                                minOf(topPitch, geometry.rowHeight), maxOf(topPitch, geometry.rowHeight), 10f, 18f,
                                topRowHeightDp = topPitch, appRowHeightDp = geometry.rowHeight)
                            val constraints = if (placement.id == FOLDER_WIDGET) FOLDER_SPAN_CONSTRAINTS
                                else widgets.manager.getAppWidgetInfo(placement.id)?.let { widgets.sizing(it, gridSizing) }
                            WidgetActions(placement, constraints,
                                canConfigure = widgets.canReconfigure(placement.id),
                                onConfigure = { widgets.reconfigure(placement.id); sheet = "" },
                                isValid = { x, y -> (placement.id != FOLDER_WIDGET || x * y >= 2) &&
                                    ((x == placement.spanX && y == placement.spanY) || resizeWidget(state.layout, widgetSlot, x, y) != state.layout) },
                                onResize = { x, y -> model.resizeWidget(widgetSlot, x, y) },
                                onStartResize = { x, y ->
                                    resizeSlot = widgetSlot; resizeWidth = x; resizeHeight = y
                                    resizeConstraints = constraints; sheet = ""
                                },
                                onMoveToPage = { page ->
                                    (0 until HOME_CELLS).firstOrNull { local ->
                                        widgetCandidate(state.layout, placement.slot, page * HOME_CELLS + local,
                                            placement.spanX, placement.spanY) != null
                                    }?.let { model.moveWidgetTo(placement.slot, page * HOME_CELLS + it) } == true
                                }, homePages = homePages,
                                onReplace = {
                                    widgetPackage = null
                                    widgetProfileSerial = widgets.manager.getAppWidgetInfo(placement.id)?.profile?.let {
                                        launcherActivity.getSystemService(UserManager::class.java).getSerialNumberForUser(it)
                                    }?.takeIf { it >= 0 }
                                    widgetExactTarget = false; sheet = "widgets"
                                },
                                onRemove = { widgets.remove(widgetSlot); sheet = "" },
                                onClose = { sheet = "" },
                                onEditFolder = if (placement.id == FOLDER_WIDGET) {{ sheet = "largeFolder" }} else null,
                                stackMembers = WidgetStacks.members(widgetSlot).map { it to stackMemberLabel(it, widgets) },
                                onStackWith = if (state.widgetPlacements.any { it.slot != widgetSlot && stackable(it, widgets) }
                                    && WidgetStacks.members(widgetSlot).size < StackRules.MAX_MEMBERS) {{ sheet = "stackPick" }} else null,
                                onRemoveFromStack = { memberId ->
                                    // An Uno widget in the stack has no Android id to release: forgetting it is all there is to do, and
                                    // its saved data is pruned once nothing refers to its slot.
                                    WidgetStacks.remove(widgetSlot, memberId); if (!StackRules.isUno(memberId)) widgets.pruneUnusedIds()
                                })
                        }
                        "largeFolder" -> LargeFolderEditor(widgetSlot, state.apps) { sheet = "widgetActions" }
                        "stackPick" -> Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(com.jake.duolauncher.R.string.stack_which_widget), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                                IconButton(onClick = { sheet = "widgetActions" }) { Icon(Icons.Rounded.Close, "Cancel") }
                            }
                            Text(stringResource(com.jake.duolauncher.R.string.it_moves_into_this_widget_s),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            state.widgetPlacements.filter { it.slot != widgetSlot && stackable(it, widgets) }
                                .forEach { other ->
                                    ActionRow(Icons.Rounded.Widgets, "${widgetLabel(other.id, widgets)}  ·  page ${other.page + 1}", {
                                        // Remember it as a stack member first, so removing its grid spot doesn't unbind it.
                                        mergeWidgetIntoStack(widgetSlot, other.slot)
                                        sheet = ""
                                    }, Modifier.testTag("stack-pick-${other.slot}"))
                                }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
            if (showWhatsNew && !showFirstRun) CompositionLocalProvider(LocalPageGlass provides controlGlass) {
                WhatsNewDialog(UpdateSecurity.appVersion, onWhatsNewDone)
            }
            if (showFirstRun) {
                GlassModalSheet(controlGlass,
                    onDismissRequest = onFinishFirstRun,
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.testTag("first-run-setup"),
                ) {
                    FirstRunSetupSheet(
                        isDefaultHome = isDefaultHome,
                        onMakeDefault = onMakeDefault,
                        onAddWidget = {
                            onFinishFirstRun()
                            widgetSlot = model.nextWidgetSlot()
                            widgetTargetIndex = pager.currentPage.coerceIn(0, homePages - 1) * HOME_CELLS
                            widgetPackage = null
                            widgetProfileSerial = null
                            widgetExactTarget = false
                            sheet = "widgets"
                        },
                        onExplore = onFinishFirstRun,
                        onSkip = onFinishFirstRun,
                    )
                }
            }
            if (sheet == "widgets") {
                val catalogProfiles = remember(state.profiles) { state.profiles.filter { it.isPersonal || it.isWork } }
                val selectedProfile = catalogProfiles.firstOrNull { it.userSerial == widgetProfileSerial }
                    ?: catalogProfiles.firstOrNull { it.isPersonal } ?: AppProfile(0, "Personal", true, false, false, true, true)
                val userManager = remember(launcherActivity) { launcherActivity.getSystemService(UserManager::class.java) }
                val providers = remember(widgetPackage, selectedProfile, sheet, state.apps) {
                    val user = userManager.getUserForSerialNumber(selectedProfile.userSerial)
                    if (user == null || !selectedProfile.available || !selectedProfile.unlocked || selectedProfile.quiet) emptyList()
                    else runCatching { widgetPackage?.let { widgets.providersForPackage(it, user) }
                        ?: widgets.providers(user) }.getOrDefault(emptyList()).filter { provider ->
                        provider.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 &&
                            provider.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER == 0
                    }
                }
                val catalog by produceState<List<WidgetCatalogEntry>?>(null, providers, selectedProfile.userSerial, sheet) {
                    value = withContext(Dispatchers.IO) { widgetCatalog(launcherActivity, providers, selectedProfile) }
                }
                val topPitch = (geometry.widgetHeight + 18f) / 2f
                val pickerSizing = remember(geometry) { WidgetGridSizing(GRID_COLUMNS, GRID_ROWS,
                    geometry.gridWidth / GRID_COLUMNS, minOf(topPitch, geometry.rowHeight),
                    maxOf(topPitch, geometry.rowHeight), 10f, 18f,
                    topRowHeightDp = topPitch, appRowHeightDp = geometry.rowHeight) }
                val footprint: (AppWidgetProviderInfo) -> WidgetSpan? = { provider ->
                    widgets.sizing(provider, pickerSizing)?.takeIf { it.minimumFitsGrid }?.preferred
                }
                VisualWidgetPicker(catalog, catalogProfiles.ifEmpty { listOf(selectedProfile) }, selectedProfile, glass = controlGlass, previewGlass = pageGlass,
                    onSelectProfile = { widgetProfileSerial = it.userSerial; widgetPlacementMessage = null },
                    onTurnOnWork = { model.turnOnWork(it) }, hiddenForDrag = widgetSession != null,
                    footprint = footprint,
                    onBack = widgetPickerBack,
                    onTap = { provider ->
                        footprint(provider)?.let { preferredSpan ->
                            val existing = model.placement(widgetSlot)
                            val constraints = widgets.sizing(provider, pickerSizing)
                            val span = existing?.let { placement ->
                                WidgetSpan(placement.spanX, placement.spanY).takeIf {
                                    constraints != null && it.width in constraints.minimum.width..constraints.maximum.width &&
                                        it.height in constraints.minimum.height..constraints.maximum.height
                                }
                            } ?: preferredSpan
                            val special = existing?.takeIf { it.row + it.spanY > GRID_ROWS }
                            if (special != null) {
                                widgetSession = WidgetPickerSession(provider, widgetSlot,
                                    WidgetSpan(special.spanX, special.spanY), Offset.Zero,
                                    dragging = false, candidate = special)
                                widgetPlacementMessage = null
                                scope.launch { pager.scrollToPage(special.page.coerceAtLeast(0).coerceAtMost(homePages - 1)) }
                                return@let
                            }
                            val requestedIndex = existing?.let { homeCellIndex(it.page, it.row * GRID_COLUMNS + it.column) }
                                ?: widgetTargetIndex.takeUnless { it == Int.MIN_VALUE } ?: 0
                            val requestedPage = homeCellPage(requestedIndex).coerceIn(if (expandedWorkspace) -1 else 0, homePages)
                            val availablePages = (if (expandedWorkspace) -1 else 0)..homePages
                            val autoPages = (listOf(requestedPage) + availablePages.filter { it != requestedPage })
                            val freeIndex = if (existing != null || widgetExactTarget) requestedIndex.takeIf {
                                widgetCandidate(state.layout, widgetSlot, it, span.width, span.height) != null
                            } else autoPages.asSequence().flatMap { page ->
                                (0 until HOME_CELLS).asSequence().map { homeCellIndex(page, it) }
                            }.firstOrNull { widgetCandidate(state.layout, widgetSlot, it, span.width, span.height) != null }
                            val targetIndex = freeIndex ?: requestedIndex
                            widgetSession = WidgetPickerSession(provider, widgetSlot, span, Offset.Zero,
                                dragging = false, targetIndex = targetIndex)
                            widgetPlacementMessage = if (freeIndex == null)
                                "There isn’t room for this size. Choose another page or move an item first." else null
                            scope.launch { pager.scrollToPage(homeCellPage(targetIndex).coerceIn(0, homePages)) }
                        }
                    },
                    onBuiltin = builtin@{ pickedId ->
                        // The wide and tall large folders are picker choices only: a large folder with that footprint.
                        val builtinId = if (pickedId == FOLDER_WIDE_PICK || pickedId == FOLDER_TALL_PICK) FOLDER_WIDGET else pickedId
                        val pickedSpan = when (pickedId) { FOLDER_WIDE_PICK -> WidgetSpan(2, 1); FOLDER_TALL_PICK -> WidgetSpan(1, 2)
                            else -> UnoWidgets.byId(pickedId)?.let { WidgetSpan(it.spanX, it.spanY) } }
                        val existing = model.placement(widgetSlot)
                        val special = existing?.takeIf { it.row + it.spanY > GRID_ROWS }
                        val span = pickedSpan ?: existing?.let { WidgetSpan(it.spanX, it.spanY) } ?: WidgetSpan(2, 2)
                        if (special != null) {
                            widgetSession = WidgetPickerSession(null, widgetSlot, span, Offset.Zero,
                                dragging = false, candidate = special, builtinId = builtinId)
                            widgetPlacementMessage = null
                            scope.launch { pager.scrollToPage(special.page.coerceAtLeast(0).coerceAtMost(homePages - 1)) }
                            return@builtin
                        }
                        val requested = existing?.let {
                            homeCellIndex(it.page, it.row * GRID_COLUMNS + it.column)
                        } ?: widgetTargetIndex.takeUnless { it == Int.MIN_VALUE } ?: 0
                        val requestedPage = homeCellPage(requested).coerceIn(if (expandedWorkspace) -1 else 0, homePages)
                        val availablePages = (if (expandedWorkspace) -1 else 0)..homePages
                        val candidates = if (model.placement(widgetSlot) != null || widgetExactTarget) sequenceOf(requested)
                            else (listOf(requestedPage) + availablePages.filter { it != requestedPage }).asSequence()
                                .flatMap { page -> (0 until HOME_CELLS).asSequence().map { homeCellIndex(page, it) } }
                        val free = candidates.firstOrNull {
                            widgetCandidate(state.layout, widgetSlot, it, span.width, span.height) != null
                        }
                        widgetSession = WidgetPickerSession(null, widgetSlot, span, Offset.Zero,
                            dragging = false, targetIndex = free ?: requested, builtinId = builtinId)
                        widgetPlacementMessage = if (free == null)
                            "There isn’t room for this card. Choose another page or move an item first." else null
                        scope.launch { pager.scrollToPage(homeCellPage(free ?: requested).coerceIn(0, homePages)) }
                    },
                    onDragStart = { provider, point ->
                        footprint(provider)?.let { span ->
                            widgetSession = WidgetPickerSession(provider, widgetSlot, span, point, dragging = true)
                            widgetPlacementMessage = null
                            scope.launch { pager.scrollToPage(lastHomePage.coerceIn(0, homePages - 1)) }
                        }
                    },
                    onDrag = { point -> widgetSession = widgetSession?.copy(pointer = point) },
                    onDrop = {
                        val session = widgetSession
                        if (session != null && widgetDraft != null) {
                            session.provider?.let { widgets.add(widgetDraft, it, pickerSizing) }
                                ?: session.builtinId?.let { widgets.setBuiltin(widgetDraft.copy(id = it)) }
                            widgetSession = null; sheet = ""; widgetPackage = null
                        } else {
                            leaveTemporaryWidgetPage(); widgetSession = null
                            widgetPlacementMessage = "There isn’t room there. Try another space or page."
                        }
                    },
                    onCancelDrag = {
                        if (widgetSession != null) {
                            leaveTemporaryWidgetPage(); widgetSession = null
                        }
                    })
                widgetSession?.let { session ->
                    val placementDensity = LocalDensity.current
                    val sessionEntry = session.provider?.let { selected -> catalog?.firstOrNull {
                        it.provider.provider == selected.provider && it.provider.profile == selected.profile } }
                    // Legacy overflow replacements are locked to their existing view
                    // bounds and may begin below the canonical six-row grid. They have
                    // no Home-cell address; specialAnchor below is their visual anchor.
                    val candidateIndex = widgetDraft?.takeIf { session.candidate == null }
                        ?.let { homeCellIndex(it.page, it.row * GRID_COLUMNS + it.column) }
                    val visualIndex = candidateIndex ?: widgetRawTarget?.index ?: session.targetIndex
                    val specialAnchor = session.candidate?.let { drag.regions[DropTarget.Widget(session.slot)]?.bounds }
                    val anchor = specialAnchor ?: visualIndex?.let { drag.regions[DropTarget.Home(it)]?.bounds }
                    Box(Modifier.fillMaxSize().testTag("widget-placement-mode")
                        .then(if (!session.dragging && session.candidate == null) Modifier.pointerInput(session.slot, session.span) {
                            detectTapGestures { local ->
                                val point = local + drag.rootOrigin
                                val cell = drag.regions.values.firstOrNull {
                                    it.target is DropTarget.Home && it.page in eligibleDragPages && it.bounds.contains(point)
                                }?.target as? DropTarget.Home
                                cell?.let { widgetSession = session.copy(pointer = point, targetIndex = it.index) }
                            }
                        } else Modifier)) {
                        Row(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp)
                            .background(Glass.copy(alpha = .97f), Corner.pill)
                            .testTag("widget-placement-toolbar"), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = widgetPickerBack) { Text(stringResource(com.jake.duolauncher.R.string.back_to_widgets)) }
                            if (session.candidate != null) Text(stringResource(com.jake.duolauncher.R.string.replace_here), color = Ink,
                                modifier = Modifier.testTag("widget-replacement-locked"))
                            val targetPage = homeCellPage(session.targetIndex ?: 0)
                            if (!session.dragging && session.candidate == null) IconButton(
                                enabled = targetPage > if (expandedWorkspace) -1 else 0, onClick = {
                                val local = homeCellLocal(session.targetIndex ?: 0)
                                val page = targetPage - 1
                                widgetSession = session.copy(targetIndex = homeCellIndex(page, local))
                                scope.launch { pager.animateScrollToPage(page.coerceAtLeast(0)) }
                            }) { Icon(Icons.Rounded.ChevronLeft, "Previous home page") }
                            Text("${session.span.width} × ${session.span.height}", color = Ink)
                            if (!session.dragging && session.candidate == null) IconButton(enabled = targetPage < homePages, onClick = {
                                val local = homeCellLocal(session.targetIndex ?: 0)
                                val page = (targetPage + 1).coerceAtMost(homePages)
                                widgetSession = session.copy(targetIndex = homeCellIndex(page, local))
                                scope.launch { pager.animateScrollToPage(page.coerceAtLeast(0)) }
                            }) { Icon(Icons.Rounded.ChevronRight, "Next home page") }
                            if (!session.dragging) TextButton(enabled = widgetDraft != null, onClick = {
                                widgetDraft?.let { draft ->
                                    val contentSize = specialAnchor?.let { bounds -> with(placementDensity) {
                                        WidgetContentSize(bounds.width.toDp().value, bounds.height.toDp().value)
                                    } }
                                    session.provider?.let { widgets.add(draft, it, pickerSizing, contentSize) }
                                        ?: session.builtinId?.let { widgets.setBuiltin(draft.copy(id = it)) }
                                    widgetSession = null; sheet = ""; widgetPackage = null
                                }
                            }, modifier = Modifier.testTag("widget-placement-apply")) { Text(stringResource(com.jake.duolauncher.R.string.place)) }
                            TextButton(onClick = { leaveTemporaryWidgetPage(); widgetSession = null; sheet = ""; widgetPackage = null },
                                modifier = Modifier.testTag("widget-placement-cancel")) { Text(stringResource(com.jake.duolauncher.R.string.cancel)) }
                        }
                        if (anchor != null) {
                            val density = LocalDensity.current
                            val cellWidthPx = with(density) { (geometry.gridWidth / GRID_COLUMNS).dp.toPx() }
                            fun pickerRowTop(row: Int): Float = if (row <= 2) row * with(density) { topPitch.dp.toPx() }
                                else with(density) { (geometry.widgetHeight + 18f + (row - 2) * geometry.rowHeight).dp.toPx() }
                            val candidateRow = homeCellLocal(visualIndex ?: 0) / GRID_COLUMNS
                            val previewWidth = specialAnchor?.let { with(density) { it.width.toDp() } }
                                ?: with(density) { (cellWidthPx * session.span.width - 10.dp.toPx()).toDp() }
                            val previewHeight = specialAnchor?.let { with(density) { it.height.toDp() } }
                                ?: with(density) { (pickerRowTop(candidateRow + session.span.height) -
                                    pickerRowTop(candidateRow) - 18.dp.toPx()).coerceAtLeast(48.dp.toPx()).toDp() }
                            val previewX = if (specialAnchor != null) anchor.left
                                else anchor.left + with(density) { 5.dp.toPx() }
                            // Cell bounds are in root coordinates and this layer is drawn inside the launcher root, so its origin is taken off (as the
                            // drag ghost does); without that the preview sits off from where the widget lands whenever the root is not at the origin.
                            Surface(Modifier.offset { IntOffset((previewX - drag.rootOrigin.x).roundToInt(), (anchor.top - drag.rootOrigin.y).roundToInt()) }
                                .size(previewWidth, previewHeight).testTag("widget-placement-preview")
                                .semantics { stateDescription = if (widgetDraft != null) "Ready to place" else "No room here" },
                                color = if (widgetDraft != null) Glass.copy(alpha = .82f) else Color(0xFFE7B6B6).copy(alpha = .9f),
                                shape = Corner.large, border = androidx.compose.foundation.BorderStroke(3.dp,
                                    if (widgetDraft != null) Color.White else Color(0xFFFF6B6B))) {
                                Box(Modifier.fillMaxSize()) {
                                    if (sessionEntry != null) WidgetProviderPreview(sessionEntry, session.span,
                                        Modifier.fillMaxSize().padding(5.dp).clip(Corner.medium))
                                    else Column(Modifier.align(Alignment.Center).padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(session.provider?.loadLabel(launcherActivity.packageManager)?.toString()
                                            ?: when (session.builtinId) {
                                                CLOCK_WIDGET -> "Clock"
                                                DATE_WIDGET -> "Date"
                                                FOLDER_WIDGET -> "Large folder"
                                                else -> UnoWidgets.byId(session.builtinId ?: 0)?.label ?: "Widget panel"
                                            }, color = Ink,
                                            textAlign = TextAlign.Center)
                                        Text("${session.span.width} × ${session.span.height}", color = Ink)
                                    }
                                    if (widgetDraft == null) Box(Modifier.matchParentSize()
                                        .background(Color(0xFFB83B3B).copy(alpha = .34f)), contentAlignment = Alignment.Center) {
                                        Text(stringResource(com.jake.duolauncher.R.string.no_room_here), color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        } else if (session.dragging) {
                            Surface(Modifier.offset { IntOffset((session.pointer.x - drag.rootOrigin.x - 90.dp.toPx()).roundToInt(),
                                (session.pointer.y - drag.rootOrigin.y - 60.dp.toPx()).roundToInt()) }.size(180.dp, 120.dp)
                                .testTag("widget-placement-preview").semantics { stateDescription = "No room here" },
                                color = Color(0xFFE7B6B6).copy(alpha = .9f), shape = Corner.large) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (sessionEntry != null) WidgetProviderPreview(sessionEntry, session.span,
                                        Modifier.fillMaxSize().padding(5.dp).clip(Corner.medium))
                                    Box(Modifier.matchParentSize().background(Color(0xFFB83B3B).copy(alpha = .34f)),
                                        contentAlignment = Alignment.Center) { Text(stringResource(com.jake.duolauncher.R.string.no_room_here), color = Color.White) }
                                }
                            }
                        }
                    }
                }
                widgetPlacementMessage?.let { message ->
                    Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp),
                        color = Glass, shape = Corner.medium) { Text(message, Modifier.padding(16.dp), color = Ink) }
                }
            }
        }
        // The control panel lives out here, beside the inset-padded Home content, so its dimming layer covers the whole
        // window. Inside that content it stopped at the status and navigation bars, leaving two undimmed strips that
        // read as the edges of a panel behind the glass; the panel itself applies the safe-area padding.
        // Search grows out of the island: the panel starts as the collapsed pill and opens to full size (and shrinks back into
        // it on the way out). The enter and exit are driven by this transition's own progress, not by a fade.
        val spotlightPill = if (appearance.island) island.restPill else null
        androidx.compose.animation.AnimatedVisibility(spotlightOpen,
            enter = androidx.compose.animation.EnterTransition.None,
            exit = androidx.compose.animation.ExitTransition.None) {
            val grow by transition.animateFloat(transitionSpec = {
                if (targetState == androidx.compose.animation.EnterExitState.Visible) androidx.compose.animation.core.spring(dampingRatio = .78f, stiffness = 320f)
                else androidx.compose.animation.core.tween(200)
            }, label = "search grows from the island") { if (it == androidx.compose.animation.EnterExitState.Visible) 1f else 0f }
            Spotlight(controlGlass, state.apps, state.hiddenApps, feed.shownEntries, extras?.store?.state?.contactSearch == true, handoffResolver,
                onLaunch = { app -> spotlightOpen = false; onLaunchFrom(app, null) },
                onOpenFeedLink = { link -> spotlightOpen = false; onFeedOpenEntry(link) },
                onOpenContact = { contact -> spotlightOpen = false; ContactsSearch.open(launcherActivity, contact) },
                onDismiss = { spotlightOpen = false }, progress = grow, visible = spotlightOpen, pill = spotlightPill,
                noteSlot = state.widgetPlacements.firstOrNull { it.id == UnoWidgets.NOTE }?.slot,
                focusOn = { extras?.store?.state?.focusOn == true },
                onToggleFocus = { extras?.toggleFocus?.invoke() })
        }
        androidx.compose.animation.AnimatedVisibility(controlPanelOpen,
            enter = androidx.compose.animation.slideInVertically { -it / 2 } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { -it / 2 } + androidx.compose.animation.fadeOut()) {
            ControlPanel(controlGlass, extras?.store?.state ?: ExtrasState(), state.apps,
                onToggleFocus = { extras?.toggleFocus?.invoke() },
                onLaunchApp = { app -> controlPanelOpen = false; onLaunch(app) },
                onCustomize = { controlPanelOpen = false; customizationPage = CustomizationPage.GESTURES; sheet = "settings" },
                onDismiss = { controlPanelOpen = false }, onSystemSettings = {
                controlPanelOpen = false
                launcherActivity.openSystemShade(ShadePanel.QUICK_SETTINGS)
            })
        }
        if (drag.active) {
            if (drag.moved) {
                if (pager.currentPage > 0) Box(Modifier.align(Alignment.CenterStart).width(6.dp).height(112.dp)
                    .background(Color.White.copy(alpha = if (edge < 0) .9f else .3f), Corner.pill).testTag("drag-edge-left"))
                if (pager.currentPage < homePages) Box(Modifier.align(Alignment.CenterEnd).width(6.dp).height(112.dp)
                    .background(Color.White.copy(alpha = if (edge > 0) .9f else .3f), Corner.pill).testTag("drag-edge-right"))
            }
            appsById[drag.source?.appId]?.let { app ->
                val size = 66.dp
                val px = with(LocalDensity.current) { size.toPx() }
                Image(app.image, "Moving ${app.label}", Modifier
                    .offset { IntOffset((drag.pointer.x - drag.rootOrigin.x - px / 2).roundToInt(), (drag.pointer.y - drag.rootOrigin.y - px * .65f).roundToInt()) }
                    .size(size).clip(Corner.icon).testTag("drag-ghost"))
            }
            drag.source?.appId?.let { state.layout.folder(it) }?.let { folder ->
                Surface(Modifier.offset { IntOffset((drag.pointer.x - drag.rootOrigin.x - 42.dp.toPx()).roundToInt(),
                    (drag.pointer.y - drag.rootOrigin.y - 52.dp.toPx()).roundToInt()) }.size(84.dp)
                    .testTag("folder-drag-ghost"),
                    color = Glass.copy(alpha = .96f), shape = Corner.medium) {
                    Box(contentAlignment = Alignment.Center) { Text(folder.title, color = Ink, textAlign = TextAlign.Center) }
                }
            }
            drag.source?.widgetId?.let { id ->
                val width = 144.dp; val height = 108.dp
                val x = with(LocalDensity.current) { width.toPx() }
                val y = with(LocalDensity.current) { height.toPx() }
                Surface(Modifier.offset { IntOffset((drag.pointer.x - x / 2).roundToInt(), (drag.pointer.y - y * .65f).roundToInt()) }
                    .size(width, height).shadow(16.dp, Corner.large).testTag("drag-ghost"),
                    color = Glass.copy(alpha = .95f), shape = Corner.large) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Widgets, null, tint = Ink)
                        Spacer(Modifier.height(8.dp))
                        Text(remember(id, widgets) { widgetLabel(id, widgets) }, color = Ink, maxLines = 2, textAlign = TextAlign.Center)
                    }
                }
            }
            if (blockedDock) Surface(
                Modifier.align(Alignment.TopCenter).statusBarsPadding()
                    .padding(top = 10.dp, start = 20.dp, end = 100.dp),
                color = Glass.copy(alpha = .96f), shape = Corner.medium
            ) {
                Text(stringResource(com.jake.duolauncher.R.string.dock_full_move_an_app_out),
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = Ink, fontSize = 13.sp)
            }
            if (drag.moved && drag.source?.target !is DropTarget.Library &&
                drag.source?.appId?.let(::isFolderId) != true) Surface(
                // Keep removal in the right-side control area that is vacated during a drag.
                // A centered target overlaps the expanded workspace's right-hand first cell.
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 12.dp, bottom = 12.dp)
                    .width((if (expandedWorkspace) state.expanded else state.compact).dockWidth.dp).height(64.dp)
                    .dropRegion(drag, DropTarget.Remove).testTag("remove-drop-target"),
                color = if (target == DropTarget.Remove) Color(0xFFB33B3B) else Glass.copy(alpha = .96f), shape = Corner.large) {
                Column(Modifier.fillMaxSize().padding(vertical = 6.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.DeleteOutline, null)
                    Text(stringResource(com.jake.duolauncher.R.string.remove), fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        resizeSlot?.let { slot ->
            val placement = model.placement(slot)
            val bounds = drag.regions[DropTarget.Widget(slot)]?.bounds
            if (placement != null && bounds != null) {
                val minW = resizeConstraints?.minimum?.width ?: 2
                val minH = resizeConstraints?.minimum?.height ?: 2
                val maxW = minOf(GRID_COLUMNS - placement.column, resizeConstraints?.maximum?.width ?: GRID_COLUMNS)
                val maxH = minOf(GRID_ROWS - placement.row, resizeConstraints?.maximum?.height ?: GRID_ROWS)
                val feasible = placement.page >= -1 && placement.row in 0 until GRID_ROWS &&
                    !(placement.id >= 0 && resizeConstraints == null) && minW <= maxW && minH <= maxH
                val candidate = resizeWidget(state.layout, slot, resizeWidth, resizeHeight)
                val valid = feasible && (placement.id != FOLDER_WIDGET || resizeWidth * resizeHeight >= 2) &&
                    ((resizeWidth == placement.spanX && resizeHeight == placement.spanY) || candidate != state.layout)
                val widthPx = (bounds.width + (resizeWidth - placement.spanX) * resizePitchX).coerceAtLeast(resizePitchX)
                val density = LocalDensity.current
                fun resizeRowTop(row: Int) = homeRowTop(row, resizeTopPitch, resizeAppPitch)
                val heightPx = (resizeRowTop(placement.row + resizeHeight) - resizeRowTop(placement.row) -
                    with(density) { 18.dp.toPx() }).coerceAtLeast(resizePitchY)
                Box(Modifier.offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                    .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
                    .border(3.dp, if (valid) Color.White else Color(0xFFFF6B6B), Corner.large)
                    .testTag("widget-resize-preview-$slot")) {
                    Box(Modifier.align(Alignment.BottomEnd).offset(12.dp, 12.dp).size(44.dp)
                        .background(if (valid) Color.White else Color(0xFFFF6B6B), CircleShape)
                        .testTag("widget-resize-handle-$slot")
                        .pointerInput(slot, resizeConstraints) {
                            var dx = 0f; var dy = 0f; var startWidth = resizeWidth; var startHeight = resizeHeight
                            detectDragGestures(onDragStart = {
                                dx = 0f; dy = 0f; startWidth = resizeWidth; startHeight = resizeHeight
                            }, onDrag = { change, amount ->
                                change.consume(); dx += amount.x; dy += amount.y
                                if (feasible && resizeConstraints?.canResizeHorizontally != false)
                                    resizeWidth = (startWidth + (dx / resizePitchX).roundToInt()).coerceIn(minW, maxW)
                                if (feasible && resizeConstraints?.canResizeVertically != false)
                                    resizeHeight = resizeRowsFor(placement.row, startHeight, dy, resizeTopPitch, resizeAppPitch, minH, maxH)
                            })
                        }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.OpenInFull, "Drag to resize widget", tint = Ink, modifier = Modifier.size(22.dp))
                    }
                    Row(Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                        .background(Glass.copy(alpha = .96f), Corner.medium)) {
                        TextButton(onClick = { resizeSlot = null }) { Text(stringResource(com.jake.duolauncher.R.string.cancel)) }
                        TextButton(enabled = valid, onClick = {
                            model.resizeWidget(slot, resizeWidth, resizeHeight); resizeSlot = null
                        }) { Text(stringResource(com.jake.duolauncher.R.string.apply)) }
                    }
                    if (!feasible) Text(stringResource(com.jake.duolauncher.R.string.move_this_widget_into_the_six),
                        color = Color.White, modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .65f)).padding(8.dp))
                }
            }
        }
        appsById[selectedId]?.let { app ->
            val pinned = state.layout.indexOfShortcut(app.id) != null
            val packageName = app.packageName
            val hasWidgets = packageName.isNotEmpty() && runCatching {
                widgets.providersForPackage(packageName, app.user)
            }.getOrDefault(emptyList()).isNotEmpty()
            GlassModalSheet(controlGlass, onDismissRequest = { appMoveMenu = false; selectedId = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)) {
                // The app's own shortcuts load in the background; the sheet is usable immediately and they fill in.
                val shortcuts by produceState(emptyList<AppShortcut>(), app.id) { value = AppShortcuts.load(launcherActivity, app) }
                LauncherAppActionSheet(app, pinned, homePages, appMoveMenu, { appMoveMenu = it },
                    onAddOrRemove = { model.setPinned(app.id, !pinned); selectedId = null },
                    onMoveFirst = { model.move(app.id, -maxOf(HOME_CELLS, state.homeSlots.size)); selectedId = null },
                    onMoveEarlier = { model.move(app.id, -1); selectedId = null },
                    onMoveLater = { model.move(app.id, 1); selectedId = null },
                    onMovePage = { page -> model.applyDrop(app.id, DropTarget.Home(homeCellIndex(page, 0))); selectedId = null },
                    onInfo = { onAppInfo(app); selectedId = null },
                    onWidgets = if (hasWidgets) {{
                        val page = lastHomePage.coerceIn(0, homePages - 1)
                        widgetTargetIndex = homeCellIndex(page, 0); widgetExactTarget = false
                        widgetSlot = model.nextWidgetSlot(); widgetPackage = packageName
                        widgetProfileSerial = app.userSerial; selectedId = null; sheet = "widgets"
                    }} else null,
                    onCreateFolder = { createFolderFirstId = app.id; selectedId = null },
                    onClose = { appMoveMenu = false; selectedId = null },
                    onSplit = { splitFirstId = app.id; selectedId = null; sheet = "split" },
                    shortcuts = shortcuts, onShortcut = { AppShortcuts.start(launcherActivity, it); selectedId = null },
                    onEditHome = { homeEditing = true; selectedId = null },
                    onHide = if (state.folders.none { app.id in it.appIds }) {{ model.hideApp(app.id); selectedId = null }} else null,
                    onUninstall = if (!isSystemApp(launcherActivity, app)) {{
                        runCatching {
                            launcherActivity.startActivity(android.content.Intent(android.content.Intent.ACTION_DELETE, android.net.Uri.parse("package:$packageName"))
                                .putExtra(android.content.Intent.EXTRA_USER, app.user))
                        }
                        selectedId = null
                    }} else null,
                    badgesMuted = extras?.store?.state?.takeIf { it.badges }?.let { packageName in BadgeMute.parse(it.badgeMuted) },
                    onToggleBadges = {
                        extras?.store?.let { store -> store.setBadgeMuted(packageName, packageName !in BadgeMute.parse(store.state.badgeMuted)) }
                        selectedId = null
                    })
            }
        }
        emptyCellIndex?.let { index ->
            GlassModalSheet(controlGlass, onDismissRequest = { emptyCellIndex = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                EmptySpaceActionSheet(onWidgets = {
                        widgetTargetIndex = index; widgetExactTarget = true; widgetSlot = model.nextWidgetSlot(); widgetPackage = null; widgetProfileSerial = null
                        emptyCellIndex = null; sheet = "widgets"
                    }, onWallpaper = { emptyCellIndex = null; sheet = "settings:wallpaper" },
                    onCustomize = { emptyCellIndex = null; sheet = "settings" }, onClose = { emptyCellIndex = null },
                    onEditHome = { emptyCellIndex = null; homeEditing = true })
            }
        }
        createFolderFirstId?.let { firstId ->
            val first = appsById[firstId]
            GlassAlertDialog(onDismissRequest = { createFolderFirstId = null }, title = { Text("Create folder with ${first?.label ?: "app"}") },
                text = { LazyColumn(Modifier.heightIn(max = 420.dp).testTag("folder-app-picker")) {
                    items(state.visibleApps.filter { it.id != firstId && it.available }, key = { it.id }) { second ->
                        GlassButton(onClick = {
                            val preferredPage = state.layout.indexOfShortcut(firstId)?.let(::homeCellPage)
                                ?.takeIf { it >= 0 || expandedWorkspace } ?: lastHomePage.coerceIn(0, homePages - 1)
                            val blocked = state.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() }
                            val targetIndex = (0 until HOME_CELLS).map { homeCellIndex(preferredPage, it) }
                                .firstOrNull { it !in blocked && state.layout.slotAt(it) in listOf(null, firstId, second.id) }
                            if (targetIndex != null) model.createFolder(firstId, second.id, targetIndex,
                                suggestedFolderName(context, listOfNotNull(first, second)))?.let { newFolderId = it; openFolderId = it }
                            createFolderFirstId = null
                        }, modifier = Modifier.fillMaxWidth().testTag("folder-app-${second.id}")) {
                            Text(second.label, Modifier.fillMaxWidth())
                        }
                    }
                } }, confirmButton = { GlassButton(onClick = { createFolderFirstId = null }) { Text(stringResource(com.jake.duolauncher.R.string.cancel)) } })
        }
        openFolderId?.let { id ->
            state.folders.firstOrNull { it.id == id }?.let { folder ->
                val blocked = state.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() }
                val destinationPages = (if (expandedWorkspace) listOf(-1) else emptyList()) + (0 until homePages)
                val homeDestinations = destinationPages.mapNotNull { destinationPage ->
                    (0 until HOME_CELLS).map { homeCellIndex(destinationPage, it) }
                        .firstOrNull { it !in blocked && state.layout.slotAt(it) == null }
                }
                FolderPanel(folder, appsById, drag, pager.currentPage, homeDestinations,
                    dockVacancies = state.dock.indices.filter { state.dock[it] == null },
                    onDismiss = { openFolderId = null; newFolderId = null }, onRename = { model.renameFolder(id, it) },
                    onLaunch = onLaunchFrom,
                    onMoveOut = { appId, destination ->
                        if (model.removeAppFromFolder(id, appId, destination)) openFolderId = model.folder(id)?.id
                    },
                    glassBackdrop = homeBackdrop.combined.takeIf { glassEnabled },
                    glassTint = glassTint.copy(alpha = .90f),
                    settings = glassSettings, editTitle = newFolderId == id)
            } ?: LaunchedEffect(id) { openFolderId = null }
        }
        launcherActivity.backups.preview?.let { preview ->
            LayoutRestorePreview(preview, onRestore = {
                launcherActivity.backups.applyImport(); sheet = ""
            }, onCancel = launcherActivity.backups::cancelImport)
        }
        if (launcherActivity.backups.pickerPending) GlassAlertDialog(onDismissRequest = {},
            title = { Text(stringResource(com.jake.duolauncher.R.string.layout_document)) },
            text = { Text(stringResource(com.jake.duolauncher.R.string.the_system_document_picker_is_still)) },
            confirmButton = { GlassButton(onClick = { launcherActivity.backups.resumePendingPicker() },
                modifier = Modifier.testTag("backup-picker-resume")) { Text(stringResource(com.jake.duolauncher.R.string.resume)) } },
            dismissButton = { GlassButton(onClick = launcherActivity.backups::cancelImport,
                modifier = Modifier.testTag("backup-picker-cancel")) { Text(stringResource(com.jake.duolauncher.R.string.cancel)) } })
        if (launcherActivity.backgrounds.pickerPending && !launcherActivity.backgrounds.loading) GlassAlertDialog(
            onDismissRequest = {}, title = { Text(stringResource(com.jake.duolauncher.R.string.background_photo)) },
            text = { Text(stringResource(com.jake.duolauncher.R.string.the_photo_picker_was_interrupted_resume)) },
            confirmButton = { GlassButton(onClick = launcherActivity.backgrounds::choosePhoto,
                modifier = Modifier.testTag("background-picker-resume")) { Text(stringResource(com.jake.duolauncher.R.string.resume)) } },
            dismissButton = { GlassButton(onClick = launcherActivity.backgrounds::cancelPendingSelection,
                modifier = Modifier.testTag("background-picker-cancel")) { Text(stringResource(com.jake.duolauncher.R.string.cancel)) } })
        (launcherActivity.backups.errorMessage ?: launcherActivity.backups.successMessage)?.let { message ->
            GlassAlertDialog(onDismissRequest = launcherActivity.backups::clearMessage,
                title = { Text(if (launcherActivity.backups.errorMessage != null) "Layout backup problem" else "Layout backup") },
                text = { Text(message) }, confirmButton = { GlassButton(onClick = launcherActivity.backups::clearMessage) { Text(stringResource(com.jake.duolauncher.R.string.ok)) } })
        }
        widgets.failureMessage?.let { message ->
            GlassAlertDialog(onDismissRequest = widgets::clearFailure, title = { Text(stringResource(com.jake.duolauncher.R.string.widget_not_added)) },
                text = { Text(message, Modifier.testTag("widget-bind-error")) },
                confirmButton = { GlassButton(onClick = widgets::clearFailure) { Text(stringResource(com.jake.duolauncher.R.string.ok)) } })
        }
        if (widgets.pendingPlacement != null && widgets.setupStatus != null) {
            GlassAlertDialog(onDismissRequest = {}, title = { Text(stringResource(com.jake.duolauncher.R.string.finish_widget_setup)) },
                text = { Text(stringResource(com.jake.duolauncher.R.string.the_widget_is_waiting_at_its)) },
                confirmButton = { Button(onClick = widgets::finishPendingSetup,
                    modifier = Modifier.semantics { contentDescription = "Continue widget setup" }) { Text(stringResource(com.jake.duolauncher.R.string.finish_setup)) } },
                dismissButton = { GlassButton(onClick = { leaveTemporaryWidgetPage(); widgets.cancelPendingSetup() },
                    modifier = Modifier.semantics { contentDescription = "Cancel widget setup" }) { Text(stringResource(com.jake.duolauncher.R.string.cancel)) } })
        }
        widgets.reconfigureWidgetId?.let {
            GlassAlertDialog(onDismissRequest = {}, title = { Text(stringResource(com.jake.duolauncher.R.string.widget_settings)) },
                text = { Text(stringResource(com.jake.duolauncher.R.string.widget_settings_were_interrupted_resume_co)) },
                confirmButton = { Button(onClick = widgets::finishPendingReconfigure,
                    modifier = Modifier.testTag("widget-reconfigure-resume")) { Text(stringResource(com.jake.duolauncher.R.string.resume)) } },
                dismissButton = { GlassButton(onClick = widgets::cancelPendingReconfigure,
                    modifier = Modifier.testTag("widget-reconfigure-cancel")) { Text(stringResource(com.jake.duolauncher.R.string.cancel)) } })
        }
        }
    }
}

/** What is under the finger during a drag. */
private data class DragTargets(val raw: DropTarget?, val target: DropTarget?, val folderApp: String?, val stackSlot: Int? = null)
