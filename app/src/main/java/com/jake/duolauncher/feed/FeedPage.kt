package com.jake.duolauncher.feed

import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
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
    onToggleSave: (String) -> Unit,
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
                FeedPageBody(feed, onRefresh, onOpenEntry, onAddFeed, onToggleSave, Modifier.fillMaxSize())
            }
        }
        return
    }
    Surface(modifier.fillMaxSize().testTag("feed-page"),
        shape = Corner.xlarge, color = Glass.copy(alpha = .92f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .5f))) {
        FeedPageBody(feed, onRefresh, onOpenEntry, onAddFeed, onToggleSave, Modifier.fillMaxSize())
    }
}

@Composable
private fun FeedPageBody(
    feed: FeedState,
    onRefresh: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onAddFeed: () -> Unit,
    onToggleSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedSource by remember { mutableStateOf<String?>(null) }
    var savedOnly by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // A chip for a feed that has since been removed or switched off must not leave the page filtered to nothing.
    val activeSource = selectedSource?.takeIf { id -> feed.sources.any { it.id == id && it.enabled } }
    Column(modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(com.jake.duolauncher.R.string.my_feed), style = MaterialTheme.typography.headlineMedium)
                    val updated = if (feed.lastRefreshAt > 0L)
                        "Updated ${describeFeedAge(System.currentTimeMillis(), feed.lastRefreshAt)}" else "Never refreshed"
                    Text(updated, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (feed.unreadCount > 0) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("feed-new-count")) {
                        Text("${feed.unreadCount} new", Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(onClick = onRefresh, enabled = !feed.refreshing && feed.configured,
                    modifier = Modifier.testTag("feed-refresh")) {
                    Icon(Icons.Rounded.Refresh, "Refresh feed")
                }
            }
            when {
                !feed.configured -> EmptyFeedState(onAddFeed, Modifier.weight(1f))
                !feed.anyEnabled -> Column(Modifier.weight(1f).testTag("feed-all-off"), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(com.jake.duolauncher.R.string.all_your_feeds_are_switched_off), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(com.jake.duolauncher.R.string.turn_one_on_under_launcher_settings), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                feed.refreshing && feed.shownEntries.isEmpty() -> {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(.6f))
                        Spacer(Modifier.height(14.dp))
                        Text(stringResource(com.jake.duolauncher.R.string.checking_feeds), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                feed.shownEntries.isEmpty() && feed.sourceErrors.isNotEmpty() -> {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(com.jake.duolauncher.R.string.feeds_couldn_t_be_read), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(feed.sourceErrors.values.joinToString("\n"), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = onRefresh, Modifier.testTag("feed-retry")) { Text(stringResource(com.jake.duolauncher.R.string.retry)) }
                    }
                }
                else -> {
                    if (feed.sourceErrors.isNotEmpty()) {
                        Text("${feed.sourceErrors.size} feed${if (feed.sourceErrors.size == 1) "" else "s"} couldn't be read.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    val filtered = filterFeedEntries(feed.shownEntries, activeSource, query, if (savedOnly) feed.savedLinks.toSet() else null)
                    FeedFilters(feed.sources.filter { it.enabled }, activeSource, { selectedSource = it }, query, { query = it },
                        savedOnly = savedOnly, savedCount = feed.savedShownCount, onSavedOnly = { savedOnly = it })
                    if (filtered.isEmpty()) {
                        Column(Modifier.weight(1f).fillMaxWidth().padding(top = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(if (savedOnly && query.isBlank()) com.jake.duolauncher.R.string.feed_nothing_saved
                                else com.jake.duolauncher.R.string.no_matching_entries),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f).padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(end = 8.dp, bottom = 8.dp)) {
                            // Keyed by feed and link: two feeds can carry the same story, and a repeated key crashes a LazyColumn.
                            itemsIndexed(filtered, key = { _, entry -> "${entry.sourceId}|${entry.link}" }) { index, entry ->
                                FeedEntryCard(entry, read = feed.isRead(entry.link), saved = feed.isSaved(entry.link),
                                    Modifier.testTag("feed-entry-$index"), onClick = { onOpenEntry(entry.link) },
                                    onToggleSave = { onToggleSave(entry.link) })
                            }
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
        Text(stringResource(com.jake.duolauncher.R.string.follow_grapheneos), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(com.jake.duolauncher.R.string.nothing_is_contacted_until_you_choose),
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
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(com.jake.duolauncher.R.string.add_your_own_feed))
        }
    }
}

/** The per-feed chips and the title/summary search box that narrow the stream. */
@Composable
private fun FeedFilters(
    sources: List<FeedSource>,
    selected: String?,
    onSelect: (String?) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    savedOnly: Boolean,
    savedCount: Int,
    onSavedOnly: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(query, onQuery, Modifier.fillMaxWidth().testTag("feed-search"),
            singleLine = true, placeholder = { Text(stringResource(com.jake.duolauncher.R.string.search_feed)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = selected == null, onClick = { onSelect(null) },
                label = { Text(stringResource(com.jake.duolauncher.R.string.feed_all)) },
                modifier = Modifier.testTag("feed-filter-all"))
            if (savedCount > 0 || savedOnly) FilterChip(selected = savedOnly, onClick = { onSavedOnly(!savedOnly) },
                label = { Text(stringResource(com.jake.duolauncher.R.string.feed_saved_chip, savedCount)) },
                leadingIcon = { Icon(Icons.Rounded.Bookmark, null, Modifier.size(16.dp)) },
                modifier = Modifier.testTag("feed-filter-saved"))
            sources.forEach { source ->
                FilterChip(selected = selected == source.id, onClick = { onSelect(source.id) },
                    label = { Text(source.label) },
                    modifier = Modifier.testTag("feed-filter-${source.id}"))
            }
        }
    }
}

@Composable
private fun FeedEntryCard(entry: FeedEntry, read: Boolean, saved: Boolean, modifier: Modifier = Modifier,
    onClick: () -> Unit, onToggleSave: () -> Unit) {
    val saveLabel = stringResource(if (saved) com.jake.duolauncher.R.string.feed_unsave else com.jake.duolauncher.R.string.feed_save)
    Surface(modifier = modifier.fillMaxWidth().alpha(if (read) .55f else 1f)
        .combinedClickable(onClick = onClick, onLongClickLabel = saveLabel, onLongClick = onToggleSave)
        // A long press is not reachable with a screen reader or a switch, so saving is also offered as an action.
        .semantics { customActions = listOf(CustomAccessibilityAction(saveLabel) { onToggleSave(); true }) },
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (saved) {
                    Icon(Icons.Rounded.Bookmark, "Saved", tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(listOfNotNull(entry.sourceLabel, describeFeedAge(System.currentTimeMillis(), entry.publishedAt)
                    .takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
