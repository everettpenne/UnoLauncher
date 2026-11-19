package com.jake.duolauncher.agent.training

import com.jake.duolauncher.search.agent.SpokenTime

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
        EvalRow.Expected.Search -> "search"
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
                // A number with no digits in it ("unknown", "") is one the model made up rather than asked for.
                plan.steps.firstOrNull { it.tool.name in setOf("dial", "message") && it.params.values["number"].orEmpty().none(Char::isDigit) }?.let {
                    return RowScore(row.query, expected, reply, "placeholder number in ${it.tool.name}", false, false, false, true)
                }
                val toolMatch = plan.steps.map { it.tool.name } == expected.steps.map { it.first }
                val paramMatch = toolMatch && plan.steps.zip(expected.steps).all { (got, want) ->
                    val raw = got.params.values
                    want.second.all { (key, value) ->
                        val a = raw[key].orEmpty()
                        val b = value.orEmpty()
                        same(got.tool, key, a, b)
                    }
                }
                RowScore(row.query, expected, reply, null, toolMatch && paramMatch, toolMatch, paramMatch, true)
            }
            EvalRow.Expected.Clarify -> RowScore(row.query, expected, reply, null, plan is Plan.Clarify, false, false, plan is Plan.Clarify)
            EvalRow.Expected.Refuse -> RowScore(row.query, expected, reply, null, plan is Plan.Refuse, false, false, plan is Plan.Refuse)
            EvalRow.Expected.Search -> RowScore(row.query, expected, reply, null, plan is Plan.OfferSearch, false, false, plan is Plan.OfferSearch)
        }
    }

    private val NOW = java.time.LocalDateTime.of(2026, 1, 5, 9, 0)
    private val word = Regex("""[\p{L}\p{N}]+""")

    /**
     * Whether two values of a parameter mean the same to the launcher. A time or a length is compared as what it parses to, so
     * "90 minutes" and "an hour and a half" are one answer; a number as its digits; anything else as its words, whatever the case.
     */
    fun same(tool: Tool, key: String, a: String, b: String): Boolean {
        val kind = tool.params.firstOrNull { it.name == key }?.kind
        if (kind == com.jake.duolauncher.search.agent.Kind.Bool) return a.equals(b, ignoreCase = true)
        fun <T : Any> parsed(parse: (String) -> T?): Boolean? {
            val x = parse(a); val y = parse(b)
            return if (x == null || y == null) null else x == y
        }
        val meaning = when (tool.name to key) {
            "timer" to "length", "calendar_event" to "length" -> parsed { SpokenTime.seconds(it) }
            "alarm" to "when" -> parsed { SpokenTime.alarm(it) }
            "calendar_event" to "start" -> parsed { SpokenTime.start(it, NOW) }
            "dial" to "number", "message" to "number" -> a.filter { it.isDigit() } == b.filter { it.isDigit() }
            "volume" to "level" -> parsed { com.jake.duolauncher.search.agent.tools.VolumeTool.target(it, 50) }
            else -> null
        }
        return meaning ?: (word.findAll(a.lowercase()).map { it.value }.toList() == word.findAll(b.lowercase()).map { it.value }.toList())
    }
}
