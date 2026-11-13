package com.jake.duolauncher.agent

import com.jake.duolauncher.AppEntry
import com.jake.duolauncher.search.agent.*
import com.jake.duolauncher.search.agent.tools.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSessionTest {
    private val env = object : ToolEnv {
        override val noteSlot: Int? = 1
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

    private val sendTool = object : Tool("send_message", "sends", effect = Effect.LeavesPhone) {
        override suspend fun execute(params: Params, env: ToolEnv) = ToolResult(true, "Message sent")
    }

    private fun session(planner: Planner) = AgentSession(
        planner = { planner },
        env = { env },
        tools = ToolRegistry.builtIn + sendTool,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )

    private fun timerPlan() = Plan.Steps(listOf(Step(TimerTool, Params(mapOf("seconds" to "1200")))))

    @Test fun aLocalPlanRunsAndEndsWithItsSummary() = runBlocking {
        val s = session(plannerFor(timerPlan()))
        s.run("timer for 20 minutes")
        s.awaitTurn()
        assertEquals("Timer set for 20 minutes", s.summary)
        assertTrue(s.state is AgentState.Done)
        assertTrue(s.canUndo)
    }

    @Test fun aLeavingThePhonePlanWaitsForThePerson() = runBlocking {
        val s = session(plannerFor(Plan.Steps(listOf(Step(sendTool, Params(emptyMap()))))))
        s.run("text Sam")
        // Parks in Confirming until answered; the UI shows the exact steps.
        assertEquals(1, (s.state as AgentState.Confirming).steps.size)
        assertTrue(s.summary == null)
        s.confirm(false)
        s.awaitTurn()
        assertEquals("Nothing was done.", s.summary)
        assertFalse(s.canUndo)
    }

    @Test fun anAcceptedConfirmationRunsIt() = runBlocking {
        val s = session(plannerFor(Plan.Steps(listOf(Step(sendTool, Params(emptyMap()))))))
        s.run("text Sam")
        s.confirm(true)
        s.awaitTurn()
        assertEquals("Message sent", s.summary)
    }

    @Test fun aQuestionAndARefusalAreSummariesToo() = runBlocking {
        val asking = session(plannerFor(Plan.Clarify("Which Sam?")))
        asking.run("text Sam")
        asking.awaitTurn()
        assertEquals("Which Sam?", asking.summary)

        val refusing = session(plannerFor(Plan.Refuse("I'd need calendar access.")))
        refusing.run("what's tomorrow")
        refusing.awaitTurn()
        assertEquals("I'd need calendar access.", refusing.summary)
    }

    @Test fun aFailureToPlanIsReportedAsAFailure() = runBlocking {
        val failing = object : Planner {
            override suspend fun plan(request: PlanRequest) = ParseResult.Failed("The planner did not answer with JSON.")
        }
        val s = session(failing)
        s.run("hello")
        s.awaitTurn()
        assertEquals("The planner did not answer with JSON.", s.summary)
        assertTrue(s.state is AgentState.Failed)
    }

    @Test fun undoReachesTheTurn() = runBlocking {
        val s = session(plannerFor(timerPlan()))
        s.run("timer")
        s.awaitTurn()
        assertEquals("Timer cancelled", s.undo()?.say)
        assertFalse(s.canUndo)
    }

    @Test fun groundingIsFoldedIntoTheRequestItPlansWith() = runBlocking {
        var seen: PlanRequest? = null
        val planner = object : Planner {
            override suspend fun plan(request: PlanRequest): ParseResult {
                seen = request
                return ParseResult.Ok(timerPlan())
            }
        }
        val s = session(planner)
        s.run("text Sam", facts = "Today: Friday", grounding = Grounding(listOf(KnownPerson("Sam Patel", "+15550100")), listOf("Messages")))
        s.awaitTurn()
        assertEquals("text Sam", seen!!.query)
        assertEquals("Today: Friday", seen!!.context)
        assertTrue(seen!!.grounding.contains("Sam Patel (+15550100)"))
    }
}
