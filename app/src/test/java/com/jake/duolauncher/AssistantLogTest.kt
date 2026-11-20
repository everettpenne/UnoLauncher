package com.jake.duolauncher

import com.jake.duolauncher.search.AssistantLogRules
import com.jake.duolauncher.search.LogEntry
import com.jake.duolauncher.search.LoggedTool
import org.junit.Assert.*
import org.junit.Test

/** The assistant log's rules: an entry survives being written down and read back, a verdict is a line of its own, and it stays in bounds. */
class AssistantLogTest {
    private val entry = LogEntry(
        id = 1_700_000_000_123, route = "plan", said = "pasta timer for 5 minutes", request = "pasta timer for 5 minutes",
        context = "Phone context (from this phone, now):\n- Today: Saturday \"6\" March", grounding = "Apps: Settings", catalogue = "ab12",
        raw = listOf("{\"steps\":[{\"tool\":\"timer\",\"params\":{\"length\":\"50 minutes\"}}]}", "{\"steps\":[{\"tool\":\"timer\",\"params\":{\"length\":\"5 minutes\",\"label\":\"pasta\"}}]}"),
        tools = listOf(LoggedTool("timer", mapOf("length" to "5 minutes", "label" to "pasta"), true, "Timer set for 5 minutes", 12),
            LoggedTool("note", mapOf("text" to "buy pasta"), null)),
        reply = "Timer set for 5 minutes", outcome = "done", ms = 7300, launcher = "0.135.1-uno01", lab = "0.3.10")

    @Test fun anEntryIsOneLineAndReadsBackExactly() {
        val line = AssistantLogRules.line(entry)
        assertFalse("one entry, one line", line.contains('\n'))
        assertEquals(listOf(entry), AssistantLogRules.read(sequenceOf(line)))
    }

    @Test fun theFieldsATrainingRowIsBuiltFromAreUnderItsOwnNames() {
        val json = org.json.JSONObject(AssistantLogRules.line(entry))
        assertEquals(entry.request, json.getString("request")); assertEquals(entry.context, json.getString("context"))
        assertEquals(entry.grounding, json.getString("grounding")); assertEquals("ab12", json.getString("catalogue"))
        // The completion is the planner's reply word for word, the last of them being the one that was used.
        assertEquals(entry.raw.last(), json.getJSONArray("raw").getString(1))
        val tool = json.getJSONArray("tools").getJSONObject(0)
        assertEquals("timer", tool.getString("tool")); assertEquals("pasta", tool.getJSONObject("params").getString("label")); assertTrue(tool.getBoolean("ok"))
        assertTrue("a step that was planned and never run says so", json.getJSONArray("tools").getJSONObject(1).isNull("ok"))
    }

    @Test fun aVerdictIsALineOfItsOwnFoldedIntoItsEntry() {
        val lines = sequenceOf(AssistantLogRules.line(entry), AssistantLogRules.patch(entry.id, rating = -1, ratedAtMs = 99),
            AssistantLogRules.patch(entry.id, undone = true), AssistantLogRules.patch(42, rating = 1, ratedAtMs = 5))
        val read = AssistantLogRules.read(lines).single()
        assertEquals(-1, read.rating); assertEquals(99, read.ratedAtMs); assertTrue(read.undone); assertFalse(read.tapped)
        // The last verdict stands, and taking one back is a verdict too.
        val again = AssistantLogRules.read(lines + AssistantLogRules.patch(entry.id, rating = 0, ratedAtMs = 120) + AssistantLogRules.patch(entry.id, tapped = true)).single()
        assertEquals(0, again.rating); assertTrue(again.undone); assertTrue(again.tapped)
    }

    @Test fun tappingAThumbSetsItAndTappingItAgainTakesItBack() {
        assertEquals(1, AssistantLogRules.tapped(0, 1)); assertEquals(0, AssistantLogRules.tapped(1, 1))
        assertEquals(-1, AssistantLogRules.tapped(1, -1)); assertEquals(0, AssistantLogRules.tapped(-1, -1))
    }

    @Test fun aDamagedLineCostsOnlyThatLine() {
        val other = entry.copy(id = entry.id + 1, said = "what is the capital of France", route = "answer", outcome = "answered")
        val read = AssistantLogRules.read(sequenceOf("{\"id\":", AssistantLogRules.line(entry), "", "not json", "{\"id\":7}", AssistantLogRules.line(other)))
        assertEquals(listOf(entry.id, other.id), read.map { it.id })
    }

    @Test fun onlyTheNewestAreKeptAndVeryLongTextIsCut() {
        val many = (1..AssistantLogRules.MAX_ENTRIES + 25L).map { entry.copy(id = it) }
        val kept = AssistantLogRules.kept(many)
        assertEquals(AssistantLogRules.MAX_ENTRIES, kept.size); assertEquals(26L, kept.first().id); assertEquals(many.last().id, kept.last().id)
        val long = AssistantLogRules.read(sequenceOf(AssistantLogRules.line(entry.copy(context = "x".repeat(20_000))))).single()
        assertEquals(AssistantLogRules.MAX_TEXT, long.context.length)
    }

    @Test fun twoTurnsEndingInTheSameMillisecondGetTheirOwnIds() {
        assertEquals(500L, AssistantLogRules.freshId(emptyList(), 500))
        assertEquals(502L, AssistantLogRules.freshId(listOf(entry.copy(id = 500), entry.copy(id = 501)), 500))
    }

    @Test fun theExportIsOneEntryALineWithVerdictsFoldedIn() {
        val rated = entry.copy(rating = 1, ratedAtMs = 77)
        val text = AssistantLogRules.export(listOf(rated, entry.copy(id = 9)))
        assertEquals(2, text.trim().lines().size)
        assertEquals(listOf(rated, entry.copy(id = 9)), AssistantLogRules.read(text.lineSequence()))
    }
}
