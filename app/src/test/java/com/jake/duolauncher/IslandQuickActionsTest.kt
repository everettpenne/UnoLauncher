package com.jake.duolauncher

import com.jake.duolauncher.island.IslandQuickActions
import com.jake.duolauncher.island.LiveKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A long press on the pill offers the leading activity's own buttons, as iOS does. */
class IslandQuickActionsTest {
    @Test fun eachActivityOffersItsOwnButtons() {
        assertEquals(listOf("+1 Min", "Cancel"), IslandQuickActions.titles(LiveKind.TIMER))
        assertEquals(listOf("Pause", "Reset"), IslandQuickActions.titles(LiveKind.STOPWATCH, stopwatchRunning = true))
        assertEquals(listOf("Resume", "Reset"), IslandQuickActions.titles(LiveKind.STOPWATCH))
        assertEquals(listOf("Previous", "Pause", "Next"), IslandQuickActions.titles(LiveKind.MEDIA, playing = true))
        assertEquals(listOf("Previous", "Play", "Next"), IslandQuickActions.titles(LiveKind.MEDIA))
        assertEquals(listOf("End", "Open"), IslandQuickActions.titles(LiveKind.CALL, canHangUp = true, canOpen = true))
        assertEquals(listOf("Open"), IslandQuickActions.titles(LiveKind.UPDATE, canOpen = true))
    }

    @Test fun nothingToOfferLeavesTheOldLongPress() {
        assertTrue(IslandQuickActions.titles(LiveKind.UPDATE).isEmpty())
        assertTrue(IslandQuickActions.titles(LiveKind.CAMERA).isEmpty())
        assertTrue(IslandQuickActions.titles(LiveKind.CALL).isEmpty())
    }
}
