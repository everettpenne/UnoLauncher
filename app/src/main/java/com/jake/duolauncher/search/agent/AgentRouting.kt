package com.jake.duolauncher.search.agent

import com.jake.duolauncher.search.AssistantIntent

/**
 * Whether a question goes to the agent or to the answer path.
 *
 * [enabled] is off: the loop is wired end to end, but an Uno Lab without the planner API (version 2) would answer every task with "I couldn't
 * reach Uno Lab", which is worse than today's answer path. It is turned on — here, or from a setting when there is one — once the eval set is
 * green on a device with that Lab installed. A task is a request that names a command; anything else is a question and is answered.
 */
internal object AgentRouting {
    @Volatile var enabled = false

    fun plans(query: String): Boolean = enabled && AssistantIntent.mightBeAction(query)
}
