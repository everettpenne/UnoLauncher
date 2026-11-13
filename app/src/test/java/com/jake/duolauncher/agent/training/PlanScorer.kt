package com.jake.duolauncher.agent.training

import com.jake.duolauncher.search.agent.EvalRow
import com.jake.duolauncher.search.agent.EvalSet
import com.jake.duolauncher.search.agent.ParseResult
import com.jake.duolauncher.search.agent.Plan
import com.jake.duolauncher.search.agent.PlanParser
import com.jake.duolauncher.search.agent.Tool

/** How one row did: whether the plan was exactly right, and if not, which half of it was wrong. */
internal class RowScore(
    val query: String,
    val expected: EvalRow.Expected,
    val reply: String?,
    val why: String?,
    val exact: Boolean,
    val toolMatch: Boolean,
    val paramMatch: Boolean,
    val typeMatch: Boolean,
)

/** The corpus measured against a model's replies: the numbers training has to move. */
internal class ScoreReport(val rows: List<RowScore>) {
    val total: Int get() = rows.size
    val exact: Int get() = rows.count { it.exact }
    val exactRate: Double get() = if (total == 0) 0.0 else exact.toDouble() / total
    val toolRate: Double get() = rate { it.toolMatch }
    val paramRate: Double get() = rate { it.paramMatch }

    /** Rows that should have acted and asked or refused instead: the failure that matters most. */
    val missedActions: Int get() = rows.count { it.expected is EvalRow.Expected.Steps && !it.typeMatch }
    val wrongTool: Int get() = rows.count { it.expected is EvalRow.Expected.Steps && it.typeMatch && !it.toolMatch }

    /** Of the rows that should ask, how many did. */
    fun clarifyRate(): Double = rateOfType { it is EvalRow.Expected.Clarify }

    /** Of the rows that should refuse, how many did. */
    fun refuseRate(): Double = rateOfType { it is EvalRow.Expected.Refuse }

    private fun rateOfType(isType: (EvalRow.Expected) -> Boolean): Double {
        val ofType = rows.filter { isType(it.expected) }
        if (ofType.isEmpty()) return 0.0
        return ofType.count { it.typeMatch && it.exact }.toDouble() / ofType.size
    }

    private fun rate(pick: (RowScore) -> Boolean): Double = if (total == 0) 0.0 else rows.count(pick).toDouble() / total

    fun summary(): String = buildString {
        append("exact ${exact}/${total} (${pct(exactRate)})")
        append(" · tool ${pct(toolRate)} · params ${pct(paramRate)}")
        append(" · clarify ${pct(clarifyRate())} · refuse ${pct(refuseRate())}")
        append(" · missed actions $missedActions")
    }

    fun failures(limit: Int = 20): List<String> = rows.filter { !it.exact }.take(limit).map {
        "${it.query} -> ${it.why ?: "expected ${typeName(it.expected)}, reply ${it.reply?.take(80)}"}"
    }

    private fun typeName(expected: EvalRow.Expected): String = when (expected) {
        is EvalRow.Expected.Steps -> "steps(${expected.steps.joinToString { it.first }})"
        EvalRow.Expected.Clarify -> "clarify"
        EvalRow.Expected.Refuse -> "refuse"
    }

    private fun pct(value: Double): String = (value * 100).toInt().let { "$it%" }
}

/**
 * Scores a model's replies against the eval rows using the real parser and the eval set's matching rules, so a number produced here means the
 * same thing as the harness the app's tests use. It compares parameter values too, which [EvalSet.check] does not: training is judged on
 * the whole plan.
 */
internal object PlanScorer {
    fun score(rows: List<EvalRow>, replies: Map<String, String>, tools: List<Tool>): ScoreReport =
        ScoreReport(rows.map { row -> scoreRow(row, replies[row.query], tools) })

    fun scoreRow(row: EvalRow, reply: String?, tools: List<Tool>): RowScore {
        if (reply == null) return RowScore(row.query, row.expected, null, "no reply", false, false, false, false)
        val parsed = runCatching { PlanParser.parse(reply, tools) }.getOrElse {
            return RowScore(row.query, row.expected, reply, "parser threw: ${it.message}", false, false, false, false)
        }
        if (parsed is ParseResult.Failed) {
            return RowScore(row.query, row.expected, reply, "does not parse: ${parsed.why}", false, false, false, false)
        }
        val plan = (parsed as ParseResult.Ok).plan
        return when (val expected = row.expected) {
            is EvalRow.Expected.Steps -> {
                if (plan !is Plan.Steps) {
                    return RowScore(row.query, expected, reply, null, false, false, false, false)
                }
                val toolMatch = plan.steps.map { it.tool.name } == expected.steps.map { it.first }
                val paramMatch = toolMatch && plan.steps.zip(expected.steps).all { (got, want) ->
                    val raw = got.params.values
                    want.second.all { (key, value) ->
                        val a = raw[key].orEmpty()
                        val b = value.orEmpty()
                        if (isBool(key, got.tool)) a.equals(b, ignoreCase = true) else a == b
                    }
                }
                RowScore(row.query, expected, reply, null, toolMatch && paramMatch, toolMatch, paramMatch, true)
            }
            EvalRow.Expected.Clarify -> RowScore(row.query, expected, reply, null, plan is Plan.Clarify, false, false, plan is Plan.Clarify)
            EvalRow.Expected.Refuse -> RowScore(row.query, expected, reply, null, plan is Plan.Refuse, false, false, plan is Plan.Refuse)
        }
    }

    private fun isBool(name: String, tool: Tool): Boolean =
        tool.params.firstOrNull { it.name == name }?.kind == com.jake.duolauncher.search.agent.Kind.Bool
}
