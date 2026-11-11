package com.jake.duolauncher.search

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.jake.duolauncher.NotificationFeed

/**
 * One assistant question, owned by the app rather than by Spotlight.
 *
 * The panel used to run the turn inside its own composition, so closing search cancelled it and the person had to sit with search open
 * until the answer arrived (measured: 3-9 s). Now the turn lives here: Spotlight hands it a query and may close at once, the result waits
 * in [state], and opening search again shows it. The island is told about it through [AssistantLive].
 *
 * Only one turn runs at a time; a new question replaces the last.
 */
internal object AssistantSession {
    sealed interface State {
        data object Idle : State
        data class Running(val query: String) : State
        /** Done: [answer] for a question, or [actionJson] when the model read it as an action for the launcher to check. */
        data class Ready(val query: String, val answer: String?, val actionJson: String?, val ms: Long) : State
        data class Failed(val query: String, val why: String) : State
    }

    var state by mutableStateOf<State>(State.Idle)
        private set

    /** Which question the panel last asked, so a result for an older question is not shown under a newer one. */
    var asked by mutableStateOf<String?>(null)

    private var client: AssistantClient? = null
    /** Kept so clearing the result can take its card off the island. */
    private var appContext: Context? = null
    private var job: Job? = null
    private var scope: CoroutineScope? = null

    /** Starts a turn. [phoneContext] is the facts for an answer (Uno Lab gets none for a possible action). */
    fun start(context: Context, query: String, phoneContext: String) {
        val app = context.applicationContext
        appContext = app
        val c = client ?: LocalAssistant(app).also { client = it }
        c.open()
        job?.cancel()
        scope?.coroutineContext?.get(Job)?.cancel()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope = it }
        asked = query
        state = State.Running(query)
        publish(app)
        job = s.launch {
            val t0 = System.currentTimeMillis()
            try {
                var json: String? = null
                // Only a possible action costs the extra round trip; a question goes straight to the answer.
                if (AssistantIntent.mightBeAction(query)) {
                    json = buildString { c.ask(query, AskMode.ACTION).collect { append(it) } }
                }
                val outcome = json?.let { AssistantIntent.read(it, query) }
                if (outcome is AssistantIntent.Outcome.Act || outcome is AssistantIntent.Outcome.Cannot) {
                    state = State.Ready(query, null, json, System.currentTimeMillis() - t0)
                    publish(app)
                    return@launch
                }
                val answer = buildString { c.ask(query, AskMode.ANSWER, phoneContext).collect { append(it) } }
                state = State.Ready(query, answer, null, System.currentTimeMillis() - t0)
                publish(app)
            } catch (e: Exception) {
                state = State.Failed(query, e.message ?: "Uno Lab couldn't answer.")
                publish(app)
            } finally {
                c.close()
            }
        }
    }

    /** Tells the island what the turn is doing. */
    private fun publish(context: Context) = NotificationFeed.publishAssistant(context, System.currentTimeMillis())

    /** Forgets the result (a new search, or the person dismissed it). */
    fun clear() {
        job?.cancel(); job = null
        asked = null
        state = State.Idle
        appContext?.let { publish(it) }   // takes the card off the island
        client?.close()
    }
}
