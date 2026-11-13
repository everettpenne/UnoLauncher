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
) : ToolEnv {
    private val resolver = ActionResolver(context, noteSlot)

    /** Best effort: the camera's torch callback reports only when it is already on, so the tool keeps its own idea. */
    private var torchOn = false

    override fun focusOn(): Boolean = focusState()
    override fun toggleFocus() = onToggleFocus()

    override fun startTimer(seconds: Long) = IslandTools.startTimer(context, seconds * 1000L)
    override fun cancelTimer() = IslandTools.cancelTimer(context)

    override fun toggleStopwatch(): Boolean {
        IslandTools.toggleStopwatch(context)
        return IslandTools.swRunning
    }

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
