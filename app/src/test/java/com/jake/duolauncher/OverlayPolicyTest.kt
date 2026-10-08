package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class OverlayPolicyTest {
    @Test fun overlayShowsOnlyWhenEverythingHolds() {
        assertTrue(OverlayPolicy.shouldShow(true, true, false, true))
    }

    @Test fun overlayStaysDownWithoutPermissionOnLockScreenOrWithScreenOff() {
        assertFalse(OverlayPolicy.shouldShow(false, true, false, true))
        assertFalse(OverlayPolicy.shouldShow(true, false, false, true))
        assertFalse(OverlayPolicy.shouldShow(true, true, true, true))
        assertFalse(OverlayPolicy.shouldShow(true, true, false, false))
    }
}
