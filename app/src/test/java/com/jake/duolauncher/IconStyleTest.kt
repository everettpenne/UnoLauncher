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
}
