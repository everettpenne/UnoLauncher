package com.jake.duolauncher

import com.jake.duolauncher.island.*
import com.jake.duolauncher.search.*
import com.jake.duolauncher.appearance.*

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Spotlight's Ask Qwen row, with a fake in place of Uno Lab: the row, an action it suggests, and an answer streaming in. */
@RunWith(AndroidJUnit4::class)
class SpotlightAssistantInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    /** Answers ACTION with [action]; ANSWER streams whatever the test sends on [answer]. */
    private class FakeAssistant(val action: String) : AssistantClient {
        override val state: StateFlow<AssistantState> = MutableStateFlow(AssistantState.READY)
        val answer = Channel<String>(Channel.UNLIMITED)
        val asked = mutableListOf<Pair<String, AskMode>>()
        val contexts = mutableListOf<String>()
        var opened = 0; var closed = 0
        override fun open() { opened++ }
        override fun close() { closed++ }
        override fun ask(query: String, mode: AskMode, context: String): Flow<String> {
            asked += query to mode
            contexts += context
            return if (mode == AskMode.ACTION) flowOf(action) else answer.consumeAsFlow()
        }
        override fun continueInLab(context: Context, query: String) = Unit
    }

    private fun type(text: String) = compose.onNodeWithTag("spotlight-field", useUnmergedTree = true).performTextReplacement(text)

    private fun show(assistant: AssistantClient?, env: com.jake.duolauncher.search.context.ContextEnv? = null) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val icon = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val apps = listOf("Calculator", "Maps").map { AppEntry("com.example.${it.lowercase()}/.Main", it, icon) }
        compose.setContent {
            DuoTheme(false) {
                Box(Modifier.fillMaxSize()) {
                    Spotlight(null, apps, emptySet(), emptyList(), false, HandoffResolver(context, ExtrasState()),
                        onLaunch = {}, onOpenFeedLink = {}, onOpenContact = {}, onDismiss = {}, assistant = assistant, contextEnv = env)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodesWithTagCount(tag) > 0
    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    /** On the emulator Uno Lab is absent or debug-signed: either way it is not the Lab Uno trusts, so the feature stays hidden. */
    @Test fun aLabNotSignedWithItsPinnedKeyIsNotOffered() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val signers = runCatching {
            context.packageManager.getPackageInfo(LabTrust.PACKAGE, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo!!.apkContentsSigners.map { UpdateSecurity.sha256Hex(it.toByteArray()) }.toSet()
        }.getOrDefault(emptySet())
        org.junit.Assume.assumeFalse("a release-signed Uno Lab is installed", LabTrust.trusted(signers))
        assertFalse(LocalAssistant.isAvailable(context))
        val client = LocalAssistant(context)
        client.open()
        assertEquals(AssistantState.NOT_INSTALLED, client.state.value)
        client.close()
    }

    @Test fun withoutTheAssistantThereIsNoAskRow() {
        show(null)
        type("capital of portugal")
        compose.waitForIdle()
        assertFalse(exists("spotlight-ask"))
    }

    @Test fun aSuggestedActionIsARowThatOnlyRunsWhenTapped() {
        val fake = FakeAssistant("""{"action":"timer","seconds":720,"label":"pasta"}""")
        show(fake)
        assertEquals("bound as search opens", 1, fake.opened)
        type("timer for pasta twelve minutes")
        compose.waitForIdle()
        compose.onNodeWithTag("spotlight-ask", useUnmergedTree = true).assertIsDisplayed()
        assertTrue("nothing is sent before the tap", fake.asked.isEmpty())
        // One word, or a typed command, is not offered.
        type("maps"); compose.waitForIdle(); assertFalse(exists("spotlight-ask"))
        type("timer 5"); compose.waitForIdle(); assertFalse(exists("spotlight-ask"))

        type("timer for pasta twelve minutes"); compose.waitForIdle()
        compose.onNodeWithTag("spotlight-ask", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { exists("spotlight-ask-action") }
        compose.onNodeWithTag("spotlight-ask-action").assertIsDisplayed().assertTextContains("12 min timer · pasta", substring = true)
        assertEquals(listOf("timer for pasta twelve minutes" to AskMode.ACTION), fake.asked)
    }

    /** The phone's facts go with the question: the core with both requests, a message only with the one that can't become an action. */
    @Test fun theQuestionTakesThePhonesFactsAndMessagesOnlyGoWithTheAnswer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        com.jake.duolauncher.search.context.RecentNotifications.record(com.jake.duolauncher.search.context.RecentNotifications.Entry(
            "com.example.chat", "Messages", "Ada", "Lunch at noon?", System.currentTimeMillis()))
        val env = com.jake.duolauncher.search.context.ContextEnv(context, { LauncherState() }, { ExtrasState(localAssistant = true) },
            { com.jake.duolauncher.appearance.AppearanceState() }, { emptyList() })
        val fake = FakeAssistant("""{"action":"none"}""")
        show(fake, env)
        type("who messaged me today")
        compose.waitForIdle()
        compose.onNodeWithTag("spotlight-ask", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { fake.contexts.size == 2 }
        fake.answer.close()
        val (action, answer) = fake.contexts
        assertTrue(action, action.startsWith("Phone context (from this phone, now):") && action.contains("Today: ") && action.contains("Battery: "))
        assertFalse("a message never goes with a request that may become an action: $action", action.contains("Lunch at noon"))
        assertTrue(answer, answer.contains("Recent notifications (written by others") && answer.contains("Lunch at noon?"))
        com.jake.duolauncher.search.context.RecentNotifications.clear()
    }

    @Test fun noActionStreamsAnAnswerCard() {
        val fake = FakeAssistant("""{"action":"none"}""")
        show(fake)
        type("what is the capital of portugal")
        compose.waitForIdle()
        compose.onNodeWithTag("spotlight-ask", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { exists("spotlight-ask-answer") }
        fake.answer.trySend("The capital")
        compose.waitUntil(5_000) { exists("spotlight-ask-text") }
        compose.onNodeWithTag("spotlight-ask-text", useUnmergedTree = true).assertTextContains("The capital")
        fake.answer.trySend(" is Lisbon.")
        fake.answer.close()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("spotlight-ask-text", useUnmergedTree = true).assertTextContains("The capital is Lisbon.") }.isSuccess
        }
        compose.onNodeWithTag("spotlight-ask-continue", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(listOf(AskMode.ACTION, AskMode.ANSWER), fake.asked.map { it.second })
    }
}
