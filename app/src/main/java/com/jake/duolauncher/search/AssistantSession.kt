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

    /**
     * The answer so far, while it is being written: the model writes it a few words at a time over several seconds, and showing nothing
     * until the end made the card sit at "Asking Qwen…" and then jump to a full paragraph. Published at most once a display frame
     * ([flushPosted]), however fast the pieces come, and read only by the answer card, so it redraws that card and nothing else.
     */
    var streamed by mutableStateOf("")
        private set
    private val streamBuffer = StringBuilder()
    private var flushPosted = false

    /** Adds [piece] to the answer so far. On the main thread (the turn's scope is Main). */
    private fun appendPiece(piece: String) {
        streamBuffer.append(piece)
        if (flushPosted) return
        flushPosted = true
        android.view.Choreographer.getInstance().postFrameCallback { flushPosted = false; streamed = streamBuffer.toString() }
    }

    private fun resetStream() { streamBuffer.setLength(0); streamed = "" }

    /** Which question the panel last asked, so a result for an older question is not shown under a newer one. */
    var asked by mutableStateOf<String?>(null)

    private var client: AssistantClient? = null
    /** Device tests put a stand-in assistant here; the app never sets it. */
    @androidx.annotation.VisibleForTesting internal var clientForTests: AssistantClient? = null
    /** Kept so clearing the result can take its card off the island. */
    private var appContext: Context? = null
    private var job: Job? = null
    /** The turn a new question has just replaced, while it stops: what it had written is kept (see [start]). */
    private var replaced: Job? = null

    /** A turn's own record of what it asked and has written, and whether that is already in the conversation. */
    private class Turn(val query: String) { val written = StringBuilder(); var kept = false }
    private var current: Turn? = null

    /**
     * Puts the answer still being written into the conversation as far as it has got, before a follow-up is asked: the follow-up is
     * sent with the conversation so far, and the turn it follows must be in it. (Otherwise it is kept only as it is replaced, after
     * the follow-up's context was read, and "where did he live?" went to the model with no "he".)
     */
    fun keepUnfinished(context: Context) {
        val t = current ?: return
        if (t.kept || state !is State.Running) return
        AnswerText.clean(t.written.toString())?.let { ChatStore.record(context.applicationContext, t.query, "$it…", ChatTurn.Kind.ANSWER); t.kept = true }
    }
    private var scope: CoroutineScope? = null

    /** Starts a turn. [phoneContext] is the facts for an answer (Uno Lab gets none for a possible action). */
    fun start(context: Context, query: String, phoneContext: String) {
        val app = context.applicationContext
        appContext = app
        val c = clientForTests ?: client ?: LocalAssistant(app).also { client = it }
        c.open()
        // The turn being replaced is let go of before it is cancelled: on Main.immediate its cancellation runs here, inside cancel(),
        // and it must already see that it is no longer the current turn (or it unbinds the client this turn has just opened).
        val previous = job
        replaced = previous
        job = null
        previous?.cancel()
        scope?.coroutineContext?.get(Job)?.cancel()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope = it }
        asked = query
        resetStream()
        state = State.Running(query)
        publish(app)
        // What this turn has written so far, kept here and not only in the shared stream, so a turn replaced mid-answer can still be
        // written into the conversation (the next question resets the stream before this one has stopped).
        val turn = Turn(query).also { current = it }
        val written = turn.written
        // Started only once it is [job], so every check below of whether it is still the current turn sees itself.
        lateinit var mine: Job
        mine = s.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
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
                c.ask(query, AskMode.ANSWER, phoneContext).collect { written.append(it); appendPiece(it) }
                val answer = written.toString()
                state = State.Ready(query, answer, null, System.currentTimeMillis() - t0)
                // The exchange goes into the chat history, which stays on this phone and can be deleted from the chat view.
                if (!turn.kept) AnswerText.clean(answer)?.let { ChatStore.record(app, query, it, ChatTurn.Kind.ANSWER) }
                publish(app)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Replaced by the next question (a follow-up sent while this was still being written) or cleared. The state is the
                // next turn's now, so it is left alone; what was written so far still goes into the conversation, so the follow-up has
                // the turn it follows. A turn cleared away (search dismissed, "seen") is not one the person moved on from mid-sentence.
                if (replaced === mine && !turn.kept) AnswerText.clean(written.toString())?.let { ChatStore.record(app, query, "$it…", ChatTurn.Kind.ANSWER) }
                throw e
            } catch (e: Exception) {
                if (job === mine) {
                    state = State.Failed(query, e.message ?: "Uno Lab couldn't answer.")
                    publish(app)
                }
            } finally {
                // The client is shared by every turn. A turn replaced by the next one must not unbind it: the next one has already
                // opened it (a no-op, since it was still bound) and would then wait for a service that is gone ("Uno Lab isn't
                // connected"). Only the turn still current closes it.
                if (job === mine) c.close()
            }
        }
        job = mine
        mine.start()
    }

    /** Tells the island what the turn is doing. */
    private fun publish(context: Context) = NotificationFeed.publishAssistant(context, System.currentTimeMillis())

    /** Forgets the result (a new search, or the person dismissed it). */
    /** Search showing the turn has closed: a finished answer (or failure) has been seen, so the island's card for it goes. One still being
     *  written is kept. Both search hosts call it (Home's, and search over an app). */
    fun seen() {
        val s = state
        if (s is State.Ready || s is State.Failed) clear()
    }

    fun clear() {
        replaced = null
        current = null
        job?.cancel(); job = null
        asked = null
        resetStream()
        state = State.Idle
        appContext?.let { publish(it) }   // takes the card off the island
        client?.close()
    }
}
