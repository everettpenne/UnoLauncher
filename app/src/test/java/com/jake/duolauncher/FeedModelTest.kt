package com.jake.duolauncher

import com.jake.duolauncher.feed.*

import org.junit.Assert.*
import org.junit.Test

class FeedModelTest {
    @Test fun feedAddressesAreNormalizedOrRejected() {
        assertEquals("https://example.com/feed", normalizeFeedUrl("example.com/feed"))
        assertEquals("https://example.com/feed", normalizeFeedUrl(" https://example.com/feed "))
        assertTrue(normalizeFeedUrl("HTTPS://Example.com/a?b=1")!!.startsWith("https://"))
        assertNull(normalizeFeedUrl(""))
        assertNull(normalizeFeedUrl("   "))
        assertNull(normalizeFeedUrl("ftp://example.com/feed"))
        assertNull(normalizeFeedUrl("http://example.com/feed"))
        assertNull(normalizeFeedUrl("https://user:pass@example.com/feed"))
        assertNull(normalizeFeedUrl("not a url at all"))
    }

    @Test fun suggestedFeedsAreHttpsAndNormalizeToThemselves() {
        assertTrue(SUGGESTED_FEEDS.isNotEmpty())
        SUGGESTED_FEEDS.forEach { suggestion ->
            assertEquals(suggestion.url, normalizeFeedUrl(suggestion.url))
            assertTrue(suggestion.host.isNotBlank())
        }
        assertEquals(SUGGESTED_FEEDS.size, SUGGESTED_FEEDS.map { it.url }.toSet().size)
    }

    @Test fun mergeKeepsNewestFirstAndDeduplicatesBySourceAndLink() {
        val cached = listOf(entry("s1", "old", 100), entry("s1", "kept", 300))
        val fresh = listOf(entry("s1", "kept", 300), entry("s1", "new", 500), entry("s2", "other", 200))
        val merged = mergeFeedEntries(cached, fresh)
        assertEquals(listOf("new", "kept", "other", "old"), merged.map { it.title })
    }

    @Test fun mergeCapsPerSourceAndTotal() {
        val fresh = (1..10).map { entry("s1", "f$it", it.toLong()) } +
            (1..10).map { entry("s2", "g$it", 1000L + it) }
        val merged = mergeFeedEntries(emptyList(), fresh, perSourceLimit = 3, totalLimit = 6)
        assertEquals(6, merged.size)
        assertEquals(3, merged.count { it.sourceId == "s1" })
        assertEquals(3, merged.count { it.sourceId == "s2" })
    }

    @Test fun agesDescribeRoughly() {
        val now = 1_000_000_000_000L
        assertEquals("", describeFeedAge(now, 0L))
        assertEquals("", describeFeedAge(now, now + 60_000L))
        assertEquals("just now", describeFeedAge(now, now - 30_000L))
        assertEquals("5m ago", describeFeedAge(now, now - 5 * 60_000L))
        assertEquals("2h ago", describeFeedAge(now, now - 2 * 3_600_000L))
        assertEquals("yesterday", describeFeedAge(now, now - 30 * 3_600_000L))
        assertEquals("3d ago", describeFeedAge(now, now - 3 * 86_400_000L))
        assertTrue(Regex("[A-Z][a-z]{2} \\d{1,2}").matches(describeFeedAge(now, now - 20 * 86_400_000L)))
    }

    @Test fun readLinksMoveNewestFirstDedupeAndCap() {
        assertEquals(listOf("b", "a"), mergeReadLinks(listOf("a"), "b"))
        assertEquals("a repeat stays put", listOf("a", "b"), mergeReadLinks(listOf("a", "b"), "a"))
        assertEquals("blank is ignored", listOf("a"), mergeReadLinks(listOf("a"), "  "))
        val full = (1..MAX_READ_LINKS).map { "https://example.com/$it" }
        val capped = mergeReadLinks(full, "https://example.com/new", max = 5)
        assertEquals(5, capped.size)
        assertEquals("https://example.com/new", capped.first())
    }

    @Test fun unreadCountOnlyCountsShownUnopenedEntries() {
        val entries = listOf(
            entry("s1", "a", 300),
            entry("s1", "b", 200),
            entry("s2", "off", 100),
        )
        val state = FeedState(
            sources = listOf(FeedSource("s1", "https://a", "a", true), FeedSource("s2", "https://b", "b", false)),
            entries = entries,
            readLinks = listOf("https://example.com/a"),
        )
        assertEquals("the switched-off source never counts", 1, state.unreadCount)
        assertTrue(state.isRead("https://example.com/a"))
        assertTrue(!state.isRead("https://example.com/b"))
    }

    @Test fun savedLinksAddFrontDedupeCapAndRemove() {
        assertEquals(listOf("b", "a"), mergeSavedLinks(listOf("a"), "b", saved = true))
        assertEquals("a repeat stays put", listOf("a", "b"), mergeSavedLinks(listOf("a", "b"), "a", saved = true))
        assertEquals("unsaving removes it", listOf("a"), mergeSavedLinks(listOf("a", "b"), "b", saved = false))
        assertEquals("blank changes nothing", listOf("a"), mergeSavedLinks(listOf("a"), "  ", saved = true))
        val full = (1..MAX_SAVED_LINKS).map { "https://example.com/$it" }
        assertEquals(5, mergeSavedLinks(full, "https://example.com/new", saved = true, max = 5).size)
    }

    @Test fun savedEntriesSurviveTheCap() {
        val fresh = (1..10).map { entry("s1", "new$it", 1000L + it) }
        val cached = (1..5).map { entry("s1", "old$it", it.toLong()) }
        val savedLink = "https://example.com/old1"
        // 15 entries, cap 12: old1 falls off normally, but survives once saved.
        assertTrue(mergeFeedEntries(cached, fresh, totalLimit = 12).none { it.link == savedLink })
        val merged = mergeFeedEntries(cached, fresh, totalLimit = 12, savedLinks = setOf(savedLink))
        assertTrue(merged.any { it.link == savedLink })
        val state = FeedState(entries = merged, savedLinks = listOf(savedLink))
        assertTrue(state.isSaved(savedLink))
        assertEquals(1, state.savedCount)
    }

    @Test fun conditionalHeadersFollowTheValidator() {
        assertTrue(conditionalHeaders(FeedValidator()).isEmpty())
        assertEquals(mapOf("If-None-Match" to "\"abc\""), conditionalHeaders(FeedValidator(etag = "\"abc\"")))
        assertEquals(mapOf("If-Modified-Since" to "Wed, 21 Oct 2015 07:28:00 GMT"),
            conditionalHeaders(FeedValidator(lastModified = "Wed, 21 Oct 2015 07:28:00 GMT")))
        assertEquals(2, conditionalHeaders(FeedValidator("\"abc\"", "Wed, 21 Oct 2015 07:28:00 GMT")).size)
    }

    @Test fun theValidatorToKeepFollowsTheResult() {
        val old = FeedValidator(etag = "\"old\"")
        val success = FeedFetchResult.Success(ParsedFeed(title = null, entries = emptyList()), FeedValidator(etag = "\"new\""))
        assertEquals(FeedValidator("\"new\""), mergeValidator(old, success))
        assertEquals("a 304 keeps the old validator", old, mergeValidator(old, FeedFetchResult.NotModified))
        assertEquals("a failure keeps the old validator", old, mergeValidator(old, FeedFetchResult.Failure("boom")))
    }

    @Test fun filterNarrowsBySourceAndSearchesTitleAndSummary() {
        val a = FeedEntry("s1", "S1", "GrapheneOS release", "https://x/a", "The stable channel", 300)
        val b = FeedEntry("s2", "S2", "Train delays today", "https://x/b", "Service updates", 200)
        val c = FeedEntry("s1", "S1", "Battery news", "https://x/c", "A train of thought", 100)
        val all = listOf(a, b, c)
        assertEquals(listOf("https://x/a", "https://x/c"), filterFeedEntries(all, "s1", "").map { it.link })
        assertEquals("matches title and summary, case-insensitive", listOf("https://x/b", "https://x/c"),
            filterFeedEntries(all, null, "TRAIN").map { it.link })
        assertEquals("an empty query means no search filter", all, filterFeedEntries(all, null, "  "))
        assertEquals(listOf("https://x/b"), filterFeedEntries(all, "s2", "train").map { it.link })
        assertEquals(emptyList<FeedEntry>(), filterFeedEntries(all, "s2", "release"))
    }

    private fun entry(sourceId: String, title: String, publishedAt: Long) =
        FeedEntry(sourceId, sourceId, title, "https://example.com/$title", publishedAt = publishedAt)

    private fun anEntry(source: String, link: String, at: Long) = FeedEntry(source, source, "title $link", link, "", at)

    @Test fun theKeepSettingBoundsEachFeedAndTheWholeCache() {
        val entries = (1..30).map { anEntry("a", "a$it", it.toLong()) } + (1..30).map { anEntry("b", "b$it", 100L + it) } +
            (1..30).map { anEntry("c", "c$it", 200L + it) }
        val kept = mergeFeedEntries(entries, emptyList(), perSourceLimit = 20, totalLimit = 60)
        assertEquals(60, kept.size)
        assertTrue(kept.groupBy { it.sourceId }.values.all { it.size <= 20 })
        assertTrue(DEFAULT_FEED_KEEP in FEED_KEEP_CHOICES)
        assertEquals(DEFAULT_FEED_KEEP * 3, FeedState().keepTotal)
        assertEquals(60, FeedState(keepPerFeed = 20).keepTotal)
    }

    @Test fun savedEntriesAreNeverTrimmedByAnySetting() {
        val entries = (1..50).map { anEntry("a", "a$it", it.toLong()) }
        val kept = mergeFeedEntries(entries, emptyList(), perSourceLimit = 20, totalLimit = 60, savedLinks = setOf("a1", "a2"))
        assertTrue(kept.any { it.link == "a1" } && kept.any { it.link == "a2" })
        assertEquals(20 + 2, kept.size)
    }

    @Test fun removingAFeedKeepsMarksOnStoriesAnotherFeedStillCarries() {
        val removed = listOf(anEntry("a", "l1", 1), anEntry("a", "l2", 2))
        val remaining = listOf(anEntry("b", "l2", 2), anEntry("b", "l3", 3))
        assertEquals(listOf("l2", "l3"), linksAfterRemoving(listOf("l1", "l2", "l3"), removed, remaining))
        assertEquals(listOf("l3"), linksAfterRemoving(listOf("l1", "l3"), removed, remaining))
        assertEquals(emptyList<String>(), linksAfterRemoving(emptyList(), removed, remaining))
    }

    @Test fun theSavedViewShowsOnlySavedStoriesAndStillSearchesAndFilters() {
        val entries = listOf(anEntry("a", "l1", 3), anEntry("a", "l2", 2), anEntry("b", "l3", 1))
        assertEquals(listOf("l1", "l3"), filterFeedEntries(entries, null, "", setOf("l1", "l3")).map { it.link })
        assertEquals(listOf("l3"), filterFeedEntries(entries, "b", "", setOf("l1", "l3")).map { it.link })
        assertEquals(listOf("l1"), filterFeedEntries(entries, null, "l1", setOf("l1", "l3")).map { it.link })
        assertEquals(3, filterFeedEntries(entries, null, "", null).size)
        assertEquals(0, FeedState().savedShownCount)
        assertEquals(1, FeedState(sources = listOf(FeedSource("a", "https://a", "A")), entries = entries, savedLinks = listOf("l1", "l3")).savedShownCount)
    }
}
