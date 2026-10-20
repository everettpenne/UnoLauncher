package com.jake.duolauncher

import com.jake.duolauncher.island.*

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** How the island's state moves: opening and closing, events taking turns on the pill, notification cards, and what it believes about
 * playback and the privacy indicators. These pin today's behaviour, so the island can be restructured without changing it by accident. */
class IslandStateTest {
    private lateinit var state: IslandState

    @Before fun fresh() { state = IslandState() }
    @After fun clean() { }

    private fun event(title: String, symbol: IslandSymbol = IslandSymbol.VPN) = IslandEvent(title, symbol)
    private fun card(text: String? = "hi") = PeekNotification("com.example", "Example", "Title", text, null, null, 5)

    // --- opening and closing ---

    @Test fun startsClosedAndTogglesOpenAndShut() {
        assertFalse(state.expanded)
        state.toggle(); assertTrue(state.expanded)
        state.toggle(); assertFalse(state.expanded)
    }

    @Test fun collapseAlwaysEndsClosedAndClearsTheCard() {
        state.expanded = true
        state.collapse()
        assertFalse(state.expanded); assertNull(state.peekCard)
    }

    @Test fun togglingDropsAFlashAndTheCard() {
        state.showEvent(event("VPN on"))
        state.toggle()
        assertNull(state.flashTitle)
    }

    // --- events on the pill ---

    @Test fun anEventShowsAtOnce() {
        state.showEvent(event("VPN on"))
        assertEquals("VPN on", state.flashTitle); assertEquals(IslandSymbol.VPN, state.flashSymbol); assertEquals(1, state.flashKey)
    }

    @Test fun twoQuickEventsBothGetSeenInOrder() {
        state.showEvent(event("VPN on", IslandSymbol.VPN))
        state.showEvent(event("USB data connected", IslandSymbol.USB))
        assertEquals("VPN on", state.flashTitle); assertEquals(1, state.queuedFlashCount)
        state.advanceFlash()
        assertEquals("USB data connected", state.flashTitle); assertEquals(0, state.queuedFlashCount)
        state.advanceFlash()
        assertNull(state.flashTitle)
    }

    @Test fun theSameEventAgainReplacesItAndRestartsTheTimer() {
        state.showEvent(event("VPN on")); val first = state.flashKey
        state.showEvent(event("VPN on"))
        assertEquals(0, state.queuedFlashCount); assertTrue(state.flashKey > first)
    }

    @Test fun aNewerRingerOrFocusChangeReplacesTheOneOnScreen() {
        state.showEvent(event("Silent", IslandSymbol.SILENT), nowMs = 1_000_000L)
        state.showEvent(event("Vibrate", IslandSymbol.VIBRATE), nowMs = 1_000_000L + 10_000L)
        assertEquals("Vibrate", state.flashTitle); assertEquals(0, state.queuedFlashCount)
    }

    @Test fun aRingerChangeRightAfterFocusIsItsSideEffectAndIsDropped() {
        state.showEvent(event("Do Not Disturb", IslandSymbol.FOCUS), nowMs = 1_000_000L)
        state.dismissFlash()
        state.showEvent(event("Silent", IslandSymbol.SILENT), nowMs = 1_000_000L + 500L)
        assertNull(state.flashTitle)
    }

    @Test fun theQueueIsBoundedAndKeepsTheNewest() {
        state.showEvent(event("first", IslandSymbol.VPN))
        repeat(10) { state.showEvent(event("e$it", IslandSymbol.USB)) }
        assertTrue(state.queuedFlashCount <= 4)
    }

    @Test fun dismissingClearsWhatIsShownAndWhatIsWaiting() {
        state.showEvent(event("VPN on")); state.showEvent(event("USB", IslandSymbol.USB))
        state.dismissFlash()
        assertNull(state.flashTitle); assertEquals(0, state.queuedFlashCount)
    }

    // --- notification cards ---

    @Test fun aCardDropsTheIslandOpenAndShowsIt() {
        state.showPeekCard(card())
        assertTrue(state.expanded); assertEquals("Title", state.peekCard?.title); assertEquals(1, state.peekKey)
    }

    @Test fun aSecondCardRestartsTheWait() {
        state.showPeekCard(card()); state.showPeekCard(card("again"))
        assertEquals(2, state.peekKey)
    }

    @Test fun aCardDoesNotInterruptSomeoneUsingTheOpenIsland() {
        state.expanded = true
        state.showPeekCard(card())
        assertNull(state.peekCard)
    }

    @Test fun aCardClearsAFlash() {
        state.showEvent(event("VPN on"))
        state.showPeekCard(card())
        assertNull(state.flashTitle)
    }

    @Test fun collapsingDismissesTheCard() {
        state.showPeekCard(card()); state.collapse()
        assertNull(state.peekCard); assertFalse(state.expanded)
    }

    // --- playback ---

    @Test fun playbackStartRecordsWhenAndSilenceIsTimedFromTheFirstQuietAnswer() {
        state.setPlaying(1_000L, true)
        assertTrue(state.playing); assertEquals(1_000L, state.lastPlayingAt); assertEquals(0L, state.audioSilentSince)
        state.setPlaying(5_000L, false)
        assertFalse(state.playing); assertEquals(5_000L, state.lastPlayingAt); assertEquals(5_000L, state.audioSilentSince)
        state.setPlaying(6_200L, false)   // the system is asked again a moment later; the quiet began at the first answer
        assertEquals(5_000L, state.audioSilentSince); assertEquals(5_000L, state.lastPlayingAt)
        state.setPlaying(9_000L, true)
        assertEquals(0L, state.audioSilentSince)
    }

    @Test fun neverPlayedHasNoTimeline() {
        state.setPlaying(1_000L, false)
        assertEquals(0L, state.lastPlayingAt)
    }

    @Test fun startingAndStoppingBothRestartTheLingering() {
        state.notePlaybackChange(1_000L, true); assertEquals(1_000L, state.lastPlayingAt)
        state.notePlaybackChange(9_000L, false); assertEquals(9_000L, state.lastPlayingAt)
        state.notePlaybackChange(20_000L, false)   // still not playing: not a change
        assertEquals(9_000L, state.lastPlayingAt)
    }

    @Test fun endingPlaybackForgetsTheTimeline() {
        state.notePlaybackChange(1_000L, true); state.endPlayback()
        assertEquals(0L, state.lastPlayingAt)
        state.notePlaybackChange(2_000L, false)
        assertEquals(0L, state.lastPlayingAt)
    }

    // --- privacy indicators and activity order ---

    @Test fun theIndicatorsAreIndependent() {
        state.cameraActive = true; state.recordingActive = true
        assertTrue(state.cameraActive && state.recordingActive && !state.micActive)
        state.cameraActive = false
        assertTrue(state.recordingActive)
    }


    @Test fun flashOpensSomethingOnlyForANotificationWithAnAction() {
        state.showEvent(event("VPN on"))
        assertFalse(state.flashOpensSomething())
    }
}
