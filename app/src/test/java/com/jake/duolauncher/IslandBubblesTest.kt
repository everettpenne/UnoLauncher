package com.jake.duolauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class IslandBubblesTest {
    private val all = listOf(LiveKind.CAMERA, LiveKind.CALL, LiveKind.TIMER, LiveKind.UPDATE, LiveKind.MEDIA)

    @Test fun nothingPopsOutWhileThePillHasRoom() {
        assertEquals(emptyList<LiveKind>(), IslandBubbles.extras(emptyList()))
        assertEquals(emptyList<LiveKind>(), IslandBubbles.extras(all.take(2)))
    }

    @Test fun activitiesBeyondTheTwoSlotsBecomeBubblesUpToTheLimit() {
        assertEquals(listOf(LiveKind.TIMER), IslandBubbles.extras(all.take(3)))
        assertEquals(listOf(LiveKind.TIMER, LiveKind.UPDATE), IslandBubbles.extras(all))
    }

    @Test fun tappingABubbleBringsItsActivityToTheFront() {
        val shift = IslandBubbles.shiftFor(all, 1)
        assertEquals(LiveKind.UPDATE, IslandLive.rotated(all, shift).first())
        assertEquals(LiveKind.TIMER, IslandLive.rotated(all, IslandBubbles.shiftFor(all, 0)).first())
    }

    @Test fun badIndexChangesNothing() {
        assertEquals(0, IslandBubbles.shiftFor(all, 5))
        assertEquals(0, IslandBubbles.shiftFor(all.take(2), 0))
    }

    @Test fun reserveGrowsWithTheBubbles() {
        assertEquals(0f, IslandBubbles.reserveDp(0), 0f)
        assertEquals(true, IslandBubbles.reserveDp(2) > IslandBubbles.reserveDp(1))
    }
}
