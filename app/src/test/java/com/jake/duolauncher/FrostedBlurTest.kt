package com.jake.duolauncher

import com.jake.duolauncher.appearance.FrostedBlur
import org.junit.Assert.*
import org.junit.Test

class FrostedBlurTest {
    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun aFlatPictureStaysFlatEvenAtTheEdges() {
        val colour = argb(255, 40, 90, 160)
        val out = FrostedBlur.boxBlur(IntArray(12 * 9) { colour }, 12, 9, 2)
        assertTrue(out.all { it == colour })
    }

    @Test fun aBrightSpotSpreadsAndDimsWithoutChangingTheTotalMuch() {
        val w = 15; val h = 15
        val pixels = IntArray(w * h) { argb(255, 0, 0, 0) }
        pixels[7 * w + 7] = argb(255, 250, 250, 250)
        val out = FrostedBlur.boxBlur(pixels, w, h, 2)
        val centre = (out[7 * w + 7] shr 16) and 255
        assertTrue("the spot is dimmer ($centre)", centre in 1..40)
        assertTrue("and has spread to its neighbours", ((out[7 * w + 8] shr 16) and 255) > 0)
        val before = pixels.sumOf { (it shr 16) and 255 }; val after = out.sumOf { (it shr 16) and 255 }
        assertEquals(before.toDouble(), after.toDouble(), before * .1)
    }

    @Test fun theOutputIsAsBigAsTheInput() {
        val out = FrostedBlur.boxBlur(IntArray(30 * 20), 30, 20, 3)
        assertEquals(30 * 20, out.size)
    }
}

class BlurredPagesAmountTest {
    @org.junit.Test fun blurFollowsTheLibraryAndTheFeedAndNotHome() {
        // Pages: feed 0, Home 1..2, library 3.
        org.junit.Assert.assertEquals(0f, blurredPagesAmount(1f, 4, 1), 0f)
        org.junit.Assert.assertEquals(0f, blurredPagesAmount(2f, 4, 1), 0f)
        org.junit.Assert.assertEquals(.5f, blurredPagesAmount(2.5f, 4, 1), .001f)
        org.junit.Assert.assertEquals(1f, blurredPagesAmount(3f, 4, 1), 0f)
        org.junit.Assert.assertEquals(1f, blurredPagesAmount(0f, 4, 1), 0f)
        org.junit.Assert.assertEquals(.4f, blurredPagesAmount(.6f, 4, 1), .001f)
        org.junit.Assert.assertEquals(0f, blurredPagesAmount(0f, 3, 0), 0f)
    }
}
