package com.jake.duolauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandPlaybackTest {
    private val window = IslandPlayback.RESUME_WINDOW_MS

    @Test fun sessionStatesThatMeanSoundIsComing() {
        assertTrue(IslandPlayback.sessionPlaying(android.media.session.PlaybackState.STATE_PLAYING))
        assertTrue(IslandPlayback.sessionPlaying(android.media.session.PlaybackState.STATE_BUFFERING))
        assertFalse(IslandPlayback.sessionPlaying(android.media.session.PlaybackState.STATE_PAUSED))
        assertFalse(IslandPlayback.sessionPlaying(android.media.session.PlaybackState.STATE_STOPPED))
        assertFalse(IslandPlayback.sessionPlaying(null))
    }

    @Test fun theSessionOutranksTheAudioSystemWhenThereIsOne() {
        assertTrue(IslandPlayback.effective(audioActive = false, sessionPlaying = true))
        assertFalse(IslandPlayback.effective(audioActive = true, sessionPlaying = false))
        assertTrue(IslandPlayback.effective(audioActive = true, sessionPlaying = null))
        assertFalse(IslandPlayback.effective(audioActive = false, sessionPlaying = null))
    }

    @Test fun pausedControlsLeaveExactlyWhenTheirWindowEnds() {
        val stoppedAt = 1_000_000L
        assertNull(IslandPlayback.hideAfterMs(stoppedAt, playing = true, lastPlayingAtMs = stoppedAt))
        assertNull(IslandPlayback.hideAfterMs(stoppedAt, playing = false, lastPlayingAtMs = 0L))
        assertEquals(window, IslandPlayback.hideAfterMs(stoppedAt, false, stoppedAt))
        assertEquals(window - 30_000L, IslandPlayback.hideAfterMs(stoppedAt + 30_000L, false, stoppedAt))
        assertEquals(0L, IslandPlayback.hideAfterMs(stoppedAt + window + 5_000L, false, stoppedAt))
        assertTrue(IslandPlayback.controlsVisible(stoppedAt + window, false, stoppedAt))
        assertFalse(IslandPlayback.controlsVisible(stoppedAt + window + 1, false, stoppedAt))
    }
}

class SpotlightEmergeTest {
    @Test fun startsAsThePillAndEndsAsThePanel() {
        assertEquals(.25f, SpotlightEmerge.scaleX(0f, 100f, 400f), 1e-4f)
        assertEquals(1f, SpotlightEmerge.scaleX(1f, 100f, 400f), 1e-4f)
        assertEquals(.1f, SpotlightEmerge.scaleY(0f, 50f, 500f), 1e-4f)
        assertEquals(1f, SpotlightEmerge.scaleY(1f, 50f, 500f), 1e-4f)
        assertEquals(0f, SpotlightEmerge.shiftX(1f, 300f, 540f), 1e-4f)
        assertEquals(-240f, SpotlightEmerge.shiftX(0f, 300f, 540f), 1e-4f)
    }

    @Test fun sizesAreSafeBeforeThePanelIsMeasuredAndNeverBelowAFloor() {
        assertEquals(1f, SpotlightEmerge.scaleX(0f, 100f, 0f), 0f)
        assertEquals(1f, SpotlightEmerge.scaleY(0f, 50f, 0f), 0f)
        assertEquals(.05f, SpotlightEmerge.scaleX(0f, 1f, 1000f), 1e-4f)
    }

    @Test fun contentAppearsOnlyOnceThereIsRoomForIt() {
        assertEquals(0f, SpotlightEmerge.contentAlpha(.3f), 0f)
        assertEquals(1f, SpotlightEmerge.contentAlpha(1f), 1e-4f)
        assertTrue(SpotlightEmerge.contentAlpha(.7f) in 0.2f..0.8f)
    }
}
