package com.jake.duolauncher

import com.jake.duolauncher.widgets.*

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Uno's own widgets stack like Android's: the second one joins the first's stack, switches with the rail, and keeps its saved data. */
class UnoWidgetStackIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun present(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test fun anUnoWidgetStacksBehindAnotherKeepsItsDataAndCanBeUnstacked() {
        compose.waitUntil(90_000) { !model().state.value.loading }
        runCatching { compose.onNodeWithText("Not now").performClick(); compose.waitForIdle() }
        val before = model().state.value.layout
        var baseSlot = -1; var memberSlot = -1
        try {
            compose.runOnIdle {
                model().state.value.order.toList().forEach { model().setPinned(it, false) }
                model().state.value.layout.widgetPlacements.map { it.slot }.forEach { model().removePlacement(DropTarget.Widget(it)) }
            }
            compose.waitForIdle()
            val layout = model().state.value.layout
            baseSlot = model().nextWidgetSlot()
            compose.runOnIdle { model().placeWidget(widgetCandidate(layout, 0, 0, 2, 2)!!.copy(slot = baseSlot, id = UnoWidgets.NOTE)) }
            memberSlot = model().nextWidgetSlot()
            compose.runOnIdle { model().placeWidget(widgetCandidate(model().state.value.layout, 0, 2, 2, 2)!!.copy(slot = memberSlot, id = UnoWidgets.COUNTER)) }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Home page 1").performClick()
            compose.runOnIdle { WidgetData.set(memberSlot, "probe", "kept") }

            compose.onNodeWithTag("widget-slot-$baseSlot").performSemanticsAction(SemanticsActions.OnLongClick)
            compose.waitUntil(10_000) { present("widget-stack-$baseSlot") }
            compose.onNodeWithTag("widget-stack-$baseSlot").performClick()
            compose.waitUntil(10_000) { present("stack-pick-$memberSlot") }
            compose.onNodeWithTag("stack-pick-$memberSlot").performClick()

            compose.waitUntil(10_000) { model().state.value.layout.placement(memberSlot) == null }
            val code = StackRules.unoCode(UnoWidgets.COUNTER, memberSlot)
            assertEquals(listOf(code), WidgetStacks.members(baseSlot))
            compose.waitUntil(10_000) { present("stack-rail") }
            compose.waitForIdle()
            assertEquals("the stacked widget's data survives leaving the grid", "kept", WidgetData.get(memberSlot, "probe"))
            assertTrue("its slot is not handed to a new widget", model().nextWidgetSlot() > memberSlot)

            compose.onNodeWithTag("stack-dot-1").performClick()
            compose.waitUntil(10_000) { present("uno-counter-$memberSlot") }
            assertFalse(present("uno-note-$baseSlot"))
            compose.onNodeWithTag("stack-dot-0").performClick()
            compose.waitUntil(10_000) { present("uno-note-$baseSlot") }

            // Removing the widget that is showing takes it out of the stack for real: its page goes and the base widget is shown.
            compose.onNodeWithTag("stack-dot-1").performClick()
            compose.waitUntil(10_000) { present("uno-counter-$memberSlot") }
            compose.onNodeWithTag("widget-slot-$baseSlot").performSemanticsAction(SemanticsActions.OnLongClick)
            compose.waitUntil(10_000) { present("widget-unstack-$code") }
            compose.onNodeWithTag("widget-unstack-$code").performClick()
            compose.waitUntil(10_000) { WidgetStacks.members(baseSlot).isEmpty() }
            compose.waitUntil(10_000) { present("uno-note-$baseSlot") && !present("uno-counter-$memberSlot") }
        } finally {
            if (baseSlot >= 0) WidgetStacks.members(baseSlot).toList().forEach { WidgetStacks.remove(baseSlot, it) }
            compose.runOnIdle { model().restoreLayout(before) }
            compose.runOnIdle { if (memberSlot >= 0) WidgetData.prune(emptySet()) }
        }
    }
}
