package com.jake.duolauncher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeEditLogicTest {
    @Test fun wiggleStaysSmallAndNeverStopsMoving() {
        val angles = (0..40).map { HomeEditLogic.wiggleAngle(it / 40f, seed = 3) }
        assertTrue(angles.all { kotlin.math.abs(it) <= HomeEditLogic.WIGGLE_DEGREES + 1e-4f })
        assertTrue("it actually moves", angles.max() - angles.min() > HomeEditLogic.WIGGLE_DEGREES)
    }

    @Test fun neighbouringIconsAreOutOfStepAndLeanOppositeWays() {
        assertNotEquals(HomeEditLogic.wiggleAngle(.1f, 1), HomeEditLogic.wiggleAngle(.1f, 2), 1e-4f)
        assertTrue(HomeEditLogic.wiggleAngle(.25f, 2) * HomeEditLogic.wiggleAngle(.25f, 3) <= 0.5f)
    }

    @Test fun negativeSeedsAreSafe() {
        assertTrue(HomeEditLogic.wiggleAngle(.3f, -5).isFinite())
        assertTrue(HomeEditLogic.wiggleAngle(.3f, Int.MIN_VALUE).isFinite())
    }

    private val a = Rect(0f, 0f, 100f, 100f)
    private val b = Rect(100f, 0f, 200f, 100f)
    private val c = Rect(0f, 100f, 100f, 200f)

    @Test fun distanceIsZeroInsideAndGrowsOutside() {
        assertEquals(0f, HomeEditLogic.distance(Offset(50f, 50f), a), 0f)
        assertEquals(30f, HomeEditLogic.distance(Offset(130f, 50f), a), 1e-4f)
        assertEquals(50f, HomeEditLogic.distance(Offset(130f, 140f), a), 1e-4f)   // 30 right, 40 below: 3-4-5
    }

    @Test fun aMissedDropLandsInTheNearestCellWithinReach() {
        val cells = listOf("a" to a, "b" to b, "c" to c)
        assertEquals("b", HomeEditLogic.nearest(Offset(210f, 50f), cells, maxDistance = 20f))
        assertEquals("c", HomeEditLogic.nearest(Offset(40f, 215f), cells, maxDistance = 20f))
        assertEquals("a", HomeEditLogic.nearest(Offset(-5f, -5f), cells, maxDistance = 20f))
    }

    @Test fun aDropFarFromEverythingIsNotPulledInAndNothingIsHandledSafely() {
        assertNull(HomeEditLogic.nearest(Offset(500f, 500f), listOf("a" to a), maxDistance = 20f))
        assertNull(HomeEditLogic.nearest(Offset(1f, 1f), emptyList<Pair<String, Rect>>(), maxDistance = 20f))
    }
}
