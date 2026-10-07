package com.jake.duolauncher

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

    private fun entry(sourceId: String, title: String, publishedAt: Long) =
        FeedEntry(sourceId, sourceId, title, "https://example.com/$title", publishedAt = publishedAt)
}
