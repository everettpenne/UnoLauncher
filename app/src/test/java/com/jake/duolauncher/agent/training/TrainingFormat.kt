package com.jake.duolauncher.agent.training

import com.jake.duolauncher.search.agent.Catalogue
import com.jake.duolauncher.search.agent.Kind
import com.jake.duolauncher.search.agent.Tool
import com.jake.duolauncher.search.agent.ToolRegistry

/**
 * The exact text Uno Lab's planner is given, and the exact reply format, so a training example is the production prompt rather than a
 * paraphrase of it. The planner's side of this lives in Uno Lab (`Planner.prefix`/`turn`); the two are kept in step by golden literals here
 * and by `PlannerTest` there.
 *
 * The completion is what the phone actually produced: compact JSON with no spaces, numbers and booleans bare, text and enums quoted, every
 * parameter present in declaration order and an unused optional written as "".
 */
internal object TrainingFormat {
    /** The prompt contract; bump when the system text, turn shape or reply shape changes. */
    const val VERSION = 1

    /** Verbatim from Uno Lab's `Planner.SYSTEM`. */
    const val SYSTEM = "You are the planner for Uno, a phone assistant. Turn what the person asks into the JSON described below. " +
        "Use only the tools listed. Prefer the fewest steps. If a detail is missing (which person, which time), ask a question instead " +
        "of guessing. If it cannot be done with these tools, refuse in one sentence and say what is missing. Reply with JSON only."

    /** The system turn: the instructions and the generated catalogue, as Lab's `prefix` renders them. */
    fun system(tools: List<Tool>): String = buildString {
        append("<|im_start|>system\n")
        append(SYSTEM)
        append("\n\n")
        append(Catalogue.prompt(tools).trim())
        append("\n<|im_end|>\n")
    }

    /** The user turn: facts, grounding, the request, then the assistant header with thinking off. */
    fun turn(query: String, context: String = "", grounding: String = ""): String = buildString {
        append("<|im_start|>user\n")
        if (context.isNotBlank()) append("<context>\n").append(context.trim()).append("\n</context>\n\n")
        if (grounding.isNotBlank()) append("<known>\n").append(grounding.trim()).append("\n</known>\n\n")
        append("Request: ").append(query.trim())
        append("<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n")
    }

    fun prompt(tools: List<Tool>, query: String, context: String = "", grounding: String = ""): String =
        system(tools) + turn(query, context, grounding)

    /** The canonical plan reply for [steps] (tool to raw values). */
    fun steps(steps: List<Pair<Tool, Map<String, String>>>): String = buildString {
        append("{\"steps\":[")
        steps.forEachIndexed { index, (tool, values) ->
            if (index > 0) append(',')
            append("{\"tool\":\"").append(tool.name).append("\",\"params\":{")
            tool.params.forEachIndexed { j, param ->
                if (j > 0) append(',')
                append('"').append(param.name).append("\":")
                val value = values[param.name].orEmpty()
                when (param.kind) {
                    // The grammar's shapes: numbers and booleans bare, everything else a JSON string. An unused optional number is the empty
                    // string the prompt asks for (the grammar's `( digits | "" )`), not a zero.
                    Kind.Int -> if (value.isEmpty() && !param.required) append("\"\"") else append(value)
                    Kind.Bool -> append(value.ifEmpty { "false" })
                    Kind.Enum, Kind.Text -> append('"').append(value).append('"')
                }
            }
            append("}}")
        }
        append("]}")
    }

    fun step(tool: Tool, values: Map<String, String>): String = steps(listOf(tool to values))

    fun clarify(question: String): String = "{\"clarify\":" + quoted(question) + "}"

    fun refuse(reason: String): String = "{\"refuse\":" + quoted(reason) + "}"

    /** The catalogue's own id: a corpus is only usable with the catalogue it was generated for. */
    fun catalogueHash(tools: List<Tool> = ToolRegistry.builtIn): String = Catalogue.hash(tools)

    /** Whether [text] is inside the grammar's `text` alphabet, so a reply that carries it could be produced on the phone. */
    fun grammarSafe(text: String): Boolean = text.length <= 200 && text.all { it in ALLOWED }

    fun requireGrammarSafe(text: String): String =
        require(grammarSafe(text)) { "not grammar-safe: $text" }.let { text }

    private const val ALLOWED = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 .,:;/?_=&%+#'-"

    private fun quoted(text: String): String = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
