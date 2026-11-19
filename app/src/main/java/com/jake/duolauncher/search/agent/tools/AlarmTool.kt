package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

internal object AlarmTool : Tool(
    name = "alarm",
    summary = "Sets an alarm. when: a clock time or how long from now, in the person's words.",
    params = listOf(Param("when", Kind.Text, maxLength = 60)),
    effect = Effect.Local,
    examples = listOf("alarm for 7:15", "wake me up in 45 minutes", "set an alarm for half past six"),
) {
    private const val ASK = "What time should the alarm be for?"

    private fun target(params: Params): SpokenTime.Alarm? = params.text("when")?.let { SpokenTime.alarm(it) }

    /** The time must be one the person gave. */
    override fun missing(params: Params, said: String): Missing? {
        val target = target(params) ?: return Missing(ASK, "when")
        return if (SpokenTime.saidWhole(said, target) { SpokenTime.alarm(it) }) null else Missing(ASK, "when")
    }

    override fun mended(params: Params, said: String): Params {
        if (missing(params, said) == null) return params
        val time = SpokenTime.theOne(said) { SpokenTime.alarm(it) } ?: return params
        return Params(params.values + ("when" to time))
    }

    override fun describe(params: Params, env: ToolEnv): String? = when (val t = target(params)) {
        is SpokenTime.Alarm.At -> "Open Clock to set an alarm for ${Speak.clock(t.hour, t.minute)}"
        is SpokenTime.Alarm.In -> "Open Clock to set an alarm in ${Speak.duration(t.minutes * 60)}"
        null -> null
    }

    override suspend fun execute(params: Params, env: ToolEnv): ToolResult = when (val t = target(params)) {
        is SpokenTime.Alarm.At -> {
            env.openAlarm(t.hour, t.minute, null)
            ToolResult(true, "Clock is open at ${Speak.clock(t.hour, t.minute)}, tap Save")
        }
        is SpokenTime.Alarm.In -> {
            env.openAlarm(null, null, t.minutes)
            ToolResult(true, "Clock is open for an alarm in ${Speak.duration(t.minutes * 60)}, tap Save")
        }
        null -> ToolResult(false, "I didn't catch what time the alarm is for.")
    }
}
