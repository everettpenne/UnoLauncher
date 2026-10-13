package com.jake.duolauncher

import com.jake.duolauncher.WhatsNew.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class WhatsNewTest {
    @Test fun anUpdateShowsTheNoteOnce() {
        assertEquals(Decision.SHOW, WhatsNew.decide(seen = "0.45.0-uno01", current = "0.46.0-uno01", freshInstall = false))
        assertEquals(Decision.SHOW, WhatsNew.decide(seen = null, current = "0.46.0-uno01", freshInstall = false))
        assertEquals(Decision.NOTHING, WhatsNew.decide(seen = "0.46.0-uno01", current = "0.46.0-uno01", freshInstall = false))
    }

    @Test fun aFreshInstallIsWelcomedByTheFirstRunSheetInstead() {
        assertEquals(Decision.MARK_SEEN, WhatsNew.decide(seen = null, current = "0.46.0-uno01", freshInstall = true))
    }

    @Test fun anUnknownVersionNeverShowsANote() {
        assertEquals(Decision.NOTHING, WhatsNew.decide(null, "unknown", false))
        assertEquals(Decision.NOTHING, WhatsNew.decide(null, "", false))
    }

    @Test fun aPatchReleaseDoesNotShowTheNoteAgain() {
        assertEquals("0.46", WhatsNew.noteKey("0.46.1-uno01")); assertEquals("0.46", WhatsNew.noteKey("0.46.0-uno01"))
        assertEquals(Decision.NOTHING, WhatsNew.decide(seen = "0.46.0-uno01", current = "0.46.1-uno01", freshInstall = false))
        assertEquals(Decision.SHOW, WhatsNew.decide(seen = "0.46.1-uno01", current = "0.47.0-uno01", freshInstall = false))
    }
}
