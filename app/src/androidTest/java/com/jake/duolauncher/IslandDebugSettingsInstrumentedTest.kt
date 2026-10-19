package com.jake.duolauncher

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jake.duolauncher.island.DebugTarget
import com.jake.duolauncher.island.IslandDebug
import com.jake.duolauncher.island.LiveKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The island debug page: its buttons walk a value, forcing a dummy turns it on, and the summary line follows. */
@RunWith(AndroidJUnit4::class)
class IslandDebugSettingsInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    @After fun reset() { IslandDebug.reset() }

    private fun show() = compose.setContent { MaterialTheme { Column(androidx.compose.ui.Modifier.verticalScroll(rememberScrollState())) { IslandDebugSettings() } } }

    @Test fun theButtonsWalkAValueAndTheSummaryFollows() {
        show()
        compose.onNodeWithTag("debug-left-w-p5").performScrollTo().performClick()
        compose.onNodeWithTag("debug-left-w-p1").performScrollTo().performClick()
        compose.onNodeWithTag("debug-left-w-value").performScrollTo().assertTextEquals("+6")
        compose.onNodeWithTag("debug-right-x-m5").performScrollTo().performClick()
        compose.onNodeWithTag("debug-right-x-value").performScrollTo().assertTextEquals("-5")
        assertEquals(6f, IslandDebug.group(DebugTarget.LEFT).dw, 0f)
        assertTrue(IslandDebug.summary().contains("left w+6 x+0 y+0"))
        assertTrue(IslandDebug.summary().contains("right w+0 x-5 y+0"))
        compose.onNodeWithTag("debug-reset").performScrollTo().performClick()
        compose.onNodeWithTag("debug-left-w-value").performScrollTo().assertTextEquals("0")
    }

    @Test fun aSwitchForcesADummyActivity() {
        show()
        compose.onNodeWithTag("debug-force-timer").performScrollTo().performClick()
        assertTrue(LiveKind.TIMER in IslandDebug.forced)
        compose.onNodeWithTag("debug-force-timer").performClick()
        assertTrue(LiveKind.TIMER !in IslandDebug.forced)
    }
}
