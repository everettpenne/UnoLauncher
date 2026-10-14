package com.jake.duolauncher

import com.jake.duolauncher.appearance.WallpaperDepth
import org.junit.Assert.*
import org.junit.Test

class WallpaperDepthTest {
    private val width = 1080f

    @Test fun theMiddlePageShowsTheWallpaperUnshifted() {
        assertEquals(0f, WallpaperDepth.drift(1f, 3, width).translationX, .001f)
        assertEquals(0f, WallpaperDepth.drift(2f, 5, width).translationX, .001f)
    }

    @Test fun theWallpaperSlidesTheOppositeWayToTheScroll() {
        val first = WallpaperDepth.drift(0f, 3, width).translationX
        val last = WallpaperDepth.drift(2f, 3, width).translationX
        assertTrue("on the first page it is shifted right", first > 0f)
        assertEquals("mirrored on the last", -first, last, .001f)
        val between = WallpaperDepth.drift(.5f, 3, width).translationX
        assertTrue("moving smoothly between pages", between < first && between > WallpaperDepth.drift(1f, 3, width).translationX)
    }

    @Test fun neverShowsAnEdgeWhateverThePageCount() {
        for (pages in listOf(2, 3, 5, 12, 40)) for (i in 0..pages * 4) {
            val d = WallpaperDepth.drift(i / 4f, pages, width)
            val coverage = (d.scale - 1f) / 2f * width
            assertTrue("pages=$pages position=${i / 4f}", kotlin.math.abs(d.translationX) <= coverage + .01f)
        }
        // A long run of pages moves less per page, so the whole range still fits.
        val few = WallpaperDepth.drift(0f, 3, width).translationX - WallpaperDepth.drift(1f, 3, width).translationX
        val many = WallpaperDepth.drift(0f, 40, width).translationX - WallpaperDepth.drift(1f, 40, width).translationX
        assertTrue(many < few)
    }

    @Test fun oneOrNoPagesAndPositionsOutsideTheRangeAreSafe() {
        assertEquals(0f, WallpaperDepth.drift(0f, 1, width).translationX, 0f)
        assertEquals(0f, WallpaperDepth.drift(3f, 0, width).translationX, 0f)
        assertEquals(WallpaperDepth.drift(2f, 3, width).translationX, WallpaperDepth.drift(9f, 3, width).translationX, .001f)
        assertEquals(WallpaperDepth.drift(0f, 3, width).translationX, WallpaperDepth.drift(-4f, 3, width).translationX, .001f)
        assertEquals(1f + 2f * WallpaperDepth.OVERSCAN, WallpaperDepth.drift(1f, 3, 0f).scale, 0f)
    }
}
