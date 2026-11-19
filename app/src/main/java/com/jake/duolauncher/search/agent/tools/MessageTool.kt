package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

internal object MessageTool : Tool(
    name = "message",
    summary = "Opens the messages app at a phone number, with the words typed in if any were given. It does not send anything.",
    params = listOf(Param("number", Kind.Text, maxLength = 40), Param("text", Kind.Text, required = false, maxLength = ReplyLogic.MAX_TEXT)),
    effect = Effect.Local,
    examples = listOf("send a text to 555 0142", "sms 555 0177 saying on my way", "message 020 7946 0000"),
) {
    private val named = Regex("""\b(?:tell|text|message|sms|ask|to)\s+([A-Z][\p{L}'-]+)""")

    /** The number must be one the person gave, or one Uno found for a name they gave. */
    override fun invented(params: Params, said: String): String? = missing(params, said)?.question

    override fun missing(params: Params, said: String): Missing? {
        val digits = params.text("number").orEmpty().filter { it.isDigit() }
        if (digits.length >= 3 && said.filter { it.isDigit() }.contains(digits)) return null
        val who = named.find(said.lineSequence().firstOrNull().orEmpty())?.groupValues?.get(1)
        return Missing(if (who != null) "I don't have a number for $who. What is it?" else "What number should I message?", "number")
    }

    override fun describe(params: Params, env: ToolEnv): String? {
        val number = params.text("number") ?: return null
        return params.text("text")?.let { "Open Messages to $number with “$it”" } ?: "Open Messages to $number"
    }

    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val number = params.text("number") ?: return ToolResult(false, "I didn't catch the number.")
        val text = params.text("text").orEmpty()
        env.openMessage(number, text)
        return ToolResult(true, "Messages open with $number — press send there to send it")
    }
}
