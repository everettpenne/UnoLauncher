package com.jake.duolauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class IslandWideModeTest {
    @Test fun fromIntMapsTheThreeStatesAndAnythingElseIsOff() {
        assertEquals(IslandWideMode.OFF, IslandWideMode.fromInt(0))
        assertEquals(IslandWideMode.WIDE, IslandWideMode.fromInt(1))
        assertEquals(IslandWideMode.EXTRA_WIDE, IslandWideMode.fromInt(2))
        assertEquals(IslandWideMode.OFF, IslandWideMode.fromInt(3))
        assertEquals(IslandWideMode.OFF, IslandWideMode.fromInt(-1))
    }

    @Test fun aNewIntPrefWinsOverTheOldBoolean() {
        assertEquals(IslandWideMode.EXTRA_WIDE, IslandWideMode.read(2, true))
        assertEquals(IslandWideMode.OFF, IslandWideMode.read(0, true))
        assertEquals(IslandWideMode.WIDE, IslandWideMode.read(1, false))
    }

    @Test fun withoutANewPrefTheOldBooleanMigratesTrueToWide() {
        assertEquals(IslandWideMode.WIDE, IslandWideMode.read(null, true))
        assertEquals(IslandWideMode.OFF, IslandWideMode.read(null, false))
    }

    @Test fun keepWideAndWideFactorMatchTheDesign() {
        assertEquals(false, IslandWideMode.OFF.keepWide)
        assertEquals(true, IslandWideMode.WIDE.keepWide)
        assertEquals(true, IslandWideMode.EXTRA_WIDE.keepWide)
        assertEquals(1f, IslandWideMode.WIDE.wideFactor)
        assertEquals(1.5f, IslandWideMode.EXTRA_WIDE.wideFactor)
    }
}
