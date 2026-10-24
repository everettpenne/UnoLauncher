package com.jake.duolauncher

import com.jake.duolauncher.island.StatusBarGrid
import com.jake.duolauncher.island.StatusBarGrid.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The island symmetric about the camera, on the Pixel 10a's measured status bar (2.625 density, 1080 px wide). */
class StatusBarSymmetryTest {
    private val d = 2.625f
    private val width = 1080f / d
    private val holeL = 516f / d; private val holeR = 564f / d
    private val cx = (holeL + holeR) / 2f
    // The icons from the screenshots: clock, four notification icons; do not disturb, signal, Wi-Fi, battery.
    private val pixel = listOf(93f to 160f, 188f to 218f, 248f to 279f, 305f to 336f, 360f to 396f,
        736f to 767f, 788f to 842f, 858f to 903f, 919f to 988f).map { (a, b) -> Span(a / d, b / d) }

    private fun clear(x: Float, icons: List<Span>) = icons.none { x > it.left - 1.5f && x < it.right + 1.5f }

    @Test fun theIslandIsCentredOnTheCameraAndClearOfEveryIcon() {
        for (want in 330..470 step 4) {
            val l = StatusBarGrid.symmetric(want / d, (1080 - want) / d, holeL, holeR, pixel, width, false, false, 0, 32f)
            assertNotNull("no symmetric width for $want", l); l!!
            assertEquals("not centred for $want", cx - l.pillLeft, l.pillRight - cx, 1e-3f)
            assertTrue("left edge in an icon for $want", clear(l.pillLeft, pixel))
            assertTrue("right edge in an icon for $want", clear(l.pillRight, pixel))
            assertTrue("never narrower than wanted", l.pillLeft <= want / d + 1e-3f)
        }
    }

    @Test fun itNeverCoversTheClockOrTheBattery() {
        val l = StatusBarGrid.symmetric(40f, width - 40f, holeL, holeR, pixel, width, false, false, 0, 32f)
        if (l != null) {
            assertTrue(l.pillLeft >= 160f / d)
            assertTrue(l.pillRight <= 919f / d)
        }
    }

    @Test fun anOffCentreWantStillGivesACentredIsland() {
        // The island showing an app's name wants to sit a few dp left; symmetric, it is wide enough for that on both sides instead.
        val l = StatusBarGrid.symmetric(340f / d, 700f / d, holeL, holeR, pixel, width, false, false, 0, 32f)!!
        assertEquals(cx - l.pillLeft, l.pillRight - cx, 1e-3f)
        assertTrue(l.pillLeft <= 340f / d)
    }

    @Test fun privacyDotsWidenItOnBothSides() {
        val plain = StatusBarGrid.symmetric(360f / d, 720f / d, holeL, holeR, pixel, width, false, false, 0, 32f)!!
        val dots = StatusBarGrid.symmetric(360f / d, 720f / d, holeL, holeR, pixel, width, false, false, 2, 32f)!!
        assertTrue(dots.pillRight - dots.pillLeft > plain.pillRight - plain.pillLeft)
        assertEquals(cx - dots.pillLeft, dots.pillRight - cx, 1e-3f)
    }

    @Test fun capsulesSitBesideItClearOfIconsWithASeam() {
        val l = StatusBarGrid.symmetric(380f / d, 700f / d, holeL, holeR, pixel, width, true, true, 0, 32f)!!
        l.left?.let {
            assertEquals(l.pillLeft - StatusBarGrid.SEAM_DP, it.right, 1e-3f)
            assertTrue(it.width >= 32f - 1e-3f); assertTrue(clear(it.left, pixel)); assertTrue(it.left >= 160f / d)
        }
        l.right?.let {
            assertEquals(l.pillRight + StatusBarGrid.SEAM_DP, it.left, 1e-3f)
            assertTrue(it.width >= 32f - 1e-3f); assertTrue(clear(it.right, pixel)); assertTrue(it.right <= 919f / d)
        }
    }

    @Test fun withNoRoomItGivesUp() {
        // Icons packed edge to edge on the right leave no clear point at any width short of the battery.
        val packed = listOf(Span(30f, 60f)) + (0 until 20).map { Span(cx + 12f + it * 9f, cx + 21f + it * 9f) }
        assertNull(StatusBarGrid.symmetric(cx - 40f, cx + 40f, holeL, holeR, packed, width, false, false, 0, 32f))
    }

    @Test fun theGridsOwnIconsGiveASymmetricIslandToo() {
        val icons = StatusBarGrid.gridIcons(width, 0f)
        val l = StatusBarGrid.symmetric(366f / d, 715f / d, holeL, holeR, icons, width, false, false, 0, 32f)
        assertNotNull(l)
        assertEquals(cx - l!!.pillLeft, l.pillRight - cx, 1e-3f)
    }
}

class StatusBarTidyTest {
    @Test fun theCommandListsThePickedSlotsInOrder() {
        assertEquals("adb shell settings put secure icon_blacklist alarm_clock,headset,nfc",
            StatusBarTidy.command(listOf("nfc", "alarm_clock", "headset")))
        assertNull(StatusBarTidy.command(emptyList()))
        // An unknown slot is never put in the command.
        assertEquals("adb shell settings put secure icon_blacklist rotate", StatusBarTidy.command(listOf("rotate", "; rm -rf /")))
    }
}

class UnoStatusBarLayoutTest {
    @Test fun theEarsAreTheSpacesBesideTheIsland() {
        val e = UnoStatusBarLayout.ears(411f, 135f, 276f)
        assertEquals(UnoStatusBarLayout.EDGE_DP, e[0], 0f)
        assertEquals(135f - UnoStatusBarLayout.GAP_DP, e[1], 1e-3f)
        assertEquals(276f + UnoStatusBarLayout.GAP_DP, e[2], 1e-3f)
        assertEquals(411f - UnoStatusBarLayout.EDGE_DP, e[3], 1e-3f)
        // Without an island the middle is kept clear, symmetric about the centre.
        val n = UnoStatusBarLayout.ears(400f, null, null)
        assertEquals(200f - n[1], n[2] - 200f, 1e-3f)
    }
}
