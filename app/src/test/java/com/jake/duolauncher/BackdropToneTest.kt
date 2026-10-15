package com.jake.duolauncher

import com.jake.duolauncher.appearance.BackdropTone
import com.jake.duolauncher.appearance.GlassTone
import org.junit.Assert.*
import org.junit.Test

class BackdropToneTest {
    @Test fun aWallpaperThatSuitsTheThemeIsLeftAlone() {
        assertEquals(GlassTone.Neutral, BackdropTone.tone(.8f, darkTheme = false))
        assertEquals(GlassTone.Neutral, BackdropTone.tone(.2f, darkTheme = true))
        assertEquals(GlassTone.Neutral, BackdropTone.tone(.55f, darkTheme = false))
    }

    @Test fun aDarkWallpaperInTheLightThemeIsLifted() {
        val t = BackdropTone.tone(.05f, darkTheme = false)
        assertTrue(t.brightness > .1f); assertTrue(t.contrast < 1f)
    }

    @Test fun aBrightWallpaperInTheDarkThemeIsDimmed() {
        val t = BackdropTone.tone(.95f, darkTheme = true)
        assertTrue(t.brightness < -.1f); assertTrue(t.contrast < 1f)
    }

    @Test fun theAdjustmentGrowsWithHowWrongTheWallpaperIsAndStaysBounded() {
        val mild = BackdropTone.tone(.40f, darkTheme = false).brightness
        val strong = BackdropTone.tone(.0f, darkTheme = false).brightness
        assertTrue(mild in 0f..strong)
        assertTrue(strong <= .2f + 1e-6f)
        assertEquals(.2f, BackdropTone.tone(-1f, darkTheme = false).brightness, 1e-6f)
    }

    @Test fun builtInScenesAreBrightByDayAndDarkByNight() {
        assertTrue(BackdropTone.builtinLuminance(false) > BackdropTone.builtinLuminance(true))
    }
}
