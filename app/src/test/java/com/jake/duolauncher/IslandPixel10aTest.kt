package com.jake.duolauncher

import com.jake.duolauncher.island.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The island on a Pixel 10a: 1080 x 2424 at 420 dpi (411 dp wide), with a small round camera hole at the top centre. The hole's exact place
 * is read from the phone at run time; the one here is a representative 50 px hole, about 19 dp, centred 48 px down.
 */
class IslandPixel10aTest {
    private val d = 2.625f
    private val screenW = 1080f
    private val hole = PxRect(515f, 23f, 565f, 73f)
    private fun env(dockDp: Float = 0f) = IslandEnvironment(hole, screenW, 63f, dockWidthPx = dockDp * d, screenHeight = 2424f)

    /** The pill is the camera hole's own height, as an iPhone's island is its cutout (it stood about 8 dp proud of the hole above and below). */
    @Test fun theCollapsedPillIsAsTallAsTheCamera() {
        val pill = IslandGeometry.frame(env(), d, 0f, 0f)
        assertTrue("pill is ${pill.height / d} dp tall", pill.height / d in 19f..21f)
        assertEquals("centred on the hole", hole.centerY, pill.top + pill.height / 2f, .5f)
        assertTrue("clear of the top edge by ${pill.top / d} dp", pill.top / d >= 3.9f)
        // The content pill: a glyph one side, a short detail the other, about 1.45 times the 16 mm resting pill with the shipped widening.
        assertTrue("wide enough for a timer on one side: ${pill.width / d} dp", pill.width / d in 115f..135f)
        assertEquals("the hole is centred in it", pill.width / 2f, pill.hole!!.centerX, .5f)
    }

    /** A cutout outline drawn larger than the glass (here 84 px, 32 dp) is still a punch hole: it was sized as a notch, padded well above and below. */
    @Test fun aLargerOutlineStillGivesAPillAsTallAsIt() {
        val big = PxRect(498f, 10f, 582f, 94f)
        val pill = IslandGeometry.frame(IslandEnvironment(big, screenW, 63f, screenHeight = 2424f), d, 0f, .5f)
        // Only 10 px below the top edge, there is no room for the half-dp ring above it, so the pill is the outline's own height.
        assertTrue("pill ${pill.height / d} dp for a ${big.height / d} dp outline", pill.height in big.height - .5f..big.height + 1f * d)
        assertEquals(big.centerY, pill.top + pill.height / 2f, .5f)
    }

    @Test fun aNotchIsNotAPunchHole() {
        assertTrue(IslandGeometry.isPunchHole(hole, screenW))
        assertTrue(IslandGeometry.isPunchHole(PxRect(498f, 10f, 582f, 94f), screenW))
        assertTrue("touches the top edge", !IslandGeometry.isPunchHole(PxRect(400f, 0f, 680f, 80f), screenW))
        assertTrue("wide", !IslandGeometry.isPunchHole(PxRect(380f, 10f, 700f, 90f), screenW))
    }

    @Test fun theHeightSettingOnlyChangesTheHeight() {
        val small = IslandGeometry.frame(env(), d, 0f, 0f)
        val large = IslandGeometry.frame(env(), d, 0f, 1f)
        assertTrue("small ${small.height / d} dp, large ${large.height / d} dp", large.height - small.height >= 8f * d)
        assertTrue("never above the top margin", large.top / d >= 3.9f && small.top / d >= 3.9f)
        assertEquals("the width does not change", small.width, large.width, .5f)
        assertEquals("centred on the camera", hole.centerY, large.top + large.height / 2f, .5f)
        assertTrue("never thinner than the hole", small.height >= hole.height)
    }

    @Test fun theOpenPanelUsesMostOfTheWidthWhenThereIsNoDock() {
        val open = IslandGeometry.frame(env(), d, 1f, .5f)
        // Wide enough to cover the status bar's icons at both ends (they sit about 35 dp in), leaving the island's own 8 dp edge margin.
        assertEquals(395f, open.width / d, .5f)
        assertTrue("margin each side is ${(screenW - open.width) / 2f / d} dp", (screenW - open.width) / 2f / d in 7.5f..9f)
        assertEquals("centred on the hole", hole.centerX, open.left + open.width / 2f, .5f)
    }

    @Test fun theOpenPanelStillClearsTheDock() {
        val open = IslandGeometry.frame(env(dockDp = 56f), d, 1f, .5f)
        assertTrue("right edge ${(open.left + open.width) / d} dp", open.left + open.width <= screenW - (56f + 16f) * d + .5f)
    }

    @Test fun widthFollowsTheScreenAndNeverShrinksBelowTheOldPanel() {
        assertEquals(.965f * 411f, IslandGeometry.expandedWidthDp(411f), .01f)
        assertEquals(.965f * 360f, IslandGeometry.expandedWidthDp(360f), .01f)
        assertEquals(336f, IslandGeometry.expandedWidthDp(300f), 0f)
        assertEquals(400f, IslandGeometry.expandedWidthDp(900f), 0f)
    }

    @Test fun theCornerIsRoundEnoughForAPanelAndFullyRoundForThePill() {
        assertEquals(44f, IslandGeometry.CORNER_CAP_DP, 0f)
        val pill = IslandGeometry.frame(env(), d, 0f, 0f)
        assertTrue("a pill is a capsule: half its height (${pill.height / 2f / d} dp) is under the cap", pill.height / 2f / d < IslandGeometry.CORNER_CAP_DP)
        val open = IslandGeometry.frame(env(), d, 1f, .5f)
        assertTrue("an open panel is tall enough for the cap to apply", open.height / 2f / d > IslandGeometry.CORNER_CAP_DP)
    }
}
