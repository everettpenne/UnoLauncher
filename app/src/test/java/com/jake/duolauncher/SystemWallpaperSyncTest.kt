package com.jake.duolauncher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.jake.duolauncher.appearance.SheetDismissGuard
import com.jake.duolauncher.appearance.SystemWallpaperSync
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SystemWallpaperSyncTest {
    @Test fun aChangeOfDayOrNightIsSentOnceAndOnlyWhenOn() {
        assertTrue(SystemWallpaperSync.needsPush(true, true, false, true))
        assertTrue(SystemWallpaperSync.needsPush(true, true, true, false))
        assertFalse("already sent", SystemWallpaperSync.needsPush(true, true, true, true))
        assertTrue("never recorded", SystemWallpaperSync.needsPush(true, true, null, false))
        assertFalse("switch off", SystemWallpaperSync.needsPush(false, true, null, true))
        assertFalse("a photo looks the same by day and night", SystemWallpaperSync.needsPush(true, false, null, true))
    }

    @Test fun theSheetGuardSwallowsOnlyDownwardLeftovers() = runBlocking {
        val guard = SheetDismissGuard(flingDismissVelocity = 1000f)
        assertEquals(Offset(0f, 40f), guard.onPostScroll(Offset.Zero, Offset(5f, 40f), NestedScrollSource.UserInput))
        assertEquals(Offset.Zero, guard.onPostScroll(Offset.Zero, Offset(0f, -40f), NestedScrollSource.UserInput))
        // A slow leftover fling is absorbed (a lazy scroll-past-top never closes the sheet) ...
        assertEquals(Velocity(0f, 900f), guard.onPostFling(Velocity.Zero, Velocity(10f, 900f)))
        // ... but a decisive downward flick passes through to dismiss the sheet.
        assertEquals(Velocity.Zero, guard.onPostFling(Velocity.Zero, Velocity(10f, 1500f)))
        assertEquals(Velocity.Zero, guard.onPostFling(Velocity.Zero, Velocity(0f, -900f)))
    }
}
