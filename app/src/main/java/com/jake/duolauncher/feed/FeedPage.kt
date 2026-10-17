package com.jake.duolauncher.feed

import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.jake.duolauncher.panel.IosOrange
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    // iOS-style rubber-banding at the list's ends. The state owns the resisted offset; the modifier below both
    // draws it and springs it back on release, snapping instead when Android's animations are switched off.
    val context = LocalContext.current
    val animationsOn = remember {
        android.provider.Settings.Global.getFloat(context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    val elastic = rememberElasticOverscrollState(animationsOn)
    val listState = rememberLazyListState()
    // A chip for a feed that has since been removed or switched off must not leave the page filtered to nothing.
    val activeSource = selectedSource?.takeIf { id -> feed.sources.any { it.id == id && it.enabled } }
    Column(modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(com.jake.duolauncher.R.string.my_feed), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(4.dp))
                    val muted = LocalContentColor.current.copy(alpha = .62f)
                    val updated = if (feed.lastRefreshAt > 0L)
                        "Updated ${describeFeedAge(System.currentTimeMillis(), feed.lastRefreshAt)}" else "Never refreshed"
                    Text(updated, style = UnoType.caption1.copy(color = muted))
                }
                if (feed.unreadCount > 0) {
                    val accent = LocalWallpaperAccent.current
                    Surface(shape = Corner.pill, color = accent?.accent ?: IosOrange,
                        modifier = Modifier.testTag("feed-new-count")) {
                        Text("${feed.unreadCount} new", Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            color = accent?.ink ?: Color.Black, style = MaterialTheme.typography.labelSmall)
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
                    Text(stringResource(com.jake.duolauncher.R.string.turn_one_on_under_launcher_settings),
                        style = UnoType.footnote.copy(color = LocalContentColor.current.copy(alpha = .62f)))
                }
                feed.refreshing && feed.shownEntries.isEmpty() -> {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(.6f))
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(com.jake.duolauncher.R.string.checking_feeds), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                feed.shownEntries.isEmpty() && feed.sourceErrors.isNotEmpty() -> {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(com.jake.duolauncher.R.string.feeds_couldn_t_be_read), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(feed.sourceErrors.values.joinToString("\n"),
                            style = UnoType.footnote.copy(color = LocalContentColor.current.copy(alpha = .62f)))
                        Spacer(Modifier.height(16.dp))
                        GlassButton(stringResource(com.jake.duolauncher.R.string.retry), onRefresh, Modifier.testTag("feed-retry"))
                    }
                }
                else -> {
                    if (feed.sourceErrors.isNotEmpty()) {
                        val errorColor = MaterialTheme.colorScheme.error
                        Surface(color = errorColor.copy(alpha = .12f), shape = Corner.small, modifier = Modifier.fillMaxWidth()) {
                            Text("${feed.sourceErrors.size} feed${if (feed.sourceErrors.size == 1) "" else "s"} couldn't be read.",
                                style = UnoType.caption1.copy(color = errorColor),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    val filtered = filterFeedEntries(feed.shownEntries, activeSource, query, if (savedOnly) feed.savedLinks.toSet() else null)
                    FeedFilters(feed.sources.filter { it.enabled }, activeSource, { selectedSource = it }, query, { query = it },
                        savedOnly = savedOnly, savedCount = feed.savedShownCount, onSavedOnly = { savedOnly = it })
                    if (filtered.isEmpty()) {
                        Column(Modifier.weight(1f).fillMaxWidth().padding(top = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(if (savedOnly && query.isBlank()) com.jake.duolauncher.R.string.feed_nothing_saved
                                else com.jake.duolauncher.R.string.no_matching_entries),
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalContentColor.current.copy(alpha = .62f))
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f).padding(top = 12.dp)
                            .elasticVerticalEdges(elastic, atStart = { !listState.canScrollBackward }, atEnd = { !listState.canScrollForward }),
                            state = listState,
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
        val accent = LocalWallpaperAccent.current?.accent ?: IosOrange
        val muted = LocalContentColor.current.copy(alpha = .62f)
        Icon(Icons.Rounded.RssFeed, null, Modifier.size(40.dp), tint = accent)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(com.jake.duolauncher.R.string.follow_grapheneos), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(com.jake.duolauncher.R.string.nothing_is_contacted_until_you_choose),
            style = UnoType.footnote, textAlign = TextAlign.Center, color = muted)
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
                    Text(if (pendingUrl == suggestion.url) "Checking…" else suggestion.label, maxLines = 1)
                    Text(suggestion.host, style = UnoType.caption1.copy(color = LocalContentColor.current.copy(alpha = .68f)), maxLines = 1)
                }
            }
            GlassButton(onClick, Modifier.fillMaxWidth(.9f).heightIn(min = 48.dp).testTag("feed-suggested-$index"),
                enabled = pendingUrl == null, prominent = index == 0, content = content)
            Spacer(Modifier.height(8.dp))
        }
        failure?.let {
            Text(it, style = UnoType.footnote, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
        }
        GlassButton(onClick = onAddFeed, Modifier.heightIn(min = 48.dp).testTag("feed-add")) {
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
        GlassField(query, onQuery, Modifier.fillMaxWidth().testTag("feed-search"),
            singleLine = true, leadingIcon = { Icon(Icons.Rounded.Search, null) },
            placeholder = { Text(stringResource(com.jake.duolauncher.R.string.search_feed)) })
        Row(Modifier.ownsHorizontalDrag().horizontalScroll(rememberScrollState()).testTag("feed-filters"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip(selected = selected == null, onClick = { onSelect(null) },
                label = { Text(stringResource(com.jake.duolauncher.R.string.feed_all)) },
                modifier = Modifier.testTag("feed-filter-all"))
            if (savedCount > 0 || savedOnly) GlassChip(selected = savedOnly, onClick = { onSavedOnly(!savedOnly) },
                label = { Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Bookmark, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                    Text(stringResource(com.jake.duolauncher.R.string.feed_saved_chip, savedCount)) } },
                modifier = Modifier.testTag("feed-filter-saved"))
            sources.forEach { source ->
                GlassChip(selected = selected == source.id, onClick = { onSelect(source.id) },
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
    val ink = LocalContentColor.current
    val accent = LocalWallpaperAccent.current?.accent ?: IosOrange
    // A glass card: an ink-tinted slab with a hairline rim, so the text sits on the launcher's own
    // ink rather than Material's grey surface and reads the same over any wallpaper.
    Surface(modifier = modifier.fillMaxWidth()
        .combinedClickable(onClick = onClick, onLongClickLabel = saveLabel, onLongClick = onToggleSave)
        // A long press is not reachable with a screen reader or a switch, so saving is also offered as an action.
        .semantics { customActions = listOf(CustomAccessibilityAction(saveLabel) { onToggleSave(); true }) },
        color = ink.copy(alpha = .08f),
        shape = Corner.medium,
        border = BorderStroke(1.dp, ink.copy(alpha = .14f))) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(entry.title, style = UnoType.headline.copy(color = ink.copy(alpha = if (read) .5f else 1f)),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (entry.summary.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(entry.summary, style = UnoType.subheadline.copy(color = ink.copy(alpha = if (read) .35f else .62f)),
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!read) {
                    // The one accent marks a story as unread; opened stories dim instead.
                    Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                    Spacer(Modifier.width(6.dp))
                }
                if (saved) {
                    Icon(Icons.Rounded.Bookmark, "Saved", tint = accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(describeEntryMeta(entry.sourceLabel, System.currentTimeMillis(), entry.publishedAt),
                    style = UnoType.caption1.copy(color = ink.copy(alpha = .58f)),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Where a sideways-scrolling strip sits, in root coordinates, so the Home pager leaves a touch that starts on it alone. The pager
 * watches every touch before its children and takes any mostly-horizontal drag as a page turn, which left no way to scroll the feed's
 * row of feed titles: the page turned instead. (The stack rail uses [StackRailRegistry] the same way.)
 */
internal object HorizontalScrollerRegistry { val bounds = mutableMapOf<Any, androidx.compose.ui.geometry.Rect>() }

internal fun Modifier.ownsHorizontalDrag(): Modifier = composed {
    val key = remember { Any() }
    DisposableEffect(Unit) { onDispose { HorizontalScrollerRegistry.bounds.remove(key) } }
    onGloballyPositioned { HorizontalScrollerRegistry.bounds[key] = it.boundsInRoot() }
}
