package com.jake.duolauncher

import com.jake.duolauncher.appearance.DuneGlass
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuneGlassTest {
    private fun r(c: Int) = (c shr 16) and 255
    private fun g(c: Int) = (c shr 8) and 255
    private fun b(c: Int) = c and 255
    private fun luma(c: Int) = .299 * r(c) + .587 * g(c) + .114 * b(c)

    @Test fun rendersTheRequestedSizeFullyOpaque() {
        val px = DuneGlass.render(120, 260, dark = false)
        assertEquals(120 * 260, px.size)
        assertTrue(px.all { (it ushr 24) == 255 })
    }

    @Test fun isDeterministic() {
        assertArrayEquals(DuneGlass.render(90, 200, true), DuneGlass.render(90, 200, true))
    }

    @Test fun dayIsBrighterThanNightAndTheyDiffer() {
        val d = DuneGlass.render(100, 220, false); val n = DuneGlass.render(100, 220, true)
        assertTrue(d.map(::luma).average() > n.map(::luma).average() + 25)
    }

    @Test fun theSkyIsCoolAndTheNearDunesAreWarmByDay() {
        val w = 100; val h = 220
        val px = DuneGlass.render(w, h, false)
        val sky = px[(h * 0.08).toInt() * w + w / 2]
        assertTrue("sky is bluer than red", b(sky) > r(sky))
        val ground = px[(h * 0.97).toInt() * w + w / 3]
        assertTrue("near dunes are warmer than cool", r(ground) > b(ground))
    }

    @Test fun nightSkyIsDarkAtTheTop() {
        val px = DuneGlass.render(100, 220, true)
        assertTrue(luma(px[5 * 100 + 50]) < 40)
    }

    @Test fun hasRealStructureNotAFlatFill() {
        // The lit and shaded faces and the haze mean the picture varies a good deal across the dune area.
        val w = 120; val h = 260
        val px = DuneGlass.render(w, h, false)
        val area = (h * .45).toInt() until h
        val values = area.flatMap { y -> (0 until w).map { x -> luma(px[y * w + x]) } }
        val mean = values.average()
        val sd = Math.sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
        assertTrue("std dev $sd", sd > 14.0)
    }

    @Test fun duneProfileStaysInRangeAndIsAsymmetric() {
        val values = (0..400).map { DuneGlass.profile(it / 400f, 1f, 0f, .38f) }
        assertTrue(values.all { it in -1.0001f..1.0001f })
        val peak = values.indices.maxByOrNull { values[it] }!!
        assertTrue("the crest is at the lean point, not the middle", peak in 130..175)
    }

    @Test fun crestsStayInsideTheScreenForEveryLayer() {
        for (i in 0 until DuneGlass.layers) for (x in listOf(0f, .25f, .5f, .75f, 1f)) {
            val c = DuneGlass.crestFraction(i, x)
            assertTrue("layer $i at $x -> $c", c > .25f && c < 1.1f)
        }
    }

    @Test fun rendersQuicklyAtTheCachedSize() {
        val start = System.nanoTime()
        DuneGlass.render(720, 1560, false)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("took $ms ms", ms < 6000)
    }

    @Test fun survivesTinyAndOddSizes() {
        for ((w, h) in listOf(1 to 1, 7 to 400, 400 to 7, 16 to 16, 2208 / 8 to 1840 / 8)) {
            val px = DuneGlass.render(w, h, false)
            assertEquals(maxOf(w, 8) * maxOf(h, 8), px.size)
        }
    }
}
