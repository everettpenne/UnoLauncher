package com.jake.duolauncher

import com.jake.duolauncher.island.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandMorphTest {
    private val d = 2.625f
    // A Pixel 10a-like pill: 37 dp tall, the camera hole in its middle.
    private val pill = IslandFrame(380f, 10f, 320f, 97f, PxRect(136f, 25f, 184f, 72f))
    // The open panel round the same camera: the hole near its top.
    private val open = IslandFrame(60f, 10f, 960f, 700f, PxRect(456f, 25f, 504f, 72f))

    @Test fun theBodyScalesAboutTheCamera() {
        val (fx, fy) = IslandMorph.pivot(open)
        assertEquals(480f / 960f, fx, 1e-4f)
        assertEquals(48.5f / 700f, fy, 1e-4f)
        // The camera's own point does not move, however the body swells or squashes.
        val (x, y) = IslandMorph.scaled(open, open.left + 480f, open.top + 48.5f, 1.2f, .8f)
        assertEquals(open.left + 480f, x, 1e-3f); assertEquals(open.top + 48.5f, y, 1e-3f)
        // A press on the open panel no longer lifts its top edge: scaled about the camera, the top moves by only the camera's few pixels' worth.
        val (_, top) = IslandMorph.scaled(open, open.left, open.top, 1f, 1.1f)
        assertTrue(top > open.top - 6f)
    }

    @Test fun withoutACameraItScalesFromTheTopCentre() {
        val plain = IslandFrame(100f, 50f, 300f, 90f, null)
        assertEquals(.5f to 0f, IslandMorph.pivot(plain))
        assertEquals(.5f to 0f, IslandMorph.pivot(plain.copy(width = 0f)))
    }

    @Test fun theDotsSitWhereTheyAlwaysDidOnThePill() {
        for (count in 1..3) for (index in 0 until count) {
            val corner = IslandMorph.cornerPx(pill.width, pill.height, d)
            val (x, y) = IslandMorph.dotCenter(pill, index, count, d, corner)
            val oldLeft = pill.left + pill.width - (IslandPrivacy.END_PAD_DP + (count - index) * IslandPrivacy.STEP_DP) * d
            assertEquals(oldLeft + IslandPrivacy.DOT_DP * d / 2f, x, 1e-3f)
            assertEquals(pill.top + pill.height / 2f, y, 1e-3f)
        }
    }

    @Test fun onTheOpenIslandTheDotsStayBesideTheCameraAndInsideTheCorner() {
        val corner = IslandMorph.cornerPx(open.width, open.height, d)
        val (x, y) = IslandMorph.dotCenter(open, 0, 1, d, corner)
        assertEquals(open.top + open.hole!!.centerY, y, 1e-3f)
        // Moved in from the edge by as much as the rounded corner curves away at that height.
        val inset = IslandMorph.cornerInset(open, y, corner)
        assertTrue(inset > 0f)
        assertEquals(open.left + open.width - inset - (IslandPrivacy.END_PAD_DP + IslandPrivacy.STEP_DP - IslandPrivacy.DOT_DP / 2f) * d, x, 1e-3f)
    }

    @Test fun theCornerInsetIsZeroAlongTheSidesAndGrowsIntoTheCorners() {
        assertEquals(0f, IslandMorph.cornerInset(open, open.top + 350f, 89f), 0f)
        assertEquals(89f, IslandMorph.cornerInset(open, open.top, 89f), 1e-3f)
        assertEquals(89f, IslandMorph.cornerInset(open, open.top + open.height, 89f), 1e-3f)
        assertEquals(0f, IslandMorph.cornerInset(open, open.top + 10f, 0f), 0f)
    }

    @Test fun theCornerFollowsTheIslandsRule() {
        assertEquals(pill.height / 2f, IslandMorph.cornerPx(pill.width, pill.height, d), 1e-3f)
        assertEquals(IslandGeometry.CORNER_CAP_DP * d, IslandMorph.cornerPx(open.width, open.height, d), 1e-3f)
        assertEquals(IslandGeometry.CORNER_CAP_DP * d * 1.35f, IslandMorph.cornerPx(open.width, open.height, d, 1.35f), 1e-3f)
        assertEquals(pill.height / 2f * .45f, IslandMorph.cornerPx(pill.width, pill.height, d, .45f), 1e-3f)
        assertEquals(0f, IslandMorph.cornerPx(-10f, 40f, d), 0f)
    }

    @Test fun theStripIsTheCamerasLine() {
        assertEquals(open.top + open.hole!!.centerY, IslandMorph.stripCenterY(open), 1e-3f)
        val sideways = open.copy(side = IslandSide.LEFT)
        assertEquals(sideways.top + sideways.height / 2f, IslandMorph.stripCenterY(sideways), 1e-3f)
    }

    @Test fun theGridsEdgesLetGoWithTheWidth() {
        // Settled shut, the edge shift is whole; fully wide (whatever the height), it is gone, so the panel is centred on the camera.
        val shut = withEdgeShift(pill, 1f, -20f, 30f)
        assertEquals(pill.left - 20f, shut.left, 1e-3f)
        assertEquals(pill.width + 50f, shut.width, 1e-3f)
        assertEquals(open, withEdgeShift(open, 0f, -20f, 30f))
    }
}
