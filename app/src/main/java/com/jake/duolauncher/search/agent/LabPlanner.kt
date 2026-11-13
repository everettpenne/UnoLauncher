package com.jake.duolauncher.search.agent

import com.jake.duolauncher.search.AssistantClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The real planner: Uno Lab plans the turn. The launcher sends everything the model needs — the question, the phone's facts, what grounding
 * resolved, plus the catalogue and grammar generated from the tool registry — so Uno Lab never needs to know what a tool does, and a new tool
 * is a launcher change alone.
 *
 * The reply is untrusted like any other model output: [PlanParser] rejects unknown tools and out-of-range values before anything runs.
 */
internal class LabPlanner(private val client: () -> AssistantClient?) : Planner {
    override suspend fun plan(request: PlanRequest): ParseResult {
        val assistant = client() ?: return ParseResult.Failed("Uno Lab isn't installed or isn't switched on.")
        val payload = PlannerRequest.json(request.query, request.context, request.grounding, request.tools)
        // The call blocks on the binder until the model has written the plan, so it must not run on the main thread.
        val reply = withContext(Dispatchers.IO) { runCatching { assistant.plan(payload) }.getOrNull() }
            ?: return ParseResult.Failed("I couldn't reach Uno Lab.")
        if (reply.isBlank()) return ParseResult.Failed("The planner didn't answer.")
        return PlanParser.parse(reply, request.tools)
    }
}
