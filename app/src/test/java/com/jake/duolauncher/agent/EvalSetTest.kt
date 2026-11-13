package com.jake.duolauncher.agent

import com.jake.duolauncher.search.agent.EvalRow
import com.jake.duolauncher.search.agent.EvalSet
import com.jake.duolauncher.search.agent.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The evaluation set is data, and this keeps it honest: every row has to parse through the same parser a model's reply does, every tool has to
 * be represented, and the rows have to stay distinct. It is the harness the later model and prompt work will be scored with.
 */
class EvalSetTest {
    private val rows = load()

    private fun load(): List<EvalRow> {
        val stream = javaClass.getResourceAsStream("/agent/phrases.json")
        assertNotNull("the eval set is missing", stream)
        return EvalSet.parse(stream!!.bufferedReader().readText())
    }

    @Test fun everyRowPassesItsOwnExpectedPlan() {
        val tools = ToolRegistry.builtIn
        val failures = rows.mapNotNull { row -> EvalSet.check(row, tools)?.let { "${row.query}: $it" } }
        assertTrue("rows that do not parse as expected:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test fun everyToolHasAtLeastThreePhrasings() {
        val counts = rows.flatMap { row -> (row.expected as? EvalRow.Expected.Steps)?.steps?.map { it.first } ?: emptyList() }
            .groupingBy { it }.eachCount()
        val thin = ToolRegistry.builtIn.map { it.name }.filter { (counts[it] ?: 0) < 3 }
        assertTrue("tools with fewer than three rows: $thin (counts: $counts)", thin.isEmpty())
    }

    @Test fun theSetCoversAskingAndRefusingToo() {
        assertTrue(rows.count { it.expected is EvalRow.Expected.Clarify } >= 3)
        assertTrue(rows.count { it.expected is EvalRow.Expected.Refuse } >= 3)
    }

    @Test fun queriesAreDistinct() {
        assertEquals(rows.size, rows.map { it.query }.toSet().size)
        assertTrue("expected a set of at least 30 phrasings, got ${rows.size}", rows.size >= 30)
    }

    @Test fun aRowThatWouldNotParseSaysWhy() {
        val broken = EvalRow("timer", EvalRow.Expected.Steps(listOf("no_such_tool" to emptyMap())))
        val why = EvalSet.check(broken, ToolRegistry.builtIn)
        assertNotNull(why)
        assertTrue(why!!.contains("does not parse"))
        assertNull(EvalSet.check(rows.first { it.expected is EvalRow.Expected.Clarify }, ToolRegistry.builtIn))
    }
}
