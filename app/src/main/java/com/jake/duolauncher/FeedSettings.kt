package com.jake.duolauncher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** News feed management inside Customize launcher: sources, preference, and refresh. */
@Composable
internal fun FeedSettings(
    feed: FeedState,
    onRefresh: () -> Unit,
    onAddFeed: (String, (FeedAddResult) -> Unit) -> Unit,
    onRemoveFeed: (String) -> Unit,
    onFeedPreferred: (Boolean) -> Unit,
) {
    SettingsSwitch("Use my feed instead of Discover", feed.feedPreferred, onFeedPreferred,
        "feed-preferred-switch")
    Text("When this is off, Uno Launcher keeps Google Discover where it works and uses your feed automatically on devices without Discover support.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (feed.sources.isEmpty()) "No feeds yet" else "Feeds (${feed.sources.size})",
            Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (feed.sources.isNotEmpty()) IconButton(onClick = onRefresh, enabled = !feed.refreshing,
            modifier = Modifier.testTag("feed-refresh-now")) {
            Icon(Icons.Rounded.Refresh, "Refresh feeds now")
        }
    }
    if (feed.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("feed-refreshing"))
    (feed.message ?: if (feed.lastRefreshAt > 0L)
        "Last updated ${describeFeedAge(System.currentTimeMillis(), feed.lastRefreshAt)}" else null)?.let { message ->
        Text(message, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    feed.sources.forEach { source ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(source.label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(source.url, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = { onRemoveFeed(source.id) },
                modifier = Modifier.testTag("feed-remove-${source.id}")
                    .semantics { contentDescription = "Remove ${source.label}" }) {
                Icon(Icons.Rounded.DeleteOutline, null)
            }
        }
    }
    var showAddDialog by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { showAddDialog = true },
        Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("feed-add")) {
        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Add a feed")
    }
    Text("Uno connects directly to the https addresses you add, and only to those servers. Entries are stored on this device. Nothing is uploaded.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (showAddDialog) AddFeedDialog(onDismiss = { showAddDialog = false },
        onAdd = { url, result -> onAddFeed(url) { addResult -> showAddDialog = addResult !is FeedAddResult.Added; result(addResult) } })
}

@Composable
private fun AddFeedDialog(onDismiss: () -> Unit, onAdd: (String, (FeedAddResult) -> Unit) -> Unit) {
    var url by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!pending) onDismiss() },
        title = { Text("Add a feed") },
        text = {
            Column {
                OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth().testTag("feed-url-field"),
                    label = { Text("Feed address") }, singleLine = true, enabled = !pending,
                    placeholder = { Text("https://example.com/feed.xml") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                Text("Suggested", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelMedium)
                SUGGESTED_FEEDS.forEach { suggestion ->
                    TextButton(onClick = { url = suggestion.url; status = null }, enabled = !pending,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(suggestion.label)
                            Text(suggestion.host, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (status != null) { Spacer(Modifier.height(8.dp)); Text(status!!, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !pending, onClick = {
                if (url.isBlank()) { status = "Enter a feed address first."; return@TextButton }
                pending = true
                status = "Checking the feed…"
                onAdd(url) { result ->
                    pending = false
                    status = when (result) {
                        is FeedAddResult.Added -> { onDismiss(); null }
                        is FeedAddResult.Rejected -> result.message
                    }
                }
            }, modifier = Modifier.testTag("feed-add-confirm")) { Text(if (pending) "Checking…" else "Test and add") }
        },
        dismissButton = { TextButton(enabled = !pending, onClick = onDismiss) { Text("Cancel") } })
}
