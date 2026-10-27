package com.jake.duolauncher

import com.jake.duolauncher.appearance.*

import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class SquircleTest {
    private class Recorder : PathSink {
        val ops = mutableListOf<String>()
        val points = mutableListOf<Pair<Float, Float>>()
        override fun moveTo(x: Float, y: Float) { ops += "M"; points += x to y }
        override fun lineTo(x: Float, y: Float) { ops += "L"; points += x to y }
        override fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            ops += "C"; points += (x1 to y1); points += (x2 to y2); points += (x3 to y3)
        }
        override fun close() { ops += "Z" }
        val firstMove get() = points.first()
    }

    private fun build(w: Float, h: Float, r: Float, smoothing: Float = Squircle.SMOOTHING) =
        Recorder().also { Squircle.build(w, h, r, it, smoothing) }

    @Test fun pathIsClosedAndStaysInsideItsBounds() {
        val path = build(100f, 100f, 24f)
        assertEquals("M", path.ops.first())
        assertEquals("Z", path.ops.last())
        path.points.forEach { (x, y) ->
            assertTrue("x=$x", x in -0.001f..100.001f)
            assertTrue("y=$y", y in -0.001f..100.001f)
        }
    }

    @Test fun anIconIsTheWholeSuperellipse() {
        // At an icon's radius (22.37 percent of its side) the corner takes in half of each side, so the path starts at the middle of the top edge.
        assertEquals(50f, build(100f, 100f, 100f * Corner.ICON_FRACTION).firstMove.first, 0.1f)
    }

    @Test fun theCurveLeavesTheEdgeLongBeforeACircularArcWould() {
        // A circular corner of the icon's radius (22.37) stays on the top edge until 22.37 from the vertex; the superellipse is already off it 10 from the vertex
        // (and its 45-degree point is about where the circle's is), which is the gradual, "squircle" look.
        val path = build(100f, 100f, 22.37f)
        val at60 = path.points.filter { (x, y) -> x > 50f && y < 50f }.minByOrNull { (x, _) -> abs(x - 90f) }!!.second
        assertTrue("y at x=90 is $at60", at60 > 1.0f)
        val diagonal = path.points.filter { (x, y) -> x > 50f && y < 50f }.minByOrNull { (x, y) -> abs((100f - x) - y) }!!
        assertEquals(6.5f, 100f - diagonal.first, 0.6f)
    }

    @Test fun shapeIsSymmetricLeftToRightAndTopToBottom() {
        val points = build(160f, 160f, 30f).points
        fun has(x: Float, y: Float) = points.any { abs(it.first - x) < 0.05f && abs(it.second - y) < 0.05f }
        points.forEach { (x, y) ->
            assertTrue("mirror x of ($x,$y)", has(160f - x, y))
            assertTrue("mirror y of ($x,$y)", has(x, 160f - y))
        }
    }

    @Test fun theCornerGivesWayWhenThereIsNoRoom() {
        // A big radius is capped at half the shorter side.
        assertEquals(50f, build(100f, 100f, 90f).firstMove.first, 0.01f)
        assertEquals(20f, build(200f, 40f, 90f).firstMove.first, 0.01f)
    }

    @Test fun aWidgetsCornerTakesInAboutTwoAndAHalfRadii() {
        assertEquals(26f * Squircle.REACH, build(340f, 340f, 26f).firstMove.first, 0.01f)
    }

    @Test fun zeroRadiusIsAPlainRectangle() {
        val rect = build(80f, 40f, 0f)
        assertEquals(listOf("M", "L", "L", "L", "Z"), rect.ops)
    }

    @Test fun iconFractionMatchesApple() {
        assertEquals(0.2237f, Corner.ICON_FRACTION, 0.0001f)
    }
}
