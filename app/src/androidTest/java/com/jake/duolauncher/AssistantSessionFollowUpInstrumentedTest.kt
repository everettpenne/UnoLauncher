package com.jake.duolauncher

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jake.duolauncher.search.AskMode
import com.jake.duolauncher.search.AssistantClient
import com.jake.duolauncher.search.AssistantError
import com.jake.duolauncher.search.AssistantSession
import com.jake.duolauncher.search.AssistantState
import com.jake.duolauncher.search.ChatStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A follow-up sent while the last answer is still being written: on a slow phone, or a long answer, the person asks the next thing before
 * the first has finished. The first turn used to unbind the client the two share as it was cancelled, so the follow-up waited for a
 * service that was gone ("Uno Lab isn't connected"), and the half-written first answer was never kept, so the conversation lost the turn
 * the follow-up followed.
 */
@RunWith(AndroidJUnit4::class)
class AssistantSessionFollowUpInstrumentedTest {
    /** Bound the way LocalAssistant is: open binds once, close unbinds, and asking while unbound fails as Lab's real client does. */
    private class BindingClient : AssistantClient {
        override val state: StateFlow<AssistantState> = MutableStateFlow(AssistantState.READY)
        var bound = false
        val answers = mutableMapOf<String, Channel<String>>()
        override fun open() { bound = true }
        override fun close() { bound = false }
        override fun ask(query: String, mode: AskMode, context: String): Flow<String> = flow {
            if (!bound) throw AssistantError("Uno Lab isn't connected.")
            for (piece in answers.getValue(query)) emit(piece)
        }
        override fun continueInLab(context: Context, query: String) = Unit
        override suspend fun plan(request: String): String? = null
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun waitFor(what: String, check: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < until) {
            var ok = false
            onMain { ok = check() }
            if (ok) return
            Thread.sleep(20)
        }
        var now: Any? = null
        onMain { now = AssistantSession.state }
        throw AssertionError("timed out waiting for $what; state $now, streamed '${AssistantSession.streamed}'")
    }

    @After fun forget() = onMain {
        AssistantSession.clientForTests = null
        AssistantSession.clear()
        ChatStore.deleteAll(context)
    }

    @Test fun aFollowUpSentWhileTheAnswerIsStillComingIsAnsweredAndBothTurnsAreKept() {
        val client = BindingClient()
        // Questions, not actions, so each turn goes straight to the answer.
        val first = "who painted the starry night"
        val second = "where did he live"
        client.answers[first] = Channel(Channel.UNLIMITED)
        client.answers[second] = Channel(Channel.UNLIMITED)
        onMain {
            ChatStore.deleteAll(context)
            AssistantSession.clientForTests = client
            AssistantSession.clear()
            AssistantSession.start(context, first, "")
        }
        onMain { client.answers.getValue(first).trySend("Vincent van Gogh painted it") }
        waitFor("the first answer to start") { AssistantSession.streamed.startsWith("Vincent") }

        // The next question, before the first answer has finished.
        onMain { AssistantSession.start(context, second, "") }
        onMain { client.answers.getValue(second).trySend("He lived in France."); client.answers.getValue(second).close() }
        waitFor("the follow-up to be answered") { AssistantSession.state is AssistantSession.State.Ready }

        onMain {
            val ready = AssistantSession.state as AssistantSession.State.Ready
            assertEquals(second, ready.query)
            assertEquals("He lived in France.", ready.answer)
            val turns = ChatStore.active?.turns.orEmpty()
            assertEquals(listOf(first, second), turns.map { it.said })
            assertTrue(turns.first().reply, turns.first().reply.startsWith("Vincent van Gogh painted it"))
        }
    }

    @Test fun theUnfinishedAnswerIsInTheConversationBeforeTheFollowUpIsAsked() {
        val client = BindingClient()
        val first = "who wrote dune"
        client.answers[first] = Channel(Channel.UNLIMITED)
        onMain {
            ChatStore.deleteAll(context)
            AssistantSession.clientForTests = client
            AssistantSession.clear()
            AssistantSession.start(context, first, "")
        }
        onMain { client.answers.getValue(first).trySend("Frank Herbert wrote it") }
        waitFor("the answer to start") { AssistantSession.streamed.startsWith("Frank") }
        // What search does before a follow-up: keep the turn in flight, then read the conversation for the follow-up's context.
        onMain {
            AssistantSession.keepUnfinished(context)
            val earlier = com.jake.duolauncher.search.ChatLog.earlier(ChatStore.active, System.currentTimeMillis())
            assertTrue(earlier, earlier.contains("who wrote dune") && earlier.contains("Frank Herbert"))
        }
        // Replacing it afterwards does not keep it twice.
        client.answers["when"] = Channel<String>(Channel.UNLIMITED).also { it.trySend("In 1965."); it.close() }
        onMain { AssistantSession.start(context, "when", "") }
        waitFor("the follow-up") { (AssistantSession.state as? AssistantSession.State.Ready)?.query == "when" }
        onMain { assertEquals(listOf(first, "when"), ChatStore.active?.turns.orEmpty().map { it.said }) }
    }

    @Test fun aTurnThatFinishesClosesTheClientAndTheNextOneOpensItAgain() {
        val client = BindingClient()
        client.answers["what is a fjord"] = Channel<String>(Channel.UNLIMITED).also { it.trySend("A long, narrow sea inlet."); it.close() }
        onMain {
            AssistantSession.clientForTests = client
            AssistantSession.clear()
            AssistantSession.start(context, "what is a fjord", "")
        }
        waitFor("the answer") { AssistantSession.state is AssistantSession.State.Ready }
        onMain { assertTrue("a finished turn lets the service go", !client.bound) }

        client.answers["how deep are they"] = Channel<String>(Channel.UNLIMITED).also { it.trySend("Often over a kilometre."); it.close() }
        onMain { AssistantSession.start(context, "how deep are they", "") }
        waitFor("the second answer") { (AssistantSession.state as? AssistantSession.State.Ready)?.query == "how deep are they" }
    }
}
