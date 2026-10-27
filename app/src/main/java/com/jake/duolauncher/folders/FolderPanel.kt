package com.jake.duolauncher.folders

import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The open folder, laid out like iOS: a compact translucent panel sized to its contents (not a full-screen sheet), the title centred
 * above it and editable in place, apps as bare icons with labels in a 3x3 grid, more than nine paging sideways with dots. It springs
 * open from slightly small and closes on a tap outside, committing the title as it goes (there is no Done button).
 * [editTitle] opens it with the title focused and selected, for a folder that was only just made.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun FolderPanel(
    folder: FolderEntry, apps: Map<String, AppEntry>, drag: HomeDragState, page: Int,
    homeDestinations: List<Int>, dockVacancies: List<Int>, onDismiss: () -> Unit,
    onRename: (String) -> Unit, onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onMoveOut: (String, DropTarget) -> Unit,
    glassBackdrop: com.kyant.backdrop.Backdrop? = null,
    glassTint: Color = Glass.copy(alpha = .97f),
    settings: GlassSettings = GlassSettings.Default,
    editTitle: Boolean = false,
) {
    // One saved value is both what the field shows and what is committed on close, so the two cannot drift apart. Saved with the
    // screen so a rotation or recreate mid-edit keeps what was typed, selection included.
    var fieldValue by rememberSaveable(folder.id, stateSaver = androidx.compose.ui.text.input.TextFieldValue.Saver) {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(folder.title,
            selection = if (editTitle) androidx.compose.ui.text.TextRange(0, folder.title.length)
                else androidx.compose.ui.text.TextRange(folder.title.length))) }
    val title = fieldValue.text
    // Grow out of the folder's icon, as iOS does, and shrink back into it: from a third of the size, the transform anchored at the icon's centre.
    // It starts exactly as big as the folder's tile (the panel's width scaled to the tile's), so what grows is the tile itself, and the scale it
    // shrinks back to on close is the same, so the panel lands on the tile and the tile takes over.
    val tile = remember(folder.id) { drag.regions.values.firstOrNull { it.appId == folder.id }?.bounds }
    var panelWidth by remember { mutableStateOf(0f) }
    val closedScale = if (tile != null && panelWidth > 0f) (tile.width / panelWidth).coerceIn(.12f, .6f) else CLOSED_SCALE
    val open = remember { Animatable(CLOSED_SCALE) }
    LaunchedEffect(panelWidth > 0f) { if (panelWidth > 0f) { open.snapTo(closedScale); open.animateTo(1f, UnoMotion.open()) } }
    DisposableEffect(folder.id) { drag.openFolder = folder.id; onDispose { if (drag.openFolder == folder.id) drag.openFolder = null } }
    val iconCentre = tile?.center
    var column by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    // Closing from any route (the scrim, Back, the keyboard's Done) keeps what was typed; an emptied title keeps the old name.
    // The title is committed at once, then the panel shrinks back into the icon with the same spring it opened with, so opening
    // and closing feel matched, and only then is it dismissed.
    val commitAndClose = {
        if (title.isNotBlank()) onRename(title.trim())
        if (!closing) {
            closing = true
            scope.launch {
                open.animateTo(closedScale, UnoMotion.open())
                onDismiss()
            }
        }
    }
    BackHandler { commitAndClose() }
    DisposableEffect(drag, folder.id) {
        drag.activeSourceScope = folder.id
        onDispose { if (drag.activeSourceScope == folder.id) drag.activeSourceScope = null }
    }
    val glass = glassBackdrop != null
    val pageGlass = remember(glassBackdrop, glassTint, settings) { glassBackdrop?.let { PageGlass(it, glassTint, settings) } }
    val reveal = ((open.value - closedScale) / (1f - closedScale)).coerceIn(0f, 1f)
    Box(Modifier.fillMaxSize()
        // The screen behind goes soft and dim, as it does on iOS, rather than a flat grey.
        .then(if (glassBackdrop != null) Modifier.drawBackdrop(backdrop = glassBackdrop, shape = { androidx.compose.ui.graphics.RectangleShape },
            effects = { blur(22.dp.toPx() * reveal) }, highlight = { null }, shadow = { null },
            onDrawSurface = { drawRect(Color.Black.copy(alpha = .30f * reveal)) })
            else Modifier.background(Color.Black.copy(alpha = .42f * reveal)))
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClickLabel = "Close folder",
            onClick = commitAndClose,
        )
        .imePadding().testTag("folder-panel"),
        contentAlignment = Alignment.Center) {
        val rows = FolderPages.rows(folder.appIds.size)
        // The title above the panel and the panel itself grow and shrink together, about the folder's icon.
        val groupModifier = Modifier.fillMaxWidth(.88f).widthIn(max = 420.dp).offset(y = (-24).dp)
            .onGloballyPositioned { column = it.boundsInRoot(); if (panelWidth == 0f) panelWidth = it.size.width.toFloat() }
            .graphicsLayer {
                // The panel grows about its own centre while its centre travels from the tile's centre to its own place, so at the start it sits exactly on
                // the tile (it was anchored at the tile's centre instead, which kept that one point fixed but let the rest drift towards the screen's middle).
                scaleX = open.value; scaleY = open.value; alpha = if (panelWidth == 0f) 0f else (reveal * 1.6f).coerceIn(0f, 1f)
                val c = iconCentre
                if (c != null && column.width > 0f) {
                    translationX = (c.x - column.center.x) * (1f - reveal)
                    translationY = (c.y - column.center.y) * (1f - reveal)
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .testTag("folder-panel-content")
        // Everything on the panel is white, on a dark frosted glass: iOS's folder is that whatever the wallpaper.
        val panelTint = Color(0xFF1F2024)
        val content: @Composable () -> Unit = {
            FolderPanelBody(folder, apps, drag, page, homeDestinations, dockVacancies, onLaunch, onMoveOut, rows, pageGlass)
        }
        Column(groupModifier, horizontalAlignment = Alignment.CenterHorizontally) {
            FolderTitle(fieldValue, { fieldValue = it }, folder, editTitle, commitAndClose)
            Spacer(Modifier.height(14.dp))
            CompositionLocalProvider(LocalKitInk provides Color.White) {
                if (glassBackdrop != null) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).liquidGlass(glassBackdrop, FolderShape, panelTint, kind = GlassKind.REGULAR, settings = settings)) { content() }
                } else {
                    Surface(Modifier.fillMaxWidth().aspectRatio(1f), color = panelTint.copy(alpha = .86f), shape = FolderShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .22f))) { content() }
                }
            }
        }
    }
}

private val FolderShape = RoundedCornerShape(36.dp)
private const val CLOSED_SCALE = .34f

/** The folder's name above its panel: bare white text, centred, with a soft shadow, editable in place (no outline, label or button), as iOS shows it. */
@Composable
private fun FolderTitle(
    fieldValue: androidx.compose.ui.text.input.TextFieldValue,
    onFieldValue: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    folder: FolderEntry, editTitle: Boolean, onDone: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(editTitle, folder.id) { if (editTitle) runCatching { focus.requestFocus() } }
    val style = TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, textAlign = TextAlign.Center,
        shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = .35f), androidx.compose.ui.geometry.Offset(0f, 2f), 10f))
    BasicTextField(fieldValue, onFieldValue,
        Modifier.fillMaxWidth().focusRequester(focus).testTag("folder-name"),
        singleLine = true, textStyle = style, cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); onDone() }),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (fieldValue.text.isEmpty()) Text(stringResource(com.jake.duolauncher.R.string.folder_name), style = style.copy(color = Color.White.copy(alpha = .5f)))
                inner()
            }
        })
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FolderPanelBody(
    folder: FolderEntry,
    apps: Map<String, AppEntry>,
    drag: HomeDragState,
    page: Int,
    homeDestinations: List<Int>,
    dockVacancies: List<Int>,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onMoveOut: (String, DropTarget) -> Unit,
    rows: Int,
    pageGlass: PageGlass?,
) {
    // A folder is a square at every size, as on iOS: the panel's height is its width (three rows of three), the apps centred in it, so a folder of two
    // apps is not a thin strip.
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val pages = FolderPages.pageCount(folder.appIds.size)
        val pager = rememberPagerState { pages }
        HorizontalPager(pager, Modifier.fillMaxWidth().testTag("folder-pages"), beyondViewportPageCount = 1) { index ->
            val ids = FolderPages.page(folder.appIds, index)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ids.chunked(FolderPages.COLUMNS).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { appId ->
                            Box(Modifier.weight(1f)) {
                                apps[appId]?.let { app -> FolderChild(app, folder.id, drag, page, homeDestinations, dockVacancies,
                                    onLaunch = onLaunch, onMoveOut = onMoveOut, glass = pageGlass) }
                            }
                        }
                        repeat(FolderPages.COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (pages > 1) Row(Modifier.padding(top = 8.dp).testTag("folder-dots"), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            repeat(pages) { i ->
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i == pager.currentPage) .95f else .38f)))
            }
        }
    }
}

@Composable
private fun FolderChild(
    app: AppEntry, folderId: String, drag: HomeDragState, page: Int,
    homeDestinations: List<Int>, dockVacancies: List<Int>,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit, onMoveOut: (String, DropTarget) -> Unit,
    glass: PageGlass?,
) {
    var menu by remember { mutableStateOf(false) }
    // Holding an app here (without dragging it out) opens its move menu, in place of the ⋮ that used to sit on every icon.
    LaunchedEffect(drag.folderMenuRequest) { if (drag.folderMenuRequest == app.id) { menu = true; drag.folderMenuRequest = null } }
    // iOS shows a folder's apps as bare icons with labels on the panel, no cards. The move menu is a small dot at the icon's corner
    // rather than a card-wide kebab, so the icon stays the main target.
    // The app opens out of its icon, as it does from Home (the icon's place is passed to the launch).
    val iconBounds = remember { android.graphics.Rect() }
    Box(Modifier.fillMaxWidth().testTag("folder-child-${app.id}")) {
        Column(Modifier.fillMaxWidth().dropRegion(drag, DropTarget.Library(app.id), app.id, page,
            folderId = folderId, scope = folderId).clickable(enabled = app.available) { onLaunch(app, iconBounds) }
            .padding(horizontal = 2.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Image(app.image, null, Modifier.size(62.dp).onGloballyPositioned { iconBounds.set(it.boundsInWindow().toAndroidBounds()) }
                    .glassIcon().clip(Corner.icon).alpha(if (app.available) 1f else .45f))
                AppBadge(badgeCount(app), Modifier.align(Alignment.TopEnd))
            }
            Text(app.label, Modifier.padding(top = 5.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium.copy(color = Color.White, shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = .4f), androidx.compose.ui.geometry.Offset(0f, 1f), 4f)))
            if (app.isWork || !app.available) Text(if (app.available) app.profileLabel else "${app.profileLabel} unavailable",
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = .7f)))
        }
        // The move menu has no visible handle (iOS shows none): hold the app, or, for a screen reader or a test, activate this.
        Box(Modifier.align(Alignment.TopStart).size(1.dp).clickable { menu = true }
            .semantics { contentDescription = "Move ${app.label}" }.testTag("folder-options-${app.id}"))
        GlassDropdownMenu(menu, onDismissRequest = { menu = false }, glass = glass) {
            homeDestinations.distinctBy(::homeCellPage).forEach { destination ->
                val destinationPage = homeCellPage(destination)
                val label = if (destinationPage == -1) "Move to Unfolded-only page" else "Move to page ${destinationPage + 1}"
                GlassMenuItem(text = { Text(label) }, onClick = {
                    menu = false; onMoveOut(app.id, DropTarget.Home(destination))
                }, modifier = Modifier.testTag("folder-move-${app.id}-page-$destinationPage"))
            }
            dockVacancies.firstOrNull()?.let { dock ->
                GlassMenuItem(text = { Text(stringResource(com.jake.duolauncher.R.string.move_to_dock)) }, onClick = {
                    menu = false; onMoveOut(app.id, DropTarget.Dock(dock))
                }, modifier = Modifier.testTag("folder-move-${app.id}-dock"))
            }
            GlassMenuItem(text = { Text(stringResource(com.jake.duolauncher.R.string.remove_shortcut)) }, onClick = {
                menu = false; onMoveOut(app.id, DropTarget.Remove)
            }, modifier = Modifier.testTag("folder-remove-${app.id}"))
        }
    }
}
