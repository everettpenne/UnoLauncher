package com.jake.duolauncher

import com.jake.duolauncher.widgets.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Stacks by gesture: a vertical swipe on a stack switches its widget, and a widget held over another's middle stacks onto it. */
class WidgetStackGesturesIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun present(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun root() = compose.onNodeWithTag("launcher-root")

    /** Two Uno widgets side by side on page 1: a Note (base) and a Counter. */
    private fun place(): Pair<Int, Int> {
        compose.runOnIdle {
            model().state.value.order.toList().forEach { model().setPinned(it, false) }
            model().state.value.layout.widgetPlacements.map { it.slot }.forEach { model().removePlacement(DropTarget.Widget(it)) }
        }
        compose.waitForIdle()
        val note = model().nextWidgetSlot()
        compose.runOnIdle { model().placeWidget(widgetCandidate(model().state.value.layout, 0, 0, 2, 2)!!.copy(slot = note, id = UnoWidgets.NOTE)) }
        val counter = model().nextWidgetSlot()
        compose.runOnIdle { model().placeWidget(widgetCandidate(model().state.value.layout, 0, 2, 2, 2)!!.copy(slot = counter, id = UnoWidgets.COUNTER)) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Home page 1").performClick()
        compose.waitForIdle()
        return note to counter
    }

    private fun ready() {
        compose.waitUntil(90_000) { !model().state.value.loading }
        runCatching { compose.onNodeWithText("Not now").performClick(); compose.waitForIdle() }
    }

    private fun cleanUp(before: HomeLayout, base: Int) {
        WidgetStacks.members(base).toList().forEach { WidgetStacks.remove(base, it) }
        compose.runOnIdle { model().restoreLayout(before) }
    }

    @Test fun swipingUpAndDownOnAStackSwitchesItsWidget() {
        ready()
        val before = model().state.value.layout
        var base = -1
        try {
            val (note, counter) = place()
            base = note
            compose.runOnIdle {
                WidgetStacks.add(note, StackRules.unoCode(UnoWidgets.COUNTER, counter))
                model().removePlacement(DropTarget.Widget(counter))
            }
            compose.waitUntil(10_000) { present("stack-rail") }
            assertTrue(present("uno-note-$note"))
            compose.onNodeWithTag("widget-slot-$note").performTouchInput { swipeUp() }
            compose.waitUntil(10_000) { present("uno-counter-$counter") }
            compose.onNodeWithTag("widget-slot-$note").performTouchInput { swipeDown() }
            compose.waitUntil(10_000) { present("uno-note-$note") && !present("uno-counter-$counter") }
        } finally { cleanUp(before, base) }
    }

    @Test fun aWidgetHeldOverAnotherStacksOntoIt() {
        ready()
        val before = model().state.value.layout
        var base = -1
        try {
            val (note, counter) = place()
            base = note
            val start = compose.onNodeWithTag("widget-slot-$counter").fetchSemanticsNode().boundsInRoot.center
            val target = compose.onNodeWithTag("widget-slot-$note").fetchSemanticsNode().boundsInRoot.center
            // Held over the middle for a moment: the Counter folds into the Note's stack.
            root().performTouchInput { down(start); advanceEventTime(700); moveTo(start + Offset(3f, 0f)) }
            root().performTouchInput { moveTo(target, 300) }
            // The hold is measured on the test clock, which only moves when it is told to.
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
            root().performTouchInput { up() }
            compose.waitUntil(10_000) { model().state.value.layout.placement(counter) == null }
            assertEquals(listOf(StackRules.unoCode(UnoWidgets.COUNTER, counter)), WidgetStacks.members(note))
            assertNotNull(model().state.value.layout.placement(note))
        } finally { cleanUp(before, base) }
    }

    @Test fun aQuickPassOverAnotherWidgetDoesNotStack() {
        ready()
        val before = model().state.value.layout
        var base = -1
        try {
            val (note, counter) = place()
            base = note
            val start = compose.onNodeWithTag("widget-slot-$counter").fetchSemanticsNode().boundsInRoot.center
            val target = compose.onNodeWithTag("widget-slot-$note").fetchSemanticsNode().boundsInRoot.center
            root().performTouchInput { down(start); advanceEventTime(700); moveTo(start + Offset(3f, 0f)) }
            root().performTouchInput { moveTo(target, 300); up() }
            compose.waitForIdle()
            assertTrue("no stack was made", WidgetStacks.members(note).isEmpty())
        } finally { cleanUp(before, base) }
    }

    @Test fun aScreenReaderCanSwitchAStackWithoutTheSwipeOrTheDots() {
        ready()
        val before = model().state.value.layout
        var base = -1
        try {
            val (note, counter) = place()
            base = note
            compose.runOnIdle {
                WidgetStacks.add(note, StackRules.unoCode(UnoWidgets.COUNTER, counter))
                model().removePlacement(DropTarget.Widget(counter))
            }
            compose.waitUntil(10_000) { present("stack-rail") }
            fun node() = compose.onNodeWithTag("widget-slot-$note").fetchSemanticsNode()
            assertEquals("Widget 1 of 2: Note", node().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription])
            var actions = node().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
            assertEquals(listOf("Next widget in stack"), actions.map { it.label })
            compose.runOnUiThread { actions.first().action() }
            compose.waitUntil(10_000) { present("uno-counter-$counter") }
            assertEquals("Widget 2 of 2: Counter", node().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription])
            actions = node().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
            assertEquals(listOf("Previous widget in stack"), actions.map { it.label })
            compose.runOnUiThread { actions.first().action() }
            compose.waitUntil(10_000) { present("uno-note-$note") }
        } finally { cleanUp(before, base) }
    }
}
