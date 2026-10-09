package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class IslandLiveTest {
    private fun kinds(camera: Boolean = false, mic: Boolean = false, call: Boolean = false, timer: Boolean = false,
        stopwatch: Boolean = false, media: Boolean = false) = IslandLive.active(camera, mic, call, timer, stopwatch, media)

    @Test fun nothingLiveMeansNothingInEitherSlot() {
        val plan = IslandLive.plan(kinds())
        assertNull(plan.leading); assertNull(plan.trailing)
    }

    @Test fun oneActivityShowsItsGlyphOnOneSideAndItsDetailOnTheOther() {
        val plan = IslandLive.plan(kinds(timer = true))
        assertEquals(LiveKind.TIMER to SlotShow.GLYPH, plan.leading)
        assertEquals(LiveKind.TIMER to SlotShow.DETAIL, plan.trailing)
    }

    @Test fun twoActivitiesGetOneSideEach() {
        // A timer and music: the music bars on the left, the countdown in full on the right.
        val plan = IslandLive.plan(kinds(timer = true, media = true))
        assertEquals(LiveKind.MEDIA to SlotShow.GLYPH, plan.leading)
        assertEquals(LiveKind.TIMER to SlotShow.DETAIL, plan.trailing)
    }

    @Test fun priorityRunsPrivacyThenCallThenTimerThenStopwatchThenMedia() {
        assertEquals(listOf(LiveKind.CAMERA, LiveKind.MIC, LiveKind.CALL, LiveKind.TIMER, LiveKind.STOPWATCH, LiveKind.MEDIA),
            kinds(true, true, true, true, true, true))
    }

    @Test fun aPrivacyIndicatorIsAlwaysAGlyphNeverAParagraph() {
        val both = IslandLive.plan(kinds(camera = true, mic = true))
        assertEquals(LiveKind.MIC to SlotShow.GLYPH, both.leading)
        assertEquals(LiveKind.CAMERA to SlotShow.GLYPH, both.trailing)
        val withMusic = IslandLive.plan(kinds(mic = true, media = true))
        assertEquals(LiveKind.MIC to SlotShow.GLYPH, withMusic.trailing)
    }

    @Test fun aFlickTurnsTheListSoADifferentActivityIsInFront() {
        val list = kinds(call = true, timer = true, media = true)
        assertEquals(list, IslandLive.rotated(list, 0))
        assertEquals(listOf(LiveKind.TIMER, LiveKind.MEDIA, LiveKind.CALL), IslandLive.rotated(list, 1))
        assertEquals(list, IslandLive.rotated(list, 3))
        assertEquals(IslandLive.rotated(list, 2), IslandLive.rotated(list, -1))
        assertEquals(kinds(timer = true), IslandLive.rotated(kinds(timer = true), 5))
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
