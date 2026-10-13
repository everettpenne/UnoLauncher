package com.jake.duolauncher

import com.jake.duolauncher.island.*
import com.jake.duolauncher.widgets.*

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** The glass menus: the Photos widget's settings and the island style page, driven and photographed. */
class GlassMenusInstrumentedTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun present(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun shot(name: String) {
        compose.waitForIdle(); Thread.sleep(800)
        val b = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = compose.activity.getExternalFilesDir(null)!!
        File(dir, "menu-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun ready() {
        compose.waitUntil(90_000) { !model().state.value.loading }
        runCatching { compose.onNodeWithText("Not now").performClick(); compose.waitForIdle() }
    }

    @Test fun photosMenuIsGlassAndWorks() {
        ready()
        val layoutBefore = model().state.value.layout
        try { photosMenuBody() } finally { compose.runOnIdle { model().restoreLayout(layoutBefore) } }
    }

    private fun photosMenuBody() {
        compose.runOnIdle { model().state.value.layout.widgetPlacements.map { it.slot }.forEach { model().removePlacement(DropTarget.Widget(it)) } }
        compose.waitForIdle()
        val layout = model().state.value.layout
        val index = (0 until HOME_CELLS).first { widgetCandidate(layout, 0, it, 2, 2) != null }
        compose.runOnIdle { assertTrue(model().placeWidget(widgetCandidate(layout, 0, index, 2, 2)!!.copy(id = UnoWidgets.PHOTOS))) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Home page 1").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithTag("uno-photos-0", useUnmergedTree = true)[0].performClick()
        compose.waitUntil(10_000) { present("photos-preview-settings") }
        compose.onNodeWithTag("photos-preview-settings", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { present("photos-menu") }
        shot("photos")
        compose.onNodeWithTag("photos-interval-30", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("30", WidgetData.get(0, "interval"))
        compose.onNodeWithTag("photos-done", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertFalse(present("photos-menu"))
    }

    @Test fun islandStyleChangesTheIslandTheme() {
        ready()
        compose.onNodeWithContentDescription("Home page 1").performClick()
        compose.waitForIdle()
        compose.openHomeCustomization()
        compose.onNodeWithTag("customization-island").performScrollTo().performClick()
        compose.waitUntil(10_000) { present("island-style") }
        compose.onNodeWithTag("island-style").performScrollTo()
        IslandThemes.reset()
        compose.onNodeWithTag("island-preset-Neon", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("Neon", IslandThemes.active.name)
        shot("island-style")
        compose.onNodeWithTag("island-panel-SQUARE", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("Custom", IslandThemes.active.name)
        assertEquals(PanelShape.SQUARE, IslandThemes.active.panel)
        // Close settings and look at the real island in the custom theme, collapsed and tapped open.
        IslandThemes.apply(IslandThemes.presets.first { it.name == "Neon" })
        compose.onNodeWithContentDescription("Close settings").performClick()
        compose.waitForIdle()
        shot("island-neon")
        val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.click(device.displayWidth / 2, 66)
        shot("island-neon-open")
        compose.onNodeWithContentDescription("Home page 1").performClick()
        compose.openHomeCustomization()
        compose.onNodeWithTag("customization-island").performScrollTo().performClick()
        compose.waitUntil(10_000) { present("island-style") }
        compose.onNodeWithTag("island-theme-reset", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("Classic", IslandThemes.active.name)
    }

    /** After an update the launcher shows its "what's new" note once; a second launch of the same version does not. */
    @Test fun whatsNewAppearsOnceAfterAnUpdate() {
        ready()
        val prefs = compose.activity.getSharedPreferences("whats_new", 0)
        val version = compose.activity.packageManager.getPackageInfo(compose.activity.packageName, 0).versionName!!
        prefs.edit().putString("seen", "0.0.1-old").commit()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(90_000) { !ViewModelProvider(compose.activity)[LauncherModel::class.java].state.value.loading }
        compose.waitUntil(15_000) { present("whats-new") }
        shot("whats-new")
        compose.onNodeWithTag("whats-new-done", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertFalse(present("whats-new"))
        assertEquals(version, prefs.getString("seen", null))
        compose.activityRule.scenario.recreate()
        compose.waitUntil(90_000) { !ViewModelProvider(compose.activity)[LauncherModel::class.java].state.value.loading }
        compose.waitForIdle()
        assertFalse("not shown again for the same version", present("whats-new"))
    }

    /** A theme copied as text can be pasted back into a preview, and nothing changes until Apply. */
    @Test fun anIslandThemeCanBeCopiedAsTextAndPastedBack() {
        ready()
        val controller = IslandThemeController.current!!
        compose.runOnIdle {
            IslandThemes.apply(IslandThemes.presets.first { it.name == "Aurora" })
            controller.copyTheme()
            IslandThemes.reset()
            controller.pasteTheme()
        }
        compose.waitForIdle()
        assertEquals("Aurora", controller.preview?.name)
        assertEquals("Classic", IslandThemes.active.name)
        compose.runOnIdle { controller.applyPreview() }
        assertEquals("Aurora", IslandThemes.active.name)
        compose.runOnIdle { IslandThemes.reset() }
    }
}
