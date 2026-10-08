package com.jake.duolauncher

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.*
import org.junit.Test

class AccentMathTest {
    private val wallpapers = listOf(
        0xFF3A7BD5.toInt(), 0xFFE0A458.toInt(), 0xFFB5651D.toInt(), 0xFF2E8B57.toInt(), 0xFFC71585.toInt(),
        0xFF808080.toInt(), 0xFFF5F5F5.toInt(), 0xFF101010.toInt(), 0xFFFFFF00.toInt(), 0xFF00FFFF.toInt())

    @Test fun hslRoundTripsPrimaries() {
        listOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFA500.toInt(), 0xFF336699.toInt()).forEach { argb ->
            val hsl = AccentMath.toHsl(argb)
            val back = AccentMath.fromHsl(hsl[0], hsl[1], hsl[2])
            listOf(16, 8, 0).forEach { shift ->
                assertEquals("channel at $shift for ${Integer.toHexString(argb)}",
                    (argb shr shift) and 0xFF, (back shr shift) and 0xFF)
            }
        }
    }

    @Test fun inkIsReadableOnTheGlassInBothThemes() {
        for (dark in listOf(false, true)) for (wallpaper in wallpapers) {
            val a = AccentMath.derive(wallpaper, dark)
            assertTrue("ink on glass for ${Integer.toHexString(wallpaper)} dark=$dark was ${AccentMath.contrast(a.ink, a.glass)}",
                AccentMath.contrast(a.ink, a.glass) >= 7f)
        }
    }

    @Test fun accentStandsOutFromTheGlassItSitsOn() {
        for (dark in listOf(false, true)) for (wallpaper in wallpapers) {
            val a = AccentMath.derive(wallpaper, dark)
            assertTrue("accent on glass for ${Integer.toHexString(wallpaper)} dark=$dark was ${AccentMath.contrast(a.accent, a.glass)}",
                AccentMath.contrast(a.accent, a.glass) >= 2.2f)
        }
    }

    @Test fun aGreyWallpaperStillGetsAVisibleAccent() {
        val a = AccentMath.derive(0xFF808080.toInt(), dark = false)
        assertTrue(AccentMath.toHsl(a.accent.toArgb())[1] >= .45f)
    }

    @Test fun theHueFollowsTheWallpaper() {
        val blue = AccentMath.toHsl(AccentMath.derive(0xFF3A7BD5.toInt(), false).accent.toArgb())[0]
        val orange = AccentMath.toHsl(AccentMath.derive(0xFFE0A458.toInt(), false).accent.toArgb())[0]
        assertTrue("blue hue $blue", blue in 200f..230f)
        assertTrue("orange hue $orange", orange in 25f..45f)
    }

    @Test fun glassIsPaleInLightAndDeepInDark() {
        val light = AccentMath.derive(0xFF3A7BD5.toInt(), false)
        val dark = AccentMath.derive(0xFF3A7BD5.toInt(), true)
        assertTrue(AdaptiveInkMath.luminance(light.glass) > .6f)
        assertTrue(AdaptiveInkMath.luminance(dark.glass) < .06f)
        assertNotEquals(Color.Unspecified, light.accent)
    }
}
