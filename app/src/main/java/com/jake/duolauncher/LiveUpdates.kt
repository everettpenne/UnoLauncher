package com.jake.duolauncher

import android.app.PendingIntent
import android.graphics.Bitmap

/** One Android 16 Live Update (a "promoted ongoing" notification: a ride, a delivery, a timer, navigation), reduced to what the
 * island shows. Held in memory only while the notification exists; the text of a message is never read, and nothing here is
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
)

/** Pure rules for recognising and presenting Live Updates, so they are unit-tested apart from the notification service. */
internal object LiveUpdateLogic {
    /** [android.app.Notification.FLAG_PROMOTED_ONGOING], repeated so this file's logic needs no Android classes. */
    const val PROMOTED_FLAG = 0x40000
    const val MAX_SHOWN = 3
    private const val MAX_GLANCE_CHARS = 14

    /** The system marks a notification promoted once it has asked and qualified; that flag is the one thing to trust. */
    fun isLiveUpdate(flags: Int): Boolean = flags and PROMOTED_FLAG != 0

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

    /** True while any shown update has a clock that needs a once-a-second redraw. */
    fun needsSecondTick(updates: List<LiveUpdate>): Boolean = updates.any { it.chronometerBase != null && it.shortText.isNullOrBlank() }
}
