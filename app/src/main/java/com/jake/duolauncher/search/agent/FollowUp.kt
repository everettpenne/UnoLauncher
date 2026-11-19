package com.jake.duolauncher.search.agent

/** What the assistant last did, kept for a few minutes so the next words can be about it. */
internal class LastTurn(val request: String, val steps: List<Step>, val summary: String, val atMs: Long, val executor: Executor)

/**
 * Words that follow on from what was just done: "undo that", "again", "make it 15", "and also turn on focus". Reading them is the
 * launcher's work where it can be: taking something back, doing it again and changing one value need no model, so they are instant and
 * cannot be planned wrongly. Anything else that leans on the last task goes to the planner with that task beside it. Pure, so it is
 * unit-tested.
 */
internal object FollowUp {
    /** How long the last task can still be followed up. */
    const val WINDOW_MS = 3 * 60_000L

    sealed interface Kind {
        data object Undo : Kind
        data object Again : Kind
        /** The same task with a value changed: the plan to run, and the words that carry the new value. */
        data class Amend(val plan: Plan.Steps, val said: String) : Kind
        /** It leans on the last task, and the planner has to work out how. */
        data object Refers : Kind
        data object None : Kind
    }

    private val polite = Regex("""^(?:(?:please|pls|hey|ok|okay|oh|hi|qwen|uno)[\s,]+)+|[\s,]+(?:please|thanks|thank you|cheers)[\s.!]*$""", RegexOption.IGNORE_CASE)
    private val undo = Regex("""^(?:undo|undo (?:that|it|this)|cancel (?:that|it)|never ?mind|take (?:that|it) back|scrap (?:that|it)|forget (?:that|it)|no,? (?:don'?t|stop)|undo the last one)$""", RegexOption.IGNORE_CASE)
    private val again = Regex("""^(?:again|(?:do|run|play) (?:that|it) again|same again|once more|one more time|repeat (?:that|it)|redo (?:that|it))$""", RegexOption.IGNORE_CASE)
    private val lead = Regex("""^(?:and|also|then|plus|actually|no|wait|sorry|instead|make (?:it|that)|change (?:it|that)(?: to)?|set (?:it|that)(?: to| for)?|move (?:it|that)(?: to)?|i meant|i mean|not that|rather)\b[\s,]*""", RegexOption.IGNORE_CASE)
    private val leans = Regex("""\b(it|that|those|them|instead|too|as well|same)\b""", RegexOption.IGNORE_CASE)
    private val question = Regex("""^(who|what|when|where|why|how|which|whose|is|are|was|were|does|did|do (?:you|i|we|they))\b|\?$""", RegexOption.IGNORE_CASE)
    private val bareNumber = Regex("""^(\d{1,4})$""")
    private val unit = Regex("""\b(seconds?|secs?|minutes?|mins?|hours?|hrs?)\b""", RegexOption.IGNORE_CASE)
    private val NOW = java.time.LocalDateTime.of(2026, 1, 5, 9, 0)

    private fun tidy(query: String): String {
        var t = query.trim()
        while (true) { val cut = polite.replace(t, "").trim(); if (cut == t) break; t = cut }
        return t.trimEnd('.', '!')
    }

    /** What [query] is to the task in [last], if anything. */
    fun read(query: String, last: List<Step>): Kind {
        val q = tidy(query)
        if (q.isEmpty() || last.isEmpty()) return Kind.None
        if (undo.matches(q)) return Kind.Undo
        if (again.matches(q)) return Kind.Again
        if (question.containsMatchIn(q)) return Kind.None
        var rest = q
        var led = false
        while (true) { val cut = lead.replace(rest, "").trim(); if (cut == rest) break; rest = cut; led = true }
        amended(last, rest, led)?.let { return it }
        // Naming another kind of task outright is a new request, unless it leans on the last one in so many words ("and also…").
        return if (led || leans.containsMatchIn(q)) Kind.Refers else Kind.None
    }

    /**
     * The last task with one value changed, when it was one step whose value [rest] gives: a new length for a timer, a new time for an
     * alarm, a new start or length for an event, a new level for the volume. A bare number takes the unit the last value had ("make it
     * 15" after ten minutes is fifteen minutes). Null when the words name some other kind of task, or give nothing to change.
     */
    private fun amended(last: List<Step>, rest: String, led: Boolean): Kind.Amend? {
        val step = last.singleOrNull() ?: return null
        val named = RequestKinds.named(rest)
        if (named.isNotEmpty() && named.none { step.tool.name in it }) return null
        val values = step.params.values
        // Without a lead ("make it…") the words must be the value and nothing else, or a request of the same kind.
        fun valueOnly(value: String) = led || named.isNotEmpty() || rest.equals(value, ignoreCase = true) ||
            rest.removePrefix("for ").removePrefix("to ").removePrefix("at ").equals(value, ignoreCase = true)
        fun with(name: String, value: String, said: String = value) =
            Kind.Amend(Plan.Steps(listOf(Step(step.tool, Params(values + (name to value))))), said)
        return when (step.tool.name) {
            "timer" -> {
                val bare = bareNumber.matchEntire(SpokenTime.digits(rest).removePrefix("for ").removePrefix("to ").trim())?.groupValues?.get(1)
                val length = if (bare != null) "$bare ${unit.find(values["length"].orEmpty())?.value?.let { if (it.endsWith("s") || bare == "1") it else it + "s" } ?: "minutes"}"
                    else SpokenTime.theOne(rest) { SpokenTime.seconds(it)?.takeIf { s -> s in 1..86_400 } }
                length?.takeIf { bare != null || valueOnly(it) }?.let { with("length", it) }
            }
            "alarm" -> SpokenTime.theOne(rest) { SpokenTime.alarm(it) }?.takeIf { valueOnly(it) }?.let { with("when", it) }
            "calendar_event" -> {
                val start = SpokenTime.theOne(rest) { SpokenTime.start(it, NOW) }
                val lengths = SpokenTime.spoken(rest) { SpokenTime.seconds(it) }.map { it.first }.filter { !it.startsWith("in ", ignoreCase = true) }
                val length = lengths.singleOrNull()?.removePrefix("for ")
                when {
                    start != null && (led || rest.startsWith(start, ignoreCase = true) || valueOnly(start)) ->
                        Kind.Amend(Plan.Steps(listOf(Step(step.tool, Params(values + ("start" to start) + (if (length != null) mapOf("length" to length) else emptyMap()))))), rest)
                    start == null && length != null && valueOnly(lengths.single()) -> with("length", length, "for $length")
                    else -> null
                }
            }
            "volume" -> rest.removePrefix("to ").trim().takeIf { com.jake.duolauncher.search.agent.tools.VolumeTool.target(it, 50) != null }?.let { with("level", it) }
            else -> null
        }
    }

    /** The last task as the planner is shown it, for words that lean on it: what was asked, and each step as the tool and its values. */
    fun block(last: LastTurn): String = buildString {
        append("Just done, for \"").append(ReplyLogic.clean(last.request.lineSequence().first())).append("\": ")
        append(last.steps.joinToString("; ") { step ->
            step.tool.name + "(" + step.params.values.filter { (k, v) -> v.isNotBlank() && !k.startsWith("_") }.entries.joinToString(", ") { "${it.key}=${it.value}" } + ")"
        })
    }
}
