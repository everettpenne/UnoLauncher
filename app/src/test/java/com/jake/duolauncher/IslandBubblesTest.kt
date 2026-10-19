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
        // Three islands at most (the pill, one on each side), so there are no further extras.
        assertEquals(emptyList<LiveKind>(), IslandBubbles.extras(all.take(3)))
        assertEquals(emptyList<LiveKind>(), IslandBubbles.extras(all))
    }

    @Test fun tappingACapsuleBringsItsActivityToTheFront() {
        // Capsule 0 is the trailing activity (all[1]), capsule 1 the first extra (all[2]).
        assertEquals(LiveKind.CALL, IslandLive.rotated(all, IslandBubbles.shiftFor(0)).first())
        assertEquals(LiveKind.TIMER, IslandLive.rotated(all, IslandBubbles.shiftFor(1)).first())
    }

    @Test fun everyCapsuleIsACircleThePillsHeight() {
        // A wide capsule would sit over the status bar's wifi, signal and battery icons; a circle covers one at most.
        val height = 30f
        assertEquals(height, IslandBubbles.widthDp(0, height), 0f)
        assertEquals(height, IslandBubbles.widthDp(1, height), 0f)
    }

    @Test fun reserveGrowsWithTheCapsules() {
        assertEquals(0f, IslandBubbles.reserveDp(0, 30f), 0f)
        assertTrue(IslandBubbles.reserveDp(2, 30f) > IslandBubbles.reserveDp(1, 30f))
        // One capsule is a circle the pill's height, plus the gap from the pill.
        assertEquals(30f + IslandBubbles.GAP_DP, IslandBubbles.reserveDp(1, 30f), 0f)
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

    @Test fun anActivityThatLeavesLingersInItsPlaceUntilItsExitIsDone() {
        val before = Lingering.merge(emptyList(), listOf(LiveKind.CALL, LiveKind.TIMER, LiveKind.MEDIA))
        // The timer ends: it stays in the list, leaving, at the place it had, and the others close ranks.
        val during = Lingering.merge(before, listOf(LiveKind.CALL, LiveKind.MEDIA))
        assertEquals(listOf(Held(LiveKind.CALL, 0, false), Held(LiveKind.MEDIA, 1, false), Held(LiveKind.TIMER, 1, true)), during)
        // Asking again changes nothing, and once the exit is done it is gone.
        assertEquals(during, Lingering.merge(during, listOf(LiveKind.CALL, LiveKind.MEDIA)))
        assertEquals(listOf(Held(LiveKind.CALL, 0, false), Held(LiveKind.MEDIA, 1, false)), Lingering.drop(during, LiveKind.TIMER))
    }

    @Test fun anActivityThatReturnsWhileLeavingStopsLeaving() {
        val leaving = Lingering.merge(Lingering.merge(emptyList(), listOf(LiveKind.CALL)), emptyList())
        assertEquals(true, leaving.single().leaving)
        assertEquals(listOf(Held(LiveKind.CALL, 0, false)), Lingering.merge(leaving, listOf(LiveKind.CALL)))
        // Dropping something that is not leaving does nothing.
        assertEquals(listOf(Held(LiveKind.CALL, 0, false)), Lingering.drop(listOf(Held(LiveKind.CALL, 0, false)), LiveKind.CALL))
    }

    @Test fun onlyAsManyCapsulesAsFitBeforeTheScreensEdge() {
        // 130 dp to the right of the pill, 38 dp circles with a 6 dp gap, 8 dp kept clear of the edge.
        assertEquals(1, IslandBubbles.capacity(130f, 38f, IslandPrivacy.widthDp(2)))
        assertEquals(1 + IslandBubbles.MAX, IslandBubbles.capacity(400f, 38f, 0f))   // never more than the right-hand capsule (three islands in all)
        assertEquals(0, IslandBubbles.capacity(20f, 38f, 0f))
        assertEquals(0, IslandBubbles.capacity(-5f, 38f, 0f))
    }

    @Test fun withThreeLiveTheSecondGoesLeftAndTheThirdRight() {
        val three = listOf(LiveKind.CALL, LiveKind.TIMER, LiveKind.MEDIA)
        val layout = IslandBubbles.layout(three)
        assertEquals(listOf(LiveKind.TIMER, LiveKind.MEDIA), layout.map { it.kind })
        assertEquals(listOf(true, false), layout.map { it.left })
        assertEquals(listOf(0, 0), layout.map { it.slot })
        // With two live, the second is a single capsule on the right; with one, none.
        assertEquals(listOf(false), IslandBubbles.layout(three.take(2)).map { it.left })
        assertEquals(emptyList<Capsule>(), IslandBubbles.layout(three.take(1)))
        // More than three live: still only the first three are shown.
        val five = listOf(LiveKind.CALL, LiveKind.TIMER, LiveKind.STOPWATCH, LiveKind.UPDATE, LiveKind.MEDIA)
        assertEquals(listOf(LiveKind.TIMER, LiveKind.STOPWATCH), IslandBubbles.layout(five).map { it.kind })
    }

    @Test fun capsulePositionsFollowTheDebugAdjustments() {
        val none = DebugGroup()
        // Right: past the dots and one gap; the next capsule is a width and another gap further.
        assertEquals(10f + IslandBubbles.GAP_DP, IslandBubbles.rightOffsetDp(0, 30f, 10f, none, none), 0f)
        assertEquals(10f + 2 * IslandBubbles.GAP_DP + 30f, IslandBubbles.rightOffsetDp(1, 30f, 10f, none, none), 0f)
        assertEquals(10f + IslandBubbles.GAP_DP + 7f, IslandBubbles.rightOffsetDp(0, 30f, 10f, DebugGroup(dx = 7f), none), 0f)
        assertEquals(IslandBubbles.GAP_DP + 5f, IslandBubbles.leftOffsetDp(DebugGroup(dx = 5f)), 0f)
        // The left island's group applies to the left capsule, the right island's to the next one, the extras' to later ones.
        val l = DebugGroup(dw = 1f); val r = DebugGroup(dw = 2f); val e = DebugGroup(dw = 3f)
        assertEquals(l, IslandBubbles.groupFor(Capsule(LiveKind.TIMER, true, 0), l, r, e))
        assertEquals(r, IslandBubbles.groupFor(Capsule(LiveKind.MEDIA, false, 0), l, r, e))
        assertEquals(e, IslandBubbles.groupFor(Capsule(LiveKind.UPDATE, false, 1), l, r, e))
    }
}
