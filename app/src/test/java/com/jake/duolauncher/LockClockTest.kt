package com.jake.duolauncher

import com.jake.duolauncher.widgets.LockClock
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class LockClockTest {
    private val at = LocalDateTime.of(2026, 10, 24, 21, 41)

    @Test fun theTimeHasNoLeadingZeroOrAmPmAsOnIos() {
        assertEquals("9:41", LockClock.time(at, is24Hour = false))
        assertEquals("21:41", LockClock.time(at, is24Hour = true))
        assertEquals("9:05", LockClock.time(LocalDateTime.of(2026, 10, 24, 9, 5), is24Hour = true))
    }

    @Test fun anUnknownSavedChoiceFallsBackToClassicWhite() {
        assertEquals(LockClock.Style.CLASSIC, LockClock.style(""))
        assertEquals(LockClock.Style.SERIF, LockClock.style("SERIF"))
        assertEquals(LockClock.Tint.WHITE, LockClock.tint("PURPLE"))
    }
}
