package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

internal object DialTool : Tool(
    name = "dial",
    summary = "Opens the dialer with a number typed in. It does not place the call.",
    params = listOf(Param("number", Kind.Text, maxLength = 40)),
    effect = Effect.Local,
    examples = listOf("phone 555 0142", "ring 020 7946 0000"),
) {
    /** The number must be one the person gave, or one Uno found for a name they gave. */
    override fun invented(params: Params, said: String): String? = missing(params, said)?.question

    override fun missing(params: Params, said: String): Missing? {
        val digits = params.text("number").orEmpty().filter { it.isDigit() }
        return if (digits.length >= 3 && said.filter { it.isDigit() }.contains(digits)) null else Missing("What number should I call?", "number")
    }

    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val number = params.text("number") ?: return ToolResult(false, "I didn't catch the number.")
        env.openDialer(number)
        return ToolResult(true, "Dialer open with $number")
    }
}
