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
}
