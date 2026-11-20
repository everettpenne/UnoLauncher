package com.jake.duolauncher.search

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** One tool the assistant used, or planned to: its name, the values it was given, and what came of it. [ok] is null for a step that was
 *  planned and never run (the plan was only shown, was turned down at the confirmation, or stopped at an earlier step). */
internal data class LoggedTool(val tool: String, val params: Map<String, String>, val ok: Boolean?, val say: String = "", val ms: Long = 0)

/**
 * One response from the assistant, written down whole so it can be judged later and learned from: what was said, everything the model
 * was shown with it, every reply the model wrote (word for word, before anything was cleaned or checked), the tools used and what each
 * did, what the person was shown, and how it ended. The person's own verdict ([rating]) is added when they give one.
 *
 * The fields a planner training row is built from are kept under the names that row uses: [request] is its `Request:`, [context] its
 * `<context>`, [grounding] its `<known>`, [catalogue] the hash of the tool catalogue the reply was written against, and the last of
 * [raw] its completion.
 */
internal data class LogEntry(
    /** When the turn ended; also the time of its turn in the chat history, where it has one, which is how a past turn finds its entry. */
    val id: Long,
    /** How the turn was handled: `answer` (a question answered), `action` (the quick-action reading), `plan` (the planner), `resume`
     *  (a plan carried on with the value it had asked for), `again`, `amend`, `undo` (follow-ups that need no planner) or `refers`
     *  (a follow-up planned with the last task beside it). */
    val route: String,
    /** What the person typed for this turn. */
    val said: String,
    /** What the model was asked: [said], or for an answer to a question back, the request it belongs to with the question and answer. */
    val request: String = said,
    val context: String = "",
    val grounding: String = "",
    val catalogue: String = "",
    val raw: List<String> = emptyList(),
    val tools: List<LoggedTool> = emptyList(),
    /** What the person was shown. */
    val reply: String = "",
    /** `answered`, `done`, `asked` (a question back), `refused`, `failed`, `cancelled` (turned down at the confirmation), `planned`
     *  (plan-only mode: shown, not run), `cut_short` (a follow-up was sent while it was still being written), `suggested` (an action
     *  offered to tap). */
    val outcome: String,
    val ms: Long = 0,
    /** 1 thumbs-up (it acted correctly or helpfully), -1 thumbs-down (not what was meant), 0 not rated. */
    val rating: Int = 0,
    val ratedAtMs: Long = 0,
    /** The Undo beside the result was used. */
    val undone: Boolean = false,
    /** A suggested action was tapped. */
    val tapped: Boolean = false,
    val launcher: String = "",
    val lab: String = "",
)

/**
 * How the log is written down and kept in bounds. Pure, so it is unit-tested.
 *
 * The file is lines of JSON and is only ever added to: an entry is a line, and a later verdict on it (a rating, an undo, a tap) is a
 * short line naming the entry's id, folded into it when the file is read. Nothing is rewritten for a thumb, and a crash mid-write
 * costs at most the last line.
 */
internal object AssistantLogRules {
    const val VERSION = 1
    const val MAX_ENTRIES = 2000
    const val MAX_TEXT = 6000

    private fun clip(text: String) = if (text.length > MAX_TEXT) text.take(MAX_TEXT) else text

    fun line(e: LogEntry): String = JSONObject().put("v", VERSION).put("id", e.id).put("route", e.route)
        .put("said", clip(e.said)).put("request", clip(e.request)).put("context", clip(e.context)).put("grounding", clip(e.grounding))
        .put("catalogue", e.catalogue).put("raw", JSONArray(e.raw.map(::clip)))
        .put("tools", JSONArray(e.tools.map { t ->
            JSONObject().put("tool", t.tool).put("params", JSONObject(t.params.mapValues { clip(it.value) }))
                .put("ok", t.ok ?: JSONObject.NULL).put("say", clip(t.say)).put("ms", t.ms)
        }))
        .put("reply", clip(e.reply)).put("outcome", e.outcome).put("ms", e.ms)
        .put("rating", e.rating).put("rated_at", e.ratedAtMs).put("undone", e.undone).put("tapped", e.tapped)
        .put("launcher", e.launcher).put("lab", e.lab).toString()

    /** The line that records a verdict on entry [id]: only what it changes. */
    fun patch(id: Long, rating: Int? = null, ratedAtMs: Long = 0, undone: Boolean? = null, tapped: Boolean? = null): String =
        JSONObject().put("patch", id).apply {
            if (rating != null) { put("rating", rating.coerceIn(-1, 1)); put("rated_at", ratedAtMs) }
            if (undone != null) put("undone", undone)
            if (tapped != null) put("tapped", tapped)
        }.toString()

    /** Reads the file: entries in the order they were written, each with the verdicts that follow it. A line that does not read is
     *  skipped, never thrown: a damaged line costs that line. */
    fun read(lines: Sequence<String>): List<LogEntry> {
        val entries = LinkedHashMap<Long, LogEntry>()
        for (line in lines) {
            if (line.isBlank()) continue
            val json = runCatching { JSONObject(line) }.getOrNull() ?: continue
            if (json.has("patch")) {
                val id = json.optLong("patch")
                val was = entries[id] ?: continue
                entries[id] = was.copy(
                    rating = if (json.has("rating")) json.optInt("rating").coerceIn(-1, 1) else was.rating,
                    ratedAtMs = if (json.has("rating")) json.optLong("rated_at") else was.ratedAtMs,
                    undone = if (json.has("undone")) json.optBoolean("undone") else was.undone,
                    tapped = if (json.has("tapped")) json.optBoolean("tapped") else was.tapped)
                continue
            }
            val id = json.optLong("id")
            if (id == 0L || !json.has("outcome")) continue
            val tools = json.optJSONArray("tools")?.let { array ->
                (0 until array.length()).mapNotNull { i ->
                    val t = array.optJSONObject(i) ?: return@mapNotNull null
                    val params = t.optJSONObject("params")?.let { p -> p.keys().asSequence().associateWith { p.optString(it) } }.orEmpty()
                    LoggedTool(t.optString("tool"), params, if (t.isNull("ok")) null else t.optBoolean("ok"), t.optString("say"), t.optLong("ms"))
                }
            }.orEmpty()
            val raw = json.optJSONArray("raw")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            entries[id] = LogEntry(id, json.optString("route"), json.optString("said"), json.optString("request"), json.optString("context"),
                json.optString("grounding"), json.optString("catalogue"), raw, tools, json.optString("reply"), json.optString("outcome"),
                json.optLong("ms"), json.optInt("rating").coerceIn(-1, 1), json.optLong("rated_at"), json.optBoolean("undone"),
                json.optBoolean("tapped"), json.optString("launcher"), json.optString("lab"))
        }
        return entries.values.toList()
    }

    /** The newest [MAX_ENTRIES], oldest first: what is kept when the file is tidied. */
    fun kept(entries: List<LogEntry>): List<LogEntry> = entries.takeLast(MAX_ENTRIES)

    /** A tap on a thumb: it sets that verdict, and a second tap on the same one takes it back. */
    fun tapped(current: Int, thumb: Int): Int = if (current == thumb) 0 else thumb

    /** An id no entry has: the time, moved on if another turn ended in the same millisecond. */
    fun freshId(entries: List<LogEntry>, nowMs: Long): Long {
        var id = nowMs
        while (entries.any { it.id == id }) id++
        return id
    }

    /** The whole log as it is exported: one entry a line, verdicts folded in. */
    fun export(entries: List<LogEntry>): String = entries.joinToString("") { line(it) + "\n" }
}

/**
 * The assistant log: every response the assistant gives, with what it was shown and the tools it used, and the thumbs-up or thumbs-down
 * the person gives it, kept so the assistant can be improved from how it really did. Off until switched on (Search settings), because
 * an entry holds the phone's facts that went with the question.
 *
 * It lives in one file in the launcher's private storage: no other app can read it, Android's backup and device transfer leave it
 * behind (`data_extraction_rules.xml` excludes all of the launcher's files), the settings backup does not include it, and it is never
 * written to the phone's system log or sent anywhere. It leaves the phone only when the person exports it to a file they choose.
 * It is separate from the chat history: deleting conversations does not delete it, and it has its own delete.
 */
internal object AssistantLog {
    private const val FILE = "assistant-log.jsonl"
    private const val PREFS = "extras"
    private const val KEY = "assistantLog"

    /** Whether responses are being logged. Read once, and set by the switch. */
    var on by mutableStateOf(false)
        private set
    var entries by mutableStateOf<List<LogEntry>>(emptyList())
        private set
    private var loaded = false
    /** One writer, so lines land in the order they were made. */
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "uno-assistant-log").apply { isDaemon = true } }

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    /** Reads the switch and what has been logged; cheap to call again. The file is read off the main thread, so opening search never
     *  waits on it; the log is read whether or not it is on, so one that was kept can still be saved or deleted after it is switched off. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        on = runCatching { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false) }.getOrDefault(false)
        val app = context.applicationContext
        writer.execute { read(app) }
    }

    /** On the writer's thread, so it sees every line written before it. What has been logged in memory meanwhile is kept. */
    private fun read(context: Context) {
        val began = epoch
        val all = runCatching { file(context).takeIf { it.exists() }?.useLines { AssistantLogRules.read(it) } }.getOrNull().orEmpty()
        main.post {
            // The log was deleted while it was being read: what was read is gone.
            if (began != epoch) return@post
            val newer = entries.associateBy { it.id }
            entries = AssistantLogRules.kept(all.map { newer[it.id] ?: it } + entries.filter { e -> all.none { it.id == e.id } })
            // Tidied when it has outgrown its bound: the newest are written back whole, verdicts folded in.
            if (all.size > AssistantLogRules.MAX_ENTRIES) rewrite(context, entries)
        }
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    /** Moves on each time the log is deleted, so a read that began before a delete does not bring the entries back. */
    @Volatile private var epoch = 0

    fun setOn(context: Context, value: Boolean) {
        load(context)
        on = value
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
    }

    /** An id for a turn that is ending now; the same id is given to its turn in the chat history. */
    fun newId(nowMs: Long = System.currentTimeMillis()): Long = AssistantLogRules.freshId(entries, nowMs)

    /** Writes [entry] down, when the log is on. The launcher's and Uno Lab's versions are added here. */
    fun record(context: Context, entry: LogEntry) {
        load(context)
        if (!on) return
        val app = context.applicationContext
        val stamped = entry.copy(launcher = entry.launcher.ifEmpty { versionOf(app, app.packageName) }, lab = entry.lab.ifEmpty { versionOf(app, LAB) })
        entries = AssistantLogRules.kept(entries.filter { it.id != stamped.id } + stamped)
        append(app, AssistantLogRules.line(stamped))
    }

    fun entry(id: Long?): LogEntry? = if (id == null || !on) null else entries.firstOrNull { it.id == id }

    /** A tap on a thumb for entry [id]: [thumb] is 1 or -1. */
    fun rate(context: Context, id: Long, thumb: Int, nowMs: Long = System.currentTimeMillis()) {
        val was = entry(id) ?: return
        val rating = AssistantLogRules.tapped(was.rating, thumb)
        entries = entries.map { if (it.id == id) it.copy(rating = rating, ratedAtMs = nowMs) else it }
        append(context, AssistantLogRules.patch(id, rating = rating, ratedAtMs = nowMs))
    }

    fun markUndone(context: Context, id: Long?) {
        val was = entry(id) ?: return
        entries = entries.map { if (it.id == was.id) it.copy(undone = true) else it }
        append(context, AssistantLogRules.patch(was.id, undone = true))
    }

    fun markTapped(context: Context, id: Long?) {
        val was = entry(id) ?: return
        if (was.tapped) return
        entries = entries.map { if (it.id == was.id) it.copy(tapped = true) else it }
        append(context, AssistantLogRules.patch(was.id, tapped = true))
    }

    /** The log as the lines to save in a file the person chose. */
    fun exported(context: Context): String { load(context); return AssistantLogRules.export(entries) }

    fun deleteAll(context: Context) {
        epoch++
        entries = emptyList()
        val target = file(context)
        writer.execute { runCatching { target.delete() } }
    }

    private fun append(context: Context, line: String) {
        val target = file(context)
        writer.execute { runCatching { target.appendText(line + "\n") } }
    }

    private fun rewrite(context: Context, kept: List<LogEntry>) {
        val target = file(context)
        writer.execute { runCatching {
            // Written whole to a second file and moved, so a crash mid-write leaves the old log, not half of one.
            val tmp = File(target.parentFile, "$FILE.tmp")
            tmp.writeText(AssistantLogRules.export(kept))
            if (!tmp.renameTo(target)) tmp.delete()
        } }
    }

    private const val LAB = "com.everettpenne.unolab"
    private fun versionOf(context: Context, pkg: String): String =
        runCatching { context.packageManager.getPackageInfo(pkg, 0).versionName }.getOrNull().orEmpty()

    /** For tests: the log in memory only, on or off, with nothing read or written. */
    @androidx.annotation.VisibleForTesting internal fun forTests(value: Boolean, with: List<LogEntry> = emptyList()) { loaded = true; on = value; entries = with }
}
