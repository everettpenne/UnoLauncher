@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.jake.duolauncher

import android.appwidget.AppWidgetProviderInfo
import android.os.UserManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal val Ink: Color
    @Composable get() = LocalDuoPalette.current.ink
internal val Glass: Color
    @Composable get() = LocalDuoPalette.current.glass

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
fun DuoTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    val palette = if (dark) DarkDuoPalette else LightDuoPalette
    CompositionLocalProvider(LocalDuoPalette provides palette) {
        MaterialTheme(shapes = DuoMaterialShapes, typography = DuoTypography, colorScheme = if (dark) darkColorScheme(primary = Color(0xFF9BC5D7), onPrimary = Color(0xFF12303D),
            surface = Color(0xFF17272E), onSurface = palette.ink, secondary = Color(0xFFD1BE98),
            secondaryContainer = Color(0xFF314852), onSecondaryContainer = palette.ink)
        else lightColorScheme(primary = Color(0xFF30596D), onPrimary = Color.White,
            surface = Color(0xFFF4F7F8), onSurface = palette.ink, secondary = Color(0xFF84775F),
            secondaryContainer = Color(0xFFDCE8ED), onSecondaryContainer = palette.ink),
            // MaterialTheme installs its own ripple as LocalIndication, so the no-op has to be provided inside it.
            content = { CompositionLocalProvider(LocalIndication provides NoIndication, content = content) })
    }
}


@Composable
internal fun LauncherScreen(
    state: LauncherState, model: LauncherModel, widgets: WidgetController, homeRequests: Int,
    onLaunch: (AppEntry) -> Unit, onMakeDefault: () -> Unit, onAppInfo: (AppEntry) -> Unit,
    isDefaultHome: Boolean, deviceStatus: DeviceStatus, onStatusMode: (Boolean) -> Unit, onWallpaperPreview: () -> Unit,
    onDiscover: () -> Unit = {}, searchRequests: Int = 0,
    onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit = { app, _ -> onLaunch(app) },
    onGoogleSearch: (android.graphics.Rect?) -> Boolean = { false },
    appearance: AppearanceState = AppearanceState(),
    onAppearanceMode: (AppearanceMode) -> Unit = {},
    onAppearanceManual: (String, Double, Double) -> Unit = { _, _, _ -> },
    onAppearanceDeviceLocation: () -> Unit = {},
    onAppearanceClear: () -> Unit = {},
    showFirstRun: Boolean = false,
    onFinishFirstRun: () -> Unit = {},
    onShadeSetup: () -> Unit = {},
    extras: ExtrasActions? = null,
    feed: FeedState = FeedState(),
    feedSetupRequests: Int = 0,
    onFeedRefresh: () -> Unit = {},
    onFeedOpenEntry: (String) -> Unit = {},
    onFeedVisible: () -> Unit = {},
    onAddFeed: (String, (FeedAddResult) -> Unit) -> Unit = { _, _ -> },
    onRemoveFeed: (String) -> Unit = {},
    onSourceEnabled: (String, Boolean) -> Unit = { _, _ -> },
    onLiquidGlass: (Boolean) -> Unit = {},
    onWallpaperColor: (Boolean) -> Unit = {},
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
    var customizationPage by rememberSaveable { mutableStateOf(CustomizationPage.OVERVIEW) }
    LaunchedEffect(selectedId) { if (selectedId == null) appMoveMenu = false }
    LaunchedEffect(sheet) { if (sheet.isEmpty()) customizationPage = CustomizationPage.OVERVIEW }
    var openFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    // The app a split screen is being set up for; the picker then chooses the one beside it.
    var splitFirstId by rememberSaveable { mutableStateOf<String?>(null) }
    var createFolderFirstId by rememberSaveable { mutableStateOf<String?>(null) }
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
    LaunchedEffect(state.widgetPlacements) {
        WidgetData.prune(state.widgetPlacements.filter { UnoWidgets.byId(it.id) != null }.map { it.slot }.toSet())
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
    BackHandler(enabled = spotlightOpen) { spotlightOpen = false }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { spotlightOpen = false }
    // A soft tick as each page settles (not on the first composition).
    LaunchedEffect(Unit) { snapshotFlow { pager.settledPage }.drop(1).collect { UnoFeedback.play(Cue.PAGE, haptic) } }
    // Themed icons are baked into bitmaps, so a style change (or, while themed, a light/dark flip) rebuilds them.
    val iconStyleNow = extras?.store?.state?.iconStyle ?: IconStyle.ORIGINAL
    val themedDarkNow = iconStyleNow.recolours && appearance.dark
    var iconsSeeded by remember { mutableStateOf(false) }
    val accentKey = if (iconStyleNow.usesWallpaper(appearance.wallpaperColor)) LauncherBackgroundCache.revision.intValue else -1
    LaunchedEffect(iconStyleNow, themedDarkNow, accentKey) { if (iconsSeeded) model.refresh() else iconsSeeded = true }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { controlPanelOpen = false }
    BackHandler(enabled = controlPanelOpen) { controlPanelOpen = false }
    BackHandler(enabled = sheet == "widgets") { widgetPickerBack() }
    BackHandler(enabled = sheet.isEmpty()) { if (resizeSlot != null) resizeSlot = null else if (drag.active) {
        val destination = if (drag.source?.target is DropTarget.Library) homePages else drag.originPage.coerceAtMost(homePages - 1)
        drag.clear(); scope.launch { pager.scrollToPage(destination) }
    } else if (selectedId != null) selectedId = null else { focus.clearFocus(); scope.launch { pager.animateScrollToPage(0) } } }
    val openDiscover = { if (firstHome > 0) scope.launch { pager.animateScrollToPage(-1) } else onDiscover(); Unit }
    val openLibrary = { scope.launch { pager.animateScrollToPage(homePages) }; Unit }

    val dragWindowPage = if (expandedWorkspace && (drag.active || widgetSession != null)) pager.settledPage else pager.currentPage
    val eligibleDragPages = remember(expandedWorkspace, dragWindowPage, visibleHomePages) {
        if (expandedWorkspace && dragWindowPage in 0 until visibleHomePages) {
            setOfNotNull((dragWindowPage - 1).takeIf { it >= -1 }, dragWindowPage)
        } else setOf(dragWindowPage)
    }
    val rawTarget = if (drag.active) drag.destination(drag.pointer, eligibleDragPages)?.target else null
    val target = if (rawTarget is DropTarget.Home && drag.source?.target is DropTarget.Widget) {
        val slot = (drag.source!!.target as DropTarget.Widget).index
        model.placement(slot)?.let {
            DropTarget.Home(adjustedWidgetDropIndex(rawTarget.index, it, drag.source!!.bounds, drag.origin))
        } ?: rawTarget
    } else rawTarget
    val blockedDock = drag.moved && target is DropTarget.Dock &&
        if (drag.source?.folderId != null) state.dock.none { it == null }
        else drag.source?.appId?.let { !canPlaceInDock(state.layout, it) } == true
    // Held over the middle of another app: the drop makes a folder, so nothing reflows out of the way.
    val folderTargetApp = if (drag.active && drag.moved) folderDropTarget(drag.destination(drag.pointer, eligibleDragPages), drag.pointer, drag.source, ::isFolderId) else null
    SideEffect { drag.folderIntentApp = folderTargetApp }
    val insertionTarget = target.takeIf { drag.moved && !blockedDock && folderTargetApp == null }
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
    val edgeWidth = with(LocalDensity.current) { 30.dp.toPx() }
    val edgePointer = widgetSession?.takeIf { it.dragging }?.pointer ?: drag.pointer
    val edgeActive = (drag.active && drag.moved) || widgetSession?.dragging == true
    val edge = if (!edgeActive) 0 else dragEdgeDirection(edgePointer, drag.rootBounds, edgeWidth)
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
    fun finishDrag(cancelled: Boolean) {
        val source = drag.source ?: return
        val moved = drag.moved
        val rawDestination = if (moved && !cancelled) drag.destination(drag.pointer, eligibleDragPages)?.target else null
        val destination = if (rawDestination is DropTarget.Home && source.target is DropTarget.Widget) {
            model.placement(source.target.index)?.let {
                DropTarget.Home(adjustedWidgetDropIndex(rawDestination.index, it, source.bounds, drag.origin))
            }
                ?: rawDestination
        } else rawDestination
        val folderOntoApp = if (moved && !cancelled && destination is DropTarget.Home)
            folderDropTarget(drag.destination(drag.pointer, eligibleDragPages), drag.pointer, source, ::isFolderId) else null
        val changed = when {
            folderOntoApp != null && destination is DropTarget.Home && source.folderId == null ->
                model.createFolder(folderOntoApp, source.appId ?: "", destination.index) != null
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
        page = pager.currentPage, eligiblePages = eligibleDragPages, onStart = {
            focus.clearFocus(); keyboard?.hide(); haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
        Box(Modifier.matchParentSize().then(if (glassEnabled) Modifier.recordBackdrop(homeBackdrop.wallpaper) else Modifier)) {
            DuneWallpaper()
        }
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
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
                        !(region?.target is DropTarget.Dock && dockScroll.value > 0) &&
                            !nativeWidgetConsumesVerticalGesture(launcherRootView, screenPoint)
                    }
                },
                // The right-hand swipe opens the launcher's own control panel; Notifications still use the system shade.
                onDownwardSwipeAt = { panel, startFraction ->
                    when (PullDownRouting.route(extras?.store?.state?.pullDown ?: PullDown.SMART, panel, startFraction)) {
                        PullRoute.SEARCH -> { UnoFeedback.play(Cue.OPEN, haptic); spotlightOpen = true }
                        PullRoute.QUICK_SETTINGS ->
                            if ((extras?.store?.state?.rightSwipe ?: RightSwipe.PANEL) == RightSwipe.PANEL) { UnoFeedback.play(Cue.OPEN, haptic); controlPanelOpen = true }
                            else launcherActivity.openSystemShade(ShadePanel.QUICK_SETTINGS)
                        PullRoute.NOTIFICATIONS -> launcherActivity.openSystemShade(ShadePanel.NOTIFICATIONS)
                    }
                },
                onUpwardSwipe = { UnoFeedback.play(Cue.OPEN, haptic); island.collapse(); customizationPage = CustomizationPage.OVERVIEW; sheet = "settings" },
                onLeadingOverscroll = if (firstHome == 0) onDiscover else null,
                ignorePress = { point -> pageStripBounds.contains(point + gestureOriginInRoot) ||
                    StackRailRegistry.bounds.values.any { it.contains(point + gestureOriginInRoot) } },
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
                    CompositionLocalProvider(LocalPageGlass provides pageGlass) { ExpandedWorkspace(
                        nativePager = nativePager, motion = workspaceMotion!!, firstHome = firstHome,
                        viewportWidth = pagerWidth, trailingOverscan = pagerEndInset,
                        visibleHomePages = visibleHomePages, panelWidth = panelWidth,
                        contentHeight = contentHeight, bottomSpace = bottomSpace, geometry = geometry,
                        state = state, previewSlots = previewLayout.slots, previewLeadingSlots = previewLayout.leadingSlots,
                        previewWidgetPlacements = previewLayout.widgetPlacements, appsById = appsById,
                        widgets = widgets, drag = drag, target = target, insertionTarget = insertionTarget,
                        feed = feed, feedVisible = feedVisible,
                        onFeedRefresh = onFeedRefresh, onFeedOpenEntry = onFeedOpenEntry,
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
                    CompositionLocalProvider(LocalPageGlass provides pageGlass) {
                    if (page == -1) {
                        DiscoverContent(Modifier.fillMaxSize().padding(start = 16.dp, top = 16.dp, bottom = bottomSpace),
                            feed, feedVisible, onFeedRefresh, onFeedOpenEntry,
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
                                onRefresh = model::refresh)
                        }
                    }
                    }
                    }
                }
            }
            // Soft blur toward the top and bottom edges, beneath the island, rail, dock and page dots.
            if (glassEnabled) {
                val insets = WindowInsets.safeDrawing.asPaddingValues()
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
            Column(Modifier.align(Alignment.BottomStart).width(pagerWidth).padding(start = 16.dp, bottom = PageIndicatorLayout.BOTTOM_MARGIN_DP.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!isDefaultHome) FilledTonalButton(onClick = { sheet = ""; onMakeDefault() }, Modifier.heightIn(min = 48.dp).testTag("home-setup")) {
                    Icon(Icons.Rounded.Home, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Set as home app")
                }
                // The page dots ride in a small glass capsule, like iOS's page indicator, with a
                // selection lens that follows the pager and the finger.
                PageStrip(pager, homePages, visibleHomePages, showCompass = !drag.active, glass = controlGlass,
                    onDiscover = openDiscover, onLibrary = openLibrary, onBounds = { pageStripBounds = it })
            }
            if (!inLibrary && !drag.active) Column(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 8.dp)
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
            if (appearance.island && !islandEverywhere && sheet.isEmpty() && !showFirstRun && !drag.active && !controlPanelOpen && !spotlightOpen) {
                DynamicIsland(island, controlGlass, deviceStatus,
                    feedHeadline = feed.shownEntries.firstOrNull()?.title,
                    sizeScale = appearance.islandScale,
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
                val activeCustomizationPage = if (sheet == "settings:wallpaper") CustomizationPage.WALLPAPER else customizationPage
                GlassModalSheet(controlGlass, onDismissRequest = {
                    customizationPage = CustomizationPage.OVERVIEW
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
                            customizationPage = CustomizationPage.OVERVIEW
                            sheet = ""; widgetPackage = null; widgetExactTarget = false
                        }
                    }
                    when (sheet) {
                        "dock" -> AppPicker(state.apps, dockSlot,
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
                            else AppPicker(state.apps.filter { it.id != first.id }, null,
                                onSelect = { second -> sheet = ""; splitFirstId = null; launcherActivity.launchSplit(first, second) },
                                onClear = {}, onLongClick = {},
                                title = "Open ${first.label} with…")
                        }
                        "pins" -> Column(Modifier.fillMaxHeight(.9f).imePadding()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { sheet = "" }) { Text("Done") }
                            }
                            AppLibrary(state, pinQuery, { pinQuery = it }, onLaunch, model::setPinned,
                                onActions = { selectedId = it.id; sheet = "" }, editing = true, modifier = Modifier.weight(1f).fillMaxWidth(),
                                onTurnOnWork = { model.turnOnWork(it) })
                        }
                        "settings", "settings:wallpaper" -> CustomizationSheet(state, wide, model, isDefaultHome,
                            page = activeCustomizationPage, onPage = { customizationPage = it; sheet = "settings" },
                            onMakeDefault = { sheet = ""; onMakeDefault() },
                            onClose = { customizationPage = CustomizationPage.OVERVIEW; sheet = "" }, onEditPins = { sheet = "pins" },
                            onWidget = { widgetSlot = it; widgetPackage = null; widgetProfileSerial = null; widgetExactTarget = false; sheet = "widgets" },
                            onAddWidget = { page -> widgetSlot = model.nextWidgetSlot(); widgetTargetIndex = page * HOME_CELLS; widgetPackage = null; widgetProfileSerial = null; widgetExactTarget = false; sheet = "widgets" },
                            onRemoveWidget = widgets::remove,
                            onExportLayout = { sheet = ""; launcherActivity.backups.startExport() },
                            onImportLayout = { sheet = ""; launcherActivity.backups.startImport() },
                            appearance = appearance, onAppearanceMode = onAppearanceMode,
                            onAppearanceManual = onAppearanceManual, onAppearanceDeviceLocation = onAppearanceDeviceLocation,
                            onAppearanceClear = onAppearanceClear,
                            onShadeSetup = { sheet = ""; onShadeSetup() },
                            extras = extras,
                            onWallpaperColor = onWallpaperColor,
                            onTiltHighlight = onTiltHighlight,
                            onTiltStrength = onTiltStrength,
                            backgrounds = launcherActivity.backgrounds,
                            onWallpaperPreview = { sheet = ""; onWallpaperPreview() }, homePage = pager.currentPage.coerceIn(0, homePages - 1),
                            feed = feed, onFeedRefresh = onFeedRefresh,
                            onAddFeed = onAddFeed, onRemoveFeed = onRemoveFeed, onSourceEnabled = onSourceEnabled,
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
                            onAutoUpdate = onAutoUpdate)
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
                                stackMembers = WidgetStacks.members(widgetSlot).map { it to widgets.label(it) },
                                onStackWith = if (state.widgetPlacements.any { it.slot != widgetSlot && it.id >= 0 && widgets.manager.getAppWidgetInfo(it.id) != null }
                                    && WidgetStacks.members(widgetSlot).size < StackRules.MAX_MEMBERS) {{ sheet = "stackPick" }} else null,
                                onRemoveFromStack = { memberId ->
                                    WidgetStacks.remove(widgetSlot, memberId); widgets.pruneUnusedIds()
                                })
                        }
                        "largeFolder" -> LargeFolderEditor(widgetSlot, state.apps) { sheet = "widgetActions" }
                        "stackPick" -> Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Stack which widget?", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                                IconButton(onClick = { sheet = "widgetActions" }) { Icon(Icons.Rounded.Close, "Cancel") }
                            }
                            Text("It moves into this widget's stack and leaves its spot on Home free.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            state.widgetPlacements.filter { it.slot != widgetSlot && it.id >= 0 && widgets.manager.getAppWidgetInfo(it.id) != null }
                                .forEach { other ->
                                    ActionRow(Icons.Rounded.Widgets, "${widgets.label(other.id)}  ·  page ${other.page + 1}", {
                                        // Remember it as a stack member first, so removing its grid spot doesn't unbind it.
                                        if (WidgetStacks.add(widgetSlot, other.id)) { widgets.remove(other.slot) }
                                        sheet = ""
                                    }, Modifier.testTag("stack-pick-${other.slot}"))
                                }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
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
                VisualWidgetPicker(catalog, catalogProfiles.ifEmpty { listOf(selectedProfile) }, selectedProfile, glass = controlGlass,
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
                            TextButton(onClick = widgetPickerBack) { Text("Back to widgets") }
                            if (session.candidate != null) Text("Replace here", color = Ink,
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
                            }, modifier = Modifier.testTag("widget-placement-apply")) { Text("Place") }
                            TextButton(onClick = { leaveTemporaryWidgetPage(); widgetSession = null; sheet = ""; widgetPackage = null },
                                modifier = Modifier.testTag("widget-placement-cancel")) { Text("Cancel") }
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
                                        Text("No room here", color = Color.White, fontWeight = FontWeight.SemiBold)
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
                                        contentAlignment = Alignment.Center) { Text("No room here", color = Color.White) }
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
        androidx.compose.animation.AnimatedVisibility(spotlightOpen,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it / 4 },
            exit = androidx.compose.animation.fadeOut()) {
            Spotlight(controlGlass, state.apps, feed.shownEntries, extras?.store?.state?.contactSearch == true, handoffResolver,
                onLaunch = { app -> spotlightOpen = false; onLaunchFrom(app, null) },
                onOpenFeedLink = { link -> spotlightOpen = false; onFeedOpenEntry(link) },
                onOpenContact = { contact -> spotlightOpen = false; ContactsSearch.open(launcherActivity, contact) },
                onDismiss = { spotlightOpen = false })
        }
        androidx.compose.animation.AnimatedVisibility(controlPanelOpen,
            enter = androidx.compose.animation.slideInVertically { -it / 2 } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { -it / 2 } + androidx.compose.animation.fadeOut()) {
            ControlPanel(controlGlass, extras?.store?.state ?: ExtrasState(), state.apps,
                onToggleFocus = { extras?.toggleFocus?.invoke() },
                onLaunchApp = { app -> controlPanelOpen = false; onLaunch(app) },
                onCustomize = { controlPanelOpen = false; customizationPage = CustomizationPage.PANEL; sheet = "settings" },
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
                Image(app.icon.asImageBitmap(), "Moving ${app.label}", Modifier
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
                Text("Dock full • Move an app out first",
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
                    Text("Remove", fontSize = 11.sp, maxLines = 1)
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
                fun resizeRowTop(row: Int) = if (row <= 2) row * resizeTopPitch else 2 * resizeTopPitch + (row - 2) * resizeAppPitch
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
                                    resizeHeight = (startHeight + (dy / resizePitchY).roundToInt()).coerceIn(minH, maxH)
                            })
                        }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.OpenInFull, "Drag to resize widget", tint = Ink, modifier = Modifier.size(22.dp))
                    }
                    Row(Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                        .background(Glass.copy(alpha = .96f), Corner.medium)) {
                        TextButton(onClick = { resizeSlot = null }) { Text("Cancel") }
                        TextButton(enabled = valid, onClick = {
                            model.resizeWidget(slot, resizeWidth, resizeHeight); resizeSlot = null
                        }) { Text("Apply") }
                    }
                    if (!feasible) Text("Move this widget into the six-row grid before resizing.",
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
                    shortcuts = shortcuts, onShortcut = { AppShortcuts.start(launcherActivity, it); selectedId = null })
            }
        }
        emptyCellIndex?.let { index ->
            GlassModalSheet(controlGlass, onDismissRequest = { emptyCellIndex = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                EmptySpaceActionSheet(onWidgets = {
                        widgetTargetIndex = index; widgetExactTarget = true; widgetSlot = model.nextWidgetSlot(); widgetPackage = null; widgetProfileSerial = null
                        emptyCellIndex = null; sheet = "widgets"
                    }, onWallpaper = { emptyCellIndex = null; sheet = "settings:wallpaper" },
                    onCustomize = { emptyCellIndex = null; sheet = "settings" }, onClose = { emptyCellIndex = null })
            }
        }
        createFolderFirstId?.let { firstId ->
            val first = appsById[firstId]
            AlertDialog(onDismissRequest = { createFolderFirstId = null }, title = { Text("Create folder with ${first?.label ?: "app"}") },
                text = { LazyColumn(Modifier.heightIn(max = 420.dp).testTag("folder-app-picker")) {
                    items(state.apps.filter { it.id != firstId && it.available }, key = { it.id }) { second ->
                        TextButton(onClick = {
                            val preferredPage = state.layout.indexOfShortcut(firstId)?.let(::homeCellPage)
                                ?.takeIf { it >= 0 || expandedWorkspace } ?: lastHomePage.coerceIn(0, homePages - 1)
                            val blocked = state.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() }
                            val targetIndex = (0 until HOME_CELLS).map { homeCellIndex(preferredPage, it) }
                                .firstOrNull { it !in blocked && state.layout.slotAt(it) in listOf(null, firstId, second.id) }
                            if (targetIndex != null) model.createFolder(firstId, second.id, targetIndex)
                            createFolderFirstId = null
                        }, modifier = Modifier.fillMaxWidth().testTag("folder-app-${second.id}")) {
                            Text(second.label, Modifier.fillMaxWidth())
                        }
                    }
                } }, confirmButton = { TextButton(onClick = { createFolderFirstId = null }) { Text("Cancel") } })
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
                    onDismiss = { openFolderId = null }, onRename = { model.renameFolder(id, it) },
                    onLaunch = onLaunchFrom,
                    onMoveOut = { appId, destination ->
                        if (model.removeAppFromFolder(id, appId, destination)) openFolderId = model.folder(id)?.id
                    },
                    glassBackdrop = homeBackdrop.combined.takeIf { glassEnabled },
                    glassTint = glassTint.copy(alpha = .90f),
                    settings = glassSettings)
            } ?: LaunchedEffect(id) { openFolderId = null }
        }
        launcherActivity.backups.preview?.let { preview ->
            LayoutRestorePreview(preview, onRestore = {
                launcherActivity.backups.applyImport(); sheet = ""
            }, onCancel = launcherActivity.backups::cancelImport)
        }
        if (launcherActivity.backups.pickerPending) AlertDialog(onDismissRequest = {},
            title = { Text("Layout document") },
            text = { Text("The system document picker is still open. Return to it to finish, or cancel this operation.") },
            confirmButton = { TextButton(onClick = { launcherActivity.backups.resumePendingPicker() },
                modifier = Modifier.testTag("backup-picker-resume")) { Text("Resume") } },
            dismissButton = { TextButton(onClick = launcherActivity.backups::cancelImport,
                modifier = Modifier.testTag("backup-picker-cancel")) { Text("Cancel") } })
        if (launcherActivity.backgrounds.pickerPending && !launcherActivity.backgrounds.loading) AlertDialog(
            onDismissRequest = {}, title = { Text("Background photo") },
            text = { Text("The photo picker was interrupted. Resume choosing a photo, or cancel and keep the current background.") },
            confirmButton = { TextButton(onClick = launcherActivity.backgrounds::choosePhoto,
                modifier = Modifier.testTag("background-picker-resume")) { Text("Resume") } },
            dismissButton = { TextButton(onClick = launcherActivity.backgrounds::cancelPendingSelection,
                modifier = Modifier.testTag("background-picker-cancel")) { Text("Cancel") } })
        (launcherActivity.backups.errorMessage ?: launcherActivity.backups.successMessage)?.let { message ->
            AlertDialog(onDismissRequest = launcherActivity.backups::clearMessage,
                title = { Text(if (launcherActivity.backups.errorMessage != null) "Layout backup problem" else "Layout backup") },
                text = { Text(message) }, confirmButton = { TextButton(onClick = launcherActivity.backups::clearMessage) { Text("OK") } })
        }
        widgets.failureMessage?.let { message ->
            AlertDialog(onDismissRequest = widgets::clearFailure, title = { Text("Widget not added") },
                text = { Text(message, Modifier.testTag("widget-bind-error")) },
                confirmButton = { TextButton(onClick = widgets::clearFailure) { Text("OK") } })
        }
        if (widgets.pendingPlacement != null && widgets.setupStatus != null) {
            AlertDialog(onDismissRequest = {}, title = { Text("Finish widget setup") },
                text = { Text("The widget is waiting at its chosen spot. Finish setup to add it, or cancel to remove the placeholder.") },
                confirmButton = { Button(onClick = widgets::finishPendingSetup,
                    modifier = Modifier.semantics { contentDescription = "Continue widget setup" }) { Text("Finish setup") } },
                dismissButton = { TextButton(onClick = { leaveTemporaryWidgetPage(); widgets.cancelPendingSetup() },
                    modifier = Modifier.semantics { contentDescription = "Cancel widget setup" }) { Text("Cancel") } })
        }
        widgets.reconfigureWidgetId?.let {
            AlertDialog(onDismissRequest = {}, title = { Text("Widget settings") },
                text = { Text("Widget settings were interrupted. Resume configuration, or cancel and keep the widget unchanged.") },
                confirmButton = { Button(onClick = widgets::finishPendingReconfigure,
                    modifier = Modifier.testTag("widget-reconfigure-resume")) { Text("Resume") } },
                dismissButton = { TextButton(onClick = widgets::cancelPendingReconfigure,
                    modifier = Modifier.testTag("widget-reconfigure-cancel")) { Text("Cancel") } })
        }
        }
    }
}

@Composable
private fun ExpandedWorkspace(
    nativePager: androidx.compose.foundation.pager.PagerState,
    motion: WorkspacePageMotion,
    firstHome: Int,
    /** Width of the scrolling viewport that WorkspacePageMotion is built on. */
    viewportWidth: Dp,
    /** Extra clip width past the viewport (under the dock) where panes stay visible. */
    trailingOverscan: Dp,
    visibleHomePages: Int,
    panelWidth: Dp,
    contentHeight: Dp,
    bottomSpace: Dp,
    geometry: HomeGeometry,
    state: LauncherState,
    previewSlots: List<String?>,
    previewLeadingSlots: List<String?>,
    previewWidgetPlacements: List<WidgetPlacement>,
    appsById: Map<String, AppEntry>,
    widgets: WidgetController,
    drag: HomeDragState,
    target: DropTarget?,
    insertionTarget: DropTarget?,
    feed: FeedState,
    feedVisible: Boolean,
    onFeedRefresh: () -> Unit,
    onFeedOpenEntry: (String) -> Unit,
    onFeedAdd: () -> Unit,
    glassBackdrop: com.kyant.backdrop.Backdrop?,
    glassTint: Color,
    settings: GlassSettings,
    libraryQuery: String,
    onLibraryQuery: (String) -> Unit,
    contacts: List<ContactResult> = emptyList(),
    onContact: (ContactResult) -> Unit = {},
    handoff: HandoffResolver? = null,
    onLaunch: (AppEntry) -> Unit,
    onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit,
    onPinned: (String, Boolean) -> Unit,
    onTurnOnWork: (Long) -> Unit,
    onActions: (AppEntry) -> Unit,
    onWidget: (Int) -> Unit,
    onFolder: (String) -> Unit,
    onEmptyWidget: (Int) -> Unit,
    onRefresh: () -> Unit,
) {
    val density = LocalDensity.current
    val viewportPx = motion.pageWidth
    val overscanPx = with(density) { trailingOverscan.toPx() }
    val stride = motion.homeStride
    val initialHomeOrigin = with(density) { panelWidth.toPx() }
    val homePaneWidth = with(density) { (geometry.gridWidth + 16f).dp.toPx() }
    val stateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val visibleHomes by remember(nativePager, motion, firstHome, visibleHomePages, initialHomeOrigin, homePaneWidth, overscanPx) {
        derivedStateOf(structuralEqualityPolicy()) {
            val physicalPosition = nativePager.currentPage + nativePager.currentPageOffsetFraction
            val scroll = motion.offset(physicalPosition)
            val intersectingHomes = (0 until visibleHomePages).filter { page ->
                val start = initialHomeOrigin + page * stride
                start + homePaneWidth > scroll && start < scroll + viewportPx + overscanPx
            }
            val nearestLogicalPage = nativePager.currentPage - firstHome
            // While Discover is current, keep the initial Home pair cached. Otherwise Home 2
            // is recreated midway through the first native exit and provider inflation can
            // block the gesture frame even though that pane began offscreen.
            val retentionAnchor = nearestLogicalPage.coerceAtLeast(0)
            (intersectingHomes + (retentionAnchor - 1..retentionAnchor + 1))
                .filter { it in 0 until visibleHomePages }.distinct().sorted()
        }
    }
    val place: Modifier.(Float) -> Modifier = { x ->
        offset {
            val physicalPosition = nativePager.currentPage + nativePager.currentPageOffsetFraction
            IntOffset((x - motion.offset(physicalPosition)).roundToInt(), 0)
        }
    }
    val showDiscover by remember(nativePager, firstHome) {
        derivedStateOf(structuralEqualityPolicy()) {
            firstHome > 0 && nativePager.currentPage + nativePager.currentPageOffsetFraction <= firstHome + .25f
        }
    }
    val leadingX = initialHomeOrigin - stride
    val showLeading by remember(nativePager, motion, firstHome, panelWidth, leadingX, homePaneWidth) {
        derivedStateOf(structuralEqualityPolicy()) {
            val physicalPosition = nativePager.currentPage + nativePager.currentPageOffsetFraction
            val scroll = motion.offset(physicalPosition)
            panelWidth.value > 0f && physicalPosition - firstHome < 1f &&
                leadingX - scroll + homePaneWidth > 0f
        }
    }
    val libraryPhysicalPage = firstHome + visibleHomePages
    val showLibrary by remember(nativePager, libraryPhysicalPage) {
        derivedStateOf(structuralEqualityPolicy()) {
            nativePager.currentPage + nativePager.currentPageOffsetFraction >= libraryPhysicalPage - 1.25f
        }
    }

    Box(Modifier.fillMaxSize().clipToBounds().testTag("expanded-workspace")) {
        // Discover and the library abut the viewport edge, so in the overscan run under the
        // dock they would peek out at rest. They stay clipped to the original viewport;
        // only Home panes slide beneath the glass.
        if (showDiscover) Box(Modifier.width(viewportWidth).fillMaxHeight().clipToBounds()) {
            key("discover-pane") {
                Box(Modifier.place(-viewportPx).width(viewportWidth).fillMaxHeight()) {
                    DiscoverContent(Modifier.fillMaxSize().padding(start = 16.dp, top = 16.dp, bottom = bottomSpace),
                        feed, feedVisible, onFeedRefresh, onFeedOpenEntry, onFeedAdd, glassBackdrop, glassTint, settings)
                }
            }
        }

        if (showLeading) {
            key("expanded-leading-home") {
                Box(Modifier.place(leadingX).width((geometry.gridWidth + 16f).dp).fillMaxHeight()
                    .testTag("expanded-leading-home")) {
                    HomePagePane(
                        -1, state, previewSlots, previewLeadingSlots, previewWidgetPlacements, appsById, geometry, contentHeight, bottomSpace,
                        widgets, drag, target, insertionTarget, showLargeWidget = true,
                        onLaunch = onLaunchFrom, onActions = onActions, onWidget = onWidget,
                        onFolder = onFolder, onEmptyWidget = onEmptyWidget, onRefresh = onRefresh,
                        modifier = Modifier,
                    )
                }
            }
        }

        visibleHomes.forEach { page ->
            key("expanded-home-$page") {
                stateHolder.SaveableStateProvider("expanded-home-$page") {
                    Box(Modifier.place(initialHomeOrigin + page * stride)
                        .width((geometry.gridWidth + 16f).dp).fillMaxHeight()) {
                        HomePagePane(
                            page, state, previewSlots, previewLeadingSlots, previewWidgetPlacements, appsById, geometry, contentHeight, bottomSpace,
                            widgets, drag, target, insertionTarget, showLargeWidget = page > 0,
                            onLaunch = onLaunchFrom, onActions = onActions, onWidget = onWidget,
                            onFolder = onFolder,
                            onEmptyWidget = onEmptyWidget,
                            onRefresh = onRefresh,
                        )
                    }
                }
            }
        }

        if (showLibrary) Box(Modifier.width(viewportWidth).fillMaxHeight().clipToBounds()) {
            key("library-pane") {
                Box(Modifier.place((visibleHomePages - 1) * stride + viewportPx).width(viewportWidth).fillMaxHeight()) {
                    AppLibrary(state, libraryQuery, onLibraryQuery, onLaunch, onPinned,
                        onActions = onActions,
                        modifier = Modifier.fillMaxSize().padding(start = 16.dp, top = 16.dp, bottom = bottomSpace)
                            .testTag("library-page"),
                        drag = drag, page = visibleHomePages, onLaunchFrom = onLaunchFrom, onTurnOnWork = onTurnOnWork,
                        contacts = contacts, onContact = onContact, handoff = handoff)
                }
            }
        }
    }
}

@Composable
private fun HomePagePane(
    page: Int,
    state: LauncherState,
    previewSlots: List<String?>,
    previewLeadingSlots: List<String?>,
    previewWidgetPlacements: List<WidgetPlacement>,
    appsById: Map<String, AppEntry>,
    geometry: HomeGeometry,
    contentHeight: Dp,
    bottomSpace: Dp,
    widgets: WidgetController,
    drag: HomeDragState,
    target: DropTarget?,
    insertionTarget: DropTarget?,
    showLargeWidget: Boolean,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onActions: (AppEntry) -> Unit,
    onWidget: (Int) -> Unit,
    onFolder: (String) -> Unit,
    onEmptyWidget: (Int) -> Unit = {},
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val homeScroll = rememberScrollState()
    var paneBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val pageStart = homeCellIndex(page, 0)
    val backgroundTarget = (pageStart until pageStart + HOME_CELLS).firstOrNull { index ->
        state.layout.slotAt(index) == null && state.widgetPlacements.none { index in it.coveredIndices() }
    } ?: pageStart
    val verticalEdge = with(LocalDensity.current) { 42.dp.toPx() }
    LaunchedEffect(drag.active, page, paneBounds) {
        while (drag.active) {
            val pointer = drag.pointer
            val amount = when {
                !paneBounds.contains(pointer) -> 0f
                pointer.y < paneBounds.top + verticalEdge && homeScroll.canScrollBackward -> -18f
                pointer.y > paneBounds.bottom - verticalEdge && homeScroll.canScrollForward -> 18f
                else -> 0f
            }
            if (amount != 0f) homeScroll.scrollBy(amount)
            delay(16)
        }
    }
    Box(modifier.testTag("home-page-$page")
        .semantics {
            onLongClick("Home options") {
                if (!drag.active) onEmptyWidget(backgroundTarget)
                !drag.active
            }
        }
        .onGloballyPositioned { paneBounds = it.boundsInRoot() }
        .width((geometry.gridWidth + 16f).dp)
        .height((contentHeight - bottomSpace).coerceAtLeast(0.dp))) {
        Box(Modifier.width(16.dp).fillMaxHeight().testTag("home-options-margin-$page")
            .pointerInput(backgroundTarget, drag.active) {
                detectTapGestures(onLongPress = {
                    if (!drag.active) onEmptyWidget(backgroundTarget)
                })
            })
        Column(Modifier.offset(x = 16.dp).width(geometry.gridWidth.dp).fillMaxHeight()
            .verticalScroll(homeScroll).padding(top = geometry.contentTop.dp, bottom = 8.dp)) {
            SharedHomeGrid(page, state.homeSlots, state.leadingSlots, previewSlots, previewLeadingSlots, previewWidgetPlacements,
                appsById, geometry, state.labels, widgets, drag, target,
                folders = state.folders, onLaunch = onLaunch, onActions = onActions, onWidget = onWidget,
                onFolder = onFolder, onEmptyWidget = onEmptyWidget)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
            if (state.error != null) Text(state.error, color = Color.White,
                modifier = Modifier.clickable(onClick = onRefresh).padding(12.dp))
        }
    }
}

@Composable
private fun CircleControl(icon: ImageVector, label: String, tag: String, visualSize: Dp, glass: PageGlass? = null, action: () -> Unit) {
    val ink = rememberAdaptiveInk(glass?.tint ?: Glass, if (glass != null) .12f else .22f)
    IconButton(onClick = action, modifier = Modifier.size(visualSize.coerceAtLeast(48.dp)).testTag(tag)) {
        Box(Modifier.size(visualSize).testTag("$tag-visual").then(ink.track).then(
            if (glass != null) Modifier.liquidGlass(glass.backdrop, CircleShape, glass.tint.copy(alpha = .12f),
                blurRadius = .75f, settings = glass.settings)
            else Modifier.background(Glass.copy(alpha = .22f), CircleShape).border(1.dp, Color.White.copy(alpha = .25f), CircleShape)),
            contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = ink.color, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SharedHomeGrid(
    page: Int,
    savedSlots: List<String?>,
    savedLeadingSlots: List<String?>,
    previewSlots: List<String?>,
    previewLeadingSlots: List<String?>,
    widgetPlacements: List<WidgetPlacement>,
    appsById: Map<String, AppEntry>,
    geometry: HomeGeometry,
    labels: Boolean,
    widgets: WidgetController,
    drag: HomeDragState,
    target: DropTarget?,
    folders: List<FolderEntry>,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onActions: (AppEntry) -> Unit,
    onWidget: (Int) -> Unit,
    onFolder: (String) -> Unit,
    onEmptyWidget: (Int) -> Unit,
) {
    val rowHeight = geometry.rowHeight
    val iconSize = geometry.iconSize
    val pageStart = homeCellIndex(page, 0)
    val pageRange = pageStart until pageStart + HOME_CELLS
    fun savedAt(index: Int) = if (page == -1) savedLeadingSlots.getOrNull(homeCellLocal(index)) else savedSlots.getOrNull(index)
    fun previewAt(index: Int) = if (page == -1) previewLeadingSlots.getOrNull(homeCellLocal(index)) else previewSlots.getOrNull(index)
    fun savedIndexOf(id: String) = if (page == -1) savedLeadingSlots.indexOf(id).takeIf { it >= 0 }?.let { homeCellIndex(-1, it) }
        else savedSlots.indexOf(id).takeIf { it >= 0 }
    fun previewIndexOf(id: String) = if (page == -1) previewLeadingSlots.indexOf(id).takeIf { it >= 0 }?.let { homeCellIndex(-1, it) }
        else previewSlots.indexOf(id).takeIf { it >= 0 }
    val draggedId = drag.source?.appId
    val homeTarget = (target as? DropTarget.Home)?.index
    val source = drag.source?.target as? DropTarget.Home
    val draggedPreviewIndex = draggedId?.let(::previewIndexOf) ?: -1
    val hiddenIndex = when {
        !drag.active || !drag.moved -> null
        homeTarget != null -> draggedPreviewIndex.takeIf { it >= 0 }
        source != null && target !is DropTarget.Dock -> draggedPreviewIndex.takeIf { it >= 0 }
        else -> null
    }
    val dimDragged = drag.active && !drag.moved && source != null
    val pending = widgets.pendingPlacement?.takeIf { it.page == page }
    val pendingIsReplacement = pending != null && widgetPlacements.any { it.slot == pending.slot }
    val pageWidgets = widgetPlacements.filter { it.page == page } + listOfNotNull(pending?.takeUnless { pendingIsReplacement })
    val renderedRows = maxOf(GRID_ROWS, pageWidgets.maxOfOrNull { it.row + it.spanY } ?: GRID_ROWS)
    val topPitch = (geometry.widgetHeight + 18f) / 2f
    fun rowTop(row: Int) = if (row <= 2) row * topPitch else geometry.widgetHeight + 18f + (row - 2) * rowHeight
    BoxWithConstraints(Modifier.fillMaxWidth().height(rowTop(renderedRows).dp)) {
        val density = LocalDensity.current
        val cellWidth = maxWidth / 4
        val cellWidthPx = with(density) { cellWidth.toPx() }
        val rowHeightPx = with(density) { rowHeight.dp.toPx() }

        repeat(HOME_CELLS) { localIndex ->
            val globalIndex = pageStart + localIndex
            val cell = DropTarget.Home(globalIndex)
            val savedId = savedAt(globalIndex)
            val savedApp = appsById[savedId]
            val savedFolder = folders.firstOrNull { it.id == savedId }
            val previewId = previewAt(globalIndex)
            val highlighted = drag.active && target == cell
            val gap = hiddenIndex == globalIndex
            val row = localIndex / GRID_COLUMNS
            val cellHeight = rowTop(row + 1) - rowTop(row)
            Box(Modifier.offset(x = cellWidth * (localIndex % GRID_COLUMNS), y = rowTop(row).dp)
                .width(cellWidth).height(cellHeight.dp).testTag("home-cell-$globalIndex")
                .dropRegion(drag, cell, savedApp?.id ?: savedFolder?.id, page)
                .combinedClickable(onClick = { savedFolder?.let { onFolder(it.id) } },
                    onLongClick = { if (savedId == null && !drag.active) onEmptyWidget(globalIndex) })
                // No frame around the cell being dragged over: only, while a drop would make a folder, a soft square behind the app below.
                .background(if (drag.folderIntentApp != null && drag.folderIntentApp == savedApp?.id) Color.White.copy(alpha = .16f) else Color.Transparent, Corner.medium),
                contentAlignment = Alignment.TopCenter) {
            }
        }

        val ids = (if (page == -1) savedLeadingSlots + previewLeadingSlots
            else savedSlots.slicePage(pageRange) + previewSlots.slicePage(pageRange)).filterNotNull().distinct()
        ids.forEach { id ->
            val savedIndex = savedIndexOf(id) ?: -1
            val previewIndex = previewIndexOf(id) ?: -1
            val renderIndex = previewIndex.takeIf { it in pageRange } ?: savedIndex.takeIf { it in pageRange } ?: return@forEach
            val app = appsById[id] ?: return@forEach
            key(id) {
                val localIndex = renderIndex - pageStart
                val row = localIndex / GRID_COLUMNS
                val animatedOffset by animateIntOffsetAsState(
                    IntOffset(((localIndex % GRID_COLUMNS) * cellWidthPx).roundToInt(), with(density) { rowTop(row).dp.toPx() }.roundToInt()),
                    label = "home insertion $id",
                )
                val visible = previewIndex in pageRange && renderIndex != hiddenIndex
                val opacity by animateFloatAsState(
                    if (dimDragged && id == draggedId) .28f else 1f,
                    label = "home insertion visibility $id",
                )
                Box(Modifier.offset { animatedOffset }.width(cellWidth).height(rowHeight.dp)
                    .alpha(opacity).testTag("home-app-$id"), contentAlignment = Alignment.TopCenter) {
                    if (visible) AppTile(app, iconSize, labels,
                        onClick = { onLaunch(app, it) }, onLongClick = { onActions(app) })
                }
            }
        }
        folders.forEach { folder ->
            val savedIndex = savedIndexOf(folder.id) ?: -1
            val previewIndex = previewIndexOf(folder.id) ?: -1
            val renderIndex = previewIndex.takeIf { it in pageRange } ?: savedIndex.takeIf { it in pageRange } ?: return@forEach
            val localIndex = renderIndex - pageStart
            val row = localIndex / GRID_COLUMNS
            val x = cellWidth * (localIndex % GRID_COLUMNS)
            val y = rowTop(row).dp
            FolderTile(folder, appsById, iconSize, labels, drag, page,
                Modifier.offset(x = x, y = y).width(cellWidth).height(rowHeight.dp)
                    .testTag("home-folder-${folder.id}"), onClick = { onFolder(folder.id) })
        }
        pageWidgets.forEach { placement ->
            key("widget-${placement.slot}") {
                val x = cellWidth * placement.column + 5.dp
                val width = (cellWidth * placement.spanX - 10.dp).coerceAtLeast(1.dp)
                val y = rowTop(placement.row)
                val height = (rowTop(placement.row + placement.spanY) - y - 18f).coerceAtLeast(48f)
                if (placement == pending) Surface(Modifier.offset(x = x, y = y.dp).width(width).height(height.dp)
                    .testTag("widget-pending-${placement.slot}").semantics(mergeDescendants = true) {
                        contentDescription = "Pending ${widgets.pendingProvider?.shortClassName ?: "widget"}"
                    }, color = Glass.copy(alpha = .72f),
                    shape = Corner.large, border = androidx.compose.foundation.BorderStroke(2.dp, Color.White)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.height(8.dp)); Text("Finish widget setup", color = Ink)
                    }
                } else MovableWidget(placement.id, placement.slot, widgets, drag, target,
                    Modifier.offset(x = x, y = y.dp).width(width).height(height.dp), page = page) { onWidget(placement.slot) }
            }
        }
    }
}

@Composable
private fun DockAppColumn(
    savedDock: List<String?>,
    previewDock: List<String?>,
    appsById: Map<String, AppEntry>,
    rowHeight: Float,
    iconSize: Float,
    drag: HomeDragState,
    target: DropTarget?,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onChoose: (Int) -> Unit,
    glass: PageGlass? = null,
) {
    val draggedId = drag.source?.appId
    val dockTarget = (target as? DropTarget.Dock)?.index
    val source = drag.source?.target as? DropTarget.Dock
    val draggedPreviewIndex = previewDock.indexOf(draggedId)
    val hiddenIndex = when {
        !drag.active || !drag.moved -> null
        dockTarget != null -> draggedPreviewIndex.takeIf { it >= 0 }
        source != null && target !is DropTarget.Home -> draggedPreviewIndex.takeIf { it >= 0 }
        else -> null
    }
    val dimDragged = drag.active && !drag.moved && source != null
    val launchBounds = remember(savedDock.size) { List(savedDock.size) { android.graphics.Rect() } }
    val interactions = remember(savedDock.size) { List(savedDock.size) { MutableInteractionSource() } }
    val slotProgress = savedDock.indices.map { index ->
        rememberPressProgress(interactions[index]).value
    }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { rowHeight.dp.toPx() }
    Box(Modifier.fillMaxWidth().height((rowHeight * savedDock.size).dp)) {
        savedDock.indices.forEach { index ->
            val cell = DropTarget.Dock(index)
            val savedApp = appsById[savedDock[index]]
            val previewId = previewDock.getOrNull(index)
            val highlighted = drag.active && target == cell
            val gap = hiddenIndex == index
            Box(Modifier.fillMaxWidth().height(rowHeight.dp).offset(y = (rowHeight * index).dp),
                contentAlignment = Alignment.Center) {
                when {
                    previewId == null -> Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(rowHeight.dp).offset(y = (rowHeight * index).dp)
                .testTag("dock-slot-$index").dropRegion(drag, cell, savedApp?.id)
                .semantics(mergeDescendants = true) { contentDescription = savedApp?.label ?: "Choose dock app ${index + 1}" }
                .combinedClickable(interactionSource = interactions[index], indication = LocalIndication.current, role = Role.Button, onClick = {
                    if (savedApp != null) onLaunch(savedApp, launchBounds[index]) else onChoose(index)
                }, onLongClick = null)
                .semantics { onLongClick("Choose dock app") { onChoose(index); true } })
        }

        val ids = (savedDock + previewDock).filterNotNull().distinct()
        ids.forEach { id ->
            val savedIndex = savedDock.indexOf(id)
            val previewIndex = previewDock.indexOf(id)
            val renderIndex = previewIndex.takeIf { it >= 0 } ?: savedIndex.takeIf { it >= 0 } ?: return@forEach
            val app = appsById[id] ?: return@forEach
            key(id) {
                // Each icon is recorded on its own so a press lens can magnify it (one layer backdrop holds one layer).
                val iconBackdrop = rememberLayerBackdrop()
                val lensBackdrop = if (glass != null) rememberCombinedBackdrop(glass.backdrop, iconBackdrop) else null
                val animatedOffset by animateIntOffsetAsState(
                    IntOffset(0, (renderIndex * rowHeightPx).roundToInt()), label = "dock insertion $id")
                val visible = previewIndex >= 0 && renderIndex != hiddenIndex
                val opacity by animateFloatAsState(
                    if (!visible) 0f else if (dimDragged && id == draggedId) .28f else 1f,
                    label = "dock insertion visibility $id",
                )
                Box(Modifier.offset { animatedOffset }.fillMaxWidth().height(rowHeight.dp).alpha(opacity)
                    .testTag("dock-app-$id"), contentAlignment = Alignment.Center) {
                    Image(app.icon.asImageBitmap(), null, Modifier.size(iconSize.dp).testTag("dock-icon-$id")
                        .then(if (lensBackdrop != null) Modifier.layerBackdrop(iconBackdrop) else Modifier)
                        .onGloballyPositioned { if (savedIndex >= 0) launchBounds[savedIndex].set(it.boundsInWindow().toAndroidBounds()) }
                        .graphicsLayer {
                            val p = slotProgress[renderIndex]
                            scaleX = 1f - .08f * p; scaleY = 1f - .08f * p
                        }
                        .pressGlow(slotProgress[renderIndex], Corner.icon))
                    Box(Modifier.size(iconSize.dp)) { AppBadge(badgeCount(app.packageName), Modifier.align(Alignment.TopEnd)) }
                    // The press lens: clear glass that lifts and magnifies the icon under your finger.
                    // A plain rounded shape, not Corner.icon: the glass library rejects squircle outlines.
                    val press = slotProgress[renderIndex]
                    if (glass != null && lensBackdrop != null && press > .02f) Box(
                        Modifier.size((iconSize + 14f).dp).graphicsLayer { alpha = press.coerceIn(0f, 1f) }
                            .glassLens(lensBackdrop, RoundedCornerShape(percent = 24), magnification = 1.14f,
                                lift = press, settings = glass.settings)
                            .testTag("dock-lens-$id"))
                }
            }
        }
    }
}

private fun <T> List<T>.slicePage(range: IntRange): List<T> =
    if (isEmpty() || range.first >= size) emptyList() else subList(range.first, minOf(range.last + 1, size))

@Composable
private fun FolderTile(folder: FolderEntry, apps: Map<String, AppEntry>, size: Float, labels: Boolean,
    drag: HomeDragState, page: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val progress by rememberPressProgress(interaction)
    Column(modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = "Folder ${folder.title}, ${folder.appIds.size} apps" },
        horizontalAlignment = Alignment.CenterHorizontally) {
        val folderGlass = LocalPageGlass.current
        // The dock's glass look: a see-through tint with a bright, fading rim (white at the top-left, soft at the bottom-right) when
        // glass is on; the flat tile otherwise. It is drawn with plain fills, not the backdrop lens: a folder tile sits straight over the
        // wallpaper, where the lens has almost nothing to bend, and the lens on every tile crashed the view tree when it was torn down.
        Box(Modifier.size(size.dp)
            .then(if (folderGlass != null) Modifier.clip(Corner.icon)
                    .background(folderGlass.tint.copy(alpha = .34f))
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White.copy(alpha = .26f), Color.White.copy(alpha = .05f))))
                    .border(1.2.dp, androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color.White.copy(alpha = .85f), Color.White.copy(alpha = .14f),
                        Color.White.copy(alpha = .5f))), Corner.icon)
                else Modifier.clip(Corner.icon).background(Glass.copy(alpha = .72f)).border(1.dp, Color.White.copy(alpha = .55f), Corner.icon))
            .graphicsLayer { scaleX = 1f - .05f * progress; scaleY = 1f - .05f * progress }
            .dropRegion(drag, DropTarget.Folder(folder.id), page = page, folderId = folder.id)
            .pressGlow(progress, Corner.icon)
            .testTag("folder-drop-${folder.id}")) {
            // Up to nine apps in a 3x3 grid, like iOS; two to four apps get the roomier 2x2 so a small folder stays readable.
            val shown = folder.appIds.take(FolderPreview.capacity(folder.appIds.size))
            val columns = FolderPreview.columns(shown.size)
            Column(Modifier.fillMaxSize().padding((size * .08f).dp), verticalArrangement = Arrangement.SpaceEvenly) {
                shown.chunked(columns).forEach { rowIds ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        rowIds.forEach { id ->
                            val app = apps[id]
                            if (app != null) Image(app.icon.asImageBitmap(), null, Modifier.size((size * FolderPreview.iconFraction(columns)).dp).clip(Corner.icon))
                            else Spacer(Modifier.size((size * FolderPreview.iconFraction(columns)).dp))
                        }
                    }
                }
            }
            // One badge for the whole folder: everything unread inside it.
            AppBadge(FolderPreview.unread(folder.appIds.mapNotNull { apps[it]?.packageName }.map(::badgeCount)), Modifier.align(Alignment.TopEnd))
        }
        if (labels) Text(folder.title, color = Color.White, fontSize = 11.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun AppTile(app: AppEntry, size: Float, labels: Boolean, modifier: Modifier = Modifier, onClick: (android.graphics.Rect) -> Unit, onLongClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val progress by rememberPressProgress(interaction)
    val iconSize by animateDpAsState(size.dp, label = "icon size")
    val bounds = remember { android.graphics.Rect() }
    Column(modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) { contentDescription = app.label }
        .clickable(interactionSource = interaction, indication = LocalIndication.current,
            role = Role.Button, onClick = { onClick(bounds) })
        .semantics { onLongClick("App options") { onLongClick(); true } }.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Image(app.icon.asImageBitmap(), null, Modifier.size(iconSize).onGloballyPositioned { bounds.set(it.boundsInWindow().toAndroidBounds()) }
                .graphicsLayer { scaleX = 1f - .08f * progress; scaleY = 1f - .08f * progress }
                .pressGlow(progress, Corner.icon))
            AppBadge(badgeCount(app.packageName), Modifier.align(Alignment.TopEnd))
        }
        if (labels) Text(app.label, color = Color.White, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .55f), Offset(0f, 1f), 3f)), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
internal fun GlassCard(modifier: Modifier = Modifier, padding: androidx.compose.ui.unit.Dp = 14.dp, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val glass = LocalPageGlass.current
    // Built-in widget text follows the wallpaper behind it: white on dark, dark ink on pale.
    val ink = rememberAdaptiveInk(glass?.tint ?: Glass, if (glass != null) .14f else .24f)
    CompositionLocalProvider(LocalGlassInk provides ink.glassInk) {
        if (glass != null) {
            Column(modifier.fillMaxSize().then(ink.track).liquidGlass(glass.backdrop, Corner.large, glass.tint.copy(alpha = .14f),
                blurRadius = 1f, settings = glass.settings).clip(Corner.large).clickable(onClick = onClick)
                .padding(padding), verticalArrangement = Arrangement.SpaceBetween, content = content)
        } else {
            Surface(modifier.fillMaxSize().then(ink.track).clip(Corner.large).clickable(onClick = onClick),
                color = Glass.copy(alpha = .24f), shape = Corner.large, border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .18f))) {
                Column(Modifier.padding(padding), verticalArrangement = Arrangement.SpaceBetween, content = content)
            }
        }
    }
}

@Composable
private fun currentTime(): LocalDateTime {
    val time by produceState(LocalDateTime.now()) { while (true) { value = LocalDateTime.now(); delay(1000) } }
    return time
}

@Composable
private fun ClockCard(onClick: () -> Unit) {
    val time = currentTime()
    val format = if (android.text.format.DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm"
    GlassCard(onClick = onClick) {
        val ink = LocalGlassInk.current
        Icon(Icons.Rounded.Schedule, "Clock widget; tap to replace", tint = ink.primary, modifier = Modifier.size(20.dp))
        Text(time.format(DateTimeFormatter.ofPattern(format)), color = ink.primary, fontWeight = FontWeight.Light, fontSize = 30.sp, maxLines = 1)
        Text("Local time", color = ink.soft(), fontSize = 11.sp)
    }
}

@Composable
private fun DateCard(onClick: () -> Unit) {
    val date = currentTime()
    GlassCard(onClick = onClick) {
        val ink = LocalGlassInk.current
        Text(date.format(DateTimeFormatter.ofPattern("EEEE")), color = ink.primary, fontSize = 12.sp, maxLines = 1)
        Text(date.dayOfMonth.toString(), color = ink.primary, fontWeight = FontWeight.Light, fontSize = 40.sp, lineHeight = 42.sp)
        Text(date.format(DateTimeFormatter.ofPattern("MMMM")), color = ink.soft(), fontSize = 12.sp)
    }
}

@Composable
private fun ExpandedCard(onClick: () -> Unit) {
    val date = currentTime()
    GlassCard(onClick = onClick) {
        val ink = LocalGlassInk.current
        Column {
            Text(date.format(DateTimeFormatter.ofPattern("EEEE")), color = ink.primary, fontSize = 22.sp)
            Text(date.format(DateTimeFormatter.ofPattern("MMMM d")), color = ink.soft(), fontSize = 16.sp)
        }
        Column {
            Icon(Icons.Rounded.Widgets, null, tint = ink.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(16.dp))
            Text("A little more room.", color = ink.primary, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(12.dp))
            Text("Add a calendar, photos, or another widget.", color = ink.soft(.85f), fontSize = 14.sp)
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = onClick) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add widget") }
        }
    }
}

@Composable
private fun WidgetSlot(id: Int, slot: Int, controller: WidgetController, modifier: Modifier, onAdd: () -> Unit, fallback: @Composable () -> Unit) {
    var restoreMessage by remember(slot) { mutableStateOf<String?>(null) }
    val glass = LocalPageGlass.current
    // Glass sits behind the provider's RemoteViews; it shows through transparent widgets
    // and is covered by widgets that paint their own opaque background.
    BoxWithConstraints(modifier.then(if (glass != null) Modifier.liquidGlass(glass.backdrop, Corner.large,
        glass.tint.copy(alpha = .14f), blurRadius = 1f, settings = glass.settings) else Modifier)
        .clip(Corner.large).testTag("widget-slot-$slot")) {
        val displayedContentSize = WidgetContentSize(maxWidth.value, maxHeight.value)
        if (id == NEEDS_BINDING_WIDGET) {
            val restore = controller.restoreDescriptor(slot)
            Surface(Modifier.fillMaxSize().testTag("widget-restore-$slot"), color = Glass.copy(alpha = .88f),
                shape = Corner.large, border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = .7f))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(restore?.title ?: "Saved widget", color = Ink, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Text(restore?.profileLabel ?: "Unavailable profile", color = Ink.copy(alpha = .72f),
                        style = MaterialTheme.typography.bodySmall)
                    restoreMessage?.let { Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
                    Row {
                        TextButton(onClick = {
                            if (!controller.rebindRestoredWidget(slot, contentSize = displayedContentSize))
                                restoreMessage = "That provider or profile isn’t available. Choose a replacement."
                        },
                            modifier = Modifier.testTag("widget-restore-reconnect-$slot")) { Text("Reconnect") }
                        TextButton(onClick = onAdd, modifier = Modifier.testTag("widget-restore-replace-$slot")) { Text("Replace") }
                    }
                }
            }
            return@BoxWithConstraints
        }
        // A stack shows one widget at a time: the base widget, then each widget merged into it.
        val members = WidgetStacks.stacks[slot].orEmpty()
        var stackPage by rememberSaveable(slot) { mutableIntStateOf(0) }
        val shown = StackRules.clampPage(stackPage, members.size)
        val shownId = if (shown == 0) id else members[shown - 1]
        val info = remember(shownId) { if (shownId >= 0) controller.manager.getAppWidgetInfo(shownId) else null }
        if (info == null) { if (shown == 0) fallback() }
        else {
            key(shownId) {
                AndroidView(factory = { context -> controller.host.createView(context, shownId, info) },
                    modifier = Modifier.fillMaxSize())
            }
        }
        var nameVisible by remember { mutableStateOf(false) }
        LaunchedEffect(shown) { if (members.isNotEmpty()) { nameVisible = true; delay(1100); nameVisible = false } }
        androidx.compose.animation.AnimatedVisibility(nameVisible && members.isNotEmpty(), Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
            enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
            Text(widgetLabel(shownId, controller), color = Color.White, fontSize = 11.sp, maxLines = 1,
                modifier = Modifier.background(Color.Black.copy(alpha = .45f), Corner.pill).padding(horizontal = 10.dp, vertical = 3.dp)
                    .testTag("stack-widget-name"))
        }
        if (members.isNotEmpty()) StackRail(shown, members.size + 1, { stackPage = it },
            Modifier.align(Alignment.CenterEnd).padding(end = 4.dp))
    }
}

/** Where each visible stack rail is, in root coordinates, so the page gestures can step aside for a touch that starts on one. */
internal object StackRailRegistry { val bounds = mutableMapOf<Any, androidx.compose.ui.geometry.Rect>() }

/** The dots down the right edge of a widget stack. Tapping a dot, or dragging along the rail, switches widgets. It is a
 * rail and not a swipe on the widget itself because vertical swipes on Home already open the panel and notifications.
 */
@Composable
private fun StackRail(selected: Int, count: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    var heightPx by remember { mutableIntStateOf(1) }
    fun pick(y: Float) {
        val index = ((y / heightPx.coerceAtLeast(1)) * count).toInt().coerceIn(0, count - 1)
        if (index != selected) { UnoFeedback.play(Cue.TICK, haptic); onSelect(index) }
    }
    val registryKey = remember { Any() }
    DisposableEffect(Unit) { onDispose { StackRailRegistry.bounds.remove(registryKey) } }
    Column(modifier.width(28.dp).background(Color.Black.copy(alpha = .22f), Corner.pill).padding(vertical = 8.dp)
        .onSizeChanged { heightPx = it.height }
        // The pager above must leave a touch that starts on the rail alone, or its swipe-up (settings) and swipe-down (panels)
        // take the rail's own vertical drag.
        .onGloballyPositioned { StackRailRegistry.bounds[registryKey] = it.boundsInRoot() }
        .pointerInput(count, selected) {
            detectTapGestures { pick(it.y) }
        }.pointerInput(count, selected) {
            detectDragGestures { change, _ -> change.consume(); pick(change.position.y) }
        }.testTag("stack-rail"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(count) { index ->
            Box(Modifier.size(if (index == selected) 8.dp else 6.dp)
                .background(Color.White.copy(alpha = if (index == selected) .95f else .5f), CircleShape)
                .testTag("stack-dot-$index"))
        }
    }
}

/** A large folder may be as small as 2 x 1 or 1 x 2 (never 1 x 1), up to the whole grid. */
private val FOLDER_SPAN_CONSTRAINTS = WidgetSpanConstraints(WidgetSpan(2, 2), WidgetSpan(1, 1), WidgetSpan(GRID_COLUMNS, GRID_ROWS), true, true)

private fun widgetLabel(id: Int, controller: WidgetController) = when (id) {
    CLOCK_WIDGET -> "Clock"
    DATE_WIDGET -> "Date"
    INFO_WIDGET -> "Widget panel"
    FOLDER_WIDGET -> "Large folder"
    EMPTY_WIDGET -> "Add widget"
    else -> UnoWidgets.byId(id)?.label ?: controller.label(id)
}

@Composable
private fun MovableWidget(id: Int, slot: Int, controller: WidgetController, drag: HomeDragState,
    target: DropTarget?, modifier: Modifier, page: Int, onAdd: () -> Unit) {
    val cell = DropTarget.Widget(slot)
    WidgetSlot(id, slot, controller, modifier.dropRegion(drag, cell, page = page, widgetId = id)
        .alpha(if (drag.source?.target == cell) .3f else 1f)
        .border(if (drag.active && target == cell) 2.dp else 0.dp,
            if (drag.active && target == cell) Color.White else Color.Transparent, Corner.large)
        .semantics { onLongClick("Move or replace widget") { onAdd(); true } }, onAdd) {
        when (id) {
            CLOCK_WIDGET -> ClockCard(onAdd)
            DATE_WIDGET -> DateCard(onAdd)
            FOLDER_WIDGET -> LargeFolderFace(slot, onAdd)
            INFO_WIDGET -> if (slot % 3 == 2) ExpandedCard(onAdd) else GlassCard(onClick = onAdd) {
                val ink = LocalGlassInk.current
                Icon(Icons.Rounded.Widgets, null, tint = ink.primary, modifier = Modifier.size(28.dp))
                Text("Your widgets", color = ink.primary, fontSize = 15.sp, maxLines = 1)
                Text("Tap to choose", color = ink.soft(), fontSize = 12.sp)
            }
            else -> {
                val library = UnoWidgets.byId(id)
                if (library != null) library.face(slot, onAdd)
                else Surface(Modifier.fillMaxSize().clickable(onClick = onAdd), color = Glass.copy(alpha = .18f),
                    shape = Corner.large, border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .25f))) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Add, null, tint = Color.White)
                        Text(if (id >= 0) "Widget unavailable" else "Add widget", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPicker(apps: List<AppEntry>, dockSlot: Int?, onSelect: (AppEntry) -> Unit, onClear: () -> Unit,
    onLongClick: (AppEntry) -> Unit, canSelect: (AppEntry) -> Boolean = { true }, blockedHint: String? = null,
    title: String? = null) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) { apps.filter { it.label.contains(query.trim(), ignoreCase = true) } }
    Column(Modifier.fillMaxWidth().fillMaxHeight(.88f).padding(horizontal = 20.dp).imePadding()) {
        Text(title ?: if (dockSlot == null) "Your apps" else "Dock position ${dockSlot + 1}", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 16.dp).testTag("search-field"),
            placeholder = { Text("Search apps") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true,
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search") } }, shape = Corner.medium)
        if (dockSlot != null) TextButton(onClick = onClear) { Text("Leave this position empty") }
        if (blockedHint != null) Text(blockedHint, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp).testTag("dock-full-guidance"))
        LazyColumn(Modifier.weight(1f)) {
            if (filtered.isEmpty()) item { Text("No apps found", Modifier.padding(vertical = 24.dp)) }
            items(filtered, key = { it.id }) { app ->
                val enabled = canSelect(app)
                Row(Modifier.fillMaxWidth().testTag("picker-app-${app.id}")
                    .combinedClickable(enabled = enabled, onClick = { onSelect(app) }, onLongClick = { onLongClick(app) })
                    .alpha(if (enabled) 1f else .45f)
                    .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(app.icon.asImageBitmap(), null, Modifier.size(44.dp).clip(Corner.icon))
                    Text(app.label, Modifier.padding(start = 16.dp).weight(1f), maxLines = 2)
                    if (dockSlot != null && enabled) Icon(Icons.Rounded.Add, "Choose ${app.label}")
                }
            }
        }
    }
}

@Composable
private fun SettingSlider(label: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = 14.dp)) {
        Row { Text(label, Modifier.weight(1f)); Text(valueLabel, color = MaterialTheme.colorScheme.secondary) }
        Slider(value, onChange, valueRange = range, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun WidgetActions(
    placement: WidgetPlacement,
    constraints: WidgetSpanConstraints?,
    canConfigure: Boolean,
    onConfigure: () -> Unit,
    isValid: (Int, Int) -> Boolean,
    onResize: (Int, Int) -> Unit,
    onStartResize: (Int, Int) -> Unit,
    onMoveToPage: (Int) -> Boolean,
    homePages: Int,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
    stackMembers: List<Pair<Int, String>> = emptyList(),
    onStackWith: (() -> Unit)? = null,
    onRemoveFromStack: (Int) -> Unit = {},
    onEditFolder: (() -> Unit)? = null,
) {
    val sheetMaxHeight = with(LocalDensity.current) {
        (LocalWindowInfo.current.containerSize.height * .88f).toDp()
    }
    var width by remember(placement.slot, placement.spanX) { mutableIntStateOf(placement.spanX) }
    var height by remember(placement.slot, placement.spanY) { mutableIntStateOf(placement.spanY) }
    val minWidth = constraints?.minimum?.width ?: 2
    val minHeight = constraints?.minimum?.height ?: 2
    val maxWidth = minOf(GRID_COLUMNS - placement.column, constraints?.maximum?.width ?: GRID_COLUMNS)
    val maxHeight = minOf(GRID_ROWS - placement.row, constraints?.maximum?.height ?: GRID_ROWS)
    val feasible = placement.page >= -1 && placement.row in 0 until GRID_ROWS &&
        !(placement.id >= 0 && constraints == null) && minWidth <= maxWidth && minHeight <= maxHeight
    val valid = feasible && isValid(width, height)
    Column(Modifier.fillMaxWidth().heightIn(max = sheetMaxHeight).verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Widget options", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close widget options") }
        }
        onEditFolder?.let { ActionRow(Icons.Rounded.Folder, "Choose folder apps", it, Modifier.testTag("widget-folder-apps-${placement.slot}")) }
        onStackWith?.let { ActionRow(Icons.Rounded.Layers, "Stack another widget here", it, Modifier.testTag("widget-stack-${placement.slot}")) }
        stackMembers.forEach { (memberId, label) ->
            ActionRow(Icons.Rounded.RemoveCircleOutline, "Remove $label from this stack", { onRemoveFromStack(memberId) },
                Modifier.testTag("widget-unstack-$memberId"), tint = MaterialTheme.colorScheme.error)
        }
        if (canConfigure) ActionRow(Icons.Rounded.Settings, "Widget settings", onConfigure,
            Modifier.testTag("widget-settings-${placement.slot}"))
        Text("Resize", style = MaterialTheme.typography.titleMedium)
        Button(enabled = feasible, onClick = { onStartResize(width, height) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Resize on Home") }
        if (!feasible) Text("Move this widget into the six-row grid before resizing.", color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            repeat(homePages) { page -> TextButton(onClick = { onMoveToPage(page) },
                modifier = Modifier.testTag("widget-move-${placement.slot}-page-$page")) { Text("Move to page ${page + 1}") } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Width", Modifier.weight(1f))
            IconButton(enabled = constraints?.canResizeHorizontally != false,
                onClick = { if (feasible) width = (width - 1).coerceAtLeast(minWidth) }) {
                Icon(Icons.Rounded.Remove, "Decrease widget width")
            }
            Text("$width columns", Modifier.width(88.dp), textAlign = TextAlign.Center)
            IconButton(enabled = constraints?.canResizeHorizontally != false,
                onClick = { if (feasible) width = (width + 1).coerceAtMost(maxWidth) }) {
                Icon(Icons.Rounded.Add, "Increase widget width")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Height", Modifier.weight(1f))
            IconButton(enabled = constraints?.canResizeVertically != false,
                onClick = { if (feasible) height = (height - 1).coerceAtLeast(minHeight) }) {
                Icon(Icons.Rounded.Remove, "Decrease widget height")
            }
            Text("$height rows", Modifier.width(88.dp), textAlign = TextAlign.Center)
            IconButton(enabled = constraints?.canResizeVertically != false,
                onClick = { if (feasible) height = (height + 1).coerceAtMost(maxHeight) }) {
                Icon(Icons.Rounded.Add, "Increase widget height")
            }
        }
        Text("Sizes that overlap another item are ignored.", style = MaterialTheme.typography.bodySmall)
        if (!valid) Text("That size overlaps another item or extends beyond the page.", color = MaterialTheme.colorScheme.error)
        Button(enabled = valid, onClick = { onResize(width, height); onClose() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Apply size") }
        ActionRow(Icons.Rounded.FindReplace, "Replace", onReplace)
        HorizontalDivider()
        ActionRow(Icons.Rounded.DeleteOutline, "Remove", onRemove, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
    }
}
