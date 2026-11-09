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

    @Test fun theMeridiemIsSplitFromTheDigits() {
        assertEquals("12:41" to " PM", OpenIslandLayout.splitMeridiem("12:41 PM"))
        assertEquals("23:05" to "", OpenIslandLayout.splitMeridiem("23:05"))
        assertEquals("--" to "", OpenIslandLayout.splitMeridiem("--"))
    }
}
