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

    @Test fun theOverlayWindowIsAbsolutelyPositionedAndStillTouchSafe() {
        val flags = OverlayPolicy.WINDOW_FLAGS
        val wm = android.view.WindowManager.LayoutParams::class.java
        fun has(name: String) = flags and wm.getField(name).getInt(null) != 0
        assertTrue("positions are screen pixels, not relative to below the status bar", has("FLAG_LAYOUT_IN_SCREEN") && has("FLAG_LAYOUT_NO_LIMITS"))
        assertTrue("never takes focus or the touches outside its own bounds", has("FLAG_NOT_FOCUSABLE") && has("FLAG_NOT_TOUCH_MODAL"))
        assertFalse(has("FLAG_FULLSCREEN"))
        assertTrue("hears of touches elsewhere so an expanded island can tuck away", has("FLAG_WATCH_OUTSIDE_TOUCH"))
    }
}
