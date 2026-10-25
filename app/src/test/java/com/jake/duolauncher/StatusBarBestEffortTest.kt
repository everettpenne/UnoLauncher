package com.jake.duolauncher

import com.jake.duolauncher.island.StatusBarGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A busy status bar, as the alignment service read it on a Pixel 10a (px at 2.625): the clock, five notification icons, then Do Not
 * Disturb, mobile data and the battery. No symmetric width near a live pill's clears every icon. */
class StatusBarBestEffortTest {
    private val d = 2.625f
    private fun s(l: Int, r: Int) = StatusBarGrid.Span(l / d, r / d)
    private val icons = listOf(s(81, 175), s(175, 233), s(233, 291), s(291, 349), s(349, 407), s(407, 465), s(696, 754), s(762, 821), s(908, 971))
    private val holeL = 498.5f / d; private val holeR = 581.5f / d; private val cx = 540f / d
    private fun cut(x: Float) = icons.sumOf { if (x > it.left && x < it.right) minOf(x - it.left, it.right - x).toDouble() else 0.0 }

    @Test fun aBusyBarHasNoCleanSymmetricWidthAndTheBestEffortIsNeverWorse() {
        val wantL = 345f / d; val wantR = 735f / d
        assertNull(StatusBarGrid.symmetric(wantL, wantR, holeL, holeR, icons, 1080 / d, false, false, 0, 30f))
        val best = StatusBarGrid.symmetricBestEffort(wantL, wantR, holeL, holeR, icons, 1080 / d, false, false, 0, 30f)
        assertEquals("centred", cx, (best.pillLeft + best.pillRight) / 2f, .01f)
        // Icons run edge to edge from 175 to 465 px here, so the only clean symmetric widths are 266 px (too narrow for a live pill's
        // detail) and 614 px (most of the bar): the best effort can only match the wanted width's cut, never be worse.
        val sliverDp = cut(best.pillLeft) + cut(best.pillRight)
        assertTrue("no worse than the wanted width: $sliverDp dp", sliverDp <= cut(wantL) + cut(wantR) + 1e-3)
        assertTrue("never over the clock or the battery", best.pillLeft > 175 / d && best.pillRight < 908 / d)
    }

    @Test fun aQuietBarKeepsTheCleanAnswer() {
        val quiet = listOf(s(81, 175), s(175, 233), s(762, 821), s(908, 971))
        val wantL = 405f / d; val wantR = 675f / d
        val best = StatusBarGrid.symmetricBestEffort(wantL, wantR, holeL, holeR, quiet, 1080 / d, false, false, 0, 30f)
        assertEquals(StatusBarGrid.symmetric(wantL, wantR, holeL, holeR, quiet, 1080 / d, false, false, 0, 30f), best)
    }
}
