package com.jake.duolauncher

import com.jake.duolauncher.island.IslandDrag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeCloseLogicTest {
    private val slop = 8f

    @Test fun aSlowDeliberateSwipePastTheDistanceCloses() {
        assertEquals(IslandDrag.CloseOutcome.CLOSE, IslandDrag.closeDecide(40f, 0f, slop))
        assertEquals("exactly at the distance", IslandDrag.CloseOutcome.CLOSE, IslandDrag.closeDecide(IslandDrag.CLOSE_DP, 0f, slop))
    }

    @Test fun aFastFlickClosesBeforeTheDistance() {
        assertEquals(IslandDrag.CloseOutcome.CLOSE, IslandDrag.closeDecide(15f, 1_000f, slop))
        assertEquals("exactly at the speed", IslandDrag.CloseOutcome.CLOSE, IslandDrag.closeDecide(15f, IslandDrag.CLOSE_VELOCITY_PX_S, slop))
    }

    @Test fun aTinyHoldWithinTheSlopNeverCloses() {
        assertEquals(IslandDrag.CloseOutcome.STAY, IslandDrag.closeDecide(0f, 0f, slop))
        assertEquals(IslandDrag.CloseOutcome.STAY, IslandDrag.closeDecide(slop, 0f, slop))
        assertEquals(IslandDrag.CloseOutcome.STAY, IslandDrag.closeDecide(4f, 0f, slop))
        // A fast finger that has not left the slop is still a tap/hold, not a close.
        assertEquals(IslandDrag.CloseOutcome.STAY, IslandDrag.closeDecide(4f, 2_000f, slop))
    }

    @Test fun aShortSlowSwipeSpringsBackOpen() {
        assertEquals(IslandDrag.CloseOutcome.STAY, IslandDrag.closeDecide(20f, 200f, slop))
        assertEquals(IslandDrag.CloseOutcome.STAY, IslandDrag.closeDecide(IslandDrag.CLOSE_DP - .1f, IslandDrag.CLOSE_VELOCITY_PX_S - 1f, slop))
    }

    @Test fun aDownwardDragNeverCloses() {
        assertEquals(IslandDrag.CloseOutcome.STAY, IslandDrag.closeDecide(-20f, -500f, slop))
        assertEquals(IslandDrag.CloseOutcome.STAY, IslandDrag.closeDecide(-60f, 0f, slop))
    }

    @Test fun theFractionFollowsTheFingerAndStaysInRange() {
        val travel = 150f
        assertEquals(1f, IslandDrag.closeFraction(0f, travel), 0f)
        assertEquals("half the travel is half closed", .5f, IslandDrag.closeFraction(travel / 2f, travel), .001f)
        assertEquals(0f, IslandDrag.closeFraction(travel, travel), 0f)
        assertEquals("never past closed", 0f, IslandDrag.closeFraction(travel * 2f, travel), 0f)
        assertEquals("never past open", 1f, IslandDrag.closeFraction(-travel, travel), 0f)
        // Grabbed mid-collapse, it continues from there instead of snapping to fully open.
        assertEquals(.6f, IslandDrag.closeFraction(0f, travel, from = .6f), .001f)
        assertTrue(IslandDrag.closeFraction(20f, travel, from = .6f) < .6f)
        assertEquals(0f, IslandDrag.closeFraction(1_000f, travel, from = .6f), 0f)
        // A degenerate travel never divides by zero.
        assertEquals(1f, IslandDrag.closeFraction(50f, 0f), 0f)
    }
}
