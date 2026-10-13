package com.jake.duolauncher

import com.jake.duolauncher.feed.*
import com.jake.duolauncher.search.*

import org.junit.Assert.*
import org.junit.Test

class SpotlightRankTest {
    private fun rank(labels: List<String>, q: String) = SpotlightRank.apps(labels, q, label = { it })

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

class SpotlightRankSmartTest {
    private fun rank(labels: List<String>, q: String, score: (String) -> Double = { 0.0 }) = SpotlightRank.apps(labels, q, { it }, score)

    @Test fun initialsFindMultiWordNames() {
        val labels = listOf("YouTube Music", "Google Maps", "Calculator")
        assertEquals(listOf("YouTube Music"), rank(labels, "ytm"))
        assertEquals(listOf("Google Maps"), rank(labels, "gm"))
    }

    @Test fun fuzzySubsequenceNeedsThreeLetters() {
        val labels = listOf("Calculator", "Camera")
        assertEquals(listOf("Calculator"), rank(labels, "clr"))
        assertTrue(rank(labels, "cr").isEmpty())
    }

    @Test fun frecencyBreaksTiesWithinATierButNotAcrossTiers() {
        val labels = listOf("Signal", "Sigma", "Design Sign")
        val scores = mapOf("Sigma" to 5.0, "Design Sign" to 100.0)
        assertEquals(listOf("Sigma", "Signal", "Design Sign"), rank(labels, "sig") { scores[it] ?: 0.0 })
    }

    @Test fun frecencyDecaysAndSuggestionsSkipUnusedApps() {
        val day = 86_400_000L
        assertEquals(1.0, SpotlightRank.frecency(1, 0, 0), 1e-9)
        assertEquals(.5, SpotlightRank.frecency(1, 0, 14 * day), 1e-9)
        assertEquals(0.0, SpotlightRank.frecency(0, 0, 0), 0.0)
        val suggested = SpotlightRank.suggested(listOf("a", "b", "c")) { mapOf("a" to 3.0, "c" to 1.0)[it] ?: 0.0 }
        assertEquals(listOf("a", "c"), suggested)
    }

    @Test fun dateAndTimeAnswersComeFromTheClock() {
        val now = java.time.LocalDateTime.of(2026, 10, 12, 9, 5)
        assertEquals("09:05", SearchSmarts.answer("time", now)?.text)
        assertTrue(SearchSmarts.answer("What is the date?", now)?.text?.contains("12") == true)
        assertNull(SearchSmarts.answer("timer", now))
    }
}
