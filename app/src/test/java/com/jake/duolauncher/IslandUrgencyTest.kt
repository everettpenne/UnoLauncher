package com.jake.duolauncher

import com.jake.duolauncher.island.IslandUrgency
import com.jake.duolauncher.island.LiveKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandUrgencyTest {
    private val all = listOf(LiveKind.TIMER, LiveKind.UPDATE, LiveKind.MEDIA)

    @Test fun aTimerLeadsInItsLastMinuteOnly() {
        assertEquals(LiveKind.TIMER, IslandUrgency.urgent(all, timerRemainingMs = 45_000, turnMeters = null, updateArriving = false))
        assertNull(IslandUrgency.urgent(all, timerRemainingMs = 5 * 60_000, turnMeters = null, updateArriving = false))
    }

    @Test fun aNearTurnOrAnArrivalBringsTheLiveUpdateForward() {
        assertEquals(LiveKind.UPDATE, IslandUrgency.urgent(all, null, turnMeters = 150, updateArriving = false))
        assertNull(IslandUrgency.urgent(all, null, turnMeters = 900, updateArriving = false))
        assertEquals(LiveKind.UPDATE, IslandUrgency.urgent(all, null, null, updateArriving = true))
    }

    @Test fun aCallAlwaysLeadsAndTheTimerBeatsTheArrival() {
        assertEquals(LiveKind.CALL, IslandUrgency.urgent(all + LiveKind.CALL, 10_000, 50, true))
        assertEquals(LiveKind.TIMER, IslandUrgency.urgent(all, 10_000, 50, true))
    }

    @Test fun urgencyOverridesAFlickAndHandsBackAfter() {
        assertEquals(LiveKind.TIMER, IslandUrgency.front(chosen = LiveKind.MEDIA, urgent = LiveKind.TIMER))
        assertEquals(LiveKind.MEDIA, IslandUrgency.front(chosen = LiveKind.MEDIA, urgent = null))
    }

    @Test fun arrivingWordsAreRecognisedAndOrdinaryOnesAreNot() {
        listOf("Arriving", "Your driver is here", "1 min away", "Approaching stop").forEach { assertTrue(it, IslandUrgency.arriving(it)) }
        listOf("12 min", "On the way", "Out for delivery", null).forEach { assertFalse("$it", IslandUrgency.arriving(it)) }
    }
}
