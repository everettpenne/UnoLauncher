package com.jake.duolauncher

import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.island.*
import com.jake.duolauncher.feed.*
import com.jake.duolauncher.search.*
import com.jake.duolauncher.panel.*
import com.jake.duolauncher.appearance.*
import com.jake.duolauncher.folders.*
import com.jake.duolauncher.widgets.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop

@Composable
internal fun ExpandedWorkspace(
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
    onFeedToggleSave: (String) -> Unit,
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
                        feed, feedVisible, onFeedRefresh, onFeedOpenEntry, onFeedAdd, onFeedToggleSave, glassBackdrop, glassTint, settings)
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
internal fun HomePagePane(
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
    modifier: Modifier = Modifier,
    onEmptyWidget: (Int) -> Unit = {},
    onRefresh: () -> Unit,
    /** A double tap on empty Home space, when the person has asked for one to do something (lock the screen); null leaves it a plain tap. */
    onDoubleTapEmpty: (() -> Unit)? = null,
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
        .width((geometry.gridWidth + geometry.gridStart).dp)
        .height((contentHeight - bottomSpace).coerceAtLeast(0.dp))) {
        // Empty wallpaper anywhere on the page answers a long press (and a double tap, when that locks the screen), not only an empty cell or the
        // side margin: below the last row there was a dead band that did nothing. It sits behind the grid, so the cells keep their own presses.
        Box(Modifier.fillMaxSize().pointerInput(backgroundTarget, drag.active, onDoubleTapEmpty != null) {
            detectTapGestures(
                onLongPress = { if (!drag.active) onEmptyWidget(backgroundTarget) },
                onDoubleTap = onDoubleTapEmpty?.let { lock -> { _: androidx.compose.ui.geometry.Offset -> lock() } })
        }.testTag("home-options-background-$page"))
        Box(Modifier.width(geometry.gridStart.dp).fillMaxHeight().testTag("home-options-margin-$page")
            .pointerInput(backgroundTarget, drag.active) {
                detectTapGestures(onLongPress = {
                    if (!drag.active) onEmptyWidget(backgroundTarget)
                })
            })
        Column(Modifier.offset(x = geometry.gridStart.dp).width(geometry.gridWidth.dp).fillMaxHeight()
            .verticalScroll(homeScroll).padding(top = geometry.contentTop.dp, bottom = 8.dp)) {
            SharedHomeGrid(page, state.homeSlots, state.leadingSlots, previewSlots, previewLeadingSlots, previewWidgetPlacements,
                appsById, geometry, state.labels, widgets, drag, target,
                folders = state.folders, onLaunch = onLaunch, onActions = onActions, onWidget = onWidget,
                onFolder = onFolder, onEmptyWidget = onEmptyWidget, onDoubleTapEmpty = onDoubleTapEmpty)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
            if (state.error != null) Text(state.error, color = Color.White,
                modifier = Modifier.clickable(onClick = onRefresh).padding(12.dp))
        }
    }
}

@Composable
internal fun CircleControl(icon: ImageVector, label: String, tag: String, visualSize: Dp, glass: PageGlass? = null, action: () -> Unit) {
    val ink = rememberAdaptiveInk(glass?.tint ?: Glass, if (glass != null) .12f else .22f)
    IconButton(onClick = action, modifier = Modifier.size(visualSize.coerceAtLeast(48.dp)).testTag(tag)) {
        Box(Modifier.size(visualSize).testTag("$tag-visual").then(ink.track).then(
            if (glass != null) Modifier.liquidGlass(glass.backdrop, CircleShape, glass.tint, kind = GlassKind.CLEAR, settings = glass.settings)
            else Modifier.background(Glass.copy(alpha = .22f), CircleShape).border(1.dp, Color.White.copy(alpha = .25f), CircleShape)),
            contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = ink.color, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
internal fun SharedHomeGrid(
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
    onDoubleTapEmpty: (() -> Unit)? = null,
) {
    val rowHeight = geometry.rowHeight
    val iconSize = geometry.iconSize
    // The Icon Size setting draws icons inside a fixed icon box: a smaller icon stays centred on the box, so the grid and the widgets do not move.
    val iconInset = geometry.iconInset
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
    val editing = LocalHomeEditing.current
    val editActions = LocalHomeEditActions.current
    val coveredCells = remember(pageWidgets) { pageWidgets.flatMapTo(mutableSetOf()) { it.coveredIndices() } }
    // The widget band's pitch and the app rows' pitch. On the iOS layout they are the same (see iosHomeGeometry), so the grid is uniform.
    val gapY = geometry.widgetGapY
    val topPitch = (geometry.widgetHeight + gapY) / 2f
    fun rowTop(row: Int) = if (row <= 2) row * topPitch else geometry.widgetHeight + gapY + (row - 2) * rowHeight
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
            val folderGrow by animateFloatAsState(if (drag.folderIntentApp != null && drag.folderIntentApp == savedApp?.id) 1f else 0f, label = "folder intent")
            val savedFolder = folders.firstOrNull { it.id == savedId }
            val previewId = previewAt(globalIndex)
            val highlighted = drag.active && target == cell
            val gap = hiddenIndex == globalIndex
            // Where an icon could go: free cells show a faint square while something is being dragged, and the one under the finger fills in.
            // Not while Home is only being edited: iOS's jiggle mode shows the icons on the bare wallpaper, with no grid.
            val showFree = drag.active && savedId == null && globalIndex !in coveredCells
            val row = localIndex / GRID_COLUMNS
            val cellHeight = rowTop(row + 1) - rowTop(row)
            Box(Modifier.offset(x = cellWidth * (localIndex % GRID_COLUMNS), y = rowTop(row).dp)
                .width(cellWidth).height(cellHeight.dp).testTag("home-cell-$globalIndex")
                .dropRegion(drag, cell, savedApp?.id ?: savedFolder?.id, page)
                .semantics {
                    // A screen reader meets only the free cells that can take something; occupied cells are described by what sits on them.
                    if (savedId == null && globalIndex !in coveredCells) contentDescription = "Empty Home space"
                    else hideFromAccessibility()
                }
                .combinedClickable(onClick = { savedFolder?.let { onFolder(it.id) } },
                    // Only an empty cell takes a double tap: waiting for a second tap would hold up opening a folder.
                    onDoubleClick = if (savedId == null && globalIndex !in coveredCells) onDoubleTapEmpty else null,
                    onLongClickLabel = if (savedId == null) "Home options" else null,
                    onLongClick = { if (savedId == null && !drag.active) onEmptyWidget(globalIndex) })
                // No frame around the cell being dragged over: only, while a drop would make a folder, a soft square behind the app below.
                .graphicsLayer { val scale = 1f + .10f * folderGrow; scaleX = scale; scaleY = scale }
                .background(Color.White.copy(alpha = .20f * folderGrow), Corner.medium),
                contentAlignment = Alignment.TopCenter) {
                if (showFree) Box(Modifier.padding(top = (2f + iconInset).dp).size(iconSize.dp).background(Color.White.copy(alpha = if (highlighted) .28f else .09f), Corner.icon)
                    .testTag("home-free-$globalIndex"))
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
                    if (visible) {
                        // Editing: the icon wiggles (but not the one being dragged), a tap does not open it, and its badge removes it.
                        val still = !editing || (drag.active && draggedId == id)
                        Box(Modifier.padding(top = iconInset.dp).homeWiggle(!still, id.hashCode())) {
                            AppTile(app, iconSize, labels,
                                onClick = { if (!editing) onLaunch(app, it) }, onLongClick = { onActions(app) })
                        }
                        if (editing && !drag.active) RemoveBadge(app.label,
                            Modifier.align(Alignment.TopStart).offset(x = (cellWidth - iconSize.dp) / 2 - 14.dp, y = (iconInset - 10f).dp)) { editActions?.remove(app) }
                    }
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
                Modifier.offset(x = x, y = y).width(cellWidth).height(rowHeight.dp).padding(top = iconInset.dp)
                    .testTag("home-folder-${folder.id}"), onClick = { onFolder(folder.id) })
        }
        pageWidgets.forEach { placement ->
            key("widget-${placement.slot}") {
                val x = cellWidth * placement.column + (geometry.widgetGapX / 2f).dp
                val width = (cellWidth * placement.spanX - geometry.widgetGapX.dp).coerceAtLeast(1.dp)
                val y = rowTop(placement.row)
                val height = (rowTop(placement.row + placement.spanY) - y - gapY).coerceAtLeast(48f)
                if (placement == pending) Surface(Modifier.offset(x = x, y = y.dp).width(width).height(height.dp)
                    .testTag("widget-pending-${placement.slot}").semantics(mergeDescendants = true) {
                        contentDescription = "Pending ${widgets.pendingProvider?.shortClassName ?: "widget"}"
                    }, color = Glass.copy(alpha = .72f),
                    shape = Corner.large, border = androidx.compose.foundation.BorderStroke(2.dp, Color.White)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.height(8.dp)); Text(stringResource(com.jake.duolauncher.R.string.finish_widget_setup), color = Ink)
                    }
                } else {
                    // Jiggle mode: widgets wiggle with the icons (a little less, being bigger) and carry the same remove badge, as on iOS.
                    val widgetStill = !editing || drag.source?.target == DropTarget.Widget(placement.slot)
                    MovableWidget(placement.id, placement.slot, widgets, drag, target,
                        Modifier.offset(x = x, y = y.dp).width(width).height(height.dp).homeWiggle(!widgetStill, placement.slot * 31 + 7, scale = .45f),
                        page = page) { onWidget(placement.slot) }
                    if (editing && !drag.active) RemoveBadge(widgetLabel(placement.id, widgets),
                        Modifier.offset(x = x - 14.dp, y = (y - 10f).dp)) { editActions?.removeWidget?.invoke(placement.slot) }
                }
            }
        }
    }
}

@Composable
internal fun DockAppColumn(
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
    /** The iOS dock: the four apps side by side in a row [rowHeight] tall, each in a quarter of its width. */
    horizontal: Boolean = false,
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
    BoxWithConstraints(Modifier.fillMaxWidth().height((if (horizontal) rowHeight else rowHeight * savedDock.size).dp)) {
        // One slot's size and where slot [i] is: down a column, or across a row.
        val slotWidth = if (horizontal) maxWidth / savedDock.size.coerceAtLeast(1) else maxWidth
        val slotWidthPx = with(density) { slotWidth.toPx() }
        fun slotOffset(i: Int) = if (horizontal) IntOffset((i * slotWidthPx).roundToInt(), 0) else IntOffset(0, (i * rowHeightPx).roundToInt())
        savedDock.indices.forEach { index ->
            val cell = DropTarget.Dock(index)
            val savedApp = appsById[savedDock[index]]
            val previewId = previewDock.getOrNull(index)
            val highlighted = drag.active && target == cell
            val gap = hiddenIndex == index
            Box(Modifier.offset { slotOffset(index) }.width(slotWidth).height(rowHeight.dp),
                contentAlignment = Alignment.Center) {
                when {
                    previewId == null -> Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            Box(Modifier.offset { slotOffset(index) }.width(slotWidth).height(rowHeight.dp)
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
                val animatedOffset by animateIntOffsetAsState(slotOffset(renderIndex), label = "dock insertion $id")
                val visible = previewIndex >= 0 && renderIndex != hiddenIndex
                val opacity by animateFloatAsState(
                    if (!visible) 0f else if (dimDragged && id == draggedId) .28f else 1f,
                    label = "dock insertion visibility $id",
                )
                Box(Modifier.offset { animatedOffset }.width(slotWidth).height(rowHeight.dp).alpha(opacity)
                    .testTag("dock-app-$id"), contentAlignment = Alignment.Center) {
                    Image(app.image, null, Modifier.size(iconSize.dp).testTag("dock-icon-$id")
                        .then(if (lensBackdrop != null) Modifier.layerBackdrop(iconBackdrop) else Modifier)
                        .onGloballyPositioned { if (savedIndex >= 0) launchBounds[savedIndex].set(it.boundsInWindow().toAndroidBounds()) }
                        .graphicsLayer {
                            val p = slotProgress[renderIndex]
                            scaleX = 1f - .08f * p; scaleY = 1f - .08f * p
                        }
                        .glassIcon()
                        .pressGlow(slotProgress[renderIndex], Corner.icon))
                    Box(Modifier.size(iconSize.dp)) { AppBadge(badgeCount(app), Modifier.align(Alignment.TopEnd)) }
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

internal fun <T> List<T>.slicePage(range: IntRange): List<T> =
    if (isEmpty() || range.first >= size) emptyList() else subList(range.first, minOf(range.last + 1, size))

@Composable
internal fun FolderTile(folder: FolderEntry, apps: Map<String, AppEntry>, size: Float, labels: Boolean,
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
                    .background(folderGlass.tint.copy(alpha = if (GlassFinish.clear) .16f else .34f))
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
                            if (app != null) Image(app.image, null, Modifier.size((size * FolderPreview.iconFraction(columns)).dp).clip(Corner.icon))
                            else Spacer(Modifier.size((size * FolderPreview.iconFraction(columns)).dp))
                        }
                    }
                }
            }
            // One badge for the whole folder: everything unread inside it.
            AppBadge(FolderPreview.unread(folder.appIds.mapNotNull { apps[it]?.packageName }.map(::badgeCount)), Modifier.align(Alignment.TopEnd))
        }
        if (labels) HomeLabel(folder.title)
    }
}

@Composable
internal fun AppTile(app: AppEntry, size: Float, labels: Boolean, modifier: Modifier = Modifier, onClick: (android.graphics.Rect) -> Unit, onLongClick: () -> Unit) {
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
            Image(app.image, null, Modifier.size(iconSize).onGloballyPositioned { bounds.set(it.boundsInWindow().toAndroidBounds()) }
                .graphicsLayer { scaleX = 1f - .08f * progress; scaleY = 1f - .08f * progress }
                .glassIcon()
                .pressGlow(progress, Corner.icon))
            AppBadge(badgeCount(app), Modifier.align(Alignment.TopEnd))
        }
        if (labels) HomeLabel(app.label)
    }
}

/** An icon's name on Home: plain white with a soft shadow, as iOS's are. Over a bright part of the wallpaper (where white alone measured about
 * 2:1) a soft dark halo is blurred behind the letters to darken the wallpaper locally; over a dark part there is none. It used to be there
 * everywhere, a dark smudge behind every name. The halo copy is hidden from screen readers.
 */
@Composable
internal fun HomeLabel(text: String, modifier: Modifier = Modifier) {
    // The same wallpaper reading as the glass ink: dark ink means a bright wallpaper behind this label.
    val behind = rememberAdaptiveInk(Color.Transparent, 0f)
    val halo by animateFloatAsState(if (behind.color.luminance() < .5f) .5f else 0f, label = "label halo")
    Box(modifier.padding(top = 4.dp).then(behind.track), contentAlignment = Alignment.Center) {
        if (halo > .01f) Text(text, color = Color.Black.copy(alpha = halo), fontSize = LABEL_SP.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.blur(4.dp, BlurredEdgeTreatment.Unbounded).clearAndSetSemantics { })
        Text(text, color = Color.White, fontSize = LABEL_SP.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .45f), Offset(0f, 1f), 4f)))
    }
}

/** Icon names are 12 sp (they were 11) and medium weight, which carries better over a busy wallpaper. */
internal const val LABEL_SP = 12
