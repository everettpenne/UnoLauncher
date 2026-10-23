package com.jake.duolauncher

import com.jake.duolauncher.island.*
import com.jake.duolauncher.search.*

import android.media.session.PlaybackState
import com.jake.duolauncher.island.IslandPlayback.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandPlaybackTest {
    private val linger = IslandPlayback.LINGER_MS
    private val window = IslandPlayback.DEFAULT_RESUME_MS
    private val t0 = 1_000_000L

    @Test fun sessionStatesThatMeanSoundIsComing() {
        assertTrue(IslandPlayback.sessionPlaying(PlaybackState.STATE_PLAYING))
        assertTrue(IslandPlayback.sessionPlaying(PlaybackState.STATE_BUFFERING))
        assertFalse(IslandPlayback.sessionPlaying(PlaybackState.STATE_PAUSED))
        assertFalse(IslandPlayback.sessionPlaying(PlaybackState.STATE_STOPPED))
        assertFalse(IslandPlayback.sessionPlaying(null))
    }

    @Test fun theSessionOutranksTheAudioSystemWhenThereIsOne() {
        assertTrue(IslandPlayback.effective(audioActive = false, sessionPlaying = true))
        assertFalse(IslandPlayback.effective(audioActive = true, sessionPlaying = false))
        assertTrue(IslandPlayback.effective(audioActive = true, sessionPlaying = null))
        assertFalse(IslandPlayback.effective(audioActive = false, sessionPlaying = null))
    }

    @Test fun aSessionThatSaysPlayingOverSilenceStopsCountingAfterAFewSeconds() {
        // A muted autoplay video: the session says playing, no audio comes out.
        assertTrue("just went quiet", IslandPlayback.sessionAudible(false, silentSinceMs = t0, nowMs = t0 + 1_000))
        assertFalse("quiet for a while", IslandPlayback.sessionAudible(false, silentSinceMs = t0, nowMs = t0 + IslandPlayback.SILENT_GRACE_MS))
        assertTrue("audio is on", IslandPlayback.sessionAudible(true, silentSinceMs = t0, nowMs = t0 + 60_000))
        assertTrue("never heard silence", IslandPlayback.sessionAudible(false, silentSinceMs = 0L, nowMs = t0))
        assertFalse(IslandPlayback.effective(false, sessionPlaying = true, sessionAudible = false))
        assertTrue(IslandPlayback.effective(true, sessionPlaying = true, sessionAudible = true))
    }

    @Test fun resumableNeedsATitleAPositionAndMoreThanThirtySeconds() {
        val paused = PlaybackState.STATE_PAUSED
        assertTrue(IslandPlayback.resumable("Episode", paused, 90_000L, 3_600_000L))
        assertFalse("no title", IslandPlayback.resumable(null, paused, 90_000L, 3_600_000L))
        assertFalse("blank title", IslandPlayback.resumable("  ", paused, 90_000L, 3_600_000L))
        assertFalse("no position", IslandPlayback.resumable("Episode", paused, -1L, 3_600_000L))
        assertFalse("at the very start", IslandPlayback.resumable("Episode", paused, 0L, 3_600_000L))
        assertFalse("thirty seconds or less", IslandPlayback.resumable("Clip", paused, 10_000L, 30_000L))
        assertFalse("unknown length", IslandPlayback.resumable("Clip", paused, 10_000L, -1L))
        assertFalse("finished: at the end", IslandPlayback.resumable("Video", paused, 119_000L, 120_000L))
        assertFalse("stopped, not paused", IslandPlayback.resumable("Episode", PlaybackState.STATE_STOPPED, 90_000L, 3_600_000L))
        assertFalse("no state", IslandPlayback.resumable("Episode", null, 90_000L, 3_600_000L))
    }

    @Test fun playingThenLingeringThenResumableThenGone() {
        assertEquals(Phase.PLAYING, IslandPlayback.phase(t0, true, t0, true))
        assertEquals(Phase.LINGER, IslandPlayback.phase(t0 + 1_000, false, t0, true))
        assertEquals(Phase.LINGER, IslandPlayback.phase(t0 + linger, false, t0, true))
        assertEquals(Phase.RESUMABLE, IslandPlayback.phase(t0 + linger + 1, false, t0, true))
        assertEquals(Phase.RESUMABLE, IslandPlayback.phase(t0 + window, false, t0, true))
        assertEquals(Phase.GONE, IslandPlayback.phase(t0 + window + 1, false, t0, true))
    }

    @Test fun finishedMediaLingersAFewSecondsAndGoes() {
        assertEquals(Phase.LINGER, IslandPlayback.phase(t0 + 2_000, false, t0, resumable = false))
        assertEquals(Phase.GONE, IslandPlayback.phase(t0 + linger + 1, false, t0, resumable = false))
        assertEquals(Phase.GONE, IslandPlayback.phase(t0, false, 0L, resumable = true))
    }

    @Test fun theWindowIsSettable() {
        assertEquals(60_000L, IslandPlayback.resumeWindowMs(1)); assertEquals(120_000L, IslandPlayback.resumeWindowMs(2))
        assertEquals(1_800_000L, IslandPlayback.resumeWindowMs(30)); assertEquals(120_000L, IslandPlayback.resumeWindowMs(7))
        assertEquals(Phase.RESUMABLE, IslandPlayback.phase(t0 + 600_000L, false, t0, true, IslandPlayback.resumeWindowMs(30)))
        assertEquals(Phase.GONE, IslandPlayback.phase(t0 + 600_000L, false, t0, true, IslandPlayback.resumeWindowMs(2)))
    }

    @Test fun theNextChangeIsTold() {
        assertNull(IslandPlayback.nextChangeMs(t0, true, t0, true))
        assertNull(IslandPlayback.nextChangeMs(t0, false, 0L, true))
        assertEquals(linger, IslandPlayback.nextChangeMs(t0, false, t0, true))
        assertEquals(window - linger - 1_000L, IslandPlayback.nextChangeMs(t0 + linger + 1_000L, false, t0, true))
        assertNull("finished media has nothing after the linger", IslandPlayback.nextChangeMs(t0 + linger, false, t0, false))
        assertNull(IslandPlayback.nextChangeMs(t0 + window, false, t0, true))
    }
}

class SpotlightEmergeTest {
    private val pill = PxRect(400f, 20f, 680f, 120f)
    private val panel = PxRect(0f, 0f, 1000f, 1600f)

    @Test fun startsAsTheIslandAndEndsAsThePanel() {
        assertEquals(pill, SpotlightEmerge.rect(0f, pill, panel))
        assertEquals(panel, SpotlightEmerge.rect(1f, pill, panel))
        val half = SpotlightEmerge.rect(.5f, pill, panel)
        assertEquals(200f, half.left, 1e-3f); assertEquals(840f, half.right, 1e-3f); assertEquals(860f, half.bottom, 1e-3f)
    }

    @Test fun anOvershootingSpringNeverCarriesItPastEitherEnd() {
        assertEquals(panel, SpotlightEmerge.rect(1.2f, pill, panel))
        assertEquals(pill, SpotlightEmerge.rect(-.1f, pill, panel))
    }

    @Test fun itKeepsTheIslandsShapeAtEverySize() {
        // A pill-sized outline is a full capsule (half its height), and the panel has the open island's capped corner: never the squashed,
        // nearly square corners the scaled panel had while it was pill-sized.
        val d = 2.625f
        assertEquals(pill.height / 2f, IslandMorph.cornerPx(pill.width, pill.height, d), 1e-3f)
        assertEquals(IslandGeometry.CORNER_CAP_DP * d, IslandMorph.cornerPx(panel.width, panel.height, d), 1e-3f)
    }

    @Test fun contentAppearsOnlyOnceThereIsRoomForIt() {
        assertEquals(0f, SpotlightEmerge.contentAlpha(.3f), 0f)
        assertEquals(1f, SpotlightEmerge.contentAlpha(1f), 1e-4f)
        assertTrue(SpotlightEmerge.contentAlpha(.7f) in 0.2f..0.8f)
    }
}
