package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class FeedEnabledTest {
    private fun entry(source: String, n: Int) = FeedEntry(source, source, "t$n", "https://x/$source/$n")

    @Test fun switchedOffFeedsStaySavedButTheirEntriesAreHidden() {
        val state = FeedState(
            sources = listOf(FeedSource("a", "https://a", "A"), FeedSource("b", "https://b", "B", enabled = false)),
            entries = listOf(entry("a", 1), entry("b", 2), entry("a", 3)))
        assertEquals(listOf("t1", "t3"), state.shownEntries.map { it.title })
        assertEquals(3, state.entries.size)
        assertTrue(state.anyEnabled)
    }

    @Test fun nothingShownWhenEveryFeedIsOff() {
        val state = FeedState(sources = listOf(FeedSource("a", "https://a", "A", enabled = false)), entries = listOf(entry("a", 1)))
        assertTrue(state.shownEntries.isEmpty())
        assertFalse(state.anyEnabled)
        assertTrue(state.configured)
    }

    @Test fun newFeedsDefaultToOn() {
        assertTrue(FeedSource("a", "https://a", "A").enabled)
    }
}
