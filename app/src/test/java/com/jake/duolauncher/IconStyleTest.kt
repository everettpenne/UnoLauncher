package com.jake.duolauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IconStyleTest {
    @Test fun originalNeverRecoloursOrReadsTheWallpaper() {
        assertFalse(IconStyle.ORIGINAL.recolours)
        assertFalse(IconStyle.ORIGINAL.usesWallpaper(true))
    }

    @Test fun themedFollowsTheWallpaperSwitch() {
        assertTrue(IconStyle.THEMED.recolours)
        assertTrue(IconStyle.THEMED.usesWallpaper(true))
        assertFalse(IconStyle.THEMED.usesWallpaper(false))
    }

    @Test fun tintedAlwaysUsesTheWallpaper() {
        assertTrue(IconStyle.TINTED.recolours)
        assertTrue(IconStyle.TINTED.usesWallpaper(false))
    }

    @Test fun unknownPreferenceFallsBackToOriginal() {
        assertEquals(IconStyle.TINTED, IconStyle.fromPreference("TINTED"))
        assertEquals(IconStyle.ORIGINAL, IconStyle.fromPreference("nonsense"))
        assertEquals(IconStyle.ORIGINAL, IconStyle.fromPreference(null))
    }

    @Test fun hyperGlassStylesAreTheirOwnKindAndOnlyTheAccentOneFollowsTheWallpaper() {
        val hyper = listOf(IconStyle.HYPER, IconStyle.HYPER_LIGHT, IconStyle.HYPER_ACCENT, IconStyle.HYPER_COLOR, IconStyle.HYPER_CLEAR, IconStyle.HYPER_CLEAR_COLOR)
        for (style in IconStyle.entries) assertEquals(style.name, style == IconStyle.HYPER_CLEAR || style == IconStyle.HYPER_CLEAR_COLOR, style.clear)
        for (style in IconStyle.entries) assertEquals(style.name, style in hyper, style.hyper)
        for (style in hyper) { assertFalse(style.recolours); assertEquals(style, style.resolve(true)); assertEquals(style, IconStyle.fromPreference(style.name)) }
        assertTrue(IconStyle.HYPER_ACCENT.usesWallpaper(true))
        assertFalse(IconStyle.HYPER_ACCENT.usesWallpaper(false))
        assertFalse(IconStyle.HYPER.usesWallpaper(true))
    }

    @Test fun theTwoIconMenusHoldEveryStyleBetweenThemAndNeitherNeedsScrolling() {
        assertEquals(IconStyle.entries.toSet(), (IconStyleMenu.styles + IconStyleMenu.looks).toSet())
        // The menu shows about eight rows before it scrolls, which hid the last three of eleven.
        assertTrue(IconStyleMenu.styles.size <= 7 && IconStyleMenu.looks.size <= 7)
        assertTrue(IconStyleMenu.looks.all { it.hyper })
        assertEquals(IconStyleMenu.looks.size, IconStyleMenu.looks.map { hyperLookName(it) }.toSet().size)
        assertEquals("App Colors on Black", hyperLookName(IconStyle.HYPER_COLOR))
        assertEquals("App Colors on Clear", hyperLookName(IconStyle.HYPER_CLEAR_COLOR))
    }

    @Test fun choosingHyperGlassKeepsTheLookOrStartsInAppColours() {
        assertEquals(IconStyle.HYPER, IconStyleMenu.styleOf(IconStyle.HYPER_CLEAR_COLOR))
        assertEquals(IconStyle.DARK, IconStyleMenu.styleOf(IconStyle.DARK))
        assertEquals(IconStyle.HYPER_COLOR, IconStyleMenu.chosen(IconStyle.HYPER, IconStyle.ORIGINAL))
        assertEquals(IconStyle.HYPER_CLEAR, IconStyleMenu.chosen(IconStyle.HYPER, IconStyle.HYPER_CLEAR))
        assertEquals(IconStyle.TINTED, IconStyleMenu.chosen(IconStyle.TINTED, IconStyle.HYPER_CLEAR))
    }
}
