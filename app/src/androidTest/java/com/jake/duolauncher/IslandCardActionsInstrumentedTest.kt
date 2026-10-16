package com.jake.duolauncher

import com.jake.duolauncher.island.*

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/** The buttons on a notification card, and the island's privacy row, driven the way a person drives them. */
@RunWith(AndroidJUnit4::class)
class IslandCardActionsInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val state = IslandState()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val received = CopyOnWriteArrayList<Intent>()
    private val receiver = object : BroadcastReceiver() { override fun onReceive(c: Context?, intent: Intent?) { intent?.let { received += it } } }

    @After fun reset() { runCatching { context.unregisterReceiver(receiver) } }

    private fun show(anchored: Boolean = false) {
        ContextCompat.registerReceiver(context, receiver, IntentFilter("uno.test.card"), ContextCompat.RECEIVER_NOT_EXPORTED)
        compose.setContent {
            DuoTheme(false) {
                DynamicIsland(state, glass = null, deviceStatus = DeviceStatus(battery = 80), feedHeadline = null,
                    environmentOverride = IslandEnvironment(PxRect(515f, 23f, 565f, 73f), 1080f, 63f, screenHeight = 2424f),
                    onSearch = {}, onOpenFeed = {}, onCustomize = {})
            }
        }
        Thread.sleep(2000); compose.waitForIdle()
    }

    private fun broadcast(code: Int, extra: String): PendingIntent =
        PendingIntent.getBroadcast(context, code, Intent("uno.test.card").setPackage(context.packageName).putExtra("which", extra),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun card(vararg actions: PeekAction) =
        PeekNotification("com.example.chat", "Messages", "Ada Lovelace", "Lunch?", null, null, 6, null, actions.toList())

    private fun waitFor(what: String, check: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!check() && System.currentTimeMillis() < end) Thread.sleep(50)
        assertTrue(what, check())
    }

    @Test fun aCardShowsItsButtonsAndTappingOneSendsItAndTucksTheIslandAway() {
        show()
        compose.runOnIdle { state.showPeekCard(card(PeekAction("Archive", broadcast(1, "archive")), PeekAction("Mark as read", broadcast(2, "read")))) }
        compose.waitForIdle()
        assertEquals(2, compose.onAllNodesWithTag("island-peek-action", useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.onAllNodesWithTag("island-peek-action", useUnmergedTree = true)[0].performClick()
        waitFor("the button's intent was sent") { received.any { it.getStringExtra("which") == "archive" } }
        compose.runOnIdle { assertFalse("the island tucks away", state.expanded) }
    }

    @Test fun aReplyIsTypedInTheIslandAndSentThroughTheNotificationsOwnInput() {
        show()
        val input = RemoteInput.Builder("reply_key").setLabel("Reply to Ada").build()
        compose.runOnIdle { state.showPeekCard(card(PeekAction("Reply", broadcast(3, "reply"), input))) }
        compose.waitForIdle()
        compose.onNodeWithTag("island-peek-action", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("the card waits while a reply is typed", state.peekEditing) }
        compose.onNodeWithTag("island-reply-field", useUnmergedTree = true).performTextInput("Yes, noon works")
        compose.onNodeWithTag("island-reply-send", useUnmergedTree = true).performClick()
        waitFor("the reply was sent") { received.any { it.getStringExtra("which") == "reply" } }
        val sent = received.first { it.getStringExtra("which") == "reply" }
        assertEquals("Yes, noon works", RemoteInput.getResultsFromIntent(sent)?.getCharSequence("reply_key")?.toString())
        compose.runOnIdle { assertFalse(state.expanded); assertFalse(state.peekEditing) }
    }

    @Test fun aCardWithNoButtonsHasNoButtonRow() {
        show()
        compose.runOnIdle { state.showPeekCard(card()) }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithTag("island-peek-actions", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun thePrivacyRowAppearsWhileTheCameraIsInUseAndLeavesWithIt() {
        show()
        compose.runOnIdle { state.cameraActive = true; state.micActive = true; state.toggle() }
        compose.waitForIdle()
        assertEquals(1, compose.onAllNodesWithTag("island-privacy-row", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithTag("island-privacy-review", useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.runOnIdle { state.cameraActive = false; state.micActive = false }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithTag("island-privacy-row", useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}
