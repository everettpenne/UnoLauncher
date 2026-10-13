package com.jake.duolauncher

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Entering a place by hand, and "Use device location", on the Appearance settings. */
class PlaceEntryInstrumentedTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)

    private fun open() {
        val model = ViewModelProvider(compose.activity)[LauncherModel::class.java]
        compose.waitUntil(90_000) { !model.state.value.loading }
        runCatching { compose.onNodeWithText("Not now").performClick(); compose.waitForIdle() }
        compose.onNodeWithContentDescription("Home page 1").performClick()
        compose.waitForIdle()
        compose.openHomeCustomization()
        compose.onNodeWithTag("customization-wallpaper").performClick()
        compose.onNodeWithTag("appearance-sunrise_sunset").performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun text(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config
        .let { if (it.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) it[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { t -> t.text } else "" }

    @Test fun aPastedPairIsUnderstoodAndConfirmedWithSunTimes() {
        open()
        compose.onNodeWithTag("appearance-place").performScrollTo().performTextReplacement("Paris")
        compose.onNodeWithTag("appearance-latitude").performScrollTo().performTextReplacement("48,8566; 2,3522")
        compose.onNodeWithTag("appearance-save-place").performScrollTo().performClick()
        try {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("appearance-saved-place").fetchSemanticsNodes().isNotEmpty() && text("appearance-saved-place").startsWith("Saved: Paris")
            }
        } catch (e: Throwable) {
            val shown = if (compose.onAllNodesWithTag("appearance-saved-place").fetchSemanticsNodes().isEmpty()) "(no saved-place line)" else text("appearance-saved-place")
            val err = if (compose.onAllNodesWithTag("appearance-manual-status").fetchSemanticsNodes().isEmpty()) "(none)" else text("appearance-manual-status")
            fail("saved line: $shown; error line: $err; place=" + compose.onNodeWithTag("appearance-place").fetchSemanticsNode().config.toString())
        }
        val saved = text("appearance-saved-place")
        assertTrue(saved, saved.startsWith("Saved: Paris, 48.9° N, 2.4° E."))
        assertTrue(saved, saved.contains("Sunrise") && saved.contains("sunset"))
        assertTrue(compose.onAllNodesWithTag("appearance-manual-status").fetchSemanticsNodes().isEmpty())
    }

    @Test fun badInputShowsTheRangeMessage() {
        open()
        compose.onNodeWithTag("appearance-latitude").performScrollTo().performTextInput("abc")
        compose.onNodeWithTag("appearance-save-place").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("appearance-manual-status").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(text("appearance-manual-status").contains("−90 to 90"))
    }

    /** Needs the location permission granted and a position set with `adb emu geo fix` before it runs. */
    @Test fun deviceLocationCompletesAndIsConfirmed() {
        // Skipped, not failed, when the permission is not granted (a cleared app starts without it).
        org.junit.Assume.assumeTrue("needs ACCESS_FINE_LOCATION granted with adb",
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
                .checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED)
        open()
        compose.onNodeWithTag("appearance-device-location").performScrollTo().performClick()
        compose.waitUntil(120_000) {
            compose.onAllNodesWithTag("appearance-saved-place").fetchSemanticsNodes().isNotEmpty() &&
                text("appearance-saved-place").startsWith("Saved: Device location (rounded), ")
        }
        assertTrue(text("appearance-saved-place"), text("appearance-saved-place").contains("40.7° N, 74.0° W"))
    }
}
