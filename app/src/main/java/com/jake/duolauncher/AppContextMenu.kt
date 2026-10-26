package com.jake.duolauncher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jake.duolauncher.appearance.*
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import kotlin.math.roundToInt

/** What the menu can do for the held app. A null action is one that app does not have (a system app cannot be deleted; a folder app cannot be hidden). */
internal class AppMenuActions(
    val placed: Boolean,
    val shortcuts: List<AppShortcut>,
    val onShortcut: (AppShortcut) -> Unit,
    val onEditHome: () -> Unit,
    val onAddToHome: () -> Unit,
    val onInfo: () -> Unit,
    val onHide: (() -> Unit)?,
    val onMore: () -> Unit,
    val onRemoveFromHome: () -> Unit,
    val onDelete: (() -> Unit)?,
)

/**
 * The menu iOS shows when an app is held: the rest of Home dims, the app lifts a little, and a small frosted liquid-glass panel opens under it
 * (above it near the bottom of the screen), headed by the app's own shortcuts and followed by Edit Home Screen and the rest. "Remove App" asks
 * what to do (delete the app, or only take it off Home) in the same panel. It replaced a full-width sheet of a dozen rows for the same press;
 * the sheet is still behind "More".
 *
 * [anchor] is the held thing's bounds in root pixels (a Home cell, or a row of the App Library, where the icon sits at the start).
 */
@Composable
internal fun AppContextMenu(
    app: AppEntry, anchor: Rect, homeIconDp: Float, glass: PageGlass?, actions: AppMenuActions, onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var confirming by remember { mutableStateOf(false) }
    val appear = remember { Animatable(0f, .001f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(.74f, 560f, .001f)) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }.testTag("app-context-menu")) {
        // Everything else steps back; a tap anywhere on it closes the menu.
        // As on iOS 26 it is blurred as well as dimmed: a frosted veil over the wallpaper and the pages, not a grey sheet.
        Box(Modifier.fillMaxSize()
            .then(if (glass != null) Modifier.drawBackdrop(
                backdrop = glass.backdrop, shape = { androidx.compose.ui.graphics.RectangleShape },
                effects = { blur(18.dp.toPx() * appear.value) }, highlight = { null }, shadow = { null },
                onDrawSurface = { drawRect(Color.Black.copy(alpha = .26f * appear.value)) })
                else Modifier.background(Color.Black.copy(alpha = .34f * appear.value)))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }.testTag("app-context-scrim"))
        // The icon sits at the middle top of a Home cell (its size on Home) and at the start of a library row (40 dp).
        val isRow = anchor.width > anchor.height * 2.2f
        val iconPx = with(density) { (if (isRow) 40f else homeIconDp).dp.toPx() }
        val iconLeft = if (isRow) anchor.left else anchor.center.x - iconPx / 2f
        val iconTop = if (isRow) anchor.center.y - iconPx / 2f else anchor.top + with(density) { 2.dp.toPx() }
        val lift = 1f + .09f * appear.value
        Image(app.image, null, Modifier.offset { IntOffset((iconLeft - origin.x).roundToInt(), (iconTop - origin.y).roundToInt()) }
            .size(with(density) { iconPx.toDp() }).graphicsLayer { scaleX = lift; scaleY = lift }
            .shadow(14.dp, Corner.icon, clip = false, ambientColor = Color.Black.copy(alpha = .3f), spotColor = Color.Black.copy(alpha = .4f))
            .clip(Corner.icon).testTag("app-context-icon"))
        // The panel: its width is fixed so it can be placed before it is measured; its height is found when it is laid out.
        val panelWidthPx = with(density) { 258.dp.toPx() }
        val screen = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
        val gap = with(density) { 10.dp.toPx() }
        val edge = with(density) { 10.dp.toPx() }
        var panelHeight by remember { mutableFloatStateOf(0f) }
        val iconBottom = iconTop + iconPx * lift
        val below = iconBottom + gap
        val flip = panelHeight > 0f && below + panelHeight > screen.height - edge && iconTop - gap - panelHeight >= edge
        val x = (if (isRow) iconLeft else iconLeft + iconPx / 2f - panelWidthPx / 2f).coerceIn(edge, (screen.width - panelWidthPx - edge).coerceAtLeast(edge))
        val y = if (flip) iconTop - gap - panelHeight else below.coerceAtMost((screen.height - panelHeight - edge).coerceAtLeast(edge))
        val pivotX = ((iconLeft + iconPx / 2f - x) / panelWidthPx).coerceIn(0f, 1f)
        val tint = MaterialTheme.colorScheme.surface.copy(alpha = .5f)
        val panel = Modifier.width(with(density) { panelWidthPx.toDp() })
            .offset { IntOffset((x - origin.x).roundToInt(), (y - origin.y).roundToInt()) }
            .onGloballyPositioned { panelHeight = it.size.height.toFloat() }
            .graphicsLayer {
                val grow = .8f + .2f * appear.value
                scaleX = grow; scaleY = grow; alpha = appear.value.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(pivotX, if (flip) 1f else 0f)
            }
            .then(if (glass != null) Modifier.liquidGlass(glass.backdrop, Corner.large, tint, GlassKind.REGULAR, glass.settings)
                else Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = .97f), Corner.large))
            .padding(vertical = 6.dp)
        Column(panel.testTag("app-context-panel")) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                if (!confirming) MainItems(app, actions, onDismiss) { confirming = true }
                else ConfirmItems(app, actions, onDismiss) { confirming = false }
            }
        }
    }
}

@Composable
private fun MainItems(app: AppEntry, actions: AppMenuActions, dismiss: () -> Unit, onRemove: () -> Unit) {
    val red = Color(0xFFFF453A)
    if (actions.shortcuts.isNotEmpty()) {
        actions.shortcuts.forEach { shortcut ->
            GlassMenuItem(text = {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(shortcut.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val bitmap = shortcut.icon
                    if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.padding(start = 10.dp).size(26.dp).clip(Corner.icon))
                }
            }, onClick = { actions.onShortcut(shortcut) }, modifier = Modifier.testTag("app-shortcut-${shortcut.info.id}"))
        }
        // A heavier break between the app's own actions and the launcher's, as iOS draws it.
        Spacer(Modifier.height(5.dp).fillMaxWidth().background(LocalContentColor.current.copy(alpha = .10f)))
    }
    if (actions.placed) MenuRow(Icons.Rounded.Smartphone, stringResource(com.jake.duolauncher.R.string.edit_home_screen), "app-edit-home", actions.onEditHome)
    else MenuRow(Icons.Rounded.Add, stringResource(com.jake.duolauncher.R.string.add_to_home_screen), "app-add-home", actions.onAddToHome)
    MenuDivider()
    MenuRow(Icons.Rounded.Info, stringResource(com.jake.duolauncher.R.string.app_info), "app-info-${app.id}", actions.onInfo)
    actions.onHide?.let { MenuDivider(); MenuRow(Icons.Rounded.VisibilityOff, stringResource(com.jake.duolauncher.R.string.hide_app), "app-hide-${app.id}", it) }
    MenuDivider()
    MenuRow(Icons.Rounded.MoreHoriz, stringResource(com.jake.duolauncher.R.string.more_options), "app-more-${app.id}", actions.onMore)
    MenuDivider()
    // With nothing to take off Home (the App Library), deleting is the only removal; an app that cannot be deleted has no red row at all.
    if (actions.placed) MenuRow(Icons.Rounded.RemoveCircleOutline, stringResource(com.jake.duolauncher.R.string.remove_app), "app-remove-${app.id}", onRemove, red)
    else actions.onDelete?.let { MenuRow(Icons.Rounded.RemoveCircleOutline, stringResource(com.jake.duolauncher.R.string.delete_app), "app-uninstall-${app.id}", it, red) }
}

@Composable
private fun ConfirmItems(app: AppEntry, actions: AppMenuActions, dismiss: () -> Unit, back: () -> Unit) {
    val red = Color(0xFFFF453A)
    Text(stringResource(com.jake.duolauncher.R.string.remove_app_title, app.label), Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current.copy(alpha = .65f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    MenuDivider()
    actions.onDelete?.let { MenuRow(Icons.Rounded.Delete, stringResource(com.jake.duolauncher.R.string.delete_app), "app-uninstall-${app.id}", it, red); MenuDivider() }
    MenuRow(Icons.Rounded.GridView, stringResource(com.jake.duolauncher.R.string.remove_from_home_screen), "app-remove-home-${app.id}", actions.onRemoveFromHome)
    MenuDivider()
    MenuRow(Icons.Rounded.Close, stringResource(com.jake.duolauncher.R.string.cancel), "app-remove-cancel", back)
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tag: String, onClick: () -> Unit, tint: Color? = null) {
    GlassMenuItem(text = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = tint ?: LocalContentColor.current, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(icon, null, tint = tint ?: LocalContentColor.current.copy(alpha = .85f), modifier = Modifier.padding(start = 10.dp).size(22.dp))
        }
    }, onClick = onClick, modifier = Modifier.testTag(tag))
}

@Composable
private fun MenuDivider() = HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = LocalContentColor.current.copy(alpha = .12f), thickness = .5.dp)
