package com.jake.duolauncher.agent.training

import com.jake.duolauncher.search.AssistantLogRules
import com.jake.duolauncher.search.LogEntry
import com.jake.duolauncher.search.LoggedTool
import com.jake.duolauncher.search.agent.EvalSet
import com.jake.duolauncher.search.agent.ToolRegistry
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRowsTest {
    private val tools = ToolRegistry.builtIn
    private val timer = """{"steps":[{"tool":"timer","params":{"length":"20 minutes"}}]}"""
    private fun entry(id: Long, said: String, raw: String = timer, route: String = "plan", outcome: String = "done", rating: Int = 0, undone: Boolean = false,
        context: String = "", grounding: String = "") =
        LogEntry(id = id, route = route, said = said, request = said, context = context, grounding = grounding, raw = listOf(raw),
            tools = listOf(LoggedTool("timer", mapOf("length" to "20 minutes", "_key" to "x"), true)), reply = "Timer set", outcome = outcome, rating = rating, undone = undone)

    @Test fun aTurnRatedUpIsARowAsItStands() {
        val rows = LogRows.kept(listOf(entry(1, "timer for 20 minutes", rating = 1)), tools)
        val parsed = EvalSet.parse(JSONArray(rows).toString())
        assertEquals(1, parsed.size)
        assertNull("the row passes the eval set's own check", EvalSet.check(parsed.single(), tools))
        assertEquals("timer for 20 minutes", parsed.single().query)
    }

    @Test fun onlyPlannedTurnsWithNothingAgainstThemAreKept() {
        val entries = listOf(
            entry(1, "what is the capital of peru", route = "answer", rating = 1),
            entry(2, "timer for 5 minutes", rating = 0),
            entry(3, "timer for 6 minutes", rating = 1, undone = true),
            entry(4, "timer for 7 minutes", rating = 1, outcome = "failed"),
            entry(5, "undo that", route = "undo", rating = 1),
            entry(6, "timer for 8 minutes", rating = 1))
        assertEquals(listOf("timer for 8 minutes"), LogRows.kept(entries, tools).map { it.getString("q") })
    }

    @Test fun aTurnThatWentWrongIsARowToFinish() {
        val entries = listOf(
            entry(1, "light up the room", raw = """{"refuse":"I cannot change the room's lighting."}""", outcome = "refused"),
            entry(2, "set an alarm", rating = -1),
            entry(3, "timer for 5 minutes", undone = true),
            entry(4, "send it", outcome = "cancelled"),
            entry(5, "timer for 9 minutes", rating = 1))
        val review = LogRows.review(entries)
        assertEquals(listOf("refused", "thumbs-down", "undone", "turned down at the confirmation"), review.map { it.getString("why") })
        assertTrue(review.all { it.isNull("expect") })
        assertEquals("""{"refuse":"I cannot change the room's lighting."}""", review[0].getString("model_wrote"))
        // What a tool settled for itself (a notification's key) is not part of what was asked.
        assertEquals("timer {length=20 minutes}", review[1].getJSONArray("did").getString(0))
    }

    @Test fun theSameWordsAskedTwiceAreOneRowTheLaterOne() {
        val entries = listOf(entry(1, "Timer for 20 minutes", rating = 1, context = "old"), entry(2, "timer for 20 minutes", rating = 1, context = "new"))
        val kept = LogRows.kept(entries, tools)
        assertEquals(1, kept.size)
        assertTrue(LogRows.turns(entries, kept).single().getString("user").contains("new"))
    }

    @Test fun aRowsTurnIsThePromptThePhoneSent() {
        val e = entry(1, "reply to Sam saying ok", rating = 1, raw = """{"steps":[{"tool":"reply","params":{"to":"Sam","text":"ok"}}]}""",
            context = "Phone context:\n- Today: Friday", grounding = "Messages waiting for a reply (newest first): Sam in Signal")
        val turn = LogRows.turns(listOf(e), LogRows.kept(listOf(e), tools)).single().getString("user")
        assertEquals("Phone context:\n- Today: Friday", OutcomeScorer.block(turn, "context"))
        assertEquals("Sam", OutcomeScorer.waitingIn(OutcomeScorer.block(turn, "known")).single().who)
        assertTrue(turn.contains("Request: reply to Sam saying ok"))
    }

    /** The log's own file format goes in: an entry a line, a verdict on a later line. */
    @Test fun anExportedFileIsRead() {
        val text = AssistantLogRules.line(entry(7, "timer for 20 minutes")) + "\n" + AssistantLogRules.patch(7, rating = 1, ratedAtMs = 9) + "\n"
        assertEquals(1, LogRows.kept(LogRows.read(text), tools).size)
    }

    /** A thumbs-up is for what happened: the plan after the launcher put it right, not the words the model wrote. */
    @Test fun aKeptRowExpectsWhatRanNotWhatTheModelWrote() {
        val e = LogEntry(id = 1, route = "plan", said = "pencil in physio at 3pm", request = "pencil in physio at 3pm",
            raw = listOf("""{"steps":[{"tool":"calendar_event","params":{"title":"pencil in physio","start":"3pm"}}]}"""),
            tools = listOf(LoggedTool("calendar_event", mapOf("title" to "physio", "start" to "3pm"), true)), reply = "Calendar open", outcome = "done", rating = 1)
        val row = LogRows.kept(listOf(e), tools).single()
        assertEquals("physio", row.getJSONArray("steps").getJSONObject(0).getJSONObject("params").getString("title"))
        // A question back that was right is a row that expects a question.
        val asked = LogEntry(id = 2, route = "plan", said = "set an alarm", request = "set an alarm", raw = listOf(timer), outcome = "asked", rating = 1)
        assertTrue(LogRows.kept(listOf(asked), tools).single().getBoolean("clarify"))
    }
}
