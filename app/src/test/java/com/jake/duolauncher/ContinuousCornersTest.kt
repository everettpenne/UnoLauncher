package com.jake.duolauncher

import com.jake.duolauncher.appearance.*

import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.*
import org.junit.Test

class ContinuousCornersTest {
    /** Records the outline as a list of segments, each the pen position followed by the segment's points. */
    private class Recorder : PathSink {
        val ops = mutableListOf<String>()
        val segments = mutableListOf<List<Pair<Float, Float>>>()
        private var pen = 0f to 0f
        val firstMove get() = segments.first().first()
        override fun moveTo(x: Float, y: Float) { ops += "M"; pen = x to y; segments += listOf(pen) }
        override fun lineTo(x: Float, y: Float) { ops += "L"; segments += listOf(pen, x to y); pen = x to y }
        override fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            ops += "C"; segments += listOf(pen, x1 to y1, x2 to y2, x3 to y3); pen = x3 to y3
        }
        override fun close() { ops += "Z" }
        val curves get() = segments.filter { it.size == 4 }
    }

    private fun build(w: Float, h: Float, r: Float) = Recorder().also { ContinuousCorners.build(w, h, r, r, r, r, it) }

    @Test fun pathIsClosedAndStaysInsideItsBounds() {
        val path = build(300f, 120f, 26f)
        assertEquals("M", path.ops.first()); assertEquals("Z", path.ops.last())
        path.segments.flatten().forEach { (x, y) ->
            assertTrue("x=$x", x in -0.001f..300.001f); assertTrue("y=$y", y in -0.001f..120.001f)
        }
    }

    @Test fun theCornerTakesInOnePointSixRadiiOfEachEdge() {
        // iOS's continuous corner leaves the edge about 1.5 radii from the vertex; a circular arc leaves it at 1, the icon superellipse at 2.2.
        assertEquals(26f * 1.6f, build(340f, 340f, 26f).firstMove.first, 0.01f)
    }

    @Test fun theMiddleOfTheCornerIsTheCircle() {
        // The arc's two ends lie on the circle of the corner's radius, centred one radius in from each edge.
        val r = 26f
        val arc = build(340f, 340f, r).curves[1]                                    // top right: easing in, arc, easing out
        listOf(arc.first(), arc.last()).forEach { (x, y) -> assertEquals(r, hypot(x - (340f - r), y - r), 0.01f) }
        // So the 45-degree point is exactly where a circular corner of the same radius has it: only the way into the corner changes.
        val mid = cubicAt(arc, .5f)
        assertEquals(r * (Math.sqrt(2.0).toFloat() - 1f), hypot(340f - mid.first, mid.second), 0.02f)
    }

    @Test fun thereAreNoKinksWhereTheSegmentsMeet() {
        val path = build(340f, 200f, 32f)
        val curves = path.curves
        for (i in 0 until curves.size - 1) {
            val a = curves[i]; val b = curves[i + 1]
            if (a.last() != b.first()) continue                                      // a straight edge between corners
            val outgoing = direction(a[2], a[3]); val incoming = direction(b[0], b[1])
            assertEquals("joint $i x", outgoing.first, incoming.first, 0.002f)
            assertEquals("joint $i y", outgoing.second, incoming.second, 0.002f)
        }
    }

    @Test fun aCapsuleEndsInSemicircles() {
        // At half the height there is no room to ease in, so the smoothing gives way and the ends are circular, starting exactly a radius in.
        val pill = build(200f, 40f, 20f)
        assertEquals(20f, pill.firstMove.first, 0.01f)
        val arc = pill.curves[1]
        assertEquals(200f, arc.last().first, 0.01f); assertEquals(20f, arc.last().second, 0.01f)
    }

    @Test fun aRadiusTooBigForTheShapeIsCapped() {
        assertEquals(50f, build(100f, 100f, 90f).firstMove.first, 0.01f)
        assertEquals(20f, build(200f, 40f, 90f).firstMove.first, 0.01f)
    }

    @Test fun aShapeWithNoAreaDrawsNothing() {
        // The island's inner fill is its size less a rim, which goes negative while it is at its smallest; that used to throw.
        assertTrue(build(-6.5f, 20f, 10f).ops.isEmpty())
        assertTrue(build(40f, 0f, 10f).ops.isEmpty())
        assertTrue(build(Float.NaN, 10f, 4f).ops.isEmpty())
    }

    @Test fun cornersCanDiffer() {
        // A sheet rounds only its top corners: the bottom ones are square.
        val sheet = Recorder().also { ContinuousCorners.build(400f, 600f, 42f, 42f, 0f, 0f, it) }
        val ends = sheet.segments.map { it.last() }
        assertTrue(400f to 600f in ends); assertTrue(0f to 600f in ends)
        assertEquals(42f * 1.6f, sheet.firstMove.first, 0.01f)
    }

    @Test fun shapeIsSymmetric() {
        val ends = build(160f, 160f, 30f).segments.flatten()
        fun has(x: Float, y: Float) = ends.any { abs(it.first - x) < 0.05f && abs(it.second - y) < 0.05f }
        ends.forEach { (x, y) -> assertTrue("mirror x of ($x,$y)", has(160f - x, y)); assertTrue("mirror y of ($x,$y)", has(x, 160f - y)) }
    }

    private fun direction(from: Pair<Float, Float>, to: Pair<Float, Float>): Pair<Float, Float> {
        val dx = to.first - from.first; val dy = to.second - from.second; val l = hypot(dx, dy)
        return dx / l to dy / l
    }

    private fun cubicAt(c: List<Pair<Float, Float>>, t: Float): Pair<Float, Float> {
        val u = 1 - t
        fun at(sel: (Pair<Float, Float>) -> Float) = u * u * u * sel(c[0]) + 3 * u * u * t * sel(c[1]) + 3 * u * t * t * sel(c[2]) + t * t * t * sel(c[3])
        return at { it.first } to at { it.second }
    }
}
