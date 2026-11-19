package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*
import java.time.Duration

internal object CalendarTool : Tool(
    name = "calendar_event",
    summary = "Opens a calendar event, prefilled. start: when it begins, in the person's words. length: how long it lasts, if they said.",
    params = listOf(
        Param("title", Kind.Text, maxLength = 120),
        Param("start", Kind.Text, maxLength = 60),
        Param("length", Kind.Text, required = false, maxLength = 60),
    ),
    effect = Effect.Local,
    examples = listOf("haircut in 2 hours for 45 minutes", "meeting with Sam tomorrow at 3pm", "add lunch to my calendar friday at noon for an hour"),
) {
    private const val DEFAULT_MINUTES = 60
    private const val MAX_AHEAD_MINUTES = 366 * 24 * 60

    private fun whenAsk(params: Params) = "When should ${params.text("title")?.let { "“$it”" } ?: "it"} start?"

    /** Minutes from now until the start, or null when the start is not a time, is past, or is more than a year off. */
    private fun inMinutes(params: Params, env: ToolEnv): Int? {
        val now = env.now()
        val start = params.text("start")?.let { SpokenTime.start(it, now) } ?: return null
        return Duration.between(now, start).toMinutes().takeIf { it in 0..MAX_AHEAD_MINUTES }?.toInt()
    }

    private fun minutes(params: Params): Int? {
        val said = params.text("length") ?: return DEFAULT_MINUTES
        return SpokenTime.seconds(said)?.let { (it / 60).toInt() }?.takeIf { it in 5..1440 }
    }

    /** The start must be one the person gave: "add a haircut to my calendar" came back from the model as starting now. */
    override fun missing(params: Params, said: String): Missing? {
        val given = params.text("start") ?: return Missing(whenAsk(params), "start")
        // Any fixed "now" serves: only whether the same words are in the request matters.
        val now = java.time.LocalDateTime.of(2026, 1, 5, 9, 0)
        val start = SpokenTime.start(given, now) ?: return Missing(whenAsk(params), "start")
        if (!SpokenTime.saidWhole(said, start) { SpokenTime.start(it, now) }) return Missing(whenAsk(params), "start")
        val length = params.text("length") ?: return null
        val seconds = SpokenTime.seconds(length) ?: return Missing("How long should it last?", "length")
        return if (SpokenTime.saidWhole(said, seconds) { SpokenTime.seconds(it) }) null else Missing("How long should it last?", "length")
    }

    override fun mended(params: Params, said: String): Params {
        var values = params.values
        val now = java.time.LocalDateTime.of(2026, 1, 5, 9, 0)
        val start = params.text("start")?.let { SpokenTime.start(it, now) }
        if (start == null || !SpokenTime.saidWhole(said, start) { SpokenTime.start(it, now) }) {
            SpokenTime.theOne(said) { SpokenTime.start(it, now) }?.let { values = values + ("start" to it) }
        }
        val length = params.text("length")
        val seconds = length?.let { SpokenTime.seconds(it) }
        if (length != null && (seconds == null || !SpokenTime.saidWhole(said, seconds) { SpokenTime.seconds(it) })) {
            // A length the person did not give is theirs if they gave exactly one ("for an hour"); otherwise it is left out, for the hour an event gets by default.
            // "in 2 hours" is when it starts, not how long it lasts.
            val lengths = SpokenTime.spoken(said) { SpokenTime.seconds(it) }.map { it.first }.filter { !it.startsWith("in ", ignoreCase = true) }
            val theirs = lengths.singleOrNull() ?: lengths.singleOrNull { it.startsWith("for ", ignoreCase = true) }
            values = values + ("length" to theirs.orEmpty().removePrefix("for ").removePrefix("For "))
        }
        return Params(values)
    }

    override fun problem(params: Params, env: ToolEnv): String? = when {
        inMinutes(params, env) == null -> whenAsk(params)
        minutes(params) == null -> "How long should it last? I can do 5 minutes to a day."
        else -> null
    }

    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val title = params.text("title") ?: return ToolResult(false, "I didn't catch what the event is called.")
        val inMinutes = inMinutes(params, env) ?: return ToolResult(false, "I didn't catch when it should start.")
        val duration = minutes(params) ?: return ToolResult(false, "I didn't catch how long it should last.")
        env.openCalendar(title, inMinutes, duration)
        val whenText = when {
            inMinutes == 0 -> "now"
            inMinutes < 24 * 60 && SpokenTime.digits(params.text("start").orEmpty()).startsWith("in ") -> "in ${Speak.duration(inMinutes * 60)}"
            else -> Speak.dayTime(env.now().plusMinutes(inMinutes.toLong()), env.now())
        }
        return ToolResult(true, "Calendar open with \"$title\" $whenText for ${Speak.duration(duration * 60)} — tap Save to add it")
    }
}
