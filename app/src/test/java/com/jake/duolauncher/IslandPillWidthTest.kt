package com.jake.duolauncher

import com.jake.duolauncher.island.IslandPillWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandPillWidthTest {
    private val hole = 15.8f; private val rest = 51.4f; private val screen = 205.7f

    @Test fun aCountingClockIsMeasuredAtItsWidestSoThePillDoesNotTwitch() {
        assertEquals("8:88", IslandPillWidth.template("9:59"))
        assertEquals(IslandPillWidth.template("1:11"), IslandPillWidth.template("9:59"))
        assertEquals("88:88", IslandPillWidth.template("10:00"))
        assertEquals("Arriving", IslandPillWidth.template("Arriving"))
    }

    @Test fun theWiderSideSetsBothSoThePillStaysSymmetric() {
        val shortText = IslandPillWidth.halfDp(hole, leadDp = 20f, trailDp = 24f, endInsetDp = 6f, minHalfDp = rest, screenHalfDp = screen)
        val longText = IslandPillWidth.halfDp(hole, leadDp = 20f, trailDp = 60f, endInsetDp = 6f, minHalfDp = rest, screenHalfDp = screen)
        assertTrue("a longer detail widens it: $shortText -> $longText", longText > shortText)
        assertEquals(longText, IslandPillWidth.halfDp(hole, 60f, 20f, 6f, rest, screen), 0f)
    }

    @Test fun itGrowsInStepsNeverBelowTheRestingPillNorPastTheScreen() {
        val a = IslandPillWidth.halfDp(hole, 20f, 38f, 6f, rest, screen)
        val b = IslandPillWidth.halfDp(hole, 20f, 39f, 6f, rest, screen)
        assertEquals("a 1 dp longer text stays in the same step", a, b, 0f)
        assertEquals(0f, a % IslandPillWidth.STEP_DP, 0f)
        assertEquals(rest, IslandPillWidth.halfDp(hole, 0f, 0f, 0f, rest, screen), 0f)
        assertEquals(screen - IslandPillWidth.SCREEN_MARGIN_DP, IslandPillWidth.halfDp(hole, 20f, 400f, 6f, rest, screen), 0f)
    }
}
