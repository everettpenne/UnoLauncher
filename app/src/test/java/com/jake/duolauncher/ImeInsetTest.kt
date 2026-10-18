package com.jake.duolauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class ImeInsetTest {
    @Test fun keyboardClosedKeepsTheGlassAtRest() {
        // ime inset 0: the glass must not move, because the screen's system-bars padding already
        // keeps it clear of the navigation bar.
        assertEquals(0, imeLiftPx(imeBottom = 0, navBarBottom = 48))
    }

    @Test fun keyboardLiftsTheGlassAboveItsTopEdge() {
        // On API 30+ the ime inset is measured from the window bottom, so it includes the
        // navigation bar region. Only the part above the navigation bar lifts the glass.
        assertEquals(-252, imeLiftPx(imeBottom = 300, navBarBottom = 48))
    }

    @Test fun liftIsCappedAtRestWhenImeIsShorterThanTheNavigationBar() {
        // Degenerate case: never push the glass down below its resting position.
        assertEquals(0, imeLiftPx(imeBottom = 20, navBarBottom = 48))
    }

    @Test fun gestureNavigationPillIsCleared() {
        assertEquals(-96, imeLiftPx(imeBottom = 120, navBarBottom = 24))
    }
}
