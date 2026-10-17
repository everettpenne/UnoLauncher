package com.jake.duolauncher

import com.jake.duolauncher.island.*

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.click
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pulling the small island down to peek, holding the open island's bottom, a notification held for half a minute, and large navigation. */
@RunWith(AndroidJUnit4::class)
class IslandPeekInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val state = IslandState()

    @After fun reset() { NotificationFeed.liveUpdates = emptyList(); IslandTools.toolsOpen = false }

    private fun show() {
        compose.setContent {
            DuoTheme(false) {
                DynamicIsland(state, glass = null, deviceStatus = DeviceStatus(battery = 80), feedHeadline = null,
                    environmentOverride = IslandEnvironment(PxRect(515f, 23f, 565f, 73f), 1080f, 63f, screenHeight = 2424f),
                    onSearch = {}, onOpenFeed = {}, onCustomize = {})
            }
        }
        Thread.sleep(2000); compose.waitForIdle()
    }

    private fun islandHeight() = compose.onNodeWithTag("dynamic-island", useUnmergedTree = true).getBoundsInRoot().height

    private fun pull(dpDown: Float) {
        compose.onNodeWithTag("dynamic-island", useUnmergedTree = true).performTouchInput {
            down(center); moveTo(center + Offset(0f, dpDown * density)); up()
        }
    }

    @Test fun pullingTheSmallIslandDownPeeksAndHoldsThenTucksAway() {
        show()
        val collapsed = islandHeight()
        compose.mainClock.autoAdvance = false
        pull(40f)
        compose.mainClock.advanceTimeBy(1_200)
        compose.runOnIdle { assertTrue("held as a peek", state.peeked); assertFalse("not all the way open", state.expanded); assertEquals(null, state.pullFraction) }
        val peek = islandHeight()
        assertTrue("the peek is taller than the pill ($peek vs $collapsed)", peek > collapsed + 20.dp)
        // Tapping the peek opens it fully.
        compose.runOnIdle { state.toggle() }
        compose.mainClock.advanceTimeBy(1_200)
        compose.runOnIdle { assertTrue(state.expanded); assertFalse(state.peeked) }
        assertTrue("the open island is taller than the peek", islandHeight() > peek)
        compose.runOnIdle { state.collapse() }
        compose.mainClock.advanceTimeBy(1_500)
        // A peek that nobody touches goes by itself.
        pull(40f)
        compose.mainClock.advanceTimeBy(1_200)
        compose.runOnIdle { assertTrue(state.peeked) }
        compose.mainClock.advanceTimeBy(PeekPull.HOLD_MS + 500)
        compose.runOnIdle { assertFalse("the peek tucks itself away", state.peeked) }
        compose.mainClock.autoAdvance = true
    }

    @Test fun aLongPullOpensTheWholeIslandAndAShortOneDoesNothing() {
        show()
        compose.mainClock.autoAdvance = false
        pull(170f)
        compose.mainClock.advanceTimeBy(1_200)
        compose.runOnIdle { assertTrue("a long pull opens it", state.expanded) }
        compose.runOnIdle { state.collapse() }
        compose.mainClock.advanceTimeBy(1_500)
        pull(2f)
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { assertFalse("a touch that hardly moves is a tap, not a pull", state.peeked) }
        compose.mainClock.autoAdvance = true
    }

    @Test fun aFingerHeldOnTheBottomOfTheOpenIslandKeepsItOpen() {
        show()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.toggle() }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithTag("dynamic-island", useUnmergedTree = true).performTouchInput { down(Offset(width / 2f, height - 12f * density)) }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { assertTrue("the hold has taken", state.holdingOpen); assertFalse("and it did not open the tools", IslandTools.toolsOpen) }
        // Far longer than the island stays open by itself.
        compose.mainClock.advanceTimeBy(40_000)
        compose.runOnIdle { assertTrue("still open while held", state.expanded) }
        compose.onNodeWithTag("dynamic-island", useUnmergedTree = true).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertFalse(state.holdingOpen); assertTrue("it does not close the moment the finger lifts", state.expanded) }
        compose.mainClock.advanceTimeBy(8_000)
        compose.runOnIdle { assertFalse("and tucks away afterwards", state.expanded) }
        compose.mainClock.autoAdvance = true
    }

    @Test fun touchingTheTopOfTheOpenIslandDoesNotHoldIt() {
        show()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.toggle() }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithTag("dynamic-island", useUnmergedTree = true).performTouchInput { down(Offset(width / 2f, 40f * density)) }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { assertFalse(state.holdingOpen) }
        compose.onNodeWithTag("dynamic-island", useUnmergedTree = true).performTouchInput { up() }
        compose.mainClock.autoAdvance = true
    }

    private fun card() = PeekNotification("com.example.chat", "Messages", "Ada Lovelace", "Are we still on for lunch?", null, null, 6)

    @Test fun aNotificationIsHeldOnTheIslandForThirtySecondsAfterItsCardHasGone() {
        show()
        compose.runOnIdle { state.showPeekCard(card()) }
        compose.waitForIdle()
        compose.runOnIdle { state.collapse() }
        compose.waitForIdle()
        assertEquals("the pill shows a dot for it", 1, compose.onAllNodesWithTag("island-recent-dot", useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.runOnIdle { state.toggle() }
        compose.waitForIdle()
        assertEquals("opening the island shows it again", 1, compose.onAllNodesWithTag("island-recent", useUnmergedTree = true).fetchSemanticsNodes().size)
        // Thirty seconds on it is gone.
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.setRecentAtForTest(System.currentTimeMillis() - 31_000L) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.mainClock.autoAdvance = true
        val held = compose.onAllNodesWithTag("island-recent", useUnmergedTree = true).fetchSemanticsNodes().size
        assertEquals("a stale notification is not held; state: recentAt=${state.recentAt} now=${System.currentTimeMillis()} expanded=${state.expanded}", 0, held)
    }

    @Test fun openingAHeldNotificationClearsIt() {
        show()
        compose.runOnIdle { state.showPeekCard(card()); state.collapse() }
        compose.runOnIdle { state.toggle() }
        compose.waitForIdle()
        compose.onNodeWithTag("island-recent-open", useUnmergedTree = true).performTouchInput { click() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(null, state.recentCard); assertFalse(state.expanded) }
    }

    @Test fun aNavigationInstructionIsDrawnLargeOnTheOpenIsland() {
        show()
        val arrow = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888).also { Canvas(it).drawColor(0xFFFFFFFF.toInt()) }
        compose.runOnIdle {
            NotificationFeed.liveUpdates = listOf(LiveUpdate("nav", "app.organicmaps", "Organic Maps", "300 m", "Turn right onto Rue de Rivoli", null, 0, 0, false, null, false,
                null, 0, System.currentTimeMillis(), null, arrow, ActivitySource.NAVIGATION))
            state.toggle()
        }
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag("island-update-arrow", useUnmergedTree = true).getBoundsInRoot()
        assertTrue("the arrow is ${bounds.width} wide", bounds.width >= 60.dp)
    }
}
