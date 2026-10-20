package com.jake.duolauncher

import com.jake.duolauncher.island.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandDebugTest {
    private fun frame() = IslandFrame(400f, 20f, 300f, 100f, PxRect(130f, 10f, 170f, 50f))

    @Test fun noAdjustmentLeavesTheFrameAlone() {
        assertEquals(frame(), withDebugAdjust(frame(), 1f, 0f, 0f, 0f, 2f))
    }

    @Test fun widthGrowsBothWaysAboutTheCentreAndTheCameraHoleStaysPutOnScreen() {
        val f = frame(); val g = withDebugAdjust(f, 1f, 20f, 0f, 0f, 2f)
        assertEquals(f.width + 40f, g.width, 0.001f)
        assertEquals(f.left - 20f, g.left, 0.001f)
        // The hole's place on the screen (island left + hole left) did not move.
        assertEquals(f.left + f.hole!!.left, g.left + g.hole!!.left, 0.001f)
        assertEquals(f.top + f.hole.top, g.top + g.hole!!.top, 0.001f)
    }

    @Test fun positionMovesAcrossAndDownInDp() {
        val g = withDebugAdjust(frame(), 1f, 0f, 10f, -4f, 3f)
        assertEquals(430f, g.left, 0.001f)
        assertEquals(8f, g.top, 0.001f)
    }

    @Test fun anOpenIslandIsNotDisturbed() {
        assertEquals(frame(), withDebugAdjust(frame(), 0f, 50f, 50f, 50f, 2f))
        val half = withDebugAdjust(frame(), .5f, 20f, 0f, 0f, 2f)
        assertEquals(frame().width + 20f, half.width, 0.001f)
    }

    @Test fun valuesAreKeptInRange() {
        val g = DebugGroup(dw = 999f, dx = -999f, dy = 999f).clamped()
        assertEquals(120f, g.dw, 0f); assertEquals(-60f, g.dx, 0f); assertEquals(40f, g.dy, 0f)
    }

    @Test fun theSummaryNamesEveryIslandAndWhatIsForced() {
        val s = IslandDebug.summaryOf(DebugGroup(), DebugGroup(2f, -3f, 0f), DebugGroup(0f, 4.5f, 1f), DebugGroup(), setOf(LiveKind.TIMER, LiveKind.CALL))
        assertEquals("main w+0 x+0 y+0 | left w+2 x-3 y+0 | right w+0 x+4.5 y+1 | extra w+0 x+0 y+0 | forced call,timer", s)
        assertTrue(IslandDebug.summaryOf(DebugGroup(), DebugGroup(), DebugGroup(), DebugGroup(), emptySet()).endsWith("forced none"))
        assertNotEquals(s, IslandDebug.summaryOf(DebugGroup(), DebugGroup(), DebugGroup(), DebugGroup(), emptySet()))
    }

    @Test fun theTouchWindowIsTheIslandAndItsCapsulesAndNothingMore() {
        // Regression: the touch window once grew by a debug "slack" all round, which took touches meant for the app under the top of the screen.
        val f = frame()
        val plain = touchArea(f, emptyList(), 12f)
        assertEquals(f.height + 24f, plain.height, 0.001f)
        assertEquals(f.top - 12f, plain.top, 0.001f)
        // Capsules either side widen it to them, and no further; at the island's height it stays the island's height.
        val left = PxRect(f.left - 120f, f.top, f.left - 10f, f.top + f.height)
        val right = PxRect(f.left + f.width + 10f, f.top, f.left + f.width + 130f, f.top + f.height)
        val both = touchArea(f, listOf(left, right), 12f)
        assertEquals(plain.height, both.height, 0.001f)
        assertEquals(left.left - 12f, both.left, 0.001f)
        assertEquals(right.right + 12f, both.right, 0.001f)
        // A capsule set lower makes the window taller below only by that much.
        assertEquals(plain.height + 8f, touchArea(f, listOf(PxRect(left.left, left.top + 8f, left.right, left.bottom + 8f)), 12f).height, 0.001f)
    }

    @Test fun theShippedLayoutIsTheOneChosenOnTheDebugPage() {
        assertEquals(DebugGroup(15f, 0f, 0f), IslandLayoutDefaults.MAIN)
        assertEquals(DebugGroup(62f, -3f, 0f), IslandLayoutDefaults.MAIN_SHOWING_APP)
        // The capsules' width and gap now come from the status bar grid, so their defaults start at zero.
        assertEquals(DebugGroup(), IslandLayoutDefaults.LEFT)
        assertEquals(DebugGroup(), IslandLayoutDefaults.RIGHT)
    }

    @Test fun aWideAdjustmentNeverRunsOffTheScreen() {
        val g = withDebugAdjust(frame(), 1f, 500f, 0f, 0f, 2f, screenWidth = 1080f)
        assertTrue(g.width <= 1080f - 32f + 0.01f)
        assertTrue(g.left >= 16f - 0.01f && g.left + g.width <= 1080f - 16f + 0.01f)
    }
}
