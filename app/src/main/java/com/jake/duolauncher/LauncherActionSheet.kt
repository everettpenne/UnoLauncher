package com.jake.duolauncher

import androidx.activity.OnBackPressedCallback
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Handles Back on the ComponentDialog which owns a Material modal sheet. A regular Compose
 * BackHandler sees the activity owner inherited by the sheet composition, while platform Back is
 * dispatched to the dialog first.
 */
@Composable
internal fun ModalDialogBackHandler(onBack: () -> Unit) {
    val localView = androidx.compose.ui.platform.LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBack by rememberUpdatedState(onBack)
    val dispatcherOwner = remember(localView) {
        val dialogWindow = (localView.parent as? DialogWindowProvider)?.window
        dialogWindow?.decorView?.findViewTreeOnBackPressedDispatcherOwner()
    }
    DisposableEffect(dispatcherOwner, lifecycleOwner) {
        val callback = object : OnBackPressedCallback(dispatcherOwner != null) {
            override fun handleOnBackPressed() = currentOnBack()
        }
        dispatcherOwner?.onBackPressedDispatcher?.addCallback(lifecycleOwner, callback)
        onDispose { callback.remove() }
    }
}

@Composable
internal fun LauncherAppActionSheet(app: AppEntry, placed: Boolean, homePages: Int,
    moving: Boolean, onMoving: (Boolean) -> Unit,
    onAddOrRemove: () -> Unit, onMoveFirst: () -> Unit, onMoveEarlier: () -> Unit, onMoveLater: () -> Unit,
    onMovePage: (Int) -> Unit, onInfo: () -> Unit, onWidgets: (() -> Unit)?, onCreateFolder: () -> Unit,
    onClose: () -> Unit, onSplit: (() -> Unit)? = null,
    shortcuts: List<AppShortcut> = emptyList(), onShortcut: (AppShortcut) -> Unit = {}) {
    ModalDialogBackHandler { if (moving) onMoving(false) else onClose() }
    val maxHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * .88f).toDp() }
    Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            if (moving) IconButton(onClick = { onMoving(false) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Image(app.icon.asImageBitmap(), null, Modifier.size(48.dp).clip(Corner.icon))
            Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) {
                Text(if (moving) "Move ${app.label}" else app.label, style = MaterialTheme.typography.titleLarge)
                Text("${app.profileLabel} profile", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close app options") }
        }
        if (moving) {
            ActionRow(Icons.Rounded.ArrowUpward, "Move to first position", onMoveFirst)
            ActionRow(Icons.Rounded.KeyboardArrowUp, "Move earlier", onMoveEarlier)
            ActionRow(Icons.Rounded.KeyboardArrowDown, "Move later", onMoveLater)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            repeat(homePages) { page -> ActionRow(Icons.Rounded.GridView, "Move to page ${page + 1}",
                { onMovePage(page) }, Modifier.testTag("app-move-${app.id}-page-$page")) }
        } else {
            // The app's own quick actions come first, as on every other launcher.
            if (shortcuts.isNotEmpty()) {
                shortcuts.forEach { shortcut ->
                    Surface(onClick = { onShortcut(shortcut) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .testTag("app-shortcut-${shortcut.info.id}"), color = androidx.compose.ui.graphics.Color.Transparent, shape = Corner.medium) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            val bitmap = shortcut.icon
                            if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.size(34.dp).clip(Corner.icon))
                            else Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f), Corner.icon))
                            Spacer(Modifier.width(14.dp))
                            Text(shortcut.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
            }
            if (placed) ActionRow(Icons.Rounded.DragIndicator, "Move on Home", { onMoving(true) })
            else ActionRow(Icons.Rounded.Home, "Add to Home", onAddOrRemove)
            onWidgets?.let { ActionRow(Icons.Rounded.Widgets, "Widgets", it) }
            onSplit?.let { ActionRow(Icons.Rounded.VerticalSplit, "Open in split screen", it, Modifier.testTag("app-split-${app.id}")) }
            ActionRow(Icons.Rounded.CreateNewFolder, "Create folder", onCreateFolder)
            ActionRow(Icons.Rounded.Info, "App info", onInfo)
            if (placed) {
                Spacer(Modifier.height(10.dp)); HorizontalDivider(); Spacer(Modifier.height(4.dp))
                ActionRow(Icons.Rounded.RemoveCircleOutline, "Remove from Home", onAddOrRemove,
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
internal fun EmptySpaceActionSheet(onWidgets: () -> Unit, onWallpaper: () -> Unit,
    onCustomize: () -> Unit, onClose: () -> Unit) {
    val maxHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * .75f).toDp() }
    Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Add to Home", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close empty space options") }
        }
        Text("Choose what belongs in this space.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        ActionRow(Icons.Rounded.Widgets, "Widgets", onWidgets, Modifier.testTag("empty-space-widgets"))
        ActionRow(Icons.Rounded.Wallpaper, "Wallpaper", onWallpaper, Modifier.testTag("empty-space-wallpaper"))
        ActionRow(Icons.Rounded.Tune, "Customize launcher", onCustomize, Modifier.testTag("empty-space-customize"))
    }
}

@Composable
internal fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 52.dp), color = androidx.compose.ui.graphics.Color.Transparent,
        shape = Corner.medium) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(tint.copy(alpha = .12f), Corner.icon), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp), tint = tint)
            }
            Spacer(Modifier.width(14.dp)); Text(label, style = MaterialTheme.typography.bodyLarge, color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface)
        }
    }
}
