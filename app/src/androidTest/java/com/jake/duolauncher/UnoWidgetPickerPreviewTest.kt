package com.jake.duolauncher

import com.jake.duolauncher.widgets.*

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** The widget picker shows each Uno widget as a live preview (its real face with sample data), and tapping the preview places the widget. */
class UnoWidgetPickerPreviewTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun present(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun openPicker() {
        compose.waitUntil(90_000) { !model().state.value.loading }
        runCatching { compose.onNodeWithText("Not now").performClick(); compose.waitForIdle() }
        compose.onNodeWithContentDescription("Home page 1").performClick()
        val layout = model().state.value.layout
        val blocked = layout.widgetPlacements.filter { it.page == 0 }.flatMap { it.coveredIndices() }.toSet()
        val free = (0 until HOME_CELLS).first { it !in blocked && layout.slotAt(it) == null && it >= 12 }
        compose.onNodeWithTag("home-cell-$free").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick)
        compose.waitUntil(10_000) { present("empty-space-widgets") }
        compose.onNodeWithTag("empty-space-widgets").performClick()
        compose.waitUntil(15_000) { present("widget-catalog-list") }
    }

    @Test fun everyUnoWidgetIsShownAsItsRealFaceAndTappingPlacesIt() {
        val before = model().state.value.layout
        try {
            openPicker()
            val faces = mapOf(UnoWidgets.BATTERY to "uno-battery-", UnoWidgets.NOTE to "uno-note-", UnoWidgets.COUNTER to "uno-counter-",
                UnoWidgets.MEDIA_PLAYER to "uno-media-player-", UnoWidgets.LIQUID_BATTERY to "uno-liquid-battery-", UnoWidgets.WORLD_CLOCK to "uno-world-clock-")
            faces.forEach { (id, prefix) ->
                compose.onNodeWithTag("widget-catalog-list", useUnmergedTree = true).performScrollToNode(hasTestTag("widget-builtin-$id"))
                compose.waitForIdle()
                assertTrue("$id has its face as a preview", present(prefix + PreviewSamples.slotFor(id)))
            }
            // The preview's sample data is shown, and never saved as the person's.
            compose.onNodeWithTag("widget-catalog-list", useUnmergedTree = true).performScrollToNode(hasTestTag("widget-builtin-${UnoWidgets.COUNTER}"))
            assertEquals("3", WidgetData.get(PreviewSamples.slotFor(UnoWidgets.COUNTER), "count"))
            WidgetData.set(PreviewSamples.slotFor(UnoWidgets.COUNTER), "count", "99")
            assertEquals("writes to a preview slot are ignored", "3", WidgetData.get(PreviewSamples.slotFor(UnoWidgets.COUNTER), "count"))
            // A tap on the preview places the widget (and does not press anything inside the preview).
            val count = model().state.value.widgetPlacements.size
            compose.onNodeWithTag("widget-catalog-list", useUnmergedTree = true).performScrollToNode(hasTestTag("widget-builtin-${UnoWidgets.NOTE}"))
            compose.onNodeWithTag("widget-builtin-${UnoWidgets.NOTE}", useUnmergedTree = true).performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("widget-placement-toolbar").fetchSemanticsNodes().isNotEmpty() || model().state.value.widgetPlacements.size > count }
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }
}
