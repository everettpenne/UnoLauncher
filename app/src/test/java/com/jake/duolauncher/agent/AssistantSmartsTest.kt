package com.jake.duolauncher.agent

import com.jake.duolauncher.AppEntry
import com.jake.duolauncher.search.AnswerText
import com.jake.duolauncher.search.agent.*
import com.jake.duolauncher.search.agent.tools.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What makes the assistant smarter than its planner: answered questions, repairs, retries, search offers, and tools that know the phone's state. */
class AssistantSmartsTest {
    private class Phone : ToolEnv {
        val did = ArrayList<String>()
        var lit = false
        var running = false
        var started = false
        var timer = false
        var playing = true
        var level = 40
        val list = ArrayList<String>()
        var waiting: List<ReplyTarget> = emptyList()
        override val noteSlot: Int? = 1
        override var checklistSlot: Int? = 2
        override val apps: List<AppEntry> = emptyList()
        override fun focusOn() = false
        override fun toggleFocus() = Unit
        override fun startTimer(seconds: Long) { timer = true; did += "timer $seconds" }
        override fun cancelTimer() { timer = false; did += "cancel" }
        override fun timerRunning() = timer
        override fun toggleStopwatch(): Boolean { running = !running; started = true; return running }
        override fun stopwatchRunning() = running
        override fun stopwatchStarted() = started
        override fun resetStopwatch() { running = false; started = false }
        override fun toggleTorch(): Boolean { lit = !lit; return lit }
        override fun setTorch(on: Boolean) { lit = on }
        override fun openAlarm(hour: Int?, minute: Int?, inMinutes: Int?) { did += "alarm $hour:$minute +$inMinutes" }
        override fun openApp(entry: AppEntry) = true
        override fun openDialer(number: String) { did += "dial $number" }
        override fun openMessages(number: String) { did += "sms $number" }
        override fun openMessage(number: String, text: String) { did += "sms $number: $text" }
        override fun openUrl(url: String) = Unit
        override fun openCalendar(title: String, inMinutes: Int, durationMinutes: Int) { did += "event $title +$inMinutes for $durationMinutes" }
        override fun openWebSearch(query: String) { did += "search $query" }
        override fun openSettingsPanel(panel: String) = Unit
        override fun writeNote(text: String): String? = ""
        override fun now(): java.time.LocalDateTime = java.time.LocalDateTime.of(2026, 11, 16, 9, 0)
        override fun media(action: String): Boolean { if (playing) did += "media $action"; return playing }
        override fun volume(): Int? = level
        override fun setVolume(percent: Int): Boolean { level = percent; return true }
        override fun addChecklist(item: String): Boolean = if (item in list) false else list.add(item)
        override fun removeChecklist(item: String) { list.remove(item) }
        override fun openDirections(place: String) { did += "maps $place" }
        override fun capabilities() = setOf(Capability.Notifications)
        override fun replyTargets() = waiting
        override fun sendReply(key: String, text: String): Boolean { did += "reply $key: $text"; return true }
    }

    private fun step(tool: Tool, vararg values: Pair<String, String>) = Step(tool, Params(values.toMap()))
    private fun plan(vararg steps: Step): Plan = Plan.Steps(steps.toList())
    private fun planner(vararg replies: ParseResult, seen: MutableList<String> = ArrayList()) = object : Planner {
        var i = 0
        override suspend fun plan(request: PlanRequest): ParseResult { seen += request.query; return replies[minOf(i++, replies.size - 1)] }
    }
    private fun ok(plan: Plan) = ParseResult.Ok(plan)
    private fun loop(phone: Phone, planner: Planner, yes: Boolean = true, shown: MutableList<List<String>> = ArrayList(),
                     states: MutableList<AgentState> = ArrayList()) =
        AgentLoop(planner, Executor(phone), confirm = { shown += it; yes }, onState = { states += it })
    private fun run(tool: Tool, phone: Phone, vararg values: Pair<String, String>) = runBlocking { tool.execute(Params(values.toMap()), phone) }

    // ---------------------------------------------------------------- times and lengths

    @Test fun theLauncherDoesTheArithmetic() = runBlocking {
        val phone = Phone()
        val done = loop(phone, planner(ok(plan(step(TimerTool, "length" to "an hour and a half"))))).run("set a timer for an hour and a half")
        assertEquals("Timer set for 1 hour 30 minutes", (done as AgentOutcome.Done).summary)
        assertEquals(listOf("timer 5400"), phone.did)
    }

    @Test fun anEventStartsWhenThePersonSaid() = runBlocking {
        val phone = Phone()
        val done = loop(phone, planner(ok(plan(step(CalendarTool, "title" to "Dentist", "start" to "tomorrow at 3pm", "length" to "an hour")))))
            .run("dentist tomorrow at 3pm for an hour") as AgentOutcome.Done
        // Monday 09:00 to Tuesday 15:00.
        assertEquals(listOf("event Dentist +1800 for 60"), phone.did)
        assertTrue(done.summary, done.summary.contains("tomorrow at 15:00 for 1 hour"))
        // With no length said, it is an hour.
        phone.did.clear()
        loop(phone, planner(ok(plan(step(CalendarTool, "title" to "Gym", "start" to "in 2 hours"))))).run("gym in 2 hours")
        assertEquals(listOf("event Gym +120 for 60"), phone.did)
    }

    @Test fun aTimeThePlannerMadeUpIsAskedForAndTheAnswerFillsIt() = runBlocking {
        val phone = Phone()
        val seen = ArrayList<String>()
        val agent = loop(phone, planner(ok(plan(step(TimerTool, "length" to "1 hour", "label" to "cake"))), seen = seen))
        val asks = agent.run("set a timer for the cake") as AgentOutcome.Asks
        assertEquals("How long should the timer be?", asks.question)
        assertTrue(phone.did.isEmpty())
        // The answer goes into the plan that was waiting: nothing is planned again.
        val done = agent.resume(asks.fix!!, "set a timer for the cake", "25 minutes") as AgentOutcome.Done
        assertEquals("Timer set for 25 minutes (cake)", done.summary)
        assertEquals(1, seen.size)
        // An answer that is not a length ends the turn with the question, not a second fix.
        val again = agent.resume(asks.fix!!, "set a timer for the cake", "a while") as AgentOutcome.Asks
        assertNull(again.fix)
    }

    @Test fun aTimeThePlannerRewroteIsPutBackFromThePersonsWords() = runBlocking {
        val phone = Phone()
        // Seen on the phone: "tomorrow at 3pm" came back as "03:00" and "an hour" as "60".
        val wrong = plan(step(CalendarTool, "title" to "Dentist", "start" to "03:00", "length" to "60"))
        loop(phone, planner(ok(wrong))).run("dentist tomorrow at 3pm for an hour")
        assertEquals(listOf("event Dentist +1800 for 60"), phone.did)
        // A time that keeps the hour and drops the day is not the one they gave.
        assertNotNull(CalendarTool.missing(Params(mapOf("title" to "Dentist", "start" to "3pm")), "dentist tomorrow at 3pm"))
        // A timer and an alarm are mended the same way.
        phone.did.clear()
        loop(phone, planner(ok(plan(step(TimerTool, "length" to "5400"))))).run("set a timer for an hour and a half")
        loop(phone, planner(ok(plan(step(AlarmTool, "when" to "7:00"))))).run("wake me at 7 tonight")
        assertEquals(listOf("timer 5400", "alarm 19:0 +null"), phone.did)
        // With two lengths said, the one after "for" is how long it lasts; with none said, an invented one is dropped for the default hour.
        phone.did.clear()
        loop(phone, planner(ok(plan(step(CalendarTool, "title" to "Dentist", "start" to "in 30 minutes", "length" to "1"))))).run("dentist in 30 minutes for an hour")
        loop(phone, planner(ok(plan(step(CalendarTool, "title" to "Gym", "start" to "in 2 hours", "length" to "90 minutes"))))).run("gym in 2 hours")
        assertEquals(listOf("event Dentist +30 for 60", "event Gym +120 for 60"), phone.did)
    }

    @Test fun aDroppedTurnIsNotAPlannerFailure() = runBlocking {
        val cancelling = object : Planner { override suspend fun plan(request: PlanRequest): ParseResult = throw kotlinx.coroutines.CancellationException("dropped") }
        val states = ArrayList<AgentState>()
        val thrown = runCatching { loop(Phone(), cancelling, states = states).run("torch on") }.exceptionOrNull()
        assertTrue(thrown is kotlinx.coroutines.CancellationException)
        assertTrue(states.none { it is AgentState.Failed })
    }

    // ---------------------------------------------------------------- retry

    @Test fun aReplyThatBrokeARuleIsAskedForOnceMore() = runBlocking {
        val phone = Phone()
        val seen = ArrayList<String>()
        val agent = loop(phone, planner(ParseResult.Failed("on must be true or false", retry = true), ok(plan(step(TorchTool, "on" to "true"))), seen = seen))
        assertTrue(agent.run("torch on") is AgentOutcome.Done)
        assertEquals(2, seen.size)
        assertTrue(seen[1].contains("on must be true or false"))
        // Twice wrong is a failure, and a planner that cannot be reached is not asked again.
        val bad = ArrayList<String>()
        assertTrue(loop(phone, planner(ParseResult.Failed("no", retry = true), seen = bad)).run("torch on") is AgentOutcome.Failed)
        assertEquals(2, bad.size)
        val down = ArrayList<String>()
        assertTrue(loop(phone, planner(ParseResult.Failed("I couldn't reach Uno Lab."), seen = down)).run("torch on") is AgentOutcome.Failed)
        assertEquals(1, down.size)
    }

    @Test fun theParserSaysWhichFailuresAreWorthAnotherTry() {
        val tools = ToolRegistry.builtIn
        assertTrue((PlanParser.parse("""{"steps":[{"tool":"teleport","params":{}}]}""", tools) as ParseResult.Failed).retry)
        assertTrue((PlanParser.parse("""{"steps":[{"tool":"focus","params":{"on":"maybe"}}]}""", tools) as ParseResult.Failed).retry)
        assertFalse((PlanParser.parse("I would set a timer.", tools) as ParseResult.Failed).retry)
    }

    // ---------------------------------------------------------------- replies

    @Test fun aMessageToSomeoneWhoIsWaitingBecomesAReplyAndStillAsks() = runBlocking {
        val phone = Phone().apply { waiting = listOf(ReplyTarget("k1", "Priya", "Messages", 10)) }
        val shown = ArrayList<List<String>>()
        // Seen from the model: a made-up number for someone whose message is waiting.
        val wrong = plan(step(MessageTool, "number" to "+123456789", "text" to "I'll call her tonight"))
        val done = loop(phone, planner(ok(wrong)), shown = shown).run("tell Priya I'll call her tonight") as AgentOutcome.Done
        assertEquals(listOf(listOf("Reply to Priya in Messages: “I'll call her tonight”")), shown)
        assertEquals(listOf("reply k1: I'll call her tonight"), phone.did)
        assertTrue(done.summary.startsWith("Replied to Priya"))
        // Declined, nothing is sent.
        phone.did.clear()
        assertEquals(AgentOutcome.Cancelled, loop(phone, planner(ok(wrong)), yes = false).run("tell Priya I'll call her tonight"))
        assertTrue(phone.did.isEmpty())
    }

    @Test fun withNobodyWaitingTheNumberIsAskedForByName() = runBlocking {
        val phone = Phone()
        val agent = loop(phone, planner(ok(plan(step(MessageTool, "number" to "+123456789", "text" to "running late")))))
        val asks = agent.run("tell Priya running late") as AgentOutcome.Asks
        assertEquals("I don't have a number for Priya. What is it?", asks.question)
        val done = agent.resume(asks.fix!!, "tell Priya running late", "555 0142") as AgentOutcome.Done
        assertEquals(listOf("sms 555 0142: running late"), phone.did)
        assertTrue(done.summary.contains("555 0142"))
    }

    // ---------------------------------------------------------------- web search offers

    @Test fun aSearchIsOfferedAndOnlyRunOnAYes() = runBlocking {
        val phone = Phone()
        val shown = ArrayList<List<String>>()
        val offer = ok(Plan.OfferSearch("weather in Lisbon this weekend"))
        assertEquals(AgentOutcome.Cancelled, loop(phone, planner(offer), yes = false, shown = shown).run("what's the weather in Lisbon this weekend"))
        assertEquals(listOf(listOf("Search the web for “weather in Lisbon this weekend”")), shown)
        assertTrue(phone.did.isEmpty())
        val done = loop(phone, planner(offer)).run("what's the weather in Lisbon this weekend") as AgentOutcome.Done
        assertEquals(listOf("search weather in Lisbon this weekend"), phone.did)
        assertTrue(done.summary.contains("Searching the web"))
    }

    @Test fun onlyThePersonsOwnWordsAreSearchedFor() = runBlocking {
        val phone = Phone()
        // The planner's query carries a word the person never said (it could have come from the phone's facts).
        loop(phone, planner(ok(Plan.OfferSearch("Sam Patel dentist prices")))).run("how much is a filling")
        assertEquals(listOf("search how much is a filling"), phone.did)
        assertEquals("order me a pizza", SearchOffer.forRequest("Hey, can you order me a pizza?"))
        assertEquals("set a timer", SearchOffer.forRequest("set a timer\nYou asked: How long?\nAnswer: 10 minutes"))
    }

    @Test fun aRefusalOffersASearchForTheRequest() = runBlocking {
        val states = ArrayList<AgentState>()
        loop(Phone(), planner(ok(Plan.Refuse("I cannot order food from here."))), states = states).run("please order me a pizza")
        assertEquals(AgentState.Failed("I cannot order food from here.", search = "order me a pizza"), states.last())
    }

    @Test fun anAnswerAboutTheWorldNowOffersASearch() {
        assertEquals("what's the weather tomorrow", AnswerText.searchFor("what's the weather tomorrow?", "It should be mild."))
        assertEquals("who wrote Dune", AnswerText.searchFor("who wrote Dune", "I'm not sure about that."))
        assertNull(AnswerText.searchFor("who wrote Dune", "Frank Herbert."))
    }

    @Test fun thePlannerCanSayThatASearchWouldHelp() {
        val tools = ToolRegistry.builtIn
        assertEquals(Plan.OfferSearch("train times to York"), (PlanParser.parse("""{"search":"train times to York"}""", tools) as ParseResult.Ok).plan)
        assertTrue(Catalogue.grammar(tools).contains("root ::= plan | clarify | refuse | search"))
        assertTrue(Catalogue.prompt(tools).contains("{\"search\":"))
    }

    // ---------------------------------------------------------------- answered questions (the session)

    private fun session(phone: Phone, planner: Planner) = AgentSession(planner = { planner }, env = { phone },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

    @Test fun anAnswerToAMissingValueCarriesOnWithoutPlanningAgain() = runBlocking {
        val phone = Phone()
        val seen = ArrayList<String>()
        val s = session(phone, planner(ok(plan(step(TimerTool, "length" to "10 minutes"))), seen = seen))
        s.run("set a timer"); s.awaitTurn()
        assertEquals("How long should the timer be?", (s.state as AgentState.Asking).question)
        assertNotNull(s.awaiting)
        // Typing the answer does not end the turn.
        s.leftFor("15 min")
        assertTrue(s.state is AgentState.Asking)
        s.answer("15 minutes"); s.awaitTurn()
        assertEquals("Timer set for 15 minutes", s.summary)
        assertEquals("15 minutes", s.asked)
        assertEquals(1, seen.size)
        assertNull(s.awaiting)
    }

    @Test fun anAnswerToThePlannersOwnQuestionIsPlannedWithTheQuestionBesideIt() = runBlocking {
        val phone = Phone()
        val seen = ArrayList<String>()
        val s = session(phone, planner(ok(Plan.Clarify("How long should the timer be?")), ok(plan(step(TimerTool, "length" to "10 minutes"))), seen = seen))
        s.run("set a timer"); s.awaitTurn()
        s.answer("10 minutes"); s.awaitTurn()
        assertEquals("set a timer\nYou asked: How long should the timer be?\nAnswer: 10 minutes", seen[1])
        assertEquals("Timer set for 10 minutes", s.summary)
    }

    @Test fun aTurnAsksOnlyOnce() = runBlocking {
        val s = session(Phone(), planner(ok(Plan.Clarify("How long?")), ok(Plan.Clarify("How long, though?"))))
        s.run("set a timer"); s.awaitTurn()
        s.answer("a while"); s.awaitTurn()
        assertEquals("How long, though?", s.summary)
        assertNull(s.awaiting)
        // Nothing is waiting now, so the next words are a new request and typing them leaves the old turn.
        s.leftFor("open maps")
        assertEquals(AgentState.Idle, s.state)
    }

    // ---------------------------------------------------------------- tools that know the phone's state

    @Test fun theTorchIsSetNotFlipped() {
        val phone = Phone()
        assertEquals("Torch off", run(TorchTool, phone, "on" to "false").say)
        assertFalse(phone.lit)
        assertEquals("Torch on", run(TorchTool, phone, "on" to "true").say)
        assertEquals("Torch on", run(TorchTool, phone, "on" to "true").say)
        assertTrue(phone.lit)
    }

    @Test fun theStopwatchDoesWhatWasAsked() {
        val phone = Phone()
        assertEquals("The stopwatch isn't running", run(StopwatchTool, phone, "action" to "stop").say)
        assertEquals("Stopwatch running", run(StopwatchTool, phone, "action" to "start").say)
        assertEquals("The stopwatch is already running", run(StopwatchTool, phone, "action" to "start").say)
        assertTrue(phone.running)
        assertEquals("Stopwatch stopped", run(StopwatchTool, phone, "action" to "stop").say)
        assertEquals("Stopwatch reset", run(StopwatchTool, phone, "action" to "reset").say)
        assertEquals("The stopwatch is already at zero", run(StopwatchTool, phone, "action" to "reset").say)
    }

    @Test fun aTimerIsCancelledOnlyWhenOneIsRunning() {
        val phone = Phone()
        assertEquals("No timer is running", run(TimerCancelTool, phone).say)
        phone.timer = true
        assertEquals("Timer cancelled", run(TimerCancelTool, phone).say)
        assertFalse(phone.timer)
    }

    @Test fun mediaIsControlledAndSaysWhenNothingIsPlaying() = runBlocking {
        val phone = Phone()
        val paused = run(MediaTool, phone, "action" to "pause")
        assertEquals("Paused", paused.say)
        assertEquals("Playing", paused.undo!!().say)
        assertEquals(listOf("media pause", "media play"), phone.did)
        phone.playing = false
        assertFalse(run(MediaTool, phone, "action" to "next").ok)
    }

    @Test fun volumeStepsMutesAndGoesToAPercentageThePersonGave() = runBlocking {
        val phone = Phone()
        assertEquals("Volume at 55%", run(VolumeTool, phone, "level" to "up").say)
        val muted = run(VolumeTool, phone, "level" to "mute")
        assertEquals("Muted", muted.say)
        assertEquals("Volume back at 55%", muted.undo!!().say)
        assertEquals("Volume at 30%", run(VolumeTool, phone, "level" to "30 percent").say)
        assertNull(VolumeTool.missing(Params(mapOf("level" to "30")), "volume to thirty percent"))
        assertNotNull(VolumeTool.missing(Params(mapOf("level" to "70")), "turn it up"))
        assertNotNull(VolumeTool.missing(Params(mapOf("level" to "loudish")), "make it loudish"))
    }

    @Test fun anItemGoesOnTheChecklistAndCanComeOff() = runBlocking {
        val phone = Phone()
        val added = run(ChecklistTool, phone, "item" to "milk")
        assertEquals("Added “milk” to the checklist", added.say)
        assertEquals(listOf("milk"), phone.list)
        added.undo!!()
        assertTrue(phone.list.isEmpty())
        phone.checklistSlot = null
        assertEquals("Add a Checklist widget to Home and I can keep a list there.", ChecklistTool.problem(Params(mapOf("item" to "milk")), phone))
    }

    @Test fun directionsGoToAPlaceThePersonNamed() {
        val phone = Phone()
        assertNull(DirectionsTool.missing(Params(mapOf("place" to "the train station")), "directions to the train station"))
        assertEquals("place", DirectionsTool.missing(Params(mapOf("place" to "12 Mill Lane")), "take me home\nPhone context: 12 Mill Lane")?.param)
        assertEquals("Maps open at “the airport”", run(DirectionsTool, phone, "place" to "the airport").say)
        assertEquals(listOf("maps the airport"), phone.did)
    }

    @Test fun aRequestsWordsNameTheNewKindsToo() {
        fun steps(tool: Tool) = Plan.Steps(listOf(Step(tool, Params(emptyMap()))))
        assertTrue(RequestKinds.mismatch("turn the volume down", steps(TorchTool)))
        assertFalse(RequestKinds.mismatch("turn the volume down", steps(VolumeTool)))
        assertFalse(RequestKinds.mismatch("cancel the timer", steps(TimerCancelTool)))
        assertFalse(RequestKinds.mismatch("skip this song", steps(MediaTool)))
        assertTrue(RequestKinds.mismatch("directions to the airport", steps(TimerTool)))
    }
}
