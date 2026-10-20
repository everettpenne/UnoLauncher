package com.jake.duolauncher

import com.jake.duolauncher.appearance.WallpaperAdjust
import com.jake.duolauncher.appearance.WallpaperAdjustMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperAdjustTest {
    private val phone = 1080f / 2400f

    @Test fun aWidePhotoIsCroppedToTheScreenShapeAndCentred() {
        val c = WallpaperAdjustMath.cropRect(4000, 3000, phone, WallpaperAdjust())
        assertEquals(3000f, c.height, .01f); assertEquals(3000f * phone, c.width, .01f)
        assertEquals((4000f - c.width) / 2f, c.left, .01f); assertEquals(0f, c.top, .01f)
    }

    @Test fun panningMovesTheCropToTheEdgesAndNoFurther() {
        val left = WallpaperAdjustMath.cropRect(4000, 3000, phone, WallpaperAdjust(panX = -1f))
        val right = WallpaperAdjustMath.cropRect(4000, 3000, phone, WallpaperAdjust(panX = 5f))      // clamped to 1
        assertEquals(0f, left.left, .01f); assertEquals(4000f, right.right, .01f)
    }

    @Test fun zoomingShrinksTheCropAndKeepsItInsideThePhoto() {
        val c = WallpaperAdjustMath.cropRect(1000, 2000, phone, WallpaperAdjust(zoom = 2f, panX = 1f, panY = -1f))
        val full = WallpaperAdjustMath.cropRect(1000, 2000, phone, WallpaperAdjust())
        assertEquals(full.width / 2f, c.width, .01f)
        assertTrue(c.left >= 0f && c.top >= 0f && c.right <= 1000f + .01f && c.bottom <= 2000f + .01f)
        assertEquals(1000f, c.right, .01f); assertEquals(0f, c.top, .01f)
    }

    @Test fun adjustmentsSurviveSavingAndBadValuesFallBackSafely() {
        val a = WallpaperAdjust(1.5f, .2f, -.3f, -.4f, .5f, 1.2f, .7f)
        assertEquals(a, WallpaperAdjust.decode(a.encode()))
        assertEquals(WallpaperAdjust(), WallpaperAdjust.decode("nonsense"))
        assertEquals(WallpaperAdjust(), WallpaperAdjust.decode("1,2,3"))
        assertEquals(WallpaperAdjust.MAX_ZOOM, WallpaperAdjust.decode("99,0,0,0,0,1,0").zoom, 0f)
        assertTrue(WallpaperAdjust().isNeutral)
    }
}
