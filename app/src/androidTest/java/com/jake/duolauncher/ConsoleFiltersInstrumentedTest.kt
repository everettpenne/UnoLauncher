package com.jake.duolauncher

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jake.duolauncher.widgets.ConsoleListeners
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** A data scheme applies to every action in its filter, so the Console must keep package events apart from the data-less ones. */
@RunWith(AndroidJUnit4::class)
class ConsoleFiltersInstrumentedTest {
    @Test fun deviceEventsNeedNoDataAndPackageEventsAreInTheirOwnFilter() {
        val device = ConsoleListeners.deviceFilter()
        assertEquals("the device filter carries no data scheme", 0, device.countDataSchemes())
        for (action in listOf(Intent.ACTION_SCREEN_ON, Intent.ACTION_POWER_CONNECTED, Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
            assertTrue("$action is heard", device.matchAction(action))
            assertTrue("$action matches a plain intent with no data", device.match(null, Intent(action), false, "test") >= 0)
        }
        val packages = ConsoleListeners.packageFilter()
        assertEquals(1, packages.countDataSchemes())
        assertEquals("package", packages.getDataScheme(0))
        assertTrue(packages.matchAction(Intent.ACTION_PACKAGE_ADDED))
        assertFalse(packages.matchAction(Intent.ACTION_SCREEN_ON))
    }
}
