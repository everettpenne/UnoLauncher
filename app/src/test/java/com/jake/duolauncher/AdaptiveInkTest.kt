package com.jake.duolauncher

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class AdaptiveInkTest {
    @Test fun luminanceMatchesTheWcagDefinitionAtTheExtremes() {
        assertEquals(0f, AdaptiveInkMath.luminance(Color.Black), 0.0001f)
        assertEquals(1f, AdaptiveInkMath.luminance(Color.White), 0.0001f)
        // Mid grey (sRGB 0x80) is about 0.216, not 0.5: luminance is linear light, not the code value.
        assertEquals(0.216f, AdaptiveInkMath.luminance(Color(0xFF808080)), 0.005f)
        assertTrue(AdaptiveInkMath.luminance(Color(0xFF00FF00)) > AdaptiveInkMath.luminance(Color(0xFFFF0000)))
    }

    @Test fun crossoverIsWhereWhiteAndDarkInkHaveEqualContrast() {
        val dark = AdaptiveInkMath.luminance(GlassInk.Dark.primary)
        val x = AdaptiveInkMath.crossover
        val whiteContrast = 1.05f / (x + .05f)
        val darkContrast = (x + .05f) / (dark + .05f)
        assertEquals(whiteContrast, darkContrast, 0.01f)
        assertTrue("crossover is a mid-dark tone, not mid-grey: $x", x in .15f..0.30f)
    }

    @Test fun brightBackdropsGetDarkInkAndDarkOnesGetWhite() {
        assertTrue(AdaptiveInkMath.useDarkInk(.8f, currentlyDark = false))
        assertFalse(AdaptiveInkMath.useDarkInk(.05f, currentlyDark = true))
        assertFalse(AdaptiveInkMath.useDarkInk(.05f, currentlyDark = false))
        assertTrue(AdaptiveInkMath.useDarkInk(.9f, currentlyDark = true))
    }

    @Test fun theDeadBandKeepsASurfaceFromFlickeringNearTheCrossover() {
        val x = AdaptiveInkMath.crossover
        // Just either side of the crossover, inside the band: whatever it was, it stays.
        listOf(x - .02f, x, x + .02f).forEach { l ->
            assertFalse("light stays light at $l", AdaptiveInkMath.useDarkInk(l, currentlyDark = false))
            assertTrue("dark stays dark at $l", AdaptiveInkMath.useDarkInk(l, currentlyDark = true))
        }
    }

    @Test fun aTintPullsTheBackdropTowardItsOwnLuminance() {
        assertEquals(.5f, AdaptiveInkMath.effective(.5f, Color.White, 0f), 0.0001f)
        assertEquals(1f, AdaptiveInkMath.effective(.0f, Color.White, 1f), 0.0001f)
        assertEquals(.505f, AdaptiveInkMath.effective(.1f, Color.White, .45f), 0.001f)
    }

    @Test fun wallpaperGridAveragesTheCellsUnderARectangle() {
        // 2 columns x 2 rows: dark top row, bright bottom row.
        val grid = WallpaperLuma(2, 2, floatArrayOf(0f, 0f, 1f, 1f))
        assertEquals(0f, grid.mean(0f, 0f, 1f, .4f), 0.0001f)
        assertEquals(1f, grid.mean(0f, .6f, 1f, 1f), 0.0001f)
        assertEquals(.5f, grid.mean(0f, 0f, 1f, 1f), 0.0001f)
        // Rectangles that stray off the screen are clamped, not an error.
        assertEquals(1f, grid.mean(-1f, 2f, 2f, 3f), 0.0001f)
    }
}
