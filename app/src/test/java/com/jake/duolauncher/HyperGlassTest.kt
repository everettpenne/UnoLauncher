package com.jake.duolauncher

import com.jake.duolauncher.appearance.HyperGlass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hyper glass icon's sums (appearance/HyperGlassIcon.kt), which the shader repeats line for line. */
class HyperGlassTest {
    private val n = 120
    /** White vertical stripes, 8 px on and 8 off, across the whole picture. */
    private val stripes = HyperGlass.Texture(n, n, IntArray(n * n) { if ((it % n) / 8 % 2 == 0) 0xFFFFFFFF.toInt() else 0 })
    private val frost = HyperGlass.frost(stripes)
    private val radius = HyperGlass.squircleField(n, n, n * .2237f)
    private val black = 0xFF000000.toInt()

    private fun icon(strength: Float, background: Int = black) = HyperGlass.render(stripes, frost, radius, strength, background)
    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF
    private fun a(c: Int) = c ushr 24

    @Test fun aPressBendsFromRestToPeakAndBackInThreeHundredMilliseconds() {
        val rest = HyperGlass.REST; val peak = HyperGlass.PRESSED
        assertTrue("the press bends past rest", peak > rest * 1.3f)
        assertEquals(rest, HyperGlass.pressStrength(0f), 1e-6f)
        assertEquals(peak, HyperGlass.pressStrength(150f), 1e-6f)
        assertEquals(rest, HyperGlass.pressStrength(300f), 1e-6f)
        assertEquals(rest, HyperGlass.pressStrength(900f), 1e-6f)
        // Up and then down, never outside the two, and eased: past half way to the peak a quarter of the way through the rise.
        var last = 0f
        for (ms in 0..150) { val s = HyperGlass.pressStrength(ms.toFloat()); assertTrue(s >= last - 1e-6f && s in rest..(peak + 1e-6f)); last = s }
        for (ms in 150..300) { val s = HyperGlass.pressStrength(ms.toFloat()); assertTrue(s <= last + 1e-6f && s >= rest - 1e-6f); last = s }
        assertTrue(HyperGlass.pressStrength(75f) > (rest + peak) / 2f)
    }

    @Test fun theCentreIsClearAndTheCornersAreOutsideTheSquircle() {
        val px = icon(HyperGlass.REST)
        // The middle rows, well inside: exactly the stripes, unbent and unfrosted.
        for (x in 50 until 70) {
            val c = px[60 * n + x]
            val want = if (x / 8 % 2 == 0) 255 else 0
            // (Not to the last digit: stripes this fine are all edge, so the blue haze that gathers round a picture's edges lies
            // faintly over the gaps between them, even here.)
            assertEquals("centre pixel $x", want.toFloat(), g(c).toFloat(), 45f)
            assertEquals(255, a(c))
        }
        // The square's corners are outside the superellipse; the middle of each side is inside.
        for ((x, y) in listOf(0 to 0, n - 1 to 0, 0 to n - 1, n - 1 to n - 1, 3 to 3)) assertEquals("corner $x,$y", 0, a(px[y * n + x]))
        for ((x, y) in listOf(60 to 1, 1 to 60, n - 2 to 60, 60 to n - 2)) assertEquals("side $x,$y", 255, a(px[y * n + x]))
    }

    @Test fun theTopEdgeCarriesAWhiteLineAtEightyPercentAndTheBottomDoesNot() {
        // On black with nothing in the picture, so the line is all there is: 80 % white, plus the little the rim's blue adds under it.
        val empty = HyperGlass.Texture(n, n, IntArray(n * n))
        val px = HyperGlass.render(empty, HyperGlass.frost(empty), radius, HyperGlass.REST, black)
        val top = px[0 * n + 60]
        assertTrue("top line red ${r(top)}", r(top) in 195..235)
        assertTrue(g(top) >= 200 && b(top) >= 215)
        // Gone two pixels further in, and never on the bottom edge, which has the blue rim instead.
        assertTrue(r(px[3 * n + 60]) < 120)
        val bottom = px[(n - 1) * n + 60]
        assertTrue("the bottom edge is blue, not white: ${r(bottom)} ${b(bottom)}", b(bottom) > r(bottom) + 30)
    }

    @Test fun thePictureBendsTowardsTheEdgeAndNotInTheMiddle() {
        val rest = icon(HyperGlass.REST); val pressed = icon(HyperGlass.PRESSED)
        // The bend moves the stripes near the edge and leaves the centre alone.
        var edgeMoved = 0; var centreMoved = 0
        for (y in 10 until 110) for (x in 10 until 110) {
            val moved = kotlin.math.abs(g(rest[y * n + x]) - g(pressed[y * n + x])) > 60
            if (!moved) continue
            if (x in 52 until 68 && y in 52 until 68) centreMoved++ else if (x < 30 || x >= 90 || y < 30 || y >= 90) edgeMoved++
        }
        assertEquals(0, centreMoved)
        assertTrue("the edge band bends ($edgeMoved)", edgeMoved > 300)
    }

    @Test fun theEdgeIsFrostedAndTheBackgroundShowsThroughThePicture() {
        // The frosted copy: 8 px stripes under a 20 px blur are an even grey, with nothing left of the stripes.
        val row = (10 until frost.width - 10).map { frost.pixels[(frost.height / 2) * frost.width + it] ushr 24 }
        assertTrue("the frost is even (${row.min()}..${row.max()})", row.max() - row.min() < 12 && row.min() in 100..155)
        // On the icon: a white stripe is full white in the middle and well on its way to that grey 4 px from the edge. The rim's blue
        // light alone would take it no lower than 240.
        val px = icon(0f)
        assertEquals(255, g(px[60 * n + 66]))
        assertTrue("pooled at the edge: ${g(px[60 * n + 4])}", g(px[60 * n + 4]) in 30..215)
        // The gaps between the stripes are the background: black, white, or any colour given.
        // (Faintly blue, not bare: stripes this fine are all edge, and the haze gathers at a picture's edges.)
        assertTrue(g(icon(0f, black)[60 * n + 59 - 59 % 16 + 12]) < 60)
        val teal = 0xFF008080.toInt()
        val gap = icon(0f, teal)[60 * n + 60 - 60 % 16 + 12]
        assertEquals(0f, r(gap).toFloat(), 45f); assertEquals(128f, g(gap).toFloat(), 45f); assertEquals(128f, b(gap).toFloat(), 60f)
    }

    /** The blue haze: where the picture's soft copy spreads past the picture itself, on any card. Round a dark shape on white it is a
     *  blue edge, never a dark one. */
    @Test fun aBlueHazeGathersRoundThePictureOnALightCardToo() {
        val white = 0xFFFFFFFF.toInt()
        // A near-black block in the middle of the card.
        val block = HyperGlass.Texture(n, n, IntArray(n * n) { val x = it % n; val y = it / n; if (x in 32 until 88 && y in 32 until 88) 0xFF101014.toInt() else 0 })
        val px = HyperGlass.render(block, HyperGlass.frost(block), radius, HyperGlass.REST, white)
        // Clear of the rim's own narrow blue light.
        var cool = 0
        for (y in 8 until 112) for (x in 8 until 112) { val c = px[y * n + x]; if (b(c) - r(c) > 30 && b(c) > 150) cool++ }
        assertTrue("blue round a dark shape on white ($cool)", cool > 40)
        // Well inside the block it is still the block, and outside it still white.
        assertTrue(g(px[60 * n + 60]) < 40)
    }

    /** Clear glass has no card: where the glass holds nothing it is see-through, the picture in it is solid, and its rim and top line
     *  are still there. */
    @Test fun clearGlassIsSeeThroughWhereItIsEmpty() {
        val dot = HyperGlass.Texture(n, n, IntArray(n * n) { val x = it % n - 60; val y = it / n - 60; if (x * x + y * y < 18 * 18) 0xFFFFFFFF.toInt() else 0 })
        val clear = (0.10f * 255).toInt() shl 24 or 0xFFFFFF
        val px = HyperGlass.render(dot, HyperGlass.frost(dot), radius, 0f, clear)
        assertEquals("the picture is solid", 255, a(px[60 * n + 60]))
        // Between the picture and the rim: only the glass's own faint tint (and a little gloss in the top half).
        assertTrue("see-through below the picture: ${a(px[92 * n + 60])}", a(px[92 * n + 60]) in 15..60)
        assertTrue("and above it: ${a(px[26 * n + 60])}", a(px[26 * n + 60]) in 15..90)
        // The rim and the top line still show.
        assertTrue("the top line: ${a(px[0 * n + 60])}", a(px[0 * n + 60]) > 180)
        assertTrue("the rim's light: ${a(px[(n - 2) * n + 60])}", a(px[(n - 2) * n + 60]) > 80)
        // Premultiplied throughout: no colour brighter than its own opacity.
        for (c in px) assertTrue(r(c) <= a(c) && g(c) <= a(c) && b(c) <= a(c))
    }

    /** An app icon that is one flat picture: its plate is the colour round its edge, and goes; the same colour inside the picture stays. */
    @Test fun aFlatIconsPlateIsClearedFromTheEdgeInwards() {
        val T = com.jake.duolauncher.appearance.HyperGlassTextures
        val w = 96
        val white = 0xFFFFFFFF.toInt(); val red = 0xFFD02020.toInt()
        fun icon() = IntArray(w * w) {
            val x = it % w - 48; val y = it / w - 48
            when {
                x * x + y * y > 44 * 44 -> 0                    // outside the round plate
                x * x + y * y < 8 * 8 -> white                   // a white dot inside the picture: the plate's colour, but cut off from it
                x * x + y * y < 26 * 26 -> red                   // the picture
                else -> white                                    // the plate
            }
        }
        val pixels = icon()
        assertTrue(T.clearPlate(pixels, w, w))
        assertEquals("the plate is gone", 0, pixels[48 * w + 12] ushr 24)
        assertEquals("the picture stays", red, pixels[48 * w + 30])
        assertEquals("and so does the plate's colour inside it", white, pixels[48 * w + 48])
        // A picture with no plate (many colours round its edge) is left alone.
        val busy = IntArray(w * w) { val x = it % w; val y = it / w; (0xFF shl 24) or ((x * 5 % 256) shl 16) or ((y * 7 % 256) shl 8) or ((x + y) * 3 % 256) }
        val before = busy.copyOf()
        assertTrue(!T.clearPlate(busy, w, w))
        assertTrue(busy.contentEquals(before))
    }

    /** The bend comes from the edge alone: past the thick rim the picture is exactly where it was, however hard the glass is pressed. */
    @Test fun theMiddleOfThePictureIsUntouchedAtAnyStrength() {
        val blocks = HyperGlass.Texture(n, n, IntArray(n * n) { if (((it % n) / 20 + (it / n) / 20) % 2 == 0) 0xFFFFFFFF.toInt() else 0 })
        val soft = HyperGlass.frost(blocks)
        val none = HyperGlass.render(blocks, soft, radius, 0f, black); val hard = HyperGlass.render(blocks, soft, radius, HyperGlass.PRESSED, black)
        val rim = (radius.depth * HyperGlass.EDGE_REACH).toInt() + 1
        var moved = 0
        for (y in rim until n - rim) for (x in rim until n - rim) assertEquals("pixel $x, $y", none[y * n + x], hard[y * n + x])
        for (y in 0 until n) for (x in 0 until n) if (kotlin.math.abs(g(none[y * n + x]) - g(hard[y * n + x])) > 60) moved++
        assertTrue("and the rim is bent ($moved)", moved > 300)
    }

    /** Any outline can be the glass's shape: measured from a drawing of it, a disc knows how far in each point is and which way its
     *  edge faces. */
    @Test fun aShapeMeasuredFromItsOutlineKnowsItsEdge() {
        val w = 96; val fine = 3; val side = w * fine
        val disc = BooleanArray(side * side) { val x = it % side + .5f - side / 2f; val y = it / side + .5f - side / 2f; x * x + y * y < (40f * fine) * (40f * fine) }
        val field = HyperGlass.maskField(disc, w, w, fine)
        val out = FloatArray(3)
        assertEquals("as deep as its radius", 40f, field.depth, 1f)
        field.read(48f + 30f, 48f, out)
        assertEquals("ten pixels in from the right", 10f, out[0], .7f)
        assertEquals("where the edge faces right", 1f, out[1], .05f); assertEquals(0f, out[2], .05f)
        field.read(48f, 48f - 35f, out)
        assertEquals(5f, out[0], .7f); assertEquals("and at the top, up", -1f, out[2], .05f)
        field.read(48f + 43f, 48f, out)
        assertEquals("outside is below nought", -3f, out[0], .7f)
        // The packed distance, as the shader unpacks it, is the same number to a twentieth of a pixel inside the rim.
        val packed = field.packedDistance()
        val i = 48 * w + 48 + 36
        val c = packed[i]
        val unpacked = ((r(c) + g(c) + b(c)) / 3f / 255f + .0013f) * (field.packedReach + HyperGlass.FIELD_MARGIN) - HyperGlass.FIELD_MARGIN
        assertEquals(field.d[i], unpacked, .06f)
    }

    /** A white logo on a coloured plate takes the plate's colour back, under each part of it; colour of its own stays. */
    @Test fun aWhiteLogoTakesBackItsPlatesColour() {
        val T = com.jake.duolauncher.appearance.HyperGlassTextures
        val white = 0xFFFFFFFF.toInt(); val orange = 0xFFFF7A33.toInt(); val blue = 0xFF19B4F2.toInt(); val purple = 0xFF7A45C4.toInt()
        val logo = intArrayOf(white, white, orange, 0)
        val under = intArrayOf(blue, purple, blue, blue)
        assertTrue(T.restoreColour(logo, under))
        assertTrue("white over blue is blue (${Integer.toHexString(logo[0])})", b(logo[0]) > 200 && r(logo[0]) < 80)
        assertTrue("white over purple is purple (${Integer.toHexString(logo[1])})", r(logo[1]) > 120 && b(logo[1]) > 200 && g(logo[1]) < 120)
        assertEquals("the logo's own orange stays", orange, logo[2])
        assertEquals("clear stays clear", 0, logo[3])
    }

    @Test fun aLogoOnAGreyPlateIsLeftAlone() {
        val T = com.jake.duolauncher.appearance.HyperGlassTextures
        val logo = intArrayOf(0xFF202020.toInt(), 0xFFFFFFFF.toInt())
        assertTrue(!T.restoreColour(logo, intArrayOf(0xFFFFFFFF.toInt(), 0xFFF0F0F0.toInt())))
        assertEquals(0xFF202020.toInt(), logo[0])
    }

    @Test fun aGivenColourIsVividEnoughToReadOnBlack() {
        val T = com.jake.duolauncher.appearance.HyperGlassTextures
        val navy = T.vivid(0xFF1A0B55.toInt())
        assertTrue("brightened (${Integer.toHexString(navy)})", maxOf(r(navy), g(navy), b(navy)) >= 229)
        assertTrue("still blue-purple", b(navy) > r(navy) && r(navy) > g(navy))
        assertEquals(0xFFFF0000.toInt(), T.vivid(0xFFFF0000.toInt()))
    }

    @Test fun aFlatIconsPlateColourIsReported() {
        val T = com.jake.duolauncher.appearance.HyperGlassTextures
        val w = 64; val plate = 0xFF2060D0.toInt()
        val pixels = IntArray(w * w) { val x = it % w - 32; val y = it / w - 32; val d = x * x + y * y; if (d > 30 * 30) 0 else if (d < 12 * 12) -1 else plate }
        val found = IntArray(1)
        assertTrue(T.clearPlate(pixels, w, w, found))
        assertEquals(plate, found[0])
    }
}
