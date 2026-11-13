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
        setOf("timer", "timers", "countdown") to setOf("timer"),
        setOf("stopwatch") to setOf("stopwatch"),
        setOf("torch", "flashlight") to setOf("torch"),
        setOf("note", "notes", "remember") to setOf("note"),
        setOf("focus", "dnd") to setOf("focus"),
        setOf("call", "dial", "phone", "ring") to setOf("dial"),
        setOf("text", "sms", "message") to setOf("message", "reply"),
        setOf("reply", "respond") to setOf("reply"),
        setOf("calendar", "appointment", "meeting", "event") to setOf("calendar_event"),
        setOf("wifi", "wi-fi", "bluetooth", "airplane", "nfc") to setOf("settings_panel"),
        setOf("remind", "reminder") to setOf("alarm", "timer", "calendar_event", "note"),
    )

    /** For each kind the request names, the tools that count as doing it. Empty when it names none. */
    fun named(query: String): List<Set<String>> {
        val said = word.findAll(query.lowercase()).map { it.value }.toSet()
        return table.filter { (words, _) -> words.any { it in said } }.map { it.second }
    }

    /** True when the request names a kind of action that no step of the plan is. */
    fun mismatch(query: String, plan: Plan.Steps): Boolean {
        val used = plan.steps.map { it.tool.name }.toSet()
        return named(query).any { tools -> tools.none { it in used } }
    }
}

internal object ToolPolicy {
    fun needsConfirmation(tool: Tool, settings: AgentSettings = AgentSettings()): Boolean =
        tool.effect == Effect.LeavesPhone || settings.askAll || tool.name in settings.alwaysAsk
}
