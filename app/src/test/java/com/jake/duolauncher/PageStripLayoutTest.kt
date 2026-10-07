package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class PageStripLayoutTest {
    @Test fun itemsAreCompassDotsAndLibraryInOrder() {
        assertEquals(listOf(32f, 28f, 28f, 28f, 32f), PageStripLayout.widths(showCompass = true, dots = 3))
        assertEquals(listOf(28f, 28f, 28f, 32f), PageStripLayout.widths(showCompass = false, dots = 3))
    }

    @Test fun centresAreCumulativeMidpoints() {
        val widths = PageStripLayout.widths(true, 3)
        assertEquals(listOf(16f, 46f, 74f, 102f, 132f), PageStripLayout.centers(widths))
    }

    @Test fun aTouchLandsOnTheItemUnderIt() {
        val widths = PageStripLayout.widths(true, 3) // edges at 32, 60, 88, 116, 148
        assertEquals(0, PageStripLayout.itemAt(0f, widths))
        assertEquals(0, PageStripLayout.itemAt(31.9f, widths))
        assertEquals(1, PageStripLayout.itemAt(32f, widths))
        assertEquals(3, PageStripLayout.itemAt(100f, widths))
        assertEquals(4, PageStripLayout.itemAt(147f, widths))
        assertEquals("past the end clamps", 4, PageStripLayout.itemAt(500f, widths))
        assertEquals("before the start clamps", 0, PageStripLayout.itemAt(-20f, widths))
    }

    @Test fun pagesAndItemsRoundTripWithAndWithoutTheCompass() {
        listOf(true, false).forEach { compass ->
            val widths = PageStripLayout.widths(compass, 4)
            widths.indices.forEach { item ->
                val page = PageStripLayout.pageFor(item, compass)
                assertEquals("item $item compass=$compass", item, PageStripLayout.itemFor(page, compass, 4))
            }
        }
        assertEquals("compass selects Discover", -1, PageStripLayout.pageFor(0, true))
        assertEquals("first dot is Home 1", 0, PageStripLayout.pageFor(1, true))
        assertEquals("library is the page after the last dot", 4, PageStripLayout.pageFor(5, true))
        assertNull("no compass item while hidden", PageStripLayout.itemFor(-1, false, 4))
    }

    @Test fun lensSitsOnTheItemAtWholePagesAndGlidesBetween() {
        val widths = PageStripLayout.widths(true, 3)
        val centers = PageStripLayout.centers(widths)
        // Discover (-1), Home 1..3 (0..2), All apps (3).
        listOf(-1f, 0f, 1f, 2f, 3f).forEachIndexed { index, page ->
            assertEquals(centers[index], PageStripLayout.centerAt(page, widths, true), 0.001f)
        }
        assertEquals("halfway between Home 1 and Home 2",
            (centers[1] + centers[2]) / 2f, PageStripLayout.centerAt(.5f, widths, true), 0.001f)
        assertEquals("overshooting the ends holds", centers.first(), PageStripLayout.centerAt(-3f, widths, true), 0.001f)
        assertEquals(centers.last(), PageStripLayout.centerAt(9f, widths, true), 0.001f)
    }

    @Test fun withoutTheCompassTheLensStartsAtHomeOne() {
        val widths = PageStripLayout.widths(false, 3)
        val centers = PageStripLayout.centers(widths)
        assertEquals(centers[0], PageStripLayout.centerAt(0f, widths, false), 0.001f)
        assertEquals("Discover has no item, so the lens holds at the first dot",
            centers[0], PageStripLayout.centerAt(-1f, widths, false), 0.001f)
    }

    @Test fun theLensIsWiderThanADotAndFitsInsideTheStrip() {
        assertTrue(PageStripLayout.LENS_WIDTH_DP > PageStripLayout.DOT_DP)
        assertTrue(PageStripLayout.LENS_HEIGHT_DP < PageStripLayout.ICON_DP)
    }
}
