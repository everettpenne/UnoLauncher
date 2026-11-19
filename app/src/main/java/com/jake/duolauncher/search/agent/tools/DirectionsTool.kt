package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

/** Hands a place to the maps app. Nothing is sent by Uno; the maps app does its own looking up. */
internal object DirectionsTool : Tool(
    name = "directions",
    summary = "Opens the maps app at a place the person named.",
    params = listOf(Param("place", Kind.Text, maxLength = 120)),
    effect = Effect.Local,
    examples = listOf("directions to the airport", "navigate to 12 Mill Lane", "how do I get to Central Station"),
) {
    private val word = Regex("""[\p{L}\p{N}]{3,}""")

    /** The place must be in the person's own words: nothing from the phone's facts is handed to a maps app. */
    override fun missing(params: Params, said: String): Missing? {
        val place = params.text("place") ?: return Missing("Where to?", "place")
        val request = said.lineSequence().firstOrNull().orEmpty().lowercase() + "\n" + said.substringAfter("Answer:", "").lowercase()
        val words = word.findAll(place.lowercase()).map { it.value }.toList()
        return if (words.isNotEmpty() && words.all { it in request }) null else Missing("Where to?", "place")
    }

    override fun describe(params: Params, env: ToolEnv): String? = params.text("place")?.let { "Open maps at “$it”" }

    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val place = params.text("place") ?: return ToolResult(false, "I didn't catch where to.")
        env.openDirections(place)
        return ToolResult(true, "Maps open at “$place”")
    }
}
