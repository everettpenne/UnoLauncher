package com.jake.duolauncher

import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jake.duolauncher.appearance.*
import com.jake.duolauncher.search.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Dev aid: renders Spotlight with a few apps and saves screenshots to the app cache (pulled by hand). */
@RunWith(AndroidJUnit4::class)
class SpotlightShowcaseInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private fun app(label: String, color: Int) = AppEntry("p.${label.lowercase().replace(' ', '.')}/.Main", label,
        Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also { it.eraseColor(color) })

    @Test fun shots() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val apps = listOf(app("YouTube Music", AColor.RED), app("Google Maps", AColor.GREEN), app("Calculator", AColor.BLUE), app("Camera", AColor.MAGENTA))
        // Seed history so the empty query has suggestions.
        SpotlightHistory(context).also { it.record(apps[2].id); it.record(apps[2].id); it.record(apps[0].id) }
        compose.setContent {
            DuoTheme(false) {
                Spotlight(null, apps, emptySet(), emptyList(), false, HandoffResolver(context, ExtrasState()),
                    onLaunch = {}, onOpenFeedLink = {}, onOpenContact = {}, onDismiss = {}, progress = 1f, pill = null)
            }
        }
        compose.waitForIdle()
        fun shot(name: String) {
            compose.waitForIdle()
            instrumentation.uiAutomation.takeScreenshot()?.let { b ->
                File(context.cacheDir, "spot-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        shot("empty")
        compose.onNodeWithTag("spotlight-field").performTextInput("ca")
        shot("ca")
        compose.onNodeWithTag("spotlight-field").performTextInput("lc")
        shot("calc")
    }
}
