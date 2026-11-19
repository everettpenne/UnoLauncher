package com.jake.duolauncher.search.agent

import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserManager
import android.util.Log
import com.jake.duolauncher.AppEntry
import com.jake.duolauncher.island.IslandTools
import com.jake.duolauncher.search.ActionLogic
import com.jake.duolauncher.search.ActionResolver
import com.jake.duolauncher.search.ContactActionKind
import com.jake.duolauncher.widgets.WidgetData
import com.jake.duolauncher.widgets.WidgetText
import java.time.LocalTime

/**
 * The real [ToolEnv] for the launcher: every tool's side effect expressed in the launcher's own machinery (the island's timer, stopwatch and
 * torch, Focus, the Note widget) or a standard Android intent handed to another app. The fake one in the tests is the same interface, which is
 * why every tool is testable without a phone.
 */
private const val TAG = "UnoAgent"

internal class AndroidToolEnv(
    private val context: Context,
    override val noteSlot: Int?,
    override val apps: List<AppEntry>,
    private val focusState: () -> Boolean,
    private val onToggleFocus: () -> Unit,
    override val checklistSlot: Int? = null,
) : ToolEnv {
    private val resolver = ActionResolver(context, noteSlot)

    /** Best effort: the camera's torch callback reports only when it is already on, so the tool keeps its own idea. */
    private var torchOn = false

    /** Replies need the notification listener (the person's opt-in notification access) to be connected. */
    override fun capabilities(): Set<Capability> =
        if (com.jake.duolauncher.NotificationFeed.connected) setOf(Capability.Notifications) else emptySet()

    override fun replyTargets(): List<ReplyTarget> = com.jake.duolauncher.NotificationFeed.replyable.map { it.target }

    override fun sendReply(key: String, text: String): Boolean {
        val found = com.jake.duolauncher.NotificationFeed.replyable.firstOrNull { it.target.key == key } ?: return false
        return com.jake.duolauncher.island.sendReply(context, found.action, text)
    }

    override fun focusOn(): Boolean = focusState()
    override fun toggleFocus() = onToggleFocus()

    override fun startTimer(seconds: Long) = IslandTools.startTimer(context, seconds * 1000L)
    override fun cancelTimer() = IslandTools.cancelTimer(context)

    override fun toggleStopwatch(): Boolean {
        IslandTools.toggleStopwatch(context)
        return IslandTools.swRunning
    }

    override fun setTorch(on: Boolean) { IslandTools.setTorch(context, on); torchOn = on }
    override fun stopwatchRunning(): Boolean = IslandTools.swRunning
    override fun stopwatchStarted(): Boolean = IslandTools.stopwatchStarted
    override fun resetStopwatch() = IslandTools.resetStopwatch(context)
    override fun timerRunning(): Boolean = IslandTools.timerActive

    override fun toggleTorch(): Boolean {
        IslandTools.toggleTorch(context)
        torchOn = !torchOn
        return torchOn
    }

    override fun openAlarm(hour: Int?, minute: Int?, inMinutes: Int?) {
        val now = LocalTime.now()
        val target = when {
            hour != null && minute != null -> hour to minute
            inMinutes != null -> ActionLogic.alarmAt(inMinutes, now.hour, now.minute)
            else -> return
        }
        resolver.open(ActionLogic.alarmIntent(target.first, target.second))
    }

    override fun openApp(entry: AppEntry): Boolean = runCatching {
        val user = context.getSystemService(UserManager::class.java).getUserForSerialNumber(entry.userSerial) ?: return@runCatching false
        context.getSystemService(LauncherApps::class.java).startMainActivity(entry.component, user, null, null)
        true
    }.getOrElse {
        Log.i(TAG, "openApp ${entry.component} failed: $it")
        false
    }

    override fun openDialer(number: String) = resolver.openContact(ContactActionKind.DIAL, number)
    override fun openMessages(number: String) = resolver.openContact(ContactActionKind.TEXT, number)
    override fun openMessage(number: String, text: String) = resolver.open(ActionLogic.smsIntent(number, text))

    override fun media(action: String): Boolean {
        val controls = com.jake.duolauncher.NotificationFeed.nowPlaying?.controller?.transportControls ?: return false
        return runCatching {
            when (action) { "play" -> controls.play(); "pause" -> controls.pause(); "next" -> controls.skipToNext(); "previous" -> controls.skipToPrevious(); else -> return false }
        }.isSuccess
    }

    private val audio get() = context.getSystemService(android.media.AudioManager::class.java)
    private val stream = android.media.AudioManager.STREAM_MUSIC

    override fun volume(): Int? = runCatching {
        val max = audio.getStreamMaxVolume(stream).takeIf { it > 0 } ?: return null
        Math.round(audio.getStreamVolume(stream) * 100f / max)
    }.getOrNull()

    override fun setVolume(percent: Int): Boolean = runCatching {
        val max = audio.getStreamMaxVolume(stream)
        audio.setStreamVolume(stream, Math.round(percent.coerceIn(0, 100) * max / 100f), android.media.AudioManager.FLAG_SHOW_UI)
    }.isSuccess

    override fun addChecklist(item: String): Boolean {
        val slot = checklistSlot ?: return false
        val items = com.jake.duolauncher.widgets.Checklist.parse(WidgetData.get(slot, "items"))
        val next = com.jake.duolauncher.widgets.Checklist.add(items, item)
        if (next == items) return false
        WidgetData.set(slot, "items", com.jake.duolauncher.widgets.Checklist.serialize(next))
        return true
    }

    override fun removeChecklist(item: String) {
        val slot = checklistSlot ?: return
        val items = com.jake.duolauncher.widgets.Checklist.parse(WidgetData.get(slot, "items"))
        val at = items.indexOfLast { it.text.equals(item.trim(), ignoreCase = true) }
        if (at >= 0) WidgetData.set(slot, "items", com.jake.duolauncher.widgets.Checklist.serialize(com.jake.duolauncher.widgets.Checklist.removeAt(items, at)))
    }

    override fun openDirections(place: String) = resolver.open(ActionLogic.viewIntent("geo:0,0?q=" + android.net.Uri.encode(place)))

    override fun openUrl(url: String) = resolver.open(ActionLogic.viewIntent(url))

    override fun openCalendar(title: String, inMinutes: Int, durationMinutes: Int) {
        val (begin, end) = ActionLogic.calendarSpan(System.currentTimeMillis(), inMinutes, durationMinutes)
        resolver.open(ActionLogic.calendarIntent(title, begin, end))
    }

    override fun openWebSearch(query: String) = resolver.open(ActionLogic.webSearchIntent(query))

    override fun openSettingsPanel(panel: String) {
        val action = when (panel) {
            "wifi" -> android.provider.Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
            "nfc" -> android.provider.Settings.ACTION_NFC_SETTINGS
            "airplane" -> android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "display" -> android.provider.Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> android.provider.Settings.ACTION_SOUND_SETTINGS
            "battery" -> android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS
            else -> return
        }
        resolver.open(ActionLogic.settingsIntent(action))
    }

    override fun writeNote(text: String): String? {
        val slot = noteSlot ?: return null
        val previous = WidgetData.get(slot, "note")
        WidgetData.set(slot, "note", WidgetText.note(text))
        return previous
    }
}
