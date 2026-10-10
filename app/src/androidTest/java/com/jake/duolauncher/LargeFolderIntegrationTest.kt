package com.jake.duolauncher

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeFolderIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun ready() = compose.waitUntil(90_000) { !model().state.value.loading }

    @Test fun aLargeFolderShowsItsAppsOnHomeAndCleansUpWithItsWidget() {
        ready()
        val before = model().state.value.layout
        try {
            // Empty the grid first, so a free 2 x 2 spot is certain.
            compose.runOnIdle { model().state.value.order.toList().forEach { model().setPinned(it, false) } }
            compose.waitForIdle()
            val arranged = model().state.value.layout
            val slot = (arranged.widgetPlacements.maxOfOrNull { it.slot } ?: -1) + 1
            val apps = model().state.value.apps.take(3)
            assertEquals("Needs three apps", 3, apps.size)
            val index = (0 until HOME_CELLS).firstOrNull { widgetCandidate(arranged, slot, it, 2, 2) != null }
            assertNotNull("Needs a free 2 x 2 spot on the first page", index)
            val placement = widgetCandidate(arranged, slot, index!!, 2, 2)!!.copy(id = FOLDER_WIDGET)
            compose.runOnIdle {
                assertTrue(model().placeWidget(placement))
                apps.forEach { LargeFolders.toggle(slot, it.id) }
            }
            compose.waitForIdle()
            apps.forEach {
                assertTrue("${it.label} should be on the folder face",
                    compose.onAllNodesWithTag("large-folder-app-${it.id}", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
            }
            compose.runOnIdle { model().removePlacement(DropTarget.Widget(slot)) }
            compose.waitForIdle()
            compose.waitUntil(5_000) { LargeFolders.folders[slot] == null }
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }

    @Test fun wideAndTallLargeFoldersShowTheirApps() {
        ready()
        val before = model().state.value.layout
        try {
            compose.runOnIdle { model().state.value.order.toList().forEach { model().setPinned(it, false) } }
            compose.waitForIdle()
            val arranged = model().state.value.layout
            val apps = model().state.value.apps.take(3)
            assertEquals(3, apps.size)
            val base = (arranged.widgetPlacements.maxOfOrNull { it.slot } ?: -1) + 1
            val shapes = listOf(base to (2 to 1), base + 1 to (1 to 2))
            shapes.forEach { (slot, span) ->
                val layout = model().state.value.layout
                val index = (0 until HOME_CELLS).firstOrNull { widgetCandidate(layout, slot, it, span.first, span.second) != null }
                assertNotNull("Needs room for ${span.first} x ${span.second}", index)
                val placement = widgetCandidate(layout, slot, index!!, span.first, span.second)!!.copy(id = FOLDER_WIDGET)
                compose.runOnIdle {
                    assertTrue(model().placeWidget(placement))
                    apps.forEach { LargeFolders.toggle(slot, it.id) }
                }
                compose.waitForIdle()
                assertTrue("${span.first} x ${span.second} should show an app",
                    apps.any { compose.onAllNodesWithTag("large-folder-app-${it.id}", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() })
            }
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }
}
