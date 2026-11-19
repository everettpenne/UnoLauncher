package com.jake.duolauncher.search.agent

/** How the person wants the agent to behave. [askAll] is the one switch that makes every action wait for a tap ("Ask before every
 *  action"); [alwaysAsk] names single tools that should, and is empty by default. */
internal class AgentSettings(val alwaysAsk: Set<String> = emptySet(), val askAll: Boolean = false)

/**
 * The whole confirmation policy: only what leaves the phone asks first. Everything local runs, and its summary is the confirmation.
 * One function, so changing the posture is one edit rather than a search through tools.
 */
/**
 * What kind of action a request's own words name, and whether a plan is that kind. A plan that is not ("set an alarm" planned as a timer:
 * seen on a phone) is not run on the model's word: the person is shown it and asked. Pure, so every row is unit-tested.
 */
internal object RequestKinds {
    private val word = Regex("""[\p{L}\p{N}'-]+""")
    /** A word (or two) of a request, and the tools that would be doing what it names. */
    private val table: List<Pair<Set<String>, Set<String>>> = listOf(
        setOf("alarm", "alarms", "wake") to setOf("alarm"),
        setOf("timer", "timers", "countdown") to setOf("timer", "stopwatch", "timer_cancel"),
        setOf("stopwatch") to setOf("stopwatch"),
        setOf("torch", "flashlight") to setOf("torch"),
        setOf("note", "notes", "remember") to setOf("note"),
        setOf("focus", "dnd") to setOf("focus"),
        setOf("call", "dial", "ring") to setOf("dial"),
        setOf("text", "sms", "message") to setOf("message", "reply"),
        // A text to them is a way of answering when nothing of theirs is waiting to be replied to.
        setOf("reply", "respond") to setOf("reply", "message"),
        // Not "meeting" or "event": they are as often what something else is for ("start the stopwatch for the meeting").
        setOf("calendar", "appointment") to setOf("calendar_event"),
        setOf("wifi", "wi-fi", "bluetooth", "airplane", "nfc") to setOf("settings_panel"),
        setOf("music", "song", "track", "playback") to setOf("media", "volume"),
        setOf("volume", "louder", "quieter") to setOf("volume"),
        setOf("directions", "navigate", "route") to setOf("directions"),
        setOf("checklist", "shopping") to setOf("checklist_add", "note"),
        setOf("remind", "reminder") to setOf("alarm", "timer", "calendar_event", "note", "checklist_add"),
    )

    /** For each kind the request names, the tools that count as doing it. Empty when it names none. */
    fun named(query: String): List<Set<String>> {
        val said = word.findAll(query.lowercase()).map { it.value }.toSet()
        return table.filter { (words, _) -> words.any { it in said } }.map { it.second }
    }

    /**
     * True when the request names a kind of action that no step of the plan is. A word that a step carries as its own text is not naming
     * an action: "write this down: call the plumber" is a note about a call, and "open Calendar" opens an app of that name.
     */
    fun mismatch(query: String, plan: Plan.Steps): Boolean {
        val used = plan.steps.map { it.tool.name }.toSet()
        val carried = plan.steps.flatMap { step -> step.params.values.values.flatMap { v -> word.findAll(v.lowercase()).map { it.value }.toList() } }.toSet()
        val said = word.findAll(query.lowercase()).map { it.value }.toSet()
        return table.any { (words, tools) ->
            val naming = words.filter { it in said && it !in carried }
            naming.isNotEmpty() && tools.none { it in used }
        }
    }
}

/** What a web search offered for a request looks for. Only the person's own words go to a search: nothing from the phone's facts. */
internal object SearchOffer {
    private val word = Regex("""[\p{L}\p{N}']+""")
    private val polite = Regex("""^(?:hey|hi|ok|okay|please|can you|could you|would you|will you|i need you to|i want you to)[\s,]+""", RegexOption.IGNORE_CASE)
    private val politeEnd = Regex("""[\s,]+(?:please|thanks|thank you|cheers|for me)[\s?.!]*$""", RegexOption.IGNORE_CASE)

    /** The request as a search: its first line (an answered question's later lines are ours), without the courtesy. */
    fun forRequest(request: String): String {
        var t = request.lineSequence().firstOrNull().orEmpty().trim()
        while (true) { val cut = politeEnd.replace(polite.replace(t, ""), ""); if (cut == t) break; t = cut }
        return t.trimEnd('?', '.', '!', ',').trim().take(200)
    }

    /** The planner's [offered] query when every word of it is the person's; otherwise the request itself. */
    fun query(offered: String, request: String): String {
        val said = word.findAll(request.lowercase()).map { it.value }.toSet()
        val words = word.findAll(offered.lowercase()).map { it.value }.toList()
        return if (words.isNotEmpty() && words.all { it in said }) offered.trim().take(200) else forRequest(request)
    }
}

internal object ToolPolicy {
    fun needsConfirmation(tool: Tool, settings: AgentSettings = AgentSettings()): Boolean =
        tool.effect == Effect.LeavesPhone || settings.askAll || tool.name in settings.alwaysAsk
}
