package com.jake.duolauncher

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Walks every settings page and saves a screenshot of each (tour-*.png in the app's files folder) so the whole launcher can be reviewed by eye. */
class LauncherTourInstrumentedTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)

    private fun shot(name: String, settle: Boolean = true) {
        if (settle) compose.waitForIdle()
        val b = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.filesDir, "tour-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun tour() {
        val model = ViewModelProvider(compose.activity)[LauncherModel::class.java]
        compose.waitUntil(60_000) { !model.state.value.loading }
        compose.onNodeWithContentDescription("Home page 1").performClick()
        compose.waitForIdle()
        runCatching { compose.onNodeWithText("Not now").performClick(); compose.waitForIdle() }
        shot("home")
        compose.openHomeCustomization()
        shot("settings")
        for (page in listOf("wallpaper", "icons", "home", "gestures", "search", "panel", "island", "keyboard", "privacy", "feed", "updates", "backup", "help")) {
            val tag = "customization-$page"
            if (compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) {
                runCatching { compose.onNodeWithTag("settings-scroll").performScrollToNode(hasTestTag(tag)) }
            }
            if (compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) continue
            compose.onNodeWithTag(tag).performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("customization-back").fetchSemanticsNodes().isNotEmpty() }
            Thread.sleep(600)
            shot("page-$page")
            compose.onNodeWithTag("customization-back").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("customization-back").fetchSemanticsNodes().isEmpty() }
            compose.waitForIdle()
        }
    }

    /** Home's own sheets: the empty-space menu, the widget picker, an app's options and Search. Each step is best-effort so one miss does not hide the rest. */
    @Test fun tourSheets() {
        val model = ViewModelProvider(compose.activity)[LauncherModel::class.java]
        compose.waitUntil(60_000) { !model.state.value.loading }
        runCatching { compose.onNodeWithText("Not now").performClick(); compose.waitForIdle() }
        compose.onNodeWithContentDescription("Home page 1").performClick()
        compose.waitForIdle()
        val layout = model.state.value.layout
        val blocked = layout.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() }
        val empty = (0 until HOME_CELLS).first { layout.slotAt(it) == null && it !in blocked }
        runCatching {
            compose.onNodeWithTag("home-cell-$empty").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick)
            Thread.sleep(500); shot("sheet-empty-space")
            compose.onNodeWithText("Add widget").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("visual-widget-picker").fetchSemanticsNodes().isNotEmpty() }
            Thread.sleep(800); shot("sheet-widget-picker")
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
            compose.waitForIdle()
        }
        runCatching {
            val app = (0 until HOME_CELLS).first { idx -> layout.slotAt(idx)?.let { id -> model.state.value.apps.any { it.id == id } } == true }
            val c = compose.onNodeWithTag("home-cell-$app").fetchSemanticsNode().boundsInRoot.center
            compose.onNodeWithTag("launcher-root").performTouchInput { down(c); advanceEventTime(700); up() }
            Thread.sleep(600); shot("sheet-app-options")
            compose.onNodeWithContentDescription("Close app options").performClick()
            compose.waitForIdle()
        }
        runCatching {
            compose.onNodeWithTag("launcher-root").performTouchInput { swipeDown(startY = height * .35f, endY = height * .75f) }
            Thread.sleep(800); shot("sheet-search")
        }
    }

    /** Search growing out of the island, frame by frame (the test clock is held and moved by hand so each picture is one moment). */
    @Test fun tourSearchGrowsFromTheIsland() {
        val model = ViewModelProvider(compose.activity)[LauncherModel::class.java]
        compose.waitUntil(60_000) { !model.state.value.loading }
        runCatching { compose.onNodeWithText("Not now").performClick(); compose.waitForIdle() }
        compose.onNodeWithContentDescription("Home page 1").performClick()
        compose.waitForIdle()
        shot("search-0-home")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("launcher-root").performTouchInput { swipeDown(startY = height * .35f, endY = height * .75f) }
        for ((i, ms) in listOf(30L, 90L, 150L, 250L, 900L).withIndex()) {
            compose.mainClock.advanceTimeBy(ms)
            shot("search-${i + 1}-${ms}ms", settle = false)
        }
        compose.mainClock.autoAdvance = true
    }

    /** Edit Home as a person sees it: wiggling icons with badges, free cells, the + and Done bar. */
    @Test fun tourEditHome() {
        val model = ViewModelProvider(compose.activity)[LauncherModel::class.java]
        compose.waitUntil(60_000) { !model.state.value.loading }
        runCatching { compose.onNodeWithText("Not now").performClick(); compose.waitForIdle() }
        compose.onNodeWithContentDescription("Home page 1").performClick()
        compose.waitForIdle()
        val layout = model.state.value.layout
        val blocked = layout.widgetPlacements.filter { it.page == 0 }.flatMap { it.coveredIndices() }.toSet()
        val free = (0 until HOME_CELLS).first { it !in blocked && layout.slotAt(it) == null && it >= 12 }
        compose.onNodeWithTag("home-cell-$free").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("empty-space-edit").fetchSemanticsNodes().isNotEmpty() }
        shot("edit-0-menu")
        compose.onNodeWithTag("empty-space-edit").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("home-edit-done", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        shot("edit-1-mode")
        compose.onNodeWithTag("home-edit-add").performClick()
        shot("edit-2-add-menu")
    }

    /** Home over the "Dune glass" background, day or night depending on the phone's mode, so the liquid glass can be judged against it. */
    @Test fun tourDuneGlass() {
        val model = ViewModelProvider(compose.activity)[LauncherModel::class.java]
        compose.waitUntil(60_000) { !model.state.value.loading }
        runCatching { compose.onNodeWithText("Not now").performClick(); compose.waitForIdle() }
        compose.onNodeWithContentDescription("Home page 1").performClick()
        compose.waitForIdle()
        val kind = com.jake.duolauncher.appearance.BuiltinBackground.valueOf(InstrumentationRegistry.getArguments().getString("kind") ?: "DUNE_GLASS")
        val night = System.getProperty("uno.night") == "1" || InstrumentationRegistry.getArguments().getString("night") == "1"
        val appearanceField = MainActivity::class.java.getDeclaredField("appearance").apply { isAccessible = true }
        val store = appearanceField.get(compose.activity) as com.jake.duolauncher.appearance.AppearanceStore
        compose.runOnIdle { store.setMode(if (night) com.jake.duolauncher.appearance.AppearanceMode.DARK else com.jake.duolauncher.appearance.AppearanceMode.LIGHT, false) }
        compose.runOnIdle {
            com.jake.duolauncher.appearance.setBuiltinBackground(compose.activity, kind)
            com.jake.duolauncher.appearance.LauncherBackgroundCache.revision.intValue++
        }
        // The scene is built on a background thread; on a slow emulator that can take a while. Wait for it.
        val t0 = System.currentTimeMillis()
        val dm = compose.activity.resources.displayMetrics
        while (System.currentTimeMillis() - t0 < 120_000 && com.jake.duolauncher.appearance.DuneSceneCache.get(kind, dm.widthPixels, dm.heightPixels, night) == null) Thread.sleep(500)
        File(compose.activity.filesDir, "dunescene-build-ms.txt").writeText((System.currentTimeMillis() - t0).toString())
        Thread.sleep(1_500)
        compose.waitForIdle()
        shot("dunescene-${kind.name.lowercase()}-${if (night) "night" else "day"}")
        compose.runOnIdle { com.jake.duolauncher.appearance.setBuiltinBackground(compose.activity, com.jake.duolauncher.appearance.BuiltinBackground.DUNES) }
    }

    /** The Wallpaper & glass page with its built-in background picker. */
    @Test fun tourWallpaperPicker() {
        val model = ViewModelProvider(compose.activity)[LauncherModel::class.java]
        compose.waitUntil(60_000) { !model.state.value.loading }
        runCatching { compose.onNodeWithText("Not now").performClick(); compose.waitForIdle() }
        compose.onNodeWithContentDescription("Home page 1").performClick()
        compose.waitForIdle()
        compose.openHomeCustomization()
        compose.onNodeWithTag("customization-wallpaper").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("builtin-dunes").fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(2_500)
        shot("wallpaper-picker")
    }
}
