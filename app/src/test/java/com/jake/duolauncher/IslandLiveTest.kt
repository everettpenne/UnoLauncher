package com.jake.duolauncher

import com.jake.duolauncher.island.*

import org.junit.Assert.*
import org.junit.Test

class IslandLiveTest {
    private fun kinds(call: Boolean = false, timer: Boolean = false, stopwatch: Boolean = false, media: Boolean = false) =
        IslandLive.active(call, timer, stopwatch, media)

    @Test fun nothingLiveMeansNothingInEitherSlot() {
        val plan = IslandLive.plan(kinds())
        assertNull(plan.leading); assertNull(plan.trailing)
    }

    @Test fun oneActivityIsTheLeadingWithNoTrailingCapsule() {
        // One activity fills the pill alone: its glyph one side, its detail the other. No detached capsule.
        val plan = IslandLive.plan(kinds(timer = true))
        assertEquals(LiveKind.TIMER, plan.leading)
        assertNull(plan.trailing)
    }

    @Test fun twoActivitiesPutTheFrontOneOnThePillAndTheSecondInItsOwnCapsule() {
        // A timer and music: the timer (most important) holds the pill, the music is the trailing detached capsule.
        val plan = IslandLive.plan(kinds(timer = true, media = true))
        assertEquals(LiveKind.TIMER, plan.leading)
        assertEquals(LiveKind.MEDIA, plan.trailing)
    }

    @Test fun priorityRunsCallThenTimerThenStopwatchThenMedia() {
        assertEquals(listOf(LiveKind.CALL, LiveKind.TIMER, LiveKind.STOPWATCH, LiveKind.MEDIA), kinds(true, true, true, true))
    }

    @Test fun privacyMarksAreDotsAndNeverTakeAPlaceOnThePill() {
        assertEquals(listOf(LiveKind.CAMERA, LiveKind.MIC, LiveKind.RECORDING), IslandLive.privacy(true, true, true))
        assertEquals(emptyList<LiveKind>(), IslandLive.privacy(false, false, false))
        assertTrue(IslandLive.privacy(true, true, true).all { it.privacy })
        assertTrue(IslandLive.active(true, true, true, true, true).none { it.privacy })
    }

    @Test fun aVideoCallKeepsItsTimerOnThePillWhateverThePrivacyMarksAre() {
        // Camera and microphone are dots, so the call holds the pill and the music is its detached capsule.
        val plan = IslandLive.plan(kinds(call = true, media = true))
        assertEquals(LiveKind.CALL, plan.leading)
        assertEquals(LiveKind.MEDIA, plan.trailing)
        assertEquals(2, IslandLive.privacy(true, true, false).size)
    }

    @Test fun theDotsHaveRoomOnlyWhenThereAreAny() {
        assertEquals(0f, IslandPrivacy.widthDp(0), 0f)
        assertTrue(IslandPrivacy.widthDp(3) > IslandPrivacy.widthDp(1))
    }

    @Test fun theMusicFadeIsStrongWhilePlayingFaintWhilePausedAndGoneWhenSomethingElseHasTheIsland() {
        assertEquals(IslandGlow.PLAYING, IslandGlow.target(true, true, false), 0f)
        assertEquals(IslandGlow.PAUSED, IslandGlow.target(true, false, false), 0f)
        assertEquals(0f, IslandGlow.target(false, true, false), 0f)
        assertEquals(0f, IslandGlow.target(true, true, true), 0f)
        assertTrue(IslandGlow.PLAYING > IslandGlow.PAUSED && IslandGlow.PAUSED > 0f)
    }

    @Test fun aFlickTurnsTheListSoADifferentActivityIsInFront() {
        val list = kinds(call = true, timer = true, media = true)
        assertEquals(list, IslandLive.rotated(list, 0))
        assertEquals(listOf(LiveKind.TIMER, LiveKind.MEDIA, LiveKind.CALL), IslandLive.rotated(list, 1))
        assertEquals(list, IslandLive.rotated(list, 3))
        assertEquals(IslandLive.rotated(list, 2), IslandLive.rotated(list, -1))
        assertEquals(kinds(timer = true), IslandLive.rotated(kinds(timer = true), 5))
    }

    // ---- compact Live Update text ----
    @Test fun compactTextLevelsChooseGlyphOnlyShortStatusOrTitle() {
        assertEquals(null, LiveCompactText.choose(0, "4 min", "Ride home"))
        assertEquals("4 min", LiveCompactText.choose(1, "4 min", "Ride home"))
        assertEquals("Ride home", LiveCompactText.choose(2, "4 min", "Ride home"))
    }

    @Test fun compactTextClampsOddLevelsToTheNearestValidOne() {
        assertEquals(null, LiveCompactText.choose(-1, "4 min", "Ride home"))
        assertEquals(null, LiveCompactText.choose(-9, "4 min", "Ride home"))
        assertEquals("Ride home", LiveCompactText.choose(9, "4 min", "Ride home"))
        assertEquals("Ride home", LiveCompactText.choose(3, "4 min", "Ride home"))
    }

    @Test fun compactTextKeepsTheChosenTextEvenWhenTheOtherIsBlank() {
        // Level 2 always shows the title, and level 1 the short status, whatever the other field holds.
        assertEquals(null, LiveCompactText.choose(0, null, ""))
        assertEquals(null, LiveCompactText.choose(1, null, "Ride home"))
        assertEquals("Ride home", LiveCompactText.choose(2, null, "Ride home"))
        assertEquals("", LiveCompactText.choose(2, "4 min", ""))
    }

    // ---- album-art tint ----
    private fun solid(argb: Int, n: Int = 64) = IntArray(n) { argb }
    private fun channels(c: Int) = Triple(c shr 16 and 0xFF, c shr 8 and 0xFF, c and 0xFF)

    @Test fun aRedCoverGivesARedTint() {
        val (r, g, b) = channels(ArtTint.fromPixels(solid(0xFFCC1122.toInt()))!!)
        assertTrue("red leads: $r $g $b", r > g + 60 && r > b + 60)
    }

    @Test fun aGreyOrBlackCoverGivesNoTint() {
        assertNull(ArtTint.fromPixels(solid(0xFF808080.toInt())))
        assertNull(ArtTint.fromPixels(solid(0xFF000000.toInt())))
        assertNull(ArtTint.fromPixels(solid(0xFFFFFFFF.toInt())))
    }

    @Test fun theMostCommonVividColourWinsOverAFewStrayPixels() {
        val pixels = IntArray(100) { if (it < 80) 0xFF1FA84A.toInt() else 0xFFE02020.toInt() }
        val (r, g, b) = channels(ArtTint.fromPixels(pixels)!!)
        assertTrue("green leads: $r $g $b", g > r + 40 && g > b + 40)
    }

    @Test fun aDarkCoverIsLiftedSoItStaysReadableOnBlack() {
        val (r, g, b) = channels(ArtTint.fromPixels(solid(0xFF300A40.toInt(), 400))!!)
        assertTrue("bright enough: ${maxOf(r, g, b)}", maxOf(r, g, b) >= 200)
    }
}

/** Every combination of the five activities, checked against the rules the island is built on, so no mix of them can clash. */
class IslandCombinationTest {
    private val all = listOf(LiveKind.CALL, LiveKind.TIMER, LiveKind.STOPWATCH, LiveKind.UPDATE, LiveKind.MEDIA)

    private fun subsets(): List<List<LiveKind>> = (0 until (1 shl all.size)).map { mask -> all.filterIndexed { i, _ -> mask and (1 shl i) != 0 } }

    @Test fun everyCombinationPlacesWhatItShowsOnceAndInPriorityOrder() {
        subsets().forEach { subset ->
            val kinds = IslandLive.active(LiveKind.CALL in subset, LiveKind.TIMER in subset, LiveKind.STOPWATCH in subset,
                LiveKind.MEDIA in subset, LiveKind.UPDATE in subset)
            assertEquals("order for $subset", subset, kinds)                         // priority order, nothing invented or lost
            val plan = IslandLive.plan(kinds)
            val bubbles = IslandBubbles.extras(kinds)
            if (kinds.isEmpty()) { assertNull(plan.leading); assertNull(plan.trailing); assertTrue(bubbles.isEmpty()); return@forEach }
            // The pill carries the leading, the trailing is its own detached capsule, then the extras.
            val shown = listOfNotNull(plan.leading, plan.trailing) + bubbles
            assertEquals("front of $subset", kinds[0], plan.leading)
            if (kinds.size >= 2) assertEquals("second of $subset", kinds[1], plan.trailing)
            // The pill, the trailing capsule and the extras never repeat an activity, and together they show the first four.
            assertTrue("no repeats in $subset", shown.distinct().size == shown.size)
            assertEquals("shown for $subset", kinds.take(4), shown)
            assertTrue("at most two extras", bubbles.size <= IslandBubbles.MAX)
        }
    }

    @Test fun onlyTheLowestPriorityActivityCanBeHiddenAndOnlyWhenAllFiveAreLive() {
        val every = IslandLive.active(true, true, true, true, true)
        val shown = listOf(every[0], every[1]) + IslandBubbles.extras(every)
        assertEquals(listOf(LiveKind.MEDIA), every - shown.toSet())
        assertEquals(emptyList<LiveKind>(), IslandLive.active(true, true, true, true, false).let { it - (listOf(it[0], it[1]) + IslandBubbles.extras(it)).toSet() })
    }

    @Test fun turningTheListNeverLosesAnActivity() {
        val kinds = IslandLive.active(true, true, true, true, true)
        (-7..7).forEach { shift -> assertEquals(kinds.toSet(), IslandLive.rotated(kinds, shift).toSet()) }
    }
}
