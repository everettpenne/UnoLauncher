package com.jake.duolauncher

import com.jake.duolauncher.appearance.DuneBloom
import com.jake.duolauncher.appearance.DuneDeep
import com.jake.duolauncher.appearance.DuneRange
import com.jake.duolauncher.appearance.DuneFlow
import com.jake.duolauncher.appearance.SceneMath
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuneScenesTest {
    private fun r(c: Int) = (c shr 16) and 255
    private fun g(c: Int) = (c shr 8) and 255
    private fun b(c: Int) = c and 255
    private fun luma(c: Int) = .299 * r(c) + .587 * g(c) + .114 * b(c)
    private fun sd(values: List<Double>): Double { val m = values.average(); return Math.sqrt(values.sumOf { (it - m) * (it - m) } / values.size) }

    private val scenes: List<Pair<String, (Int, Int, Boolean) -> IntArray>> =
        listOf("flow" to DuneFlow::render, "bloom" to DuneBloom::render, "deep" to DuneDeep::render, "range" to DuneRange::render)

    @Test fun everySceneRendersTheRequestedSizeOpaqueAndDeterministic() {
        for ((name, render) in scenes) for (dark in listOf(false, true)) {
            val a = render(120, 260, dark)
            assertEquals("$name size", 120 * 260, a.size)
            assertTrue("$name opaque", a.all { (it ushr 24) == 255 })
            assertArrayEquals("$name deterministic", a, render(120, 260, dark))
        }
    }

    @Test fun dayAndNightAreDifferentPictures() {
        for ((name, render) in scenes) {
            val d = render(100, 220, false); val n = render(100, 220, true)
            assertTrue("$name day brighter", d.map(::luma).average() > n.map(::luma).average() + 20)
        }
    }

    @Test fun everySceneHasRealStructureAndNoise() {
        for ((name, render) in scenes) {
            val px = render(120, 260, true)
            assertTrue("$name varies across the picture", sd(px.map(::luma)) > 20.0)
        }
    }

    @Test fun flowNightIsDarkAtTheTopAndWarmAlongARidge() {
        val w = 120; val h = 260
        val px = DuneFlow.render(w, h, true)
        assertTrue("black sky at the top", luma(px[3 * w + 60]) < 40)
        val warm = px.count { r(it) > 180 && r(it) > b(it) + 60 }
        assertTrue("a ribbon of orange light exists ($warm)", warm > 200)
        val blue = px.count { b(it) > 140 && b(it) > r(it) + 60 }
        assertTrue("and electric blue ($blue)", blue > 200)
    }

    @Test fun flowCrestsStayOnScreenAndDoNotCross() {
        for (x in listOf(0f, .25f, .5f, .75f, 1f)) {
            var previous = -1f
            for (i in 0 until DuneFlow.layers) {
                val c = DuneFlow.crestFraction(i, x)
                assertTrue("layer $i at $x -> $c", c > .02f && c < 1.1f)
                assertTrue("ridge $i is below ridge ${i - 1} at $x", c > previous)
                previous = c
            }
        }
    }

    @Test fun bloomHasASkyAboveSoftDomesAndGreenNearTheBottom() {
        val w = 120; val h = 260
        val px = DuneBloom.render(w, h, false)
        val sky = px[(h * .05).toInt() * w + 60]
        assertTrue("cobalt sky", b(sky) > r(sky) + 40)
        val pink = px.count { r(it) > 200 && g(it) < 160 && b(it) > 120 }
        assertTrue("the pink dome is there ($pink)", pink > 150)
        val green = px.count { g(it) > r(it) + 40 && g(it) > b(it) - 20 }
        assertTrue("and green ($green)", green > 150)
    }

    @Test fun mathHelpersBehave() {
        val out = FloatArray(3)
        SceneMath.ramp(.5f, floatArrayOf(0f, 1f), intArrayOf(0x000000, 0xFFFFFF), out)
        assertEquals(.5f, out[0], .01f)
        SceneMath.ramp(-3f, floatArrayOf(0f, 1f), intArrayOf(0x102030, 0xFFFFFF), out)
        assertEquals(0x10 / 255f, out[0], .01f)
        assertEquals(0f, SceneMath.smooth(-1f), 0f); assertEquals(1f, SceneMath.smooth(2f), 0f)
        assertEquals(0xFF000000.toInt(), SceneMath.pack(-1f, -1f, -1f))
        assertEquals(0xFFFFFFFF.toInt(), SceneMath.pack(2f, 2f, 2f))
        val g1 = SceneMath.grain(5, 9, 1); assertEquals(g1, SceneMath.grain(5, 9, 1), 0f)
        val samples = (0 until 4000).map { SceneMath.grain(it, it * 7, 2).toDouble() }
        assertTrue("mean near 0 (${samples.average()})", Math.abs(samples.average()) < .08)
        assertTrue("spread about 1 (${sd(samples)})", sd(samples) in .7..1.3)
    }

    @Test fun survivesTinyAndOddSizes() {
        for ((_, render) in scenes) for ((w, h) in listOf(1 to 1, 7 to 400, 400 to 7, 16 to 16)) {
            assertEquals(maxOf(w, 8) * maxOf(h, 8), render(w, h, false).size)
        }
    }

    @Test fun renderQuicklyAtTheCachedSize() {
        for ((name, render) in scenes) {
            val t = System.nanoTime(); render(720, 1560, false)
            val ms = (System.nanoTime() - t) / 1_000_000
            assertTrue("$name took $ms ms", ms < 8000)
        }
    }

    @Test fun deepDunesKeepTheOriginalsGeometryToThePixel() {
        // The original's three dunes: they start at .57, .72 and .85 of the height and end at start - swing * .35 on the right.
        val starts = floatArrayOf(.57f, .72f, .85f); val swings = floatArrayOf(.17f, .12f, .19f)
        for (i in 0 until DuneDeep.layers) {
            assertEquals("left end of dune $i", starts[i], DuneDeep.crestFraction(i, 0f), 1e-3f)
            assertEquals("right end of dune $i", starts[i] - swings[i] * .35f, DuneDeep.crestFraction(i, 1f), 1e-3f)
        }
    }

    @Test fun deepDunesAreNestedAndOnScreen() {
        for (x in listOf(0f, .2f, .5f, .8f, 1f)) {
            var previous = 0f
            for (i in 0 until DuneDeep.layers) { val c = DuneDeep.crestFraction(i, x); assertTrue("dune $i at $x = $c", c > previous && c < 1.05f); previous = c }
        }
    }

    @Test fun rangeNightIsADarkSkyWithStarsAndARoseHorizon() {
        val w = 360; val h = 780
        val px = DuneRange.render(w, h, true)
        assertTrue("dark at the very top", luma(px[4 * w + 180]) < 50)
        val stars = (0 until (h * .4).toInt()).sumOf { y -> (0 until w).count { x -> luma(px[y * w + x]) > 110 } }
        assertTrue("some stars ($stars)", stars in 8..4000)
        val horizon = px[(h * .50).toInt() * w + 20]
        assertTrue("a warm, rose-leaning horizon (${r(horizon)},${g(horizon)},${b(horizon)})", r(horizon) >= g(horizon) - 4 && luma(horizon) > 45)
    }

    @Test fun rangeHasDistantRangesBehindTheDunes() {
        val w = 360; val h = 780
        val px = DuneRange.render(w, h, false)
        val row = (h * .49).toInt()
        val values = (0 until w).map { luma(px[row * w + it]) }
        val mean = values.average(); val sd = Math.sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
        assertTrue("the range band varies ($sd)", sd > 5.0)
    }

    @Test fun rangeHasBroadSlopesNotTheOriginalsThreeCurves() {
        // Its dunes are not the original's: at the left edge they start somewhere else.
        val originals = floatArrayOf(.57f, .72f, .85f)
        val differs = (0 until DuneRange.layers).any { kotlin.math.abs(DuneRange.crestFraction(it, 0f) - originals[it]) > .03f }
        assertTrue(differs)
        for (i in 0 until DuneRange.layers) for (x in listOf(0f, .3f, .6f, 1f)) {
            val c = DuneRange.crestFraction(i, x)
            assertTrue("dune $i at $x = $c", c > .5f && c < .85f)
        }
    }

    @Test fun nightScenesFadeToPureBlackAtTheTop() {
        for ((name, render) in scenes + listOf("glass" to com.jake.duolauncher.appearance.DuneGlass::render)) {
            val w = 120; val h = 400
            val px = render(w, h, true)
            val before = px.copyOf()
            SceneMath.applyTopBlack(px, w, h)
            val topRows = (h * SceneMath.BLACK_TOP * .9f).toInt()
            assertTrue("$name: the very top is pure black", (0 until topRows).all { y -> (0 until w).all { x -> (px[y * w + x] and 0xFFFFFF) == 0 } })
            // Below the fade nothing changes.
            val below = ((SceneMath.BLACK_TOP + SceneMath.BLACK_FADE) * h).toInt() + 2
            assertTrue("$name: untouched below the fade", (below until h).all { y -> (0 until w).all { x -> px[y * w + x] == before[y * w + x] } })
            // And it gets brighter, never darker, going down through the fade.
            var previous = -1.0
            for (y in 0 until below) { val avg = (0 until w).map { luma(px[y * w + it]) }.average(); assertTrue("$name row $y", avg >= previous - 6.0); previous = avg }
        }
    }

    @Test fun theBlackMaskRisesSmoothly() {
        assertEquals(0f, SceneMath.topBlackMask(0f), 0f)
        assertEquals(0f, SceneMath.topBlackMask(SceneMath.BLACK_TOP), 0f)
        assertEquals(1f, SceneMath.topBlackMask(SceneMath.BLACK_TOP + SceneMath.BLACK_FADE), 1e-4f)
        assertTrue(SceneMath.topBlackMask(.12f) in 0.2f..0.8f)
    }
}
