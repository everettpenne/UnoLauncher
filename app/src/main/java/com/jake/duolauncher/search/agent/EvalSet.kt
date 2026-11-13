package com.jake.duolauncher.search.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * The evaluation set: real phrasings and the plan each should produce. It exists so a change to a tool, a prompt or the model can be scored
 * instead of eyeballed — `check` says why a row does not pass, and the coverage test fails when a tool has no rows.
 */
internal class EvalRow(val query: String, val expected: Expected) {
    sealed interface Expected {
        data class Steps(val steps: List<Pair<String, Map<String, String>>>) : Expected
        data object Clarify : Expected
        data object Refuse : Expected
    }
}

internal object EvalSet {
    /** Reads `[{"q": "...", "steps": [{"tool": "timer", "params": {...}}]}, {"q": "...", "clarify": true}]`. */
    fun parse(json: String): List<EvalRow> {
        val array = JSONArray(json)
        val rows = ArrayList<EvalRow>(array.length())
        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: continue
            val query = row.optString("q").trim()
            if (query.isEmpty()) continue
            val expected = when {
                row.has("steps") -> {
                    val steps = row.getJSONArray("steps")
                    EvalRow.Expected.Steps((0 until steps.length()).map { j ->
                        val step = steps.getJSONObject(j)
                        val params = step.optJSONObject("params")?.let { objectParams(it) } ?: emptyMap()
                        step.optString("tool") to params
                    })
                }
                row.has("clarify") -> EvalRow.Expected.Clarify
                row.has("refuse") -> EvalRow.Expected.Refuse
                else -> null
            } ?: continue
            rows += EvalRow(query, expected)
        }
        return rows
    }

    /** The planner reply a row expects, in the same JSON shape Uno Lab returns, so the parser is exercised as it really is. */
    fun reply(row: EvalRow): String = when (val expected = row.expected) {
        is EvalRow.Expected.Steps -> JSONObject()
            .put("steps", JSONArray(expected.steps.map { (tool, params) ->
                JSONObject().put("tool", tool).put("params", JSONObject(params))
            }))
            .toString()
        EvalRow.Expected.Clarify -> """{"clarify":"Which one do you mean?"}"""
        EvalRow.Expected.Refuse -> """{"refuse":"I can't do that with the tools I have."}"""
    }

    /** Null when the row passes, otherwise what went wrong. The row's own reply is parsed exactly as a model's would be. */
    fun check(row: EvalRow, tools: List<Tool>): String? {
        val parsed = PlanParser.parse(reply(row), tools)
        if (parsed is ParseResult.Failed) return "does not parse: ${parsed.why}"
        val plan = (parsed as ParseResult.Ok).plan
        return when (val expected = row.expected) {
            is EvalRow.Expected.Steps -> {
                val steps = (plan as? Plan.Steps)?.steps ?: return "expected steps, got ${plan::class.simpleName}"
                if (steps.size != expected.steps.size) return "expected ${expected.steps.size} steps, got ${steps.size}"
                for (i in steps.indices) {
                    val wantTool = expected.steps[i].first
                    if (steps[i].tool.name != wantTool) return "step ${i + 1}: expected $wantTool, got ${steps[i].tool.name}"
                }
                null
            }
            EvalRow.Expected.Clarify -> if (plan is Plan.Clarify) null else "expected a question, got ${plan::class.simpleName}"
            EvalRow.Expected.Refuse -> if (plan is Plan.Refuse) null else "expected a refusal, got ${plan::class.simpleName}"
        }
    }

    /** Every tool named anywhere in the set: the coverage test requires at least three rows for each. */
    fun toolsUsed(rows: List<EvalRow>): Set<String> = rows.flatMap { row ->
        (row.expected as? EvalRow.Expected.Steps)?.steps?.map { it.first } ?: emptyList()
    }.toSet()

    private fun objectParams(obj: JSONObject): Map<String, String> =
        obj.keys().asSequence().associateWith { key -> obj.optString(key) }
}
