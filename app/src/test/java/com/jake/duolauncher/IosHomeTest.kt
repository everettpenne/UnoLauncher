package com.jake.duolauncher

import androidx.compose.ui.graphics.Color
import com.jake.duolauncher.appearance.GlassFinish
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IosHomeTest {
    @Test fun anIphoneSizedScreenGetsTheIphonesGrid() {
        // Apple's 393 pt iPhone: widgets 158 square (small), 338 wide (medium, large), 354 tall (large), 60 pt icons.
        val g = iosHomeGeometry(393f, 900f, labels = true)
        val pitchX = g.gridWidth / GRID_COLUMNS
        assertEquals(158f, 2 * pitchX - g.widgetGapX, .01f)
        assertEquals(338f, 4 * pitchX - g.widgetGapX, .01f)
        assertEquals(158f, 2 * g.rowHeight - g.widgetGapY, .01f)
        assertEquals(354f, 4 * g.rowHeight - g.widgetGapY, .01f)
        assertEquals(60f, g.iconSize, .01f)
        // 27.5 pt from each side to the widgets' edges.
        assertEquals(27.5f, g.gridStart + g.widgetGapX / 2f, .01f)
        assertTrue(g.ios)
    }

    @Test fun theGridIsUniform() {
        // The widget band's pitch (the side layout's two top rows) is the row pitch, so every row is the same height.
        for (w in listOf(360f, 393f, 411f, 430f)) {
            val g = iosHomeGeometry(w, 860f, labels = true)
            assertEquals((g.widgetHeight + g.widgetGapY) / 2f, g.rowHeight, .01f)
            assertEquals(homeRowTop(3, (g.widgetHeight + g.widgetGapY) / 2f, g.rowHeight), 3 * g.rowHeight, .01f)
        }
    }

    @Test fun itScalesWithThePhone() {
        val pixel = iosHomeGeometry(411f, 860f, labels = true)
        assertEquals(60f * 411f / 393f, pixel.iconSize, .05f)
        // Small widgets stay square when there is height for it.
        assertEquals(2 * pixel.gridWidth / GRID_COLUMNS - pixel.widgetGapX, pixel.widgetHeight, .05f)
    }

    @Test fun aShortScreenShrinksTheRowsSoNothingRunsUnderTheDock() {
        // A foldable's cover screen, less its bars: about 411 x 730.
        val g = iosHomeGeometry(411f, 730f, labels = true)
        assertTrue(g.contentTop + GRID_ROWS * g.rowHeight + g.bottomBarReserve <= 730f + .01f)
        // The name still fits under the icon.
        assertTrue(g.iconSize + 20f <= g.rowHeight)
        assertTrue(g.widgetGapY > 0f)
    }

    @Test fun theBottomBarReservesTheDockAndSearch() {
        val g = iosHomeGeometry(393f, 900f, labels = true)
        assertEquals(g.dockBarBottom + g.dockBarHeight + 2 * g.pillGap + g.pillHeight, g.bottomBarReserve, .01f)
        assertTrue(g.dockBarHeight > g.iconSize)
    }

    @Test fun widgetsSnapToTheIosSizes() {
        assertEquals(IosWidgetSizes.SMALL, IosWidgetSizes.snap(WidgetSpan(2, 2)))
        assertEquals(IosWidgetSizes.SMALL, IosWidgetSizes.snap(WidgetSpan(1, 1)))
        // Never squeezed below what it asked for when a bigger iOS size fits.
        assertEquals(IosWidgetSizes.MEDIUM, IosWidgetSizes.snap(WidgetSpan(3, 2)))
        assertEquals(IosWidgetSizes.MEDIUM, IosWidgetSizes.snap(WidgetSpan(4, 1)))
        assertEquals(IosWidgetSizes.LARGE, IosWidgetSizes.snap(WidgetSpan(4, 3)))
        assertEquals(IosWidgetSizes.LARGE, IosWidgetSizes.snap(WidgetSpan(2, 4)))
    }

    @Test fun aSnapRespectsWhatTheWidgetAllows() {
        // At most 2 columns wide: only small fits, even for a wide request.
        val narrow = WidgetSpanConstraints(WidgetSpan(2, 2), WidgetSpan(1, 1), WidgetSpan(2, 6), true, true)
        assertEquals(listOf(IosWidgetSizes.SMALL), IosWidgetSizes.allowed(narrow))
        assertEquals(IosWidgetSizes.SMALL, IosWidgetSizes.snap(WidgetSpan(4, 2), narrow))
        // A fixed 3 x 1 fits no iOS size and keeps its own.
        val fixed = WidgetSpanConstraints(WidgetSpan(3, 1), WidgetSpan(3, 1), WidgetSpan(3, 1), false, false)
        assertEquals(WidgetSpan(3, 1), IosWidgetSizes.snap(WidgetSpan(3, 1), fixed))
        assertEquals("Medium", IosWidgetSizes.label(IosWidgetSizes.MEDIUM))
        assertEquals(null, IosWidgetSizes.label(WidgetSpan(3, 1)))
    }

    @Test fun clearGlassClearsOnlyLightSurfaces() {
        val dock = Color.White.copy(alpha = .12f)
        val sheet = Color.White.copy(alpha = .58f)
        assertEquals(.12f * GlassFinish.CLEAR_TINT_SHARE, GlassFinish.tint(dock, clear = true).alpha, 4e-3f)
        assertEquals(.12f, GlassFinish.tint(dock, clear = false).alpha, 4e-3f)
        assertEquals(.58f, GlassFinish.tint(sheet, clear = true).alpha, 4e-3f)
        assertTrue(GlassFinish.clearEdge(dock, clear = true))
        assertFalse(GlassFinish.clearEdge(sheet, clear = true))
        assertFalse(GlassFinish.clearEdge(dock, clear = false))
    }
}

class IosWidgetTypeTest {
    @Test fun headersDarkenOnLightGlassAndKeepTheirColourOnDark() {
        val gold = Color(0xFFFFD60A)
        // Dark ink means a light wallpaper: the gold header is darkened so it reads.
        val onLight = com.jake.duolauncher.widgets.WidgetType.legibleAccent(gold, Color(0xFF1C1C1E))
        assertTrue(onLight.red < gold.red && onLight.green < gold.green)
        assertEquals(gold, com.jake.duolauncher.widgets.WidgetType.legibleAccent(gold, Color.White))
    }

    @Test fun onACardAHeaderKeepsItsSystemColourUnlessItIsVeryPale() {
        val red = Color(0xFFFF3B30); val orange = Color(0xFFFF9F0A); val gold = Color(0xFFFFD60A)
        val dark = Color(0xFF0B0B0D)
        assertEquals(red, com.jake.duolauncher.widgets.WidgetType.legibleAccent(red, dark, card = true))
        assertEquals(orange, com.jake.duolauncher.widgets.WidgetType.legibleAccent(orange, dark, card = true))
        assertNotEquals(gold, com.jake.duolauncher.widgets.WidgetType.legibleAccent(gold, dark, card = true))
        assertEquals(gold, com.jake.duolauncher.widgets.WidgetType.legibleAccent(gold, Color.White, card = true))
    }

    @Test fun bigFiguresAreSemiboldTabularAndTight() {
        val style = com.jake.duolauncher.widgets.WidgetType.number(androidx.compose.ui.unit.TextUnit(40f, androidx.compose.ui.unit.TextUnitType.Sp))
        assertEquals(androidx.compose.ui.text.font.FontWeight.SemiBold, style.fontWeight)
        assertEquals("tnum", style.fontFeatureSettings)
        assertTrue(style.letterSpacing.value < 0f)
    }
}

class StatusIconsTest {
    @Test fun iconsFollowTheWallpaperUnderTheStatusBar() {
        assertFalse(com.jake.duolauncher.appearance.StatusIcons.dark(null, false))
        assertFalse(com.jake.duolauncher.appearance.StatusIcons.dark(.02f, false))
        assertTrue(com.jake.duolauncher.appearance.StatusIcons.dark(.8f, false))
        // The dead band: once dark, a strip just under the crossover keeps them dark rather than flickering.
        val edge = (0..100).map { it / 100f }.first { com.jake.duolauncher.appearance.StatusIcons.dark(it, false) }
        assertTrue(com.jake.duolauncher.appearance.StatusIcons.dark(edge - .05f, true))
    }
}
