package com.jake.duolauncher

import com.jake.duolauncher.island.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusBarScanTest {
    private val d = 2.625f
    private val width = 1080f
    private val bar = 136f
    private fun px(l: Float, r: Float, top: Float = 40f, bottom: Float = 96f) = PxRect(l, top, r, bottom)

    @Test fun onlySmallItemsInTheBarCount() {
        val icons = StatusBarScan.icons(listOf(
            px(93f, 160f),                      // the clock
            px(188f, 218f),                     // a notification icon
            PxRect(0f, 0f, 1080f, 136f),        // the whole bar: a container, not an icon
            px(400f, 430f, 300f, 360f),         // below the bar: the shade, not the bar
            px(919f, 988f),                     // the battery
        ), width, bar, d)
        assertEquals(3, icons.size)
        assertEquals(93f / d, icons[0].left, 1e-3f)
        assertEquals(988f / d, icons[2].right, 1e-3f)
    }

    @Test fun overlappingPartsAreOneIconButTouchingOnesAreTwo() {
        // A battery and its bolt drawn over it: one icon.
        val battery = StatusBarScan.icons(listOf(px(919f, 980f), px(950f, 988f)), width, bar, d)
        assertEquals(1, battery.size)
        assertEquals(919f / d, battery[0].left, 1e-3f); assertEquals(988f / d, battery[0].right, 1e-3f)
        // The clock and the notification icon after it, read on the emulator: their bounds touch (each holds its own padding), two icons.
        assertEquals(2, StatusBarScan.icons(listOf(px(11f, 126f), px(126f, 184f)), width, bar, d).size)
    }

    @Test fun gapsAreTheMiddlesBetweenIconsEachSideOfTheCamera() {
        val icons = listOf(StatusBarGrid.Span(35f, 61f), StatusBarGrid.Span(71f, 83f), StatusBarGrid.Span(94f, 106f),
            StatusBarGrid.Span(300f, 320f), StatusBarGrid.Span(326f, 344f), StatusBarGrid.Span(350f, 376f))
        val (left, right) = StatusBarScan.gaps(icons, holeLeftDp = 195f, holeRightDp = 216f)
        // Left: never before the clock (the first icon), then between icons, then just past the last.
        assertEquals(listOf(66f, 88.5f, 109f), left)
        // Right: just before the first, between icons, just past the last.
        assertEquals(listOf(297f, 323f, 347f, 379f), right)
    }

    @Test fun aSideWithNoIconsKeepsTheGrid() {
        val (left, right) = StatusBarScan.gaps(listOf(StatusBarGrid.Span(300f, 320f)), 195f, 216f)
        assertNull(left)
        assertEquals(listOf(297f, 323f), right)
    }

    @Test fun measuredGapsDriveTheLayout() {
        // An island wanting 120..290 dp snaps outward to the measured gaps, not the Pixel grid's.
        val left = listOf(66f, 88.5f, 109f); val right = listOf(297f, 323f, 347f, 379f)
        val layout = StatusBarGrid.layout(120f, 290f, 411f, 0f, 195f, 216f, wantLeft = false, wantRight = false, dots = 0, capsuleMinWidth = 37f,
            measuredLeft = left, measuredRight = right)
        assertEquals(109f - StatusBarGrid.OUTER_BIAS_DP, layout.pillLeft, 1e-3f)
        assertEquals(297f + StatusBarGrid.OUTER_BIAS_DP, layout.pillRight, 1e-3f)
        // Without a reading, the measured Pixel grid is used as before.
        val grid = StatusBarGrid.layout(120f, 290f, 411f, 0f, 195f, 216f, false, false, 0, 37f)
        assertTrue(grid.pillLeft != layout.pillLeft)
    }
}
