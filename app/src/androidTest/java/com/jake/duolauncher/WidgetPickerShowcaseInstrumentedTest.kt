package com.jake.duolauncher

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

/** A developer aid: opens the widget picker over Home and photographs it, scrolling a screen at a time (not part of the suites). */
class WidgetPickerShowcaseInstrumentedTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)

    private fun shot(name: String) {
        compose.waitForIdle(); Thread.sleep(1_200)
        val b = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null)!!, "picker-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun picker() {
        val model = ViewModelProvider(compose.activity)[LauncherModel::class.java]
        compose.waitUntil(90_000) { !model.state.value.loading }
        runCatching { compose.onNodeWithText("Not now").performClick(); compose.waitForIdle() }
        compose.onNodeWithContentDescription("Home page 1").performClick()
        val layout = model.state.value.layout
        val blocked = layout.widgetPlacements.filter { it.page == 0 }.flatMap { it.coveredIndices() }.toSet()
        val free = (0 until HOME_CELLS).first { it !in blocked && layout.slotAt(it) == null && it >= 12 }
        compose.onNodeWithTag("home-cell-$free").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("empty-space-widgets").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("empty-space-widgets").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("widget-catalog-list").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle(); Thread.sleep(2_500)
        for (page in 0 until 22) {
            shot("$page")
            runCatching { compose.onNodeWithTag("widget-catalog-list").performTouchInput { swipeUp(startY = bottom * .85f, endY = bottom * .85f - height * .28f, durationMillis = 500) } }
        }
    }
}
