package com.jake.duolauncher

import com.jake.duolauncher.island.*

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class IslandRingTest {
    private val orange = Color(0xFFFF9F0A); private val green = Color(0xFF34C759)
    private fun pick(flash: Boolean = false, expanded: Boolean = false, ringing: Boolean = false, timer: Boolean = false,
        tf: Float = .5f, charging: Boolean = false, battery: Int? = 80) =
        IslandRingLogic.choose(flash, expanded, ringing, timer, tf, charging, battery, orange, green)

    @Test fun aTimerBeatsChargingAndEventsHideTheRing() {
        assertEquals(orange, pick(timer = true, charging = true)?.color)
        assertEquals(green, pick(charging = true)?.color)
        assertEquals(.8f, pick(charging = true, battery = 80)!!.fraction, .001f)
        assertNull(pick(timer = true, flash = true))
        assertNull(pick(timer = true, expanded = true))
        assertNull(pick(timer = true, ringing = true))
        assertNull(pick())
        assertNull(pick(charging = true, battery = null))
    }

    @Test fun fractionsAreClamped() {
        assertEquals(1f, pick(timer = true, tf = 3f)!!.fraction, 0f)
        assertEquals(1f, pick(charging = true, battery = 140)!!.fraction, 0f)
    }

    @Test fun theArcIsCentredOnTheBottomAndGrowsEvenlyUpBothSides() {
        assertEquals(listOf(75f..125f), IslandRingLogic.arcs(200f, 100f, .25f))
        assertEquals(listOf(0f..200f), IslandRingLogic.arcs(200f, 100f, 5f))
        val r = IslandRingLogic.arcs(200f, 100f, .69f).single()
        assertEquals("symmetric about the bottom", 100f - r.start, r.endInclusive - 100f, .001f)
        assertTrue(IslandRingLogic.arcs(200f, 100f, 0f).isEmpty())
        assertTrue(IslandRingLogic.arcs(0f, 0f, .5f).isEmpty())
    }

    @Test fun anArcRunningPastTheOutlinesStartIsSplitInTwo() {
        // The outline may start anywhere: with its bottom centre 10 along, a quarter (50) runs from 185 round to 35.
        val parts = IslandRingLogic.arcs(200f, 10f, .25f)
        assertEquals(listOf(0f..35f, 185f..200f), parts)
        assertEquals(50f, parts.sumOf { (it.endInclusive - it.start).toDouble() }.toFloat(), .001f)
        assertEquals(listOf(180f..200f, 0f..10f), IslandRingLogic.arcs(200f, 195f, .15f))
    }

    @Test fun theBottomCentreIsFoundAlongTheOutline() {
        // A 100-long loop whose point nearest the target is 37 along.
        assertEquals(37f, IslandRingLogic.closestAlong(100f) { (it - 37f) * (it - 37f) }, .1f)
        assertEquals(0f, IslandRingLogic.closestAlong(0f) { it }, 0f)
    }
}
