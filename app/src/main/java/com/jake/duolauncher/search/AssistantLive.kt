package com.jake.duolauncher.search

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.jake.duolauncher.island.ActivitySource
import com.jake.duolauncher.island.LiveUpdate

/**
 * The assistant's turn, as one of the island's live activities: "Thinking…" on the pill while the model works, "Ready" when the answer
 * is waiting, and a card whose Open goes back to search to read it. It rides in [com.jake.duolauncher.NotificationFeed.liveUpdates], which
 * the island already draws (pill text, long-press card, quick action), so nothing in the island had to learn a new activity.
 */
internal object AssistantLive {
    const val KEY = "unolab.assistant"

    /** The entry for the session's state, or null when there is nothing to show. */
    fun of(context: Context, nowMs: Long): LiveUpdate? {
        val s = AssistantSession.state
        if (s is AssistantSession.State.Idle) return null
        val query = when (s) {
            is AssistantSession.State.Running -> s.query
            is AssistantSession.State.Ready -> s.query
            is AssistantSession.State.Failed -> s.query
            else -> ""
        }
        val text = when (s) {
            is AssistantSession.State.Running -> query
            is AssistantSession.State.Ready -> s.answer?.take(160) ?: "An action is ready in search"
            is AssistantSession.State.Failed -> s.why
            else -> null
        }
        val short = when (s) {
            is AssistantSession.State.Running -> "Thinking…"
            is AssistantSession.State.Ready -> "Ready"
            is AssistantSession.State.Failed -> "Couldn't answer"
            else -> null
        }
        return LiveUpdate(
            key = KEY,
            packageName = context.packageName,
            appLabel = "Uno",
            title = "Ask Qwen",
            text = text,
            shortText = short,
            progress = 0,
            progressMax = 0,
            indeterminate = s is AssistantSession.State.Running,
            chronometerBase = null,
            countDown = false,
            icon = null,
            color = 0,
            postTime = nowMs,
            openIntent = open(context),
            source = ActivitySource.PROGRESS,
        )
    }

    /**
     * The island's "Ready" tapped: the answer in search over whatever is on screen. Straight to the surface, in this process, when search can
     * be put over the app; otherwise through [AssistActivity] (the same as [LiveUpdate.openIntent]), which falls back to Home's search.
     */
    fun openAnswer(context: Context, fallback: PendingIntent?) {
        if (AssistOverlay.canShow(context)) AssistOverlay.show(context, null, com.jake.duolauncher.island.SurfaceSeed.Answer)
        else com.jake.duolauncher.island.sendTapAction(context, fallback)
    }

    /**
     * Tapping the island opens search where the session's answer is shown: over whatever is on screen, as the assist gesture does
     * ([AssistActivity] puts search over the app in front, asks for the unlock first if the phone is locked, and falls back to Home's search
     * without the permission it needs). It used to open Home, taking the person away from what they were doing.
     */
    private fun open(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 7,
        Intent(context, AssistActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
