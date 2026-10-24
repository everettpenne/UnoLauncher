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
        // The outline starts at the top centre, so its bottom centre is at 100 of 200.
        assertEquals(75f..125f, IslandRingLogic.arc(200f, .25f))
        assertEquals(0f..200f, IslandRingLogic.arc(200f, 5f))
        val r = IslandRingLogic.arc(200f, .69f)!!
        assertEquals("symmetric about the bottom", 100f - r.start, r.endInclusive - 100f, .001f)
        assertNull(IslandRingLogic.arc(200f, 0f))
        assertNull(IslandRingLogic.arc(0f, .5f))
    }
}
