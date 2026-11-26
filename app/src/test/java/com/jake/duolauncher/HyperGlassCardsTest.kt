package com.jake.duolauncher

import com.jake.duolauncher.appearance.AppearanceState
import com.jake.duolauncher.appearance.HyperGlassCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HyperGlassCardsTest {
    @Test fun offUnlessChosen() { assertFalse(AppearanceState().hyperCards) }

    @Test fun theDriftLoopHasNoSeam() {
        val turn = 2f * Math.PI.toFloat()
        assertEquals(0f, HyperGlassCards.phaseAt(0), 1e-6f)
        assertEquals(turn / 2f, HyperGlassCards.phaseAt(HyperGlassCards.PERIOD_MS / 2), 1e-4f)
        // The step before the loop closes is as near to the start as any other step is to the one before it.
        val step = HyperGlassCards.phaseAt(HyperGlassCards.STEP_MS)
        assertEquals(turn - step, HyperGlassCards.phaseAt(HyperGlassCards.PERIOD_MS - HyperGlassCards.STEP_MS), 1e-4f)
        assertEquals(HyperGlassCards.phaseAt(123), HyperGlassCards.phaseAt(123 + 3 * HyperGlassCards.PERIOD_MS), 1e-4f)
    }

    @Test fun aKickPartsTheColoursAndComesBack() {
        assertEquals(HyperGlassCards.SPLIT_DP, HyperGlassCards.splitDp(0f), 0f)
        assertEquals(HyperGlassCards.SPLIT_KICKED_DP, HyperGlassCards.splitDp(1f), 1e-6f)
        assertEquals(HyperGlassCards.SPLIT_KICKED_DP, HyperGlassCards.splitDp(4f), 1e-6f)
        assertTrue(HyperGlassCards.splitDp(1f) > HyperGlassCards.splitDp(.5f) && HyperGlassCards.splitDp(.5f) > HyperGlassCards.splitDp(0f))
        // Enough to be seen (the first try was under a pixel), and a word must still read.
        assertTrue(HyperGlassCards.SPLIT_DP in .75f..2f)
    }

    @Test fun theCardStaysNearlyBare() {
        assertTrue(HyperGlassCards.FILL in .06f..(.10f))
        assertTrue(HyperGlassCards.SHADE_SHARE < .5f)
    }

    @Test fun theGrainDoesNotComeRoundAgainWithinAScreen() {
        fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
        assertEquals(1, gcd(HyperGlassCards.GRAIN_A, HyperGlassCards.GRAIN_B))
        assertTrue(HyperGlassCards.GRAIN_A * HyperGlassCards.GRAIN_B > 4000)
        assertTrue(HyperGlassCards.GRAIN_TURN % 90f != 0f)
    }
}
