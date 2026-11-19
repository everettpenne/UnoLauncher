package com.jake.duolauncher.agent.training

import com.jake.duolauncher.search.agent.EvalRow
import com.jake.duolauncher.search.agent.EvalSet
import com.jake.duolauncher.search.agent.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanScorerTest {
    private val tools = ToolRegistry.builtIn
    private val timerRow = EvalRow("timer for 20 minutes", EvalRow.Expected.Steps(listOf("timer" to mapOf("length" to "20 minutes"))))
    private val clarifyRow = EvalRow("text Sam", EvalRow.Expected.Clarify)

    @Test fun theEvalSetsOwnReplyScoresExact() {
        val score = PlanScorer.scoreRow(timerRow, EvalSet.reply(timerRow), tools)
        assertTrue(score.exact)
        assertTrue(score.toolMatch)
        assertTrue(score.paramMatch)
    }

    @Test fun theWrongToolIsNotExact() {
        val score = PlanScorer.scoreRow(timerRow, """{"steps":[{"tool":"stopwatch","params":{"action":"start"}}]}""", tools)
        assertFalse(score.exact)
        assertFalse(score.toolMatch)
    }

    @Test fun theRightToolWithTheWrongValueIsNotExact() {
        val score = PlanScorer.scoreRow(timerRow, """{"steps":[{"tool":"timer","params":{"length": "10 minutes"}}]}""", tools)
        assertFalse(score.exact)
        assertTrue(score.toolMatch)
        assertFalse(score.paramMatch)
    }

    @Test fun aMadeUpNumberIsNotAPlan() {
        val row = EvalRow("reply to Sam saying hi", EvalRow.Expected.Steps(listOf("message" to mapOf("number" to "555 0142", "text" to "hi"))))
        val score = PlanScorer.scoreRow(row, """{"steps":[{"tool":"message","params":{"number":"unknown","text":"hi"}}]}""", tools)
        assertFalse(score.exact)
    }

    @Test fun askingInsteadOfActingIsAMissedAction() {
        val score = PlanScorer.scoreRow(timerRow, """{"clarify":"How long?"}""", tools)
        assertFalse(score.exact)
        assertFalse(score.typeMatch)
    }

    @Test fun anUnparseableReplyIsReportedNotThrown() {
        val score = PlanScorer.scoreRow(timerRow, "I would set a timer.", tools)
        assertFalse(score.exact)
        assertTrue(score.why!!.contains("does not parse"))
    }

    @Test fun aReportSaysRatesAndFailures() {
        val rows = listOf(timerRow, clarifyRow)
        val report = PlanScorer.score(rows, mapOf(
            timerRow.query to EvalSet.reply(timerRow),
            clarifyRow.query to """{"clarify":"Which number?"}""",
        ), tools)
        assertEquals(2, report.total)
        assertEquals(2, report.exact)
        assertEquals(1.0, report.exactRate, 0.001)
        assertEquals(1.0, report.clarifyRate(), 0.001)
        assertTrue(report.summary().contains("exact 2/2"))
        assertTrue(report.failures().isEmpty())
    }

    @Test fun aRefusalForAnActionRowCounts() {
        val report = PlanScorer.score(listOf(timerRow), mapOf(timerRow.query to """{"refuse":"I cannot do that."}"""), tools)
        assertEquals(0, report.exact)
        assertEquals(1, report.missedActions)
    }
}
