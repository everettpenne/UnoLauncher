package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class SpotlightRankTest {
    private fun rank(labels: List<String>, q: String) = SpotlightRank.apps(labels, q) { it }

    @Test fun prefixBeatsWordPrefixBeatsContainsThenAlphabetical() {
        val labels = listOf("Unit Converter", "Fortune", "Tune In", "Tuner", "Maps", "Late Tuner")
        assertEquals(listOf("Tune In", "Tuner", "Late Tuner", "Fortune"), rank(labels, "tun"))
        assertEquals(listOf("Maps"), rank(labels, "MAP"))
    }

    @Test fun blankQueryAndNoMatchGiveNothingAndResultsAreCapped() {
        val labels = (1..20).map { "Alpha $it" }
        assertTrue(rank(labels, "  ").isEmpty())
        assertTrue(rank(labels, "zzz").isEmpty())
        assertEquals(SpotlightRank.MAX_APPS, rank(labels, "alpha").size)
    }

    @Test fun feedEntriesMustMentionEveryWord() {
        val entries = listOf(
            FeedEntry("s", "S", "Kernel update released", "https://x/1", "Security fixes"),
            FeedEntry("s", "S", "Browser update", "https://x/2", "New features"))
        assertEquals(listOf("https://x/1"), SpotlightRank.feed(entries, "kernel update").map { it.link })
        assertEquals(2, SpotlightRank.feed(entries, "update").size)
        assertTrue(SpotlightRank.feed(entries, "a").isEmpty())
    }
}
