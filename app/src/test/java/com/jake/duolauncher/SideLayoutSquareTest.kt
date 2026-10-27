package com.jake.duolauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SideLayoutSquareTest {
    @Test fun aTwoByTwoWidgetIsSquareOnPhonesOfEveryCommonWidth() {
        for (width in listOf(320f, 360f, 392f, 411f, 430f, 480f)) {
            val g = homeGeometry(width, 800f, LayoutPreset(), labels = true)
            // Its width is two of the four columns less the gap between widgets; its height is the widget band's.
            val widgetWidth = g.gridWidth / GRID_COLUMNS * 2f - g.widgetGapX
            assertEquals("width $width", widgetWidth, g.widgetHeight, .01f)
        }
    }

    @Test fun theWidgetsStayAReasonableSizeOnAVeryWideWindow() {
        val g = homeGeometry(640f, 800f, LayoutPreset(), labels = true)
        assertTrue(g.widgetHeight <= 260f)
    }

    @Test fun aTwoByTwoWidgetInTheAppRowsIsSquareToo() {
        for ((width, height) in listOf(360f to 780f, 393f to 852f, 411f to 923f, 430f to 932f, 480f to 1000f)) {
            val g = iosHomeGeometry(width, height, labels = true)
            val widgetWidth = g.gridWidth / GRID_COLUMNS * 2f - g.widgetGapX
            // In the widget band, and from the first app row on (a row's pitch is the band's).
            assertEquals("band $width", widgetWidth, g.widgetHeight, .01f)
            val topPitch = (g.widgetHeight + g.widgetGapY) / 2f
            if (g.rowHeight == topPitch) assertEquals("rows $width", widgetWidth, 2f * g.rowHeight - g.widgetGapY, .01f)
        }
    }

    @Test fun aWidgetRunsFromTheTopOfItsFirstIconsToTheBottomOfItsLast() {
        for (width in listOf(320f, 360f, 392f, 411f, 430f, 480f)) {
            val g = homeGeometry(width, 800f, LayoutPreset(), labels = true)
            // Two rows' pitch less the gap under a widget is its height: one pitch plus one icon, the icon's own top and bottom.
            assertEquals("width $width", g.rowHeight + g.box, 2f * g.rowHeight - g.widgetGapY, .01f)
            assertEquals("width $width", g.widgetHeight, g.rowHeight + g.box, .01f)
        }
    }

    @Test fun theIconSizeSettingScalesTheIconAndNotTheGrid() {
        val full = homeGeometry(411f, 900f, LayoutPreset(iconSize = 66f), labels = true)
        val small = homeGeometry(411f, 900f, LayoutPreset(iconSize = 50f), labels = true)
        assertTrue(small.iconSize < full.iconSize)
        assertEquals(full.rowHeight, small.rowHeight, 0f)
        assertEquals(full.widgetHeight, small.widgetHeight, 0f)
        assertEquals(full.box, small.box, 0f)
        assertEquals((small.box - small.iconSize) / 2f, small.iconInset, .01f)
    }
}

class IconSizeRangeTest {
    @org.junit.Test fun theIconSizeCanGoToAQuarterAboveTheDefaultAndNoFurther() {
        assertEquals(66f * 1.25f, ICON_SIZE_MAX, .01f)
        val big = homeGeometry(411f, 900f, LayoutPreset(iconSize = ICON_SIZE_MAX), labels = true)
        val normal = homeGeometry(411f, 900f, LayoutPreset(), labels = true)
        assertTrue(big.iconSize > normal.iconSize)
        assertTrue(big.iconSize <= normal.box * 1.25f + .01f)
        assertEquals(normal.rowHeight, big.rowHeight, 0f)
        assertTrue("a larger icon grows out of its box evenly", big.iconInset < 0f)
        val ios = iosHomeGeometry(393f, 852f, true, iconScale = 1.25f)
        assertEquals(ios.box * 1.25f, ios.iconSize, .01f)
        assertEquals(ios.box * 1.25f, iosHomeGeometry(393f, 852f, true, iconScale = 9f).iconSize, .01f)
    }
}
