package com.jake.duolauncher

import com.jake.duolauncher.widgets.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidBatteryWidgetTest {
    private val g = 9.81f

    @Test fun fillScalesAndClamps() {
        assertEquals(0.78f, LiquidBattery.fill(78), 1e-4f)
        assertEquals(0f, LiquidBattery.fill(-1), 1e-4f)
        assertEquals(0f, LiquidBattery.fill(0), 1e-4f)
        assertEquals(1f, LiquidBattery.fill(120), 1e-4f)
    }

    @Test fun tiltIsZeroWhenFlatAndLevelWhenUpright() {
        // Lying flat: no "sideways", so the surface stays level.
        assertEquals(0f, LiquidBattery.tilt(0f, 0f, g), 1e-3f)
        // Upright portrait: gravity along +Y is also level.
        assertEquals(0f, LiquidBattery.tilt(0f, g, 0f), 1e-3f)
    }

    @Test fun tiltLeansAgainstTheRoll() {
        // Rolled onto its side, gravity along +X: the surface leans the other way.
        assertEquals(-1f, LiquidBattery.tilt(g, 0f, 0f), 1e-3f)
        assertEquals(1f, LiquidBattery.tilt(-g, 0f, 0f), 1e-3f)
        assertTrue("the tilt stays bounded", LiquidBattery.tilt(g, 0f, 0f) in -1f..1f)
    }

    @Test fun smoothingMovesPartWayTowardTheNewValue() {
        assertEquals(.3f, LiquidBattery.smooth(0f, 1f, .3f), 1e-4f)
        assertEquals(.35f, LiquidBattery.smooth(.5f, 0f, .3f), 1e-4f)
    }

    @Test fun theSurfaceIsFlatAtTheFillLevelWithoutTiltOrWave() {
        val fill = .5f
        assertEquals(fill, LiquidBattery.surfaceY(0f, fill, 0f, 0f, 0f), 1e-4f)
        assertEquals(fill, LiquidBattery.surfaceY(.5f, fill, 0f, 0f, 0f), 1e-4f)
        assertEquals(fill, LiquidBattery.surfaceY(1f, fill, 0f, 0f, 0f), 1e-4f)
    }

    @Test fun theSurfaceTiltsAndRipples() {
        // Tilt +1 lifts the right edge and lowers the left.
        val left = LiquidBattery.surfaceY(0f, .5f, 1f, 0f, 0f)
        val right = LiquidBattery.surfaceY(1f, .5f, 1f, 0f, 0f)
        assertTrue("the right edge is higher than the left", right > left)
        // The wave oscillates around the fill level: at x=0.25 with phase 0 the sine is at a crest.
        val crest = LiquidBattery.surfaceY(.25f, .5f, 0f, .05f, 0f)
        assertTrue("a crest rises above the fill", crest > .5f)
    }

    @Test fun surfacePointsSpanTheWidthFromEndToEnd() {
        val points = LiquidBattery.surfacePoints(.5f, 0f, 0f, 0f)
        assertEquals(LiquidBattery.SEGMENTS + 1, points.size)
        assertEquals(0f, points.first().first, 1e-4f)
        assertEquals(1f, points.last().first, 1e-4f)
        assertTrue(points.all { (x, y) -> x in 0f..1f && y in 0f..1f })
    }

    @Test fun theLiquidChangesColourWithTheCharge() {
        assertEquals(androidx.compose.ui.graphics.Color(0xFF34C759), LiquidBattery.liquidColor(30, true, null))
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFF453A), LiquidBattery.liquidColor(8, false, null))
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFF9500), LiquidBattery.liquidColor(15, false, null))
        val accent = androidx.compose.ui.graphics.Color(0xFF123456)
        assertEquals(accent, LiquidBattery.liquidColor(80, false, accent))
        assertEquals(androidx.compose.ui.graphics.Color(0xFF38A8FF), LiquidBattery.liquidColor(80, false, null))
    }

    @Test fun bubblesStayInsideTheLiquidAndKeepMoving() {
        for (p in listOf(0f, .25f, .5f, .99f)) LiquidBattery.bubbles(p).forEach { b ->
            assertTrue(b.x in 0f..1f); assertTrue(b.y in 0f..1f); assertTrue(b.size > 0f)
        }
        assertNotEquals(LiquidBattery.bubbles(0f).map { it.y }, LiquidBattery.bubbles(.3f).map { it.y })
    }
}
