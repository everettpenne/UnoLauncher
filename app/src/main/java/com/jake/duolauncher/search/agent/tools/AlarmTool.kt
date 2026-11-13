package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

internal object AlarmTool : Tool(
    name = "alarm",
    summary = "Sets an alarm: either at a clock time, or a number of minutes from now.",
    params = listOf(
        Param("at", Kind.Text, required = false, maxLength = 5),
        Param("in_minutes", Kind.Int, required = false, min = 1, max = 1440),
    ),
    effect = Effect.Local,
    examples = listOf("alarm for 7:15", "wake me up in 45 minutes"),
) {
    private val clockTime = Regex("""^([01]?\d|2[0-3]):[0-5]\d$""")

    override fun validate(raw: Map<String, String>): Validation<Params> {
        val base = super.validate(raw)
        val params = when (base) {
            is Validation.Rejected -> return base
            is Validation.Ok -> base.value
        }
        val at = params.text("at")
        val inMinutes = params.int("in_minutes")
        if ((at == null) == (inMinutes == null)) {
            return Validation.Rejected("alarm needs either at (a clock time) or in_minutes, and not both")
        }
        if (at != null && !clockTime.matches(at)) return Validation.Rejected("at must be a 24-hour time like 06:45")
        return base
    }

    override fun describe(params: Params, env: ToolEnv): String? =
        params.text("at")?.let { "Open Clock to set an alarm for $it" } ?: params.int("in_minutes")?.let { "Open Clock to set an alarm in $it minutes" }

    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val at = params.text("at")
        val inMinutes = params.int("in_minutes")
        if (at != null) {
            val (hour, minute) = at.substringBefore(':').toInt() to at.substringAfter(':').toInt()
            env.openAlarm(hour, minute, null)
            return ToolResult(true, "Clock is open at ${Speak.clock(hour, minute)}, tap Save")
        }
        env.openAlarm(null, null, inMinutes)
        return ToolResult(true, "Clock is open for an alarm in $inMinutes minutes, tap Save")
    }
}
