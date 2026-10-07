package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class EdgeBlurTest {
    @Test fun bandsGetStrongerAndNarrowerTowardTheEdge() {
        val bands = EdgeBlur.bands
        assertTrue(bands.size >= 3)
        bands.zipWithNext().forEach { (a, b) ->
            assertTrue("radius grows: $a -> $b", b.radiusDp > a.radiusDp)
            assertTrue("coverage shrinks: $a -> $b", b.coverage < a.coverage)
        }
        assertEquals("the softest band spans the whole strip", 1f, bands.first().coverage, 0.0001f)
        bands.forEach { assertTrue(it.coverage in 0.1f..1f && it.radiusDp > 0f) }
    }
}
