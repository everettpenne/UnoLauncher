package com.jake.duolauncher

import androidx.compose.ui.graphics.Color
import com.jake.duolauncher.appearance.ControlShapes
import com.jake.duolauncher.appearance.Expression
import com.jake.duolauncher.appearance.Personality
import com.jake.duolauncher.appearance.UnoTones
import com.jake.duolauncher.panel.TileMorph
import org.junit.Assert.*
import org.junit.Test

class PersonalityTest {
    private val calm = Expression.of(Personality.CALM)
    private val balanced = Expression.of(Personality.BALANCED)
    private val expressive = Expression.of(Personality.EXPRESSIVE)

    @Test fun calmIsThePlainIosLook() {
        assertFalse(calm.displayTitles); assertFalse(calm.shapedBadges); assertFalse(calm.displayNumerals); assertFalse(calm.restsSquare)
        assertFalse(calm.thickTracks); assertFalse(calm.widgetShapes)
        assertEquals(0, calm.tonal); assertEquals(0f, calm.morph, 0f); assertEquals(1f, calm.bounce, 0f)
    }

    @Test fun balancedKeepsWhatShippedAndAddsOnlyColourAndAHintOfMorph() {
        assertTrue(balanced.displayTitles); assertTrue(balanced.shapedBadges)
        assertFalse(balanced.displayNumerals); assertFalse(balanced.restsSquare); assertFalse(balanced.thickTracks)
        assertEquals(1, balanced.tonal)
        assertTrue(balanced.morph > 0f && balanced.morph < .5f)
    }

    @Test fun eachLevelGoesAtLeastAsFarAsTheOneBefore() {
        val levels = listOf(calm, balanced, expressive)
        levels.zipWithNext { a, b ->
            assertTrue(b.tonal >= a.tonal); assertTrue(b.morph >= a.morph); assertTrue(b.bounce <= a.bounce)
        }
        assertTrue(expressive.displayNumerals); assertTrue(expressive.restsSquare); assertEquals(1f, expressive.morph, 0f)
    }

    @Test fun aCalmControlNeverLeavesItsCapsule() {
        assertEquals(0f, ControlShapes.amount(calm.morph, press = 1f, rest = 1f), 0f)
        assertEquals(50, ControlShapes.percent(0f))
        assertSame(com.jake.duolauncher.appearance.Corner.pill, ControlShapes.morphed(0f))
    }

    @Test fun aPressedOrSelectedControlTightensAsFarAsTheStyleAllows() {
        assertEquals(ControlShapes.SQUARE_PERCENT, ControlShapes.percent(ControlShapes.amount(expressive.morph, press = 1f, rest = 0f)))
        assertEquals(ControlShapes.SQUARE_PERCENT, ControlShapes.percent(ControlShapes.amount(expressive.morph, press = 0f, rest = 1f)))
        val part = ControlShapes.percent(ControlShapes.amount(balanced.morph, press = 1f, rest = 0f))
        assertTrue(part in (ControlShapes.SQUARE_PERCENT + 1)..49)
        // A spring's overshoot past 1 or below 0 never takes the shape out of range.
        assertEquals(ControlShapes.SQUARE_PERCENT, ControlShapes.percent(ControlShapes.amount(1f, press = 1.3f, rest = 0f)))
        assertEquals(50, ControlShapes.percent(ControlShapes.amount(1f, press = -.2f, rest = 0f)))
        // The same amount gives the same shape object: a morph allocates one shape per step, once.
        assertSame(ControlShapes.morphed(.5f), ControlShapes.morphed(.5f))
    }

    @Test fun aTileChangesShapeWithItsStateOnlyWhenExpressive() {
        assertEquals(TileMorph.REST_DP, TileMorph.cornerDp(on = true, restsSquare = false), 0f)
        assertEquals(TileMorph.REST_DP, TileMorph.cornerDp(on = false, restsSquare = false), 0f)
        assertTrue(TileMorph.cornerDp(on = true, restsSquare = true) < TileMorph.REST_DP)
        assertTrue(TileMorph.cornerDp(on = false, restsSquare = true) > TileMorph.REST_DP)
    }

    private fun lum(c: Color): Double {
        fun ch(v: Float) = if (v <= .03928f) v / 12.92 else Math.pow((v + .055) / 1.055, 2.4)
        return .2126 * ch(c.red) + .7152 * ch(c.green) + .0722 * ch(c.blue)
    }
    private fun contrast(a: Color, b: Color): Double { val x = lum(a); val y = lum(b); return (maxOf(x, y) + .05) / (minOf(x, y) + .05) }

    @Test fun theTonalRolesAreReadable() {
        for (accent in listOf(Color(0xFFFF9F0A), Color(0xFF0A84FF), Color(0xFF34C759), Color(0xFFFF375F), Color(0xFF808080))) {
            val selected = UnoTones.selected(accent)
            assertTrue("$accent", contrast(selected.container, selected.onContainer) >= 4.5)
            repeat(4) { i -> UnoTones.widgetAccent(accent, i).let { assertTrue("$accent $i", contrast(it.container, it.onContainer) >= 4.5) } }
        }
        // Something on wears the accent with the ink the launcher already pairs with it.
        val on = UnoTones.on(Color(0xFFFF9F0A), Color.Black)
        assertTrue(contrast(on.container, on.onContainer) >= 4.5)
    }

    @Test fun onlyUnosOwnSingleWidgetsTakeAShapeAndOnlyWhenTheStyleAllows() {
        assertTrue(WidgetShapeRules.eligible(CLOCK_WIDGET)); assertTrue(WidgetShapeRules.eligible(DATE_WIDGET)); assertTrue(WidgetShapeRules.eligible(com.jake.duolauncher.widgets.UnoWidgets.COUNTER))
        assertFalse(WidgetShapeRules.eligible(7)); assertFalse(WidgetShapeRules.eligible(EMPTY_WIDGET))
        assertFalse(WidgetShapeRules.eligible(FOLDER_WIDGET)); assertFalse(WidgetShapeRules.eligible(NEEDS_BINDING_WIDGET))
        assertEquals(WidgetShape.COOKIE, WidgetShapeRules.resolve(WidgetShape.COOKIE, CLOCK_WIDGET, stacked = false, shapesAllowed = true))
        assertEquals(WidgetShape.DEFAULT, WidgetShapeRules.resolve(WidgetShape.COOKIE, CLOCK_WIDGET, stacked = false, shapesAllowed = false))
        assertEquals(WidgetShape.DEFAULT, WidgetShapeRules.resolve(WidgetShape.COOKIE, CLOCK_WIDGET, stacked = true, shapesAllowed = true))
        assertEquals(WidgetShape.DEFAULT, WidgetShapeRules.resolve(WidgetShape.SUNNY, 12, stacked = false, shapesAllowed = true))
        assertEquals(WidgetShape.DEFAULT, WidgetShapeRules.resolve(null, CLOCK_WIDGET, stacked = false, shapesAllowed = true))
        assertEquals(WidgetShape.SUNNY, WidgetShapeRules.parse("SUNNY")); assertNull(WidgetShapeRules.parse("HEXAGON"))
        // A widget that paints to its edge would be a rectangle floating in the shape; the Lock Screen clock has no card at all.
        assertFalse(WidgetShapeRules.eligible(com.jake.duolauncher.widgets.UnoWidgets.LIQUID_BATTERY))
        assertTrue("a photo fills its shape", WidgetShapeRules.eligible(com.jake.duolauncher.widgets.UnoWidgets.PHOTOS))
        // Every shape but Default has an outline, the first two bend glass along it, and the saved names of the first two are unchanged.
        WidgetShape.entries.forEach { assertEquals(it == WidgetShape.DEFAULT, WidgetShapeRules.outline(it) == null) }
        assertTrue(WidgetShapeRules.outline(WidgetShape.COOKIE)!!.bends); assertTrue(WidgetShapeRules.outline(WidgetShape.SUNNY)!!.bends)
        listOf(WidgetShape.CLOVER, WidgetShape.FLOWER, WidgetShape.BURST, WidgetShape.PENTAGON).forEach { assertFalse("$it", WidgetShapeRules.outline(it)!!.bends) }
        assertEquals(listOf("DEFAULT", "COOKIE", "SUNNY"), WidgetShape.entries.take(3).map { it.name })
        // Every outline leaves its content room: nothing is shrunk below the floor.
        WidgetShape.entries.mapNotNull { WidgetShapeRules.outline(it) }.forEach { o ->
            val fit = WidgetShapeRules.contentScale(160f, o.outer, o.inner, 16f)
            assertTrue("${o.lobes} lobes: $fit", fit > .4f && fit <= 1f)
        }
        assertFalse(WidgetShapeRules.eligible(com.jake.duolauncher.widgets.UnoWidgets.LOCK_CLOCK))
        assertTrue(WidgetStyleRules.bare(com.jake.duolauncher.widgets.UnoWidgets.LOCK_CLOCK)); assertFalse(WidgetStyleRules.bare(CLOCK_WIDGET))
    }

    @Test fun aShapedWidgetsContentIsDrawnSmallEnoughToStayInsideTheDips() {
        // A 170 dp widget with 16 dp margins in a cookie whose dips are at 83 % of its tips: the content's corner sits on the dips' circle.
        val scale = WidgetShapeRules.contentScale(170f, outer = .5f, inner = .83f, padding = 16f)
        val halfDiagonal = (170f - 32f) * scale / 2f * 1.41421356f
        assertEquals(170f * .5f * .83f, halfDiagonal, .5f)
        assertTrue(scale < 1f && scale > .6f)
        // Never enlarged, and never shrunk to nothing.
        assertEquals(1f, WidgetShapeRules.contentScale(170f, outer = .5f, inner = 1f, padding = 40f), 0f)
        assertTrue(WidgetShapeRules.contentScale(40f, outer = .5f, inner = .2f, padding = 4f) >= .4f)
    }

    @Test fun theLensIsFittedToGooglesOutline() {
        // The fit is measured from the polygon: the tips' radius (a little under half the box, since their points are rounded off), the dips
        // where Material's star puts them, and the centre the polygon itself reports.
        val nine = com.jake.duolauncher.appearance.LobedShapes.cookie9
        assertTrue("outer ${nine.outer}", nine.outer in .47f..0.53f)
        assertTrue("inner ${nine.inner}", nine.inner in .78f..0.92f)
        assertEquals(.5f, nine.centerY, .02f)
        val sunny = com.jake.duolauncher.appearance.LobedShapes.sunny
        assertEquals(.5f, sunny.centerX, .01f); assertEquals(.5f, sunny.centerY, .01f)
        assertTrue("sunny inner ${sunny.inner} outer ${sunny.outer}", sunny.inner in .75f..0.9f)
    }

    @Test fun accentWordsAreReadableOnTheSettingsPlateInBothThemes() {
        // Uno orange as it is fails on a pale plate; as text it is deepened just enough, and lifted on the dark plate.
        val orange = Color(0xFFFF9F0A)
        assertTrue(AccentText.contrast(orange, AccentText.LIGHT_PLATE) < AccentText.MIN_CONTRAST)
        for (accent in listOf(orange, Color(0xFF0A84FF), Color(0xFF34C759), Color(0xFFFFD60A), Color(0xFFFF3B30), Color(0xFF808080), Color.White, Color.Black)) {
            assertTrue("$accent light", AccentText.contrast(AccentText.readable(accent, dark = false), AccentText.LIGHT_PLATE) >= AccentText.MIN_CONTRAST)
            assertTrue("$accent dark", AccentText.contrast(AccentText.readable(accent, dark = true), AccentText.DARK_PLATE) >= AccentText.MIN_CONTRAST)
        }
        // A colour that already reads is left alone.
        val deep = Color(0xFF7A3E00)
        assertEquals(deep, AccentText.readable(deep, dark = false))
    }

    @Test fun atCalmTheIslandsHeadlineIsTheLaunchersOwnFont() {
        assertEquals(com.jake.duolauncher.appearance.DisplayFace.CLASSIC,
            com.jake.duolauncher.island.IslandHero.face(com.jake.duolauncher.appearance.DisplayFace.SCRIPT, displayType = false))
        assertEquals(com.jake.duolauncher.appearance.DisplayFace.SCRIPT,
            com.jake.duolauncher.island.IslandHero.face(com.jake.duolauncher.appearance.DisplayFace.SCRIPT, displayType = true))
        assertEquals(calm.displayTitles, false)
    }

    @Test fun unosOwnWidgetsHaveNamesForTheSettingsList() {
        assertEquals("Clock", WidgetNames.builtIn(CLOCK_WIDGET)); assertEquals("Date", WidgetNames.builtIn(DATE_WIDGET))
        assertEquals("Battery", WidgetNames.builtIn(com.jake.duolauncher.widgets.UnoWidgets.BATTERY))
        assertEquals("Lock Screen clock", WidgetNames.builtIn(com.jake.duolauncher.widgets.UnoWidgets.LOCK_CLOCK))
        // An app's widget is named by its provider, not here.
        assertNull(WidgetNames.builtIn(42))
    }

    // ---- the second round: search and the assistant, folders, menus and sheets, the island ----

    @Test fun everythingTheSecondRoundAddsIsExpressivesAlone() {
        for (level in listOf(calm, balanced)) {
            assertEquals(1, level.palette); assertFalse(level.shapedMarks); assertFalse(level.displayHeads)
            assertFalse(level.tonalBubbles); assertFalse(level.islandNumerals); assertFalse(level.islandThickProgress)
        }
        assertEquals(3, expressive.palette); assertTrue(expressive.shapedMarks); assertTrue(expressive.displayHeads)
        assertTrue(expressive.tonalBubbles); assertTrue(expressive.islandNumerals); assertTrue(expressive.islandThickProgress)
    }

    @Test fun balancedIsExactlyWhatItWasBeforeTheSecondRound() {
        // The values Balanced shipped with in 0.135: any change here changes the look most people have.
        assertTrue(balanced.displayTitles); assertTrue(balanced.shapedBadges); assertTrue(balanced.widgetShapes)
        assertEquals(1, balanced.tonal); assertEquals(.35f, balanced.morph, 0f); assertEquals(.92f, balanced.bounce, 0f)
        assertFalse(balanced.restsSquare); assertFalse(balanced.displayNumerals); assertFalse(balanced.thickTracks)
    }

    @Test fun everyKindOfMarkHasAShapeAndOneOfThePalettesThreeColours() {
        val kinds = com.jake.duolauncher.appearance.MarkKind.entries
        assertEquals("each kind its own shape", kinds.size, kinds.map { com.jake.duolauncher.appearance.Marks.shape(it) }.toSet().size)
        assertTrue(kinds.all { com.jake.duolauncher.appearance.Marks.colour(it) in 0..2 })
        // The three that sit side by side in search (something to do, asking, going elsewhere) take the three different colours.
        assertEquals(setOf(0, 1, 2), listOf(com.jake.duolauncher.appearance.MarkKind.DO, com.jake.duolauncher.appearance.MarkKind.ASK,
            com.jake.duolauncher.appearance.MarkKind.GO).map { com.jake.duolauncher.appearance.Marks.colour(it) }.toSet())
        // Cycling through a list gives every kind and wraps, whatever number it is handed.
        assertEquals(kinds.toSet(), (0 until kinds.size).map { com.jake.duolauncher.appearance.Marks.cycled(it) }.toSet())
        assertEquals(com.jake.duolauncher.appearance.Marks.cycled(1), com.jake.duolauncher.appearance.Marks.cycled(1 + kinds.size))
        assertNotNull(com.jake.duolauncher.appearance.Marks.cycled(-3))
    }

    @Test fun anIconShapeIsTheSameForAnAppEveryTimeAndMixedUsesThemAll() {
        assertNull("the default is the rounded square", AppIconShape.SQUIRCLE.forApp("com.example/.Main"))
        assertEquals(AppIconShape.COOKIE, AppIconShape.COOKIE.forApp("anything"))
        val ids = (1..200).map { "com.example.app$it/.Main" }
        val mixed = ids.map { AppIconShape.MIXED.forApp(it)!! }
        assertEquals("an app keeps its shape", mixed, ids.map { AppIconShape.MIXED.forApp(it)!! })
        assertEquals(setOf(AppIconShape.COOKIE, AppIconShape.CLOVER, AppIconShape.FLOWER, AppIconShape.SUNNY, AppIconShape.PENTAGON), mixed.toSet())
        assertEquals(AppIconShape.SQUIRCLE, AppIconShape.fromPreference(null)); assertEquals(AppIconShape.SQUIRCLE, AppIconShape.fromPreference("HEXAGON"))
        assertEquals(AppIconShape.FLOWER, AppIconShape.fromPreference("FLOWER"))
    }
}
