package com.jake.duolauncher

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val message: String? = null,
    val lastRefreshAt: Long = 0L,
    val feedPreferred: Boolean = false,
) {
    val configured: Boolean get() = sources.isNotEmpty()
    /** Entries from the feeds that are switched on; switched-off feeds stay saved but never reach the page. */
    val shownEntries: List<FeedEntry> get() {
        val on = sources.filter { it.enabled }.mapTo(mutableSetOf()) { it.id }
        return entries.filter { it.sourceId in on }
    }
    val anyEnabled: Boolean get() = sources.any { it.enabled }
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

/** Merges freshly fetched entries with the cached list: newest first, unknown dates last,
 * deduplicated by source + link, bounded per source and in total.
 */
internal fun mergeFeedEntries(
    cached: List<FeedEntry>,
    fresh: List<FeedEntry>,
    perSourceLimit: Int = 40,
    totalLimit: Int = 120,
): List<FeedEntry> {
    val seen = mutableSetOf<Pair<String, String>>()
    val merged = mutableListOf<FeedEntry>()
    fun add(entry: FeedEntry) {
        val key = entry.sourceId to entry.link
        if (seen.add(key)) merged += entry
    }
    (fresh + cached).sortedByDescending { it.publishedAt }.forEach { add(it) }
    val grouped = merged.groupBy { it.sourceId }
    val bounded = grouped.values.flatMap { it.take(perSourceLimit) }
    return bounded.sortedByDescending { it.publishedAt }.take(totalLimit)
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

/** Owns feed sources and their on-device cache. Fetching is optional and direct:
 * Uno connects only to addresses the user added (redirects never leave that host), and
 * never uploads anything.
 */
class FeedStore(private val context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("feed", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<FeedState> = _state
    private var fetching = false

    private fun load(): FeedState = FeedState(
        sources = decodeSources(runCatching { prefs.getString("sources", "[]") }.getOrDefault("[]") ?: "[]"),
        entries = decodeEntries(runCatching { prefs.getString("entries", "[]") }.getOrDefault("[]") ?: "[]"),
        lastRefreshAt = runCatching { prefs.getLong("last_refresh", 0L) }.getOrDefault(0L),
        // The feed is the main Discover surface; Google's own Discover is an extra where its app exists.
        feedPreferred = runCatching { prefs.getBoolean("preferred", true) }.getOrDefault(true),
    )

    private fun save(state: FeedState) {
        prefs.edit()
            .putString("sources", encodeSources(state.sources))
            .putString("entries", encodeEntries(state.entries))
            .putLong("last_refresh", state.lastRefreshAt)
            .putBoolean("preferred", state.feedPreferred)
            .apply()
    }

    /** Refreshes every source once; no-ops while a refresh is already running. */
    fun refresh() {
        val sources = _state.value.sources.filter { it.enabled }
        if (sources.isEmpty() || fetching) return
        fetching = true
        _state.update { it.copy(refreshing = true, message = null) }
        scope.launch {
            val failures = mutableListOf<String>()
            val collected = mutableListOf<FeedEntry>()
            var updated = _state.value
            for (source in sources) {
                val result = withContext(Dispatchers.IO) { FeedFetcher.fetch(source.url) }
                when (result) {
                    is FeedFetchResult.Success -> {
                        val label = result.feed.title ?: source.label
                        if (source.label != label) updated = relabel(updated, source.id, label)
                        collected += result.feed.entries.map { it.toFeedEntry(source.id, label) }
                    }
                    is FeedFetchResult.Failure -> failures += "${source.label}: ${result.message}"
                }
            }
            updated = _state.value.takeIf { it.refreshing } ?: return@launch
            val merged = mergeFeedEntries(updated.entries, collected)
            _state.value = updated.copy(entries = merged, refreshing = false,
                message = if (failures.isEmpty()) null else failures.joinToString("\n"),
                lastRefreshAt = if (failures.isEmpty() || collected.isNotEmpty())
                    System.currentTimeMillis() else updated.lastRefreshAt)
            save(_state.value)
            fetching = false
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
        scope.launch {
            val result = withContext(Dispatchers.IO) { FeedFetcher.fetch(url) }
            when (result) {
                is FeedFetchResult.Success -> {
                    val id = UUID.randomUUID().toString().take(8)
                    val source = FeedSource(id, url, result.feed.title ?: url)
                    val fetched = result.feed.entries.map { it.toFeedEntry(id, source.label) }
                    _state.update {
                        it.copy(sources = it.sources + source,
                            entries = mergeFeedEntries(it.entries, fetched),
                            lastRefreshAt = System.currentTimeMillis())
                    }
                    save(_state.value)
                    onResult(FeedAddResult.Added)
                }
                is FeedFetchResult.Failure -> onResult(FeedAddResult.Rejected(result.message))
            }
        }
    }

    fun removeFeed(id: String) {
        _state.update {
            it.copy(sources = it.sources.filterNot { source -> source.id == id },
                entries = it.entries.filterNot { entry -> entry.sourceId == id })
        }
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
