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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowUpTest {
    private fun step(tool: Tool, vararg values: Pair<String, String>) = Step(tool, Params(values.toMap()))
    private val timer = listOf(step(TimerTool, "length" to "10 minutes", "label" to ""))
    private val alarm = listOf(step(AlarmTool, "when" to "7am"))
    private val event = listOf(step(CalendarTool, "title" to "Dentist", "start" to "tomorrow at 3pm", "length" to "an hour"))
    private val torch = listOf(step(TorchTool, "on" to "true"))

    private fun amended(query: String, last: List<Step>) = (FollowUp.read(query, last) as? FollowUp.Kind.Amend)?.plan?.steps?.single()?.params?.values

    @Test fun takingItBackAndDoingItAgainNeedNoPlanner() {
        for (said in listOf("undo that", "undo", "cancel that", "never mind", "nevermind", "take that back", "Undo that please")) assertEquals(said, FollowUp.Kind.Undo, FollowUp.read(said, timer))
        for (said in listOf("again", "do that again", "same again", "once more", "ok do it again")) assertEquals(said, FollowUp.Kind.Again, FollowUp.read(said, timer))
    }

    @Test fun aNewValueChangesTheLastTask() {
        assertEquals("15 minutes", amended("make it 15", timer)?.get("length"))
        assertEquals("15 minutes", amended("actually 15 minutes", timer)?.get("length"))
        assertEquals("20 minutes", amended("20 minutes", timer)?.get("length"))
        assertEquals("an hour", amended("no, make it an hour", timer)?.get("length"))
        // A bare number takes the unit the last value had.
        assertEquals("45 seconds", amended("make it 45", listOf(step(TimerTool, "length" to "30 seconds")))?.get("length"))
        assertEquals("6:30", amended("make it 6:30", alarm)?.get("when"))
        assertEquals("in 45 minutes", amended("actually in 45 minutes", alarm)?.get("when"))
        val moved = amended("move it to friday at 10am", event)!!
        assertEquals("friday at 10am", moved["start"]); assertEquals("Dentist", moved["title"]); assertEquals("an hour", moved["length"])
        assertEquals("2 hours", amended("make it 2 hours", event)?.get("length"))
        assertEquals("down", amended("down", listOf(step(VolumeTool, "level" to "up")))?.get("level"))
    }

    @Test fun wordsThatLeanOnTheLastTaskGoToThePlannerWithIt() {
        for (said in listOf("and also turn on focus", "then open maps", "also remind me to call Sam", "do the same for the eggs", "turn it off"))
            assertEquals(said, FollowUp.Kind.Refers, FollowUp.read(said, timer))
        val block = FollowUp.block(LastTurn("set a timer for 10 minutes", timer, "Timer set for 10 minutes", 0, Executor(env)))
        assertEquals("Just done, for \"set a timer for 10 minutes\": timer(length=10 minutes)", block)
    }

    @Test fun aNewRequestIsNotAFollowUp() {
        for (said in listOf("set an alarm for 7", "open maps", "why is the sky blue", "what time is it", "directions to the airport", ""))
            assertEquals(said, FollowUp.Kind.None, FollowUp.read(said, timer))
        // A length is not a change to a torch, and nothing follows on from nothing.
        assertEquals(FollowUp.Kind.None, FollowUp.read("20 minutes", torch))
        assertEquals(FollowUp.Kind.None, FollowUp.read("undo that", emptyList()))
        // A new task of another kind is not an amendment, even with a value in it.
        assertNull(amended("set an alarm for 6:30", timer))
    }

    // ---------------------------------------------------------------- through the session

    private val did = ArrayList<String>()
    private val env = object : ToolEnv {
        override val noteSlot: Int? = 1
        override val apps: List<AppEntry> = emptyList()
        override fun focusOn() = false
        override fun toggleFocus() { did += "focus" }
        override fun startTimer(seconds: Long) { did += "timer $seconds" }
        override fun cancelTimer() { did += "cancel" }
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
    private var now = 1_000L
    private val seen = ArrayList<PlanRequest>()
    private fun session(vararg plans: Plan): AgentSession {
        var i = 0
        val planner = object : Planner {
            override suspend fun plan(request: PlanRequest): ParseResult { seen += request; return ParseResult.Ok(plans[minOf(i++, plans.size - 1)]) }
        }
        return AgentSession(planner = { planner }, env = { env }, scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), clock = { now })
    }

    @Test fun theLastTaskIsChangedUndoneAndRepeatedWithoutPlanningAgain() = runBlocking {
        val s = session(Plan.Steps(timer))
        s.run("set a timer for 10 minutes"); s.awaitTurn()
        assertEquals("Timer set for 10 minutes", s.summary)
        // The card going (search closed, or another word typed) does not lose the task.
        s.seen()
        assertTrue(s.follows("make it 15"))
        assertTrue(s.followUp("make it 15")); s.awaitTurn()
        assertEquals("Timer set for 15 minutes", s.summary)
        assertTrue(s.followUp("undo that")); s.awaitTurn()
        assertEquals("Timer cancelled", s.summary)
        assertTrue(s.followUp("again")); s.awaitTurn()
        assertEquals("Timer set for 15 minutes", s.summary)
        assertEquals(listOf("timer 600", "timer 900", "cancel", "timer 900"), did)
        assertEquals(1, seen.size)
    }

    @Test fun wordsThatLeanOnItArePlannedWithItBesideThem() = runBlocking {
        val s = session(Plan.Steps(timer), Plan.Steps(listOf(step(FocusTool, "on" to "true"))))
        s.run("set a timer for 10 minutes"); s.awaitTurn()
        assertTrue(s.followUp("and also turn on focus")); s.awaitTurn()
        assertEquals("and also turn on focus", seen[1].query)
        assertTrue(seen[1].grounding, seen[1].grounding.endsWith("Just done, for \"set a timer for 10 minutes\": timer(length=10 minutes)"))
        assertEquals("Focus on", s.summary)
    }

    @Test fun afterAFewMinutesThereIsNothingToFollowUp() = runBlocking {
        val s = session(Plan.Steps(timer))
        s.run("set a timer for 10 minutes"); s.awaitTurn()
        now += FollowUp.WINDOW_MS + 1
        assertFalse(s.follows("undo that"))
        assertFalse(s.followUp("undo that"))
    }

    @Test fun anUnansweredQuestionWaitsWhileSearchIsClosed() = runBlocking {
        val s = session(Plan.Clarify("How long should the timer be?"), Plan.Steps(timer))
        s.run("set a timer"); s.awaitTurn()
        s.seen()
        assertTrue(s.state is AgentState.Asking)
        s.answer("10 minutes"); s.awaitTurn()
        assertEquals("Timer set for 10 minutes", s.summary)
    }
}
