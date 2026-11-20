package com.jake.duolauncher.agent

import com.jake.duolauncher.AppEntry
import com.jake.duolauncher.search.agent.*
import com.jake.duolauncher.search.agent.tools.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun timerPlan() = Plan.Steps(listOf(Step(TimerTool, Params(mapOf("length" to "20 minutes")))))

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
        s.run("timer for 20 minutes")
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
        // A timer request, so the timer plan is what was asked for and runs without a question.
        s.run("timer for 20 minutes for Sam", facts = "Today: Friday", grounding = Grounding(listOf(KnownPerson("Sam Patel", "+15550100")), listOf("Messages")))
        s.awaitTurn()
        assertEquals("timer for 20 minutes for Sam", seen!!.query)
        assertEquals("Today: Friday", seen!!.context)
        assertTrue(seen!!.grounding.contains("Sam Patel (+15550100)"))
    }

    // ---- the assistant log: every turn, whole ----

    private class Logged { val entries = ArrayList<com.jake.duolauncher.search.LogEntry>(); val chat = ArrayList<Long>(); val undone = ArrayList<Long>() }

    private fun loggedSession(planner: Planner, logged: Logged, toolEnv: ToolEnv = env): AgentSession {
        var next = 1000L
        return AgentSession(planner = { planner }, env = { toolEnv }, tools = ToolRegistry.builtIn + sendTool,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            record = { _, _, _, at -> logged.chat += at }, log = { logged.entries += it }, logUndone = { logged.undone += it }, newId = { next++ })
    }

    private fun rawPlanner(reply: String, plan: Plan): Planner = object : Planner {
        override val lastReply: String get() = reply
        override suspend fun plan(request: PlanRequest) = ParseResult.Ok(plan)
    }

    @Test fun aTaskIsLoggedWithWhatThePlannerWroteAndWhatEachToolDid() = runBlocking {
        val logged = Logged()
        val raw = "{\"steps\":[{\"tool\":\"timer\",\"params\":{\"length\":\"20 minutes\"}}]}"
        val s = loggedSession(rawPlanner(raw, timerPlan()), logged)
        s.run("timer for 20 minutes", facts = "- Today: Monday")
        s.awaitTurn()
        val e = logged.entries.single()
        assertEquals("plan", e.route); assertEquals("done", e.outcome); assertEquals("timer for 20 minutes", e.said)
        assertEquals("- Today: Monday", e.context); assertEquals(listOf(raw), e.raw); assertEquals("Timer set for 20 minutes", e.reply)
        assertEquals(Catalogue.hash(ToolRegistry.available(env.capabilities(), ToolRegistry.builtIn + sendTool)), e.catalogue)
        val tool = e.tools.single()
        assertEquals("timer", tool.tool); assertEquals("20 minutes", tool.params["length"]); assertEquals(true, tool.ok); assertEquals("Timer set for 20 minutes", tool.say)
        // Its turn in the conversation has the same id, and so does the card that can be rated.
        assertEquals(listOf(e.id), logged.chat); assertEquals(e.id, s.logId)
    }

    @Test fun aPlanTurnedDownAtTheConfirmationIsLoggedAsCancelledWithItsStepsNotRun() = runBlocking {
        val logged = Logged()
        val s = loggedSession(plannerFor(Plan.Steps(listOf(Step(sendTool, Params(mapOf("text" to "on my way")))))), logged)
        s.run("text Sam on my way")
        s.confirm(false)
        s.awaitTurn()
        val e = logged.entries.single()
        assertEquals("the conversation calls it done; the log says what happened", "cancelled", e.outcome)
        assertEquals("send_message", e.tools.single().tool); assertNull("planned, never run", e.tools.single().ok)
    }

    @Test fun aQuestionBackARefusalAndAFailureAreAllLogged() = runBlocking {
        val logged = Logged()
        loggedSession(plannerFor(Plan.Clarify("Which Sam?")), logged).apply { run("call Sam"); awaitTurn() }
        loggedSession(plannerFor(Plan.Refuse("I can't order food.")), logged).apply { run("order a pizza"); awaitTurn() }
        val failing = object : Planner { override suspend fun plan(request: PlanRequest) = ParseResult.Failed("I couldn't reach Uno Lab.") }
        val s = loggedSession(failing, logged).apply { run("timer for 5 minutes"); awaitTurn() }
        assertEquals(listOf("asked", "refused", "failed"), logged.entries.map { it.outcome })
        assertEquals(listOf("Which Sam?", "I can't order food.", "I couldn't reach Uno Lab."), logged.entries.map { it.reply })
        // A failure has no turn in the conversation, and can still be rated.
        assertEquals(2, logged.chat.size); assertEquals(logged.entries.last().id, s.logId)
    }

    @Test fun aPlanOnlyRunIsLoggedAsPlannedAndAFollowUpByItsRoute() = runBlocking {
        val logged = Logged()
        val s = loggedSession(plannerFor(timerPlan()), logged)
        s.run("timer for 20 minutes", planOnly = true); s.awaitTurn()
        assertEquals("planned", logged.entries.single().outcome); assertNull(logged.entries.single().tools.single().ok)
        s.run("timer for 20 minutes"); s.awaitTurn()
        assertTrue(s.followUp("again")); s.awaitTurn()
        assertEquals(listOf("plan", "plan", "again"), logged.entries.map { it.route })
        assertTrue("a follow-up that needs no planner has nothing the model wrote", logged.entries.last().raw.isEmpty())
        assertEquals("done", logged.entries.last().outcome)
    }

    @Test fun usingUndoIsWrittenAgainstTheTurnItTookBack() = runBlocking {
        val logged = Logged()
        val s = loggedSession(plannerFor(timerPlan()), logged, UndoEnv(env))
        s.run("timer for 20 minutes"); s.awaitTurn()
        s.undo()
        assertEquals(listOf(logged.entries.single().id), logged.undone)
    }

    // ---- a turn ends, and its undo shows ----

    private class UndoEnv(val base: ToolEnv, var cancels: Int = 0, var fail: Boolean = false) : ToolEnv by base {
        override fun cancelTimer() { if (fail) error("no timer"); cancels++ }
    }

    private fun sessionOn(env: ToolEnv, planner: Planner, published: MutableList<AgentState>? = null, open: () -> Boolean = { true },
        expiryMs: Long = 60_000): AgentSession {
        lateinit var s: AgentSession
        s = AgentSession(planner = { planner }, env = { env }, scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            publish = { published?.add(s.state) }, searchOpen = open, expiryMs = expiryMs)
        return s
    }

    @Test fun undoChangesWhatThePanelAndTheIslandShow() = runBlocking {
        val undoEnv = UndoEnv(env)
        val published = ArrayList<AgentState>()
        val s = sessionOn(undoEnv, plannerFor(timerPlan()), published)
        s.run("timer for 20 minutes"); s.awaitTurn()
        published.clear()
        val result = s.undo()
        assertTrue(result!!.ok)
        assertEquals(1, undoEnv.cancels)
        // It used to change only the summary, which nothing draws: the rows stayed exactly as they were.
        assertEquals(AgentState.Done("Timer cancelled", false), s.state)
        assertFalse(s.canUndo)
        assertEquals(listOf<AgentState>(AgentState.Done("Timer cancelled", false)), published)
    }

    @Test fun anUndoThatFailsSaysSoAndCanBeTriedAgain() = runBlocking {
        val undoEnv = UndoEnv(env, fail = true)
        val s = sessionOn(undoEnv, plannerFor(timerPlan()))
        s.run("timer for 20 minutes"); s.awaitTurn()
        assertFalse(s.undo()!!.ok)
        assertEquals(AgentState.Done("I couldn't undo that.", true), s.state)
        undoEnv.fail = false
        assertTrue(s.undo()!!.ok)
        assertEquals(AgentState.Done("Timer cancelled", false), s.state)
    }

    @Test fun aFinishedTurnIsClearedOnceItHasBeenSeenAndARunningOneIsLeft() = runBlocking {
        val s = sessionOn(env, plannerFor(timerPlan()))
        s.run("timer for 20 minutes"); s.awaitTurn()
        assertEquals("timer for 20 minutes", s.asked)
        s.seen()
        assertEquals(AgentState.Idle, s.state)
        assertNull(s.asked)
        // Waiting for a yes is not over: closing search leaves it for the island.
        val waiting = session(plannerFor(Plan.Steps(listOf(Step(sendTool, Params(emptyMap()))))))
        waiting.run("text Sam")
        waiting.seen()
        assertTrue(waiting.state is AgentState.Confirming)
        waiting.confirm(false)
    }

    @Test fun typingAnotherQuestionLeavesTheTurnAndTheSameQuestionDoesNot() = runBlocking {
        val s = sessionOn(env, plannerFor(timerPlan()))
        s.run("timer for 20 minutes"); s.awaitTurn()
        s.leftFor("timer for 20 minutes ")
        assertTrue(s.state is AgentState.Done)
        s.leftFor("what is the weather")
        assertEquals(AgentState.Idle, s.state)
    }

    @Test fun aResultNobodyIsLookingAtLeavesTheIslandByItselfAndOneBeingReadStays() = runBlocking {
        val closed = sessionOn(env, plannerFor(timerPlan()), open = { false }, expiryMs = 30)
        closed.run("timer for 20 minutes"); closed.awaitTurn()
        assertTrue(closed.state is AgentState.Done)
        kotlinx.coroutines.delay(200)
        assertEquals(AgentState.Idle, closed.state)
        val open = sessionOn(env, plannerFor(timerPlan()), open = { true }, expiryMs = 30)
        open.run("timer for 20 minutes"); open.awaitTurn()
        kotlinx.coroutines.delay(200)
        assertTrue(open.state is AgentState.Done)
    }
}
