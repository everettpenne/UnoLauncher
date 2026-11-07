package com.jake.duolauncher

import com.jake.duolauncher.island.IslandGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The open island rounds as the phone's screen does, and sits as far from its sides as from its top. */
class IslandConcentricTest {
    @Test fun theOpenIslandRoundsAsTheScreenDoes() {
        // The display's own corner radius, not the concentric one less the island's margin.
        assertEquals(50f, IslandGeometry.displayCapDp(50f), 0f)
        assertEquals(36f, IslandGeometry.displayCapDp(36f), 0f)
    }

    @Test fun noReportedCornerFallsBackToApplesNumber() {
        assertEquals(IslandGeometry.CORNER_CAP_DP, IslandGeometry.displayCapDp(null), 0f)
        assertEquals(IslandGeometry.CORNER_CAP_DP, IslandGeometry.displayCapDp(0f), 0f)
        assertEquals(IslandGeometry.CORNER_CAP_DP, IslandGeometry.displayCapDp(Float.NaN), 0f)
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
        // A square-ish display would leave almost no corner; a huge report would make a blob.
        assertEquals(24f, IslandGeometry.displayCapDp(12f), 0f)
        assertEquals(72f, IslandGeometry.displayCapDp(140f), 0f)
    }
}
