package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

/** Play, pause and skip on whatever is playing, through the media session the island already shows. */
internal object MediaTool : Tool(
    name = "media",
    summary = "Controls what is playing: play, pause, next track, previous track.",
    params = listOf(Param("action", Kind.Enum, choices = listOf("play", "pause", "next", "previous"))),
    effect = Effect.Local,
    examples = listOf("pause the music", "skip this song", "play", "go back a track"),
) {
    private val said = mapOf("play" to "Playing", "pause" to "Paused", "next" to "Skipped to the next track", "previous" to "Back to the previous track")
    private val reverse = mapOf("play" to "pause", "pause" to "play", "next" to "previous", "previous" to "next")

    override fun describe(params: Params, env: ToolEnv): String? = params.text("action")?.let { "Media: $it" }

    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val action = params.text("action") ?: return ToolResult(false, "I didn't catch what to do with the music.")
        if (!env.media(action)) return ToolResult(false, "Nothing is playing that I can control.")
        val back = reverse.getValue(action)
        return ToolResult(true, said.getValue(action), undo = { ToolResult(env.media(back), said.getValue(back)) })
    }
}
