package com.jake.duolauncher

import com.jake.duolauncher.island.LowPass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LowPassTest {
    private val dt = 0.016f

    @Test fun theFirstStepSnapsToTheRawValue() {
        assertEquals(100f, LowPass().step(dt, 100f), 0f)
        assertEquals(0f, LowPass().step(dt, 0f), 0f)
    }

    @Test fun aStepConvergesPartWayTowardTheRawValue() {
        val f = LowPass()
        f.step(dt, 0f)
        val y = f.step(dt, 100f)
        // One 16 ms step at tau 40 ms moves about a third of the way, not all of it.
        assertTrue("partial step: $y", y > 20f && y < 50f)
    }

    @Test fun jitterIsAttenuatedNotRemovedEntirely() {
        val f = LowPass()
        f.step(dt, 0f)
        var last = 0f
        repeat(40) { i -> last = f.step(dt, if (i % 2 == 0) 0f else 100f) }
        // The raw value swings 0 ↔ 100; the filtered value hugs the middle.
        assertTrue("filtered stays near the mean: $last", last in 30f..70f)
    }

    @Test fun velocityTracksAConstantRamp() {
        val f = LowPass()
        f.step(dt, 0f)
        val slope = 800f   // units per second
        repeat(120) { i -> f.step(dt, slope * (i + 1) * dt) }
        assertEquals(slope, f.velocity(), slope * .05f)
    }

    @Test fun velocityIsNegativeForAnUpwardRamp() {
        val f = LowPass()
        f.step(dt, 0f)
        val slope = -600f   // the finger moving up
        repeat(120) { i -> f.step(dt, slope * (i + 1) * dt) }
        assertEquals(slope, f.velocity(), -slope * .05f)
    }

    @Test fun aLongGapSnapsAndForgetsTheVelocity() {
        val f = LowPass()
        f.step(dt, 0f)
        f.step(dt, 100f)
        val y = f.step(1f, 200f)   // 1 s gap: snap, no carry-over
        assertEquals(200f, y, 0f)
        assertEquals(0f, f.velocity(), 0f)
    }
}
