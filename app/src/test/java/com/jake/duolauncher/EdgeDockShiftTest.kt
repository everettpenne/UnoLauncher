package com.jake.duolauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeDockShiftTest {
    private val width = 1000f

    @Test fun `home to home stays pinned`() {
        // Three Home pages: feed = -1, Home = 0..2, All apps = 3. The interior span never moves.
        assertEquals(0f, EdgeDockShift.shiftPx(0f, 3, width), 0f)
        assertEquals(0f, EdgeDockShift.shiftPx(.5f, 3, width), 0f)
        assertEquals(0f, EdgeDockShift.shiftPx(1f, 3, width), 0f)
        assertEquals(0f, EdgeDockShift.shiftPx(1.999f, 3, width), 0f)
    }

    @Test fun `toward the feed the dock rides right with home zero`() {
        assertEquals(width, EdgeDockShift.shiftPx(-1f, 3, width), .001f)
        assertEquals(width * .5f, EdgeDockShift.shiftPx(-.5f, 3, width), .001f)
        assertEquals(0f, EdgeDockShift.shiftPx(0f, 3, width), 0f)
    }

    @Test fun `toward the app list the dock rides left with the last home`() {
        assertEquals(0f, EdgeDockShift.shiftPx(2f, 3, width), 0f)
        assertEquals(-width * .5f, EdgeDockShift.shiftPx(2.5f, 3, width), .001f)
        assertEquals(-width, EdgeDockShift.shiftPx(3f, 3, width), .001f)
    }

    @Test fun `a single home page has no interior span`() {
        // Home 0 is both the first and last Home page: the whole range is the two edges.
        assertEquals(width, EdgeDockShift.shiftPx(-1f, 1, width), .001f)
        assertEquals(0f, EdgeDockShift.shiftPx(0f, 1, width), 0f)
        assertEquals(-width, EdgeDockShift.shiftPx(1f, 1, width), .001f)
    }

    @Test fun `the two edges mirror each other`() {
        // The slide out of the span toward either edge has the same magnitude, opposite sign.
        assertEquals(
            EdgeDockShift.shiftPx(-.75f, 3, width),
            -EdgeDockShift.shiftPx(2.75f, 3, width),
            .001f,
        )
    }

    @Test fun `the shift is proportional to the finger`() {
        // Every step toward the feed moves the dock the same distance the pager moved.
        for (step in 0..8) {
            val position = -step / 8f
            assertEquals(-position * width, EdgeDockShift.shiftPx(position, 2, width), .001f)
        }
    }

    @Test fun `degenerate inputs are safe`() {
        assertEquals(0f, EdgeDockShift.shiftPx(-1f, 0, width), 0f)
        assertEquals(0f, EdgeDockShift.shiftPx(0f, 3, 0f), 0f)
    }
}
