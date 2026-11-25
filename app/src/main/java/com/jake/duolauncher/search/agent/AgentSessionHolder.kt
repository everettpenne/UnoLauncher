package com.jake.duolauncher.search.agent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
    private var searchOpen: () -> Boolean = { false }
    private var record: (String, String, com.jake.duolauncher.search.ChatTurn.Kind, Long) -> Unit = { _, _, _, _ -> }
    private var log: (com.jake.duolauncher.search.LogEntry) -> Unit = {}
    private var logUndone: (Long) -> Unit = {}
    private var newId: () -> Long = { System.currentTimeMillis() }
    /** Snapshot state, so what watches the agent's turn (the finished-answer wave) finds a session made after it first looked. */
    private var session by androidx.compose.runtime.mutableStateOf<AgentSession?>(null)

    fun bind(planner: () -> Planner, env: () -> ToolEnv, publish: () -> Unit = {},
        settings: () -> AgentSettings = { AgentSettings() }, searchOpen: () -> Boolean = { false },
        record: (String, String, com.jake.duolauncher.search.ChatTurn.Kind, Long) -> Unit = { _, _, _, _ -> },
        log: (com.jake.duolauncher.search.LogEntry) -> Unit = {}, logUndone: (Long) -> Unit = {},
        newId: () -> Long = { System.currentTimeMillis() }): AgentSession {
        this.record = record
        this.log = log; this.logUndone = logUndone; this.newId = newId
        this.planner = planner
        this.env = env
        this.publish = publish
        this.settings = settings
        this.searchOpen = searchOpen
        // The session must read the fields per run, not the values they had when it was made: a panel that opens before the app list
        // has loaded binds an empty environment, and a later bind has to be the one a turn uses.
        return session ?: AgentSession(
            planner = { this.planner() },
            env = { this.env() },
            publish = { this.publish() },
            settings = { this.settings() },
            searchOpen = { this.searchOpen() },
            record = { said, reply, kind, at -> this.record(said, reply, kind, at) },
            log = { this.log(it) }, logUndone = { this.logUndone(it) }, newId = { this.newId() },
        ).also { session = it }
    }

    fun current(): AgentSession? = session
}
