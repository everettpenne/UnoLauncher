package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

internal object DialTool : Tool(
    name = "dial",
    summary = "Opens the dialer with a number typed in. It does not place the call.",
    params = listOf(Param("number", Kind.Text, maxLength = 40)),
    effect = Effect.Local,
) {
    override fun validate(raw: Map<String, String>): Validation<Params> {
        val base = super.validate(raw)
        val params = when (base) {
            is Validation.Rejected -> return base
            is Validation.Ok -> base.value
        }
        val digits = params.text("number").orEmpty().count { it.isDigit() }
        if (digits < 3) return Validation.Rejected("that doesn't look like a phone number")
        return base
    }

    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val number = params.text("number") ?: return ToolResult(false, "I didn't catch the number.")
        env.openDialer(number)
        return ToolResult(true, "Dialer open with $number")
    }
}
