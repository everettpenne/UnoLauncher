package com.jake.duolauncher

import com.jake.duolauncher.appearance.SheetMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetMotionTest {
    @Test fun `a decisive downward flick dismisses but a lazy scroll does not`() {
        assertTrue(SheetMotion.flingDismisses(SheetMotion.FLING_DISMISS_VELOCITY_DP))
        assertTrue(SheetMotion.flingDismisses(SheetMotion.FLING_DISMISS_VELOCITY_DP + 1f))
        assertFalse(SheetMotion.flingDismisses(SheetMotion.FLING_DISMISS_VELOCITY_DP - 1f))
        assertFalse(SheetMotion.flingDismisses(0f))
    }

    @Test fun `upward over-drag resists with diminishing returns and a cap`() {
        val max = 100f
        val first = SheetMotion.resistUpwardDrag(100f, max)
        val second = SheetMotion.resistUpwardDrag(200f, max)
        assertTrue(second > first)
        assertTrue(SheetMotion.resistUpwardDrag(10_000f, max) < max)
        assertEquals(0f, SheetMotion.resistUpwardDrag(0f, max), 0f)
        assertEquals(0f, SheetMotion.resistUpwardDrag(50f, 0f), 0f)
    }

    @Test fun `upward over-drag carries the drag's sign`() {
        assertEquals(-SheetMotion.resistUpwardDrag(50f, 100f), SheetMotion.resistUpwardDrag(-50f, 100f), 0.001f)
    }
}
