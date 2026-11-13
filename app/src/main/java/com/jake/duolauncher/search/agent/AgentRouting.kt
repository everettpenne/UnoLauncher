package com.jake.duolauncher.search.agent

import android.content.Context
import com.jake.duolauncher.search.AssistantIntent

/**
 * Whether a question goes to the agent or to the answer path.
 *
 * Off by default: the loop is wired end to end, but an Uno Lab without the planner API (version 2) would answer every task with "I couldn't
 * reach Uno Lab", which is worse than today's answer path. It is turned on with one global setting — while the eval set is being run on a
 * device — so the test needs no rebuild, and a settings switch can drive the same key later:
 *
 *     adb shell settings put global unolab_agent 1
 *     adb shell settings put global unolab_agent 0
 */
internal object AgentRouting {
    const val SETTING = "unolab_agent"

    fun enabled(context: Context): Boolean = runCatching {
        android.provider.Settings.Global.getInt(context.contentResolver, SETTING, 0) == 1
    }.getOrDefault(false)

    fun plans(context: Context, query: String): Boolean = plans(query, enabled(context))

    /** Pure form, so the routing decision is tested without a Context. */
    fun plans(query: String, enabled: Boolean): Boolean = enabled && AssistantIntent.mightBeAction(query)
}
