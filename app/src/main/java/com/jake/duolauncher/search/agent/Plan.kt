package com.jake.duolauncher.search.agent

import org.json.JSONObject

/** One step of a plan: a tool and the values it was given. */
internal data class Step(val tool: Tool, val params: Params)

/** What the planner may reply: something to do, a question, or a reason it cannot. */
internal sealed interface Plan {
    data class Steps(val steps: List<Step>) : Plan
    data class Clarify(val question: String) : Plan
    data class Refuse(val reason: String) : Plan
    /** "I can't do or don't know this, but a web search would help": offered to the person, never run on the planner's word. */
    data class OfferSearch(val query: String) : Plan
    /** "I need a fact first": the sources to read ([LookSource] ids), answered by asking again with what was found. Protocol 2 only. */
    data class Look(val sources: List<LookSource>) : Plan
}

/**
 * What the planner may ask to look at before it plans: the phone's own facts, by kind. Each is something the launcher already reads
 * for a question about it (search.context.PhoneContext), and only facts the phone itself holds: nothing written by other people (a
 * notification's words, a web page) can be asked for, so nothing other people wrote can steer a plan.
 */
internal enum class LookSource(val id: String, val holds: String, /** The PhoneContext source it reads. */ val context: String) {
    CALENDAR("calendar", "events in the next 7 days, with their times and places", "calendar"),
    CONTACTS("contacts", "people the request names, with their numbers", "contacts"),
    APPS("apps", "the apps that are installed", "apps"),
    ALARMS("alarms", "the next alarm", "alarm"),
    TIMERS("timers", "a running timer or stopwatch", "timer"),
    PLAYING("playing", "what is playing now", "playing"),
    NOTES("notes", "the note, the checklist and the counters on Home", "notes"),
    BATTERY("battery", "the battery level and whether it is charging", "battery"),
    PLACE("place", "the place set as home, and the last known location", "location");

    companion object { fun of(id: String): LookSource? = entries.firstOrNull { it.id == id.trim().lowercase() } }
}

internal sealed interface ParseResult {
    data class Ok(val plan: Plan) : ParseResult
    /** [retry]: the planner answered, but with something that is not allowed (an unknown tool, a value out of range), so asking it once
     *  more with the reason may help. A planner that could not be reached is not asked again. */
    data class Failed(val why: String, val retry: Boolean = false) : ParseResult
}

/**
 * Reads the planner's reply. The reply is untrusted: unknown tools, missing parameter types and out-of-range values are rejected here, so
 * nothing past this point has to defend against malformed JSON. Pure, so every failure is unit-tested.
 */
internal object PlanParser {
    /** [protocol]: [Catalogue.V2] also reads a request to look something up, and allows five steps. */
    fun parse(reply: String, tools: List<Tool>, protocol: Int = Catalogue.V1): ParseResult {
        val json = JSONObject(unfence(reply) ?: return ParseResult.Failed("The planner did not answer with JSON."))
        if (protocol >= Catalogue.V2) json.optJSONArray("look")?.let { array ->
            val sources = (0 until array.length()).map { i -> LookSource.of(array.optString(i)) ?: return ParseResult.Failed("There is no \"${array.optString(i)}\" to look at. You can look at: ${LookSource.entries.joinToString(", ") { it.id }}.", retry = true) }
            if (sources.isEmpty() || sources.size > Catalogue.MAX_LOOKS) return ParseResult.Failed("Look at one or two things.", retry = true)
            return ParseResult.Ok(Plan.Look(sources.distinct()))
        }
        json.optString("clarify").takeIf { it.isNotBlank() }?.let { return ParseResult.Ok(Plan.Clarify(it.trim())) }
        json.optString("refuse").takeIf { it.isNotBlank() }?.let { return ParseResult.Ok(Plan.Refuse(it.trim())) }
        json.optString("search").takeIf { it.isNotBlank() }?.let { return ParseResult.Ok(Plan.OfferSearch(it.trim())) }
        val array = json.optJSONArray("steps") ?: return ParseResult.Failed("The planner gave neither steps, a question nor a reason.")
        if (array.length() == 0) return ParseResult.Failed("The plan had no steps.", retry = true)
        if (protocol >= Catalogue.V2 && array.length() > Catalogue.MAX_STEPS_V2) return ParseResult.Failed("A plan has ${Catalogue.MAX_STEPS_V2} steps at most.", retry = true)
        val steps = ArrayList<Step>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: return ParseResult.Failed("Step ${i + 1} is not an object.")
            val name = item.optString("tool").trim()
            val tool = tools.firstOrNull { it.name == name }
                ?: return ParseResult.Failed("There is no \"$name\" tool. I can: ${tools.joinToString(", ") { it.name }}.", retry = true)
            val values = HashMap<String, String>()
            item.optJSONObject("params")?.let { params ->
                params.keys().forEach { key -> values[key] = params.optString(key, "") }
            }
            when (val checked = tool.validate(values)) {
                is Validation.Ok -> steps += Step(tool, checked.value)
                is Validation.Rejected -> return ParseResult.Failed(checked.why, retry = true)
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
