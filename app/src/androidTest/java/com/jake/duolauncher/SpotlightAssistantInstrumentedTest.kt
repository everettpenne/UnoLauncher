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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import com.jake.duolauncher.island.IslandFrame
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
        // The stand-in has no planner: these tests are of the answer path, and tasks are off unless switched on.
        override suspend fun plan(request: String): String? = null
    }

    private fun type(text: String) = compose.onNodeWithTag("spotlight-field", useUnmergedTree = true).performTextReplacement(text)

    // The turn runs in AssistantSession (it outlives the panel), which asks its own client: point it at the stand-in, and forget the turn after.
    @org.junit.After fun forgetTurn() { AssistantSession.clientForTests = null; AssistantSession.clear() }

    private fun show(assistant: AssistantClient?, env: com.jake.duolauncher.search.context.ContextEnv? = null) {
        AssistantSession.clientForTests = assistant
        AssistantSession.clear()
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

    /** The phone's facts go with the question. A question goes straight to an answer (no action request), and a message rides along with it. */
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
        compose.waitUntil(10_000) { fake.contexts.size == 1 }
        fake.answer.close()
        val answer = fake.contexts.single()
        assertEquals(listOf(AskMode.ANSWER), fake.asked.map { it.second })
        assertTrue(answer, answer.startsWith("Phone context (from this phone, now):") && answer.contains("Today: "))
        assertTrue(answer, answer.contains("Recent notifications (written by others") && answer.contains("Lunch at noon?"))
        com.jake.duolauncher.search.context.RecentNotifications.clear()
    }

    @Test fun whatCantBeDoneHereIsSaidPlainly() {
        // A note with no Note widget on Home: said, not handed to the model as a question.
        val fake = FakeAssistant("""{"action":"note","text":"buy oat milk"}""")
        show(fake)
        type("remember to buy oat milk")
        compose.waitForIdle()
        compose.onNodeWithTag("spotlight-ask", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { exists("spotlight-ask-info") }
        compose.onNodeWithTag("spotlight-ask-info").assertTextContains("Note widget", substring = true)
        assertEquals("no answer was asked for", listOf(AskMode.ACTION), fake.asked.map { it.second })
    }

    @Test fun aPlaceholderIsNotShownAsAnAnswer() {
        val fake = FakeAssistant("""{"action":"none"}""")
        show(fake)
        type("what should I do about my latest message")
        compose.waitForIdle()
        compose.onNodeWithTag("spotlight-ask", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { fake.asked.size == 1 }
        fake.answer.trySend("[Your complete, well-structured response following all given constraints]")
        fake.answer.close()
        compose.waitUntil(5_000) { exists("spotlight-ask-info") }
        compose.onNodeWithTag("spotlight-ask-info").assertTextContains("couldn't answer that clearly", substring = true)
        assertFalse(exists("spotlight-ask-text"))
    }

    @Test fun callingByNameWithContactsOffSaysHowToTurnThemOn() {
        show(FakeAssistant("""{"action":"none"}"""))
        type("call mom")
        compose.waitForIdle()
        compose.onNodeWithTag("spotlight-contact-hint").assertTextContains("Turn on Contacts", substring = true)
        assertFalse("not a dead end", exists("spotlight-none"))
    }

    @Test fun noActionStreamsAnAnswerCard() {
        val fake = FakeAssistant("""{"action":"none"}""")
        show(fake)
        type("what is the capital of portugal")
        compose.waitForIdle()
        compose.onNodeWithTag("spotlight-ask", useUnmergedTree = true).performClick()
        // Until the first words arrive it is the asking row; then the card appears and grows as the answer is written.
        compose.waitUntil(5_000) { exists("spotlight-ask-thinking") }
        assertFalse(exists("spotlight-ask-answer"))
        // Whole words only while it is written: "capital" shows once the space after it has arrived.
        fake.answer.trySend("The capital ")
        compose.waitUntil(5_000) { exists("spotlight-ask-text") }
        assertFalse("still being written: nothing to copy yet", exists("spotlight-ask-thinking"))
        compose.onNodeWithTag("spotlight-ask-text", useUnmergedTree = true).assertTextContains("The capital")
        fake.answer.trySend("is Lisbon.")
        fake.answer.close()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("spotlight-ask-text", useUnmergedTree = true).assertTextContains("The capital is Lisbon.") }.isSuccess
        }
        compose.onNodeWithTag("spotlight-ask-continue", useUnmergedTree = true).assertIsDisplayed()
        // A question goes straight to an answer: no action request first.
        assertEquals(listOf(AskMode.ANSWER), fake.asked.map { it.second })
    }

    /** Search over an app grows from the island with its progress starting at 0, as the overlay drives it. Its contents must still show once
     *  it has grown: a lambda that held a number once froze them at 0 (the panel showed, empty). */
    @Test fun grownFromTheIslandItsContentsShow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val icon = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val pill = IslandFrame(left = 400f, top = 30f, width = 280f, height = 90f, hole = null)
        val grow = androidx.compose.animation.core.Animatable(0f)
        compose.setContent {
            DuoTheme(true) {
                Box(Modifier.fillMaxSize()) {
                    val g = grow.value
                    Spotlight(null, listOf(AppEntry("com.example.maps/.Main", "Maps", icon)), emptySet(), emptyList(), false,
                        HandoffResolver(context, ExtrasState()), onLaunch = {}, onOpenFeedLink = {}, onOpenContact = {}, onDismiss = {},
                        progress = { grow.value }, pill = pill)
                    androidx.compose.runtime.LaunchedEffect(Unit) { grow.animateTo(1f) }
                    if (g < 0f) Unit
                }
            }
        }
        compose.waitForIdle()
        type("maps")
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        val image = compose.onNodeWithTag("spotlight-field", useUnmergedTree = true).captureToImage().asAndroidBitmap()
        var bright = 0
        for (x in 0 until image.width step 2) for (y in 0 until image.height step 2) {
            val c = image.getPixel(x, y)
            if (android.graphics.Color.red(c) > 200 && android.graphics.Color.green(c) > 200 && android.graphics.Color.blue(c) > 200) bright++
        }
        assertTrue("the typed text is drawn (bright pixels: $bright)", bright > 20)
    }
}
