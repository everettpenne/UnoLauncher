package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

/** Media volume: a step up or down, mute, or a percentage the person gave. */
internal object VolumeTool : Tool(
    name = "volume",
    summary = "Changes the media volume. level: up, down, mute, max, or a percentage the person said.",
    params = listOf(Param("level", Kind.Text, maxLength = 20)),
    effect = Effect.Local,
    examples = listOf("turn it up", "volume down", "mute the music", "volume to 40 percent"),
) {
    private const val STEP = 15
    private const val ASK = "How loud: up, down, mute, or a percentage?"
    private val percent = Regex("""^(\d{1,3})\s*(?:%|percent)?$""")

    /** The level to set, from where it is [now]; null when [level] is not one. */
    fun target(level: String, now: Int): Int? {
        val l = SpokenTime.digits(level).trim()
        return when (l) {
            "up", "louder", "higher" -> (now + STEP).coerceAtMost(100)
            "down", "quieter", "lower" -> (now - STEP).coerceAtLeast(0)
            "mute", "off", "silent" -> 0
            "max", "full", "maximum" -> 100
            "half" -> 50
            else -> percent.matchEntire(l)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..100 }
        }
    }

    /** A percentage must be one the person gave. */
    override fun missing(params: Params, said: String): Missing? {
        val level = params.text("level") ?: return Missing(ASK, "level")
        val n = percent.matchEntire(SpokenTime.digits(level).trim())?.groupValues?.get(1)
        if (n == null) return if (target(level, 50) == null) Missing(ASK, "level") else null
        return if (Regex("""\b$n\b""").containsMatchIn(SpokenTime.digits(said))) null else Missing(ASK, "level")
    }

    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val before = env.volume() ?: return ToolResult(false, "I couldn't read the volume.")
        val to = target(params.text("level").orEmpty(), before) ?: return ToolResult(false, "I didn't catch how loud.")
        if (!env.setVolume(to)) return ToolResult(false, "Android wouldn't let me change the volume.")
        return ToolResult(true, if (to == 0) "Muted" else "Volume at $to%",
            undo = { ToolResult(env.setVolume(before), "Volume back at $before%") })
    }
}
