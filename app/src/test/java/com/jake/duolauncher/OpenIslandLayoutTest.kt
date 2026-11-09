package com.jake.duolauncher

import com.jake.duolauncher.island.*
import org.junit.Assert.*
import org.junit.Test

class OpenIslandLayoutTest {
    @Test fun theRoomBesideACentredCameraIsMeasuredInsideThePanelsPadding() {
        assertEquals(135f to 135f, OpenIslandLayout.ears(165f, 195f, 360f))
    }

    @Test fun aSideTooNarrowForItsTextGivesUpTheEars() {
        assertNull(OpenIslandLayout.ears(100f, 130f, 360f))
        assertNull(OpenIslandLayout.ears(90f, 110f, 200f))
    }

    @Test fun theBandBesideTheCameraComesInClearOfTheCorner() {
        // A 44 dp corner, text 7 dp down: the curve is about 20 dp in there, so the text starts 8 dp past that, beyond the 18 dp side padding.
        assertEquals(10f, OpenIslandLayout.earIndent(44f, 7f), .5f)
        // Further down the corner has curved back, and a square panel needs nothing.
        assertEquals(0f, OpenIslandLayout.earIndent(44f, 44f), 0f)
        assertEquals(0f, OpenIslandLayout.earIndent(0f, 7f), 0f)
    }

    @Test fun atMostTwoLinesShowOppositeTheTimeAndTheLabelGoesFirst() {
        val l = OpenIslandLayout
        assertEquals(listOf(OpenIslandLayout.HeroLine.CHARGE, OpenIslandLayout.HeroLine.ALARM), l.heroLines(clock = false, note = true, charge = true, alarm = true))
        assertEquals(listOf(OpenIslandLayout.HeroLine.NOTE, OpenIslandLayout.HeroLine.ALARM), l.heroLines(clock = false, note = true, charge = false, alarm = true))
        assertEquals(listOf(OpenIslandLayout.HeroLine.CLOCK, OpenIslandLayout.HeroLine.CHARGE), l.heroLines(clock = true, note = true, charge = true, alarm = true))
        assertTrue(l.heroLines(clock = false, note = false, charge = false, alarm = false).isEmpty())
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
