package com.jake.duolauncher

import com.jake.duolauncher.widgets.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTimeWidgetTest {
    private fun usage(pkg: String, minutes: Int, label: String? = null) = ScreenTime.AppUsage(pkg, label, minutes)

    @Test fun topAppsDropsTinyUseAndRepeatsAndSortsDescending() {
        val usages = listOf(
            usage("com.example.b", 45),
            usage("com.example.a", 74),
            usage("com.example.a", 10),   // same app twice (a second stat bucket)
            usage("com.example.c", 0),    // under a minute
            usage("com.example.d", 21),
        )
        assertEquals(listOf("com.example.a", "com.example.b", "com.example.d"),
            ScreenTime.topApps(usages).map { it.packageName })
    }

    @Test fun totalMinutesSumsEveryApp() {
        assertEquals(119, ScreenTime.totalMinutes(listOf(usage("a", 74), usage("b", 45))))
        assertEquals(0, ScreenTime.totalMinutes(emptyList()))
    }

    @Test fun displayNameFallsBackToThePackage() {
        assertEquals("Browser", ScreenTime.displayName(usage("com.example.browser", 1, "Browser")))
        assertEquals("browser", ScreenTime.displayName(usage("com.example.browser", 1, "  ")))
        assertEquals("browser", ScreenTime.displayName(usage("com.example.browser", 1)))
    }

    @Test fun durationsReadPlainly() {
        assertEquals("0 min", ScreenTime.formatDuration(0))
        assertEquals("45 min", ScreenTime.formatDuration(45))
        assertEquals("2 h", ScreenTime.formatDuration(120))
        assertEquals("3 h 12 min", ScreenTime.formatDuration(192))
    }

    @Test fun goalReadsFromTheSettingAndFallsBack() {
        assertEquals(180, ScreenTime.goalMinutes(null))
        assertEquals(120, ScreenTime.goalMinutes("120"))
        assertEquals("out of range falls back", 180, ScreenTime.goalMinutes("5"))
        assertEquals(180, ScreenTime.goalMinutes("garbage"))
    }

    @Test fun goalProgressClampsAndOverGoalDetects() {
        assertEquals(0.5f, ScreenTime.goalProgress(90, 180), 1e-4f)
        assertEquals(1f, ScreenTime.goalProgress(300, 180), 1e-4f)
        assertEquals(0f, ScreenTime.goalProgress(90, 0), 1e-4f)
        assertTrue(ScreenTime.overGoal(181, 180))
        assertFalse(ScreenTime.overGoal(180, 180))
    }
}
