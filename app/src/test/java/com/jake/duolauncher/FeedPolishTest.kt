package com.jake.duolauncher

import com.jake.duolauncher.feed.*
import org.junit.Assert.*
import org.junit.Test

/** Pure-logic rules added by the feed polish pass: stale detection and the partial-fetch callout. */
class FeedPolishTest {
    @Test fun staleIsReportedOnlyAfterTheThreshold() {
        val now = 1_000_000_000_000L
        assertFalse("a fetch just now is not stale", isFeedStale(now, now - 60_000L))
        assertFalse("exactly the threshold is not stale", isFeedStale(now, now - STALE_AFTER_MILLIS))
        assertTrue("past the threshold is stale", isFeedStale(now, now - STALE_AFTER_MILLIS - 1L))
        assertFalse("never fetched is 'Never refreshed', not stale", isFeedStale(now, 0L))
    }

    @Test fun aStaleThresholdCanBeOverridden() {
        val now = 1_000_000_000_000L
        assertTrue(isFeedStale(now, now - 2L * 60L * 60L * 1000L, staleAfterMillis = 60L * 60L * 1000L))
        assertFalse(isFeedStale(now, now - 30L * 60L * 1000L, staleAfterMillis = 60L * 60L * 1000L))
    }

    @Test fun errorCalloutNamesTheFirstFeedAndCountsTheRest() {
        assertEquals("A feed couldn't be read.", describeSourceErrors(emptyList()))
        assertEquals("Example News couldn't be read.", describeSourceErrors(listOf("Example News")))
        assertEquals("Example News and 1 other couldn't be read.",
            describeSourceErrors(listOf("Example News", "Releases")))
        assertEquals("Example News and 2 others couldn't be read.",
            describeSourceErrors(listOf("Example News", "Releases", "Forum")))
    }
}
