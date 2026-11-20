package com.jake.duolauncher

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jake.duolauncher.search.AskMode
import com.jake.duolauncher.search.AssistantClient
import com.jake.duolauncher.search.AssistantError
import com.jake.duolauncher.search.AssistantLog
import com.jake.duolauncher.search.AssistantLogRules
import com.jake.duolauncher.search.AssistantSession
import com.jake.duolauncher.search.AssistantState
import com.jake.duolauncher.search.ChatStore
import com.jake.duolauncher.search.RateButtons
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The assistant log on a device: an answer and a failure are written to the launcher's private file with what the model was shown and
 * wrote, the thumbs under a response save a verdict to that file, and with the log off nothing is written and there are no thumbs.
 */
@RunWith(AndroidJUnit4::class)
class AssistantLogInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private class Scripted(val answers: Map<String, String>) : AssistantClient {
        override val state: StateFlow<AssistantState> = MutableStateFlow(AssistantState.READY)
        override fun open() = Unit
        override fun close() = Unit
        override fun ask(query: String, mode: AskMode, context: String): Flow<String> = flow {
            val answer = answers[query] ?: throw AssistantError("Uno Lab couldn't answer.")
            answer.chunked(7).forEach { emit(it) }
        }
        override fun continueInLab(context: Context, query: String) = Unit
        override suspend fun plan(request: String): String? = null
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val file get() = File(context.filesDir, "assistant-log.jsonl")
    private var wasOn = false

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun waitFor(what: String, check: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < until) {
            var ok = false
            onMain { ok = check() }
            if (ok) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out waiting for $what")
    }

    /** What is in the file once the writer has caught up: the lines are written off the main thread. */
    private fun onDisk(count: Int): List<com.jake.duolauncher.search.LogEntry> {
        val until = System.currentTimeMillis() + 5_000
        var read = emptyList<com.jake.duolauncher.search.LogEntry>()
        while (System.currentTimeMillis() < until) {
            read = if (file.exists()) file.useLines { AssistantLogRules.read(it) } else emptyList()
            if (read.size >= count) break
            Thread.sleep(20)
        }
        return read
    }

    @Before fun start() = onMain {
        AssistantLog.load(context); wasOn = AssistantLog.on
        AssistantLog.setOn(context, true); AssistantLog.deleteAll(context)
        ChatStore.deleteAll(context)
    }

    @After fun forget() = onMain {
        AssistantSession.clientForTests = null
        AssistantSession.clear()
        AssistantLog.deleteAll(context); AssistantLog.setOn(context, wasOn)
        ChatStore.deleteAll(context)
    }

    @Test fun anAnswerAndAFailureAreWrittenDownWithWhatTheModelWasShownAndWrote() {
        val question = "who painted the starry night"
        val raw = "  **Vincent van Gogh** painted it in 1889. "
        onMain {
            AssistantSession.clientForTests = Scripted(mapOf(question to raw))
            AssistantSession.start(context, question, "- Today: Monday")
        }
        waitFor("the answer") { AssistantSession.state is AssistantSession.State.Ready }
        var id: Long? = null
        onMain { id = AssistantSession.logId }
        val answered = onDisk(1).single()
        assertEquals(id, answered.id)
        assertEquals("answer", answered.route); assertEquals("answered", answered.outcome); assertEquals(question, answered.said)
        assertEquals("- Today: Monday", answered.context)
        assertEquals("word for word, before it was tidied for the card", listOf(raw), answered.raw)
        assertTrue(answered.reply.contains("Vincent van Gogh")); assertTrue(answered.launcher.isNotEmpty())
        // Its turn in the conversation has the same time, which is how the conversation finds it to rate.
        onMain { assertEquals(answered.id, ChatStore.chats.single().turns.single().atMs) }

        onMain { AssistantSession.start(context, "what is the meaning of life", "") }
        waitFor("the failure") { AssistantSession.state is AssistantSession.State.Failed }
        val failed = onDisk(2).last()
        assertEquals("failed", failed.outcome); assertEquals("Uno Lab couldn't answer.", failed.reply)
        onMain { assertEquals("a failure is not a turn in the conversation", 1, ChatStore.chats.single().turns.size); assertEquals(failed.id, AssistantSession.logId) }
    }

    @Test fun theThumbsSaveAVerdictAndASecondTapTakesItBack() {
        val question = "what is the capital of France"
        onMain {
            AssistantSession.clientForTests = Scripted(mapOf(question to "Paris."))
            AssistantSession.start(context, question, "")
        }
        waitFor("the answer") { AssistantSession.state is AssistantSession.State.Ready }
        var id = 0L
        onMain { id = AssistantSession.logId!! }
        compose.setContent { RateButtons(id, Color.White) }
        compose.onNodeWithTag("assistant-rate-down").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("assistant-rate-down-on").assertExists()
        waitFor("the verdict on disk") { file.useLines { AssistantLogRules.read(it) }.singleOrNull()?.rating == -1 }
        compose.onNodeWithTag("assistant-rate-up").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("assistant-rate-up-on").assertExists()
        compose.onNodeWithTag("assistant-rate-up-on").performClick()
        compose.waitForIdle()
        waitFor("the verdict taken back") { file.useLines { AssistantLogRules.read(it) }.singleOrNull()?.rating == 0 }
        // The export is what is on show: one entry a line.
        var exported = ""
        onMain { exported = AssistantLog.exported(context) }
        assertEquals(question, AssistantLogRules.read(exported.lineSequence()).single().said)
    }

    @Test fun withTheLogOffNothingIsWrittenAndThereAreNoThumbs() {
        val question = "what is the capital of France"
        onMain {
            AssistantLog.setOn(context, false)
            AssistantSession.clientForTests = Scripted(mapOf(question to "Paris."))
            AssistantSession.start(context, question, "- Today: Monday")
        }
        waitFor("the answer") { AssistantSession.state is AssistantSession.State.Ready }
        var id: Long? = null
        onMain { id = AssistantSession.logId; assertEquals(0, AssistantLog.entries.size) }
        Thread.sleep(300)
        assertTrue("no file, or nothing in it", !file.exists() || file.length() == 0L)
        compose.setContent { RateButtons(id, Color.White) }
        compose.onAllNodesWithTag("assistant-rate").assertCountEquals(0)
        assertNull(AssistantLog.entry(id))
        // The conversation is kept as before: the log is separate from it.
        onMain { assertEquals(1, ChatStore.chats.single().turns.size) }
    }
}
