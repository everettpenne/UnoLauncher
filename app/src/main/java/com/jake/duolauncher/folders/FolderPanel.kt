package com.jake.duolauncher.folders

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
import androidx.compose.ui.draw.scale
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
import androidx.compose.material.icons.rounded.MoreVert
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
    // Closing from any route (the scrim, Back, the keyboard's Done) keeps what was typed; an emptied title keeps the old name.
    val commitAndClose = { if (title.isNotBlank()) onRename(title.trim()); onDismiss() }
    BackHandler { commitAndClose() }
    DisposableEffect(drag, folder.id) {
        drag.activeSourceScope = folder.id
        onDispose { if (drag.activeSourceScope == folder.id) drag.activeSourceScope = null }
    }
    // Spring open from the icon's scale, as iOS does; a plain fade would feel like a different feature.
    val open = remember { Animatable(.86f) }
    LaunchedEffect(Unit) { open.animateTo(1f, spring(dampingRatio = .72f, stiffness = 420f)) }
    val glass = glassBackdrop != null
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f * ((open.value - .86f) / .14f).coerceIn(0f, 1f)))
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClickLabel = "Close folder",
            onClick = commitAndClose,
        )
        .imePadding().testTag("folder-panel"),
        contentAlignment = Alignment.Center) {
        val rows = FolderPages.rows(folder.appIds.size)
        val panelModifier = Modifier.fillMaxWidth(.88f).widthIn(max = 420.dp)
            .graphicsLayerScale(open.value)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .testTag("folder-panel-content")
        val content: @Composable () -> Unit = {
            FolderPanelBody(fieldValue, { fieldValue = it }, folder, apps, drag, page, homeDestinations, dockVacancies,
                onLaunch, onMoveOut, commitAndClose, editTitle, rows, glass)
        }
        if (glassBackdrop != null) {
            Box(panelModifier.liquidGlass(glassBackdrop, Corner.xlarge, glassTint, blurRadius = 4f, settings = settings)) { content() }
        } else {
            Surface(panelModifier, color = glassTint, shape = Corner.xlarge,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .6f))) { content() }
        }
    }
}

private fun Modifier.graphicsLayerScale(scale: Float): Modifier = this.scale(scale)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FolderPanelBody(
    fieldValue: androidx.compose.ui.text.input.TextFieldValue,
    onFieldValue: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    folder: FolderEntry,
    apps: Map<String, AppEntry>,
    drag: HomeDragState,
    page: Int,
    homeDestinations: List<Int>,
    dockVacancies: List<Int>,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onMoveOut: (String, DropTarget) -> Unit,
    onDone: () -> Unit,
    editTitle: Boolean,
    rows: Int,
    glass: Boolean,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(editTitle, folder.id) { if (editTitle) runCatching { focus.requestFocus() } }
    Column(Modifier.padding(horizontal = 18.dp).padding(top = 16.dp, bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // The title is the text itself, centred and bare: no outline, label or button, like iOS. Tapping it edits it in place.
        BasicTextField(fieldValue, onFieldValue,
            Modifier.fillMaxWidth().focusRequester(focus).testTag("folder-name"),
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(color = GlassInkOnPanel, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(GlassInkOnPanel),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); onDone() }),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (fieldValue.text.isEmpty()) Text(stringResource(com.jake.duolauncher.R.string.folder_name),
                        style = MaterialTheme.typography.titleLarge, color = GlassInkOnPanel.copy(alpha = .45f))
                    inner()
                }
            })
        val pages = FolderPages.pageCount(folder.appIds.size)
        val pager = rememberPagerState { pages }
        HorizontalPager(pager, Modifier.fillMaxWidth().padding(top = 14.dp).testTag("folder-pages"), beyondViewportPageCount = 1) { index ->
            val ids = FolderPages.page(folder.appIds, index)
            Column(Modifier.fillMaxWidth().heightIn(min = (rows * 92).dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ids.chunked(FolderPages.COLUMNS).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { appId ->
                            Box(Modifier.weight(1f)) {
                                apps[appId]?.let { app -> FolderChild(app, folder.id, drag, page, homeDestinations, dockVacancies,
                                    onLaunch = onLaunch, onMoveOut = onMoveOut) }
                            }
                        }
                        repeat(FolderPages.COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (pages > 1) Row(Modifier.padding(top = 6.dp).testTag("folder-dots"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(pages) { i ->
                Box(Modifier.size(7.dp).clip(CircleShape).background(GlassInkOnPanel.copy(alpha = if (i == pager.currentPage) .85f else .28f)))
            }
        }
    }
}

@Composable
private fun FolderChild(
    app: AppEntry, folderId: String, drag: HomeDragState, page: Int,
    homeDestinations: List<Int>, dockVacancies: List<Int>,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit, onMoveOut: (String, DropTarget) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    // iOS shows a folder's apps as bare icons with labels on the panel, no cards. The move menu is a small dot at the icon's corner
    // rather than a card-wide kebab, so the icon stays the main target.
    Box(Modifier.fillMaxWidth().testTag("folder-child-${app.id}")) {
        Column(Modifier.fillMaxWidth().dropRegion(drag, DropTarget.Library(app.id), app.id, page,
            folderId = folderId, scope = folderId).clickable(enabled = app.available) { onLaunch(app, null) }
            .padding(horizontal = 2.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Image(app.image, null, Modifier.size(58.dp).clip(Corner.icon).alpha(if (app.available) 1f else .45f))
                AppBadge(badgeCount(app.packageName), Modifier.align(Alignment.TopEnd))
            }
            Text(app.label, Modifier.padding(top = 5.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium.copy(color = GlassInkOnPanel))
            if (app.isWork || !app.available) Text(if (app.available) app.profileLabel else "${app.profileLabel} unavailable",
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall.copy(color = GlassInkOnPanel.copy(alpha = .7f)))
        }
        IconButton(onClick = { menu = true }, Modifier.align(Alignment.TopStart).size(32.dp)
            .testTag("folder-options-${app.id}")) { Icon(Icons.Rounded.MoreVert, "Move ${app.label}", Modifier.size(16.dp), tint = GlassInkOnPanel.copy(alpha = .55f)) }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            homeDestinations.distinctBy(::homeCellPage).forEach { destination ->
                val destinationPage = homeCellPage(destination)
                val label = if (destinationPage == -1) "Move to Unfolded-only page" else "Move to page ${destinationPage + 1}"
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    menu = false; onMoveOut(app.id, DropTarget.Home(destination))
                }, modifier = Modifier.testTag("folder-move-${app.id}-page-$destinationPage"))
            }
            dockVacancies.firstOrNull()?.let { dock ->
                DropdownMenuItem(text = { Text(stringResource(com.jake.duolauncher.R.string.move_to_dock)) }, onClick = {
                    menu = false; onMoveOut(app.id, DropTarget.Dock(dock))
                }, modifier = Modifier.testTag("folder-move-${app.id}-dock"))
            }
            DropdownMenuItem(text = { Text(stringResource(com.jake.duolauncher.R.string.remove_shortcut)) }, onClick = {
                menu = false; onMoveOut(app.id, DropTarget.Remove)
            }, modifier = Modifier.testTag("folder-remove-${app.id}"))
        }
    }
}
