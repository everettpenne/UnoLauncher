package com.jake.duolauncher.feed

import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.panel.*
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** News feed management inside Launcher settings: sources, preference, and refresh. */
@Composable
internal fun FeedSettings(
    feed: FeedState,
    onRefresh: () -> Unit,
    onAddFeed: (String, (FeedAddResult) -> Unit) -> Unit,
    onRemoveFeed: (String) -> Unit,
    onSourceEnabled: (String, Boolean) -> Unit,
    onImportOpml: (String) -> FeedOpmlResult,
    onKeepPerFeed: (Int) -> Unit = {},
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (feed.sources.isEmpty()) "No feeds yet" else "Feeds (${feed.sources.size})",
            Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (feed.sources.isNotEmpty()) IconButton(onClick = onRefresh, enabled = !feed.refreshing,
            modifier = Modifier.testTag("feed-refresh-now")) {
            Icon(Icons.Rounded.Refresh, "Refresh feeds now")
        }
    }
    val context = LocalContext.current
    var importStatus by remember { mutableStateOf<String?>(null) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val xml = readFeedFile(context, uri)
            importStatus = if (xml == null) "Couldn't read that file." else {
                val result = onImportOpml(xml)
                when {
                    result.added > 0 -> "Added ${result.added} feed${if (result.added == 1) "" else "s"}" +
                        (if (result.skipped > 0) " · ${result.skipped} skipped" else "")
                    else -> "No new feeds to add."
                }
            }
        }
    }
    var exportStatus by remember { mutableStateOf<String?>(null) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { uri ->
        if (uri != null) exportStatus = if (writeFeedFile(context, uri, serializeOpml(feed.sources)))
            "Exported ${feed.sources.size} feed${if (feed.sources.size == 1) "" else "s"}."
        else "Couldn't write the file."
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassButton("Import OPML", { importPicker.launch(arrayOf("*/*")) }, Modifier.weight(1f).testTag("feed-import"))
        GlassButton("Export OPML", { exportPicker.launch("feeds.opml") }, Modifier.weight(1f).testTag("feed-export"))
    }
    importStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    exportStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Text(stringResource(com.jake.duolauncher.R.string.feed_keep_title), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FEED_KEEP_CHOICES.forEach { keep ->
            FilterChip(selected = feed.keepPerFeed == keep, onClick = { onKeepPerFeed(keep) }, label = { Text("$keep") },
                modifier = Modifier.testTag("feed-keep-$keep"))
        }
    }
    Text(stringResource(com.jake.duolauncher.R.string.feed_keep_note), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (feed.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("feed-refreshing"))
    if (feed.lastRefreshAt > 0L) {
        Text("Last updated ${describeFeedAge(System.currentTimeMillis(), feed.lastRefreshAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    feed.sources.forEach { source ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(source.label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(source.url, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                feed.sourceErrors[source.id]?.let { error ->
                    Text(error, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("feed-error-${source.id}"))
                }
            }
            LiquidSwitchControl(source.enabled, { onSourceEnabled(source.id, it) }, LocalPageGlass.current,
                Modifier.testTag("feed-enabled-${source.id}").semantics { contentDescription = "Show ${source.label} in the feed" })
            IconButton(onClick = { onRemoveFeed(source.id) },
                modifier = Modifier.testTag("feed-remove-${source.id}")
                    .semantics { contentDescription = "Remove ${source.label}" }) {
                Icon(Icons.Rounded.DeleteOutline, null)
            }
        }
    }
    var showAddDialog by remember { mutableStateOf(false) }
    GlassButton(onClick = { showAddDialog = true },
        Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("feed-add")) {
        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(com.jake.duolauncher.R.string.add_a_feed))
    }
    Text(stringResource(com.jake.duolauncher.R.string.uno_connects_directly_to_the_https),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (showAddDialog) AddFeedDialog(onDismiss = { showAddDialog = false },
        onAdd = { url, result -> onAddFeed(url) { addResult -> showAddDialog = addResult !is FeedAddResult.Added; result(addResult) } })
}

@Composable
private fun AddFeedDialog(onDismiss: () -> Unit, onAdd: (String, (FeedAddResult) -> Unit) -> Unit) {
    var url by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    GlassAlertDialog(onDismissRequest = { if (!pending) onDismiss() },
        title = { Text(stringResource(com.jake.duolauncher.R.string.add_a_feed)) },
        text = {
            Column {
                GlassField(url, { url = it }, Modifier.fillMaxWidth().testTag("feed-url-field"),
                    label = { Text(stringResource(com.jake.duolauncher.R.string.feed_address)) }, singleLine = true, enabled = !pending,
                    placeholder = { Text(stringResource(com.jake.duolauncher.R.string.https_example_com_feed_xml)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                Text(stringResource(com.jake.duolauncher.R.string.suggested), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelMedium)
                SUGGESTED_FEEDS.forEach { suggestion ->
                    GlassButton(onClick = { url = suggestion.url; status = null }, enabled = !pending,
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
            GlassButton(enabled = !pending, onClick = {
                if (url.isBlank()) { status = "Enter a feed address first."; return@GlassButton }
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
        dismissButton = { GlassButton(enabled = !pending, onClick = onDismiss) { Text(stringResource(com.jake.duolauncher.R.string.cancel)) } })
}

/** Reads a user-picked document as UTF-8 text, or null when it can't be read. */
private fun readFeedFile(context: Context, uri: Uri): String? = runCatching {
    // Capped, so a huge file picked by mistake is refused instead of read whole into memory (a long OPML list is a few dozen KB).
    context.contentResolver.openInputStream(uri)?.use { BoundedRead.readCapped(it, MAX_OPML_BYTES)?.toString(Charsets.UTF_8) }
}.getOrNull()

/** Writes [text] to a user-chosen document, returning whether it succeeded. */
private fun writeFeedFile(context: Context, uri: Uri, text: String): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    true
}.getOrDefault(false)
