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

    @Test fun editHomeShowsBadgesWithoutAGridAndDoneLeavesIt() {
        ready()
        val before = model().state.value.layout
        try {
            val free = freeCell(before)
            enterEditMode(free)
            assertFalse("free cells show only while something is dragged, as on iOS", present("home-free-$free"))
            val app = model().state.value.apps.first { it.id == before.slots.firstOrNull { s -> s != null } }
            compose.onNodeWithContentDescription("Remove ${app.label} from Home").assertExists()
            compose.onNodeWithTag("home-edit-done").performClick()
            compose.waitForIdle()
            assertFalse("Done leaves edit mode", present("home-edit-done"))
            assertFalse(present("home-free-$free"))
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }

    @Test fun addPageKeepsABlankPageAndRemoveTakesItAway() {
        ready()
        val before = model().state.value.layout
        try {
            enterEditMode(freeCell(before))
            compose.onNodeWithTag("home-edit-add").performClick()
            compose.onNodeWithTag("home-edit-add-page").performClick()
            compose.waitUntil(10_000) { model().state.value.homePages == before.pageCount + 1 }
            compose.onNodeWithTag("home-edit-add").performClick()
            compose.onNodeWithTag("home-edit-remove-page").assertExists().performClick()
            compose.waitUntil(10_000) { model().state.value.homePages == before.pageCount }
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }

    @Test fun aBlankPageCanBeMovedEarlierAndKeepsItsPlace() {
        ready()
        val before = model().state.value.layout
        try {
            enterEditMode(freeCell(before))
            compose.onNodeWithTag("home-edit-add").performClick()
            compose.onNodeWithTag("home-edit-add-page").performClick()
            compose.waitUntil(10_000) { model().state.value.homePages == before.pageCount + 1 }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("home-edit-add", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("home-edit-add").performClick()
            compose.onNodeWithTag("home-edit-page-earlier").assertExists().performClick()
            compose.waitUntil(10_000) { model().state.value.layout.pageIsEmpty(before.pageCount - 1) }
            assertEquals(before.pageCount + 1, model().state.value.homePages)
            assertFalse("the page that was last is now second to last", model().state.value.layout.pageIsEmpty(before.pageCount))
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }

    @Test fun hidingAnAppFromItsMenuTakesItOffHomeAndOutOfAllApps() {
        ready()
        val before = model().state.value.layout
        val id = before.slots.firstOrNull { it != null && model().state.value.folders.none { f -> it in f.appIds } } ?: error("Home needs an app outside a folder for this test")
        val app = model().state.value.apps.first { it.id == id }
        try {
            compose.onNodeWithContentDescription(app.label).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick)
            compose.waitUntil(10_000) { present("app-hide-$id") }
            compose.onNodeWithTag("app-hide-$id").performClick()
            compose.waitUntil(10_000) { id in model().state.value.hiddenApps }
            assertNull("no longer on Home", model().state.value.layout.indexOfShortcut(id))
            assertTrue("out of the visible list", model().state.value.visibleApps.none { it.id == id })
        } finally {
            compose.runOnIdle { model().unhideApp(id); model().restoreLayout(before) }
        }
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
