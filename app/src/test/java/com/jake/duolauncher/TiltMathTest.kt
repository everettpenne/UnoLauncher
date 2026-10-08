package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class TiltMathTest {
    @Test fun uprightKeepsTheLightWhereItStarts() {
        assertEquals(TiltMath.BASE_ANGLE, TiltMath.angle(0f, 9.81f, 0f), .001f)
    }

    @Test fun lyingFlatFadesTheTiltOutInsteadOfJittering() {
        assertEquals(TiltMath.BASE_ANGLE, TiltMath.angle(.4f, .3f, 9.8f), .001f)
        assertEquals(TiltMath.BASE_ANGLE, TiltMath.angle(-.6f, -.2f, 9.8f), .001f)
    }

    @Test fun rollingMovesTheLightAndTheOppositeRollMovesItTheOtherWay() {
        val right = TiltMath.angle(6.9f, 6.9f, 0f)
        val left = TiltMath.angle(-6.9f, 6.9f, 0f)
        assertEquals(TiltMath.BASE_ANGLE + 45f, right, .5f)
        assertEquals(TiltMath.BASE_ANGLE - 45f, left, .5f)
    }

    @Test fun theSwingIsClampedSoLandscapeDoesNotSpinTheRim() {
        assertEquals(TiltMath.BASE_ANGLE + TiltMath.MAX_SWING, TiltMath.angle(9.81f, 0f, 0f), .001f)
        assertEquals(TiltMath.BASE_ANGLE - TiltMath.MAX_SWING, TiltMath.angle(-9.81f, 0f, 0f), .001f)
        assertEquals(TiltMath.BASE_ANGLE + TiltMath.MAX_SWING, TiltMath.angle(1f, -9.8f, 0f), .001f)
    }

    @Test fun smoothingMovesPartWayAndSmallChangesAreNotWorthARedraw() {
        assertEquals(10f, TiltMath.smooth(0f, 100f, .1f), .001f)
        assertEquals(50f, TiltMath.smooth(50f, 50f), .001f)
        assertFalse(TiltMath.worthUpdating(45f, 46f))
        assertTrue(TiltMath.worthUpdating(45f, 47f))
        assertTrue(TiltMath.worthUpdating(45f, 43f))
    }
}
