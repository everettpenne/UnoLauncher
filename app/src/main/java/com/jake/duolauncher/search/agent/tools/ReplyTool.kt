package com.jake.duolauncher.search.agent.tools

import com.jake.duolauncher.search.agent.*

/**
 * Answers a message from its notification, as the notification's own reply box would.
 *
 * LeavesPhone: the words go to another person and cannot be taken back, so the turn always shows who it goes to and the exact text and
 * waits for a tap (ToolPolicy). There is no undo.
 *
 * Who it goes to is settled once, before that tap ([bind]), and the send goes to exactly that notification: a message arriving from
 * someone else while the question is on screen cannot change where the reply lands. If that notification has gone by then, nothing is sent.
 *
 * Only the sender's name and the app's are read from a notification, and only when a reply is asked for. A message's own words are never
 * read here, so nothing a sender wrote can steer what is done.
 */
internal object ReplyTool : Tool(
    name = "reply",
    summary = "Replies to a message that is waiting as a notification. to: the sender's name or the app, or \"that\" for the newest message. text: the exact words to send.",
    params = listOf(Param("to", Kind.Text, maxLength = 60), Param("text", Kind.Text, maxLength = ReplyLogic.MAX_TEXT)),
    effect = Effect.LeavesPhone,
    needs = setOf(Capability.Notifications),
    examples = listOf("reply to Dana saying running late", "answer that with thanks", "tell Jo back that I got it"),
) {
    private const val KEY = "_key"
    private const val WHO = "_who"
    private const val APP = "_app"

    override fun bind(params: Params, env: ToolEnv): Params =
        when (val found = ReplyLogic.resolve(params.text("to").orEmpty(), env.replyTargets())) {
            is ReplyLogic.Found.One -> Params(params.values + mapOf(KEY to found.target.key, WHO to found.target.who, APP to found.target.app))
            else -> params
        }

    override fun problem(params: Params, env: ToolEnv): String? {
        if (params.values[KEY] != null) return null
        return when (val found = ReplyLogic.resolve(params.text("to").orEmpty(), env.replyTargets())) {
            is ReplyLogic.Found.One -> null
            ReplyLogic.Found.None -> ReplyLogic.nobody(params.text("to").orEmpty())
            is ReplyLogic.Found.Many -> ReplyLogic.which(found.targets)
        }
    }

    override fun describe(params: Params, env: ToolEnv): String? {
        val who = params.values[WHO] ?: return null
        return "Reply to $who in ${params.values[APP]}: “${params.text("text").orEmpty()}”"
    }

    override suspend fun execute(params: Params, env: ToolEnv): ToolResult {
        val text = params.text("text") ?: return ToolResult(false, "I didn't catch what to say.")
        // Unbound (a plan run without the confirmation step's binding): settle it now, by the same rules.
        val bound = if (params.values[KEY] != null) params else bind(params, env)
        val key = bound.values[KEY] ?: return ToolResult(false, problem(params, env) ?: "I don't see a message to reply to.")
        val who = bound.values[WHO].orEmpty()
        val app = bound.values[APP].orEmpty()
        if (env.replyTargets().none { it.key == key }) return ToolResult(false, "That message from $who is gone, so nothing was sent.")
        return if (env.sendReply(key, text)) ToolResult(true, "Replied to $who in $app: “$text”")
        else ToolResult(false, "The reply to $who didn't send. Open $app to answer.")
    }
}
