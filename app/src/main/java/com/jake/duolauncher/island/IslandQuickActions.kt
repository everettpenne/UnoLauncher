package com.jake.duolauncher.island

import com.jake.duolauncher.*
import android.content.Context
import androidx.compose.ui.graphics.toArgb
import com.jake.duolauncher.panel.IosGreen

/**
 * A long press on the pill, as on iOS: the island opens on a small card for the activity it is showing, with that activity's own buttons
 * (pause a timer, skip a track, end a call), instead of the whole open island. Built from what the island already has; nothing new is read.
 */
internal object IslandQuickActions {
    const val CARD_SECONDS = 6

    /** The buttons, in order, for [kind]: pure, so each activity's choices are tested. */
    fun titles(kind: LiveKind, playing: Boolean = false, stopwatchRunning: Boolean = false, canHangUp: Boolean = false,
        canOpen: Boolean = false): List<String> = when (kind) {
        LiveKind.TIMER -> listOf("+1 Min", "Cancel")
        LiveKind.STOPWATCH -> listOf(if (stopwatchRunning) "Pause" else "Resume", "Reset")
        LiveKind.MEDIA -> listOf("Previous", if (playing) "Pause" else "Play", "Next")
        LiveKind.CALL -> listOfNotNull("End".takeIf { canHangUp }, "Open".takeIf { canOpen })
        LiveKind.UPDATE -> listOfNotNull("Open".takeIf { canOpen })
        else -> emptyList()
    }.take(PeekActionRules.MAX_ACTIONS)

    /** The card for [kind], or null when it has nothing to offer (the long press then opens the activity's app, as before). */
    fun card(context: Context, kind: LiveKind?, nowMs: Long): PeekNotification? {
        val k = kind ?: return null
        fun card(title: String, text: String?, actions: Map<String, () -> Unit>, packageName: String = context.packageName,
            open: android.app.PendingIntent? = null, tint: Int? = null) = PeekNotification(packageName, title, title, text, null, open,
            CARD_SECONDS, tint, titles(k, NotificationFeed.nowPlaying?.playing == true, IslandTools.swRunning,
                NotificationFeed.ongoingCall?.hangUp != null, open != null).mapNotNull { t -> actions[t]?.let { PeekAction(t, null, run = it) } },
            quickActions = true, kind = k)
        val timerTint = IslandThemes.active.timer.toArgb()
        return when (k) {
            LiveKind.TIMER -> if (!IslandTools.timerActive) null else card("Timer", "${MediaTimes.clock(IslandTools.timerEndAt - nowMs)} left", mapOf(
                "+1 Min" to { IslandTools.startTimer(context, (IslandTools.timerEndAt - System.currentTimeMillis()).coerceAtLeast(0L) + 60_000L) },
                "Cancel" to { IslandTools.cancelTimer(context) }), tint = timerTint)
            LiveKind.STOPWATCH -> card("Stopwatch", MediaTimes.clock(IslandTools.elapsedMs(nowMs)), mapOf(
                "Pause" to { IslandTools.toggleStopwatch(context) }, "Resume" to { IslandTools.toggleStopwatch(context) },
                "Reset" to { IslandTools.resetStopwatch(context) }), tint = timerTint)
            LiveKind.MEDIA -> NotificationFeed.nowPlaying?.let { now ->
                val controls = now.controller.transportControls
                card(now.title ?: "Now Playing", now.artist, mapOf(
                    "Previous" to { runCatching { controls.skipToPrevious() } }, "Next" to { runCatching { controls.skipToNext() } },
                    "Pause" to { runCatching { controls.pause() } }, "Play" to { runCatching { controls.play() } }),
                    packageName = now.controller.packageName, tint = IslandThemes.active.playback.toArgb())
            }
            LiveKind.CALL -> NotificationFeed.ongoingCall?.let { call ->
                card(call.caller, "Call", mapOf(
                    "End" to { call.hangUp?.let { runCatching { it.send() } } },
                    "Open" to { sendTapAction(context, call.openIntent) }), packageName = call.packageName, open = call.openIntent, tint = IosGreen.toArgb())
            }
            LiveKind.UPDATE -> NotificationFeed.liveUpdates.firstOrNull()?.let { u ->
                card(u.title, u.text, mapOf("Open" to { sendTapAction(context, u.openIntent) }), packageName = u.packageName, open = u.openIntent,
                    tint = u.color.takeIf { it != 0 })
            }
            else -> null
        }
    }
}
