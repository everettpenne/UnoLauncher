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
}
