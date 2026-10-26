package com.jake.duolauncher

import com.jake.duolauncher.island.CompactText
import com.jake.duolauncher.island.StatusBarGrid
import org.junit.Assert.assertEquals
import org.junit.Test

class CompactTextTest {
    @Test fun unitsBecomeTheirLetters() {
        assertEquals("12m", CompactText.shorten("12 min"))
        assertEquals("1h 5m", CompactText.shorten("1 hr 5 min"))
        assertEquals("3 stops, 8m", CompactText.shorten("3 stops, 8 minutes"))
        assertEquals("400m", CompactText.shorten("400 m"))
        assertEquals("1.2km", CompactText.shorten("1.2 km"))
    }

    @Test fun aLevelAfterAWordIsTheLevelAlone() {
        assertEquals("84%", CompactText.shorten("Charging 84%"))
        assertEquals("100%", CompactText.shorten("Charging 100%"))
    }

    @Test fun nothingToShortenIsLeftAlone() {
        assertEquals("Arriving", CompactText.shorten("Arriving"))
        assertEquals("9:59", CompactText.shorten("9:59"))
    }

    @Test fun anEdgeInsideAnIconCutsTheSmallerPart() {
        val icons = listOf(StatusBarGrid.Span(100f, 120f))
        assertEquals(5f, StatusBarGrid.cutDp(105f, 200f, icons), .001f)
        assertEquals(0f, StatusBarGrid.cutDp(90f, 200f, icons), .001f)
    }
}
