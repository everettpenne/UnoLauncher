package com.jake.duolauncher

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jake.duolauncher.search.AnswerAberration
import com.jake.duolauncher.search.AskMode
import com.jake.duolauncher.search.AssistantClient
import com.jake.duolauncher.search.AssistantSession
import com.jake.duolauncher.search.AssistantState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The wave of split colour that crosses the screen as the assistant finishes: that it really parts red from blue on the screen, that a
 * turn ending fires it once and it then leaves nothing behind, and that an answer already finished when the screen is drawn does not.
 * The pictures are kept in the app's files directory as aberration-<name>.png, for looking at.
 */
@RunWith(AndroidJUnit4::class)
class AnswerAberrationInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private class Streaming : AssistantClient {
        override val state: StateFlow<AssistantState> = MutableStateFlow(AssistantState.READY)
        val answer = Channel<String>(Channel.UNLIMITED)
        override fun open() = Unit
        override fun close() = Unit
        override fun ask(query: String, mode: AskMode, context: String): Flow<String> = answer.consumeAsFlow()
        override fun continueInLab(context: Context, query: String) = Unit
        override suspend fun plan(request: String): String? = null
    }

    // The turns these tests run are real ones, so they are taken out of the conversation again (they were once left in, and the next
    // real question was sent with them as its history).
    @After fun forget() = instrumentation.runOnMainSync {
        AssistantSession.clientForTests = null; AssistantSession.clear(); com.jake.duolauncher.search.ChatStore.deleteAll(context)
    }

    /** White bars on black: every edge is somewhere red and blue can be seen to part. */
    @androidx.compose.runtime.Composable private fun Bars() {
        Row(Modifier.fillMaxSize().background(Color.Black), horizontalArrangement = Arrangement.SpaceEvenly) {
            repeat(6) { Box(Modifier.width(24.dp).fillMaxHeight().background(Color.White)) }
        }
    }

    private fun shot(name: String): Bitmap {
        Thread.sleep(500)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(context.filesDir, "aberration-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    /** Pixels that are strongly one of red or blue and not the other: there are none in white bars on black unless the colours are split. */
    private fun fringe(bitmap: Bitmap): Int {
        var n = 0
        // Below the status bar and above the navigation bar, whose own icons are not the test's.
        for (y in bitmap.height / 6 until bitmap.height * 5 / 6 step 4) for (x in 0 until bitmap.width step 2) {
            val c = bitmap.getPixel(x, y)
            if (kotlin.math.abs(android.graphics.Color.red(c) - android.graphics.Color.blue(c)) > 120) n++
        }
        return n
    }

    private fun waveOn() = compose.onAllNodes(hasTestTag("answer-aberration"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test fun theWavePartsRedFromBlueAndAtRestThereIsNone() {
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 33)
        val at = mutableStateOf<Float?>(null)
        compose.setContent { AnswerAberration(testProgress = at.value) { Bars() } }
        compose.waitForIdle()
        assertEquals("nothing is split at rest", 0, fringe(shot("rest")))
        assertTrue(!waveOn())
        var most = 0
        for ((name, t) in listOf("early" to .10f, "peak" to .16f, "middle" to .35f, "late" to .6f)) {
            at.value = t; compose.waitForIdle()
            val n = fringe(shot(name))
            most = maxOf(most, n)
            assertTrue("split colour on screen at $name ($n)", n > 200)
        }
        at.value = null; compose.waitForIdle()
        assertEquals("and gone again afterwards", 0, fringe(shot("after")))
        assertTrue("fringe pixels at most: $most", most > 1000)
    }

    @Test fun aTurnEndingFiresItOnceAndItLeavesNothingBehind() {
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 33)
        val client = Streaming()
        instrumentation.runOnMainSync { AssistantSession.clientForTests = client; AssistantSession.clear() }
        compose.setContent { AnswerAberration { Bars() } }
        compose.waitForIdle()
        // The clock is held from here, so the wave stays where the test puts it.
        compose.mainClock.autoAdvance = false
        instrumentation.runOnMainSync { AssistantSession.start(context, "who painted the starry night", "") }
        compose.mainClock.advanceTimeBy(100)
        assertTrue("no wave while the answer is written", !waveOn())
        client.answer.trySend("Vincent van Gogh painted it.")
        compose.mainClock.advanceTimeBy(100)
        assertTrue(!waveOn())
        client.answer.close()
        val until = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < until && !waveOn()) { compose.mainClock.advanceTimeBy(32); Thread.sleep(20) }
        assertTrue("the turn is done", AssistantSession.state is AssistantSession.State.Ready)
        assertTrue("the wave runs once the answer is finished", waveOn())
        compose.mainClock.advanceTimeBy(150)
        assertTrue("split colour on screen as the answer finishes", fringe(shot("finished")) > 200)
        compose.mainClock.advanceTimeBy(1_200)
        assertTrue("the wave is over", !waveOn())
        assertEquals(0, fringe(shot("finished-after")))
        // The answer being cleared afterwards is not a finish.
        instrumentation.runOnMainSync { AssistantSession.clear() }
        compose.mainClock.advanceTimeBy(200)
        assertTrue(!waveOn())
        compose.mainClock.autoAdvance = true
    }

    @Test fun anAnswerAlreadyFinishedWhenTheScreenIsDrawnDoesNotFireIt() {
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 33)
        val client = Streaming()
        instrumentation.runOnMainSync {
            AssistantSession.clientForTests = client; AssistantSession.clear()
            AssistantSession.start(context, "who painted the starry night", "")
            client.answer.trySend("Vincent van Gogh painted it."); client.answer.close()
        }
        val until = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < until && AssistantSession.state !is AssistantSession.State.Ready) Thread.sleep(20)
        assertTrue(AssistantSession.state is AssistantSession.State.Ready)
        compose.mainClock.autoAdvance = false
        compose.setContent { AnswerAberration { Bars() } }
        repeat(10) { compose.mainClock.advanceTimeBy(32); assertTrue(!waveOn()) }
        compose.mainClock.autoAdvance = true
    }

    /** A lifecycle the test moves by hand, standing in for Home going behind another app and coming back. */
    private class Screen(start: androidx.lifecycle.Lifecycle.State) : androidx.lifecycle.LifecycleOwner {
        val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).also { it.currentState = start }
        override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
    }

    /** Home stays drawn behind another app. An answer that finishes then is not kept for later: coming back shows no wave. */
    @Test fun anAnswerThatFinishesWhileTheScreenIsNotShowingIsLetGo() {
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 33)
        val client = Streaming()
        val screen = Screen(androidx.lifecycle.Lifecycle.State.CREATED)
        instrumentation.runOnMainSync { AssistantSession.clientForTests = client; AssistantSession.clear() }
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides screen) { AnswerAberration { Bars() } }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        instrumentation.runOnMainSync { AssistantSession.start(context, "who painted the starry night", "") }
        compose.mainClock.advanceTimeBy(100)
        client.answer.trySend("Vincent van Gogh painted it."); client.answer.close()
        val until = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < until && AssistantSession.state !is AssistantSession.State.Ready) { compose.mainClock.advanceTimeBy(32); Thread.sleep(20) }
        assertTrue(AssistantSession.state is AssistantSession.State.Ready)
        repeat(10) { compose.mainClock.advanceTimeBy(32); assertTrue("no wave on a screen that is not showing", !waveOn()) }
        instrumentation.runOnMainSync { screen.registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        repeat(20) { compose.mainClock.advanceTimeBy(32); assertTrue("and none on coming back to it", !waveOn()) }
        compose.mainClock.autoAdvance = true
    }

    /** A task ending is a finish too, and the agent's session is made after the screen is first drawn (when search first opens). */
    @Test fun aTaskEndingFiresItEvenThoughItsSessionCameAfterTheScreen() {
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 33)
        instrumentation.runOnMainSync { AssistantSession.clientForTests = null; AssistantSession.clear() }
        compose.setContent { AnswerAberration { Bars() } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val planner = object : com.jake.duolauncher.search.agent.Planner {
            override suspend fun plan(request: com.jake.duolauncher.search.agent.PlanRequest): com.jake.duolauncher.search.agent.ParseResult {
                gate.await()
                return com.jake.duolauncher.search.agent.ParseResult.Ok(com.jake.duolauncher.search.agent.Plan.Steps(listOf(
                    com.jake.duolauncher.search.agent.Step(com.jake.duolauncher.search.agent.tools.TimerTool,
                        com.jake.duolauncher.search.agent.Params(mapOf("length" to "20 minutes"))))))
            }
        }
        val env = com.jake.duolauncher.search.agent.AndroidToolEnv(context, null, emptyList(), { false }, {})
        var session: com.jake.duolauncher.search.agent.AgentSession? = null
        instrumentation.runOnMainSync {
            session = com.jake.duolauncher.search.agent.AgentSessionHolder.bind({ planner }, { env })
            // Planned and not carried out, so the test starts no timer.
            session!!.run("timer for 20 minutes", planOnly = true)
        }
        compose.mainClock.advanceTimeBy(100)
        assertTrue("planning: ${session!!.state}", session!!.state is com.jake.duolauncher.search.agent.AgentState.Planning)
        assertTrue(!waveOn())
        gate.complete(Unit)
        val until = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < until && !waveOn()) { compose.mainClock.advanceTimeBy(32); Thread.sleep(20) }
        assertTrue("the task is done: ${session!!.state}", session!!.state is com.jake.duolauncher.search.agent.AgentState.Done)
        assertTrue("the wave runs once the task is finished", waveOn())
        compose.mainClock.advanceTimeBy(1_300)
        assertTrue(!waveOn())
        instrumentation.runOnMainSync { session!!.clear() }
        compose.mainClock.autoAdvance = true
    }
}
