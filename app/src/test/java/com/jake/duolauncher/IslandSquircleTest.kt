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

    @Test fun theJoinNarrowsToAWaistThenSnaps() {
        assertEquals(0f, LiquidSplit.tension(0f), 0f)
        assertEquals(0f, LiquidSplit.tension(LiquidSplit.PINCH), 0f)
        assertEquals(0f, LiquidSplit.tension(1.1f), 0f)                      // a spring's overshoot is past the snap
        assertTrue(LiquidSplit.tension(.6f) > LiquidSplit.tension(.3f))
        val start = LiquidSplit.waist(0f)!!; val mid = LiquidSplit.waist(.4f)!!; val late = LiquidSplit.waist(.7f)!!
        assertTrue("a fat join at first, nearly the pill's own sides", start > Math.toRadians(70.0).toFloat())
        assertTrue("it narrows, and fastest at the end", late < mid && mid < start && (mid - late) > (start - mid))
        assertNull(LiquidSplit.waist(LiquidSplit.PINCH))
        assertNull(LiquidSplit.waist(1f))
    }

    @Test fun theJoinRunsFromThePillsEndToTheTipOnBothSides() {
        val p = Points()
        // The end (r 28 at 0, 0) and a tip (r 20) 30 px to the right: overlapping, as at the start of the pull.
        LiquidSplit.join(0f, 0f, 28f, 30f, 0f, 20f, LiquidSplit.waist(.2f)!!, 1f, p)
        assertTrue(p.closed); assertEquals(4, p.pts.size)
        val (start, tipTop) = p.pts[0] to p.pts[1]
        assertEquals("starts on the end's edge", 28f, kotlin.math.hypot(start.first, start.second), .01f)
        assertEquals("meets the tip's edge", 20f, kotlin.math.hypot(tipTop.first - 30f, tipTop.second), .01f)
        assertEquals("the two flanks mirror each other", -p.pts[0].second, p.pts[3].second, .01f)
        assertTrue(p.pts[0].second < 0f && p.pts[3].second > 0f)
        // Mirrored for a capsule on the left.
        val l = Points()
        LiquidSplit.join(0f, 0f, 28f, -30f, 0f, 20f, LiquidSplit.waist(.2f)!!, -1f, l)
        assertEquals(-p.pts[1].first, l.pts[1].first, .01f)
    }

    @Test fun theTipStaysRoundWhileHeldAndIsOnlyStretchedOnceItPops() {
        assertEquals(1f to 1f, LiquidSplit.stretch(0f))
        assertEquals(1f to 1f, LiquidSplit.stretch(.5f, speed = 5f))
        assertEquals(1f to 1f, LiquidSplit.stretch(1f))
        val (along, across) = LiquidSplit.stretch(.9f, speed = 5f)
        assertTrue(along > 1f); assertTrue(across < 1f)
    }

    @Test fun theTipIsDrawnOutThenPops() {
        assertEquals(0f, LiquidSplit.travel(0f), 0f)
        assertEquals(1f, LiquidSplit.travel(1f), 1e-6f)
        assertEquals(1.1f, LiquidSplit.travel(1.1f), 1e-6f)                   // a spring's overshoot carries it past its place
        // Drawn out steadily, then faster once it has snapped.
        assertTrue(LiquidSplit.travelRate(LiquidSplit.PINCH + .01f) > LiquidSplit.travelRate(LiquidSplit.PINCH - .01f))
        // No jump at the snap.
        assertEquals(LiquidSplit.travel(LiquidSplit.PINCH - 1e-4f), LiquidSplit.travel(LiquidSplit.PINCH), 1e-3f)
        var last = -1f
        for (i in 0..120) { val v = LiquidSplit.travel(i / 100f); assertTrue(v >= last); last = v }
        // It pops a little big on the overshoot, and is its own size settled.
        assertEquals(1f, LiquidSplit.size(1f), 1e-6f)
        assertTrue(LiquidSplit.size(1.1f) > 1f && LiquidSplit.size(2f) <= 1.15f)
        assertTrue(LiquidSplit.size(0f) < LiquidSplit.size(.5f))
    }

    @Test fun thePillIsDrawnOutTowardThePull() {
        assertEquals(LiquidSplit.BodyPull(0f, 0f, 1f), LiquidSplit.bodyPull(0f, 0f))
        val right = LiquidSplit.bodyPull(0f, 1f)
        assertTrue(right.right > 0f && right.left == 0f && right.thin < 1f)
        // The spring's overshoot after the snap pulls the end back a little past rest, never far.
        val recoil = LiquidSplit.bodyPull(0f, -1f)
        assertTrue(recoil.right < 0f && recoil.right > -right.right && recoil.thin > 1f)
        // Two capsules, one each side, draw out both ends.
        val both = LiquidSplit.bodyPull(1f, 1f)
        assertTrue(both.left > 0f && both.right > 0f && both.stretch > right.stretch)
    }

    @Test fun theTipComesRoundAsItSlows() {
        val (fast, fastAcross) = LiquidSplit.stretch(1f, speed = 6f)
        val (slow, _) = LiquidSplit.stretch(1f, speed = 1f)
        assertTrue(fast > slow && slow > 1f)
        assertEquals(1f to 1f, LiquidSplit.stretch(1f, speed = 0f))           // settled, it is round
        assertTrue("it keeps about its area", fast * fastAcross in .85f..1.15f)
        assertEquals(LiquidSplit.stretch(1f, 3f), LiquidSplit.stretch(1f, -3f)) // the overshoot's return stretches it too
        assertEquals(0f, LiquidSplit.glyphAlpha(.5f), 0f); assertEquals(1f, LiquidSplit.glyphAlpha(1f), 0f)
    }
}
