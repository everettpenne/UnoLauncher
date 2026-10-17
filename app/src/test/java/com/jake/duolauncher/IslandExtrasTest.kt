package com.jake.duolauncher

import com.jake.duolauncher.island.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class IslandExtrasTest {
    @Test fun aReplyComesFirstAndAtMostThreeActionsShow() {
        val picked = PeekActionRules.pick(listOf(
            RawAction("Mark as read", true, false), RawAction("Archive", true, false), RawAction("Reply", true, true), RawAction("Mute", true, false)))
        assertEquals(listOf(2, 0, 1), picked)
    }

    @Test fun blankTitledUnsendableContextualAndRepeatedActionsAreLeftOut() {
        val picked = PeekActionRules.pick(listOf(
            RawAction("  ", true, false), RawAction("Open", false, false), RawAction("Suggested", true, false, contextual = true),
            RawAction("Archive", true, false), RawAction("archive", true, false), RawAction(null, true, false)))
        assertEquals(listOf(3), picked)
    }

    @Test fun anActionLabelIsTrimmedAndKeptShort() {
        assertEquals("Reply", PeekActionRules.label("  Reply "))
        assertEquals(PeekActionRules.MAX_TITLE, PeekActionRules.label("Mark every message in this chat as read").length)
    }

    @Test fun aBluetoothDeviceIsNamedOrCalledADevice() {
        assertEquals("Pixel Watch connected", BluetoothEvents.event(true, " Pixel Watch ").title)
        assertEquals("Bluetooth device disconnected", BluetoothEvents.event(false, null).title)
        assertEquals(IslandSymbol.BLUETOOTH, BluetoothEvents.event(true, "x").symbol)
        assertEquals(24, BluetoothEvents.label("A very long device name that goes on and on").length)
    }

    @Test fun audioDevicesAreNotAnnouncedTwice() {
        assertTrue(BluetoothEvents.shouldShow(0x0100, audioAlertsOn = true))
        assertTrue(!BluetoothEvents.shouldShow(BluetoothEvents.MAJOR_AUDIO_VIDEO, audioAlertsOn = true))
        assertTrue(BluetoothEvents.shouldShow(BluetoothEvents.MAJOR_AUDIO_VIDEO, audioAlertsOn = false))
    }

    @Test fun theSameDeviceIsNotAnnouncedAgainWithinAFewSeconds() {
        assertTrue(BluetoothEvents.isRepeat(1_000L, 3_000L))
        assertTrue(!BluetoothEvents.isRepeat(1_000L, 6_000L))
        assertTrue(!BluetoothEvents.isRepeat(null, 6_000L))
    }

    @Test fun thePrivacyLineSaysWhatIsInUseForHowLongAndWhatWasInFront() {
        val line = PrivacyLine.describe(camera = true, mic = true, recording = false, sinceMs = 1_000L, nowMs = 43_500L, inFront = "Signal")!!
        assertEquals("Camera and Microphone in use", line.title)
        assertEquals("For 0:42 · Started with Signal in front", line.subtitle)
        assertEquals("Microphone in use", PrivacyLine.describe(false, true, false, 0L, 5_000L, null)!!.title)
        assertEquals("", PrivacyLine.describe(false, true, false, 0L, 5_000L, null)!!.subtitle)
        assertEquals("Screen is being recorded", PrivacyLine.describe(false, false, true, 1_000L, 2_000L, null)!!.title)
        assertEquals("Camera in use, screen recorded", PrivacyLine.describe(true, false, true, 1_000L, 2_000L, null)!!.title)
        assertNull(PrivacyLine.describe(false, false, false, 0L, 1L, null))
    }

    @Test fun theAppInFrontIsTheLastOneResumedAndNotPausedSince() {
        val r = ForegroundApp.RESUMED; val p = ForegroundApp.PAUSED
        assertEquals("b", ForegroundApp.current(listOf("a" to r, "a" to p, "b" to r), "uno"))
        assertNull("paused with nothing resumed after: Home is in front", ForegroundApp.current(listOf("a" to r, "a" to p), "uno"))
        assertEquals("a", ForegroundApp.current(listOf("a" to r, "b" to r, "b" to p, "a" to r), "uno"))
        assertNull("Uno itself is not named", ForegroundApp.current(listOf("uno" to r), "uno"))
        assertNull(ForegroundApp.current(emptyList(), "uno"))
    }

    @Test fun aMetaballNeckJoinsCirclesThatAreNearAndNotOnesThatAreFar() {
        val c1 = Metaball.Pt(0f, 0f); val c2 = Metaball.Pt(35f, 0f)
        val neck = Metaball.neck(c1, 18f, c2, 15f)
        assertNotNull(neck)
        // The neck starts on the first circle's edge and ends on the second's.
        assertEquals(18f, hypot(neck!!.p1a.x - c1.x, neck.p1a.y - c1.y), .01f)
        assertEquals(18f, hypot(neck.p1b.x - c1.x, neck.p1b.y - c1.y), .01f)
        assertEquals(15f, hypot(neck.p2a.x - c2.x, neck.p2a.y - c2.y), .01f)
        assertEquals(15f, hypot(neck.p2b.x - c2.x, neck.p2b.y - c2.y), .01f)
        // The two sides of the neck are on opposite sides of the line between the centres.
        assertTrue(neck.p1a.y * neck.p1b.y < 0f)
        assertNull("too far", Metaball.neck(c1, 18f, Metaball.Pt(200f, 0f), 15f))
        assertNull("one inside the other", Metaball.neck(c1, 18f, Metaball.Pt(1f, 0f), 15f))
        assertNull("empty", Metaball.neck(c1, 0f, c2, 15f))
        // With no reach left, circles that only just fail to touch are no longer joined (a bubble that has settled).
        assertNull(Metaball.neck(c1, 18f, Metaball.Pt(36f, 0f), 15f, reach = 0f))
    }

    @Test fun theMorphSpeedIsSmoothedForgetsGapsAndIsZeroOnceSettled() {
        val m = MorphVelocity()
        val ms = 1_000_000L
        assertEquals("the first frame has no speed", 0f, m.update(0f, 100 * ms, 1f), 0f)
        val v1 = m.update(.05f, 116 * ms, 1f)
        assertTrue("moving up is positive: $v1", v1 > 0f)
        val v2 = m.update(.12f, 132 * ms, 1f)
        assertTrue("and speeds up as the frames do: $v2 > $v1", v2 > v1)
        assertEquals("a pause in the frames is forgotten", 0f, m.update(.3f, 900 * ms, 1f), 0f)
        assertEquals("settled on its target is still", 0f, m.update(1f, 916 * ms, 1f), 0f)
        // Closing is negative, and the stretch follows the sign, is capped, and is scaled by the theme.
        val c = MorphVelocity(); c.update(1f, 0L + ms, 0f)
        assertTrue(c.update(.9f, 17 * ms, 0f) < 0f)
        assertEquals(.06f, MorphVelocity.stretch(100f, 1f), 0f)
        assertEquals(-.06f, MorphVelocity.stretch(-100f, 1f), 0f)
        assertEquals(0f, MorphVelocity.stretch(5f, 0f), 0f)
    }

    @Test fun pullingTheIslandDownReachesThePeekThenTheWholePanel() {
        val d = 2.625f; val peek = .5f
        assertEquals(0f, PeekPull.fraction(0f, d, peek), 0f)
        assertEquals("half way to the peek", peek / 2f, PeekPull.fraction(PeekPull.TO_PEEK_DP / 2f * d, d, peek), .001f)
        assertEquals("at the peek", peek, PeekPull.fraction(PeekPull.TO_PEEK_DP * d, d, peek), .001f)
        assertEquals("all the way", 1f, PeekPull.fraction(PeekPull.TO_FULL_DP * d, d, peek), .001f)
        assertEquals("never past it", 1f, PeekPull.fraction(1000f * d, d, peek), 0f)
        assertEquals("pulling up never goes below nothing", 0f, PeekPull.fraction(-50f * d, d, peek), 0f)
        // On a peek, pulling down takes it on, and pulling up takes it back.
        assertTrue(PeekPull.fraction(30f * d, d, peek, from = peek) > peek)
        assertTrue(PeekPull.fraction(-30f * d, d, peek, from = peek) < peek)
    }

    @Test fun aGrabMidCollapseContinuesFromWhereTheIslandIs() {
        val d = 2.625f; val peek = .5f
        // The island is part-way collapsed (0.6 open) when the finger lands. With the pull anchored there,
        // a zero-movement frame keeps it at 0.6 instead of snapping to the collapsed start.
        assertEquals(.6f, PeekPull.fraction(0f, d, peek, from = .6f), .001f)
        // Pulling down re-opens it further, and pulling up closes it, both from the same place.
        assertTrue(PeekPull.fraction(20f * d, d, peek, from = .6f) > .6f)
        assertTrue(PeekPull.fraction(-20f * d, d, peek, from = .6f) < .6f)
        // It never leaves 0..1 even when grabbed from a mid value.
        assertEquals(0f, PeekPull.fraction(-1000f * d, d, peek, from = .6f), 0f)
        assertEquals(1f, PeekPull.fraction(1000f * d, d, peek, from = .6f), 0f)
    }

    @Test fun lettingGoDecidesBetweenDroppingBackPeekingAndOpening() {
        val peek = .5f
        assertEquals(PeekPull.Outcome.COLLAPSE, PeekPull.outcome(.02f, peek))
        assertEquals(PeekPull.Outcome.PEEK, PeekPull.outcome(.3f, peek))
        assertEquals(PeekPull.Outcome.PEEK, PeekPull.outcome(peek, peek))
        assertEquals(PeekPull.Outcome.OPEN, PeekPull.outcome(.95f, peek))
        // A pull that Android cuts short at the edge of the status bar (about 35 dp) still lands in the peek, not in the bin.
        val cut = PeekPull.fraction(35f * 2.625f, 2.625f, peek)
        assertEquals(PeekPull.Outcome.PEEK, PeekPull.outcome(cut, peek))
    }

    @Test fun onlyTheBottomStripHoldsTheIslandOpen() {
        val d = 2.625f; val h = 600f
        assertTrue(HoldOpen.inZone(h - 10f, h, d))
        assertTrue(HoldOpen.inZone(h - HoldOpen.ZONE_DP * d, h, d))
        assertTrue(!HoldOpen.inZone(h - HoldOpen.ZONE_DP * d - 5f, h, d))
        assertTrue(!HoldOpen.inZone(20f, h, d))
        assertTrue(!HoldOpen.inZone(h + 5f, h, d))
        assertTrue(!HoldOpen.inZone(10f, 0f, d))
    }

    @Test fun aNotificationStaysAvailableForThirtySeconds() {
        assertTrue(RecentNotice.live(1_000L, 1_000L))
        assertTrue(RecentNotice.live(1_000L, 30_999L))
        assertTrue(!RecentNotice.live(1_000L, 31_000L))
        assertTrue(!RecentNotice.live(0L, 5_000L))
        assertTrue("a clock that went backwards is not live", !RecentNotice.live(10_000L, 5_000L))
        assertEquals(30, RecentNotice.secondsLeft(1_000L, 1_000L))
        assertEquals(1, RecentNotice.secondsLeft(1_000L, 30_500L))
        assertEquals(0, RecentNotice.secondsLeft(1_000L, 40_000L))
    }

    @Test fun navigationIsSetFarLargerThanTheIslandsOtherText() {
        // The distance and the street were 20 sp and 12 sp, with a 46 dp arrow, on the open island.
        assertTrue(NavigationText.DISTANCE_SP >= 32)
        assertTrue(NavigationText.INSTRUCTION_SP >= 18)
        assertTrue(NavigationText.ARROW_DP >= 64f)
        assertTrue("the arrow is read at more than twice the pixels of the old 96 px bitmap", NavigationText.ARROW_PX >= 192)
        assertTrue("and the pill's distance is not the 12 sp of the other slots", NavigationText.PILL_SP >= 14)
        assertTrue(NavigationText.CARD_ROW_DP > UPDATE_ROW_DP)
    }
}
