package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class PullDownRoutingTest {
    private fun r(mode: PullDown, panel: ShadePanel, y: Float) = PullDownRouting.route(mode, panel, y)

    @Test fun likeIosTheTopEdgeKeepsTheShadesAndEverythingLowerSearches() {
        assertEquals(PullRoute.NOTIFICATIONS, r(PullDown.SMART, ShadePanel.NOTIFICATIONS, .05f))
        assertEquals(PullRoute.QUICK_SETTINGS, r(PullDown.SMART, ShadePanel.QUICK_SETTINGS, .05f))
        assertEquals(PullRoute.NOTIFICATIONS, r(PullDown.SMART, ShadePanel.NOTIFICATIONS, PullDownRouting.TOP_ZONE))
        assertEquals(PullRoute.SEARCH, r(PullDown.SMART, ShadePanel.NOTIFICATIONS, .5f))
        assertEquals(PullRoute.SEARCH, r(PullDown.SMART, ShadePanel.NOTIFICATIONS, .95f))
        assertEquals(PullRoute.SEARCH, r(PullDown.SMART, ShadePanel.QUICK_SETTINGS, .6f))
    }

    @Test fun theOldModesBehaveAsBefore() {
        assertEquals(PullRoute.NOTIFICATIONS, r(PullDown.NOTIFICATIONS, ShadePanel.NOTIFICATIONS, .9f))
        assertEquals(PullRoute.QUICK_SETTINGS, r(PullDown.NOTIFICATIONS, ShadePanel.QUICK_SETTINGS, .9f))
        assertEquals(PullRoute.SEARCH, r(PullDown.SEARCH, ShadePanel.NOTIFICATIONS, .01f))
        assertEquals(PullRoute.QUICK_SETTINGS, r(PullDown.SEARCH, ShadePanel.QUICK_SETTINGS, .01f))
    }

    @Test fun anOlderSavedChoiceMigratesAndEveryoneElseGetsTheNewDefault() {
        assertEquals(PullDown.SMART, PullDownRouting.migrate(null, null))
        assertEquals(PullDown.SMART, PullDownRouting.migrate(null, "NOTIFICATIONS"))
        assertEquals(PullDown.SEARCH, PullDownRouting.migrate(null, "SEARCH"))
        assertEquals(PullDown.NOTIFICATIONS, PullDownRouting.migrate("NOTIFICATIONS", "SEARCH"))
        assertEquals(PullDown.SMART, PullDownRouting.migrate("garbage", null))
    }
}
