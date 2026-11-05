package com.jake.duolauncher

import com.jake.duolauncher.island.IslandGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    /** A Pixel 10a: 1080 x 2424 at 420 dpi (2.625 px a dp), its centred punch-hole camera as IslandPixel10aTest has it. */
    private val pixel10a = com.jake.duolauncher.island.IslandEnvironment(
        cutout = com.jake.duolauncher.island.PxRect(515f, 23f, 565f, 73f), screenWidth = 1080f, statusBarHeight = 63f, screenHeight = 2424f)

    @Test fun theOpenIslandIsAsFarFromTheSidesAsFromTheTop() {
        val d = 2.625f
        for (setting in listOf(0f, .5f, 1f)) {
            val open = IslandGeometry.frame(pixel10a, d, 1f, setting)
            assertEquals("left gap at height setting $setting", open.top, open.left, .01f)
            assertEquals("right gap at height setting $setting", open.top, 1080f - (open.left + open.width), .01f)
        }
        // An off-centre camera still gives equal gaps, and the camera stays inside the panel.
        val off = pixel10a.copy(cutout = com.jake.duolauncher.island.PxRect(560f, 23f, 610f, 73f))
        val open = IslandGeometry.frame(off, d, 1f, 0f)
        assertEquals(open.left, 1080f - (open.left + open.width), .01f)
        val hole = open.hole!!
        assertTrue(hole.left > 0f && hole.right < open.width)
    }

    @Test fun thePillStillWrapsTheCamera() {
        val pill = IslandGeometry.frame(pixel10a, 2.625f, 0f, 0f)
        assertEquals(540f, pill.left + pill.width / 2f, .5f)
        assertTrue(pill.width < 600f)
    }

    @Test fun anOddReportIsKeptSensible() {
        // Square-ish displays or a far-down island would leave almost no corner; a huge report would make a blob.
        assertEquals(24f, IslandGeometry.concentricCapDp(20f, 12f), 0f)
        assertEquals(64f, IslandGeometry.concentricCapDp(140f, 10f), 0f)
        assertEquals(40f, IslandGeometry.concentricCapDp(40f, -5f), 0f)
    }
}
