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
    private const val AT = "_at"
    private const val EVENT = "_event"
    private const val LEAD = "_lead"

    /** A time worked out from the calendar ([bind]) is the time; otherwise the words are read as a clock time or "in 45 minutes". */
    private fun target(params: Params): SpokenTime.Alarm? = (params.values[AT] ?: params.text("when"))?.let { SpokenTime.alarm(it) }

    /** "30 minutes before my first meeting", when that is what the step says. */
    private fun event(params: Params): EventTime.Ref? = params.text("when")?.let { EventTime.parse(it) }

    /** The time must be one the person gave: a clock time, a length from now, or something in their calendar that they named. */
    override fun missing(params: Params, said: String): Missing? {
        event(params)?.let { return if (EventTime.said(said, it)) null else Missing(ASK, "when") }
        val target = target(params) ?: return Missing(ASK, "when")
        return if (SpokenTime.saidWhole(said, target) { SpokenTime.alarm(it) }) null else Missing(ASK, "when")
    }

    override fun mended(params: Params, said: String): Params {
        if (missing(params, said) == null) return params
        val time = SpokenTime.theOne(said) { SpokenTime.alarm(it) } ?: return params
        return Params(params.values + ("when" to time))
    }

    /** A time given by the calendar is looked up once, here, so what is shown and what is set are the same event. */
    override fun bind(params: Params, env: ToolEnv): Params {
        val ref = event(params) ?: return params
        // Looked up afresh each time: a time found for an earlier run of the same step is not kept if the event has gone.
        val asked = Params(params.values - setOf(AT, EVENT, LEAD))
        if (Capability.CalendarRead !in env.capabilities()) return asked
        val found = EventTime.resolve(ref, env.events(), env.now()) as? EventTime.Found.At ?: return asked
        return Params(asked.values + mapOf(AT to "%02d:%02d".format(found.alarm.hour, found.alarm.minute), EVENT to found.event.title,
            LEAD to Speak.duration(ref.minutes * 60)))
    }

    override fun problem(params: Params, env: ToolEnv): String? {
        val ref = event(params) ?: return null
        if (params.values[AT] != null) return null
        if (Capability.CalendarRead !in env.capabilities()) return "I can't see your calendar. Allow calendar access for Uno, or tell me the time."
        return (EventTime.resolve(ref, env.events(), env.now()) as? EventTime.Found.None)?.why
    }

    private fun because(params: Params): String = params.values[EVENT]?.let { ", ${params.values[LEAD]} before “$it”" }.orEmpty()

    override fun describe(params: Params, env: ToolEnv): String? = when (val t = target(params)) {
        is SpokenTime.Alarm.At -> "Open Clock to set an alarm for ${Speak.clock(t.hour, t.minute)}${because(params)}"
        is SpokenTime.Alarm.In -> "Open Clock to set an alarm in ${Speak.duration(t.minutes * 60)}"
        null -> null
    }

    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        // Unbound (a plan run without the loop's binding): settle it now, by the same rules.
        val bound = if (event(params) != null && params.values[AT] == null) bind(params, env) else params
        if (event(bound) != null && bound.values[AT] == null) return ToolResult(false, problem(bound, env) ?: "I couldn't find that in your calendar.")
        return when (val t = target(bound)) {
            is SpokenTime.Alarm.At -> {
                // Named for what it is before, so the alarm says why it is ringing.
                env.openAlarm(t.hour, t.minute, null, bound.values[EVENT])
                ToolResult(true, "Clock is open at ${Speak.clock(t.hour, t.minute)}${because(bound)}, tap Save")
            }
            is SpokenTime.Alarm.In -> {
                env.openAlarm(null, null, t.minutes)
                ToolResult(true, "Clock is open for an alarm in ${Speak.duration(t.minutes * 60)}, tap Save")
            }
            null -> ToolResult(false, "I didn't catch what time the alarm is for.")
        }
    }
}
