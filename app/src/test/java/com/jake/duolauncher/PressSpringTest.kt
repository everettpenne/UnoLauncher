package com.jake.duolauncher

import com.jake.duolauncher.appearance.PressSpring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PressSpringTest {
    @Test fun `the one press spring matches the design contract`() {
        val spec = PressSpring.float()
        assertEquals(0.45f, spec.dampingRatio, 0.0001f)
        assertEquals(260f, spec.stiffness, 0.0001f)
    }

    @Test fun `the press spring is under-damped so a tap settles with one bounce`() {
        // Damping strictly below 1 (and above 0) overshoots once before settling: the iOS press feel.
        assertTrue(PressSpring.DAMPING_RATIO > 0f)
        assertTrue(PressSpring.DAMPING_RATIO < 1f)
    }

    @Test fun `the shared spec honours a custom visibility threshold`() {
        val spec = PressSpring.float(visibilityThreshold = 0.5f)
        assertEquals(0.5f, spec.visibilityThreshold ?: 0f, 0.0001f)
    }
}
