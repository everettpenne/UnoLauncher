package com.jake.duolauncher

import com.jake.duolauncher.island.SensorLog
import org.junit.Assert.*
import org.junit.Test

class SensorLogTest {
    @Test fun aUseIsListedWhenItEndsWithItsLength() {
        val started = SensorLog.step(emptyMap(), setOf(SensorLog.CAMERA), 10_000L)
        assertEquals(mapOf(SensorLog.CAMERA to 10_000L), started.open)
        assertTrue(started.finished.isEmpty())
        val ended = SensorLog.step(started.open, emptySet(), 80_000L)
        assertEquals(listOf(SensorLog.Entry(SensorLog.CAMERA, 10_000L, 70_000L)), ended.finished)
        assertTrue(ended.open.isEmpty())
    }

    @Test fun twoKindsAreTrackedSeparately() {
        val a = SensorLog.step(emptyMap(), setOf(SensorLog.MIC), 1_000L)
        val b = SensorLog.step(a.open, setOf(SensorLog.MIC, SensorLog.CAMERA), 5_000L)
        assertEquals(1_000L, b.open[SensorLog.MIC]); assertEquals(5_000L, b.open[SensorLog.CAMERA])
        val c = SensorLog.step(b.open, setOf(SensorLog.CAMERA), 9_000L)
        assertEquals(listOf(SensorLog.Entry(SensorLog.MIC, 1_000L, 8_000L)), c.finished)
    }

    @Test fun aBlinkIsNotWorthListing() {
        val a = SensorLog.step(emptyMap(), setOf(SensorLog.CAMERA), 0L)
        assertTrue(SensorLog.step(a.open, emptySet(), 800L).finished.isEmpty())
    }

    @Test fun theLogKeepsTheNewestAndStopsAtItsCap() {
        val old = (1..5).map { SensorLog.Entry(SensorLog.MIC, it * 1000L, 2000L) }.reversed()
        val added = listOf(SensorLog.Entry(SensorLog.CAMERA, 99_000L, 3000L))
        val all = SensorLog.append(old, added, cap = 4)
        assertEquals(4, all.size); assertEquals(SensorLog.CAMERA, all.first().kind)
    }

    @Test fun encodingRoundTripsAndSkipsDamagedLines() {
        val e = listOf(SensorLog.Entry(SensorLog.SCREEN, 1L, 2L), SensorLog.Entry(SensorLog.MIC, 3L, 4L))
        assertEquals(e, SensorLog.decode(SensorLog.encode(e)))
        assertEquals(listOf(SensorLog.Entry(SensorLog.MIC, 3L, 4L)), SensorLog.decode("junk\nbogus|1|2\nmic|3|4\nmic|x|4"))
        assertTrue(SensorLog.decode(null).isEmpty())
    }

    @Test fun lengthsReadInTheFewestWords() {
        assertEquals("45 s", SensorLog.length(45_000L)); assertEquals("2 min", SensorLog.length(120_000L)); assertEquals("1 min 20 s", SensorLog.length(80_000L))
    }
}
