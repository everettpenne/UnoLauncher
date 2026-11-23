package com.jake.duolauncher

import com.jake.duolauncher.island.ActivitySource
import com.jake.duolauncher.island.LiveUpdate
import com.jake.duolauncher.island.LiveUpdateLogic
import com.jake.duolauncher.island.SportsLogic
import org.junit.Assert.*
import org.junit.Test

/** A score is recognised in the ways sports apps say one, and in nothing else: a scoreboard on a download would be worse than none. */
class SportsScoreTest {
    private fun score(title: String?, text: String? = null) = SportsLogic.parse(title, text)
    private fun line(title: String?, text: String? = null) = score(title, text)?.let { "${it.left.name} ${it.left.score} | ${it.right.name} ${it.right.score} | ${it.status}" }

    @Test fun theWaysAScoreIsSaid() {
        assertEquals("Arsenal 2 | Chelsea 1 | null", line("Arsenal 2 - 1 Chelsea"))
        assertEquals("Arsenal 2 | Chelsea 1 | 67'", line("Arsenal 2–1 Chelsea (67')"))
        assertEquals("LAL 98 | BOS 101 | null", line("LAL 98, BOS 101"))
        assertEquals("Lakers 98 | Celtics 101 | Q3 4:21", line("Lakers 98 @ Celtics 101 · Q3 4:21"))
        assertEquals("India 245 | Australia 198 | null", line("India 245 vs Australia 198"))
        assertEquals("Real Madrid 0 | FC Barcelona 0 | HT", line("Real Madrid 0 : 0 FC Barcelona | HT"))
        assertEquals("49ers 21 | Seahawks 17 | Final", line("49ers 21 - 17 Seahawks - Final"))
        assertEquals("Man City 3 | St. Pauli 3 | null", line("Man City 3-3 St. Pauli"))
    }

    @Test fun whereTheGameIsCanBeTheLineUnderTheScore() {
        assertEquals("Arsenal 2 | Chelsea 1 | Half time", line("Arsenal 2 - 1 Chelsea", "Half time"))
        assertEquals("the title's own status wins", "Arsenal 2 | Chelsea 1 | 67'", line("Arsenal 2 - 1 Chelsea (67')", "Premier League"))
        // The score can be in the text, under a title that names the game.
        assertEquals("Lakers 98 | Celtics 101 | null", line("NBA · Live", "Lakers 98, Celtics 101\nTap for play-by-play"))
        assertNull("a long line under it is not where the game is", score("Arsenal 2 - 1 Chelsea", "Saka scores from the edge of the area after a fine move down the right")?.status)
    }

    @Test fun whatIsNotAScore() {
        for (title in listOf(null, "", "Arsenal v Chelsea", "Kick-off at 3", "Downloading 2 - 1 files", "Step 2 - 1 of 5", "Uploading photos 3, items 12",
            "Track 4 - 12 Greatest Hits", "Sam: see you at 5 - 6 ok", "2 - 1", "Page 12, chapter 3", "Battery 80 - 20 minutes left", "A 1 - 2",
            "Sync 3 files, 2 messages 4", "Version 2 - 1 available", "Call with Priya 12:31", "Timer 4:59",
            // What rides, deliveries and boarding passes say, which the island already shows as Live Updates and must go on showing so.
            "Arriving at 5:42 PM", "Flight 204 Gate 12", "Recording 0:42 tap to stop", "Terminal 2 Gate 15", "Driver arrives 5:42", "Order 12 - 3 items",
            "Pickup at 10:15 from Gate 4", "Table 12, Order 48", "Lakers 98 Celtics 101", "Seat 14 - 22 Row B", "ETA 12 - 15 min", "Lakers 98, Celtics 101 and a very long tail of words that goes on well past any score line at all"))
            assertNull(title, score(title))
        assertNull(score("Now playing", "Bohemian Rhapsody - Queen"))
    }

    @Test fun onlyAnOngoingNotificationThatIsNotAConversationOrATransferIsLookedAt() {
        assertTrue(SportsLogic.mayBeScore(null, ongoing = true, progressMax = 0))
        assertTrue(SportsLogic.mayBeScore("status", ongoing = true, progressMax = 0))
        assertTrue(SportsLogic.mayBeScore("event", ongoing = true, progressMax = 0))
        assertFalse("one that can be swiped away", SportsLogic.mayBeScore(null, ongoing = false, progressMax = 0))
        assertFalse("a transfer", SportsLogic.mayBeScore(null, ongoing = true, progressMax = 100))
        for (category in listOf("msg", "email", "social", "call", "navigation", "transport", "alarm", "progress"))
            assertFalse(category, SportsLogic.mayBeScore(category, ongoing = true, progressMax = 0))
    }

    @Test fun namesAreShortenedForAScoreboard() {
        assertEquals("LAL", SportsLogic.short("LAL")); assertEquals("ARS", SportsLogic.short("Arsenal"))
        assertEquals("RM", SportsLogic.short("Real Madrid")); assertEquals("FB", SportsLogic.short("FC Barcelona"))
        assertEquals("49ER", SportsLogic.short("49er")); assertEquals("49E", SportsLogic.short("49ers"))
    }

    @Test fun thePillShowsTheTwoScores() {
        val score = score("Lakers 98 @ Celtics 101 · Q3 4:21")!!
        val update = LiveUpdate("k", "com.sports", "Sports", "Lakers 98 @ Celtics 101 · Q3 4:21", null, null, 0, 0, false, null, false, null, 0, 0L, null,
            source = ActivitySource.SPORTS, score = score)
        assertEquals("LAK 98–101 CEL", LiveUpdateLogic.glance(update, 0L))
        // Names too long to sit beside it leave the two scores.
        val long = score.copy(left = score.left.copy(name = "Internazionale Milano Primavera"), right = score.right.copy(name = "Borussia Monchengladbach II"))
        assertEquals("IMP 98–101 BMI", long.glance)
        assertEquals("1000–1000", long.copy(left = long.left.copy(score = 1000), right = long.right.copy(score = 1000)).glance)
        // A team's mark is the same colour every time, and two teams are seldom alike.
        assertEquals(SportsLogic.hue("Lakers"), SportsLogic.hue(" lakers "), 0f)
        assertTrue(SportsLogic.hue("Lakers") != SportsLogic.hue("Celtics")); assertTrue(SportsLogic.hue("Arsenal") in 0f..360f)
        // Whatever the two teams, their marks are far enough apart in colour to tell the sides by.
        for (pair in listOf("Arsenal" to "Chelsea", "Lakers" to "Celtics", "AAA" to "AAA", "India" to "Australia", "Real Madrid" to "FC Barcelona")) {
            val s = SportsLogic.parse("${pair.first} 1 - 0 ${pair.second}", null)!!
            val apart = kotlin.math.abs(s.leftHue - s.rightHue).let { if (it > 180f) 360f - it else it }
            assertTrue("$pair: $apart", apart >= SportsLogic.MARKS_APART)
        }
        // Without a score an activity's glance is what it was.
        assertEquals("Lakers 98 @ Ce", LiveUpdateLogic.glance(update.copy(score = null), 0L))
    }
}
