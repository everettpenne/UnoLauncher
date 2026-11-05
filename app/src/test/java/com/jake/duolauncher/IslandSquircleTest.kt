package com.jake.duolauncher

import com.jake.duolauncher.appearance.PathSink
import com.jake.duolauncher.appearance.SquircleCapsule
import com.jake.duolauncher.island.IslandGeometry
import com.jake.duolauncher.island.IslandMorph
import com.jake.duolauncher.island.LiquidSplit
import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.*
import org.junit.Test

/** Apple's squircle pill, and the stretchy split of a second activity off it. */
class IslandSquircleTest {
    private class Points : PathSink {
        val pts = mutableListOf<Pair<Float, Float>>(); var closed = false
        override fun moveTo(x: Float, y: Float) { pts += x to y }
        override fun lineTo(x: Float, y: Float) { pts += x to y }
        override fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) { pts += x3 to y3 }
        override fun close() { closed = true }
    }

    private fun pill(w: Float, h: Float, q: Float) = Points().also { SquircleCapsule.build(w, h, q, it) }

    @Test fun thePillIsClosedAndInsideItsBounds() {
        val p = pill(266f, 78f, 1f)
        assertTrue(p.closed)
        p.pts.forEach { (x, y) -> assertTrue("x=$x", x in -.01f..266.01f); assertTrue("y=$y", y in -.01f..78.01f) }
        // It touches all four sides: the ends at mid-height, the straight sides top and bottom.
        assertTrue(p.pts.any { abs(it.first) < .01f && abs(it.second - 39f) < .01f })
        assertTrue(p.pts.any { abs(it.first - 266f) < .01f && abs(it.second - 39f) < .01f })
    }

    @Test fun withNoSquircleItIsTheStadium() {
        // Every point of each end lies on the semicircle of half the height.
        val p = pill(200f, 40f, 0f)
        p.pts.filter { it.first > 180f }.forEach { (x, y) -> assertEquals(20f, hypot(x - 180f, y - 20f), .01f) }
    }

    @Test fun applesEndIsLongerAndFlatterTipped() {
        val stadium = pill(266f, 78f, 0f); val apple = pill(266f, 78f, 1f)
        // The straight top side ends sooner (the end is 1.25 half-heights long), and at 45 degrees the outline is further out than a circle's.
        val straightEnd = apple.pts.first().first
        assertEquals(39f * SquircleCapsule.END_LENGTH, straightEnd, .01f)
        fun reachAt(p: Points, y: Float) = p.pts.filter { it.first > 133f }.minBy { abs(it.second - y) }.first
        assertTrue(reachAt(apple, 8f) > reachAt(stadium, 8f) - 40f)
        assertTrue("the tip is fuller", reachAt(apple, 30f) - (266f - 39f * 1.25f) > (reachAt(stadium, 30f) - (266f - 39f)) * 1.0f)
    }

    @Test fun aShapeWithNoAreaDrawsNothing() {
        assertTrue(pill(-4f, 20f, 1f).pts.isEmpty())
        assertTrue(pill(40f, Float.NaN, 1f).pts.isEmpty())
    }

    @Test fun onlyThePillIsSquircled() {
        val d = 2.625f
        // The resting pill (16 mm, about 38 x 11 dp): all of it.
        assertEquals(1f, IslandMorph.squircleness(100f * d, 30f * d, 15f * d, d), 1e-4f)
        // A circle (a detached capsule, the ring round the camera) stays round.
        assertEquals(0f, IslandMorph.squircleness(30f * d, 30f * d, 15f * d, d), 0f)
        // A rounded panel (its corner under half its height) has the continuous corner.
        assertEquals(0f, IslandMorph.squircleness(380f * d, 200f * d, IslandGeometry.cornerCapDp * d, d), 0f)
        // Growing from the pill to a panel it eases out, reaching none where the continuous corner takes over (twice the corner tall).
        val full = 2f * IslandGeometry.cornerCapDp
        assertEquals(0f, IslandMorph.squircleness(380f * d, full * d, full / 2f * d, d), 1e-3f)
        val mid = IslandMorph.squircleness(380f * d, 66f * d, 33f * d, d)
        assertTrue(mid > 0f && mid < 1f)
    }

    @Test fun theNeckStretchesThinnerThenPinchesOff() {
        assertEquals(0f, LiquidSplit.tension(0f), 0f)
        assertEquals(0f, LiquidSplit.tension(LiquidSplit.PINCH), 0f)
        assertEquals(0f, LiquidSplit.tension(1.1f), 0f)                      // a spring's overshoot is past the pinch
        assertTrue(LiquidSplit.tension(.6f) > LiquidSplit.tension(.3f))
        val early = LiquidSplit.spread(.05f)!!; val late = LiquidSplit.spread(.7f)!!
        assertTrue("the neck thins as it stretches", late < early)
        assertNull(LiquidSplit.spread(LiquidSplit.PINCH))
        assertNull(LiquidSplit.spread(1f))
    }

    @Test fun theDropIsStretchedOnlyWhileTethered() {
        assertEquals(1f to 1f, LiquidSplit.stretch(0f))
        assertEquals(1f to 1f, LiquidSplit.stretch(1f))
        val (along, across) = LiquidSplit.stretch(.6f)
        assertTrue(along > 1f); assertTrue(across < 1f)
        assertEquals(0f, LiquidSplit.bulge(1f), 0f)
        assertTrue(LiquidSplit.bulge(.4f) > 0f)
    }
}
