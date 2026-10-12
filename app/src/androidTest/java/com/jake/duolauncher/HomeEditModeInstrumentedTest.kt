package com.jake.duolauncher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Edit Home: entering it, wiggling icons with remove badges, free cells showing, dragging with no long press, and leaving. */
@RunWith(AndroidJUnit4::class)
class HomeEditModeInstrumentedTest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun present(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun ready() {
        compose.waitUntil(60_000) { !model().state.value.loading }
        runCatching { compose.onNodeWithText("Not now").performClick(); compose.waitForIdle() }
        compose.onNodeWithContentDescription("Home page 1").performClick()
        compose.waitForIdle()
    }

    private fun freeCell(layout: HomeLayout): Int {
        val blocked = layout.widgetPlacements.filter { it.page == 0 }.flatMap { it.coveredIndices() }.toSet()
        return (0 until HOME_CELLS).first { it !in blocked && layout.slotAt(it) == null && it >= 12 }
    }

    private fun enterEditMode(freeIndex: Int) {
        compose.onNodeWithTag("home-cell-$freeIndex").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick)
        compose.waitUntil(10_000) { present("empty-space-edit") }
        compose.onNodeWithTag("empty-space-edit").performClick()
        compose.waitUntil(10_000) { present("home-edit-done") }
    }

    @Test fun editHomeShowsBadgesAndFreeCellsAndDoneLeavesIt() {
        ready()
        val before = model().state.value.layout
        try {
            val free = freeCell(before)
            enterEditMode(free)
            assertTrue("free cells are shown", present("home-free-$free"))
            val app = model().state.value.apps.first { it.id == before.slots.firstOrNull { s -> s != null } }
            compose.onNodeWithContentDescription("Remove ${app.label} from Home").assertExists()
            compose.onNodeWithTag("home-edit-done").performClick()
            compose.waitForIdle()
            assertFalse("Done leaves edit mode", present("home-edit-done"))
            assertFalse(present("home-free-$free"))
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }

    @Test fun theBadgeTakesTheAppOffHome() {
        ready()
        val before = model().state.value.layout
        try {
            enterEditMode(freeCell(before))
            val id = before.slots.first { it != null }!!
            val app = model().state.value.apps.first { it.id == id }
            compose.onNodeWithContentDescription("Remove ${app.label} from Home").performClick()
            compose.waitForIdle()
            assertNull("the app is no longer on Home", model().state.value.layout.indexOfShortcut(id))
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }

    @Test fun anIconDragsAtOnceWithNoLongPress() {
        ready()
        val before = model().state.value.layout
        try {
            val free = freeCell(before)
            enterEditMode(free)
            val id = before.slots.first { it != null }!!
            val from = before.indexOfShortcut(id)!!
            val start = compose.onNodeWithTag("home-cell-$from").fetchSemanticsNode().boundsInRoot.center
            val end = compose.onNodeWithTag("home-cell-$free").fetchSemanticsNode().boundsInRoot.center
            compose.onNodeWithTag("launcher-root").performTouchInput {
                down(start); moveTo(start + Offset(24f, 0f)); moveTo(end, 300); up()   // no waiting: a long press is not needed here
            }
            compose.waitForIdle()
            assertEquals("it landed in the free cell", free, model().state.value.layout.indexOfShortcut(id))
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }

    @Test fun aTapOnAnIconDoesNotOpenItWhileEditingAndBackLeavesEditMode() {
        ready()
        val before = model().state.value.layout
        try {
            enterEditMode(freeCell(before))
            val id = before.slots.first { it != null }!!
            compose.onNodeWithTag("home-app-$id").performClick()
            compose.waitForIdle()
            assertTrue("still editing after a tap", present("home-edit-done"))
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
            compose.waitForIdle()
            assertFalse("Back leaves edit mode", present("home-edit-done"))
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }
}
