package com.jake.duolauncher

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
        assertEquals(4, path.ops.count { it == "L" })
        path.points.forEach { (x, y) ->
            assertTrue("x=$x", x in -0.001f..100.001f)
            assertTrue("y=$y", y in -0.001f..100.001f)
        }
    }

    @Test fun continuousCornersEaseInOverMoreThanTheRadius() {
        // A circular corner of radius 24 would start 24 from the corner; the continuous curve
        // starts (1 + 0.6) * 24 = 38.4 away.
        assertEquals(38.4f, build(200f, 200f, 24f).firstMove.first, 0.01f)
        assertEquals(24f, build(200f, 200f, 24f, smoothing = 0f).firstMove.first, 0.01f)
    }

    @Test fun shapeIsSymmetricLeftToRightAndTopToBottom() {
        val points = build(160f, 160f, 30f).points
        fun has(x: Float, y: Float) = points.any { abs(it.first - x) < 0.01f && abs(it.second - y) < 0.01f }
        points.forEach { (x, y) ->
            assertTrue("mirror x of ($x,$y)", has(160f - x, y))
            assertTrue("mirror y of ($x,$y)", has(x, 160f - y))
        }
    }

    @Test fun smoothingGivesWayBeforeTheRadiusWhenThereIsNoRoom() {
        // r = 40 on a 100-square: 1.6 * 40 = 64 > 50, so the easing run is capped at half the side.
        assertEquals(50f, build(100f, 100f, 40f).firstMove.first, 0.01f)
        // A radius beyond half the side clamps to a capsule; the edge run is then zero length.
        assertEquals(50f, build(100f, 100f, 90f).firstMove.first, 0.01f)
    }

    @Test fun zeroRadiusIsAPlainRectangle() {
        val rect = build(80f, 40f, 0f)
        assertEquals(listOf("M", "L", "L", "L", "Z"), rect.ops)
    }

    @Test fun cornerCurveEndsWhereTheNextEdgeBegins() {
        // Every curve is followed by a line starting exactly at its end: no gaps in the outline.
        val path = build(120f, 80f, 20f)
        var index = 0
        var last: Pair<Float, Float>? = null
        for (op in path.ops) when (op) {
            "M", "L" -> { last = path.points[index]; index++ }
            "C" -> { last = path.points[index + 2]; index += 3 }
        }
        assertEquals(path.firstMove.first, last!!.first, 0.01f)
        assertEquals(path.firstMove.second, last.second, 0.01f)
    }

    @Test fun iconFractionMatchesApple() {
        assertEquals(0.2237f, Corner.ICON_FRACTION, 0.0001f)
    }
}
