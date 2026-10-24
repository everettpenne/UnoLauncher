package com.jake.duolauncher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jake.duolauncher.appearance.*
import com.jake.duolauncher.search.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaterialYouAndSpotlightSourcesInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** Material You was folded into Accent › From wallpaper (0.104): someone who had it on keeps a wallpaper accent, and it is not saved again. */
    @Test fun materialYouBecomesTheWallpaperAccent() {
        val prefs = context.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE)
        val before = prefs.all.toMap()
        try {
            prefs.edit().putBoolean("materialYou", true).putBoolean("wallpaperColor", false).commit()
            val store = AppearanceStore(context)
            assertTrue(store.state.wallpaperColor)
            store.setWallpaperColor(false)
            assertTrue(!prefs.contains("materialYou"))
            assertTrue(!AppearanceStore(context).state.wallpaperColor)
        } finally {
            prefs.edit().clear().apply { before.forEach { (k, v) -> when (v) { is Boolean -> putBoolean(k, v); is String -> putString(k, v)
                is Float -> putFloat(k, v); is Int -> putInt(k, v); is Long -> putLong(k, v) } } }.commit()
        }
    }

    private fun spotlight() = compose.setContent {
        DuoTheme(false) {
            Spotlight(null, emptyList(), emptySet(), emptyList(), false, HandoffResolver(context, ExtrasState()),
                onLaunch = {}, onOpenFeedLink = {}, onOpenContact = {}, onDismiss = {})
        }
    }

    @Test fun spotlightOffersSettingsPagesAndQuickActions() {
        spotlight()
        compose.onNodeWithTag("spotlight-field").performTextInput("wifi")
        compose.waitForIdle()
        compose.onNodeWithTag("spotlight-settings-wi-fi").assertIsDisplayed()
    }

    @Test fun spotlightTurnsATimerQueryIntoARow() {
        spotlight()
        compose.onNodeWithTag("spotlight-field").performTextInput("timer 5")
        compose.waitForIdle()
        compose.onNodeWithTag("spotlight-quick").assertIsDisplayed()
    }
}
