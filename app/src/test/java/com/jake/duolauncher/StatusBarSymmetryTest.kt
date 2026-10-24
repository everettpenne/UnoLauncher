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

    @Test fun whenItIsCentredItIsClearOfEveryIconAndBarelyWider() {
        var centred = 0
        for (want in 330..470 step 4) {
            val l = StatusBarGrid.symmetric(want / d, (1080 - want) / d, holeL, holeR, pixel, width, false, false, 0, 32f) ?: continue
            centred++
            assertEquals("not centred for $want", cx - l.pillLeft, l.pillRight - cx, 1e-3f)
            assertTrue("left edge in an icon for $want", clear(l.pillLeft, pixel))
            assertTrue("right edge in an icon for $want", clear(l.pillRight, pixel))
            assertTrue("never narrower than wanted", l.pillLeft <= want / d + 1e-3f)
            // It may grow only a little to centre itself; it never balloons (0.102.0 grew until both sides cleared, far wider than wanted).
            assertTrue("grew too much for $want", want / d - l.pillLeft <= StatusBarGrid.MAX_SYMMETRIC_GROW_DP + 1e-3f)
        }
        assertTrue("centred for most widths on the measured bar", centred >= 10)
    }

    @Test fun itNeverCoversTheClockOrTheBattery() {
        val l = StatusBarGrid.symmetric(40f, width - 40f, holeL, holeR, pixel, width, false, false, 0, 32f)
        if (l != null) {
            assertTrue(l.pillLeft >= 160f / d)
            assertTrue(l.pillRight <= 919f / d)
        }
    }

    @Test fun anOffCentreWantStillGivesACentredIsland() {
        // The island showing an app's name wants to sit a few dp left; when centred, it is wide enough for that on both sides instead.
        val l = (330..400 step 2).asSequence().mapNotNull { w -> StatusBarGrid.symmetric(w / d, (w + 352) / d, holeL, holeR, pixel, width, false, false, 0, 32f)
            ?.let { w to it } }.first()
        assertEquals(cx - l.second.pillLeft, l.second.pillRight - cx, 1e-3f)
        assertTrue(l.second.pillLeft <= l.first / d + 1e-3f)
    }
    @Test fun privacyDotsWidenItOnBothSides() {
        val pairs = (300..440 step 2).mapNotNull { w ->
            val plain = StatusBarGrid.symmetric(w / d, (1080 - w) / d, holeL, holeR, pixel, width, false, false, 0, 32f)
            val dots = StatusBarGrid.symmetric(w / d, (1080 - w) / d, holeL, holeR, pixel, width, false, false, 2, 32f)
            if (plain != null && dots != null) plain to dots else null
        }
        assertTrue(pairs.isNotEmpty())
        pairs.forEach { (plain, dots) ->
            assertTrue(dots.pillRight - dots.pillLeft > plain.pillRight - plain.pillLeft)
            assertEquals(cx - dots.pillLeft, dots.pillRight - cx, 1e-3f)
        }
    }
    @Test fun capsulesSitBesideItClearOfIconsWithASeam() {
        val layouts = (300..440 step 2).mapNotNull { w -> StatusBarGrid.symmetric(w / d, (1080 - w) / d, holeL, holeR, pixel, width, true, true, 0, 32f) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach { l ->
            l.left?.let {
                assertEquals(l.pillLeft - StatusBarGrid.SEAM_DP, it.right, 1e-3f)
                assertTrue(it.width >= 32f - 1e-3f); assertTrue(clear(it.left, pixel)); assertTrue(it.left >= 160f / d)
            }
            l.right?.let {
                assertEquals(l.pillRight + StatusBarGrid.SEAM_DP, it.left, 1e-3f)
                assertTrue(it.width >= 32f - 1e-3f); assertTrue(clear(it.right, pixel)); assertTrue(it.right <= 919f / d)
            }
        }
    }
    @Test fun withNoRoomItGivesUp() {
        // Icons packed edge to edge on the right leave no clear point at any width short of the battery.
        val packed = listOf(Span(30f, 60f)) + (0 until 20).map { Span(cx + 12f + it * 9f, cx + 21f + it * 9f) }
        assertNull(StatusBarGrid.symmetric(cx - 40f, cx + 40f, holeL, holeR, packed, width, false, false, 0, 32f))
    }

    @Test fun theGridsOwnIconsNeverMakeItBalloon() {
        val icons = StatusBarGrid.gridIcons(width, 0f)
        for (want in 330..470 step 4) {
            val l = StatusBarGrid.symmetric(want / d, (1080 - want) / d, holeL, holeR, icons, width, false, false, 0, 32f) ?: continue
            assertEquals(cx - l.pillLeft, l.pillRight - cx, 1e-3f)
            assertTrue(want / d - l.pillLeft <= StatusBarGrid.MAX_SYMMETRIC_GROW_DP + 1e-3f)
        }
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

class StatusBarNearestTest {
    private val d = 2.625f
    private val width = 1080f / d
    private val holeL = 516f / d; private val holeR = 564f / d

    @Test fun withFewIconsTheIslandStaysTheWidthItWants() {
        // The phone in the report: a clock and one notification icon on the left, Wi-Fi and the battery on the right. Snapping to the gaps' middles
        // sent the left edge out to the gap after the notification icon; with the icons' real places it stays where it wants.
        val icons = listOf(Span(28f, 52f), Span(56f, 66f), Span(330f, 346f), Span(352f, 378f))
        val l = StatusBarGrid.nearest(140f, 272f, holeL, holeR, icons, width, false, false, 0, 32f)
        assertEquals(140f, l.pillLeft, 1e-3f)
        assertEquals(272f, l.pillRight, 1e-3f)
    }

    @Test fun anEdgeInAnIconStepsJustPastIt() {
        val icons = listOf(Span(28f, 52f), Span(130f, 145f), Span(268f, 280f), Span(352f, 378f))
        val l = StatusBarGrid.nearest(140f, 272f, holeL, holeR, icons, width, true, true, 0, 32f)
        assertEquals(130f - StatusBarGrid.EDGE_CLEAR_DP, l.pillLeft, 1e-3f)
        assertEquals(280f + StatusBarGrid.EDGE_CLEAR_DP, l.pillRight, 1e-3f)
        l.left?.let { assertEquals(l.pillLeft - StatusBarGrid.SEAM_DP, it.right, 1e-3f); assertTrue(it.left >= 52f) }
        l.right?.let { assertEquals(l.pillRight + StatusBarGrid.SEAM_DP, it.left, 1e-3f); assertTrue(it.right <= 352f) }
    }

    @Test fun itNeverCoversTheClockOrTheBattery() {
        val icons = listOf(Span(28f, 52f), Span(352f, 378f))
        val l = StatusBarGrid.nearest(40f, 360f, holeL, holeR, icons, width, false, false, 0, 32f)
        assertTrue(l.pillLeft >= 52f); assertTrue(l.pillRight <= 352f)
    }
}
