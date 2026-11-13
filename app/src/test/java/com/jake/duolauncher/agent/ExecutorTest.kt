package com.jake.duolauncher.agent

import com.jake.duolauncher.AppEntry
import com.jake.duolauncher.search.agent.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutorTest {
    private var clock = 0L
    private val env = object : ToolEnv {
        override val noteSlot: Int? = null
        override val apps: List<AppEntry> = emptyList()
        override fun focusOn() = false
        override fun toggleFocus() = Unit
        override fun startTimer(seconds: Long) = Unit
        override fun cancelTimer() = Unit
        override fun toggleStopwatch() = true
        override fun toggleTorch() = true
        override fun openAlarm(hour: Int?, minute: Int?, inMinutes: Int?) = Unit
        override fun openApp(entry: AppEntry) = true
        override fun openDialer(number: String) = Unit
        override fun openMessages(number: String) = Unit
        override fun openUrl(url: String) = Unit
        override fun openCalendar(title: String, inMinutes: Int, durationMinutes: Int) = Unit
        override fun openWebSearch(query: String) = Unit
        override fun openSettingsPanel(panel: String) = Unit
        override fun writeNote(text: String): String? = ""
    }

    private fun tool(name: String, behaviour: suspend () -> ToolResult) =
        object : Tool(name, "test $name", effect = Effect.Local) {
            override suspend fun execute(params: Params, env: ToolEnv): ToolResult = behaviour()
        }

    private fun plan(vararg steps: Step) = Plan.Steps(steps.toList())

    private fun step(tool: Tool, vararg values: Pair<String, String> = arrayOf()) = Step(tool, Params(values.toMap()))

    @Test fun stepsRunInOrderAndReportWhatTheySaid() = runBlocking {
        val seen = mutableListOf<String>()
        val one = tool("one") { seen += "one"; ToolResult(true, "did one") }
        val two = tool("two") { seen += "two"; ToolResult(true, "did two") }
        val outcomes = Executor(env).run(plan(step(one), step(two)))
        assertEquals(listOf("one", "two"), seen)
        assertEquals(listOf("did one", "did two"), outcomes.map { it.result.say })
    }

    @Test fun aFailureStopsThePlan() = runBlocking {
        val good = tool("good") { ToolResult(true, "ok") }
        val bad = tool("bad") { ToolResult(false, "no") }
        val never = tool("never") { ToolResult(true, "should not run") }
        val outcomes = Executor(env).run(plan(step(good), step(bad), step(never)))
        assertEquals(2, outcomes.size)
        assertFalse(outcomes.last().result.ok)
    }

    @Test fun theStepAndPerToolCapsHold() = runBlocking {
        Trace.clear()
        var runs = 0
        val t = tool("t") { runs++; ToolResult(true, "ran") }
        Executor(env).run(plan(step(t), step(t), step(t), step(t), step(t)))
        assertEquals(Executor.MAX_PER_TOOL, runs)
        assertEquals(Executor.MAX_PER_TOOL, Trace.recent().size)
    }

    @Test fun undoRunsTheMostRecentUndoableStep() = runBlocking {
        var undone = false
        val t = tool("t") { ToolResult(true, "did it", undo = { undone = true; ToolResult(true, "undone") }) }
        val executor = Executor(env)
        executor.run(plan(step(t)))
        assertTrue(executor.canUndo)
        assertEquals("undone", executor.undoLast()?.say)
        assertTrue(undone)
        assertFalse(executor.canUndo)
    }

    @Test fun aDryRunDescribesWithoutExecuting() {
        var runs = 0
        val t = tool("t") { runs++; ToolResult(true, "ran") }
        val described = Executor(env).describe(plan(step(t, "a" to "b")))
        assertEquals(listOf("t (a=b)"), described)
        assertEquals(0, runs)
    }
}
