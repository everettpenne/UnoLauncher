package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

internal object TimerTool : Tool(
    name = "timer",
    summary = "Starts a countdown timer that the island shows. length: how long, in the person's words.",
    params = listOf(Param("length", Kind.Text, maxLength = 60), Param("label", Kind.Text, required = false, maxLength = 60)),
    effect = Effect.Local,
    examples = listOf("count down 15 minutes", "egg timer 7 minutes", "set a timer for an hour and a half"),
) {
    private const val ASK = "How long should the timer be?"
    private val timeLike = Regex("""\d|\b(am|pm|tomorrow|today|tonight|morning|evening|minutes?|hours?|seconds?|timer|reminder)\b""", RegexOption.IGNORE_CASE)

    private fun seconds(params: Params): Int? = params.text("length")?.let { SpokenTime.seconds(it) }?.takeIf { it in 1..86_400 }?.toInt()

    /** The length must be one the person gave: "set a timer for the cake" came back from the model as an hour. */
    override fun missing(params: Params, said: String): Missing? {
        val seconds = seconds(params) ?: return Missing(ASK, "length")
        return if (SpokenTime.saidWhole(said, seconds.toLong()) { SpokenTime.seconds(it) }) null else Missing(ASK, "length")
    }

    override fun mended(params: Params, said: String): Params {
        if (missing(params, said) == null) return params
        val length = SpokenTime.theOne(said) { SpokenTime.seconds(it)?.takeIf { s -> s in 1..86_400 } } ?: return params
        return Params(params.values + ("length" to length))
    }

    override fun describe(params: Params, env: ToolEnv): String? = seconds(params)?.let { "Start a timer for ${Speak.duration(it)}" }

    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val seconds = seconds(params) ?: return ToolResult(false, "I didn't catch how long the timer should be.")
        env.startTimer(seconds.toLong())
        // A label that only says a time again ("6am tomorrow", "30 minutes") is not a name for the timer, and reads as if it were one.
        val label = params.text("label")?.takeUnless { timeLike.containsMatchIn(it) }?.let { " ($it)" }.orEmpty()
        return ToolResult(true, "Timer set for ${Speak.duration(seconds)}$label",
            undo = { env.cancelTimer(); ToolResult(true, "Timer cancelled") })
    }
}
