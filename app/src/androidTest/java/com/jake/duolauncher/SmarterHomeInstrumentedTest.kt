package com.jake.duolauncher

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jake.duolauncher.appearance.Personality
import com.jake.duolauncher.appearance.UnoPersonality
import com.jake.duolauncher.island.AssistantGlyph
import com.jake.duolauncher.island.LiveUpdate
import com.jake.duolauncher.island.ScoreCard
import com.jake.duolauncher.island.SportsLogic
import com.jake.duolauncher.widgets.SuggestionsFace
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Suggested apps widget, the island's scoreboard and the assistant's glyph, drawn on a device. */
@RunWith(AndroidJUnit4::class)
class SmarterHomeInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val icon = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
    private fun app(n: Int) = AppEntry("com.example.app$n/com.example.app$n.Main", "App $n", icon)

    @After fun reset() { UnoPersonality.setForTest(Personality.BALANCED) }

    @Test fun theWidgetShowsAsManyAppsAsFitAndATapOpensOne() {
        val opened = ArrayList<String>()
        val apps = (1..12).map(::app)
        compose.setContent {
            CompositionLocalProvider(LocalHomeApps provides HomeApps(apps, { opened += it.label }, {})) {
                Box(Modifier.size(340.dp, 180.dp)) { SuggestionsFace(slot = 7, onOptions = {}) }
            }
        }
        compose.waitForIdle()
        // Wide and two rows tall: eight places, each with an app, and no more.
        compose.onAllNodesWithTag("uno-suggestion").assertCountEquals(8)
        assertEquals(8, (1..12).count { n -> compose.onAllNodesWithText("App $n").fetchSemanticsNodes().isNotEmpty() })
        val first = (1..12).first { n -> compose.onAllNodesWithText("App $n").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("App $first").performClick()
        compose.waitForIdle()
        assertEquals(listOf("App $first"), opened)
    }

    @Test fun aSmallWidgetShowsFourAndThePickersPreviewShowsPlaceholders() {
        compose.setContent { Box(Modifier.size(160.dp, 170.dp)) { SuggestionsFace(slot = 8, onOptions = {}) } }
        compose.waitForIdle()
        // Outside Home there are no apps to offer: four quiet places, and no hint about learning.
        assertEquals("places", 4, compose.onAllNodesWithTag("uno-suggestion", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals("hint", 0, compose.onAllNodesWithTag("uno-suggestions-hint").fetchSemanticsNodes().size)
    }

    @Test fun aScoreIsAScoreboard() {
        val score = SportsLogic.parse("Lakers 98 @ Celtics 101 · Q3 4:21", null)!!
        val update = LiveUpdate("k", "com.sports", "Sports", "Lakers 98 @ Celtics 101 · Q3 4:21", null, null, 0, 0, false, null, false, null, 0, 0L, null, score = score)
        compose.setContent { Box(Modifier.size(340.dp, 80.dp)) { ScoreCard(update, score, Color.Cyan) {} } }
        compose.waitForIdle()
        compose.onNodeWithTag("island-score-left", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("98", useUnmergedTree = true).assertExists(); compose.onNodeWithText("101", useUnmergedTree = true).assertExists()
        // Each side has its mark (its short name on a roundel) and its name.
        compose.onNodeWithText("LAK", useUnmergedTree = true).assertExists(); compose.onNodeWithText("Celtics", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithTag("island-team-mark", useUnmergedTree = true).assertCountEquals(2)
        compose.onNodeWithText("Q3 4:21", useUnmergedTree = true).assertExists()
    }

    @Test fun theAssistantsGlyphIsTheLoaderWhileItWorksExceptAtCalm() {
        var working = androidx.compose.runtime.mutableStateOf(true)
        compose.setContent { AssistantGlyph(working.value, 36.dp, Color.Cyan) }
        compose.onAllNodesWithTag("island-assistant-loader").assertCountEquals(1)
        compose.runOnIdle { working.value = false }
        compose.onAllNodesWithTag("island-assistant-loader").assertCountEquals(0)
        compose.onAllNodesWithTag("island-assistant-glyph").assertCountEquals(1)
        // At Calm it is the still sparkle even while the model works.
        compose.runOnIdle { UnoPersonality.setForTest(Personality.CALM); working.value = true }
        compose.onAllNodesWithTag("island-assistant-loader").assertCountEquals(0)
        compose.onAllNodesWithTag("island-assistant-glyph").assertCountEquals(1)
    }
}
