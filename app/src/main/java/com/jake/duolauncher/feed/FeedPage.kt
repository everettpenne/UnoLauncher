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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect

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
            .frostedGlass(Corner.xlarge, glassTint.copy(alpha = GlassKind.REGULAR.tintAlpha)) {
                liquidGlass(glassBackdrop, Corner.xlarge, glassTint, kind = GlassKind.REGULAR, settings = settings) }) {
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
    val wallpaperAccent = LocalWallpaperAccent.current
    val accent = wallpaperAccent?.accent ?: IosOrange
    val accentInk = wallpaperAccent?.ink ?: Color.Black
    // Pull-to-refresh rides the existing rubber band: past a threshold the page arms, and releasing at the top
    // refreshes. It never fires while a refresh is already running or when nothing is switched on.
    val refreshThresholdPx = with(LocalDensity.current) { 64.dp.toPx() }
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    val currentRefreshing by rememberUpdatedState(feed.refreshing)
    val currentAnyEnabled by rememberUpdatedState(feed.anyEnabled)
    var pulledPastThreshold by remember { mutableStateOf(false) }
    LaunchedEffect(elastic) {
        snapshotFlow { elastic.offset }.collect { offset ->
            if (offset >= refreshThresholdPx) pulledPastThreshold = true
            else if (pulledPastThreshold && offset <= 0f) {
                pulledPastThreshold = false
                if (!currentRefreshing && currentAnyEnabled) currentOnRefresh()
            }
        }
    }
    Column(modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(com.jake.duolauncher.R.string.my_feed), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(4.dp))
                    val muted = LocalContentColor.current.copy(alpha = .74f)
                    val now = System.currentTimeMillis()
                    val updated = if (feed.lastRefreshAt > 0L)
                        "Updated ${describeFeedAge(now, feed.lastRefreshAt)}" else "Never refreshed"
                    // A feed that hasn't been fetched in a while takes the accent so "Updated yesterday" reads as
                    // "there may be more now" rather than as freshness.
                    Text(updated, style = UnoType.caption1.copy(color = if (isFeedStale(now, feed.lastRefreshAt)) accent else muted))
                }
                if (feed.unreadCount > 0) {
                    Surface(shape = Corner.pill, color = accent,
                        modifier = Modifier.testTag("feed-new-count")) {
                        Text("${feed.unreadCount} new", Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = accentInk, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(onClick = onRefresh, enabled = !feed.refreshing && feed.anyEnabled,
                    modifier = Modifier.testTag("feed-refresh")) {
                    if (feed.refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = accent)
                    else Icon(Icons.Rounded.Refresh, "Refresh feed")
                }
            }
            when {
                !feed.configured -> EmptyFeedState(onAddFeed, Modifier.weight(1f))
                !feed.anyEnabled -> Column(Modifier.weight(1f).testTag("feed-all-off"), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(com.jake.duolauncher.R.string.all_your_feeds_are_switched_off), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(com.jake.duolauncher.R.string.turn_one_on_under_launcher_settings),
                        style = UnoType.footnote.copy(color = LocalContentColor.current.copy(alpha = .74f)))
                    Spacer(Modifier.height(16.dp))
                    // One tap opens the feed settings (the same place "Add your own feed" leads) so a switched-off feed
                    // can be turned back on from the page instead of hunting through Launcher settings.
                    GlassButton("Manage feeds", onAddFeed, Modifier.heightIn(min = 48.dp).testTag("feed-manage"))
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
                            style = UnoType.footnote.copy(color = LocalContentColor.current.copy(alpha = .74f)))
                        Spacer(Modifier.height(16.dp))
                        GlassButton(stringResource(com.jake.duolauncher.R.string.retry), onRefresh, Modifier.testTag("feed-retry"))
                    }
                }
                else -> {
                    if (feed.sourceErrors.isNotEmpty()) {
                        val errorColor = MaterialTheme.colorScheme.error
                        Surface(color = errorColor.copy(alpha = .12f), shape = Corner.small, modifier = Modifier.fillMaxWidth()) {
                            Text(describeSourceErrors(feed.sources.filter { it.id in feed.sourceErrors }.map { it.label }),
                                style = UnoType.caption1.copy(color = errorColor),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    // The shown list, the saved set and the filtered result are each derived once per state change rather than
                    // rebuilt on every recomposition, so typing in the search field does not re-filter the whole cache.
                    val shown = remember(feed.sources, feed.entries) {
                        val on = feed.sources.filter { it.enabled }.mapTo(mutableSetOf()) { it.id }
                        feed.entries.filter { it.sourceId in on }
                    }
                    val savedSet = remember(feed.savedLinks, savedOnly) { if (savedOnly) feed.savedLinks.toSet() else null }
                    val filtered = remember(shown, activeSource, query, savedSet) {
                        filterFeedEntries(shown, activeSource, query, savedSet)
                    }
                    FeedFilters(feed.sources.filter { it.enabled }, activeSource, { selectedSource = it }, query, { query = it },
                        savedOnly = savedOnly, savedCount = feed.savedShownCount, onSavedOnly = { savedOnly = it })
                    if (filtered.isEmpty()) {
                        Column(Modifier.weight(1f).fillMaxWidth().padding(top = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(if (savedOnly && query.isBlank()) com.jake.duolauncher.R.string.feed_nothing_saved
                                else com.jake.duolauncher.R.string.no_matching_entries),
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalContentColor.current.copy(alpha = .74f))
                        }
                    } else {
                        Box(Modifier.weight(1f)) {
                            LazyColumn(Modifier.fillMaxSize().padding(top = 12.dp)
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
                            // The pull-to-refresh arc appears in the gap the rubber band opens above the list and fills as the
                            // finger pulls toward the threshold.
                            val pull = elastic.offset
                            if (pull > 0f && !feed.refreshing) {
                                CircularProgressIndicator(progress = { (pull / refreshThresholdPx).coerceIn(0f, 1f) },
                                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp).size(20.dp),
                                    strokeWidth = 2.dp, color = accent)
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
        val muted = LocalContentColor.current.copy(alpha = .74f)
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
                    Text(suggestion.host, style = UnoType.caption1.copy(color = LocalContentColor.current.copy(alpha = .74f)), maxLines = 1)
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
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(entry.title, style = UnoType.headline.copy(color = ink.copy(alpha = if (read) .62f else 1f)),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (entry.summary.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                // Muted, not faint: unread body and meta hold ≥ 4.5:1 against the card, and read text dims enough to
                // stay obviously read without becoming illegible.
                Text(entry.summary, style = UnoType.subheadline.copy(color = ink.copy(alpha = if (read) .48f else .74f)),
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!read) {
                    // The one accent marks a story as unread; opened stories dim instead.
                    Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                    Spacer(Modifier.width(8.dp))
                }
                if (saved) {
                    Icon(Icons.Rounded.Bookmark, "Saved", tint = accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(describeEntryMeta(entry.sourceLabel, System.currentTimeMillis(), entry.publishedAt),
                    style = UnoType.caption1.copy(color = ink.copy(alpha = if (read) .48f else .74f)),
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
