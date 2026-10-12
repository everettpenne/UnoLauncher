package com.jake.duolauncher

import android.app.WallpaperManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SystemWallpaperSyncInstrumentedTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)

    /** The switch is off by default, and turning it on hands the chosen background to both the home and lock screens. */
    @Test fun turningOnTheSwitchSetsHomeAndLockWallpaper() {
        val model = ViewModelProvider(compose.activity)[LauncherModel::class.java]
        compose.waitUntil(60_000) { !model.state.value.loading }
        runCatching { compose.onNodeWithText("Not now").performClick(); compose.waitForIdle() }
        val manager = WallpaperManager.getInstance(compose.activity)
        val before = manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM) to manager.getWallpaperId(WallpaperManager.FLAG_LOCK)
        compose.onNodeWithContentDescription("Home page 1").performClick()
        compose.waitForIdle()
        compose.openHomeCustomization()
        compose.onNodeWithTag("customization-wallpaper").performClick()
        val switch = compose.onNodeWithTag("system-wallpaper-sync-switch")
        switch.performScrollTo()
        assertFalse("off by default", compose.activity.getSharedPreferences("launcher_background", 0).getBoolean("systemWallpaperSync", false))
        assertEquals(before, manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM) to manager.getWallpaperId(WallpaperManager.FLAG_LOCK))
        switch.performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("background-message").fetchSemanticsNodes().isNotEmpty() }
        val message = compose.onNodeWithTag("background-message").fetchSemanticsNode().config
            .let { if (it.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) it[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { t -> t.text } else "" }
        assertTrue("message: $message", message.contains("set to match"))
        assertNotEquals(before, manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM) to manager.getWallpaperId(WallpaperManager.FLAG_LOCK))
        assertTrue(compose.activity.getSharedPreferences("launcher_background", 0).getBoolean("systemWallpaperSync", false))
    }
}
