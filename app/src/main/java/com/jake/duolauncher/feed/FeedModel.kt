package com.jake.duolauncher.feed

import com.jake.duolauncher.*
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** A user-added feed address, kept entirely on the device. */
data class FeedSource(val id: String, val url: String, val label: String, val enabled: Boolean = true)

/** A cached feed entry shown on the feed page. */
data class FeedEntry(
    val sourceId: String,
    val sourceLabel: String,
    val title: String,
    val link: String,
    val summary: String = "",
    val publishedAt: Long = 0L,
)

/** Observable feed state: sources, cached entries, and the current fetch status. */
data class FeedState(
    val sources: List<FeedSource> = emptyList(),
    val entries: List<FeedEntry> = emptyList(),
    val refreshing: Boolean = false,
    /** Each source's last fetch error, keyed by source id; empty when every feed came back. Not persisted. */
    val sourceErrors: Map<String, String> = emptyMap(),
    val lastRefreshAt: Long = 0L,
    val feedPreferred: Boolean = false,
    /** Links the user has opened, most recent first; used to dim read items and count the new ones. */
    val readLinks: List<String> = emptyList(),
    /** Links the user has saved for later, most recent first; kept past the normal cache cap. */
    val savedLinks: List<String> = emptyList(),
    /** How many entries to keep per feed (one of [FEED_KEEP_CHOICES]); the whole cache holds three times that. */
    val keepPerFeed: Int = DEFAULT_FEED_KEEP,
) {
    val configured: Boolean get() = sources.isNotEmpty()
    /** Entries from the feeds that are switched on; switched-off feeds stay saved but never reach the page. */
    val shownEntries: List<FeedEntry> get() {
        val on = sources.filter { it.enabled }.mapTo(mutableSetOf()) { it.id }
        return entries.filter { it.sourceId in on }
    }
    val anyEnabled: Boolean get() = sources.any { it.enabled }

    /** The cap on the whole cache, in step with [keepPerFeed]. */
    val keepTotal: Int get() = keepPerFeed * 3

    /** Whether [link] has been opened before. */
    fun isRead(link: String): Boolean = link in readLinks

    /** How many of the shown entries are still unopened, for a "N new" badge or island line. */
    val unreadCount: Int get() = shownEntries.count { !isRead(it.link) }

    /** Whether [link] has been saved for later. */
    fun isSaved(link: String): Boolean = link in savedLinks

    /** How many of the shown entries are saved (the "Saved" chip's number). */
    val savedShownCount: Int get() = shownEntries.count { isSaved(it.link) }

    /** How many entries are saved. */
    val savedCount: Int get() = savedLinks.size
}

sealed interface FeedAddResult {
    data object Added : FeedAddResult
    data class Rejected(val message: String) : FeedAddResult
}

/** A feed offered one tap away on the empty feed page. Nothing here is contacted at install,
 * on first run, or in the background: the host is reached only after the user follows it.
 */
internal data class SuggestedFeed(val url: String, val label: String, val detail: String) {
    val host: String get() = java.net.URI(url).host
}

/** The GrapheneOS project's own feeds. The community forum (Flarum) publishes no RSS or Atom
 * feed, so these are the official sources closest to it: release notes on grapheneos.org and
 * the project's announcement account.
 */
internal val SUGGESTED_FEEDS = listOf(
    SuggestedFeed("https://grapheneos.social/@GrapheneOS.rss", "GrapheneOS announcements", "News from the project's official account"),
    SuggestedFeed("https://grapheneos.org/releases.atom", "GrapheneOS releases", "Every release and what changed"),
)

/** Normalizes a user-typed feed address. Returns null when it can't be a web address.
 * Only https is accepted: a plain-http feed would expose what the user reads, and the app
 * declares no cleartext traffic.
 */
internal fun normalizeFeedUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    // Preserve any explicit scheme so unsupported ones are rejected rather than re-prefixed.
    val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    return runCatching {
        val uri = java.net.URI(candidate)
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https") return@runCatching null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null) return@runCatching null
        java.net.URI(scheme, null, uri.host, uri.port, uri.path, uri.query, null).toASCIIString()
    }.getOrNull()
}

/** The choices for how many entries each feed keeps; the middle one is the long-standing default. */
internal val FEED_KEEP_CHOICES = listOf(20, 40, 80)
internal const val DEFAULT_FEED_KEEP = 40

/** The most feeds one list will hold, however they arrive (typed, OPML or a backup). */
internal const val MAX_FEED_SOURCES = 200

/** Merges freshly fetched entries with the cached list: newest first, unknown dates last,
 * deduplicated by source + link, bounded per source and in total. Entries whose link is in
 * [savedLinks] survive both caps (up to [savedLimit]), so saving an item keeps it even after
 * it would otherwise fall off the end.
 */
internal fun mergeFeedEntries(
    cached: List<FeedEntry>,
    fresh: List<FeedEntry>,
    perSourceLimit: Int = DEFAULT_FEED_KEEP,
    totalLimit: Int = DEFAULT_FEED_KEEP * 3,
    savedLinks: Set<String> = emptySet(),
    savedLimit: Int = MAX_SAVED_LINKS,
): List<FeedEntry> {
    val seen = mutableSetOf<Pair<String, String>>()
    val merged = mutableListOf<FeedEntry>()
    fun add(entry: FeedEntry) {
        val key = entry.sourceId to entry.link
        if (seen.add(key)) merged += entry
    }
    (fresh + cached).sortedByDescending { it.publishedAt }.forEach { add(it) }
    val (saved, unsaved) = merged.partition { it.link in savedLinks }
    val keptSaved = saved.take(savedLimit)
    val remainingTotal = (totalLimit - keptSaved.size).coerceAtLeast(0)
    val keptUnsaved = unsaved.groupBy { it.sourceId }.values
        .flatMap { it.take(perSourceLimit) }
        .sortedByDescending { it.publishedAt }
        .take(remainingTotal)
    return (keptSaved + keptUnsaved).sortedByDescending { it.publishedAt }
}

/** How many opened links are remembered. Old ones are dropped first, so the set stays small and on-device. */
internal const val MAX_READ_LINKS = 200

/** How many saved links are remembered. */
internal const val MAX_SAVED_LINKS = 200

/** How many feeds from one host are fetched at once, so no single server is hammered by a refresh. */
internal const val PER_HOST_CONCURRENCY = 2

/** Records [newlyRead] as opened: it moves to the front, repeats drop out, and the oldest is forgotten past [max]. */
internal fun mergeReadLinks(existing: List<String>, newlyRead: String, max: Int = MAX_READ_LINKS): List<String> {
    if (newlyRead.isBlank()) return existing
    return (listOf(newlyRead) + existing.filter { it != newlyRead }).take(max)
}

/** Adds [link] to the saved set (front, deduped, capped) or removes it; a blank link changes nothing. */
internal fun mergeSavedLinks(existing: List<String>, link: String, saved: Boolean, max: Int = MAX_SAVED_LINKS): List<String> {
    if (link.isBlank()) return existing
    return if (saved) (listOf(link) + existing.filter { it != link }).take(max)
    else existing.filter { it != link }
}

/** [links] without the ones only [removed] entries carried: a link still carried by a remaining entry stays, so removing one
 *  of two feeds that share a story does not make the other forget that it was read or saved. */
internal fun linksAfterRemoving(links: List<String>, removed: List<FeedEntry>, remaining: List<FeedEntry>): List<String> {
    val gone = removed.mapTo(mutableSetOf()) { it.link } - remaining.mapTo(mutableSetOf()) { it.link }
    return links.filterNot { it in gone }
}

/** Narrows entries to one source (or all when [sourceId] is null) whose title or summary contains
 *  [query] (case-insensitive). An empty query means no search filter. A non-null [savedOnly] keeps only those links. */
internal fun filterFeedEntries(entries: List<FeedEntry>, sourceId: String?, query: String, savedOnly: Set<String>? = null): List<FeedEntry> {
    val q = query.trim()
    return entries.filter { entry ->
        (sourceId == null || entry.sourceId == sourceId) &&
            (savedOnly == null || entry.link in savedOnly) &&
            (q.isEmpty() || entry.title.contains(q, ignoreCase = true) || entry.summary.contains(q, ignoreCase = true))
    }
}

/** Short human-readable age for a cached entry. */
internal fun describeFeedAge(nowMillis: Long, publishedAt: Long): String {
    if (publishedAt <= 0L) return ""
    val minutes = (nowMillis - publishedAt) / 60_000L
    if (minutes < 0L) return ""
    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "${minutes}m ago"
        minutes < 60L * 24L -> "${minutes / 60L}h ago"
        minutes < 60L * 48L -> "yesterday"
        minutes < 60L * 24L * 7L -> "${minutes / (60L * 24L)}d ago"
        else -> DateTimeFormatter.ofPattern("MMM d")
            .format(Instant.ofEpochMilli(publishedAt).atZone(ZoneId.systemDefault()))
    }
}

/** The byline a card shows under the headline: "Source · 5m ago". Parts that are blank (a missing
 *  source label, an entry with no date) drop out rather than leaving a stray separator, so the
 *  line never reads "Source · " or "· 5m ago". */
internal fun describeEntryMeta(sourceLabel: String, nowMillis: Long, publishedAt: Long): String =
    listOfNotNull(
        sourceLabel.trim().takeIf { it.isNotBlank() },
        describeFeedAge(nowMillis, publishedAt).takeIf { it.isNotBlank() },
    ).joinToString(" · ")

/** How old the last fetch can be before the feed page flags its content as stale. Longer than the
 *  page's 15-minute auto-refresh window, so "stale" never flashes on a feed the user just opened. */
internal const val STALE_AFTER_MILLIS = 24L * 60L * 60L * 1000L

/** Whether the cached content is old enough to flag. A feed that has never been fetched is shown
 *  as "Never refreshed" rather than stale, so this is false when [lastRefreshAt] is unset. */
internal fun isFeedStale(nowMillis: Long, lastRefreshAt: Long, staleAfterMillis: Long = STALE_AFTER_MILLIS): Boolean =
    lastRefreshAt > 0L && nowMillis - lastRefreshAt > staleAfterMillis

/** The one-line callout for a partial fetch: names the first feed that failed and counts the rest,
 *  so the user knows which subscription is down without a wall of names. */
internal fun describeSourceErrors(labels: List<String>): String = when (labels.size) {
    0 -> "A feed couldn't be read."
    1 -> "${labels.first()} couldn't be read."
    else -> "${labels.first()} and ${labels.size - 1} other${if (labels.size - 1 == 1) "" else "s"} couldn't be read."
}

/** A feed to add, from an OPML file or a layout backup: its address, its name, and whether it is switched on. */
data class FeedSpec(val url: String, val label: String, val enabled: Boolean = true)

/** Owns feed sources and their on-device cache. Fetching is optional and direct:
 * Uno connects only to addresses the user added (redirects never leave that host), and
 * never uploads anything.
 */
class FeedStore(private val context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("feed", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<FeedState> = _state
    private var fetching = false
    /** One semaphore per host, so feeds on the same server share a fetch budget. */
    private val hostPermits = java.util.concurrent.ConcurrentHashMap<String, Semaphore>()
    /** Each source's last ETag / Last-Modified, for conditional (304) refreshes. */
    private var validators: Map<String, FeedValidator> = loadValidators()

    private fun load(): FeedState = FeedState(
        sources = decodeSources(runCatching { prefs.getString("sources", "[]") }.getOrDefault("[]") ?: "[]"),
        entries = decodeEntries(runCatching { prefs.getString("entries", "[]") }.getOrDefault("[]") ?: "[]"),
        lastRefreshAt = runCatching { prefs.getLong("last_refresh", 0L) }.getOrDefault(0L),
        // The feed is the main Discover surface; Google's own Discover is an extra where its app exists.
        feedPreferred = runCatching { prefs.getBoolean("preferred", true) }.getOrDefault(true),
        readLinks = decodeLinks(runCatching { prefs.getString("read_links", "[]") }.getOrDefault("[]") ?: "[]"),
        savedLinks = decodeLinks(runCatching { prefs.getString("saved_links", "[]") }.getOrDefault("[]") ?: "[]", max = MAX_SAVED_LINKS),
        keepPerFeed = runCatching { prefs.getInt("keep_per_feed", DEFAULT_FEED_KEEP) }.getOrDefault(DEFAULT_FEED_KEEP)
            .takeIf { it in FEED_KEEP_CHOICES } ?: DEFAULT_FEED_KEEP,
    )

    private fun save(state: FeedState) {
        prefs.edit()
            .putString("sources", encodeSources(state.sources))
            .putString("entries", encodeEntries(state.entries))
            .putLong("last_refresh", state.lastRefreshAt)
            .putBoolean("preferred", state.feedPreferred)
            .putString("read_links", encodeLinks(state.readLinks))
            .putString("saved_links", encodeLinks(state.savedLinks))
            .putInt("keep_per_feed", state.keepPerFeed)
            // Written in the same edit as the entries: a validator saved without the entries it answered for would make the next
            // refresh come back "not modified" and the new entries would never arrive.
            .putString("validators", encodeValidators())
            .apply()
    }

    private fun hostPermit(host: String): Semaphore = hostPermits.getOrPut(host) { Semaphore(PER_HOST_CONCURRENCY) }

    private fun loadValidators(): Map<String, FeedValidator> {
        val raw = runCatching { prefs.getString("validators", "{}") }.getOrDefault("{}") ?: "{}"
        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                root.keys().forEach { id ->
                    val entry = root.optJSONObject(id) ?: return@forEach
                    val etag = entry.optString("etag", "").trim().takeIf { it.isNotEmpty() }
                    val lastModified = entry.optString("last_modified", "").trim().takeIf { it.isNotEmpty() }
                    if (etag != null || lastModified != null) put(id, FeedValidator(etag, lastModified))
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodeValidators(): String {
        val root = JSONObject()
        validators.forEach { (id, validator) ->
            root.put(id, JSONObject().put("etag", validator.etag ?: "").put("last_modified", validator.lastModified ?: ""))
        }
        return root.toString()
    }

    /** Refreshes every source once; no-ops while a refresh is already running. Sources are fetched
     *  concurrently, at most [PER_HOST_CONCURRENCY] at a time per host, and each request carries the
     *  source's last ETag / Last-Modified so an unchanged feed is answered with 304 instead of a download. */
    fun refresh() {
        val sources = _state.value.sources.filter { it.enabled }
        if (sources.isEmpty() || fetching) return
        fetching = true
        _state.update { it.copy(refreshing = true) }
        scope.launch {
            // However this ends (even an unexpected exception from a fetch), the flags are released, so a refresh can never
            // leave the page stuck on "checking" with every later refresh refused.
            try {
                val collected = mutableListOf<FeedEntry>()
                val sourceErrors = mutableMapOf<String, String>()
                val labels = mutableMapOf<String, String>()
                // Fetch every source in parallel (bounded per host), then process in source order.
                val results = sources.map { source ->
                    async(Dispatchers.IO) {
                        val host = runCatching { java.net.URI(source.url).host }.getOrNull().orEmpty()
                        val result = hostPermit(host).withPermit {
                            FeedFetcher.fetch(source.url, validators[source.id] ?: FeedValidator())
                        }
                        source to result
                    }
                }.awaitAll()
                for ((source, result) in results) {
                    when (result) {
                        is FeedFetchResult.Success -> {
                            val label = result.feed.title ?: source.label
                            if (source.label != label) labels[source.id] = label
                            collected += result.feed.entries.map { it.toFeedEntry(source.id, label) }
                            validators = validators + (source.id to result.validator)
                        }
                        is FeedFetchResult.NotModified -> Unit
                        is FeedFetchResult.Failure -> sourceErrors[source.id] = result.message
                    }
                }
                var updated = _state.value.takeIf { it.refreshing } ?: return@launch
                // A feed that names itself differently now is renamed here, on the state as it is now (a rename applied to a copy taken
                // before the fetch would be thrown away by the line above).
                labels.forEach { (id, label) -> updated = relabel(updated, id, label) }
                // Merging sorts and bounds the whole cache (up to a few thousand entries across 200 feeds); it runs off the main
                // thread so a large refresh never janks the page while the results arrive.
                val merged = withContext(Dispatchers.Default) {
                    mergeFeedEntries(updated.entries, collected, perSourceLimit = updated.keepPerFeed,
                        totalLimit = updated.keepTotal, savedLinks = updated.savedLinks.toSet())
                }
                val succeeded = results.count { it.second !is FeedFetchResult.Failure }
                _state.value = updated.copy(entries = merged, refreshing = false, sourceErrors = sourceErrors,
                    lastRefreshAt = if (succeeded > 0) System.currentTimeMillis() else updated.lastRefreshAt)
                save(_state.value)
            } finally {
                fetching = false
                if (_state.value.refreshing) _state.update { it.copy(refreshing = false) }
            }
        }
    }

    fun refreshIfStale(maxAgeMillis: Long = 15L * 60L * 1000L) {
        val state = _state.value
        if (state.anyEnabled &&
            System.currentTimeMillis() - state.lastRefreshAt > maxAgeMillis) refresh()
    }

    /** Validates, fetches once, and adds a source. The result callback runs on the main thread. */
    fun addFeed(rawUrl: String, onResult: (FeedAddResult) -> Unit) {
        val url = normalizeFeedUrl(rawUrl)
        if (url == null) {
            onResult(FeedAddResult.Rejected("Enter a web address that starts with https://"))
            return
        }
        if (_state.value.sources.any { it.url == url }) {
            onResult(FeedAddResult.Rejected("That feed is already added."))
            return
        }
        if (_state.value.sources.size >= MAX_FEED_SOURCES) {
            onResult(FeedAddResult.Rejected("You can follow up to $MAX_FEED_SOURCES feeds."))
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) { FeedFetcher.fetch(url) }
            when (result) {
                is FeedFetchResult.Success -> {
                    val id = UUID.randomUUID().toString().take(8)
                    val source = FeedSource(id, url, result.feed.title ?: url)
                    val fetched = result.feed.entries.map { it.toFeedEntry(id, source.label) }
                    validators = validators + (id to result.validator)
                    _state.update {
                        it.copy(sources = it.sources + source,
                            entries = mergeFeedEntries(it.entries, fetched, perSourceLimit = it.keepPerFeed, totalLimit = it.keepTotal,
                                savedLinks = it.savedLinks.toSet()),
                            lastRefreshAt = System.currentTimeMillis())
                    }
                    save(_state.value)
                    onResult(FeedAddResult.Added)
                }
                is FeedFetchResult.NotModified -> onResult(FeedAddResult.Rejected("The feed answered unexpectedly."))
                is FeedFetchResult.Failure -> onResult(FeedAddResult.Rejected(result.message))
            }
        }
    }

    /** Removes a feed with everything it left behind: its cached entries, its validators, and the read or saved marks that only its
     *  entries carried (a story another feed also carries keeps its marks). */
    fun removeFeed(id: String) {
        _state.update { state ->
            val remaining = state.entries.filterNot { it.sourceId == id }
            val removed = state.entries.filter { it.sourceId == id }
            state.copy(sources = state.sources.filterNot { it.id == id }, entries = remaining,
                readLinks = linksAfterRemoving(state.readLinks, removed, remaining),
                savedLinks = linksAfterRemoving(state.savedLinks, removed, remaining),
                sourceErrors = state.sourceErrors - id)
        }
        validators = validators - id
        save(_state.value)
    }

    /** Turns a saved feed on or off. Turning one on fetches it straight away so it isn't empty or stale. */
    fun setSourceEnabled(id: String, enabled: Boolean) {
        _state.update { it.copy(sources = it.sources.map { s -> if (s.id == id) s.copy(enabled = enabled) else s }) }
        save(_state.value)
        if (enabled) refresh()
    }

    fun setPreferred(preferred: Boolean) {
        _state.update { it.copy(feedPreferred = preferred) }
        save(_state.value)
    }

    /** Sets how many entries each feed keeps ([FEED_KEEP_CHOICES]) and trims what is cached to match at once. Saved entries are never trimmed. */
    fun setKeepPerFeed(keep: Int) {
        if (keep !in FEED_KEEP_CHOICES) return
        _state.update { state ->
            val next = state.copy(keepPerFeed = keep)
            next.copy(entries = mergeFeedEntries(next.entries, emptyList(), perSourceLimit = keep, totalLimit = next.keepTotal,
                savedLinks = next.savedLinks.toSet()))
        }
        save(_state.value)
    }

    /** Records that the user opened [link], so it dims on the page and stops counting as new. */
    fun markRead(link: String) {
        if (link.isBlank() || _state.value.isRead(link)) return
        _state.update { it.copy(readLinks = mergeReadLinks(it.readLinks, link)) }
        save(_state.value)
    }

    /** Saves [link] for later, or unsaves it. A saved entry is kept past the normal cache cap. */
    fun toggleSaved(link: String) {
        if (link.isBlank()) return
        _state.update { state ->
            state.copy(savedLinks = mergeSavedLinks(state.savedLinks, link, !state.isSaved(link)))
        }
        save(_state.value)
    }

    /** Adds every feed in an OPML document that isn't already present, then refreshes once. Returns how many were
     *  added and how many were skipped (invalid, already in the list, or past the limit). */
    fun importOpml(xml: String): FeedOpmlResult =
        addSources(parseOpml(xml).map { FeedSpec(it.url, it.label) })

    /** Adds the feeds of a layout backup that are not already followed (it never removes or changes the ones you have), then
     *  refreshes once. Returns how many were added. */
    internal fun restoreFeeds(feeds: List<FeedSpec>): Int = addSources(feeds).added

    private fun addSources(candidates: List<FeedSpec>): FeedOpmlResult {
        val existing = _state.value.sources.mapTo(mutableSetOf()) { it.url }
        val added = mutableListOf<FeedSource>()
        var skipped = 0
        for (feed in candidates) {
            val url = normalizeFeedUrl(feed.url)
            if (url == null || url in existing || _state.value.sources.size + added.size >= MAX_FEED_SOURCES) { skipped++; continue }
            existing += url
            added += FeedSource(UUID.randomUUID().toString().take(8), url, feed.label.ifBlank { url }, feed.enabled)
        }
        if (added.isNotEmpty()) {
            _state.update { it.copy(sources = it.sources + added) }
            save(_state.value)
            refresh()
        }
        return FeedOpmlResult(added.size, skipped)
    }

    private fun relabel(state: FeedState, sourceId: String, label: String) = state.copy(
        sources = state.sources.map { if (it.id == sourceId) it.copy(label = label) else it },
        entries = state.entries.map { if (it.sourceId == sourceId) it.copy(sourceLabel = label) else it })

    private fun ParsedFeedEntry.toFeedEntry(sourceId: String, sourceLabel: String) =
        FeedEntry(sourceId, sourceLabel, title, link, summary, publishedAt)

    private fun encodeSources(sources: List<FeedSource>) = JSONArray().also { array ->
        sources.forEach { source -> array.put(JSONObject()
            .put("id", source.id).put("url", source.url).put("label", source.label).put("enabled", source.enabled)) }
    }.toString()

    private fun decodeSources(raw: String): List<FeedSource> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val url = item.optString("url", "")
                val id = item.optString("id", "").ifBlank { url.hashCode().toString(36) }
                add(FeedSource(id, url, item.optString("label", url), item.optBoolean("enabled", true)))
            }
        }.filter { it.url.isNotBlank() }
    }.getOrDefault(emptyList())

    private fun encodeLinks(links: List<String>) = JSONArray().also { array ->
        links.forEach { array.put(it) }
    }.toString()

    private fun decodeLinks(raw: String, max: Int = MAX_READ_LINKS): List<String> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val link = array.optString(index, "").trim()
                if (link.isNotEmpty()) add(link)
            }
        }.distinct().take(max)
    }.getOrDefault(emptyList())

    private fun encodeEntries(entries: List<FeedEntry>) = JSONArray().also { array ->
        entries.forEach { entry -> array.put(JSONObject()
            .put("sourceId", entry.sourceId).put("sourceLabel", entry.sourceLabel)
            .put("title", entry.title).put("link", entry.link)
            .put("summary", entry.summary).put("publishedAt", entry.publishedAt)) }
    }.toString()

    private fun decodeEntries(raw: String): List<FeedEntry> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(FeedEntry(
                    sourceId = item.optString("sourceId", ""),
                    sourceLabel = item.optString("sourceLabel", ""),
                    title = item.optString("title", ""),
                    link = item.optString("link", ""),
                    summary = item.optString("summary", ""),
                    publishedAt = item.optLong("publishedAt", 0L)))
            }
        }.filter { it.title.isNotBlank() && it.link.isNotBlank() }
    }.getOrDefault(emptyList())
}
