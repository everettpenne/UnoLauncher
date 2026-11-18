package com.jake.duolauncher

import com.jake.duolauncher.appearance.*

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class GlassLegibilityTest {
    private val defaults = GlassSettings.Default

    @Test fun aLargeSurfaceGetsTheLensTheKnobsAskFor() {
        val dock = GlassLensSize.of(defaults, shortSideDp = 100f)
        assertEquals(23.84f, dock.heightDp, .01f)
        assertEquals(40.36f, dock.amountDp, .01f)
        assertTrue(dock.chromatic)
    }

    @Test fun aSmallPillKeepsItsEdgeAnEdgeAndLosesTheFringe() {
        val pill = GlassLensSize.of(defaults, shortSideDp = 40f)
        // The band is held to under a third of the pill's height, so its middle is clear glass, not bent wallpaper.
        assertEquals(12f, pill.heightDp, .01f)
        assertTrue(pill.heightDp * 2 < 40f)
        // The bend shrinks with the band: the same lens, smaller.
        assertEquals(40.36f * 12f / 23.84f, pill.amountDp, .05f)
        assertFalse(pill.chromatic)
    }

    @Test fun theColourSplitStillFollowsItsSwitch() {
        assertFalse(GlassLensSize.of(defaults.copy(chromatic = 0f), shortSideDp = 200f).chromatic)
    }

    @Test fun anUnmeasuredSurfaceIsLeftAsTheKnobsSay() {
        assertEquals(23.84f, GlassLensSize.of(defaults, shortSideDp = 0f).heightDp, .01f)
    }

    @Test fun theVeilIsTheToneOppositeTheInk() {
        val underDark = GlassVeil.behind(GlassInk.Dark.primary)
        assertTrue(underDark.red > .95f && underDark.green > .95f)
        assertEquals(GlassVeil.UNDER_DARK_INK, underDark.alpha, .01f)
        assertEquals(Color.Black.copy(alpha = GlassVeil.UNDER_LIGHT_INK), GlassVeil.behind(GlassInk.Light.primary))
    }

    @Test fun theVeilCrossesOverWithTheInkNotInOneStep() {
        // Halfway through the ink's fade the veil is halfway too: neither the full white nor the full black wash.
        val mid = GlassVeil.behind(Color(0xFFBCBCBC))
        assertTrue(mid.red in .2f..0.9f)
    }

    @Test fun theVeilLiftsDarkInkOffAMidBrightWallpaperToReadableContrast() {
        // A saturated mid-bright backdrop (leaf green, luminance near the ink crossover) is where neither ink reaches 4.5:1 alone.
        val leaf = AdaptiveInkMath.luminance(Color(0xFF5FA02E))
        val veiled = AdaptiveInkMath.effective(leaf, Color.White, GlassVeil.UNDER_DARK_INK)
        val ink = AdaptiveInkMath.luminance(GlassInk.Dark.primary)
        fun contrast(a: Float, b: Float) = (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
        assertTrue(contrast(leaf, ink) < contrast(veiled, ink))
        assertTrue("veiled contrast ${contrast(veiled, ink)}", contrast(veiled, ink) >= 4.5f)
    }
}
