package com.jake.duolauncher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Follows a suggested feed after the user taps it. Provided by the activity; the default does
 * nothing, so previews and tests never reach the network.
 */
internal val LocalFeedFollow = staticCompositionLocalOf<(String, (FeedAddResult) -> Unit) -> Unit> { { _, _ -> } }

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
    glassBackdrop: com.kyant.backdrop.Backdrop? = null,
    glassTint: Color = Glass.copy(alpha = .82f),
    settings: GlassSettings = GlassSettings.Default,
) {
    if (glassBackdrop != null) {
        Box(modifier.fillMaxSize().testTag("feed-page")
            .liquidGlass(glassBackdrop, Corner.xlarge, glassTint, blurRadius = 2f, settings = settings)) {
            // Glass over the wallpaper is dark in both themes, so text on it uses the light ink the
            // other glass panels use; Material's default content color is dark in the light theme.
            CompositionLocalProvider(LocalContentColor provides Ink) {
                FeedPageBody(feed, onRefresh, onOpenEntry, onAddFeed, Modifier.fillMaxSize())
            }
        }
        return
    }
    Surface(modifier.fillMaxSize().testTag("feed-page"),
        shape = Corner.xlarge, color = Glass.copy(alpha = .92f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .5f))) {
        FeedPageBody(feed, onRefresh, onOpenEntry, onAddFeed, Modifier.fillMaxSize())
    }
}

@Composable
private fun FeedPageBody(
    feed: FeedState,
    onRefresh: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onAddFeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(start = 22.dp, top = 18.dp, end = 14.dp, bottom = 10.dp)) {
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

@Composable
private fun EmptyFeedState(onAddFeed: () -> Unit, modifier: Modifier = Modifier) {
    val follow = LocalFeedFollow.current
    var pendingUrl by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.RssFeed, null, Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Follow GrapheneOS", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text("Nothing is contacted until you choose a feed, and each choice connects only to the server shown. Nothing is uploaded.",
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        SUGGESTED_FEEDS.forEachIndexed { index, suggestion ->
            val onClick = {
                failure = null; pendingUrl = suggestion.url
                follow(suggestion.url) { result ->
                    pendingUrl = null
                    if (result is FeedAddResult.Rejected) failure = "${suggestion.label}: ${result.message}"
                }
            }
            val content: @Composable RowScope.() -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (pendingUrl == suggestion.url) "Checking…" else suggestion.label)
                    Text(suggestion.host, style = MaterialTheme.typography.labelSmall)
                }
            }
            val modifier = Modifier.fillMaxWidth(.9f).heightIn(min = 56.dp).testTag("feed-suggested-$index")
            if (index == 0) Button(onClick, modifier, enabled = pendingUrl == null, content = content)
            else FilledTonalButton(onClick, modifier, enabled = pendingUrl == null, content = content)
            Spacer(Modifier.height(10.dp))
        }
        failure?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
        }
        TextButton(onClick = onAddFeed, Modifier.heightIn(min = 48.dp).testTag("feed-add")) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Add your own feed")
        }
    }
}

@Composable
private fun FeedEntryCard(entry: FeedEntry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .52f),
        shape = Corner.medium) {
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
