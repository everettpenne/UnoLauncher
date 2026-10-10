package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class FrameStatsTest {
    private fun feed(stats: FrameStats, intervalsMs: List<Float>) {
        var t = 1_000_000_000L; stats.record(t)
        intervalsMs.forEach { t += (it * 1_000_000f).toLong(); stats.record(t) }
    }

    @Test fun steadySixtyFramesAreSixtyFpsWithNoJank() {
        val s = FrameStats(); feed(s, List(60) { 16.67f })
        assertEquals(60f, s.fps, 1f); assertEquals(16.67f, s.averageMs, .05f); assertEquals(16.67f, s.worstMs, .05f); assertEquals(0, s.jankPercent)
    }

    @Test fun slowFramesCountAsJankAndTheWorstIsReported() {
        val s = FrameStats(); feed(s, List(8) { 16.7f } + listOf(60f, 40f))
        assertEquals(20, s.jankPercent); assertEquals(60f, s.worstMs, .01f); assertEquals(10, s.samples)
    }

    @Test fun anIdleGapIsNotASlowFrame() {
        val s = FrameStats(); feed(s, listOf(16f, 16f, 5_000f, 16f))
        assertEquals(3, s.samples); assertEquals(0, s.jankPercent)
    }

    @Test fun onlyTheLatestSamplesAreKeptAndResetClears() {
        val s = FrameStats(capacity = 4); feed(s, listOf(100f, 100f, 100f, 100f, 10f, 10f, 10f, 10f))
        assertEquals(4, s.samples); assertEquals(10f, s.averageMs, .01f)
        s.reset(); assertEquals(0, s.samples); assertEquals(0f, s.fps, 0f)
    }

    @Test fun theHudLinesNameTheWindowsAndTheFullScreenState() {
        val snap = HudSnapshot(1080, 2424, "509,40-571,102", "94x94 @493,24", "935x881 @73,24", "126x126 @477,8", false, false, true, false, 2, 1)
        val text = snap.lines(FrameStats()).joinToString("\n")
        assertTrue(text.contains("1080x2424")); assertTrue(text.contains("935x881")); assertTrue(text.contains("bars=hidden"))
        assertTrue(text.contains("fullScreenHidden=true")); assertTrue(text.contains("queued=1"))
    }
}
