package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class UnoFeedbackTest {
    @Test fun soundsStayOffUnlessAskedAndTicksNeverMakeNoise() {
        assertEquals(true to false, UnoFeedback.plays(Cue.PAGE, haptics = true, sounds = false))
        assertEquals(true to true, UnoFeedback.plays(Cue.PAGE, haptics = true, sounds = true))
        assertEquals(true to false, UnoFeedback.plays(Cue.TICK, haptics = true, sounds = true))
    }

    @Test fun switchingHapticsOffSilencesEveryFeel() {
        Cue.entries.forEach { assertFalse("$it", UnoFeedback.plays(it, haptics = false, sounds = true).first) }
    }

    @Test fun everythingOffIsSilent() {
        Cue.entries.forEach { assertEquals(false to false, UnoFeedback.plays(it, false, false)) }
    }
}
