package com.jake.duolauncher

import com.jake.duolauncher.widgets.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaPlayerWidgetTest {
    @Test fun fractionAdvancesWhilePlayingAndHoldsWhenPaused() {
        // A 100-second track, position 50s last updated at elapsed-time 1000ms.
        assertEquals(0.5f, MediaWidget.fraction(50_000, 1_000, 1f, playing = true, nowElapsedMs = 1_000, durationMs = 100_000)!!, 1e-4f)
        assertEquals(0.52f, MediaWidget.fraction(50_000, 1_000, 1f, playing = true, nowElapsedMs = 3_000, durationMs = 100_000)!!, 1e-4f)
        assertEquals("paused keeps the last position", 0.5f,
            MediaWidget.fraction(50_000, 1_000, 1f, playing = false, nowElapsedMs = 99_000, durationMs = 100_000)!!, 1e-4f)
    }

    @Test fun fractionRespectsPlaybackSpeedAndClamps() {
        assertEquals(0.56f, MediaWidget.fraction(50_000, 1_000, 2f, playing = true, nowElapsedMs = 4_000, durationMs = 100_000)!!, 1e-4f)
        assertEquals(1f, MediaWidget.fraction(99_000, 1_000, 1f, playing = true, nowElapsedMs = 60_000, durationMs = 100_000)!!, 1e-4f)
        assertEquals(0f, MediaWidget.fraction(-5_000, 1_000, 1f, playing = false, nowElapsedMs = 1_000, durationMs = 100_000)!!, 1e-4f)
    }

    @Test fun aPlayerWithoutADurationHasNoSeekBar() {
        assertNull(MediaWidget.fraction(50_000, 1_000, 1f, true, 1_000, 0))
        assertNull(MediaWidget.fraction(50_000, 1_000, 1f, true, 1_000, -1))
    }

    @Test fun seekTargetIsTheRightPositionAndIsBounded() {
        assertEquals(50_000L, MediaWidget.seekTarget(0.5f, 100_000L))
        assertEquals(100_000L, MediaWidget.seekTarget(2f, 100_000L))
        assertEquals(0L, MediaWidget.seekTarget(-1f, 100_000L))
    }

    @Test fun aTrackWithoutATitleStillReadsPlayingOrPaused() {
        assertEquals("Playing", MediaWidget.displayTitle(null, true))
        assertEquals("Paused", MediaWidget.displayTitle("   ", false))
        assertEquals("Title", MediaWidget.displayTitle("Title", true))
    }

    @Test fun theEmptyStateSaysWhatToTurnOn() {
        assertEquals(MediaWidget.EmptyHint.NEEDS_ACCESS, MediaWidget.emptyHint(accessConnected = false, detailsOn = true))
        assertEquals(MediaWidget.EmptyHint.NEEDS_ACCESS, MediaWidget.emptyHint(false, false))
        assertEquals(MediaWidget.EmptyHint.NEEDS_SETTING, MediaWidget.emptyHint(accessConnected = true, detailsOn = false))
        assertEquals(MediaWidget.EmptyHint.IDLE, MediaWidget.emptyHint(true, true))
    }

    @Test fun timeLabelsReadLikeAPlayer() {
        assertEquals("0:00", MediaWidget.timeLabel(0)); assertEquals("0:00", MediaWidget.timeLabel(-5))
        assertEquals("1:23", MediaWidget.timeLabel(83_000)); assertEquals("59:59", MediaWidget.timeLabel(3_599_000))
        assertEquals("1:02:03", MediaWidget.timeLabel(3_723_000))
    }
}
