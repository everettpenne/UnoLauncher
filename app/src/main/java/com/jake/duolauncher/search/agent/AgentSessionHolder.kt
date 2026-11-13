package com.jake.duolauncher.search.agent

/**
 * The agent's turn, owned by the app rather than by the panel — the same shape as the answer session, so search can be closed while a plan
 * runs and the island keeps saying what is happening.
 *
 * The panel rebinds the planner and environment each time it opens (its tool environment is built from that screen's state), while the
 * session object, its state and its undo stack are kept.
 */
internal object AgentSessionHolder {
    private var planner: () -> Planner = { error("the agent has no planner") }
    private var env: () -> ToolEnv = { error("the agent has no environment") }
    private var publish: () -> Unit = {}
    private var settings: () -> AgentSettings = { AgentSettings() }
    private var session: AgentSession? = null

    fun bind(planner: () -> Planner, env: () -> ToolEnv, publish: () -> Unit = {},
        settings: () -> AgentSettings = { AgentSettings() }): AgentSession {
        this.planner = planner
        this.env = env
        this.publish = publish
        this.settings = settings
        // The session must read the fields per run, not the values they had when it was made: a panel that opens before the app list
        // has loaded binds an empty environment, and a later bind has to be the one a turn uses.
        return session ?: AgentSession(
            planner = { this.planner() },
            env = { this.env() },
            publish = { this.publish() },
            settings = { this.settings() },
        ).also { session = it }
    }

    fun current(): AgentSession? = session
}
