package com.jake.duolauncher

import com.jake.duolauncher.island.*
import com.jake.duolauncher.search.*
import com.jake.duolauncher.appearance.*

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Search grows out of where the island really is, and dims the whole window, even when its parent starts below the top of the window. */
@RunWith(AndroidJUnit4::class)
class SpotlightPlacementInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun thePanelTopMatchesThePillAndTheScrimReachesTheTopEdgeInsideAnOffsetParent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pill = IslandFrame(left = 300f, top = 60f, width = 480f, height = 100f, hole = null)
        compose.setContent {
            DuoTheme(false) {
                // A parent that starts 120dp down, like a window whose content is laid out under the status bar or the cutout.
                Box(Modifier.fillMaxSize().padding(top = 120.dp)) {
                    Spotlight(null, emptyList(), emptySet(), emptyList(), false, HandoffResolver(context, ExtrasState()),
                        onLaunch = {}, onOpenFeedLink = {}, onOpenContact = {}, onDismiss = {}, progress = { 1f }, pill = pill)
                }
            }
        }
        compose.waitForIdle()
        val panel = compose.onNodeWithTag("spotlight").fetchSemanticsNode().boundsInWindow
        val scrim = compose.onNodeWithTag("spotlight-scrim").fetchSemanticsNode().boundsInWindow
        println("PLACEMENT panel=$panel scrim=$scrim")
        assertEquals("the panel starts at the pill's top, in window coordinates: $panel / $scrim", pill.top, panel.top, 2f)
        assertEquals("the dimming reaches the very top of the window", 0f, scrim.top, 1f)
        assertTrue("and covers its height", scrim.height >= compose.onNodeWithTag("spotlight").fetchSemanticsNode().boundsInRoot.bottom)
    }
}
