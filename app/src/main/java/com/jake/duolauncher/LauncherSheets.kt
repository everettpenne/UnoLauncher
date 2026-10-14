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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun AppPicker(apps: List<AppEntry>, dockSlot: Int?, onSelect: (AppEntry) -> Unit, onClear: () -> Unit,
    onLongClick: (AppEntry) -> Unit, canSelect: (AppEntry) -> Boolean = { true }, blockedHint: String? = null,
    title: String? = null) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) { apps.filter { it.label.contains(query.trim(), ignoreCase = true) } }
    Column(Modifier.fillMaxWidth().fillMaxHeight(.88f).padding(horizontal = 20.dp).imePadding()) {
        Text(title ?: if (dockSlot == null) "Your apps" else "Dock position ${dockSlot + 1}", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 16.dp).testTag("search-field"),
            placeholder = { Text(stringResource(com.jake.duolauncher.R.string.search_apps)) }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true,
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search") } }, shape = Corner.medium)
        if (dockSlot != null) TextButton(onClick = onClear) { Text(stringResource(com.jake.duolauncher.R.string.leave_this_position_empty)) }
        if (blockedHint != null) Text(blockedHint, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp).testTag("dock-full-guidance"))
        LazyColumn(Modifier.weight(1f)) {
            if (filtered.isEmpty()) item { Text(stringResource(com.jake.duolauncher.R.string.no_apps_found), Modifier.padding(vertical = 24.dp)) }
            items(filtered, key = { it.id }) { app ->
                val enabled = canSelect(app)
                Row(Modifier.fillMaxWidth().testTag("picker-app-${app.id}")
                    .combinedClickable(enabled = enabled, onClick = { onSelect(app) }, onLongClick = { onLongClick(app) })
                    .alpha(if (enabled) 1f else .45f)
                    .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(app.image, null, Modifier.size(44.dp).clip(Corner.icon))
                    Text(app.label, Modifier.padding(start = 16.dp).weight(1f), maxLines = 2)
                    if (dockSlot != null && enabled) Icon(Icons.Rounded.Add, "Choose ${app.label}")
                }
            }
        }
    }
}

@Composable
internal fun SettingSlider(label: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = 14.dp)) {
        Row { Text(label, Modifier.weight(1f)); Text(valueLabel, color = MaterialTheme.colorScheme.secondary) }
        Slider(value, onChange, valueRange = range, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
internal fun WidgetActions(
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
            Text(stringResource(com.jake.duolauncher.R.string.widget_options), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
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
        Text(stringResource(com.jake.duolauncher.R.string.resize), style = MaterialTheme.typography.titleMedium)
        Button(enabled = feasible, onClick = { onStartResize(width, height) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(com.jake.duolauncher.R.string.resize_on_home)) }
        if (!feasible) Text(stringResource(com.jake.duolauncher.R.string.move_this_widget_into_the_six), color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            repeat(homePages) { page -> TextButton(onClick = { onMoveToPage(page) },
                modifier = Modifier.testTag("widget-move-${placement.slot}-page-$page")) { Text("Move to page ${page + 1}") } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(com.jake.duolauncher.R.string.width), Modifier.weight(1f))
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
            Text(stringResource(com.jake.duolauncher.R.string.height), Modifier.weight(1f))
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
        Text(stringResource(com.jake.duolauncher.R.string.sizes_that_overlap_another_item_are), style = MaterialTheme.typography.bodySmall)
        if (!valid) Text(stringResource(com.jake.duolauncher.R.string.that_size_overlaps_another_item_or), color = MaterialTheme.colorScheme.error)
        Button(enabled = valid, onClick = { onResize(width, height); onClose() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(com.jake.duolauncher.R.string.apply_size)) }
        ActionRow(Icons.Rounded.FindReplace, "Replace", onReplace)
        HorizontalDivider()
        ActionRow(Icons.Rounded.DeleteOutline, "Remove", onRemove, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
    }
}

/** Whether [app] ships with the phone and has not been updated, so Android would refuse to uninstall it. */
internal fun isSystemApp(context: android.content.Context, app: AppEntry): Boolean = runCatching {
    val flags = context.packageManager.getApplicationInfo(app.packageName, 0).flags
    (flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 && (flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
}.getOrDefault(!app.isWork)
