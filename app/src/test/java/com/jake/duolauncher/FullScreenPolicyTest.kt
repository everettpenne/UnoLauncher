package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class FullScreenPolicyTest {
    @Test fun theIslandHidesOnlyAfterTheBarHasBeenGoneAMoment() {
        assertFalse(FullScreenPolicy.shouldHide(statusBarVisible = true, goneForMs = 10_000L))
        assertFalse(FullScreenPolicy.shouldHide(statusBarVisible = false, goneForMs = 0L))
        assertFalse(FullScreenPolicy.shouldHide(statusBarVisible = false, goneForMs = FullScreenPolicy.HIDE_AFTER_MS - 1))
        assertTrue(FullScreenPolicy.shouldHide(statusBarVisible = false, goneForMs = FullScreenPolicy.HIDE_AFTER_MS))
    }
}
