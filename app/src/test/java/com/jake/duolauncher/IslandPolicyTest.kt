package com.jake.duolauncher

import com.jake.duolauncher.island.IslandDrag
import com.jake.duolauncher.island.IslandPolicy
import com.jake.duolauncher.island.IslandPolicy.IdleStep
import com.jake.duolauncher.island.LiveKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the island's timing and clean-up rules as they were when they lived inline in DynamicIsland (roadmap: one owner for the island). */
class IslandPolicyTest {
    private fun step(expanded: Boolean = true, peeked: Boolean = false, holding: Boolean = false, pulling: Boolean = false,
        card: Int? = null, editing: Boolean = false, playing: Boolean = false) =
        IslandPolicy.idleStep(expanded, peeked, holding, pulling, card, editing, playing)

    @Test fun anOpenIslandWithNothingOnItClosesAfterFiveSeconds() = assertEquals(IdleStep.Collapse(5_000L), step())

    @Test fun musicKeepsItOpenLongerAndACardUsesItsAppsOwnTime() {
        assertEquals(IdleStep.Collapse(12_000L), step(playing = true))
        assertEquals(IdleStep.Collapse(8_000L), step(card = 8, playing = true))
        assertEquals(IdleStep.Collapse(30_000L), step(card = 8, editing = true))
    }

    @Test fun aPeekIsAGlanceThatEndsByItself() {
        assertEquals(IdleStep.EndPeek(IslandDrag.PEEK_HOLD_MS), step(expanded = false, peeked = true))
        assertEquals("a peek that was opened all the way follows the open rules", IdleStep.Collapse(5_000L), step(peeked = true))
    }

    @Test fun aFingerOrAPullHoldsItOpenAndAClosedIslandWaitsForNothing() {
        assertEquals(IdleStep.Stay, step(holding = true))
        assertEquals(IdleStep.Stay, step(pulling = true))
        assertEquals(IdleStep.Stay, step(expanded = false, peeked = true, pulling = true))
        assertEquals(IdleStep.Stay, step(expanded = false))
    }

    @Test fun anEndedActivityIsNoLongerPinnedToTheFront() {
        assertTrue(IslandPolicy.forgetFront(LiveKind.TIMER, listOf(LiveKind.MEDIA)))
        assertFalse(IslandPolicy.forgetFront(LiveKind.TIMER, listOf(LiveKind.TIMER, LiveKind.MEDIA)))
        assertFalse(IslandPolicy.forgetFront(null, emptyList()))
    }

    @Test fun aClosedPlayerTakesItsControlsUnlessAudioStillPlays() {
        assertTrue(IslandPolicy.endPlaybackOnClose(sessionPresent = false, audioPlaying = false))
        assertFalse(IslandPolicy.endPlaybackOnClose(sessionPresent = false, audioPlaying = true))
        assertFalse(IslandPolicy.endPlaybackOnClose(sessionPresent = true, audioPlaying = false))
    }

    @Test fun anOverdueTimerRingsOnceFromTheIsland() {
        assertTrue(IslandPolicy.timerOverdue(timerActive = true, ringing = false, nowMs = 10_000, endAtMs = 10_000))
        assertFalse(IslandPolicy.timerOverdue(timerActive = true, ringing = false, nowMs = 9_999, endAtMs = 10_000))
        assertFalse("already ringing", IslandPolicy.timerOverdue(timerActive = true, ringing = true, nowMs = 20_000, endAtMs = 10_000))
        assertFalse(IslandPolicy.timerOverdue(timerActive = false, ringing = false, nowMs = 20_000, endAtMs = 10_000))
    }

    @Test fun privacyTimeStartsWhenSomethingComesOnAndClearsWhenAllAreOff() {
        assertEquals(1234L, IslandPolicy.privacySince(true, 1234L))
        assertEquals(0L, IslandPolicy.privacySince(false, 1234L))
    }
}
