package com.jake.duolauncher

import android.app.NotificationManager
import android.media.AudioManager
import org.junit.Assert.*
import org.junit.Test

class IslandActivitiesTest {
    @Test fun ringerModesGetTheirOwnWordingAndSymbol() {
        assertEquals(IslandEvent("Silent", IslandSymbol.SILENT), IslandEvents.ringer(AudioManager.RINGER_MODE_SILENT))
        assertEquals(IslandEvent("Vibrate", IslandSymbol.VIBRATE), IslandEvents.ringer(AudioManager.RINGER_MODE_VIBRATE))
        assertEquals(IslandEvent("Ringer", IslandSymbol.RINGER), IslandEvents.ringer(AudioManager.RINGER_MODE_NORMAL))
    }

    @Test fun airplaneAndChargingTitlesReadNaturally() {
        assertEquals("Airplane", IslandEvents.airplane(true).title)
        assertEquals("Airplane off", IslandEvents.airplane(false).title)
        assertEquals("Charging 84%", IslandEvents.charging(84).title)
        assertEquals("unknown level still reads", "Charging", IslandEvents.charging(null).title)
        assertEquals(IslandSymbol.CHARGING, IslandEvents.charging(10).symbol)
    }

    @Test fun anyInterruptionFilterButAllIsDoNotDisturb() {
        listOf(NotificationManager.INTERRUPTION_FILTER_PRIORITY, NotificationManager.INTERRUPTION_FILTER_NONE,
            NotificationManager.INTERRUPTION_FILTER_ALARMS).forEach {
            assertEquals("Do Not Disturb", IslandEvents.doNotDisturb(it).title)
        }
        assertEquals("Focus off", IslandEvents.doNotDisturb(NotificationManager.INTERRUPTION_FILTER_ALL).title)
        assertEquals("Focus off", IslandEvents.doNotDisturb(NotificationManager.INTERRUPTION_FILTER_UNKNOWN).title)
        assertEquals(IslandSymbol.FOCUS, IslandEvents.doNotDisturb(NotificationManager.INTERRUPTION_FILTER_NONE).symbol)
    }

    @Test fun controlsShowWhilePlayingAndForAWhileAfter() {
        val stopped = 1_000_000L
        assertTrue(IslandPlayback.controlsVisible(stopped, playing = true, lastPlayingAtMs = 0L))
        assertTrue("just after stopping", IslandPlayback.controlsVisible(stopped + 1_000, false, stopped))
        assertTrue("at the end of the window",
            IslandPlayback.controlsVisible(stopped + IslandPlayback.RESUME_WINDOW_MS, false, stopped))
        assertFalse("after the window", IslandPlayback.controlsVisible(stopped + IslandPlayback.RESUME_WINDOW_MS + 1, false, stopped))
        assertFalse("never played", IslandPlayback.controlsVisible(stopped, false, 0L))
    }

    @Test fun theResumeWindowStartsWhenPlaybackStopsNotWhenItStarted() {
        val state = IslandState()
        state.setPlaying(1_000L, true)
        state.setPlaying(60_000L, true)
        state.setPlaying(100_000L, false)
        assertFalse(state.playing)
        assertEquals("the stop time anchors the window", 100_000L, state.lastPlayingAt)
        // A repeated "stopped" report must not extend the window.
        state.setPlaying(150_000L, false)
        assertEquals(100_000L, state.lastPlayingAt)
    }

    @Test fun repeatedEventsBumpTheFlashKeySoTheyPulseAgain() {
        val state = IslandState()
        state.showEvent(IslandEvents.ringer(AudioManager.RINGER_MODE_SILENT))
        val first = state.flashKey
        state.showEvent(IslandEvents.ringer(AudioManager.RINGER_MODE_SILENT))
        assertTrue(state.flashKey > first)
        assertEquals("Silent", state.flashTitle)
        state.dismissFlash()
        assertNull(state.flashTitle); assertNull(state.flashSymbol)
    }

    @Test fun chargingDoesNotInterruptAnEventAlreadyShowing() {
        val state = IslandState()
        state.showEvent(IslandEvents.airplane(true))
        state.showCharging(50)
        assertEquals("Airplane", state.flashTitle)
    }

    @Test fun aRingerChangeRightAfterDoNotDisturbIsItsSideEffectAndIsDropped() {
        val state = IslandState()
        state.showEvent(IslandEvents.doNotDisturb(NotificationManager.INTERRUPTION_FILTER_NONE), nowMs = 10_000L)
        state.showEvent(IslandEvents.ringer(AudioManager.RINGER_MODE_SILENT), nowMs = 10_400L)
        assertEquals("Do Not Disturb", state.flashTitle)
        // A real ringer change well afterwards still shows.
        state.showEvent(IslandEvents.ringer(AudioManager.RINGER_MODE_VIBRATE), nowMs = 10_000L + FOCUS_SIDE_EFFECT_MS + 1)
        assertEquals("Vibrate", state.flashTitle)
    }

    @Test fun aRingerChangeBeforeDoNotDisturbDoesNotBlockIt() {
        val state = IslandState()
        state.showEvent(IslandEvents.ringer(AudioManager.RINGER_MODE_SILENT), nowMs = 5_000L)
        state.showEvent(IslandEvents.doNotDisturb(NotificationManager.INTERRUPTION_FILTER_NONE), nowMs = 5_100L)
        assertEquals("Do Not Disturb", state.flashTitle)
    }
}
