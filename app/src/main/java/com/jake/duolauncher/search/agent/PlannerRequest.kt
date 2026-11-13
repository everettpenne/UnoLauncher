package com.jake.duolauncher.search.agent

/**
 * What the launcher asks the planner for, as JSON. One message carries everything Uno Lab needs — the question, the facts, the tools and the
 * grammar — so the planner never has to know what a tool does or which tools exist. Adding a tool changes this payload, not the AIDL.
 */
internal object PlannerRequest {
    const val VERSION = 1

    fun json(query: String, context: String, grounding: String, tools: List<Tool>): String = buildString {
        append("{\"v\":").append(VERSION)
        append(",\"query\":").append(quote(query))
        append(",\"context\":").append(quote(context))
        append(",\"grounding\":").append(quote(grounding))
        append(",\"catalogue\":").append(quote(Catalogue.prompt(tools)))
        append(",\"grammar\":").append(quote(Catalogue.grammar(tools)))
        append(",\"hash\":").append(quote(Catalogue.hash(tools)))
        append('}')
    }

    /** A JSON string literal: quotes, backslashes, the control characters JSON forbids bare, and anything outside the BMP. */
    fun quote(text: String): String = buildString {
        append('"')
        for (ch in text) {
            when {
                ch == '"' -> append("\\\"")
                ch == '\\' -> append("\\\\")
                ch == '\n' -> append("\\n")
                ch == '\r' -> append("\\r")
                ch == '\t' -> append("\\t")
                ch < ' ' -> append("\\u").append("%04x".format(ch.code))
                else -> append(ch)
            }
        }
        append('"')
    }
}
