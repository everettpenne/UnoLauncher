package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class WidgetMakesRoomTest {
    private fun layout(vararg apps: Pair<Int, String>, widgets: List<WidgetPlacement> = emptyList()): HomeLayout {
        var l = HomeLayout(slots = emptyList(), dock = List(4) { null }, widgetPlacements = widgets)
        apps.forEach { (i, id) -> l = l.withSlot(i, id) }
        return l
    }

    @Test fun iconsUnderADroppedWidgetMoveToTheNextFreeCellsInOrder() {
        // A 2 x 2 widget at the first cell covers cells 0, 1, 4, 5; apps a and b are under it, c is beside it.
        val l = layout(0 to "a", 1 to "b", 2 to "c")
        val widget = WidgetPlacement(7, CLOCK_WIDGET, 0, 0, 0, 2, 2)
        val placed = placeWidget(l.copy(widgetPlacements = emptyList()), widget)
        assertEquals(listOf(widget), placed.widgetPlacements)
        assertNull(placed.slotAt(0)); assertNull(placed.slotAt(1))
        assertEquals("c", placed.slotAt(2))
        // a and b took the next free cells after the widget, in order: 3 (beside c), then 6 (cells 4 and 5 are under the widget).
        assertEquals("a", placed.slotAt(3)); assertEquals("b", placed.slotAt(6))
        assertEquals(listOf("a", "b", "c"), listOf(3, 6, 2).sortedBy { it }.map { placed.slotAt(it) }.let { listOf("a", "b", "c") })
    }

    @Test fun nothingMovesWhenNothingIsUnderTheWidget() {
        val l = layout(8 to "x")
        val widget = WidgetPlacement(7, CLOCK_WIDGET, 0, 0, 0, 2, 2)
        val placed = placeWidget(l, widget)
        assertEquals("x", placed.slotAt(8))
        assertEquals(1, placed.widgetPlacements.size)
    }

    @Test fun aWidgetOverAnotherWidgetIsStillRefused() {
        val first = WidgetPlacement(1, CLOCK_WIDGET, 0, 0, 0, 2, 2)
        val l = layout(widgets = listOf(first))
        val second = WidgetPlacement(2, DATE_WIDGET, 0, 1, 1, 2, 2)
        assertEquals(l, placeWidget(l, second))
        assertNull(widgetCandidate(l, 2, 5, 2, 2))
    }

    @Test fun aFullPageSendsTheIconsOnToTheNextPage() {
        var l = HomeLayout(slots = emptyList(), dock = List(4) { null })
        for (i in 0 until HOME_CELLS) l = l.withSlot(i, "app$i")
        val widget = WidgetPlacement(3, CLOCK_WIDGET, 0, 0, 0, 2, 2)
        val placed = placeWidget(l, widget)
        assertEquals(1, placed.widgetPlacements.size)
        // Four apps were under it; the page had no free cell, so they are on the next page, in order.
        assertEquals(listOf("app0", "app1", "app4", "app5"), (HOME_CELLS until HOME_CELLS + 4).map { placed.slotAt(it) })
        assertEquals(2, placed.pageCount)
    }

    @Test fun noRoomMeansNoPlacement() {
        var l = HomeLayout(slots = emptyList(), dock = List(4) { null })
        for (i in 0 until MAX_HOME_PAGES * HOME_CELLS) l = l.withSlot(i, "app$i")
        val widget = WidgetPlacement(3, CLOCK_WIDGET, 0, 0, 0, 2, 2)
        assertEquals(l, placeWidget(l, widget))
        assertNull(widgetCandidate(l, 3, 0, 2, 2))
    }
}
