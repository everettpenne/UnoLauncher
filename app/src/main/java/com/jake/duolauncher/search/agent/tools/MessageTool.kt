package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

internal object MessageTool : Tool(
    name = "message",
    summary = "Opens the messages app with a number typed in. It does not send anything.",
    params = listOf(Param("number", Kind.Text, maxLength = 40)),
    effect = Effect.Local,
) {
    override fun validate(raw: Map<String, String>): Validation<Params> {
        val base = super.validate(raw)
        val params = when (base) {
            is Validation.Rejected -> return base
            is Validation.Ok -> base.value
        }
        if (params.text("number").orEmpty().count { it.isDigit() } < 3) return Validation.Rejected("that doesn't look like a phone number")
        return base
    }

    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val number = params.text("number") ?: return ToolResult(false, "I didn't catch the number.")
        env.openMessages(number)
        return ToolResult(true, "Messages open with $number — press send there to send it")
    }
}
