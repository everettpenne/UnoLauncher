package com.jake.duolauncher.search.agent

import android.content.Context
import com.jake.duolauncher.search.AssistantIntent

/**
 * Whether a question goes to the agent or to the answer path.
 *
 * Off unless turned on: "Let Qwen do tasks" in Launcher settings, or one global setting for a run of the eval set on a device, which
 * needs no rebuild (below). With either on, an Uno Lab too old to plan (before version 2) is noticed the first time it is spoken to,
 * and tasks go to the answer path until a newer one connects.
 *
 *     adb shell settings put global unolab_agent 1
 *     adb shell settings put global unolab_agent 0
 */
internal object AgentRouting {
    const val SETTING = "unolab_agent"
    /** The switches in Launcher settings (ExtrasStore keeps them in the "extras" preferences). */
    const val PREF_TASKS = "agentTasks"
    const val PREF_ASK_FIRST = "agentAskFirst"

    /** Set by the device-test hook (test builds only): the turn stops once it has a plan and says what it would do, so an evaluation
     *  run over many requests does not set forty timers on the phone. */
    @Volatile var planOnly = false

    /** The first Uno Lab that plans. An older one has no planner to ask. */
    const val PLANNER_VERSION = 2

    /** True once the Uno Lab that is connected has said it is too old to plan. Until it has said anything, it is taken to be able to. */
    @Volatile var plannerMissing = false
        private set

    /** Told what version the connected Uno Lab is, each time a new connection to it is first used (so an updated Lab is seen). */
    fun labVersion(version: Int) { plannerMissing = version < PLANNER_VERSION }

    /** On with "Let Qwen do tasks" in Launcher settings, or with the global setting a device test flips; and not while the connected
     *  Uno Lab is known to have no planner, when every task would end in "I couldn't reach the planner": those requests are answered
     *  the ordinary way instead, as they are with the switch off. */
    fun enabled(context: Context): Boolean = !plannerMissing && (prefs(context)?.getBoolean(PREF_TASKS, false) == true || runCatching {
        android.provider.Settings.Global.getInt(context.contentResolver, SETTING, 0) == 1
    }.getOrDefault(false))

    /**
     * Which contract the connected planner was trained on: 1 (the first planners, three steps and nineteen tools) unless set to 2 (the
     * 4B planner: more tools, looking a fact up, planning again after a failure). A planner is only right under the contract it was
     * trained on, so this is switched when the model is, not before:
     *
     *     adb shell settings put global unolab_protocol 2
     */
    const val PROTOCOL_SETTING = "unolab_protocol"
    fun protocol(context: Context): Int = runCatching { android.provider.Settings.Global.getInt(context.contentResolver, PROTOCOL_SETTING, Catalogue.V1) }
        .getOrDefault(Catalogue.V1).coerceIn(Catalogue.V1, Catalogue.V2)

    /** How the person has set the agent to behave, read for each turn. */
    fun settings(context: Context): AgentSettings = AgentSettings(askAll = prefs(context)?.getBoolean(PREF_ASK_FIRST, false) == true)

    private fun prefs(context: Context) = runCatching { context.getSharedPreferences("extras", Context.MODE_PRIVATE) }.getOrNull()

    fun plans(context: Context, query: String): Boolean = plans(query, enabled(context))

    /** Pure form, so the routing decision is tested without a Context. */
    fun plans(query: String, enabled: Boolean): Boolean = enabled && AssistantIntent.mightBeAction(query)
}
