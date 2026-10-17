package com.jake.duolauncher

import com.jake.duolauncher.island.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.down
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pulling the small island down must feel like holding it: the body grows with the finger and never twitches, nothing re-composes per touch event,
 * and letting go carries on from where the finger was instead of jumping.
 */
@RunWith(AndroidJUnit4::class)
class IslandPullSmoothnessInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val state = IslandState()

    private fun show() {
        compose.setContent {
            DuoTheme(false) {
                DynamicIsland(state, glass = null, deviceStatus = DeviceStatus(battery = 80, charging = true), feedHeadline = "A headline for the open panel",
                    environmentOverride = IslandEnvironment(PxRect(515f, 23f, 565f, 73f), 1080f, 63f, screenHeight = 2424f),
                    onSearch = {}, onOpenFeed = {}, onCustomize = {})
            }
        }
        Thread.sleep(2000); compose.waitForIdle()
    }

    private fun heightDp(): Float = compose.onNodeWithTag("dynamic-island", useUnmergedTree = true).getBoundsInRoot().height.value

    @Test fun aPullGrowsTheIslandSmoothlyAndLettingGoCarriesOn() {
        show()
        compose.mainClock.autoAdvance = false
        val during = ArrayList<Float>()
        val compositionsBefore = IslandDiagnostics.compositions
        compose.onNodeWithTag("dynamic-island", useUnmergedTree = true).performTouchInput { down(center) }
        repeat(16) {
            compose.onNodeWithTag("dynamic-island", useUnmergedTree = true).performTouchInput { moveBy(Offset(0f, 3f * density)) }
            compose.mainClock.advanceTimeByFrame()
            during += heightDp()
        }
        val pulledCompositions = IslandDiagnostics.compositions - compositionsBefore
        compose.runOnIdle { assertTrue("pulling", state.pullFraction != null) }
        val last = during.last()
        compose.onNodeWithTag("dynamic-island", useUnmergedTree = true).performTouchInput { up() }
        val after = ArrayList<Float>()
        repeat(60) { compose.mainClock.advanceTimeByFrame(); after += heightDp() }
        compose.runOnIdle { assertTrue("held as a peek", state.peeked); assertEquals(null, state.pullFraction) }
        // The body only ever grows while the finger pulls it down (a tolerance of half a dp for rounding) ...
        during.zipWithNext().forEach { (a, b) -> assertTrue("height went from $a to $b during the pull: $during", b >= a - .5f) }
        assertTrue("it grew at all: $during", during.last() > during.first() + 10f)
        // ... and letting go neither drops back nor jumps: the first frame after is within a few dp of the last frame of the pull.
        assertTrue("jumped on release from $last to ${after.first()}", kotlin.math.abs(after.first() - last) < 8f)
        assertTrue("fell back below the pull after release: min ${after.min()} vs $last", after.min() >= last - 1f)
        // Composition is not run for every touch event.
        assertTrue("the pull composed $pulledCompositions times for 16 moves", pulledCompositions <= 6)
        compose.mainClock.autoAdvance = true
    }

    /** Bubbles sit beside the pill; on a peek (panel-wide) they would hang off the panel's edge, so they wait until it closes. */
    @Test fun bubblesStayAwayFromAPeekAndReturnAfterIt() {
        show()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            NotificationFeed.ongoingCall = OngoingCall("Ada", "com.example.phone", System.currentTimeMillis() - 5_000, null)
            IslandTools.startTimer(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext, 5 * 60_000L)
            state.setPlaying(System.currentTimeMillis(), true)
        }
        compose.mainClock.advanceTimeBy(1_500)
        fun bubbles() = compose.onAllNodesWithTag("island-bubble-media", useUnmergedTree = true).fetchSemanticsNodes().size +
            compose.onAllNodesWithTag("island-bubble-timer", useUnmergedTree = true).fetchSemanticsNodes().size +
            compose.onAllNodesWithTag("island-bubble-call", useUnmergedTree = true).fetchSemanticsNodes().size
        assertEquals("one bubble beside the pill", 1, bubbles())
        compose.runOnIdle { state.peeked = true }
        compose.mainClock.advanceTimeBy(1_500)
        assertEquals("none on a peek", 0, bubbles())
        compose.runOnIdle { state.collapse() }
        compose.mainClock.advanceTimeBy(1_500)
        assertEquals("and back when it closes", 1, bubbles())
        compose.runOnIdle { NotificationFeed.ongoingCall = null; IslandTools.cancelTimer(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext) }
        compose.mainClock.autoAdvance = true
    }
}
