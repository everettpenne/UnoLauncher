package com.jake.duolauncher

import androidx.compose.ui.graphics.Color
import com.jake.duolauncher.appearance.DisplayFace
import com.jake.duolauncher.appearance.DisplayFonts
import com.jake.duolauncher.appearance.TonalRamp
import com.jake.duolauncher.island.HeroContent
import com.jake.duolauncher.island.HeroTint
import com.jake.duolauncher.island.IslandHero
import com.jake.duolauncher.island.IslandTheme
import com.jake.duolauncher.island.IslandThemeFile
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class ExpressiveTest {
    @After fun restore() { DisplayFonts.exists = { java.io.File(it).canRead() } }

    private fun lum(c: Color): Double {
        fun ch(v: Float) = if (v <= .03928f) v / 12.92 else Math.pow((v + .055) / 1.055, 2.4)
        return .2126 * ch(c.red) + .7152 * ch(c.green) + .0722 * ch(c.blue)
    }
    private fun contrast(a: Color, b: Color): Double { val x = lum(a); val y = lum(b); return (maxOf(x, y) + .05) / (minOf(x, y) + .05) }

    @Test fun everyToneOfTheRampIsReadableForAnyAccent() {
        val accents = listOf(Color(0xFFFF9F0A), Color(0xFF0A84FF), Color(0xFF34C759), Color(0xFFFF375F), Color(0xFF808080), Color(0xFFFFD60A), Color.Black, Color.White)
        for (accent in accents) for (dark in listOf(false, true)) TonalRamp.tones(accent, dark).forEach { t ->
            assertTrue("$accent dark=$dark ${contrast(t.container, t.onContainer)}", contrast(t.container, t.onContainer) >= 4.5)
        }
    }

    @Test fun theRampHasDistinctTonesAndWrapsAround() {
        val tones = TonalRamp.tones(Color(0xFFFF9F0A), dark = false)
        assertEquals(tones.size, tones.map { it.container }.toSet().size)
        assertEquals(TonalRamp.tone(Color(0xFFFF9F0A), false, 0).container, TonalRamp.tone(Color(0xFFFF9F0A), false, tones.size).container)
    }

    @Test fun aFaceThePhoneDoesNotHaveIsClassic() {
        DisplayFonts.exists = { false }
        // Classic, System (the phone's default font, always there) and Bagel need no file; the rest are the phone's.
        assertEquals(listOf(DisplayFace.CLASSIC, DisplayFace.SYSTEM, DisplayFace.BAGEL), DisplayFonts.available())
        assertEquals(DisplayFace.BAGEL, DisplayFonts.resolve(DisplayFace.BAGEL))
        assertEquals(DisplayFace.CLASSIC, DisplayFonts.resolve(DisplayFace.SCRIPT))
        DisplayFonts.exists = { it.endsWith("DancingScript-Regular.ttf") }
        assertEquals(listOf(DisplayFace.CLASSIC, DisplayFace.SYSTEM, DisplayFace.SCRIPT, DisplayFace.BAGEL), DisplayFonts.available())
    }

    @Test fun theHeadlineRoundTripsThroughAThemeFile() {
        val theme = IslandTheme("Typed", face = DisplayFace.TALL, heroTint = HeroTint.SWEEP)
        val ok = IslandThemeFile.parse(IslandThemeFile.toJson(theme)) as IslandThemeFile.Result.Ok
        assertEquals(theme, ok.theme)
        // A plain theme says nothing about type, so old files and old Unos read it as before.
        assertFalse(IslandThemeFile.toJson(IslandTheme("Plain")).contains("\"type\""))
    }

    @Test fun aFaceFromANewerUnoLoadsAsClassic() {
        val json = IslandThemeFile.toJson(IslandTheme("Future")).replace("\"motion\"", "\"type\": {\"face\": \"hologram\", \"tint\": \"rainbow\"}, \"motion\"")
        val ok = IslandThemeFile.parse(json) as IslandThemeFile.Result.Ok
        assertEquals(DisplayFace.CLASSIC, ok.theme.face)
        assertEquals(HeroTint.INK, ok.theme.heroTint)
    }

    @Test fun theGreetingIsOnePlainShortLine() {
        assertEquals("hello there", IslandHero.clean("  hello\n  there "))
        assertNull(IslandHero.clean("   "))
        assertEquals(IslandHero.MAX_GREETING, IslandHero.clean("x".repeat(80))!!.length)
    }

    @Test fun theTimeMovesToTheSideLineWhenTheHeadlineIsNotTheTime() {
        val now = LocalDateTime.of(2026, 11, 18, 8, 12)
        assertEquals("Wed, Nov 18", IslandHero.sideLine(HeroContent.TIME, "Wed, Nov 18", "8:12", now))
        assertEquals("8:12 · Wed, Nov 18", IslandHero.sideLine(HeroContent.GREETING, "Wed, Nov 18", "8:12", now))
        assertEquals("Wednesday", IslandHero.words(HeroContent.DAY, "hello", now))
        assertFalse(IslandHero.sideLine(HeroContent.DATE, "Wed, Nov 18", "8:12", now).contains("Nov"))
    }
}

class ExpressiveShapesTest {
    @Test fun everyMaterialShapeBuildsAndFillsItsBox() {
        with(com.jake.duolauncher.appearance.ExpressivePolygons) {
            listOf(cookie4, cookie7, cookie9, cookie12, sunny, verySunny, softBurst, flower, clover8, pentagon, pill).forEach { p ->
                val b = p.calculateBounds()
                // Normalized: inside the unit box and touching it on its longer side.
                assertTrue(b.joinToString(), b[0] >= -.001f && b[1] >= -.001f && b[2] <= 1.001f && b[3] <= 1.001f)
                assertTrue(b.joinToString(), maxOf(b[2] - b[0], b[3] - b[1]) > .99f)
            }
        }
    }
}
