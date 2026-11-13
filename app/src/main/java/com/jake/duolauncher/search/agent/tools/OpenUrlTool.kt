package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

internal object OpenUrlTool : Tool(
    name = "open_url",
    summary = "Opens a web address in the browser.",
    params = listOf(Param("url", Kind.Text, maxLength = 500)),
    effect = Effect.Local,
) {
    override fun validate(raw: Map<String, String>): Validation<Params> {
        val base = super.validate(raw)
        val params = when (base) {
            is Validation.Rejected -> return base
            is Validation.Ok -> base.value
        }
        val url = params.text("url").orEmpty()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return Validation.Rejected("url must start with http:// or https://")
        return base
    }

    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val url = params.text("url") ?: return ToolResult(false, "I didn't catch the address.")
        env.openUrl(url)
        return ToolResult(true, "Opened $url")
    }
}
