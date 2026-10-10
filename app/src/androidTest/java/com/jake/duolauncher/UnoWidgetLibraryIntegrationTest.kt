package com.jake.duolauncher

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UnoWidgetLibraryIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun ready() = compose.waitUntil(90_000) { !model().state.value.loading }
    private fun present(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test fun everyLibraryWidgetDrawsAndTheTypedOnesRemember() {
        ready()
        val before = model().state.value.layout
        try {
            compose.runOnIdle {
                model().state.value.order.toList().forEach { model().setPinned(it, false) }
                // Clear the default clock and date too, so the first page has room for six 2 x 2 widgets.
                model().state.value.layout.widgetPlacements.map { it.slot }.forEach { model().removePlacement(DropTarget.Widget(it)) }
            }
            compose.waitForIdle()
            val chosen = listOf(UnoWidgets.BATTERY, UnoWidgets.MONTH, UnoWidgets.MOON, UnoWidgets.COUNTER, UnoWidgets.COUNTDOWN, UnoWidgets.NOTE)
            val base = (model().state.value.layout.widgetPlacements.maxOfOrNull { it.slot } ?: -1) + 1
            chosen.forEachIndexed { i, id ->
                val slot = base + i
                val layout = model().state.value.layout
                val index = (0 until HOME_CELLS).firstOrNull { widgetCandidate(layout, slot, it, 2, 2) != null }
                assertNotNull("room for $id", index)
                compose.runOnIdle { assertTrue(model().placeWidget(widgetCandidate(layout, slot, index!!, 2, 2)!!.copy(id = id))) }
            }
            compose.waitForIdle()
            val tags = listOf("battery", "month", "moon", "counter", "countdown", "note")
            tags.forEachIndexed { i, tag -> assertTrue("$tag face", present("uno-$tag-${base + i}")) }
            // Counter: a tap on the card adds one and survives recomposition.
            val counterSlot = base + 3
            compose.onAllNodesWithTag("uno-counter-$counterSlot", useUnmergedTree = true)[0].performClick()
            compose.waitForIdle()
            assertEquals(1, WidgetData.getInt(counterSlot, "count"))
            // Typed data goes with its widget.
            compose.runOnIdle {
                WidgetData.set(base + 5, "note", "buy milk\ncall the dentist\nbook the train")
                WidgetData.set(base + 4, "name", "Trip to Lisbon"); WidgetData.set(base + 4, "date", java.time.LocalDate.now().plusDays(23).toString())
            }
            compose.waitForIdle()
            assertEquals("buy milk\ncall the dentist\nbook the train", WidgetData.get(base + 5, "note"))
            // A picture of the page, for looking at (pulled by the person running the tests).
            val shot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(compose.activity.filesDir, "uno-widgets.png").outputStream().use { shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            compose.runOnIdle { model().removePlacement(DropTarget.Widget(base + 5)) }
            compose.waitForIdle()
            compose.waitUntil(5_000) { WidgetData.get(base + 5, "note").isEmpty() }
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }

    @Test fun theOtherThreeDrawToo() {
        ready()
        val before = model().state.value.layout
        try {
            compose.runOnIdle {
                model().state.value.order.toList().forEach { model().setPinned(it, false) }
                model().state.value.layout.widgetPlacements.map { it.slot }.forEach { model().removePlacement(DropTarget.Widget(it)) }
            }
            compose.waitForIdle()
            val chosen = listOf(UnoWidgets.PROGRESS to "progress", UnoWidgets.TIMERS to "timers", UnoWidgets.SUN to "sun")
            val base = (model().state.value.layout.widgetPlacements.maxOfOrNull { it.slot } ?: -1) + 1
            chosen.forEachIndexed { i, (id, _) ->
                val slot = base + i
                val layout = model().state.value.layout
                val index = (0 until HOME_CELLS).firstOrNull { widgetCandidate(layout, slot, it, 2, 2) != null }
                assertNotNull(index)
                compose.runOnIdle { assertTrue(model().placeWidget(widgetCandidate(layout, slot, index!!, 2, 2)!!.copy(id = id))) }
            }
            compose.waitForIdle()
            chosen.forEachIndexed { i, (_, tag) -> assertTrue("$tag face", present("uno-$tag-${base + i}")) }
            assertTrue(present("timer-start-25"))
            val shot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(compose.activity.filesDir, "uno-widgets2.png").outputStream().use { shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }
}
