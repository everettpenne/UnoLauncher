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
private const val EVENT_DAYS = 3
private const val MAX_EVENTS = 60

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

    private fun start(intent: android.content.Intent): Boolean =
        runCatching { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrElse { Log.w(TAG, "could not start ${intent.action}", it); false }

    override fun openCamera(mode: String): Boolean = start(when (mode) {
        "video" -> android.content.Intent(android.provider.MediaStore.INTENT_ACTION_VIDEO_CAMERA)
        // There is no public way to ask for the front camera; these extras are the ones camera apps commonly honour, and are ignored otherwise.
        "selfie" -> android.content.Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            .putExtra("android.intent.extras.CAMERA_FACING", 1).putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
        else -> android.content.Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
    })

    override fun composeEmail(to: String, subject: String, body: String): Boolean = start(
        android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:" + android.net.Uri.encode(to)))
            .putExtra(android.content.Intent.EXTRA_SUBJECT, subject).putExtra(android.content.Intent.EXTRA_TEXT, body))

    private val ringerAudio get() = context.getSystemService(android.media.AudioManager::class.java)
    private fun dndAllowed() = context.getSystemService(android.app.NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    override fun ringer(): String? = when (ringerAudio?.ringerMode) {
        android.media.AudioManager.RINGER_MODE_NORMAL -> "ring"; android.media.AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
        android.media.AudioManager.RINGER_MODE_SILENT -> "silent"; else -> null
    }

    override fun setRinger(mode: String): Boolean = runCatching {
        ringerAudio?.ringerMode = when (mode) { "ring" -> android.media.AudioManager.RINGER_MODE_NORMAL; "vibrate" -> android.media.AudioManager.RINGER_MODE_VIBRATE
            else -> android.media.AudioManager.RINGER_MODE_SILENT }
        ringer() == mode
    }.getOrDefault(false)

    override fun playFromSearch(what: String): Boolean = start(android.content.Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
        .putExtra(android.provider.MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*").putExtra(android.app.SearchManager.QUERY, what))

    private fun mayWriteSettings() = android.provider.Settings.System.canWrite(context)

    override fun brightness(): Int? = runCatching {
        android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) * 100 / 255
    }.getOrNull()

    override fun setBrightness(percent: Int): Boolean = mayWriteSettings() && runCatching {
        // A level set by hand only holds with adaptive brightness off.
        android.provider.Settings.System.putInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
            android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        android.provider.Settings.System.putInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, (percent * 255 / 100).coerceIn(1, 255))
    }.getOrDefault(false)

    override fun deviceAction(action: String): Boolean {
        val service = com.jake.duolauncher.SystemShadeAccessibilityService.connected() ?: return false
        val global = when (action) {
            "screenshot" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
            "lock" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
            "notifications" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            "recents" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
            else -> return false
        }
        return runCatching { service.performGlobalAction(global) }.getOrDefault(false)
    }

    override fun addContact(name: String, number: String): Boolean = start(android.content.Intent(android.provider.ContactsContract.Intents.Insert.ACTION)
        .setType(android.provider.ContactsContract.RawContacts.CONTENT_TYPE)
        .putExtra(android.provider.ContactsContract.Intents.Insert.NAME, name).putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, number))

    override fun openAppInfo(entry: AppEntry): Boolean = runCatching {
        context.getSystemService(LauncherApps::class.java).startAppDetailsActivity(entry.component, entry.user, null, null); true
    }.getOrDefault(false)

    override fun showAlarms(): Boolean = start(android.content.Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))

    override fun openCalendarAt(day: java.time.LocalDate): Boolean = start(android.content.Intent(android.content.Intent.ACTION_VIEW,
        android.provider.CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
            .appendPath(day.atTime(9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli().toString()).build()))

    private fun calendarAllowed() =
        context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Replies need the notification listener (the person's opt-in notification access) to be connected; a time given by the
     *  calendar needs the calendar permission the person may have given Uno for its widgets and its facts. */
    override fun capabilities(): Set<Capability> = buildSet {
        if (com.jake.duolauncher.NotificationFeed.connected) add(Capability.Notifications)
        if (calendarAllowed()) add(Capability.CalendarRead)
        if (com.jake.duolauncher.SystemShadeAccessibilityService.isConnected()) add(Capability.Accessibility)
        if (dndAllowed()) add(Capability.Ringer)
        if (mayWriteSettings()) add(Capability.SystemSettings)
    }

    /** The next few days of the calendar: titles and start times only, read when a step points at the calendar and kept nowhere. */
    override fun events(): List<CalendarEvent> {
        if (!calendarAllowed()) return emptyList()
        return runCatching {
            val zone = java.time.ZoneId.systemDefault()
            val from = System.currentTimeMillis()
            val uri = android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                android.content.ContentUris.appendId(it, from); android.content.ContentUris.appendId(it, from + EVENT_DAYS * 24L * 60 * 60 * 1000)
            }.build()
            val found = ArrayList<CalendarEvent>()
            context.contentResolver.query(uri, arrayOf(android.provider.CalendarContract.Instances.TITLE, android.provider.CalendarContract.Instances.BEGIN,
                android.provider.CalendarContract.Instances.ALL_DAY), null, null, "${android.provider.CalendarContract.Instances.BEGIN} ASC")?.use { rows ->
                while (rows.moveToNext() && found.size < MAX_EVENTS) {
                    val start = java.time.Instant.ofEpochMilli(rows.getLong(1)).atZone(zone).toLocalDateTime()
                    found += CalendarEvent(rows.getString(0).orEmpty(), start, rows.getInt(2) == 1)
                }
            }
            found
        }.getOrElse { Log.w(TAG, "calendar could not be read", it); emptyList() }
    }

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

    override fun openAlarm(hour: Int?, minute: Int?, inMinutes: Int?) = openAlarm(hour, minute, inMinutes, null)

    override fun openAlarm(hour: Int?, minute: Int?, inMinutes: Int?, label: String?) {
        val now = LocalTime.now()
        val target = when {
            hour != null && minute != null -> hour to minute
            inMinutes != null -> ActionLogic.alarmAt(inMinutes, now.hour, now.minute)
            else -> return
        }
        val alarm = ActionLogic.alarmIntent(target.first, target.second)
        // A reminder's alarm carries what it is for, which the clock app shows when it rings (AlarmClock.EXTRA_MESSAGE).
        resolver.open(if (label.isNullOrBlank()) alarm else alarm.copy(stringExtras = alarm.stringExtras + (android.provider.AlarmClock.EXTRA_MESSAGE to label.take(80))))
    }

    override fun style(): String = com.jake.duolauncher.appearance.UnoPersonality.level.name.lowercase()

    override fun setStyle(level: String): Boolean {
        val next = com.jake.duolauncher.appearance.Personality.entries.firstOrNull { it.name.equals(level, ignoreCase = true) } ?: return false
        com.jake.duolauncher.appearance.UnoPersonality.set(context, next)
        return true
    }

    override fun openUnoSettings(page: String): Boolean = runCatching {
        context.startActivity(android.content.Intent(context, com.jake.duolauncher.MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("duo_destination", if (page == "keyboard") "keyboard" else "customize"))
    }.isSuccess

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
            "mobile_data" -> android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS
            "hotspot" -> android.provider.Settings.ACTION_WIRELESS_SETTINGS
            "location" -> android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "storage" -> android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            "apps" -> android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS
            "notifications" -> android.provider.Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS
            "security" -> android.provider.Settings.ACTION_SECURITY_SETTINGS
            "accessibility" -> android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
            "date" -> android.provider.Settings.ACTION_DATE_SETTINGS
            "vpn" -> android.provider.Settings.ACTION_VPN_SETTINGS
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
