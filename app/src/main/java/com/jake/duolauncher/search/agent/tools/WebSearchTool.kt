package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

internal object WebSearchTool : Tool(
    name = "web_search",
    summary = "Hands a query to the browser's own search.",
    params = listOf(Param("query", Kind.Text, maxLength = 200)),
    effect = Effect.Local,
    examples = listOf("search for train times to York", "look online for a banana bread recipe"),
) {
    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val query = params.text("query") ?: return ToolResult(false, "There was nothing to search for.")
        env.openWebSearch(query)
        return ToolResult(true, "Searching the web for \"$query\"")
    }
}
