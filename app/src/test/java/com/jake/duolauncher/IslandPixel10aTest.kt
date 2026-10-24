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
        val pill = IslandGeometry.frame(env(), d, 0f, .5f)
        assertTrue("pill is ${pill.height / d} dp tall", pill.height / d in 19f..21f)
        assertEquals("centred on the hole", hole.centerY, pill.top + pill.height / 2f, .5f)
        assertTrue("clear of the top edge by ${pill.top / d} dp", pill.top / d >= 3.9f)
        assertTrue("wide enough for a timer on one side: ${pill.width / d} dp", pill.width / d in 130f..165f)
        assertEquals("the hole is centred in it", pill.width / 2f, pill.hole!!.centerX, .5f)
    }

    @Test fun theSizeSettingOnlyChangesTheWidth() {
        val small = IslandGeometry.frame(env(), d, 0f, 0f)
        val large = IslandGeometry.frame(env(), d, 0f, 1f)
        assertTrue("small ${small.width / d} dp, large ${large.width / d} dp", large.width - small.width >= 50f * d)
        assertTrue("never above the top margin", large.top / d >= 3.9f && small.top / d >= 3.9f)
        assertEquals("the height is the hole's at every size", small.height, large.height, .5f)
        assertTrue("never thinner than the hole", small.height >= hole.height)
    }

    @Test fun theOpenPanelUsesMostOfTheWidthWhenThereIsNoDock() {
        val open = IslandGeometry.frame(env(), d, 1f, .5f)
        assertEquals(372f, open.width / d, .5f)
        assertTrue("margin each side is ${(screenW - open.width) / 2f / d} dp", (screenW - open.width) / 2f / d >= 16f)
        assertEquals("centred on the hole", hole.centerX, open.left + open.width / 2f, .5f)
    }

    @Test fun theOpenPanelStillClearsTheDock() {
        val open = IslandGeometry.frame(env(dockDp = 56f), d, 1f, .5f)
        assertTrue("right edge ${(open.left + open.width) / d} dp", open.left + open.width <= screenW - (56f + 16f) * d + .5f)
    }

    @Test fun widthFollowsTheScreenAndNeverShrinksBelowTheOldPanel() {
        assertEquals(372f, IslandGeometry.expandedWidthDp(411f), 0f)
        assertEquals(336f, IslandGeometry.expandedWidthDp(360f), 0f)
        assertEquals(336f, IslandGeometry.expandedWidthDp(300f), 0f)
        assertEquals(372f, IslandGeometry.expandedWidthDp(900f), 0f)
    }

    @Test fun theCornerIsRoundEnoughForAPanelAndFullyRoundForThePill() {
        assertEquals(34f, IslandGeometry.CORNER_CAP_DP, 0f)
        val pill = IslandGeometry.frame(env(), d, 0f, .5f)
        assertTrue("a pill is a capsule: half its height (${pill.height / 2f / d} dp) is under the cap", pill.height / 2f / d < IslandGeometry.CORNER_CAP_DP)
        val open = IslandGeometry.frame(env(), d, 1f, .5f)
        assertTrue("an open panel is tall enough for the cap to apply", open.height / 2f / d > IslandGeometry.CORNER_CAP_DP)
    }
}
