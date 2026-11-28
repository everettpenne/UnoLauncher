package com.jake.duolauncher.search.agent

import java.security.MessageDigest

/**
 * Everything the model sees about the tools, generated from the registry: the prompt that lists them, the GBNF that keeps a reply to the
 * shape we parse, and the docs. Generated, so a new tool never needs its prompt or grammar hand-edited.
 */
internal object Catalogue {
    /** The contract the first planners were trained on (v4 and before): steps, a question, a refusal or a search offer; three steps. */
    const val V1 = 1
    /** The contract from the 4B planner on: also looking a fact up before planning, planning again after a step failed, five steps,
     *  and a tool list that is read and not remembered (see docs/agent/protocol-v2.md). */
    const val V2 = 2

    /** The planner prompt: what to reply with, and the tools available. [protocol] [V1] is word for word what v4 was trained on. */
    fun prompt(tools: List<Tool>, protocol: Int = V1): String = buildString {
        appendLine("You plan tasks for a phone. Reply with JSON only, no prose.")
        appendLine("To act: {\"steps\":[{\"tool\":\"<name>\",\"params\":{\"<param>\":\"<value>\"}}]}")
        appendLine("Every parameter must be present; give an unused optional one as an empty string.")
        appendLine("If you must ask the person something first: {\"clarify\":\"<question>\"}")
        appendLine("If it cannot be done: {\"refuse\":\"<one sentence saying what is missing>\"}")
        appendLine("If it is a question you cannot answer from the phone, or a web search would help: {\"search\":\"<what to search for, in the person's words>\"}")
        if (protocol >= V2) appendLine("If you need a fact from the phone that you were not given: {\"look\":[\"<source>\"]}. You will be asked again with what was found.")
        appendLine("Rules:")
        if (protocol >= V2) {
            appendLine("- Use only the steps the request asks for, $MAX_STEPS_V2 at most. Most requests are one step.")
            appendLine("- Never invent a detail. If a time, a length, a number or a name is missing, ask with clarify.")
            appendLine("- Copy times and lengths as the person said them (\"an hour and a half\", \"tomorrow 3pm\"). Do not convert or calculate.")
            appendLine("- A value may be copied from the facts you were given (context, known, Found) when the request points at it (\"this song\", \"my next meeting\").")
            appendLine("- Look only for a fact the request needs and you were not given, at most once. After Found, plan, ask or refuse; never look again.")
            appendLine("- After Failed, do not repeat the step that failed: plan another way, ask, or refuse and say why.")
            appendLine("- Use only the tools listed here. The list changes from phone to phone; a tool that is not listed does not exist.")
            appendLine("- Never make up a web address. If no listed tool does what is asked (ordering, booking, paying), refuse.")
            appendLine()
            appendLine("Sources you may look at:")
            for (source in LookSource.entries) append("- ").append(source.id).append(": ").append(source.holds).append('\n')
        } else {
            appendLine("- Use only the steps the request asks for, three at most. Most requests are one step.")
            appendLine("- Never invent a detail. If a time, a length, a number or a name is missing, ask with clarify.")
            appendLine("- Copy times and lengths as the person said them (\"an hour and a half\", \"tomorrow 3pm\"). Do not convert or calculate.")
            appendLine("- Never make up a web address. If no tool does what is asked (ordering, booking, paying), refuse.")
        }
        appendLine()
        appendLine("Tools:")
        for (tool in tools) {
            append("- ").append(tool.name).append(": ").append(tool.summary).append('\n')
            if (tool.examples.isNotEmpty()) append("  for: ").append(tool.examples.joinToString("; ") { "\"$it\"" }).append('\n')
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

    const val MAX_STEPS_V2 = 5
    const val MAX_LOOKS = 2

    /** A grammar for the same shape: tool names and parameter names are fixed, values are validated afterwards. */
    fun grammar(tools: List<Tool>, protocol: Int = V1): String = buildString {
        val v2 = protocol >= V2
        appendLine("root ::= plan | clarify | refuse | search" + if (v2) " | look" else "")
        val more = " (" + lit(",") + " step)?"
        appendLine("plan ::= " + lit("{\"steps\":[") + " step" + more.repeat(if (v2) MAX_STEPS_V2 - 1 else 2) + " " + lit("]}"))
        appendLine("step ::= " + tools.joinToString(" | ") { rule(it.name) })
        for (tool in tools) appendLine(rule(tool.name) + " ::= " + body(tool))
        appendLine("clarify ::= " + lit("{\"clarify\":") + " " + lit("\"") + " text " + lit("\"") + " " + lit("}"))
        appendLine("refuse ::= " + lit("{\"refuse\":") + " " + lit("\"") + " text " + lit("\"") + " " + lit("}"))
        appendLine("search ::= " + lit("{\"search\":") + " " + lit("\"") + " text " + lit("\"") + " " + lit("}"))
        if (v2) {
            appendLine("look ::= " + lit("{\"look\":[") + " source (" + lit(",") + " source)? " + lit("]}"))
            appendLine("source ::= " + LookSource.entries.joinToString(" | ") { lit("\"" + it.id + "\"") })
        }
        appendLine("digits ::= [0-9] [0-9]? [0-9]? [0-9]? [0-9]? [0-9]?")
        appendLine("bool ::= " + lit("true") + " | " + lit("false"))
        // V2: an address needs its @, a message its ! and brackets, and a reply may be as long as a reply is allowed to be.
        appendLine(if (v2) "text ::= [a-zA-Z0-9 .,:;/?_=&%+#'@!()*$-]{0,400}" else "text ::= [a-zA-Z0-9 .,:;/?_=&%+#'-]{0,200}")
    }

    /** Stable id for this catalogue, so a cached prompt prefix is only reused while the tools are the same. */
    fun hash(tools: List<Tool>, protocol: Int = V1): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(prompt(tools, protocol).toByteArray())
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
                // An optional number may be left out, as the empty string the prompt asks for.
                Kind.Int -> append(if (param.required) "digits" else "( digits | " + lit("\"\"") + " )")
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
