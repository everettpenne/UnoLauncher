package com.jake.duolauncher.search.agent

import org.json.JSONObject

/** One step of a plan: a tool and the values it was given. */
internal data class Step(val tool: Tool, val params: Params)

/** What the planner may reply: something to do, a question, or a reason it cannot. */
internal sealed interface Plan {
    data class Steps(val steps: List<Step>) : Plan
    data class Clarify(val question: String) : Plan
    data class Refuse(val reason: String) : Plan
}

internal sealed interface ParseResult {
    data class Ok(val plan: Plan) : ParseResult
    data class Failed(val why: String) : ParseResult
}

/**
 * Reads the planner's reply. The reply is untrusted: unknown tools, missing parameter types and out-of-range values are rejected here, so
 * nothing past this point has to defend against malformed JSON. Pure, so every failure is unit-tested.
 */
internal object PlanParser {
    fun parse(reply: String, tools: List<Tool>): ParseResult {
        val json = JSONObject(unfence(reply) ?: return ParseResult.Failed("The planner did not answer with JSON."))
        json.optString("clarify").takeIf { it.isNotBlank() }?.let { return ParseResult.Ok(Plan.Clarify(it.trim())) }
        json.optString("refuse").takeIf { it.isNotBlank() }?.let { return ParseResult.Ok(Plan.Refuse(it.trim())) }
        val array = json.optJSONArray("steps") ?: return ParseResult.Failed("The planner gave neither steps, a question nor a reason.")
        if (array.length() == 0) return ParseResult.Failed("The plan had no steps.")
        val steps = ArrayList<Step>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: return ParseResult.Failed("Step ${i + 1} is not an object.")
            val name = item.optString("tool").trim()
            val tool = tools.firstOrNull { it.name == name }
                ?: return ParseResult.Failed("There is no \"$name\" tool. I can: ${tools.joinToString(", ") { it.name }}.")
            val values = HashMap<String, String>()
            item.optJSONObject("params")?.let { params ->
                params.keys().forEach { key -> values[key] = params.optString(key, "") }
            }
            when (val checked = tool.validate(values)) {
                is Validation.Ok -> steps += Step(tool, checked.value)
                is Validation.Rejected -> return ParseResult.Failed(checked.why)
            }
        }
        return ParseResult.Ok(Plan.Steps(steps))
    }

    /** The reply with any Markdown fence taken off: models like to wrap JSON in ```json. */
    fun unfence(reply: String): String? {
        val text = reply.trim()
        val start = text.indexOf("```")
        if (start < 0) return text.takeIf { it.startsWith("{") }
        val afterFence = text.indexOf('\n', start).let { if (it < 0) return null else it + 1 }
        val end = text.indexOf("```", afterFence)
        if (end < 0) return null
        return text.substring(afterFence, end).trim().takeIf { it.isNotEmpty() }
    }
}
