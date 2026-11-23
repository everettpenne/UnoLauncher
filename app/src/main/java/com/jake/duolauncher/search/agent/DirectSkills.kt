package com.jake.duolauncher.search.agent

/**
 * Things the assistant can do that the launcher recognises by itself: no model is asked, so they are instant, and they are not in the
 * catalogue the planner is sent ([ToolRegistry.builtIn]), so the planner trained on that catalogue sees exactly what it was trained on.
 * A new one needs no training, only a recogniser.
 *
 * A recogniser is strict: it answers only when the whole request is one it understands, every word of it, and otherwise says nothing
 * and the request goes to the planner as before. A wrong match would act at once, so a near miss must be a miss
 * (`DirectSkillsTest` runs every phrase the planner is evaluated on through [read] and expects silence).
 *
 * What it returns is an ordinary plan, run by the ordinary loop: missing values are asked for, anything that leaves the phone waits
 * for a yes, Undo works, and the turn goes into the conversation and the assistant log (route `direct`).
 */
internal object DirectSkills {
    val tools: List<Tool> = listOf(ReminderTool, StyleTool, UnoSettingsTool)

    private val polite = Regex("""^(?:(?:please|pls|hey|ok|okay|hi|qwen|uno)[\s,]+)+""", RegexOption.IGNORE_CASE)
    private val politeEnd = Regex("""[\s,]+(?:please|pls|thanks|thank you)$""", RegexOption.IGNORE_CASE)
    private fun plain(query: String): String =
        politeEnd.replace(polite.replace(query.trim().trimEnd('.', '!'), ""), "").replace(Regex("""\s+"""), " ").trim()

    /** The plan for [query], or null when it is not wholly one of these. */
    fun read(query: String): Plan.Steps? {
        val q = plain(query)
        if (q.isEmpty() || q.contains('\n')) return null
        val step = reminder(q) ?: style(q) ?: settings(q) ?: return null
        return Plan.Steps(listOf(step))
    }

    // ---- "remind me to call Sam at 5", "remind me in 20 minutes to take the bread out" -------------------------------------------

    private const val WHEN = """(?:in\s+.+|(?:at\s+)?\d{1,2}(?::\d{2})?\s*(?:am|pm|a\.m\.|p\.m\.)?|at\s+(?:noon|midnight|half past \w+|quarter (?:past|to) \w+))"""
    private val remindThenWhen = Regex("""^remind me (?:to |about |that )?(.+?) ($WHEN)$""", RegexOption.IGNORE_CASE)
    private val remindWhenThen = Regex("""^remind me ($WHEN) (?:to |about |that )(.+)$""", RegexOption.IGNORE_CASE)
    /** Words that mean the reminder is for another day, which an alarm for a time today cannot be: left to the planner's calendar. */
    private val anotherDay = Regex("""\b(tomorrow|tonight|today|monday|tuesday|wednesday|thursday|friday|saturday|sunday|next|week|month|every|daily)\b""", RegexOption.IGNORE_CASE)

    private fun reminder(q: String): Step? {
        if (anotherDay.containsMatchIn(q)) return null
        val (what, at) = remindThenWhen.matchEntire(q)?.let { it.groupValues[1] to it.groupValues[2] }
            ?: remindWhenThen.matchEntire(q)?.let { it.groupValues[2] to it.groupValues[1] } ?: return null
        val about = what.trim()
        val time = at.trim().removePrefix("at ").trim()
        // "remind me at 5" has a time and nothing to be reminded of: what it is about must be more than a joining word.
        if (about.lowercase() in setOf("at", "in", "on", "to", "about", "that", "me", "it", "this")) return null
        // The time must be one the parser reads whole, and what it is about must be words, not another time.
        if (about.length !in 2..80 || SpokenTime.alarm(time) == null || SpokenTime.alarm(about) != null) return null
        if (Regex("""\b(and|then)\b""", RegexOption.IGNORE_CASE).containsMatchIn(time)) return null
        return Step(ReminderTool, Params(mapOf("what" to about, "when" to time)))
    }

    // ---- "use the expressive style", "switch uno to calm" -------------------------------------------------------------------------

    private val styleA = Regex("""^(?:set|switch|change|make|put)\s+(?:the\s+)?(?:uno(?:'s)?\s+|launcher(?:'s)?\s+)?style\s+(?:to\s+)?(calm|balanced|expressive)$""", RegexOption.IGNORE_CASE)
    private val styleB = Regex("""^(?:use|switch to|set|go)\s+(?:the\s+)?(calm|balanced|expressive)\s+(?:style|look|theme)$""", RegexOption.IGNORE_CASE)
    private val styleC = Regex("""^(?:set|switch|change|make|put)\s+(?:uno|the launcher)\s+(?:to\s+)?(calm|balanced|expressive)$""", RegexOption.IGNORE_CASE)

    private fun style(q: String): Step? {
        val level = (styleA.matchEntire(q) ?: styleB.matchEntire(q) ?: styleC.matchEntire(q))?.groupValues?.get(1) ?: return null
        return Step(StyleTool, Params(mapOf("level" to level.lowercase())))
    }

    // ---- "open uno settings", "open the launcher's keyboard settings" --------------------------------------------------------------

    private val unoSettings = Regex("""^(?:open|show|go to)\s+(?:the\s+)?(?:uno(?:'s)?|launcher(?:'s)?|uno launcher(?:'s)?)\s+(keyboard\s+)?settings$""", RegexOption.IGNORE_CASE)

    private fun settings(q: String): Step? {
        val m = unoSettings.matchEntire(q) ?: return null
        return Step(UnoSettingsTool, Params(mapOf("page" to if (m.groupValues[1].isNotBlank()) "keyboard" else "all")))
    }
}

/** A reminder: an alarm in the clock app, named for what it is about, so it rings with its reason on it. The clock app opens with it
 *  filled in and is the one that keeps and rings it (the launcher has no way of its own to wake at a time). */
internal object ReminderTool : Tool(
    name = "reminder",
    summary = "Sets a named alarm as a reminder. what: what it is about. when: a clock time today, or how long from now.",
    params = listOf(Param("what", Kind.Text, maxLength = 80), Param("when", Kind.Text, maxLength = 60)),
    effect = Effect.Local,
    examples = listOf("remind me to call Sam at 5", "remind me in 20 minutes to take the bread out"),
) {
    private val halfOfDay = Regex("""(?<![a-z])(am|pm|a\.m\.?|p\.m\.?)(?![a-z])|\b(noon|midnight|morning|evening|night)\b|\b(?:1[3-9]|2[0-3]|0\d):\d{2}""", RegexOption.IGNORE_CASE)

    /**
     * When it is for. "At 5" with no morning or afternoon said is the next 5 there is: 5 in the afternoon when asked at two, 5 in the
     * morning when asked at nine at night. (An alarm "for 5" is the morning's; a reminder is for the next time that hour comes.)
     */
    private fun target(params: Params, now: java.time.LocalDateTime? = null): SpokenTime.Alarm? {
        val text = params.text("when") ?: return null
        val t = SpokenTime.alarm(text) ?: return null
        if (now == null || t !is SpokenTime.Alarm.At || halfOfDay.containsMatchIn(text) || t.hour !in 1..12) return t
        val morning = t.hour % 12; val evening = morning + 12
        val nowMinutes = now.hour * 60 + now.minute
        val hour = listOf(morning, evening).firstOrNull { it * 60 + t.minute > nowMinutes } ?: morning
        return SpokenTime.Alarm.At(hour, t.minute)
    }

    override fun missing(params: Params, said: String): Missing? = when {
        params.text("what") == null -> Missing("What should I remind you about?", "what")
        target(params) == null -> Missing("When should I remind you?", "when")
        else -> null
    }

    override fun describe(params: Params, env: ToolEnv): String? {
        val what = params.text("what") ?: return null
        return when (val t = target(params, env.now())) {
            is SpokenTime.Alarm.At -> "Open Clock to set “$what” for ${Speak.clock(t.hour, t.minute)}"
            is SpokenTime.Alarm.In -> "Open Clock to set “$what” in ${Speak.duration(t.minutes * 60)}"
            null -> null
        }
    }

    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val what = params.text("what") ?: return ToolResult(false, "I didn't catch what to remind you about.")
        return when (val t = target(params, env.now())) {
            is SpokenTime.Alarm.At -> { env.openAlarm(t.hour, t.minute, null, what); ToolResult(true, "Clock is open with “$what” at ${Speak.clock(t.hour, t.minute)}, tap Save") }
            is SpokenTime.Alarm.In -> { env.openAlarm(null, null, t.minutes, what); ToolResult(true, "Clock is open with “$what” in ${Speak.duration(t.minutes * 60)}, tap Save") }
            null -> ToolResult(false, "I didn't catch when to remind you.")
        }
    }
}

/** The launcher's own Style (Appearance › Style): Calm, Balanced or Expressive. Taken back with Undo. */
internal object StyleTool : Tool(
    name = "uno_style",
    summary = "Sets Uno's Style. level: calm, balanced or expressive.",
    params = listOf(Param("level", Kind.Text, maxLength = 12)),
    effect = Effect.Local,
    examples = listOf("use the expressive style", "set the style to calm"),
) {
    private val levels = listOf("calm", "balanced", "expressive")
    private fun level(params: Params) = params.text("level")?.lowercase()?.takeIf { it in levels }

    override fun missing(params: Params, said: String): Missing? = if (level(params) == null) Missing("Calm, Balanced or Expressive?", "level") else null
    override fun describe(params: Params, env: ToolEnv): String? = level(params)?.let { "Set Uno's Style to ${it.replaceFirstChar(Char::uppercase)}" }

    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val level = level(params) ?: return ToolResult(false, "The styles are Calm, Balanced and Expressive.")
        val name = level.replaceFirstChar(Char::uppercase)
        val was = env.style()
        if (was == level) return ToolResult(true, "Uno's Style is already $name")
        if (!env.setStyle(level)) return ToolResult(false, "I couldn't change Uno's Style from here.")
        return ToolResult(true, "Uno's Style is $name", undo = was?.let { before ->
            { env.setStyle(before); ToolResult(true, "Uno's Style is ${before.replaceFirstChar(Char::uppercase)} again") }
        })
    }
}

/** Opens the launcher's own settings, or its keyboard's. */
internal object UnoSettingsTool : Tool(
    name = "uno_settings",
    summary = "Opens Uno's settings. page: all or keyboard.",
    params = listOf(Param("page", Kind.Text, maxLength = 12)),
    effect = Effect.Local,
    examples = listOf("open uno settings", "open the launcher's keyboard settings"),
) {
    private fun page(params: Params) = params.text("page")?.lowercase()?.takeIf { it == "all" || it == "keyboard" }
    override fun describe(params: Params, env: ToolEnv): String? = page(params)?.let { if (it == "keyboard") "Open Uno's keyboard settings" else "Open Uno's settings" }
    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val page = page(params) ?: return ToolResult(false, "I can open Uno's settings, or its keyboard's.")
        return if (env.openUnoSettings(page)) ToolResult(true, if (page == "keyboard") "Uno's keyboard settings are open" else "Uno's settings are open")
            else ToolResult(false, "I couldn't open Uno's settings from here.")
    }
}
