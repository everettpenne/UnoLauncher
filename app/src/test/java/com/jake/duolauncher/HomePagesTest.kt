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

class MovePageAndHiddenAppsTest {
    private fun layoutWithThreePages(): HomeLayout {
        val slots = List(HOME_CELLS * 3) { index ->
            when (index) { 0 -> "first"; HOME_CELLS -> "second"; HOME_CELLS * 2 + 3 -> "third"; else -> null }
        }
        return HomeLayout(slots, emptyList(), widgetPlacements = listOf(WidgetPlacement(0, 5, 1, 0, 0, 2, 2)))
    }

    @Test fun movingAPageCarriesItsAppsAndWidgets() {
        val moved = layoutWithThreePages().withPageMoved(1, 1)
        assertEquals("third", moved.slots[HOME_CELLS + 3])
        assertEquals("second", moved.slots[HOME_CELLS * 2])
        assertEquals("first", moved.slots[0])
        assertEquals(2, moved.widgetPlacements.single().page)
        assertEquals(3, moved.pageCount)
    }

    @Test fun movingBackAndForthIsAnIdentityAndEdgesDoNothing() {
        val layout = layoutWithThreePages()
        assertEquals(normalizeHomeSlots(layout.slots), layout.withPageMoved(0, 1).withPageMoved(1, -1).slots)
        assertSame(layout, layout.withPageMoved(0, -1))
        assertSame(layout, layout.withPageMoved(2, 1))
        assertSame(layout, layout.withPageMoved(1, 2))
    }

    @Test fun hiddenAppsStayOutUnlessTheExactNameIsTyped() {
        val apps = listOf("Signal" to "a", "Camera" to "b", "Calculator" to "c")
        val shown = { q: String -> HiddenApps.shown(apps, setOf("a"), q, { it.second }, { it.first }).map { it.first } }
        assertEquals(listOf("Camera", "Calculator"), shown(""))
        assertEquals(listOf("Camera", "Calculator"), shown("sig"))
        assertEquals(listOf("Signal", "Camera", "Calculator"), shown("signal"))
        assertEquals(listOf("Signal", "Camera", "Calculator"), shown("  SIGNAL "))
        assertEquals(apps, HiddenApps.shown(apps, emptySet(), "x", { it.second }, { it.first }))
    }

    @Test fun badgeMuteListRoundTrips() {
        val muted = BadgeMute.with(BadgeMute.parse(""), "com.a", true)
        assertEquals("com.a", BadgeMute.encode(muted))
        assertEquals("com.a,com.b", BadgeMute.encode(BadgeMute.with(muted, "com.b", true)))
        assertEquals(emptySet<String>(), BadgeMute.with(muted, "com.a", false))
        assertEquals(setOf("x", "y"), BadgeMute.parse(" y, x ,,"))
    }
}
