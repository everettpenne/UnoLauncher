package com.jake.duolauncher

import com.jake.duolauncher.appearance.UnoMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolishTest {
    @Test fun smallLogosAreEnlargedToFillTheirTileButNeverPastTheSafeZone() {
        assertEquals(1f, IconArt.fillScale(.7f), 0f)                      // already fills it
        assertEquals(1f, IconArt.fillScale(IconArt.TARGET_EXTENT), 0f)
        assertEquals(IconArt.TARGET_EXTENT / .55f, IconArt.fillScale(.55f), 1e-4f)
        assertEquals(IconArt.MAX_GROW, IconArt.fillScale(.2f), 0f)        // a tiny logo grows only so far
        assertEquals(1f, IconArt.fillScale(0f), 0f)                        // no artwork read: untouched
        // Enlarged to the target, a logo stays inside Android's safe zone (66 of the 72 dp the tile shows).
        assertTrue(IconArt.TARGET_EXTENT <= 66f / 72f)
    }

    @Test fun theDarkStyleKeepsEachAppsOwnArtwork() {
        assertFalse(IconStyle.DARK.recolours)
        assertTrue(IconStyle.THEMED.recolours && IconStyle.TINTED.recolours)
        assertEquals(IconStyle.DARK, IconStyle.fromPreference("DARK"))
    }

    @Test fun theSharedSpringsHaveOneCharacter() {
        // Opening may overshoot a little; settling never does; a press is the springiest.
        assertTrue(UnoMotion.OPEN_DAMPING in .7f..<1f)
        assertTrue(UnoMotion.GLIDE_DAMPING > UnoMotion.OPEN_DAMPING)
        assertTrue(UnoMotion.PRESS_DAMPING < UnoMotion.OPEN_DAMPING)
    }
}

class IslandRestWidthTest {
    @Test fun thirteenAndAHalfMillimetresOnThePixel10a() {
        // 13.5 mm at the Pixel 10a's 422.2 PPI is 224 px, which at its 2.625 density is about 85 dp.
        val dp = com.jake.duolauncher.island.IslandGeometry.mmToDp(13.5f, 422.2f, 2.625f)
        assertEquals(224.4f / 2.625f, dp, .2f)
    }

    @Test fun anImplausibleReportedDensityIsNotTrusted() {
        // A phone reporting 160 dpi on a 2.625 layout density is off by far more than 30 %: the layout's own 420 dpi is used.
        val dp = com.jake.duolauncher.island.IslandGeometry.mmToDp(13.5f, 160f, 2.625f)
        assertEquals(13.5f / 25.4f * 420f / 2.625f, dp, .01f)
    }
}

class GlassKindTest {
    @Test fun twoMaterialsAsOnIos() {
        val clear = com.jake.duolauncher.appearance.GlassKind.CLEAR
        val regular = com.jake.duolauncher.appearance.GlassKind.REGULAR
        assertTrue(clear.tintAlpha < .2f && clear.blurDp < 2f)
        assertTrue(regular.tintAlpha > .4f && regular.blurDp >= 6f)
    }
}

/** 0.104's settings: the photo styles survive a save and older saves still read, and the three glass choices map onto the two switches. */
class SettingsReorganizationTest {
    @Test fun wallpaperStyleIsSavedAndOldSavesAreNatural() {
        val a = com.jake.duolauncher.appearance.WallpaperAdjust(1.5f, .2f, -.3f, -.4f, .5f, 1.2f, .7f, com.jake.duolauncher.appearance.WallpaperStyle.DUOTONE)
        org.junit.Assert.assertEquals(a, com.jake.duolauncher.appearance.WallpaperAdjust.decode(a.encode()))
        val old = com.jake.duolauncher.appearance.WallpaperAdjust.decode("1.5,0,0,0,0,1,0")
        org.junit.Assert.assertEquals(com.jake.duolauncher.appearance.WallpaperStyle.NATURAL, old.style)
        org.junit.Assert.assertEquals(1.5f, old.zoom, 0f)
        // a style from a later version reads as Natural rather than breaking the picture
        org.junit.Assert.assertEquals(com.jake.duolauncher.appearance.WallpaperStyle.NATURAL,
            com.jake.duolauncher.appearance.WallpaperAdjust.decode("1,0,0,0,0,1,0,99").style)
    }

    @Test fun aStyleCountsAsAToneChange() {
        org.junit.Assert.assertTrue(com.jake.duolauncher.appearance.WallpaperAdjust(style = com.jake.duolauncher.appearance.WallpaperStyle.MONO).toneChanged)
        org.junit.Assert.assertFalse(com.jake.duolauncher.appearance.WallpaperAdjust(style = com.jake.duolauncher.appearance.WallpaperStyle.MONO).isNeutral)
    }

    @Test fun glassChoiceFollowsTheTwoSwitches() {
        val s = com.jake.duolauncher.appearance.AppearanceState()
        org.junit.Assert.assertEquals(com.jake.duolauncher.appearance.GlassChoice.CLEAR, s.glassKind)
        org.junit.Assert.assertEquals(com.jake.duolauncher.appearance.GlassChoice.FROSTED, s.copy(clearGlass = false).glassKind)
        org.junit.Assert.assertEquals(com.jake.duolauncher.appearance.GlassChoice.OFF, s.copy(liquidGlass = false).glassKind)
    }

    @Test fun settingsCardsAreConcentricWithTheSheet() {
        org.junit.Assert.assertEquals("the Glass kit's and iOS 26's grouped card", 26f, (SHEET_RADIUS - SHEET_INSET).value, 0f)
    }

    @Test fun everyPageHasAName() {
        CustomizationPage.entries.forEach { org.junit.Assert.assertTrue(SettingsSearch.pageName(it).isNotBlank()) }
        org.junit.Assert.assertEquals(CustomizationPage.ISLAND, CustomizationPage.ISLAND_STYLE.parent())
        org.junit.Assert.assertEquals(CustomizationPage.OVERVIEW, CustomizationPage.KEYBOARD.parent())
    }
}

class IconHueTest {
    private fun px(vararg c: Int) = IntArray(c.size) { c[it] }
    @org.junit.Test fun whiteAndBlackAreNeutralAndABlueTileIsNot() {
        assertTrue(IconArt.hue(px(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt())).saturation < IconArt.NEUTRAL)
        assertNull(IconArt.hue(px(0xFF000000.toInt())).colour)
        val blue = IconArt.hue(px(0xFF2A6CE0.toInt(), 0xFF2A6CE0.toInt()))
        assertTrue(blue.saturation >= IconArt.NEUTRAL)
        assertNotNull(blue.colour)
    }
    @org.junit.Test fun transparentPixelsDoNotCount() {
        val h = IconArt.hue(px(0x00FF0000, 0xFFFFFFFF.toInt()))
        assertTrue(h.saturation < IconArt.NEUTRAL)
    }
}
