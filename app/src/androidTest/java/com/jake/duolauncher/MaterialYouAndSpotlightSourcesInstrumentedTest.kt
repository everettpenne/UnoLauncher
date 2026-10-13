package com.jake.duolauncher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jake.duolauncher.appearance.*
import com.jake.duolauncher.search.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaterialYouAndSpotlightSourcesInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun materialYouThemesMaterialWithThePhonesDynamicPalette() {
        var primary = Color.Unspecified
        var unoPrimary = Color.Unspecified
        compose.setContent {
            DuoTheme(dark = false, materialYou = true) { primary = MaterialTheme.colorScheme.primary }
            DuoTheme(dark = false, materialYou = false) { unoPrimary = MaterialTheme.colorScheme.primary }
        }
        compose.waitForIdle()
        assertEquals(dynamicLightColorScheme(context).primary, primary)
        assertEquals(Color(0xFF30596D), unoPrimary)
        assertNotEquals(unoPrimary, primary)
    }

    @Test fun materialYouIsSavedAndRestored() {
        val store = AppearanceStore(context)
        val before = store.state.materialYou
        try {
            store.setMaterialYou(true)
            assertTrue(AppearanceStore(context).state.materialYou)
        } finally { store.setMaterialYou(before) }
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
