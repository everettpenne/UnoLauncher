package com.jake.duolauncher

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CustomizationNavigationIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)

    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun ready() {
        compose.waitUntil(15_000) { !model().state.value.loading }
        compose.onNodeWithContentDescription("Home page 1").performClick()
        compose.waitForIdle()
    }


    @Test fun systemBackReturnsFromSubpageToOverviewThenHome() {
        ready()
        compose.onNodeWithTag("settings").assertDoesNotExist()
        compose.openHomeCustomization()
        compose.onNodeWithTag("customization-home").performClick()
        compose.onNodeWithTag("customization-back").assertExists()

        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("customization-wallpaper").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithTag("customization-wallpaper").assertIsDisplayed()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("search").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithTag("customization-wallpaper").assertDoesNotExist()
    }

    @Test fun theSettingsPageIsKeptWhenTheSheetIsClosedAndReopened() {
        ready()
        compose.openHomeCustomization()
        compose.onNodeWithTag("customization-home").performClick()
        compose.onNodeWithTag("customization-back").assertExists()
        // Close the sheet outright (not Back one level): the page being looked at is remembered.
        compose.onNodeWithContentDescription("Close settings").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("customization-back").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
        compose.openHomeCustomization()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("customization-back").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    }

    @Test fun emptySpaceWallpaperOpensPhotoControlsWithoutChangingLayoutOrBindings() {
        ready()
        val before = model().state.value.layout
        val idsBefore = model().state.value.widgetPlacements.map { it.slot to it.id }
        val blocked = before.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() }
        val empty = (0 until HOME_CELLS).first { before.slotAt(it) == null && it !in blocked }

        compose.onNodeWithTag("home-cell-$empty").performSemanticsAction(SemanticsActions.OnLongClick)
        compose.onNodeWithTag("empty-space-wallpaper").performClick()
        // The page now opens with the built-in scene picker above the photo button, so the button can be below the fold.
        compose.onNodeWithTag("background-choose").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("wallpaper-preview").assertExists()
        assertEquals(before, model().state.value.layout)
        assertEquals(idsBefore, model().state.value.widgetPlacements.map { it.slot to it.id })
    }

    @Test fun appMoveSubpageBackDoesNotChangePlacement() {
        ready()
        val before = model().state.value.layout
        val appId = (0 until HOME_CELLS).mapNotNull(before::slotAt)
            .first { id -> model().state.value.apps.any { it.id == id } }
        val index = requireNotNull(before.indexOfShortcut(appId))

        val start = compose.onNodeWithTag("home-cell-$index").fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithTag("launcher-root").performTouchInput { down(start); advanceEventTime(700); up() }
        compose.onNodeWithText("Move on Home").performClick()
        compose.onNodeWithText("Move to first position").assertIsDisplayed()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("Move on Home").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close app options").performClick()
        assertEquals(before, model().state.value.layout)
    }

    @Test fun helpIsReachableFromPackedPageEntryAndBackReturnsToCustomization() {
        ready()
        val before = model().state.value.layout
        compose.openHomeCustomization()
        compose.onNodeWithTag("customization-help").performScrollTo().performClick()
        compose.onNodeWithText("Settings on any page").assertIsDisplayed()
        compose.onNodeWithTag("help-home-settings").assertExists()
        compose.onNodeWithTag("help-add-widget").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("help-shade-setup").performScrollTo().assertIsDisplayed()

        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("customization-help")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        assertEquals(before, model().state.value.layout)
    }
}
