package com.jake.duolauncher

import com.jake.duolauncher.appearance.*
import org.junit.Assert.*
import org.junit.Test

class SharedWallpaperMapTest {
    private fun point(p: SharedWallpaperMap.Place, x: Float, y: Float) = (x * p.scale + p.translateX) to (y * p.scale + p.translateY)

    @Test fun withNoDriftThePictureLiesWhereTheWallpaperDoes() {
        val p = SharedWallpaperMap.place(360, 1080, 2400, 1f, 0f, 100f, 200f)
        assertEquals(3f, p.scale, 0f)
        // The picture's pixel at the surface's top left, in the surface's own coordinates, is its origin.
        val (x, y) = point(p, 100f / 3f, 200f / 3f)
        assertEquals(0f, x, .001f); assertEquals(0f, y, .001f)
    }

    @Test fun driftScalesAboutTheWallpapersCentreAndMovesIt() {
        val p = SharedWallpaperMap.place(360, 1080, 2400, 1.09f, 20f, 0f, 0f)
        val (cx, cy) = point(p, 180f, 400f * 3f / 3f)
        assertEquals(540f + 20f, cx, .01f)
        assertEquals(1200f, cy, .01f)
    }

    @Test fun theBoxRadiusMatchesTheLiveBlurAndIsNeverZero() {
        // Clear glass, 2 dp at 2.625 on a third-size picture: sigma about 1.2, which two passes of radius 1 give.
        assertEquals(1, SharedWallpaperMap.boxRadius(2f, 2.625f, 3))
        assertEquals(1, SharedWallpaperMap.boxRadius(.1f, 1f, 3))
        assertTrue(SharedWallpaperMap.boxRadius(14f, 2.625f, 3) > 3)
    }
}
