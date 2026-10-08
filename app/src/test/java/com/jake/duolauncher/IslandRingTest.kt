package com.jake.duolauncher

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

    @Test fun theArcCoversTheRequestedShareOfTheOutlineFromTheStart() {
        assertEquals(0f..50f, IslandRingLogic.arc(200f, .25f))
        assertEquals(0f..200f, IslandRingLogic.arc(200f, 5f))
        assertNull(IslandRingLogic.arc(200f, 0f))
        assertNull(IslandRingLogic.arc(0f, .5f))
    }
}
