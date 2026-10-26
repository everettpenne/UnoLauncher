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
}
