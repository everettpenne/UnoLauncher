package com.jake.duolauncher

import com.jake.duolauncher.island.MotionAction
import com.jake.duolauncher.island.MotionChannel
import com.jake.duolauncher.island.MotionPlan
import com.jake.duolauncher.island.MotionTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class IslandMotionPlanTest {
    private val rest = MotionTarget(height = 0f, width = 0f, opening = false, fingerDriven = false, eventWidthDp = 0f, compactness = 0f,
        edgeLeft = -20f, edgeRight = 20f, pressX = 1f, pressY = 1f, screen = "phone", animate = true)

    @Test fun theFirstFrameIsSimplyWhereItBelongs() {
        assertEquals(MotionChannel.entries.toSet(), MotionPlan.of(null, rest).filterValues { it == MotionAction.SNAP }.keys)
    }

    @Test fun onlyTheChannelsWhoseTargetChangedMove() {
        val opened = rest.copy(height = 1f, width = 1f, opening = true)
        val plan = MotionPlan.of(rest, opened)
        assertEquals(MotionAction.ANIMATE, plan[MotionChannel.HEIGHT]); assertEquals(MotionAction.ANIMATE, plan[MotionChannel.WIDTH])
        // An open island does not disturb the event width, the ring, the edges or the press, which keep doing whatever they were doing.
        listOf(MotionChannel.EVENT, MotionChannel.COMPACT, MotionChannel.EDGE_LEFT, MotionChannel.EDGE_RIGHT, MotionChannel.PRESS_X, MotionChannel.PRESS_Y)
            .forEach { assertEquals("$it", MotionAction.KEEP, plan[it]) }
        // An event widening it moves only that.
        assertEquals(setOf(MotionChannel.EVENT), MotionPlan.of(rest, rest.copy(eventWidthDp = 60f)).filterValues { it != MotionAction.KEEP }.keys)
    }

    @Test fun aFingerDrivesTheMorphExactlyAndLettingGoSpringsFromThere() {
        val pulling = rest.copy(height = .3f, width = .75f, fingerDriven = true)
        assertEquals(MotionAction.SNAP, MotionPlan.of(rest, pulling)[MotionChannel.HEIGHT])
        assertEquals(MotionAction.SNAP, MotionPlan.of(pulling, pulling.copy(height = .35f))[MotionChannel.HEIGHT])
        // Released back towards where it started: the target is the old one, but the island is part-way, so it springs.
        assertEquals(MotionAction.ANIMATE, MotionPlan.of(pulling, rest)[MotionChannel.HEIGHT])
        assertEquals(MotionAction.ANIMATE, MotionPlan.of(pulling, rest)[MotionChannel.WIDTH])
    }

    @Test fun aNewScreenJumpsTheSizeDependentValuesInsteadOfFlyingThem() {
        val plan = MotionPlan.of(rest, rest.copy(screen = "rotated", edgeLeft = -30f, eventWidthDp = 10f))
        assertEquals(MotionAction.SNAP, plan[MotionChannel.EDGE_LEFT]); assertEquals(MotionAction.SNAP, plan[MotionChannel.EVENT])
        assertEquals(MotionAction.SNAP, plan[MotionChannel.EDGE_RIGHT])
    }

    @Test fun withAnimationsOffEverythingThatChangesSnaps() {
        val off = rest.copy(animate = false)
        val plan = MotionPlan.of(off, off.copy(height = 1f, width = 1f, opening = true, eventWidthDp = 40f))
        assertEquals(MotionAction.SNAP, plan[MotionChannel.HEIGHT]); assertEquals(MotionAction.SNAP, plan[MotionChannel.EVENT])
        assertEquals(MotionAction.KEEP, plan[MotionChannel.EDGE_LEFT])
    }
}
