package com.jake.duolauncher.agent

import com.jake.duolauncher.agent.training.RecordingEnv
import com.jake.duolauncher.search.agent.*
import com.jake.duolauncher.search.agent.tools.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** The rules the launcher applies to a plan after the model has written it: command words taken off a value, a step swapped for the
 *  one that was meant, and a time looked up in the calendar. */
class HarnessRulesTest {
    private val tools = ToolRegistry.builtIn
    private fun loop(reply: String, env: ToolEnv) = AgentLoop(object : Planner {
        override suspend fun plan(request: PlanRequest) = PlanParser.parse(reply, tools)
    }, Executor(env), tools = tools, confirm = { true })

    // ---------------------------------------------------------------------------------------------- command words

    @Test fun aValueLosesTheRequestsOwnCommandWords() {
        assertEquals("physio", LeadIn.stripped("pencil in physio", "pencil in physio at 3", listOf("pencil in")))
        assertEquals("physio", LeadIn.stripped("Pencil in physio", "ok pencil in physio at 3", listOf("pencil in")))
        assertEquals("physio", LeadIn.stripped("pencil in physio", "could you please pencil in physio at 3", listOf("pencil in")))
        // Not when the request began another way: the words are then part of what was asked for.
        assertEquals("pencil in physio", LeadIn.stripped("pencil in physio", "add pencil in physio to my calendar", listOf("pencil in")))
        // Nothing would be left, or the value never had them.
        assertEquals("pencil in", LeadIn.stripped("pencil in", "pencil in at 3", listOf("pencil in")))
        assertEquals("physio", LeadIn.stripped("physio", "pencil in physio at 3", listOf("pencil in")))
        // Only the first line is the request; what follows is an answer to a question.
        assertEquals("dentist", LeadIn.stripped("pencil in dentist", "pencil in dentist\nAnswer: at 3", listOf("pencil in")))
    }

    @Test fun eachToolTakesOffOnlyItsOwn() {
        fun mended(tool: Tool, key: String, value: String, said: String) = tool.mended(Params(mapOf(key to value)), said).text(key)
        assertEquals("physio", mended(CalendarTool, "title", "pencil in physio", "pencil in physio in 3 hours"))
        // "book" and "schedule" are also things: "book club at 7" keeps its name.
        assertEquals("book club", mended(CalendarTool, "title", "book club", "book club tomorrow at 7"))
        assertEquals("lemons", mended(ChecklistTool, "item", "stick lemons", "stick lemons on my list"))
        assertEquals("renew the bus pass", mended(ChecklistTool, "item", "pop renew the bus pass", "ok pop renew the bus pass on the checklist"))
        assertEquals("tape", mended(ChecklistTool, "item", "pop tape", "pop tape on the shopping list"))
        // Without the list named after it, the word may be the thing itself.
        assertEquals("pop tarts", mended(ChecklistTool, "item", "pop tarts", "pop tarts"))
        assertEquals("pop tarts", mended(ChecklistTool, "item", "pop tarts", "add pop tarts to my list"))
        assertEquals("the door code is 4411", mended(NoteTool, "text", "jot down the door code is 4411", "jot down the door code is 4411"))
        assertEquals("milk and eggs", mended(NoteTool, "text", "milk and eggs", "write down milk and eggs"))
    }

    // ---------------------------------------------------------------------------------------------- one step for another

    @Test fun thePeopleInAKnownBlockAreReadBack() {
        assertEquals(listOf(KnownPerson("Sam Patel", "555 0142"), KnownPerson("Ana", "+44 20 7946 0000")),
            GroundingLogic.peopleIn("Messages waiting for a reply (newest first): Jo in Signal\nPeople: Sam Patel (555 0142); Ana (+44 20 7946 0000)\nApps: Maps"))
        assertTrue(GroundingLogic.peopleIn("Apps: Maps").isEmpty())
    }

    @Test fun aMessageToSomeoneWhoseMessageIsWaitingIsAReply() = runBlocking {
        val env = RecordingEnv(grounding = "Messages waiting for a reply (newest first): Dana in Signal; Priya in Messages")
        val outcome = loop("""{"steps":[{"tool":"message","params":{"number":"020 7946 0000","text":"I'll call you tonight"}}]}""", env)
            .run("tell Priya in Messages I'll call her tonight", grounding = "Messages waiting for a reply (newest first): Dana in Signal; Priya in Messages")
        assertTrue("$outcome", outcome is AgentOutcome.Done)
        assertEquals(listOf("reply Priya/Messages: i'll call you tonight"), env.did)
    }

    @Test fun aReplyToSomeoneWithNothingWaitingAndAKnownNumberIsAMessageNotSent() = runBlocking {
        val known = "People: Mateo (555 0144)"
        val env = RecordingEnv(grounding = known)
        val outcome = loop("""{"steps":[{"tool":"reply","params":{"to":"Mateo","text":"call you after lunch"}}]}""", env)
            .run("write back to Mateo saying call you after lunch", grounding = known)
        assertTrue("$outcome", outcome is AgentOutcome.Done)
        assertEquals(listOf("message 5550144: call you after lunch"), env.did)
    }

    @Test fun aReplyStaysAReplyWhenTheMessageIsThereOrNobodyIsKnown() = runBlocking {
        // Mateo's message is waiting: it is answered, though his number is known too.
        val both = "Messages waiting for a reply (newest first): Mateo in Signal\nPeople: Mateo (555 0144)"
        val env = RecordingEnv(grounding = both)
        loop("""{"steps":[{"tool":"reply","params":{"to":"Mateo","text":"ok"}}]}""", env).run("reply to Mateo saying ok", grounding = both)
        assertEquals(listOf("reply Mateo/Signal: ok"), env.did)
        // Nobody of that name anywhere: the person is told, and nothing is opened.
        val none = RecordingEnv()
        val asked = loop("""{"steps":[{"tool":"reply","params":{"to":"Mateo","text":"ok"}}]}""", none).run("reply to Mateo saying ok")
        assertEquals(AgentOutcome.Asks("I don't see a message from Mateo to reply to."), asked)
        assertTrue(none.did.isEmpty())
        // "that" with nothing waiting is not a name to look up.
        val that = RecordingEnv(grounding = "People: That Guy (555 0100)")
        assertTrue(loop("""{"steps":[{"tool":"reply","params":{"to":"that","text":"ok"}}]}""", that).run("reply to that saying ok", grounding = "People: That Guy (555 0100)") is AgentOutcome.Asks)
        // Two people it could be: not guessed between.
        val two = "People: Sam Patel (555 0142); Sam Lee (555 0199)"
        val twoEnv = RecordingEnv(grounding = two)
        assertTrue(loop("""{"steps":[{"tool":"reply","params":{"to":"Sam","text":"ok"}}]}""", twoEnv).run("write back to Sam saying ok", grounding = two) is AgentOutcome.Asks)
        assertTrue(twoEnv.did.isEmpty())
    }

    // ---------------------------------------------------------------------------------------------- a time from the calendar

    private val now = LocalDateTime.of(2026, 11, 27, 19, 40)
    private val events = listOf(
        CalendarEvent("Bank holiday", LocalDateTime.of(2026, 11, 28, 0, 0), allDay = true),
        CalendarEvent("Late call", LocalDateTime.of(2026, 11, 27, 21, 0)),
        CalendarEvent("Standup", LocalDateTime.of(2026, 11, 28, 9, 30)),
        CalendarEvent("Dentist", LocalDateTime.of(2026, 11, 28, 14, 0)),
        CalendarEvent("Offsite", LocalDateTime.of(2026, 11, 30, 10, 0)))

    @Test fun aTimeBeforeAnEventIsRead() {
        assertEquals(EventTime.Ref(30, EventTime.Which.FIRST, "", EventTime.Day.TOMORROW), EventTime.parse("30 minutes before my first meeting tomorrow"))
        assertEquals(EventTime.Ref(60, EventTime.Which.NAMED, "dentist", EventTime.Day.TOMORROW), EventTime.parse("an hour before the dentist tomorrow"))
        assertEquals(EventTime.Ref(15, EventTime.Which.NEXT, "", null), EventTime.parse("fifteen minutes before my next meeting"))
        assertEquals(EventTime.Ref(10, EventTime.Which.NEXT, "", EventTime.Day.TODAY), EventTime.parse("10 minutes before my meeting today"))
        assertEquals(EventTime.Ref(30, EventTime.Which.NAMED, "lunch with priya", null), EventTime.parse("half an hour before lunch with Priya"))
        // Not an event: a clock time, a length, a sum.
        for (other in listOf("7:30", "in 20 minutes", "half past six", "10 minutes before 7", "10 minutes before 7pm", "before noon", "tomorrow"))
            assertNull(other, EventTime.parse(other))
    }

    @Test fun theEventIsFoundAndTheAlarmWorkedOut() {
        fun at(text: String) = EventTime.resolve(EventTime.parse(text)!!, events, now)
        val first = at("30 minutes before my first meeting tomorrow") as EventTime.Found.At
        assertEquals("the all-day entry is not a meeting", "Standup", first.event.title)
        assertEquals(LocalDateTime.of(2026, 11, 28, 9, 0), first.alarm)
        assertEquals(LocalDateTime.of(2026, 11, 28, 13, 0), (at("an hour before the dentist") as EventTime.Found.At).alarm)
        assertEquals("Late call", (at("15 minutes before my next meeting") as EventTime.Found.At).event.title)
        // What cannot be found or set is said, not guessed.
        assertEquals("I don't see “physio” in your calendar tomorrow.", (at("an hour before physio tomorrow") as EventTime.Found.None).why)
        assertTrue((at("2 hours before my next meeting") as EventTime.Found.None).why.contains("less than 2 hours away"))
        assertTrue((at("an hour before the offsite") as EventTime.Found.None).why.contains("next 24 hours"))
        assertEquals("I don't see anything in your calendar later today.",
            (EventTime.resolve(EventTime.parse("10 minutes before my meeting today")!!, events.filter { it.title != "Late call" }, now) as EventTime.Found.None).why)
    }

    private class Phone(private val now: LocalDateTime, private val events: List<CalendarEvent>, private val calendar: Boolean = true) : ToolEnv by RecordingEnv() {
        val alarms = ArrayList<String>()
        override fun now() = now
        override fun events() = events
        override fun capabilities() = if (calendar) setOf(Capability.CalendarRead) else emptySet()
        override fun openAlarm(hour: Int?, minute: Int?, inMinutes: Int?, label: String?) { alarms += "$hour:$minute +$inMinutes $label" }
        override fun openAlarm(hour: Int?, minute: Int?, inMinutes: Int?) = openAlarm(hour, minute, inMinutes, null)
    }

    private val wake = """{"steps":[{"tool":"alarm","params":{"when":"30 minutes before my first meeting tomorrow"}}]}"""

    @Test fun anAlarmBeforeAMeetingIsSetFromTheCalendar() = runBlocking {
        val phone = Phone(now, events)
        val outcome = loop(wake, phone).run("wake me 30 minutes before my first meeting tomorrow") as AgentOutcome.Done
        assertEquals(listOf("9:0 +null Standup"), phone.alarms)
        assertEquals("Clock is open at 09:00, 30 minutes before “Standup”, tap Save", outcome.summary)
    }

    @Test fun whatTheCalendarCannotAnswerIsSaidAndNothingIsSet() = runBlocking {
        val empty = Phone(now, emptyList())
        assertEquals(AgentOutcome.Asks("I don't see anything in your calendar tomorrow."), loop(wake, empty).run("wake me 30 minutes before my first meeting tomorrow"))
        val shut = Phone(now, events, calendar = false)
        assertTrue((loop(wake, shut).run("wake me 30 minutes before my first meeting tomorrow") as AgentOutcome.Asks).question.startsWith("I can't see your calendar"))
        assertTrue(empty.alarms.isEmpty() && shut.alarms.isEmpty())
    }

    @Test fun aCalendarTimeNobodyAskedForIsNotWorkedOut() = runBlocking {
        // The model wrote it; the person only said "set an alarm".
        val phone = Phone(now, events)
        assertEquals(AgentOutcome.Asks("What time should the alarm be for?", null).question,
            (loop(wake, phone).run("set an alarm") as AgentOutcome.Asks).question)
        assertTrue(phone.alarms.isEmpty())
    }

    @Test fun anOrdinaryAlarmIsAsItWas() = runBlocking {
        val phone = Phone(now, events)
        loop("""{"steps":[{"tool":"alarm","params":{"when":"7:15"}}]}""", phone).run("alarm for 7:15")
        loop("""{"steps":[{"tool":"alarm","params":{"when":"in 45 minutes"}}]}""", phone).run("wake me up in 45 minutes")
        assertEquals(listOf("7:15 +null null", "null:null +45 null"), phone.alarms)
    }

    @Test fun aNamedEventIsNotMatchedOnALittleWord() {
        assertTrue(EventTime.named("lunch with priya", "Lunch with Priya"))
        assertTrue(EventTime.named("dentist", "Dentist appointment"))
        assertTrue(EventTime.named("the dentist", "dentist"))
        assertTrue("a word's beginning will do", EventTime.named("stand", "Standup"))
        assertTrue(!EventTime.named("lunch with priya", "Call with Sam"))
        assertTrue(!EventTime.named("lunch with priya", "Lunch with Sam"))
        assertTrue(!EventTime.named("with", "Call with Sam"))
        val calls = listOf(CalendarEvent("Call with Sam", LocalDateTime.of(2026, 11, 28, 8, 0)), CalendarEvent("Lunch with Priya", LocalDateTime.of(2026, 11, 28, 12, 30)))
        val found = EventTime.resolve(EventTime.parse("half an hour before lunch with Priya")!!, calls, now) as EventTime.Found.At
        assertEquals("Lunch with Priya", found.event.title)
    }

    @Test fun aTimeFoundBeforeIsNotKeptWhenTheEventHasGone() {
        val step = Params(mapOf("when" to "30 minutes before my first meeting tomorrow"))
        val bound = AlarmTool.bind(step, Phone(now, events))
        assertEquals("09:00", bound.values["_at"])
        assertNull(AlarmTool.bind(bound, Phone(now, emptyList())).values["_at"])
        assertNull(AlarmTool.bind(bound, Phone(now, events, calendar = false)).values["_at"])
    }

    /**
     * The scores compare a reply with the expected plan after both have been through these rules, so a rule that spoilt a right
     * plan would spoil both and not show. Here every expected step of every row (the three sets in the repository and the generated
     * dev set) goes through them alone: a right plan must come out as it went in.
     */
    @Test fun noRuleChangesAPlanThatIsAlreadyRight() {
        val sets = listOf("phrases.json", "phrases-fresh.json", "phrases-followup.json").flatMap { name ->
            EvalSet.parse(javaClass.getResourceAsStream("/agent/$name")!!.bufferedReader().readText()).map { it to "" }
        } + com.jake.duolauncher.agent.training.TrainingCorpus.dev().map { example ->
            EvalSet.parse("[" + org.json.JSONObject(example.reply).put("q", example.query).toString() + "]").firstOrNull()?.let { it to example.grounding }
        }.filterNotNull()
        var steps = 0
        for ((row, grounding) in sets) {
            val expected = (row.expected as? EvalRow.Expected.Steps)?.steps ?: continue
            val said = row.query + "\n" + grounding
            for ((name, values) in expected) {
                val tool = tools.first { it.name == name }
                val mended = tool.mended(Params(values), said).values
                for (key in listOf("title", "item", "text")) assertEquals("${row.query}: $name.$key", values[key], mended[key])
                val swapped = tool.instead(Params(values), row.query, said, RecordingEnv(grounding = grounding), tools)
                assertNull("${row.query}: $name was swapped for ${swapped?.tool?.name}", swapped)
                steps++
            }
        }
        assertTrue("every set was read ($steps steps)", steps > 500)
    }

    // ---------------------------------------------------------------------------------------------- the tools added with protocol 2

    private fun v2(reply: String, env: ToolEnv) = AgentLoop(object : Planner {
        override suspend fun plan(request: PlanRequest) = PlanParser.parse(reply, request.tools, request.protocol)
    }, Executor(env, maxSteps = Catalogue.MAX_STEPS_V2, maxPerTool = Executor.MAX_PER_TOOL_V2), tools = ToolRegistry.next, confirm = { true }, protocol = Catalogue.V2)

    @Test fun theNewToolsDoWhatTheySay() = runBlocking {
        val env = RecordingEnv()
        v2("""{"steps":[{"tool":"camera","params":{"mode":"selfie"}},{"tool":"ringer","params":{"mode":"vibrate"}},{"tool":"brightness","params":{"level":"30 percent"}},{"tool":"device","params":{"action":"screenshot"}},{"tool":"alarms_show","params":{}}]}""", env)
            .run("take a selfie, put the phone on vibrate, brightness to 30 percent, take a screenshot and show my alarms")
        assertEquals(listOf("camera selfie", "ringer vibrate", "brightness 30", "device screenshot", "alarms shown"), env.did)
        val more = RecordingEnv()
        v2("""{"steps":[{"tool":"play","params":{"what":"Blue in Green"}},{"tool":"contact_add","params":{"name":"Sam","number":"555 0142"}},{"tool":"email","params":{"to":"sam@example.com","subject":"friday","body":""}},{"tool":"calendar_open","params":{"day":"tomorrow"}},{"tool":"settings_panel","params":{"panel":"hotspot"}}]}""", more)
            .run("play Blue in Green, save 555 0142 as Sam, email sam@example.com about friday, open the calendar for tomorrow and turn on my hotspot")
        assertEquals(listOf("play blue in green", "contact sam 5550142", "email sam@example.com: friday / ", "calendar at 2026-11-24", "panel hotspot"), more.did)
    }

    @Test fun whatNobodyGaveIsAskedForByTheNewToolsToo() = runBlocking {
        val env = RecordingEnv()
        assertEquals("What is their email address?", (v2("""{"steps":[{"tool":"email","params":{"to":"sam@example.com","subject":"","body":""}}]}""", env).run("email Sam") as AgentOutcome.Asks).question)
        assertEquals("What is Sam's number?", (v2("""{"steps":[{"tool":"contact_add","params":{"name":"Sam","number":"555 0199"}}]}""", env).run("add Sam to my contacts") as AgentOutcome.Asks).question)
        assertEquals("What should I play?", (v2("""{"steps":[{"tool":"play","params":{"what":"Kind of Blue"}}]}""", env).run("play something") as AgentOutcome.Asks).question)
        assertEquals("How bright?", (v2("""{"steps":[{"tool":"brightness","params":{"level":"60 percent"}}]}""", env).run("change the brightness") as AgentOutcome.Asks).question)
        assertTrue(env.did.isEmpty())
    }

    @Test fun aToolThePhoneCannotDoIsNotOffered() {
        val names = { caps: Set<Capability> -> ToolRegistry.available(caps, ToolRegistry.next).map { it.name }.toSet() }
        assertTrue("ringer" !in names(emptySet()) && "brightness" !in names(emptySet()) && "device" !in names(emptySet()) && "reply" !in names(emptySet()))
        assertTrue("camera" in names(emptySet()) && "email" in names(emptySet()))
        assertTrue("ringer" in names(setOf(Capability.Ringer)) && "device" in names(setOf(Capability.Accessibility)))
        assertEquals(ToolRegistry.builtIn, ToolRegistry.forProtocol(Catalogue.V1))
    }
}
