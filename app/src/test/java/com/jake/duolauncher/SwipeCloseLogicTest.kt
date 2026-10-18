package com.jake.duolauncher

import com.jake.duolauncher.island.SwipeCloseLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeCloseLogicTest {
    private val slop = 8f

    @Test fun aSlowDeliberateSwipePastTheDistanceCloses() {
        assertEquals(SwipeCloseLogic.Outcome.CLOSE, SwipeCloseLogic.decide(40f, 0f, slop))
        assertEquals("exactly at the distance", SwipeCloseLogic.Outcome.CLOSE, SwipeCloseLogic.decide(SwipeCloseLogic.CLOSE_DP, 0f, slop))
    }

    @Test fun aFastFlickClosesBeforeTheDistance() {
        assertEquals(SwipeCloseLogic.Outcome.CLOSE, SwipeCloseLogic.decide(15f, 1_000f, slop))
        assertEquals("exactly at the speed", SwipeCloseLogic.Outcome.CLOSE, SwipeCloseLogic.decide(15f, SwipeCloseLogic.CLOSE_VELOCITY_PX_S, slop))
    }

    @Test fun aTinyHoldWithinTheSlopNeverCloses() {
        assertEquals(SwipeCloseLogic.Outcome.STAY, SwipeCloseLogic.decide(0f, 0f, slop))
        assertEquals(SwipeCloseLogic.Outcome.STAY, SwipeCloseLogic.decide(slop, 0f, slop))
        assertEquals(SwipeCloseLogic.Outcome.STAY, SwipeCloseLogic.decide(4f, 0f, slop))
        // A fast finger that has not left the slop is still a tap/hold, not a close.
        assertEquals(SwipeCloseLogic.Outcome.STAY, SwipeCloseLogic.decide(4f, 2_000f, slop))
    }

    @Test fun aShortSlowSwipeSpringsBackOpen() {
        assertEquals(SwipeCloseLogic.Outcome.STAY, SwipeCloseLogic.decide(20f, 200f, slop))
        assertEquals(SwipeCloseLogic.Outcome.STAY, SwipeCloseLogic.decide(SwipeCloseLogic.CLOSE_DP - .1f, SwipeCloseLogic.CLOSE_VELOCITY_PX_S - 1f, slop))
    }

    @Test fun aDownwardDragNeverCloses() {
        assertEquals(SwipeCloseLogic.Outcome.STAY, SwipeCloseLogic.decide(-20f, -500f, slop))
        assertEquals(SwipeCloseLogic.Outcome.STAY, SwipeCloseLogic.decide(-60f, 0f, slop))
    }

    @Test fun theFractionFollowsTheFingerAndStaysInRange() {
        val travel = 150f
        assertEquals(1f, SwipeCloseLogic.fraction(0f, travel), 0f)
        assertEquals("half the travel is half closed", .5f, SwipeCloseLogic.fraction(travel / 2f, travel), .001f)
        assertEquals(0f, SwipeCloseLogic.fraction(travel, travel), 0f)
        assertEquals("never past closed", 0f, SwipeCloseLogic.fraction(travel * 2f, travel), 0f)
        assertEquals("never past open", 1f, SwipeCloseLogic.fraction(-travel, travel), 0f)
        // Grabbed mid-collapse, it continues from there instead of snapping to fully open.
        assertEquals(.6f, SwipeCloseLogic.fraction(0f, travel, from = .6f), .001f)
        assertTrue(SwipeCloseLogic.fraction(20f, travel, from = .6f) < .6f)
        assertEquals(0f, SwipeCloseLogic.fraction(1_000f, travel, from = .6f), 0f)
        // A degenerate travel never divides by zero.
        assertEquals(1f, SwipeCloseLogic.fraction(50f, 0f), 0f)
    }
}
