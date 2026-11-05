package com.jake.duolauncher

import com.jake.duolauncher.island.IslandGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

/** The open island's corner follows Apple's rule: concentric with the display, its corner less the island's margin from the top. */
class IslandConcentricTest {
    @Test fun anIphoneGivesApplesFortyFour() {
        // iPhone 14 Pro and later: 55 pt display corners, the island 11 pt from the top.
        assertEquals(44f, IslandGeometry.concentricCapDp(55f, 11f), 0f)
    }

    @Test fun itFollowsThePhonesOwnCorners() {
        assertEquals(38f, IslandGeometry.concentricCapDp(50f, 12f), 0f)
        assertEquals(30f, IslandGeometry.concentricCapDp(36f, 6f), 0f)
    }

    @Test fun noReportedCornerFallsBackToApplesNumber() {
        assertEquals(IslandGeometry.CORNER_CAP_DP, IslandGeometry.concentricCapDp(null, 10f), 0f)
        assertEquals(IslandGeometry.CORNER_CAP_DP, IslandGeometry.concentricCapDp(0f, 10f), 0f)
        assertEquals(IslandGeometry.CORNER_CAP_DP, IslandGeometry.concentricCapDp(Float.NaN, 10f), 0f)
    }

    @Test fun anOddReportIsKeptSensible() {
        // Square-ish displays or a far-down island would leave almost no corner; a huge report would make a blob.
        assertEquals(24f, IslandGeometry.concentricCapDp(20f, 12f), 0f)
        assertEquals(64f, IslandGeometry.concentricCapDp(140f, 10f), 0f)
        assertEquals(40f, IslandGeometry.concentricCapDp(40f, -5f), 0f)
    }
}
