package com.jake.duolauncher

import com.jake.duolauncher.island.FlashFit
import org.junit.Assert.*
import org.junit.Test

class FlashFitTest {
    @Test fun textThatFitsIsLeftAlone() {
        assertEquals("AirPods connected", FlashFit.fit("AirPods connected") { it.length <= 30 })
    }

    @Test fun aLongNameIsShortenedAndTheVerbKept() {
        val out = FlashFit.fit("OpenRun Pro 2 by Shokz connected") { it.length <= 26 }
        assertTrue(out, out.endsWith("… connected")); assertTrue(out.length <= 26); assertTrue(out.startsWith("OpenRun"))
    }

    @Test fun disconnectedIsKeptToo() {
        val out = FlashFit.fit("Bose QuietComfort Ultra Earbuds disconnected") { it.length <= 30 }
        assertTrue(out, out.endsWith("… disconnected")); assertTrue(out.length <= 30)
    }

    @Test fun textWithNoVerbIsCutAtItsEnd() {
        val out = FlashFit.fit("A very long app name indeed") { it.length <= 10 }
        assertTrue(out, out.endsWith("…")); assertTrue(out.length <= 10)
    }

    @Test fun nothingNarrowEnoughStillGivesAShortForm() {
        assertEquals("Bos… connected", FlashFit.fit("Bose Headphones connected") { false })
    }
}
