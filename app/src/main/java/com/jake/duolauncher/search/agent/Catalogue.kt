package com.jake.duolauncher.search.agent

import java.security.MessageDigest

/**
 * Everything the model sees about the tools, generated from the registry: the prompt that lists them, the GBNF that keeps a reply to the
 * shape we parse, and the docs. Generated, so a new tool never needs its prompt or grammar hand-edited.
 */
internal object Catalogue {
    /** The planner prompt: what to reply with, and the tools available. */
    fun prompt(tools: List<Tool>): String = buildString {
        appendLine("You plan tasks for a phone. Reply with JSON only, no prose.")
        appendLine("To act: {\"steps\":[{\"tool\":\"<name>\",\"params\":{\"<param>\":\"<value>\"}}]}")
        appendLine("Every parameter must be present; give an unused optional one as an empty string.")
        appendLine("If you must ask the person something first: {\"clarify\":\"<question>\"}")
        appendLine("If it cannot be done: {\"refuse\":\"<one sentence saying what is missing>\"}")
        appendLine()
        appendLine("Tools:")
        for (tool in tools) {
            append("- ").append(tool.name).append(": ").append(tool.summary).append('\n')
            if (tool.params.isNotEmpty()) {
                append("  params: ")
                append(tool.params.joinToString(", ") { p ->
                    val kind = when (p.kind) {
                        Kind.Int -> "whole number ${p.min}..${p.max}".trim()
                        Kind.Bool -> "true|false"
                        Kind.Enum -> p.choices.joinToString("|")
                        Kind.Text -> "text"
                    }
                    "${p.name}($kind${if (p.required) "" else ", optional"})"
                })
                append('\n')
            }
        }
    }

    /** A grammar for the same shape: tool names and parameter names are fixed, values are validated afterwards. */
    fun grammar(tools: List<Tool>): String = buildString {
        appendLine("root ::= plan | clarify | refuse")
        appendLine("plan ::= " + lit("{\"steps\":[") + " step (" + lit(",") + " step)* " + lit("]}"))
        appendLine("step ::= " + tools.joinToString(" | ") { rule(it.name) })
        for (tool in tools) appendLine(rule(tool.name) + " ::= " + body(tool))
        appendLine("clarify ::= " + lit("{\"clarify\":") + " " + lit("\"") + " text " + lit("\"") + " " + lit("}"))
        appendLine("refuse ::= " + lit("{\"refuse\":") + " " + lit("\"") + " text " + lit("\"") + " " + lit("}"))
        appendLine("digits ::= [0-9] [0-9]? [0-9]? [0-9]? [0-9]? [0-9]?")
        appendLine("bool ::= " + lit("true") + " | " + lit("false"))
        appendLine("text ::= [a-zA-Z0-9 .,:;/?_=&%+#'-]{0,200}")
    }

    /** Stable id for this catalogue, so a cached prompt prefix is only reused while the tools are the same. */
    fun hash(tools: List<Tool>): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(prompt(tools).toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    /** The tools as a Markdown table, so the docs cannot drift from the registry. */
    fun markdown(tools: List<Tool>): String = buildString {
        appendLine("| tool | effect | parameters | does |")
        appendLine("| --- | --- | --- | --- |")
        for (tool in tools) {
            val params = tool.params.joinToString(", ") { "${it.name}:${it.kind.name.lowercase()}" }.ifEmpty { "—" }
            appendLine("| `${tool.name}` | ${tool.effect} | $params | ${tool.summary} |")
        }
    }

    private fun rule(name: String): String = name.replace(Regex("[^a-zA-Z0-9-]"), "-") + "-step"

    private fun body(tool: Tool): String = buildString {
        append(lit("{\"tool\":\"" + tool.name + "\",\"params\":{"))
        tool.params.forEachIndexed { index, param ->
            if (index > 0) append(" ").append(lit(","))
            append(" ").append(lit("\"" + param.name + "\":"))
            append(' ')
            when (param.kind) {
                Kind.Int -> append("digits")
                Kind.Bool -> append("bool")
                // Parenthesised: a bare alternation would split the whole step rule, so a step could be just one choice.
                Kind.Enum -> append("( " + param.choices.joinToString(" | ") { lit("\"" + it + "\"") } + " )")
                Kind.Text -> append(lit("\"") + " text " + lit("\""))
            }
        }
        append(' ').append(lit("}}"))
    }

    /** A GBNF string literal for exactly [text]. */
    private fun lit(text: String): String = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
