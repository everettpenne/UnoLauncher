package com.jake.duolauncher

import com.jake.duolauncher.island.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandBubblesTest {
    private val all = listOf(LiveKind.CAMERA, LiveKind.CALL, LiveKind.TIMER, LiveKind.UPDATE, LiveKind.MEDIA)

    @Test fun nothingPopsOutWhileThePillHasRoom() {
        assertEquals(emptyList<LiveKind>(), IslandBubbles.extras(emptyList()))
        assertEquals(emptyList<LiveKind>(), IslandBubbles.extras(all.take(2)))
    }

    @Test fun activitiesBeyondTheLeadingAndTrailingBecomeExtraCapsulesUpToTheLimit() {
        assertEquals(listOf(LiveKind.TIMER), IslandBubbles.extras(all.take(3)))
        assertEquals(listOf(LiveKind.TIMER, LiveKind.UPDATE), IslandBubbles.extras(all))
    }

    @Test fun tappingACapsuleBringsItsActivityToTheFront() {
        // Capsule 0 is the trailing activity (all[1]), capsule 1 the first extra (all[2]).
        assertEquals(LiveKind.CALL, IslandLive.rotated(all, IslandBubbles.shiftFor(0)).first())
        assertEquals(LiveKind.TIMER, IslandLive.rotated(all, IslandBubbles.shiftFor(1)).first())
    }

    @Test fun theTrailingCapsuleIsWiderThanAnExtraAndAnExtraIsACircle() {
        val height = 30f
        assertTrue(IslandBubbles.widthDp(0, height) > IslandBubbles.widthDp(1, height))
        assertEquals(IslandBubbles.TRAILING_WIDTH_DP, IslandBubbles.widthDp(0, height), 0f)
        // An extra capsule is a circle of the pill's height, so its glyph fits with a ring round it.
        assertEquals(height, IslandBubbles.widthDp(1, height), 0f)
    }

    @Test fun reserveGrowsWithTheCapsules() {
        assertEquals(0f, IslandBubbles.reserveDp(0, 30f), 0f)
        assertTrue(IslandBubbles.reserveDp(2, 30f) > IslandBubbles.reserveDp(1, 30f))
        // One capsule is the trailing one: its width plus the gap from the pill.
        assertEquals(IslandBubbles.TRAILING_WIDTH_DP + IslandBubbles.GAP_DP, IslandBubbles.reserveDp(1, 30f), 0f)
    }

    @Test fun aChosenActivityStaysInFrontWhileOthersComeAndGo() {
        val three = listOf(LiveKind.CALL, LiveKind.TIMER, LiveKind.MEDIA)
        assertEquals(listOf(LiveKind.MEDIA, LiveKind.CALL, LiveKind.TIMER), IslandLive.withFront(three, LiveKind.MEDIA))
        // The timer ends: media is still in front, not whichever activity a turn count would now land on.
        assertEquals(listOf(LiveKind.MEDIA, LiveKind.CALL), IslandLive.withFront(three - LiveKind.TIMER, LiveKind.MEDIA))
        // The pinned activity is gone, or nothing is pinned: the usual order.
        assertEquals(three - LiveKind.MEDIA, IslandLive.withFront(three - LiveKind.MEDIA, LiveKind.MEDIA))
        assertEquals(three, IslandLive.withFront(three, null))
    }

    @Test fun flickingVisitsEveryActivityAndWrapsRound() {
        val three = listOf(LiveKind.CALL, LiveKind.TIMER, LiveKind.MEDIA)
        var front: LiveKind? = null
        val seen = buildList { repeat(3) { front = IslandLive.nextFront(three, IslandLive.withFront(three, front).first()); add(front!!) } }
        assertEquals(listOf(LiveKind.TIMER, LiveKind.MEDIA, LiveKind.CALL), seen)
        assertEquals(null, IslandLive.nextFront(listOf(LiveKind.CALL), null))
    }
}
