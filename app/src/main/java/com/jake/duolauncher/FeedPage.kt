package com.jake.duolauncher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The local news feed surface shown in the Discover slot. Fetches nothing on its own;
 * it renders the on-device cache and asks the owner to refresh or to add a source.
 */
@Composable
internal fun FeedPage(
    feed: FeedState,
    onRefresh: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onAddFeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize().testTag("feed-page"),
        shape = RoundedCornerShape(30.dp), color = Glass.copy(alpha = .92f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .5f))) {
        Column(Modifier.fillMaxSize().padding(start = 22.dp, top = 18.dp, end = 14.dp, bottom = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("My feed", style = MaterialTheme.typography.headlineMedium)
                    val updated = if (feed.lastRefreshAt > 0L)
                        "Updated ${describeFeedAge(System.currentTimeMillis(), feed.lastRefreshAt)}" else "Never refreshed"
                    Text(updated, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRefresh, enabled = !feed.refreshing && feed.configured,
                    modifier = Modifier.testTag("feed-refresh")) {
                    Icon(Icons.Rounded.Refresh, "Refresh feed")
                }
            }
            when {
                !feed.configured -> EmptyFeedState(onAddFeed, Modifier.weight(1f))
                feed.refreshing && feed.entries.isEmpty() -> {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(.6f))
                        Spacer(Modifier.height(14.dp))
                        Text("Checking feeds…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                feed.entries.isEmpty() && feed.message != null -> {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Feeds couldn't be read.", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(feed.message ?: "", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = onRefresh, Modifier.testTag("feed-retry")) { Text("Retry") }
                    }
                }
                else -> {
                    if (feed.message != null) {
                        Text(feed.message ?: "", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    LazyColumn(Modifier.weight(1f).padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(end = 8.dp, bottom = 8.dp)) {
                        itemsIndexed(feed.entries, key = { _, entry -> entry.link }) { index, entry ->
                            FeedEntryCard(entry, Modifier.testTag("feed-entry-$index")) { onOpenEntry(entry.link) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFeedState(onAddFeed: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.RssFeed, null, Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Add a feed to read your own headlines here.",
            style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text("Duo fetches only the addresses you add. Nothing is uploaded.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onAddFeed, Modifier.testTag("feed-add")) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Add a feed")
        }
    }
}

@Composable
private fun FeedEntryCard(entry: FeedEntry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .52f),
        shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (entry.summary.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(entry.summary, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Text(listOfNotNull(entry.sourceLabel, describeFeedAge(System.currentTimeMillis(), entry.publishedAt)
                .takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}
