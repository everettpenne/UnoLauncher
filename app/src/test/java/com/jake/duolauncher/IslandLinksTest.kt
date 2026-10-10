package com.jake.duolauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandLinksTest {
    @Test fun everyAppIsLinkedUntilSwitchedOff() {
        assertTrue(IslandLinks.allowed("a.b", emptySet()))
        assertFalse(IslandLinks.allowed("a.b", setOf("a.b")))
        assertTrue(IslandLinks.allowed("a.c", setOf("a.b")))
    }

    @Test fun listRoundTripsAndToleratesJunk() {
        assertEquals(setOf("a.b", "c.d"), IslandLinks.parse(IslandLinks.serialize(setOf("c.d", "a.b"))))
        assertEquals(emptySet<String>(), IslandLinks.parse(null))
        assertEquals(setOf("x"), IslandLinks.parse(" x ,, "))
    }

    @Test fun toggleFlipsMembership() {
        val once = IslandLinks.toggle(emptySet(), "p")
        assertEquals(setOf("p"), once)
        assertEquals(emptySet<String>(), IslandLinks.toggle(once, "p"))
    }

    @Test fun positionAdvancesWhilePlayingOnly() {
        assertEquals(15_000L, MediaProgress.positionNow(10_000, 1_000, 1f, true, 6_000, 100_000))
        assertEquals(10_000L, MediaProgress.positionNow(10_000, 1_000, 1f, false, 6_000, 100_000))
        assertEquals(20_000L, MediaProgress.positionNow(10_000, 1_000, 2f, true, 6_000, 100_000))
    }

    @Test fun positionStaysInsideTheTrackAndNeedsADuration() {
        assertEquals(100_000L, MediaProgress.positionNow(99_000, 0, 1f, true, 50_000, 100_000))
        assertNull(MediaProgress.positionNow(5_000, 0, 1f, true, 0, 0))
        assertNull(MediaProgress.positionNow(-1, 0, 1f, true, 0, 100_000))
        assertEquals(.25f, MediaProgress.fraction(25_000, 0, 1f, false, 0, 100_000)!!, 1e-6f)
    }

    @Test fun seekTargetFollowsTheTouchAndClamps() {
        assertEquals(50_000L, MediaProgress.seekTarget(100f, 200f, 100_000))
        assertEquals(0L, MediaProgress.seekTarget(-20f, 200f, 100_000))
        assertEquals(100_000L, MediaProgress.seekTarget(900f, 200f, 100_000))
        assertEquals(0L, MediaProgress.seekTarget(10f, 0f, 100_000))
    }
}
