package com.jake.duolauncher

import com.jake.duolauncher.island.*
import org.junit.Assert.*
import org.junit.Test

class NavigationCueTest {
    private fun snap(street: String? = "Main Street", arrow: Int? = 1, meters: Int? = 900, key: String = "nav") =
        NavigationCue.Snapshot(key, street, arrow, meters)

    @Test fun distancesReadInMetresWhateverTheUnit() {
        assertEquals(300, NavigationCue.parseMeters("300 m"))
        assertEquals(300, NavigationCue.parseMeters("  300m"))
        assertEquals(500, NavigationCue.parseMeters("0.5 km"))
        assertEquals(1200, NavigationCue.parseMeters("1,2 km"))
        assertEquals(1609, NavigationCue.parseMeters("1 mi"))
        assertEquals(152, NavigationCue.parseMeters("500 ft"))
        assertEquals(91, NavigationCue.parseMeters("100 yards"))
        assertNull(NavigationCue.parseMeters("Turn left"))
        assertNull(NavigationCue.parseMeters(null))
        assertNull(NavigationCue.parseMeters(""))
    }

    @Test fun theFirstSightOfARouteAlwaysCues() {
        assertEquals(NavigationCue.Reason.STARTED, NavigationCue.reason(null, snap(), 0L))
        assertEquals(NavigationCue.Reason.STARTED, NavigationCue.reason(snap(key = "a"), snap(key = "b"), 0L))
    }

    @Test fun aNewArrowOrStreetCuesButTheSameInstructionDoesNot() {
        assertEquals(NavigationCue.Reason.TURN, NavigationCue.reason(snap(arrow = 1), snap(arrow = 2), 60_000L))
        assertEquals(NavigationCue.Reason.STREET, NavigationCue.reason(snap(street = "A"), snap(street = "B"), 60_000L))
        assertNull(NavigationCue.reason(snap(), snap(meters = 880), 60_000L))
        assertNull("no arrow on either side is not a change", NavigationCue.reason(snap(arrow = null), snap(arrow = null), 60_000L))
        assertNull("a missing street is not a change", NavigationCue.reason(snap(street = null), snap(street = "B"), 60_000L))
    }

    @Test fun theTurnComingCloseCuesOncePerThreshold() {
        assertEquals(NavigationCue.Reason.NEAR, NavigationCue.reason(snap(meters = 230), snap(meters = 190), 60_000L))
        assertEquals(NavigationCue.Reason.NEAR, NavigationCue.reason(snap(meters = 60), snap(meters = 45), 60_000L))
        assertNull("still on the same side of 200", NavigationCue.reason(snap(meters = 190), snap(meters = 180), 60_000L))
        assertNull("moving away does not cue", NavigationCue.reason(snap(meters = 190), snap(meters = 230), 60_000L))
        assertNull("unknown distances never cue", NavigationCue.reason(snap(meters = null), snap(meters = 40), 60_000L))
    }

    @Test fun aChattyAppCannotKeepTheIslandOpen() {
        assertNull(NavigationCue.reason(snap(arrow = 1), snap(arrow = 2), NavigationCue.MIN_GAP_MS - 1))
        assertNotNull(NavigationCue.reason(snap(arrow = 1), snap(arrow = 2), NavigationCue.MIN_GAP_MS))
        assertEquals("a route starting is never held back", NavigationCue.Reason.STARTED, NavigationCue.reason(null, snap(), 0L))
    }

    @Test fun onlyNavigationCarriesALargeIconAndTheDefaultsLeaveOtherKindsAlone() {
        val update = LiveUpdate("k", "p", "App", "t", null, null, 0, 0, false, null, false, null, 0, 0L, null)
        assertNull(update.largeIcon)
        assertEquals(ActivitySource.PROMOTED, update.source)
    }
}
