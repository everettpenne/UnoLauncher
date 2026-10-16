package com.jake.duolauncher

import com.jake.duolauncher.island.*

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opening and closing the island is a spring that runs for about a second. The island must not compose again on every frame of it: the
 * animated size is read when the island lays out and draws, so the whole of its faces are left alone while it moves.
 */
@RunWith(AndroidJUnit4::class)
class IslandMorphPerfInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val state = IslandState()

    private fun show() {
        compose.setContent {
            DuoTheme(false) {
                DynamicIsland(state, glass = null, deviceStatus = DeviceStatus(battery = 80), feedHeadline = "A headline that is long enough to wrap onto two lines of the open island",
                    environmentOverride = IslandEnvironment(PxRect(509f, 40f, 571f, 102f), 1080f, 80f, screenHeight = 2400f),
                    onSearch = {}, onOpenFeed = {}, onCustomize = {})
            }
        }
        Thread.sleep(2500)
        compose.waitForIdle()
    }

    private fun compositionsWhile(action: () -> Unit): Int {
        compose.mainClock.autoAdvance = false
        val before = IslandDiagnostics.compositions
        action()
        repeat(100) { compose.mainClock.advanceTimeByFrame() }
        val used = IslandDiagnostics.compositions - before
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        return used
    }

    @Test fun openingAndClosingDoNotComposeOncePerFrame() {
        show()
        val opening = compositionsWhile { compose.runOnIdle { state.toggle() } }
        val closing = compositionsWhile { compose.runOnIdle { state.collapse() } }
        // The state change itself, and the two moments a face appears or goes: a handful, not the ~100 frames the morph runs for.
        assertTrue("opening composed $opening times", opening <= 5)
        assertTrue("closing composed $closing times", closing <= 5)
    }

    /** The open island has to be tall enough for what is in it. With a feed headline the base height was short by the headline's two lines, and the
     * Search / Feed / Settings row was laid out below the island's bottom edge, where it is clipped away.
     */
    @Test fun theOpenIslandIsTallEnoughForItsHeadlineAndTheActionRow() {
        show()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.toggle() }
        compose.mainClock.advanceTimeBy(1_500)
        val island = compose.onNodeWithTag("dynamic-island", useUnmergedTree = true).getBoundsInRoot()
        val actions = compose.onNodeWithTag("island-actions", useUnmergedTree = true).getBoundsInRoot()
        // A row squeezed by the island's bottom edge is laid out shorter than its natural height (27 dp with labels), and one pushed past it has almost none (6 dp before the fix).
        assertTrue("the action row is only ${actions.height} tall", actions.height >= 24.dp)
        assertTrue("the action row ends at ${actions.bottom}, below the island's bottom ${island.bottom}", actions.bottom <= island.bottom + .5.dp)
        compose.mainClock.autoAdvance = true
    }
}
