package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

internal object NoteTool : Tool(
    name = "note",
    summary = "Saves a note to the Note widget on Home.",
    params = listOf(Param("text", Kind.Text, maxLength = 400)),
    effect = Effect.Local,
    examples = listOf("remember that the door code is 4411", "note to self call the vet", "write down milk and eggs"),
) {
    /** "jot down the door code is 4411" is a note that the door code is 4411. */
    override fun mended(params: Params, said: String): Params {
        val text = params.text("text") ?: return params
        return Params(params.values + ("text" to LeadIn.stripped(text, said, listOf("jot down", "note down", "write down", "make a note of", "make a note that"))))
    }

    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val text = params.text("text") ?: return ToolResult(false, "There was nothing to write down.")
        val previous = env.writeNote(text) ?: return ToolResult(false, "There's no Note widget on Home to write to.")
        return ToolResult(true, "Note saved: $text", undo = { env.writeNote(previous); ToolResult(true, "Note restored") })
    }
}
