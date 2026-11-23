package com.jake.duolauncher.agent

import com.jake.duolauncher.search.agent.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * What the launcher recognises by itself, with no model: exactly the requests it is sure of, and silence for everything else, since a
 * match acts at once. And the planner's catalogue is exactly what the planner was trained on: nothing added here reaches it.
 */
class DirectSkillsTest {
    private fun step(q: String) = DirectSkills.read(q)?.steps?.single()
    private fun said(q: String) = step(q)?.let { it.tool.name to it.params.values }

    @Test fun thePlannerIsSentExactlyTheCatalogueItWasTrainedOn() {
        // The adapter was trained against this list. Adding a tool here changes what the model is shown, which needs a new training
        // run; a capability the launcher can recognise by itself goes in DirectSkills.tools instead.
        assertEquals(listOf("timer", "stopwatch", "torch", "focus", "note", "alarm", "open_app", "dial", "message", "open_url", "calendar_event",
            "web_search", "settings_panel", "reply", "timer_cancel", "media", "volume", "checklist_add", "directions"), ToolRegistry.builtIn.map { it.name })
        assertEquals("b13ce3444d307385", Catalogue.hash(ToolRegistry.builtIn))
        assertTrue("a direct skill's tool is not in the planner's list", DirectSkills.tools.none { direct -> ToolRegistry.builtIn.any { it.name == direct.name } })
        assertEquals(DirectSkills.tools.size, DirectSkills.tools.map { it.name }.toSet().size)
    }

    @Test fun aReminderWithWhatAndWhenIsRecognised() {
        assertEquals("reminder" to mapOf("what" to "call Sam", "when" to "5"), said("remind me to call Sam at 5"))
        assertEquals("reminder" to mapOf("what" to "take the bread out", "when" to "in 20 minutes"), said("Remind me in 20 minutes to take the bread out"))
        assertEquals("reminder" to mapOf("what" to "the meeting", "when" to "3:30pm"), said("hey qwen, remind me about the meeting at 3:30pm please"))
        assertEquals("reminder" to mapOf("what" to "stretch", "when" to "in an hour"), said("remind me to stretch in an hour."))
    }

    @Test fun aReminderThatIsNotWhollyUnderstoodIsLeftToThePlanner() {
        for (q in listOf("remind me", "remind me later", "remind me in a bit", "remind me to call Sam", "remind me at 5",
            // Another day is a calendar's job, not an alarm's for today.
            "remind me to call Sam tomorrow at 5", "remind me to pay rent on friday at 9", "remind me every day at 8 to take my pills",
            // Two things, or a time that is not one.
            "remind me to call Sam at 5 and text Priya", "remind me to call Sam at some point", "remind me to call Sam in a while",
            "remind Sam to call me at 5", "set a reminder", "what did I ask you to remind me about at 5"))
            assertNull(q, DirectSkills.read(q))
    }

    @Test fun theLaunchersOwnStyleAndSettingsAreRecognised() {
        assertEquals("uno_style" to mapOf("level" to "expressive"), said("use the expressive style"))
        assertEquals("uno_style" to mapOf("level" to "calm"), said("Set the style to Calm"))
        assertEquals("uno_style" to mapOf("level" to "balanced"), said("switch uno to balanced"))
        assertEquals("uno_settings" to mapOf("page" to "all"), said("open uno settings"))
        assertEquals("uno_settings" to mapOf("page" to "keyboard"), said("open the launcher's keyboard settings"))
        // Android's own settings, and anything else with the same words in it, are not these.
        for (q in listOf("open settings", "open keyboard settings", "open wifi settings", "what is the expressive style", "use the expressive style of writing in my note",
            "set the style", "calm", "note: use the calm style tomorrow", "play calm music", "open uno", "search for uno settings"))
            assertNull(q, DirectSkills.read(q))
    }

    @Test fun everyRequestThePlannerIsEvaluatedOnIsLeftToThePlanner() {
        // The planner's own evaluation phrases: none of them is a direct skill's, so adding the skills changed nothing for any of them.
        val phrases = listOf("phrases.json", "phrases-fresh.json", "phrases-followup.json").flatMap { name ->
            val file = listOf("src/test/resources/agent/$name", "app/src/test/resources/agent/$name").map(::File).first { it.exists() }
            Regex("\"q\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(file.readText()).map { it.groupValues[1].replace("\\\"", "\"").replace("\\n", "\n") }.toList()
        }
        assertTrue("read the phrases: ${phrases.size}", phrases.size > 120)

        val taken = phrases.filter { DirectSkills.read(it) != null }
        assertEquals("these would no longer reach the planner", emptyList<String>(), taken)
    }

    @Test fun theSkillsToolsSayWhatTheyWillDoAndAskForWhatIsMissing() {
        val env = FakeEnv()
        val plan = DirectSkills.read("remind me to call Sam at 5")!!
        // Asked at two in the afternoon, "at 5" is five that afternoon; asked at nine at night, it is five in the morning.
        assertEquals("Open Clock to set “call Sam” for ${Speak.clock(17, 0)}", ReminderTool.describe(plan.steps[0].params, env))
        env.clock = java.time.LocalDateTime.of(2026, 11, 23, 21, 0)
        assertEquals("Open Clock to set “call Sam” for ${Speak.clock(5, 0)}", ReminderTool.describe(plan.steps[0].params, env))
        assertEquals("said outright, it is what was said", "Open Clock to set “call Sam” for ${Speak.clock(5, 0)}",
            ReminderTool.describe(Params(mapOf("what" to "call Sam", "when" to "5am")), FakeEnv()))
        assertNotNull(ReminderTool.missing(Params(mapOf("what" to "call Sam")), ""))
        assertNotNull(StyleTool.missing(Params(mapOf("level" to "loud")), ""))
        assertNull(StyleTool.missing(Params(mapOf("level" to "calm")), ""))
    }

    @Test fun settingTheStyleCanBeUndoneAndAReminderNamesItsAlarm() = kotlinx.coroutines.runBlocking {
        val env = FakeEnv()
        val set = StyleTool.execute(Params(mapOf("level" to "expressive")), env)
        assertTrue(set.ok); assertEquals("expressive", env.styleNow)
        assertTrue(set.undo!!.invoke().ok); assertEquals("balanced", env.styleNow)
        assertEquals("Uno's Style is already Balanced", StyleTool.execute(Params(mapOf("level" to "balanced")), env).say)
        assertTrue(ReminderTool.execute(Params(mapOf("what" to "call Sam", "when" to "in 20 minutes")), env).ok)
        assertEquals("call Sam", env.alarmLabel); assertEquals(20, env.alarmIn)
        assertTrue(UnoSettingsTool.execute(Params(mapOf("page" to "keyboard")), env).ok); assertEquals("keyboard", env.opened)
    }

    // ---- through a session, as search runs them ----

    private class Turn { val log = ArrayList<com.jake.duolauncher.search.LogEntry>(); val chat = ArrayList<String>(); val states = ArrayList<AgentState>() }

    private fun session(env: FakeEnv, turn: Turn): AgentSession {
        val neverAsked = object : Planner { override suspend fun plan(request: PlanRequest): ParseResult = error("a direct skill asked the planner") }
        lateinit var s: AgentSession
        s = AgentSession(planner = { neverAsked }, env = { env }, scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined),
            publish = { turn.states += s.state }, record = { _, reply, _, _ -> turn.chat += reply }, log = { turn.log += it })
        return s
    }

    @Test fun everyDirectSkillRunsToDoneWithNoModelAndNoQuestion() = kotlinx.coroutines.runBlocking {
        for ((asked, done) in listOf("remind me in 20 minutes to take the bread out" to "Clock is open with “take the bread out” in 20 minutes, tap Save",
            "use the expressive style" to "Uno's Style is Expressive", "open uno settings" to "Uno's settings are open")) {
            val env = FakeEnv(); val turn = Turn()
            val s = session(env, turn)
            s.direct(asked, DirectSkills.read(asked)!!)
            s.awaitTurn()
            assertEquals(asked, done, s.summary)
            assertTrue("$asked ended done: ${s.state}", s.state is AgentState.Done)
            assertTrue("$asked was not put to the person first", turn.states.none { it is AgentState.Confirming || it is AgentState.Asking })
            val entry = turn.log.single()
            assertEquals("direct", entry.route); assertEquals("done", entry.outcome); assertTrue("nothing the model wrote", entry.raw.isEmpty())
            assertEquals(asked, entry.said); assertEquals(true, entry.tools.single().ok)
            assertEquals(listOf(done), turn.chat)
        }
    }

    @Test fun aStyleSetByAskingIsTakenBackWithUndo() = kotlinx.coroutines.runBlocking {
        val env = FakeEnv(); val turn = Turn()
        val s = session(env, turn)
        s.direct("switch uno to calm", DirectSkills.read("switch uno to calm")!!); s.awaitTurn()
        assertEquals("calm", env.styleNow); assertTrue(s.canUndo)
        assertTrue(s.undo()!!.ok)
        assertEquals("balanced", env.styleNow)
    }

    @Test fun aDirectSkillInPlanOnlyModeDoesNothing() = kotlinx.coroutines.runBlocking {
        val env = FakeEnv(); val turn = Turn()
        val s = session(env, turn)
        s.direct("use the expressive style", DirectSkills.read("use the expressive style")!!, planOnly = true); s.awaitTurn()
        assertEquals("balanced", env.styleNow); assertEquals("planned", turn.log.single().outcome)
    }

    private class FakeEnv : ToolEnv {
        var styleNow = "balanced"; var alarmLabel: String? = null; var alarmIn: Int? = null; var opened: String? = null
        var clock: java.time.LocalDateTime = java.time.LocalDateTime.of(2026, 11, 23, 14, 0)
        override fun now() = clock
        override val noteSlot: Int? = null
        override val apps: List<com.jake.duolauncher.AppEntry> = emptyList()
        override fun focusOn() = false
        override fun toggleFocus() = Unit
        override fun startTimer(seconds: Long) = Unit
        override fun cancelTimer() = Unit
        override fun toggleStopwatch() = true
        override fun toggleTorch() = true
        override fun openAlarm(hour: Int?, minute: Int?, inMinutes: Int?) = Unit
        override fun openAlarm(hour: Int?, minute: Int?, inMinutes: Int?, label: String?) { alarmLabel = label; alarmIn = inMinutes }
        override fun openApp(entry: com.jake.duolauncher.AppEntry) = true
        override fun openDialer(number: String) = Unit
        override fun openMessages(number: String) = Unit
        override fun openUrl(url: String) = Unit
        override fun openCalendar(title: String, inMinutes: Int, durationMinutes: Int) = Unit
        override fun openWebSearch(query: String) = Unit
        override fun openSettingsPanel(panel: String) = Unit
        override fun writeNote(text: String): String? = ""
        override fun style(): String = styleNow
        override fun setStyle(level: String): Boolean { styleNow = level; return true }
        override fun openUnoSettings(page: String): Boolean { opened = page; return true }
    }
}
