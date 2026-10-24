package com.jake.duolauncher

import com.jake.duolauncher.appearance.UnoMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
