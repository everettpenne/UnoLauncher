package com.jake.duolauncher.agent.training

import com.jake.duolauncher.search.AssistantLogRules
import com.jake.duolauncher.search.LogEntry
import com.jake.duolauncher.search.agent.ParseResult
import com.jake.duolauncher.search.agent.Plan
import com.jake.duolauncher.search.agent.PlanParser
import com.jake.duolauncher.search.agent.Tool
import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns an assistant log exported from the phone (Launcher settings, Assistant, Export) into rows for the eval set, so the set
 * grows from what was really asked and how it really went, not from phrasings thought up at a desk.
 *
 * Only turns the planner wrote ([planned]) are used. A turn the person gave a thumbs-up is a row as it stands: the request, with the
 * steps that ran as the plan to expect ([kept]). A turn that went wrong (a thumbs-down, an Undo, a refusal, a failure, a
 * question that was turned down) is a row someone has to finish ([review]): the request and what happened are written out, and
 * `expect` is left for the right plan. Each row carries its user turn as the launcher sent it, facts and all, so a model is scored on
 * the prompt it was really given. Pure, so it is unit-tested; nothing here reads a log that was not handed to it.
 */
internal object LogRows {
    private val routes = setOf("plan", "refers", "resume")

    fun planned(entries: List<LogEntry>): List<LogEntry> = entries.filter { it.route in routes && it.raw.isNotEmpty() && it.request.isNotBlank() }

    /** What a turn that went wrong went wrong as, or null for one with nothing against it. */
    fun wrong(e: LogEntry): String? = when {
        e.rating < 0 -> "thumbs-down"
        e.undone -> "undone"
        e.outcome == "failed" -> "failed"
        e.outcome == "cancelled" -> "turned down at the confirmation"
        e.outcome == "refused" -> "refused"
        else -> null
    }

    /**
     * What a turn the person approved of comes to, as a row's expectation. For a turn that acted it is the steps that ran, with the
     * values they ran with: a thumbs-up is a verdict on what happened, which is the plan after the launcher put it right, not the
     * words the model wrote. For a question back, a refusal or a search offer it is that. Null when the steps no longer read as a plan
     * with today's tools.
     */
    private fun expectation(e: LogEntry, tools: List<Tool>): Pair<String, Any>? {
        if (e.outcome == "done" && e.tools.isNotEmpty() && e.tools.all { it.ok == true }) {
            val steps = JSONArray(e.tools.map { t -> JSONObject().put("tool", t.tool).put("params", JSONObject(t.params.filterKeys { !it.startsWith("_") })) })
            val reads = (runCatching { PlanParser.parse(JSONObject().put("steps", steps).toString(), tools) }.getOrNull() as? ParseResult.Ok)?.plan is Plan.Steps
            return if (reads) "steps" to steps else null
        }
        if (e.outcome == "asked") return "clarify" to true
        return when ((runCatching { PlanParser.parse(e.raw.last(), tools) }.getOrNull() as? ParseResult.Ok)?.plan) {
            is Plan.Refuse -> "refuse" to true
            is Plan.OfferSearch -> "search" to true
            else -> null
        }
    }

    private fun turn(e: LogEntry) = JSONObject().put("q", e.request.trim()).put("user", TrainingFormat.turn(e.request, e.context, e.grounding))

    /** Rows to keep as they are: rated up, nothing against them, and a reply that still reads as a plan with today's tools. One row a
     *  request: the same words asked twice are one row, the later one. */
    fun kept(entries: List<LogEntry>, tools: List<Tool>): List<JSONObject> = planned(entries)
        .filter { it.rating > 0 && wrong(it) == null }
        .associateBy { it.request.trim().lowercase() }.values
        .mapNotNull { e -> expectation(e, tools)?.let { (key, value) -> JSONObject().put("q", e.request.trim()).put(key, value) } }

    /** Rows to finish by hand: what was asked, what the model wrote, what the person was shown and why it counts as wrong. */
    fun review(entries: List<LogEntry>): List<JSONObject> = planned(entries).filter { wrong(it) != null }
        .associateBy { it.request.trim().lowercase() }.values
        .map { e ->
            JSONObject().put("q", e.request.trim()).put("why", wrong(e)).put("model_wrote", e.raw.last()).put("shown", e.reply)
                .put("did", JSONArray(e.tools.map { t -> "${t.tool} ${t.params.filterKeys { !it.startsWith("_") }}" + if (t.ok == false) " (failed)" else "" }))
                .put("catalogue", e.catalogue)
                // To be written: {"steps":[…]}, or "clarify"/"refuse"/"search": true. Then move the row into the eval set.
                .put("expect", JSONObject.NULL)
        }

    /** The user turns for [rows], as the launcher sent them, for scoring a model on them (the `…-turns.json` beside a rows file). */
    fun turns(entries: List<LogEntry>, rows: List<JSONObject>): List<JSONObject> {
        val wanted = rows.map { it.getString("q").lowercase() }.toSet()
        return planned(entries).associateBy { it.request.trim().lowercase() }.filterKeys { it in wanted }.values.map(::turn)
    }

    fun read(text: String): List<LogEntry> = AssistantLogRules.read(text.lineSequence())
}
