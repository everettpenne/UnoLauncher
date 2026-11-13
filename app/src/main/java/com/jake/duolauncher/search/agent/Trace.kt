package com.jake.duolauncher.search.agent

import java.util.ArrayDeque

/** One thing the agent did: for the summary's Undo, the recent-actions list, and debugging. */
internal data class TraceEntry(
    val at: Long,
    val tool: String,
    val params: Map<String, String>,
    val ok: Boolean,
    val say: String,
    val ms: Long,
)

/** The last [MAX] actions, newest first. Main thread only (the loop runs there), so it needs no locking. */
internal object Trace {
    private const val MAX = 50
    private val entries = ArrayDeque<TraceEntry>()

    fun record(entry: TraceEntry) {
        entries.addFirst(entry)
        while (entries.size > MAX) entries.removeLast()
    }

    fun recent(count: Int = 10): List<TraceEntry> = entries.take(count)

    fun clear() = entries.clear()
}
