package com.jake.duolauncher.search

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One exchange with the assistant: what the person said and what came back. */
internal data class ChatTurn(val atMs: Long, val said: String, val reply: String, val kind: Kind) {
    /** What the reply was: an answer, something done, a question back, a refusal. */
    enum class Kind { ANSWER, DONE, ASKED, REFUSED }
}

/** A conversation: its turns, oldest first. */
internal data class Chat(val id: Long, val turns: List<ChatTurn>) {
    val lastMs: Long get() = turns.lastOrNull()?.atMs ?: id
    /** What it is called in the list: the first thing that was said in it. */
    val title: String get() = turns.firstOrNull()?.said?.lineSequence()?.firstOrNull()?.take(ChatLog.TITLE_CHARS).orEmpty()
}

/**
 * The rules for the chat history: where a turn goes, when a new conversation starts, how much is kept, and how it is written down.
 * Pure, so every rule is unit-tested.
 */
internal object ChatLog {
    /** After this long with nothing said, the next words start a new conversation. */
    const val NEW_AFTER_MS = 30 * 60_000L
    const val MAX_CHATS = 40
    const val MAX_TURNS = 200
    const val MAX_TEXT = 4000
    const val TITLE_CHARS = 60

    /** The conversation [turn] belongs to: [active] while it is recent (or has just been opened to carry on), otherwise a new one. */
    fun add(chats: List<Chat>, active: Long?, turn: ChatTurn): Pair<List<Chat>, Long> {
        val clipped = turn.copy(said = turn.said.take(MAX_TEXT), reply = turn.reply.take(MAX_TEXT))
        val current = chats.firstOrNull { it.id == active }
        val carriesOn = current != null && (current.turns.isEmpty() || clipped.atMs - current.lastMs < NEW_AFTER_MS)
        if (current != null && carriesOn) {
            val next = current.copy(turns = (current.turns + clipped).takeLast(MAX_TURNS))
            return chats.map { if (it.id == current.id) next else it } to current.id
        }
        val fresh = Chat(freshId(chats, clipped.atMs), listOf(clipped))
        // The oldest conversations go once there are too many; empty ones are never kept.
        return (chats.filter { it.turns.isNotEmpty() } + fresh).sortedBy { it.lastMs }.takeLast(MAX_CHATS) to fresh.id
    }

    /** An id no conversation has: the time it began, moved on if two began in the same millisecond. */
    fun freshId(chats: List<Chat>, nowMs: Long): Long {
        var id = nowMs
        while (chats.any { it.id == id }) id++
        return id
    }

    fun delete(chats: List<Chat>, id: Long): List<Chat> = chats.filter { it.id != id }

    /** How much of the conversation goes with the next question: the last few exchanges, each cut short, within a budget that leaves
     *  room for the phone's facts in what Uno Lab reads (2,000 characters in all). */
    const val EARLIER_TURNS = 3
    const val EARLIER_SAID = 160
    const val EARLIER_REPLY = 320
    const val EARLIER_BUDGET = 900

    /**
     * The conversation so far, as Qwen is shown it with the next question, so "tell me more" and "why?" have something to refer to:
     * the last [EARLIER_TURNS] exchanges of [chat], oldest first, while the conversation is still going ([NEW_AFTER_MS]). "" when there
     * is nothing recent. Whole exchanges are dropped from the oldest end to stay within [EARLIER_BUDGET].
     */
    fun earlier(chat: Chat?, nowMs: Long): String {
        val turns = chat?.turns.orEmpty().filter { nowMs - it.atMs < NEW_AFTER_MS }.takeLast(EARLIER_TURNS)
        fun line(text: String, max: Int) = text.replace('\n', ' ').trim().let { if (it.length > max) it.take(max - 1).trimEnd() + "…" else it }
        val parts = turns.map { t ->
            val did = when (t.kind) {
                ChatTurn.Kind.ANSWER -> "You answered"; ChatTurn.Kind.DONE -> "You did"
                ChatTurn.Kind.ASKED -> "You asked back"; ChatTurn.Kind.REFUSED -> "You said"
            }
            "- They said: ${line(t.said.lineSequence().last().removePrefix("Answer: "), EARLIER_SAID)}\n  $did: ${line(t.reply, EARLIER_REPLY)}"
        }.toMutableList()
        while (parts.isNotEmpty() && parts.sumOf { it.length + 1 } > EARLIER_BUDGET) parts.removeAt(0)
        return if (parts.isEmpty()) "" else "Earlier in this conversation (oldest first):\n" + parts.joinToString("\n")
    }

    /** Newest first, for the list. */
    fun listed(chats: List<Chat>): List<Chat> = chats.filter { it.turns.isNotEmpty() }.sortedByDescending { it.lastMs }

    fun toJson(chats: List<Chat>): String = JSONArray(chats.filter { it.turns.isNotEmpty() }.map { chat ->
        JSONObject().put("id", chat.id).put("turns", JSONArray(chat.turns.map { t ->
            JSONObject().put("at", t.atMs).put("said", t.said).put("reply", t.reply).put("kind", t.kind.name)
        }))
    }).toString()

    /** Reads what [toJson] wrote. Anything that does not read is dropped, never thrown: a damaged file is an empty history. */
    fun fromJson(json: String?): List<Chat> = runCatching {
        val array = JSONArray(json.orEmpty())
        (0 until array.length()).mapNotNull { i ->
            val chat = array.optJSONObject(i) ?: return@mapNotNull null
            val turns = chat.optJSONArray("turns") ?: return@mapNotNull null
            val read = (0 until turns.length()).mapNotNull { j ->
                val t = turns.optJSONObject(j) ?: return@mapNotNull null
                val kind = runCatching { ChatTurn.Kind.valueOf(t.optString("kind")) }.getOrNull() ?: return@mapNotNull null
                ChatTurn(t.optLong("at"), t.optString("said"), t.optString("reply"), kind).takeIf { it.said.isNotBlank() || it.reply.isNotBlank() }
            }
            Chat(chat.optLong("id"), read).takeIf { it.turns.isNotEmpty() }
        }
    }.getOrDefault(emptyList())
}

/**
 * The chat history: what was asked and what the assistant answered or did, kept on this phone so a conversation can be read back and
 * carried on. It lives in one file in the launcher's private storage (never backed up, never sent anywhere, readable by no other app),
 * and any conversation, or all of them, can be deleted from the chat view or from search's settings. Nothing is recorded while the
 * assistant is off, since nothing is asked.
 */
internal object ChatStore {
    var chats by mutableStateOf<List<Chat>>(emptyList())
        private set

    /** The conversation the next turn goes into; 0 when the next turn starts one. */
    var activeId by mutableLongStateOf(0L)
        private set

    private var loaded = false
    private const val FILE = "assistant-chats.json"

    val active: Chat? get() = chats.firstOrNull { it.id == activeId }

    /** Reads the history once; cheap to call again. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        chats = ChatLog.fromJson(runCatching { file(context).readText() }.getOrNull())
        // The last conversation is carried on only while it is recent ([ChatLog.add] decides when the next turn arrives).
        activeId = ChatLog.listed(chats).firstOrNull()?.id ?: 0L
    }

    fun record(context: Context, said: String, reply: String, kind: ChatTurn.Kind, nowMs: Long = System.currentTimeMillis()) {
        if (said.isBlank() || reply.isBlank()) return
        load(context)
        val (next, id) = ChatLog.add(chats, activeId.takeIf { it != 0L }, ChatTurn(nowMs, said.trim(), reply.trim(), kind))
        chats = next; activeId = id
        save(context)
    }

    /** Opens an old conversation to read and carry on: the next turn goes into it. */
    fun open(id: Long) { if (chats.any { it.id == id }) activeId = id }

    /** The next turn starts a new conversation. */
    fun startNew() { activeId = 0L }

    fun delete(context: Context, id: Long) {
        load(context)
        chats = ChatLog.delete(chats, id)
        if (activeId == id) activeId = 0L
        save(context)
    }

    fun deleteAll(context: Context) {
        loaded = true
        chats = emptyList(); activeId = 0L
        runCatching { file(context).delete() }
    }

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    private fun save(context: Context) {
        val json = ChatLog.toJson(chats)
        val target = file(context)
        // Off the main thread; written whole to a second file and moved, so a crash mid-write leaves the old history, not half of one.
        Thread {
            // One write at a time, so two turns close together cannot leave the older history on top.
            synchronized(this) { runCatching {
                if (json == "[]") { target.delete(); return@runCatching }
                val tmp = File(target.parentFile, "$FILE.tmp")
                tmp.writeText(json)
                if (!tmp.renameTo(target)) tmp.delete()
            } }
        }.start()
    }
}
