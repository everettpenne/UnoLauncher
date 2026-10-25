package com.jake.duolauncher

import com.jake.duolauncher.island.FlameShape
import org.junit.Assert.*
import org.junit.Test

class IslandFlameTest {
    @Test fun tonguesStandAcrossTheWidthAndStayWithinTheirBounds() {
        for (i in 0 until FlameShape.TONGUES) for (step in 0..400) {
            val (x, h) = FlameShape.tongue(i, step * .25f)
            val home = (i + .5f) / FlameShape.TONGUES
            assertTrue("tongue $i sways within its share of the width ($x vs $home)", Math.abs(x - home) <= .061f)
            assertTrue("tongue $i height $h flickers but never reaches the top", h in .12f..0.88f)
        }
    }

    @Test fun theyMoveOverTimeAndAreNotInLockstep() {
        val a = (0..40).map { FlameShape.tongue(0, it * .1f).second }
        assertTrue("it changes", a.max() - a.min() > .1f)
        val heights = (0 until FlameShape.TONGUES).map { FlameShape.tongue(it, 3f).second }
        assertTrue("neighbouring tongues differ", heights.toSet().size > 2)
        assertEquals("deterministic", FlameShape.tongue(2, 1.7f), FlameShape.tongue(2, 1.7f))
    }

    @Test fun theHiddenLineAppearsAfterSevenTapsOnly() {
        assertFalse(EasterEggs.revealed(0)); assertFalse(EasterEggs.revealed(EasterEggs.TAPS - 1))
        assertTrue(EasterEggs.revealed(EasterEggs.TAPS)); assertTrue(EasterEggs.revealed(20))
    }
}
