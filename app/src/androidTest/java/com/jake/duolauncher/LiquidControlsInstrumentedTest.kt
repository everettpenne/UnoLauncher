package com.jake.duolauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.jake.duolauncher.appearance.*
import com.jake.duolauncher.panel.*
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** The liquid switch and slider: a tap, a drag and the semantics a screen reader and the other tests rely on. */
class LiquidControlsInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private var on by mutableStateOf(false)
    private var level by mutableStateOf(.5f)

    private fun show() {
        compose.setContent {
            DuoTheme(false) {
                val backdrop = rememberLayerBackdrop()
                val glass = PageGlass(backdrop, Color.White.copy(alpha = .3f), GlassSettings.Default)
                Box(Modifier.size(320.dp, 200.dp)) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color(0xFF668899)))
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        LiquidSwitchControl(on, { on = it }, glass, Modifier.testTag("sw"))
                        LiquidSliderControl(level, 0f..1f, { level = it }, glass, Modifier.testTag("slider"))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun aTapFlipsTheSwitchBothWaysAndSaysItsState() {
        show()
        compose.onNodeWithTag("sw").assertContentDescriptionEquals("Off")
        compose.onNodeWithTag("sw").performClick(); compose.waitForIdle()
        assertTrue(on); compose.onNodeWithTag("sw").assertContentDescriptionEquals("On")
        compose.onNodeWithTag("sw").performClick(); compose.waitForIdle()
        assertFalse(on)
    }

    @Test fun theSwitchCanBeFlippedForAScreenReader() {
        show()
        compose.onNodeWithTag("sw").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue(on)
    }

    @Test fun draggingTheThumbAcrossTurnsItOnAndBackOffTurnsItOffAgain() {
        show()
        compose.onNodeWithTag("sw").performTouchInput { swipeRight(startX = left + 20f, endX = right - 4f, durationMillis = 300) }
        compose.waitForIdle()
        assertTrue("dragged right", on)
        compose.onNodeWithTag("sw").performTouchInput { swipeLeft(startX = right - 20f, endX = left + 4f, durationMillis = 300) }
        compose.waitForIdle()
        assertFalse("dragged back left", on)
    }

    @Test fun aShortDragThatEndsWhereItStartedLeavesTheSwitchAlone() {
        show()
        compose.onNodeWithTag("sw").performTouchInput { swipeRight(startX = left + 20f, endX = left + 26f, durationMillis = 200) }
        compose.waitForIdle()
        assertFalse(on)
    }

    @Test fun theSliderFollowsATapAndADrag() {
        show()
        compose.onNodeWithTag("slider").performTouchInput { click(percentOffset(.9f, .5f)) }
        compose.waitForIdle()
        assertTrue("tap near the right end sets a high value, was $level", level > .8f)
        compose.onNodeWithTag("slider").performTouchInput { swipeLeft(startX = right * .9f, endX = right * .1f, durationMillis = 300) }
        compose.waitForIdle()
        assertTrue("dragging to the left end sets a low value, was $level", level < .2f)
    }

    @Test fun theSliderStopsAtItsEndsWhenTheFingerGoesPast() {
        show()
        compose.onNodeWithTag("slider").performTouchInput { swipeRight(startX = right * .5f, endX = right - 1f, durationMillis = 200) }
        compose.waitForIdle()
        assertTrue(level in .95f..1f)
    }
}
