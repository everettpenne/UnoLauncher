package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class PageIndicatorLayoutTest {
    @Test fun reserveCoversMarginCapsuleAndAGapOnTheEightDpGrid() {
        assertEquals(48f, PageIndicatorLayout.reserveDp(isDefaultHome = true), 0.001f)
        assertEquals(96f, PageIndicatorLayout.reserveDp(isDefaultHome = false), 0.001f)
        listOf(true, false).forEach { assertEquals(0f, PageIndicatorLayout.reserveDp(it) % 8f, 0.001f) }
    }

    @Test fun contentAlwaysClearsTheTopOfTheStripByAtLeastTheSiblingGap() {
        val stripTop = PageIndicatorLayout.BOTTOM_MARGIN_DP + PageIndicatorLayout.CAPSULE_HEIGHT_DP
        assertTrue(PageIndicatorLayout.reserveDp(true) - stripTop >= 8f)
    }
}
