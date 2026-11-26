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
        assertEquals(HyperGlassCards.SPLIT, HyperGlassCards.split(0f), 0f)
        assertEquals(HyperGlassCards.SPLIT_KICKED, HyperGlassCards.split(1f), 1e-7f)
        assertEquals(HyperGlassCards.SPLIT_KICKED, HyperGlassCards.split(4f), 1e-7f)
        assertTrue(HyperGlassCards.fringeDp(1f) > HyperGlassCards.fringeDp(.5f) && HyperGlassCards.fringeDp(.5f) > HyperGlassCards.fringeDp(0f))
    }

    @Test fun theCardStaysNearlyBare() {
        assertTrue(HyperGlassCards.FILL in .06f..(.10f))
        assertTrue(HyperGlassCards.SHADE_SHARE < .5f)
    }
}
