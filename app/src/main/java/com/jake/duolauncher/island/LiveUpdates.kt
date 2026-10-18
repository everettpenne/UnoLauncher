package com.jake.duolauncher.island

import com.jake.duolauncher.*
import android.app.PendingIntent
import android.graphics.Bitmap

/** One Android 16 Live Update (a "promoted ongoing" notification: a ride, a delivery, a timer, navigation), reduced to what the
 * island shows. Held in memory only while the notification exists; the text of a chat or message notification is never read (only a promoted Live Update's own title, body and status are), and nothing here is
 * stored or sent.
 */
internal data class LiveUpdate(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String?,
    /** The app's own very short status ("12 min"), shown in the status bar chip on Android 16. */
    val shortText: String?,
    val progress: Int,
    val progressMax: Int,
    val indeterminate: Boolean,
    /** The base time of a chronometer, when the notification shows one, and whether it counts down. */
    val chronometerBase: Long?,
    val countDown: Boolean,
    val icon: Bitmap?,
    val color: Int,
    val postTime: Long,
    val openIntent: PendingIntent?,
    /** A navigation instruction's turn arrow (the notification's large icon). Read only for navigation, never for other activities. */
    val largeIcon: Bitmap? = null,
    val source: ActivitySource = ActivitySource.PROMOTED,
)

/** What kind of thing an island activity (a [LiveUpdate]) came from. */
internal enum class ActivitySource { PROMOTED, NAVIGATION, PROGRESS }

/** Pure rules for recognising and presenting Live Updates, so they are unit-tested apart from the notification service. */
internal object LiveUpdateLogic {
    /** Notification categories whose progress bars are not "a task" (a call, a media player, navigation has its own kind). */
    private val NOT_TASKS = setOf("call", "transport", "navigation", "alarm", "reminder", "msg", "email", "social")

    /** Whether the notification feeds the island as a Live Update, a navigation instruction or a progress task (a download, an
     * upload, an install), or nothing. [promoted] is the Android 16 flag; the other kinds work on any Android version. A progress task
     * needs a real, unfinished, determinate progress bar on an ongoing notification, from an app other than a player or a call.
     */
    fun classify(promoted: Boolean, category: String?, ongoing: Boolean, progress: Int, progressMax: Int, indeterminate: Boolean,
        liveUpdatesOn: Boolean, navigationOn: Boolean, tasksOn: Boolean): ActivitySource? = when {
        promoted && liveUpdatesOn -> ActivitySource.PROMOTED
        navigationOn && ongoing && category == "navigation" -> ActivitySource.NAVIGATION
        tasksOn && ongoing && !indeterminate && progressMax > 0 && progress in 0 until progressMax &&
            (category == null || category !in NOT_TASKS) -> ActivitySource.PROGRESS
        else -> null
    }

    /** The pure notification → [LiveUpdate] field mapping, so it is unit-tested apart from the notification service. It takes the raw
     * values the listener already reads from a notification and reduces them to the island's model. Context-derived values — the
     * small icon, the content intent and a navigation instruction's large (turn-arrow) icon — are not taken here: the returned
     * update leaves them null and the caller fills them in.
     *
     * [promoted] is the Android 16 flag and [category] the notification's category; together with the ongoing flag and the progress
     * fields they drive [classify] in the caller to produce [source], which then steers this mapping: the short status is kept only for a
     * promoted Live Update, and the body text is dropped for a progress task (which shows its percentage, not a file name).
     */
    fun build(
        key: String,
        packageName: String,
        appLabel: String,
        postTime: Long,
        source: ActivitySource,
        promoted: Boolean,
        category: String?,
        title: String,
        shortCriticalText: String?,
        text: String?,
        progress: Int,
        progressMax: Int,
        indeterminate: Boolean,
        chronometerBase: Long?,
        countDown: Boolean,
        color: Int,
    ): LiveUpdate = LiveUpdate(
        key = key,
        packageName = packageName,
        appLabel = appLabel,
        title = title,
        text = if (source == ActivitySource.PROGRESS) null else text,
        shortText = if (source == ActivitySource.PROMOTED) shortCriticalText else null,
        progress = progress,
        progressMax = progressMax,
        indeterminate = indeterminate,
        chronometerBase = chronometerBase,
        countDown = countDown,
        icon = null,
        color = color,
        postTime = postTime,
        openIntent = null,
        largeIcon = null,
        source = source,
    )

    /** The exact set of notification fields the island reads for a Live Update, named as they are in PRIVACY.md. A test pins this
     * list to that document, so the wording cannot drift: change one and you must change the other.
     */
    val READ_FIELDS: List<String> = listOf(
        "title", "short status", "body text", "progress", "progress max", "indeterminate",
        "chronometer base", "count-down", "small icon", "color", "content intent", "category", "large icon",
    )

    /** [android.app.Notification.FLAG_PROMOTED_ONGOING], repeated so this file's logic needs no Android classes. */
    const val PROMOTED_FLAG = 0x40000
    const val MAX_SHOWN = 3
    private const val MAX_GLANCE_CHARS = 14

    /** The system marks a notification promoted once it has asked and qualified; that flag is the one thing to trust. */
    fun isLiveUpdate(flags: Int): Boolean = flags and PROMOTED_FLAG != 0

    fun isOngoing(flags: Int): Boolean = flags and (0x2 or 0x40) != 0 // FLAG_ONGOING_EVENT or FLAG_FOREGROUND_SERVICE

    /** How far along it is, 0 to 1, or null when it has no progress or its progress is indeterminate. */
    fun fraction(progress: Int, max: Int, indeterminate: Boolean): Float? =
        if (indeterminate || max <= 0) null else (progress.toFloat() / max).coerceIn(0f, 1f)

    /** A chronometer as m:ss (or h:mm:ss): elapsed for a count-up, remaining for a count-down. */
    fun timer(baseMs: Long, countDown: Boolean, nowMs: Long): String {
        val seconds = (if (countDown) baseMs - nowMs else nowMs - baseMs).coerceAtLeast(0L) / 1000L
        val h = seconds / 3600; val m = seconds % 3600 / 60; val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** What fits beside the camera: the app's own short text, else the timer, else the percentage, else the start of the title. */
    fun glance(update: LiveUpdate, nowMs: Long): String {
        update.shortText?.trim()?.takeIf { it.isNotEmpty() }?.let { return it.take(MAX_GLANCE_CHARS) }
        update.chronometerBase?.let { return timer(it, update.countDown, nowMs) }
        fraction(update.progress, update.progressMax, update.indeterminate)?.let { return "${(it * 100).toInt()}%" }
        return update.title.take(MAX_GLANCE_CHARS)
    }

    /** Newest first, at most [MAX_SHOWN], one per notification key. */
    fun ordered(updates: List<LiveUpdate>): List<LiveUpdate> =
        updates.distinctBy { it.key }.sortedByDescending { it.postTime }.take(MAX_SHOWN)

    /** How often the listener re-reads the active notifications even when no post or removal event arrived. A Live
     * Update whose app ends or dies without a cancellation event reaching the listener (or whose notification is
     * removed while the listener is re-binding) is dropped on the next sweep instead of lingering forever. */
    const val SAFETY_SWEEP_MS = 60_000L

    /** A still-present Live Update that has gone this long without a fresh post is stale: it keeps its card, but gives up its pill slot. */
    const val STALE_MS = 10 * 60_000L

    /** Whether a still-present update has gone quiet long enough to demote from the pill. A navigation instruction (which only
     * reposts when the manoeuvre changes) and anything with a counting clock stay fresh however old their notification is, because
     * the clock is still moving. [nowMs] and [update.postTime] are both wall-clock milliseconds. */
    fun isStale(update: LiveUpdate, nowMs: Long, staleAfterMs: Long = STALE_MS): Boolean =
        update.source != ActivitySource.NAVIGATION && update.chronometerBase == null && nowMs - update.postTime > staleAfterMs

    /** The updates whose notification is still among [presentKeys], newest first. Anything whose notification has
     * gone is dropped: the single rule that clears a navigation icon once its app ends or dies without a removal
     * event reaching us. */
    fun present(updates: List<LiveUpdate>, presentKeys: Set<String>): List<LiveUpdate> =
        ordered(updates.filter { it.key in presentKeys })

    /** True while any shown update has a clock that needs a once-a-second redraw. */
    fun needsSecondTick(updates: List<LiveUpdate>): Boolean = updates.any { it.chronometerBase != null && it.shortText.isNullOrBlank() }
}
