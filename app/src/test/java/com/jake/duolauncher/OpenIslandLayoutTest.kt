package com.jake.duolauncher

import com.jake.duolauncher.island.*
import org.junit.Assert.*
import org.junit.Test

class OpenIslandLayoutTest {
    @Test fun oneLineShowsUnderTheDateAndTheLabelGoesFirst() {
        val l = OpenIslandLayout
        assertEquals(OpenIslandLayout.HeroLine.CLOCK, l.heroStatus(clock = true, charge = true, note = true))
        assertEquals(OpenIslandLayout.HeroLine.CHARGE, l.heroStatus(clock = false, charge = true, note = true))
        assertEquals(OpenIslandLayout.HeroLine.NOTE, l.heroStatus(clock = false, charge = false, note = true))
        assertNull(l.heroStatus(clock = false, charge = false, note = false))
    }

    @Test fun theOpenIslandSharesSearchsRampSolidRoundTheCameraThenThinning() {
        val stops = com.jake.duolauncher.search.SpotlightBar.barStops(.2f)
        assertEquals(.2f, stops[1].first, 0f)
        assertEquals(1f, stops[1].second.alpha, 0f)
        assertEquals(.42f, stops.last().second.alpha, .01f)
        assertTrue("it only thins going down", stops.toList().zipWithNext().all { (a, b) -> b.first >= a.first && b.second.alpha <= a.second.alpha })
    }

    @Test fun theTracksColourOnlyTintsTheGlassBandAndDeepensDownIt() {
        val stops = IslandGlow.glassStops(.6f, androidx.compose.ui.graphics.Color.Red)
        assertEquals(0f, stops[1].second.alpha, 0f)
        assertEquals(.6f, stops[1].first, 0f)
        assertEquals(.6f, stops.last().second.alpha, .01f)
        assertTrue(stops.toList().zipWithNext().all { (a, b) -> b.first >= a.first && b.second.alpha >= a.second.alpha })
    }

    @Test fun theLabelIsDroppedWhenTheRowBelowOrTheClockSaysIt() {
        assertEquals("You're navigating", OpenIslandLayout.heroNote("You're navigating", rowBelowSaysIt = false, clockShown = false))
        assertNull(OpenIslandLayout.heroNote("Now playing", rowBelowSaysIt = true, clockShown = false))
        assertNull(OpenIslandLayout.heroNote("Timer", rowBelowSaysIt = false, clockShown = true))
    }

    @Test fun aBusyPanelGivesTheHeadlineOneLineAndLessHeight() {
        assertEquals(2, PanelHeight.headlineLines(panelBusy = false))
        assertEquals(1, PanelHeight.headlineLines(panelBusy = true))
        assertEquals(PanelHeight.HEADLINE_ONE_LINE_DP, PanelHeight.extraDp(true, 1f, headlineLines = 1), 0f)
    }

    @Test fun theMeridiemIsSplitFromTheDigits() {
        assertEquals("12:41" to " PM", OpenIslandLayout.splitMeridiem("12:41 PM"))
        assertEquals("23:05" to "", OpenIslandLayout.splitMeridiem("23:05"))
        assertEquals("--" to "", OpenIslandLayout.splitMeridiem("--"))
    }
}
