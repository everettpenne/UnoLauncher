package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

/** Adds a line to the Checklist widget on Home, as [NoteTool] writes the Note widget. */
internal object ChecklistTool : Tool(
    name = "checklist_add",
    summary = "Adds one item to the Checklist widget on Home (a shopping or to-do list).",
    params = listOf(Param("item", Kind.Text, maxLength = 80)),
    effect = Effect.Local,
    examples = listOf("add milk to my list", "put call the vet on my to-do list", "add batteries to the shopping list"),
) {
    override fun problem(params: Params, env: ToolEnv): String? =
        if (env.checklistSlot == null) "Add a Checklist widget to Home and I can keep a list there." else null

    override fun describe(params: Params, env: ToolEnv): String? = params.text("item")?.let { "Add “$it” to the checklist" }

    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val item = params.text("item") ?: return ToolResult(false, "I didn't catch what to add.")
        if (env.checklistSlot == null) return ToolResult(false, "There is no Checklist widget on Home.")
        if (!env.addChecklist(item)) return ToolResult(true, "“$item” is already on the list, or the list is full")
        return ToolResult(true, "Added “$item” to the checklist", undo = { env.removeChecklist(item); ToolResult(true, "Took “$item” off the checklist") })
    }
}
