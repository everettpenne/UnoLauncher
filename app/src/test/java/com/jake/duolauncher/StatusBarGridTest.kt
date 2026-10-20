package com.jake.duolauncher

import com.jake.duolauncher.island.StatusBarGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The grid is measured on a Pixel 10a at 2.625 density (1080 px = 411.4 dp wide); the screenshots' icon positions are the oracle. */
class StatusBarGridTest {
    private val d = 2.625f
    private val width = 1080f / d
    private fun px(dp: Float) = dp * d
    // The camera hole: 516..564 px.
    private val holeL = 516f / d; private val holeR = 564f / d
    private fun layout(l: Float, r: Float, left: Boolean = false, right: Boolean = false, dots: Int = 0, shift: Float = 0f) =
        StatusBarGrid.layout(l, r, width, shift, holeL, holeR, left, right, dots, 32f)

    @Test fun theRestingIslandSnapsToTheGapsBesideTheFourthNotificationIcon() {
        // Measured: the island at rest spanned about 366..715 px and cut the fourth icon (360..396). Its edges move to the gaps either side.
        val l = layout(366f / d, 715f / d)
        assertEquals(348f, px(l.pillLeft), 5f)          // the gap between icons 3 (ends 336) and 4 (starts 360)
        assertEquals(726f, px(l.pillRight), 5f)         // the gap before the do-not-disturb icon (starts 736)
        assertTrue("clear of icon 3", px(l.pillLeft) > 336f && px(l.pillLeft) < 360f)
        assertTrue("clear of the do-not-disturb icon", px(l.pillRight) < 736f)
    }

    @Test fun anIslandEdgeNeverLandsInsideAnIconOnTheLeftWhateverItWants() {
        // Notification icons measured at 188..218, 248..279, 305..336, 360..396 px (pitch about 57 px, each about 30 to 36 px wide).
        val icons = (0 until 6).map { 188f + it * 57.3f to 188f + it * 57.3f + 33f }
        for (want in 150..500 step 3) {
            val edge = px(layout(want / d, 726f / d).pillLeft)
            assertTrue("edge $edge for want $want is inside an icon", icons.none { (a, b) -> edge > a - 2f && edge < b + 2f })
        }
    }

    @Test fun anIslandEdgeNeverLandsInsideAnIconOnTheRightWhateverItWants() {
        // Measured right-hand icons: do not disturb 736..767, signal 788..842, Wi-Fi 858..903, battery 919..988 px; extras pack leftward.
        val icons = listOf(736f to 767f, 788f to 842f, 858f to 903f, 919f to 988f, 679f to 712f, 622f to 655f)
        for (want in 700..1040 step 3) {
            val edge = px(layout(348f / d, want / d).pillRight)
            assertTrue("edge $edge for want $want is inside an icon", icons.none { (a, b) -> edge > a - 2f && edge < b + 2f })
        }
    }

    @Test fun aTwoDigitHourMovesTheLeftGapsRightByADigit() {
        val one = layout(366f / d, 715f / d).pillLeft
        val two = layout(366f / d, 715f / d, shift = StatusBarGrid.DIGIT_DP).pillLeft
        // The wanted edge is the same, but the grid has moved: the island picks the gap at or left of it, here the gap one slot to the left.
        assertTrue("moved by a gap", two != one)
        // Grown outward to the gap at or left of the wanted edge (the gap just right of it now starts at about 367 px).
        assertTrue(px(two) in 290f..370f && two <= 366f / d + StatusBarGrid.INSET_DP)
    }

    @Test fun theIslandGrowsOutwardNeverInward() {
        val l = layout(300f / d, 800f / d)
        assertTrue(l.pillLeft <= 300f / d + StatusBarGrid.INSET_DP + 0.01f)
        assertTrue(l.pillRight >= 800f / d - StatusBarGrid.INSET_DP - 0.01f)
    }

    @Test fun theIslandStopsAtTheClockAndTheBatteryAndAlwaysWrapsTheHole() {
        val wide = layout(0f, width)
        assertTrue("not over the clock", px(wide.pillLeft) >= 160f)
        assertTrue("not over the battery", px(wide.pillRight) <= 1080f)
        assertTrue(wide.pillLeft < holeL && wide.pillRight > holeR)
        val narrow = layout(holeL, holeR)
        assertTrue(narrow.pillLeft < holeL && narrow.pillRight > holeR)
    }

    @Test fun aLeftCapsuleCoversTwoIconSlotsAndLeavesAHairlineSeamToTheIsland() {
        val l = layout(366f / d, 715f / d, left = true)
        val left = assertNotNull(l.left).let { l.left!! }
        // Two slots: from just past the outer gap's middle to just inside the seam's.
        assertEquals("two slots wide", 2 * StatusBarGrid.ICON_PITCH_DP - StatusBarGrid.INSET_DP + StatusBarGrid.OUTER_BIAS_DP, left.width, 0.6f)
        // The seam between capsule and island is centred on one gap: wholly inside the 8 dp the gap has.
        assertEquals(2 * StatusBarGrid.INSET_DP, l.pillLeft - left.right, 0.01f)
        assertTrue("clear of the clock", px(left.left) > 160f)
    }

    @Test fun aRightCapsuleCoversAsFewSlotsAsItNeedsToBeWideEnough() {
        val l = layout(366f / d, 715f / d, right = true)
        val right = l.right!!
        assertTrue(right.width >= 32f)
        assertEquals(2 * StatusBarGrid.INSET_DP, right.left - l.pillRight, 0.01f)
        assertTrue("inside the screen", right.right < width)
        // It ends in a gap too: not through the signal icon (788..842) or the Wi-Fi icon (858..903).
        assertTrue(px(right.right).let { e -> e < 788f - 2f || (e > 842f + 2f && e < 858f - 2f) || (e > 903f + 2f && e < 919f - 2f) })
    }

    @Test fun privacyDotsAskTheIslandForMoreRoomOnTheRight() {
        val none = layout(366f / d, 715f / d).pillRight
        val two = layout(366f / d, 715f / d, dots = 2).pillRight
        val three = layout(366f / d, 715f / d, dots = 3).pillRight
        assertTrue(two > none && three > two)
    }

    @Test fun withoutKnownIconsTheCapsulesSitBesideTheIslandFlush() {
        val f = StatusBarGrid.flush(100f, 300f, wantLeft = true, wantRight = true, capsuleMinWidth = 32f)
        assertEquals(100f, f.pillLeft, 0f); assertEquals(300f, f.pillRight, 0f)
        assertEquals(62f, f.left!!.left, 0.01f); assertEquals(94f, f.left.right, 0.01f)
        assertEquals(306f, f.right!!.left, 0.01f)
        assertNull(StatusBarGrid.flush(100f, 300f, false, false, 32f).left)
    }

    /** Every island edge in every state lands in a gap: never inside a notification icon (with a one- or a two-digit hour) or a right-hand icon. */
    @Test fun noEdgeOfAnyIslandInAnyStateLandsInsideAnIcon() {
        val shifts = listOf(0f, StatusBarGrid.DIGIT_DP)
        val rightIcons = listOf(736f to 767f, 788f to 842f, 858f to 903f, 919f to 988f, 679f to 712f, 622f to 655f)
        for (shift in shifts) {
            val leftIcons = (0 until 7).map { 188f + shift * d + it * 57.3f to 188f + shift * d + it * 57.3f + 33f }
            val clock = 93f to 160f + shift * d
            for (want in 200..520 step 10) for (wantR in 600..1000 step 20) for (left in listOf(false, true)) for (right in listOf(false, true)) for (dots in 0..3) {
                val l = layout(want / d, wantR / d, left, right, dots, shift)
                val edges = listOfNotNull(l.pillLeft, l.pillRight, l.left?.left, l.left?.right, l.right?.left, l.right?.right).map { px(it) }
                for (e in edges) {
                    assertTrue("edge $e in an icon (want $want..$wantR left=$left right=$right dots=$dots shift=$shift)",
                        leftIcons.none { (a, b) -> e > a - 1f && e < b + 1f } && rightIcons.none { (a, b) -> e > a - 1f && e < b + 1f })
                    assertTrue("edge $e through the clock", e < clock.first - 1f || e > clock.second + 1f)
                }
                assertTrue("islands overlap", (l.left?.right ?: -1f) <= l.pillLeft && (l.right?.left ?: 1e9f) >= l.pillRight)
                assertTrue("hole wrapped", l.pillLeft < holeL && l.pillRight > holeR)
            }
        }
    }

    /** Regression from the phone: the wide island's right end sat 1.5 dp inside the narrow signal-to-Wi-Fi gap, and its round end showed the top of
     * the signal icon's tallest bar. An outer edge now sits a little past the gap's middle, away from the island. */
    @Test fun anOuterEdgeSitsPastTheGapsMiddleSoTheRoundEndCoversTheIconsCorners() {
        val wide = layout(250f / d, 845f / d)
        val signalEnd = 842f; val wifiStart = 858f
        val edge = px(wide.pillRight)
        assertTrue("past the gap's middle (850): $edge", edge > 850f)
        assertTrue("still clear of Wi-Fi", edge < wifiStart - 2f)
        // An island 80 px tall falls back about 4.3 px at an icon's top or bottom (18 px from the middle row); the icon is still covered there.
        assertTrue(edge - 4.5f > signalEnd)
    }
}
