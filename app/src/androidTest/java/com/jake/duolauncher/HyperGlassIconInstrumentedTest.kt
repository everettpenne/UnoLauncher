package com.jake.duolauncher

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jake.duolauncher.appearance.HyperGlass
import com.jake.duolauncher.appearance.HyperGlassIcon
import com.jake.duolauncher.appearance.HyperGlyphs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The hyper glass icon on a real screen: that the shader compiles and draws what the plain sums say it should (the same sums make the
 * picture older phones get, so the two are held against each other), that a vector glyph ends up inside the glass, and that a press
 * bends it and lets it go. Pictures are kept in the app's files directory as hyperglass-<name>.png.
 */
@RunWith(AndroidJUnit4::class)
class HyperGlassIconInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun icons() = compose.onAllNodes(hasTestTag("hyper-glass-icon"), useUnmergedTree = true)
    private fun picture(index: Int, name: String): Bitmap {
        val bitmap = icons()[index].captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        File(context.filesDir, "hyperglass-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }
    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF

    /** How many pixels differ by more than [by] in any channel. */
    private fun differing(one: Bitmap, other: Bitmap, by: Int): Int {
        var n = 0
        for (y in 0 until minOf(one.height, other.height)) for (x in 0 until minOf(one.width, other.width)) {
            val p = one.getPixel(x, y); val q = other.getPixel(x, y)
            if (kotlin.math.abs(r(p) - r(q)) > by || kotlin.math.abs(g(p) - g(q)) > by || kotlin.math.abs(b(p) - b(q)) > by) n++
        }
        return n
    }

    @Test fun theShaderDrawsWhatTheSumsSay() {
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 33)
        // Coloured stripes and a block, so a wrong channel, a flipped axis or a missing layer would all show.
        val texture = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888).also { bitmap ->
            for (y in 0 until 240) for (x in 0 until 240) bitmap.setPixel(x, y, when {
                x in 90..150 && y in 150..200 -> 0xFF30C060.toInt()
                x / 20 % 2 == 0 -> if (y < 120) 0xFFFFFFFF.toInt() else 0xFFFF5030.toInt()
                else -> 0
            })
        }.asImageBitmap()
        compose.setContent {
            Row(Modifier.fillMaxSize().background(Color(0xFF808080)).padding(top = 120.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                HyperGlassIcon(texture, size = 80.dp, background = Color.Black, strength = .05f)
                HyperGlassIcon(texture, size = 80.dp, background = Color.Black, strength = .05f, forceFallback = true)
                HyperGlassIcon(texture, size = 80.dp, background = Color(0xFF3D5AFE), strength = .05f)
                HyperGlassIcon(texture, size = 80.dp, background = Color(0xFF3D5AFE), strength = .05f, forceFallback = true)
            }
        }
        // The pre-made pictures are made off the main thread: wait until they have something in them.
        compose.waitUntil(10_000) { val p = icons()[1].captureToImage().asAndroidBitmap(); r(p.getPixel(p.width / 2, p.height * 175 / 240)) != 0x80 && r(icons()[3].captureToImage().asAndroidBitmap().let { it.getPixel(it.width / 2, it.height * 175 / 240) }) != 0x80 }
        compose.waitForIdle()
        for ((shader, sums, name) in listOf(Triple(0, 1, "black"), Triple(2, 3, "accent"))) {
            val drawn = picture(shader, "shader-$name"); val made = picture(sums, "fallback-$name")
            assertEquals(drawn.width, made.width)
            val total = drawn.width * drawn.height
            // Not to the pixel: along a hard stripe that the rim has bent a long way, the two round a sample differently. Looked at side
            // by side they are the same picture (the difference is a faint trace along the bent stripes' edges).
            val off = differing(drawn, made, 48)
            assertTrue("$name: the shader and the sums disagree on $off of $total pixels", off < total * 5 / 100)
        }
        // And it is the picture it should be: clear in the middle (the green block, as given), and light on the top edge. (The blue rim is checked in the
        // unit tests on an empty picture, and by the comparison above.)
        val drawn = picture(0, "shader-black")
        val middle = drawn.getPixel(drawn.width / 2, drawn.height * 175 / 240)
        assertTrue("the block in the middle is its own green", kotlin.math.abs(r(middle) - 0x30) < 12 && kotlin.math.abs(g(middle) - 0xC0) < 12 && kotlin.math.abs(b(middle) - 0x60) < 12)
        val top = drawn.getPixel(drawn.width / 2, 0)
        assertTrue("a light line on the top edge (${r(top)}, ${g(top)}, ${b(top)})", r(top) > 170 && g(top) > 170 && b(top) > 170)
        // Outside the squircle the page shows.
        assertEquals(0x80, r(drawn.getPixel(1, 1)))
    }

    @Test fun aVectorGlyphIsInsideTheGlassAndAPressBendsItAndLetsItGo() {
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 33)
        var taps = 0
        compose.setContent {
            Row(Modifier.fillMaxSize().background(Color(0xFF808080)).padding(top = 120.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                HyperGlassIcon(HyperGlyphs.Camera, size = 132.dp, background = Color.Black, onClick = { taps++ })
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val rest = picture(0, "press-rest")
        // The glyph is there, white on black, and takes up the middle of the icon.
        var white = 0
        for (y in 0 until rest.height step 2) for (x in 0 until rest.width step 2) { val c = rest.getPixel(x, y); if (r(c) > 230 && g(c) > 230 && b(c) > 230) white++ }
        assertTrue("the glyph is drawn ($white)", white > rest.width * rest.height / 4 / 12)
        // Down: at 150 ms the bend is at its height, and the glyph's edges have moved.
        icons()[0].performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(150)
        val pressed = picture(0, "press-peak")
        val moved = differing(rest, pressed, 60)
        assertTrue("the press bends the picture ($moved pixels)", moved > 150)
        icons()[0].performTouchInput { up() }
        compose.mainClock.advanceTimeBy(600)
        val after = picture(0, "press-after")
        assertTrue("and it settles back (${differing(rest, after, 30)})", differing(rest, after, 30) < 20)
        assertEquals(1, taps)
        assertEquals(HyperGlass.PRESSED, HyperGlass.pressStrength(150f), 1e-6f)
        compose.mainClock.autoAdvance = true
    }

    /** An icon given less room than it asked for is the whole icon, smaller: the shader once read the picture by the size drawn, and
     *  showed only its top left corner. */
    @Test fun anIconLaidOutSmallerIsTheWholeIconSmaller() {
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 33)
        // Four quarters of four colours: the bottom right one is only there if the whole picture is.
        val texture = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888).also { bitmap ->
            for (y in 0 until 240) for (x in 0 until 240) bitmap.setPixel(x, y,
                if (y < 120) (if (x < 120) 0xFFE03030.toInt() else 0xFF30C060.toInt()) else (if (x < 120) 0xFF3060E0.toInt() else 0xFFE0C030.toInt()))
        }.asImageBitmap()
        compose.setContent {
            Row(Modifier.fillMaxSize().background(Color(0xFF808080)).padding(top = 120.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                HyperGlassIcon(texture, Modifier.requiredSize(40.dp), size = 80.dp, background = Color.Black, strength = 0f)
            }
        }
        compose.waitForIdle()
        val drawn = picture(0, "small")
        val w = drawn.width
        val full = with(androidx.compose.ui.unit.Density(context.resources.displayMetrics.density)) { 80.dp.roundToPx() }
        assertTrue("drawn at about half the size it asked for ($w of $full)", kotlin.math.abs(w - full / 2) <= 1)
        fun near(c: Int, want: Int) = kotlin.math.abs(r(c) - ((want shr 16) and 0xFF)) < 40 && kotlin.math.abs(g(c) - ((want shr 8) and 0xFF)) < 40 && kotlin.math.abs(b(c) - (want and 0xFF)) < 40
        val lo = w * 35 / 100; val hi = w * 65 / 100
        assertTrue("top left is the red quarter", near(drawn.getPixel(lo, lo), 0xE03030))
        assertTrue("top right is the green quarter", near(drawn.getPixel(hi, lo), 0x30C060))
        assertTrue("bottom left is the blue quarter", near(drawn.getPixel(lo, hi), 0x3060E0))
        assertTrue("bottom right is the yellow quarter", near(drawn.getPixel(hi, hi), 0xE0C030))
        // And its corners are still cut to the squircle at this size.
        assertEquals(0x80, r(drawn.getPixel(1, 1)))
    }

    /** Every icon shape the launcher has: the glass is cut to it, the picture's middle is untouched, and the shader draws what the sums
     *  say for that shape too. */
    @Test fun everyIconShapeIsHyperGlass() {
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 33)
        val shapes = listOf(null, AppIconShape.CIRCLE, AppIconShape.COOKIE, AppIconShape.CLOVER, AppIconShape.FLOWER, AppIconShape.SUNNY, AppIconShape.PENTAGON)
        // A green block in the middle, white bars reaching out to the edges.
        val texture = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888).also { bitmap ->
            for (y in 0 until 160) for (x in 0 until 160) bitmap.setPixel(x, y, when {
                x in 62..98 && y in 62..98 -> 0xFF30C060.toInt()
                x / 16 % 2 == 0 -> 0xFFFFFFFF.toInt()
                else -> 0
            })
        }.asImageBitmap()
        compose.setContent {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().background(Color(0xFF808080)).padding(top = 120.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(Modifier.requiredSize(420.dp, 56.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    shapes.forEach { HyperGlassIcon(texture, size = 56.dp, background = Color.Black, shape = it, strength = HyperGlass.REST) }
                }
                Row(Modifier.requiredSize(420.dp, 56.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    shapes.forEach { HyperGlassIcon(texture, size = 56.dp, background = Color.Black, shape = it, strength = HyperGlass.REST, forceFallback = true) }
                }
            }
        }
        compose.waitUntil(15_000) {
            (shapes.size until shapes.size * 2).all { i -> val p = icons()[i].captureToImage().asAndroidBitmap(); r(p.getPixel(p.width / 2, p.height / 2)) != 0x80 }
        }
        compose.waitForIdle()
        for ((i, shape) in shapes.withIndex()) {
            val name = shape?.name?.lowercase() ?: "squircle"
            val drawn = picture(i, "shape-$name"); val made = picture(i + shapes.size, "shape-$name-fallback")
            val total = drawn.width * drawn.height
            val middle = drawn.getPixel(drawn.width / 2, drawn.height / 2)
            assertTrue("$name: the middle is the picture's green", kotlin.math.abs(r(middle) - 0x30) < 14 && kotlin.math.abs(g(middle) - 0xC0) < 14)
            assertEquals("$name: its corner is outside the shape", 0x80, r(drawn.getPixel(0, 0)))
            val off = differing(drawn, made, 48)
            assertTrue("$name: the shader and the sums disagree on $off of $total pixels", off < total * 6 / 100)
            // Not the squircle's outline: something inside the squircle's is outside this shape's.
            if (shape != null) {
                var outside = 0
                val square = picture(0, "shape-squircle")
                for (y in 0 until drawn.height step 2) for (x in 0 until drawn.width step 2)
                    if (r(square.getPixel(x, y)) != 0x80 && drawn.getPixel(x, y) == 0xFF808080.toInt()) outside++
                assertTrue("$name is its own outline ($outside)", outside > 12)
            }
        }
    }
}
