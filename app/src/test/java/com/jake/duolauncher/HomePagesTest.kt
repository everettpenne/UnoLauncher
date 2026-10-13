package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class HomePagesTest {
    private val one = HomeLayout(listOf("a", "b"), emptyList(), widgetPlacements = emptyList())

    @Test fun `added pages persist as blank pages`() {
        val two = one.withAddedPage()
        assertEquals(2, two.pageCount)
        assertTrue(two.pageIsEmpty(1))
        assertEquals(3, two.withAddedPage().pageCount)
    }

    @Test fun `page count is capped`() {
        var layout = one
        repeat(MAX_HOME_PAGES + 5) { layout = layout.withAddedPage() }
        assertEquals(MAX_HOME_PAGES, layout.pageCount)
    }

    @Test fun `empty middle page removal shifts later apps and widgets down`() {
        val slots = List(HOME_CELLS * 2 + 1) { index -> if (index == HOME_CELLS * 2) "z" else null }
        val widget = WidgetPlacement(0, 5, 2, 0, 0, 2, 2)
        val layout = HomeLayout(slots, emptyList(), widgetPlacements = listOf(widget), minPages = 3)
        assertTrue(layout.pageIsEmpty(1))
        val next = layout.withoutPage(1)
        assertEquals(2, next.pageCount)
        assertEquals("z", next.slots[HOME_CELLS])
        assertEquals(1, next.widgetPlacements.single().page)
    }

    @Test fun `occupied and first pages are never removed`() {
        val layout = HomeLayout(listOf("a"), emptyList(), minPages = 2)
        assertSame(layout, layout.withoutPage(0))
        val busy = HomeLayout(List(HOME_CELLS + 1) { if (it == HOME_CELLS) "x" else null }, emptyList())
        assertEquals(busy, busy.withoutPage(1))
    }
}

class ResizeRowsTest {
    @Test fun `drag distance follows real mixed row pitches`() {
        // Top pitch 80, app pitch 100: a widget at row 2 spanning 1 row dragged 100 px is exactly 2 rows.
        assertEquals(2, resizeRowsFor(2, 1, 100f, 80f, 100f, 1, 4))
        // Half a row of travel below the midpoint stays put instead of rounding up by a uniform 80 px pitch.
        assertEquals(1, resizeRowsFor(2, 1, 40f, 80f, 100f, 1, 4))
        // Two top rows at the top pitch.
        assertEquals(3, resizeRowsFor(0, 2, 80f, 80f, 100f, 1, 6))
        assertEquals(1, resizeRowsFor(0, 2, -500f, 80f, 100f, 1, 6))
        assertEquals(6, resizeRowsFor(0, 2, 9000f, 80f, 100f, 1, 6))
    }
}
