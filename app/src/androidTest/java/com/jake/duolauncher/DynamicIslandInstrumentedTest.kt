package com.jake.duolauncher

import com.jake.duolauncher.island.*

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performImeAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import androidx.compose.ui.semantics.getOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The island composed on its own, with its state and feeds driven directly. */
@RunWith(AndroidJUnit4::class)
class DynamicIslandInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val state = IslandState()

    @After fun reset() {
        NotificationFeed.liveUpdates = emptyList()
        IslandTools.toolsOpen = false
    }

    private fun show() {
        compose.setContent {
            DuoTheme(false) {
                DynamicIsland(state, glass = null, deviceStatus = DeviceStatus(), feedHeadline = null,
                    environmentOverride = IslandEnvironment(PxRect(509f, 40f, 571f, 102f), 1080f, 80f, screenHeight = 2400f),
                    onSearch = {}, onOpenFeed = {}, onCustomize = {})
            }
        }
        compose.waitForIdle()
        // IslandSystemEvents resets the camera/mic marks asynchronously when it registers; let that settle before a test sets state.
        Thread.sleep(2_500)
    }

    /** The island is laid out around a camera hole that this test window does not have on screen, so presence in the tree is
     * what is checked, in the unmerged tree because the clickable pill merges its children's tags (its content is composed only when the state calls for it). */
    private fun assertPresent(tag: String) = assertTrue("$tag is missing",
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())

    private fun liveUpdate(key: String = "k", progress: Int = 40, shortText: String? = "12 min") = LiveUpdate(
        key = key, packageName = "com.example.ride", appLabel = "Ride", title = "Ride to the airport", text = "Arriving soon",
        shortText = shortText, progress = progress, progressMax = 100, indeterminate = false, chronometerBase = null,
        countDown = false, icon = null, color = 0xFF1A73E8.toInt(), postTime = System.currentTimeMillis(), openIntent = null)

    @Test fun startsCollapsedAndShown() {
        show()
        assertPresent("dynamic-island")
        assertFalse(state.expanded)
    }

    @Test fun tapOpensAndTapAgainCloses() {
        show()
        compose.onAllNodesWithTag("dynamic-island", useUnmergedTree = true)[0].performClick()
        compose.waitForIdle()
        assertTrue(state.expanded)
        compose.onAllNodesWithTag("dynamic-island", useUnmergedTree = true)[0].performClick()
        compose.waitForIdle()
        assertFalse(state.expanded)
    }

    @Test fun collapseWorksFromOutside() {
        show()
        compose.runOnIdle { state.toggle() }
        compose.waitForIdle()
        assertTrue(state.expanded)
        compose.runOnIdle { state.collapse() }
        compose.waitForIdle()
        assertFalse(state.expanded)
    }

    @Test fun cameraAndMicrophoneMarksFollowTheirState() {
        show()
        assertEquals(0, compose.onAllNodesWithTag("island-camera", useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.runOnIdle { state.cameraActive = true; state.micActive = true }
        compose.waitForIdle()
        assertPresent("island-camera")
        assertPresent("island-mic")
        compose.runOnIdle { state.cameraActive = false; state.micActive = false }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithTag("island-camera", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun screenRecordingMarkFollowsItsState() {
        show()
        assertEquals(0, compose.onAllNodesWithTag("island-recording", useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.runOnIdle { state.recordingActive = true }
        compose.waitForIdle()
        assertPresent("island-recording")
        compose.runOnIdle { state.recordingActive = false }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithTag("island-recording", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun liveUpdateShowsItsShortStatusWhenCollapsed() {
        show()
        compose.runOnIdle { NotificationFeed.liveUpdates = listOf(liveUpdate()) }
        compose.waitForIdle()
        assertPresent("island-live-update")
    }

    @Test fun liveUpdateOpensIntoACardWithProgress() {
        show()
        compose.runOnIdle { NotificationFeed.liveUpdates = listOf(liveUpdate()) }
        compose.waitForIdle()
        compose.runOnIdle { state.toggle() }
        compose.waitForIdle()
        assertPresent("island-update")
        assertPresent("island-update-progress")
    }

    private fun navigation() = liveUpdate(key = "nav", progress = 0, shortText = null).copy(
        packageName = "app.organicmaps", appLabel = "Organic Maps", title = "300 m", text = "Main Street", source = ActivitySource.NAVIGATION,
        largeIcon = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888).also { it.eraseColor(0xFFFFFFFF.toInt()) })

    @Test fun aNavigationInstructionShowsItsTurnArrowOnThePillAndLargeInTheOpenIsland() {
        show()
        compose.runOnIdle { NotificationFeed.liveUpdates = listOf(navigation()) }
        compose.waitForIdle()
        assertPresent("island-live-update")
        assertPresent("island-update-arrow")
        compose.runOnIdle { state.toggle() }
        compose.waitForIdle()
        assertPresent("island-update")
        assertPresent("island-update-arrow")
    }

    @Test fun anInstructionWithoutAnArrowFallsBackToTheAppsOwnIconAndAnOrdinaryUpdateNeverShowsAnArrow() {
        show()
        compose.runOnIdle { NotificationFeed.liveUpdates = listOf(navigation().copy(largeIcon = null)) }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithTag("island-update-arrow", useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.runOnIdle { NotificationFeed.liveUpdates = listOf(liveUpdate().copy(largeIcon = navigation().largeIcon)) }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithTag("island-update-arrow", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun endingTheLiveUpdateRemovesItFromTheIsland() {
        show()
        compose.runOnIdle { NotificationFeed.liveUpdates = listOf(liveUpdate()) }
        compose.waitForIdle()
        compose.runOnIdle { NotificationFeed.liveUpdates = emptyList() }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithTag("island-live-update", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun longPressToolsStateOpensTheToolsPanel() {
        show()
        compose.runOnIdle { IslandTools.toolsOpen = true; state.expanded = true }
        compose.waitForIdle()
        assertPresent("island-tools-timer")
    }

    @Test fun typingInTheIslandSearchFieldAndPressingEnterSendsTheQuery() {
        val sent = mutableListOf<String>()
        compose.setContent {
            DuoTheme(false) {
                DynamicIsland(state, glass = null, deviceStatus = DeviceStatus(), feedHeadline = null,
                    environmentOverride = IslandEnvironment(PxRect(509f, 40f, 571f, 102f), 1080f, 80f, screenHeight = 2400f),
                    onSearch = {}, onWebSearch = { sent += it }, onOpenFeed = {}, onCustomize = {})
            }
        }
        compose.runOnIdle { state.toggle() }
        compose.waitForIdle()
        compose.onAllNodesWithTag("island-search-field", useUnmergedTree = true)[0].performTextInput("graphene os release")
        compose.onAllNodesWithTag("island-search-field", useUnmergedTree = true)[0].performImeAction()
        compose.waitForIdle()
        assertEquals(listOf("graphene os release"), sent)
        assertEquals("the field empties after sending", "", compose.onAllNodesWithTag("island-search-field", useUnmergedTree = true)[0]
            .fetchSemanticsNode().config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.EditableText)?.text ?: "")
    }

    @Test fun theOpenIslandShowsTheControlPanelShortcutsAsAQuickLaunchStrip() {
        val prefs = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("extras", android.content.Context.MODE_PRIVATE)
        val before = prefs.getString("shortcuts", null)
        try {
            prefs.edit().putString("shortcuts", "com.jake.duolauncher/com.jake.duolauncher.MainActivity").commit()
            show()
            compose.runOnIdle { state.toggle() }
            compose.waitUntil(20_000) { compose.onAllNodesWithTag("island-quick-app", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1, compose.onAllNodesWithTag("island-quick-app", useUnmergedTree = true).fetchSemanticsNodes().size)
        } finally { prefs.edit().apply { if (before == null) remove("shortcuts") else putString("shortcuts", before) }.commit() }
    }

    @Test fun anOngoingCallWithAHangUpActionShowsAnEndButton() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val noop = android.app.PendingIntent.getBroadcast(context, 0, android.content.Intent("com.jake.duolauncher.TEST_HANGUP").setPackage(context.packageName),
            android.app.PendingIntent.FLAG_IMMUTABLE)
        show()
        compose.runOnIdle { NotificationFeed.ongoingCall = OngoingCall("Ada", "com.example.phone", System.currentTimeMillis(), noop, hangUp = noop) }
        compose.runOnIdle { state.toggle() }
        compose.waitForIdle()
        try { assertPresent("island-call-end") } finally { compose.runOnIdle { NotificationFeed.ongoingCall = null } }
    }

    private fun card(title: String?, text: String?, seconds: Int = 6) = PeekNotification("com.example.chat", "Chat", title, text, null, null, seconds)

    @Test fun aNotificationCardShowsTheTitleAndTextItWasGiven() {
        show()
        compose.runOnIdle { state.showPeekCard(card("Ada", "see you at six")) }
        compose.waitForIdle()
        assertPresent("island-peek-card"); assertPresent("island-peek-title"); assertPresent("island-peek-text")
    }

    @Test fun aTitleOnlyCardHasNoTextLine() {
        show()
        compose.runOnIdle { state.showPeekCard(card("Ada", null)) }
        compose.waitForIdle()
        assertPresent("island-peek-title")
        assertEquals(0, compose.onAllNodesWithTag("island-peek-text", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun theCardTucksAwayAfterTheTimeChosenForItsApp() {
        compose.mainClock.autoAdvance = false
        show()
        compose.runOnIdle { state.showPeekCard(card("Ada", "hi", seconds = 3)) }
        compose.mainClock.advanceTimeBy(1_000)
        assertPresent("island-peek-card")
        compose.mainClock.advanceTimeBy(4_000)
        compose.waitForIdle()
        assertFalse(state.expanded)
        assertEquals(null, state.peekCard)
    }

    @Test fun aCardDoesNotInterruptAnIslandThePersonOpened() {
        show()
        compose.runOnIdle { state.toggle() }
        compose.waitForIdle()
        compose.runOnIdle { state.showPeekCard(card("Ada", "hi")) }
        compose.waitForIdle()
        assertEquals(null, state.peekCard)
        assertTrue(state.expanded)
    }
}
