package com.jake.duolauncher.agent

import com.jake.duolauncher.AppEntry
import com.jake.duolauncher.search.agent.*
import com.jake.duolauncher.search.agent.tools.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLoopTest {
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

    private fun plannerFor(plan: Plan): Planner = object : Planner {
        override suspend fun plan(request: PlanRequest) = ParseResult.Ok(plan)
    }

    private fun timerPlan() = Plan.Steps(listOf(Step(TimerTool, Params(mapOf("seconds" to "1200")))))

    private val sendTool = object : Tool("send_message", "sends a message", effect = Effect.LeavesPhone) {
        override suspend fun execute(params: Params, env: ToolEnv) = ToolResult(true, "Message sent to Sam")
    }

    private fun loop(planner: Planner, confirmed: Boolean = true, asked: MutableList<List<String>> = mutableListOf(),
                     states: MutableList<AgentState> = mutableListOf()) =
        AgentLoop(planner, Executor(env), tools = ToolRegistry.builtIn + sendTool, confirm = { steps ->
            asked += steps
            confirmed
        }, onState = { states += it })

    @Test fun aLocalPlanRunsWithoutATapAndSaysWhatItDid() = runBlocking {
        val asked = mutableListOf<List<String>>()
        val outcome = loop(plannerFor(timerPlan()), asked = asked).run("timer for 20 minutes")
        val done = outcome as AgentOutcome.Done
        assertEquals("Timer set for 20 minutes", done.summary)
        assertTrue(asked.isEmpty())
        assertTrue(done.canUndo)
    }

    @Test fun aPlanOnlyTurnSaysWhatItWouldDoAndRunsNothing() = runBlocking {
        var started = 0
        val counting = object : ToolEnv by env { override fun startTimer(seconds: Long) { started++ } }
        val loop = AgentLoop(plannerFor(timerPlan()), Executor(counting))
        val outcome = loop.run("timer for 20 minutes", planOnly = true) as AgentOutcome.Done
        assertEquals(0, started)
        assertTrue(outcome.summary.startsWith("Would: "))
        assertFalse(outcome.canUndo)
        assertTrue(outcome.outcomes.isEmpty())
    }

    @Test fun anAddressOrNumberThePersonDidNotGiveIsAskedForNotActedOn() = runBlocking {
        var opened = 0; var dialled = 0
        val counting = object : ToolEnv by env {
            override fun openUrl(url: String) { opened++ }
            override fun openDialer(number: String) { dialled++ }
        }
        fun url(u: String) = Plan.Steps(listOf(Step(OpenUrlTool, Params(mapOf("url" to u)))))
        fun dial(n: String) = Plan.Steps(listOf(Step(DialTool, Params(mapOf("number" to n)))))
        // Seen on the phone: "order me a pizza" planned as opening a pizza site the model made up.
        assertEquals(AgentOutcome.Asks("Which web address should I open?"),
            AgentLoop(plannerFor(url("https://www.pizzerias.com/")), Executor(counting)).run("order me a pizza"))
        assertEquals(AgentOutcome.Asks("What number should I call?"),
            AgentLoop(plannerFor(dial("5550199")), Executor(counting)).run("call my dentist"))
        assertEquals(0, opened); assertEquals(0, dialled)
        // What the person gave, or what Uno resolved for a name they gave, is used.
        assertTrue(AgentLoop(plannerFor(url("https://www.example.com/news")), Executor(counting)).run("open example.com") is AgentOutcome.Done)
        assertTrue(AgentLoop(plannerFor(dial("+1 555 0100")), Executor(counting)).run("dial +1 555 0100") is AgentOutcome.Done)
        assertTrue(AgentLoop(plannerFor(dial("5550142")), Executor(counting)).run("call Sam", grounding = "People: Sam (555 0142)") is AgentOutcome.Done)
        assertEquals(1, opened); assertEquals(2, dialled)
    }

    @Test fun aMultiStepLocalPlanReadsAsOneSentence() = runBlocking {
        val plan = Plan.Steps(listOf(
            Step(TimerTool, Params(mapOf("seconds" to "1200"))),
            Step(FocusTool, Params(mapOf("on" to "true"))),
        ))
        val done = loop(plannerFor(plan)).run("timer and focus") as AgentOutcome.Done
        assertEquals("Timer set for 20 minutes · Focus on", done.summary)
    }

    @Test fun whatLeavesThePhoneAsksFirstAndCanBeDeclined() = runBlocking {
        val asked = mutableListOf<List<String>>()
        val plan = Plan.Steps(listOf(Step(sendTool, Params(emptyMap()))))
        val states = mutableListOf<AgentState>()
        val outcome = loop(plannerFor(plan), confirmed = false, asked = asked, states = states).run("text Sam")
        assertTrue(outcome is AgentOutcome.Cancelled)
        assertEquals(1, asked.size)
        assertTrue(asked.first().isNotEmpty())
        // Nothing ran, and the person is told so rather than left guessing.
        assertEquals("Nothing was done.", (states.last() as AgentState.Done).summary)
    }

    @Test fun anAcceptedConfirmationRunsTheStep() = runBlocking {
        val plan = Plan.Steps(listOf(Step(sendTool, Params(emptyMap()))))
        val done = loop(plannerFor(plan), confirmed = true).run("text Sam") as AgentOutcome.Done
        assertEquals("Message sent to Sam", done.summary)
    }

    @Test fun aQuestionIsNotAnAction() = runBlocking {
        val outcome = loop(plannerFor(Plan.Clarify("Which Sam?"))).run("text Sam")
        assertEquals("Which Sam?", (outcome as AgentOutcome.Asks).question)
    }

    @Test fun aRefusalSaysWhyNothingHappened() = runBlocking {
        val outcome = loop(plannerFor(Plan.Refuse("I'd need calendar access."))).run("what's tomorrow")
        assertEquals("I'd need calendar access.", (outcome as AgentOutcome.Refused).reason)
    }

    @Test fun aFailedPlanIsReportedNotIgnored() = runBlocking {
        val failing = object : Planner {
            override suspend fun plan(request: PlanRequest) = ParseResult.Failed("The planner did not answer with JSON.")
        }
        val outcome = loop(failing).run("hello")
        assertTrue(outcome is AgentOutcome.Failed)
    }

    @Test fun aStepThatFailsIsSaidOutLoudAndStopsTheRest() = runBlocking {
        val plan = Plan.Steps(listOf(Step(TimerTool, Params(mapOf("seconds" to "60"))), Step(OpenAppTool, Params(mapOf("app" to "Nope")))))
        val done = loop(plannerFor(plan)).run("timer then open nope") as AgentOutcome.Done
        assertTrue(done.summary.contains("Timer set for 1 minute"))
        assertTrue(done.summary.contains("but"))
        assertEquals(2, done.outcomes.size)
        assertFalse(done.outcomes.last().result.ok)
    }

    @Test fun theStateStreamSaysWhatIsHappening() = runBlocking {
        val states = mutableListOf<AgentState>()
        loop(plannerFor(timerPlan()), states = states).run("timer")
        assertTrue(states.first() is AgentState.Planning)
        assertTrue(states.any { it is AgentState.Acting })
        assertEquals("Timer set for 20 minutes", (states.last() as AgentState.Done).summary)
    }

    @Test fun undoUndoesTheLastStep() = runBlocking {
        val undone = mutableListOf<String>()
        val t = object : Tool("t", "test", effect = Effect.Local) {
            override suspend fun execute(params: Params, env: ToolEnv) =
                ToolResult(true, "did it", undo = { undone += "undone"; ToolResult(true, "undone") })
        }
        val loop = AgentLoop(plannerFor(Plan.Steps(listOf(Step(t, Params(emptyMap()))))), Executor(env), tools = listOf(t))
        loop.run("do it")
        assertEquals("undone", loop.undo()?.say)
        assertEquals(listOf("undone"), undone)
    }

    @Test fun aPlanThatIsNotWhatWasAskedForIsShownAndAskedAboutNotRun() = runBlocking {
        var started = 0
        val counting = object : ToolEnv by env { override fun startTimer(seconds: Long) { started++ } }
        val asked = ArrayList<List<String>>()
        // The model's own mistake, as seen on a phone: an alarm request planned as a timer.
        val wrong = Plan.Steps(listOf(Step(TimerTool, Params(mapOf("seconds" to "3600", "label" to "6am tomorrow")))))
        val declined = AgentLoop(plannerFor(wrong), Executor(counting), confirm = { asked += it; false }).run("set an alarm for 6am tomorrow")
        assertEquals(AgentOutcome.Cancelled, declined)
        assertEquals(listOf(listOf("Start a timer for 1 hour")), asked)
        assertEquals(0, started)
        // Said yes to, it runs; and a label that only repeats a time is not shown as the timer's name.
        val done = AgentLoop(plannerFor(wrong), Executor(counting), confirm = { true }).run("set an alarm for 6am tomorrow") as AgentOutcome.Done
        assertEquals("Timer set for 1 hour", done.summary)
        assertEquals(1, started)
        // The same plan for a request that is a timer runs with no question.
        asked.clear()
        assertTrue(AgentLoop(plannerFor(wrong), Executor(counting), confirm = { asked += it; false }).run("timer for an hour") is AgentOutcome.Done)
        assertTrue(asked.isEmpty())
    }
}
