package com.jake.duolauncher.search.context

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.CalendarContract
import android.provider.Settings
import android.telephony.TelephonyManager
import com.jake.duolauncher.appearance.AppearanceState
import com.jake.duolauncher.ExtrasState
import com.jake.duolauncher.FocusMode
import com.jake.duolauncher.LauncherState
import com.jake.duolauncher.NotificationFeed
import com.jake.duolauncher.SettingsSearch
import com.jake.duolauncher.UpdateSecurity
import com.jake.duolauncher.WidgetStacks
import com.jake.duolauncher.appearance.solarSchedule
import com.jake.duolauncher.feed.FeedEntry
import com.jake.duolauncher.island.ChargeText
import com.jake.duolauncher.island.IslandRuntime
import com.jake.duolauncher.island.IslandTools
import com.jake.duolauncher.island.SensorLog
import com.jake.duolauncher.search.ContactsSearch
import com.jake.duolauncher.search.SpotlightHistory
import com.jake.duolauncher.search.context.ContextPolicy.Core
import com.jake.duolauncher.search.context.ContextPolicy.Group
import com.jake.duolauncher.search.context.ContextPolicy.Source
import com.jake.duolauncher.widgets.Checklist
import com.jake.duolauncher.widgets.Countdown
import com.jake.duolauncher.widgets.MoonPhase
import com.jake.duolauncher.widgets.ScreenTime
import com.jake.duolauncher.widgets.UnoWidgets
import com.jake.duolauncher.widgets.WidgetData
import com.jake.duolauncher.widgets.hasUsageAccess
import com.jake.duolauncher.widgets.readUsageToday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/** What the context sources read from: Uno's own state, read when a question is asked. */
internal class ContextEnv(
    val context: Context,
    val launcher: () -> LauncherState,
    val extras: () -> ExtrasState?,
    val appearance: () -> AppearanceState?,
    val feed: () -> List<FeedEntry>,
)

/**
 * The phone's facts for Uno Lab's assistant: every source the person can switch off (Search › Local assistant › What Qwen can see), all on
 * by default. For each question [build] reads the core sources and the ones the question points at (see [ContextPolicy]), each on a
 * background thread with [TIMEOUT_MS] to answer: a slow one is left out rather than waited for. Nothing here is stored or sent anywhere but
 * to Uno Lab with that one question.
 */
internal object PhoneContext {
    const val TIMEOUT_MS = 250L

    val sources: List<Source> = listOf(
        Source("time", "Date, time, language and region", Group.TIME, Core.ALWAYS, emptyList(), priority = 0),
        Source("holidays", "Public holidays", Group.TIME, Core.NO, listOf("holiday*", "day off", "bank holiday", "long weekend", "christmas", "thanksgiving", "easter")),
        Source("place", "Place you set", Group.TIME, Core.NO, listOf("where", "here", "near*", "local", "city", "town", "weather", "place")),
        Source("location", "Precise location", Group.TIME, Core.NO, listOf("where am i", "my location", "exact location", "coordinates", "gps")),
        Source("sky", "Sunrise, sunset and moon", Group.TIME, Core.NO, listOf("sun*", "dark", "daylight", "light out", "moon", "dusk", "dawn", "golden hour")),
        Source("alarm", "Next alarm", Group.SCHEDULE, Core.ALWAYS, emptyList(), priority = 2),
        Source("timer", "Timer and stopwatch", Group.SCHEDULE, Core.WHEN_LIVE, listOf("timer", "stopwatch"), priority = 3),
        Source("countdowns", "Countdowns", Group.SCHEDULE, Core.NO, listOf("countdown", "until", "how many days", "days left", "days to go")),
        Source("calendar", "Calendar", Group.SCHEDULE, Core.NO, listOf("calendar", "meeting*", "event*", "appointment*", "free", "busy", "schedule", "tomorrow", "today", "tonight", "this week", "agenda")),
        Source("quiet", "Focus, Do Not Disturb and ringer", Group.SCHEDULE, Core.ALWAYS, emptyList(), priority = 4),
        Source("battery", "Battery", Group.DEVICE, Core.ALWAYS, emptyList(), priority = 1),
        Source("storage", "Storage", Group.DEVICE, Core.NO, listOf("storage", "space", "full", "memory", "gigabyte*", "gb")),
        Source("network", "Network", Group.DEVICE, Core.NO, listOf("wifi", "wi fi", "internet", "online", "offline", "signal", "network", "data", "airplane", "connected", "carrier")),
        Source("audio", "Connected headphones and speakers", Group.DEVICE, Core.NO, listOf("bluetooth", "earbuds", "headphones", "headset", "speaker*", "connected", "airpods", "buds")),
        Source("sound", "Volume and brightness", Group.DEVICE, Core.NO, listOf("volume", "loud", "quiet", "mute*", "brightness", "bright", "dim", "silent", "ring*")),
        Source("versions", "Phone model and versions", Group.DEVICE, Core.NO, listOf("version", "update*", "android", "model", "which phone", "what phone", "pixel", "uno")),
        Source("privacy", "Camera and microphone use", Group.DEVICE, Core.NO, listOf("camera", "mic", "microphone", "recording", "listening", "spying")),
        Source("apps", "Installed apps", Group.APPS, Core.NO, listOf("app*", "install*", "open", "launch", "do i have")),
        Source("layout", "Home screen and folders", Group.APPS, Core.NO, listOf("folder*", "home screen", "dock", "hidden", "where is", "where's")),
        Source("screentime", "Screen time", Group.APPS, Core.NO, listOf("screen time", "how long", "on my phone", "usage", "used", "addicted", "minutes on")),
        Source("playing", "Now playing", Group.MEDIA, Core.WHEN_LIVE, listOf("song", "music", "playing", "track", "artist", "album", "podcast"), priority = 5),
        Source("live", "Deliveries, rides, navigation and calls", Group.MEDIA, Core.NO, listOf("delivery", "order", "ride", "driver", "arriv*", "eta", "navigation", "call", "on the phone")),
        Source("notes", "Your note, checklist and counters", Group.YOURS, Core.NO, listOf("note*", "list", "to do", "todo", "task*", "checklist", "count*", "remind*")),
        Source("contacts", "Contact names in the question", Group.PEOPLE, Core.NO, listOf("call", "text", "message", "phone", "contact*", "number", "email", "birthday")),
        Source("unread", "Unread counts", Group.PEOPLE, Core.NO, listOf("unread", "notification*", "missed", "inbox")),
        Source("notifications", "Recent notifications", Group.PEOPLE, Core.NO, listOf("notification*", "message*", "texted", "messaged", "who", "email*", "said", "sent", "chat*", "reply", "missed"), untrusted = true, priority = 60),
        Source("settings", "Uno's settings", Group.UNO, Core.NO, listOf("setting*", "turn on", "turn off", "enable", "disable", "how do i", "change", "uno", "island", "widget*", "glass")),
        Source("feed", "Feed headlines", Group.UNO, Core.NO, listOf("news", "feed", "headline*", "article*"), untrusted = true, priority = 70),
        Source("clipboard", "Clipboard", Group.UNO, Core.NO, listOf("clipboard", "copied", "pasted", "this text", "summari*", "translate"), untrusted = true, priority = 80),
    )

    /** The context for [question], or "" when nothing is switched on or says anything. [action]: the reply may become an action. */
    suspend fun build(question: String, action: Boolean, env: ContextEnv): String = withContext(Dispatchers.IO) {
        val off = env.extras()?.contextOff.orEmpty()
        val chosen = ContextPolicy.select(sources, question, action, off)
        val parts = coroutineScope {
            chosen.map { s -> async { s to withTimeoutOrNull(TIMEOUT_MS) { runCatching { read(s.id, question, env) }.getOrNull() } } }.awaitAll()
        }
        ContextPolicy.assemble(parts)
    }

    private fun granted(c: Context, permission: String) = c.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun is24(c: Context) = android.text.format.DateFormat.is24HourFormat(c)

    private fun read(id: String, question: String, env: ContextEnv): String? {
        val c = env.context
        val now = ZonedDateTime.now()
        val today = now.toLocalDate()
        return when (id) {
            "time" -> ContextText.dateTime(now, Locale.getDefault(), is24(c), WeekFields.of(Locale.getDefault()).firstDayOfWeek)
            "holidays" -> Holidays.line(Locale.getDefault().country, today)
            "place" -> env.appearance()?.let { a ->
                listOfNotNull(a.place.takeIf { it.isNotBlank() }?.let { "Place set in Uno: $it" },
                    if (a.latitude != null && a.longitude != null) "Approximate coordinates: %.1f, %.1f".format(Locale.ROOT, a.latitude, a.longitude) else null)
                    .joinToString("\n").ifBlank { null }
            }
            "location" -> lastLocation(c, now)
            "sky" -> sky(env, now)
            "alarm" -> {
                val next = c.getSystemService(AlarmManager::class.java)?.nextAlarmClock
                if (next == null) ContextText.alarm(null, null, null) else {
                    val parts = com.jake.duolauncher.widgets.NextAlarmText.describe(now, Instant.ofEpochMilli(next.triggerTime).atZone(now.zone), is24(c))
                    ContextText.alarm(parts.time, parts.day, parts.remaining)
                }
            }
            "timer" -> {
                val nowMs = System.currentTimeMillis()
                listOfNotNull(
                    if (IslandTools.timerActive || IslandTools.ringing) ContextText.timer(IslandTools.timerEndAt - nowMs, IslandTools.timerTotal, IslandTools.ringing) else null,
                    if (IslandTools.stopwatchStarted) ContextText.stopwatch(IslandTools.elapsedMs(nowMs), IslandTools.swRunning) else null,
                ).joinToString("\n").ifBlank { null }
            }
            "countdowns" -> unoSlots(env, UnoWidgets.COUNTDOWN).mapNotNull { slot ->
                val set = Countdown.parse(WidgetData.get(slot, "date")) ?: return@mapNotNull null
                ContextText.countdown(WidgetData.get(slot, "name", "Countdown"), today, Countdown.target(today, set, WidgetData.get(slot, "yearly") == "1"))
            }.joinToString("\n").ifBlank { null }
            "calendar" -> calendar(c, now)
            "quiet" -> {
                val dnd = c.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter?.let(FocusMode::dndOn)
                val ringer = when (c.getSystemService(AudioManager::class.java)?.ringerMode) {
                    AudioManager.RINGER_MODE_SILENT -> "silent"; AudioManager.RINGER_MODE_VIBRATE -> "vibrate"; AudioManager.RINGER_MODE_NORMAL -> "normal"; else -> null
                }
                ContextText.quiet(env.extras()?.focusOn == true, dnd, ringer)
            }
            "battery" -> {
                val bm = c.getSystemService(BatteryManager::class.java)
                val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
                val status = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                val toFull = if (charging) bm?.computeChargeTimeRemaining()?.let { ChargeText.toFull(it) } else null
                ContextText.battery(level, charging, toFull)
            }
            "storage" -> StatFs(Environment.getDataDirectory().path).let { ContextText.storage(it.availableBytes, it.totalBytes) }
            "network" -> network(c, env)
            "audio" -> c.getSystemService(AudioManager::class.java)?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                ?.filter { it.type in OUTPUTS }?.map { "${it.productName} (${outputKind(it.type)})" }
                .let { ContextText.list("Audio connected", it.orEmpty(), 6) ?: "Audio connected: phone speaker only" }
            "sound" -> {
                val am = c.getSystemService(AudioManager::class.java)
                val media = am?.let { "media volume ${it.getStreamVolume(AudioManager.STREAM_MUSIC)} of ${it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}" }
                val ring = am?.let { "ring volume ${it.getStreamVolume(AudioManager.STREAM_RING)} of ${it.getStreamMaxVolume(AudioManager.STREAM_RING)}" }
                val bright = runCatching { Settings.System.getInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrNull()?.let { "brightness ${it * 100 / 255}%" }
                listOfNotNull(media, ring, bright).joinToString(", ").ifBlank { null }?.let { "Sound and screen: $it" }
            }
            "versions" -> {
                val lab = runCatching { c.packageManager.getPackageInfo("com.everettpenne.unolab", 0).versionName }.getOrNull()
                "Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}; Uno Launcher ${UpdateSecurity.appVersion}" +
                    (lab?.let { "; Uno Lab $it" } ?: "")
            }
            "privacy" -> {
                val st = IslandRuntime.state
                val inUse = listOfNotNull("camera".takeIf { st.cameraActive }, "microphone".takeIf { st.micActive }, "screen recording".takeIf { st.recordingActive })
                val log = runCatching { SensorLog.entries(c) }.getOrDefault(emptyList()).takeLast(3).map { e ->
                    "${e.kind} at " + Instant.ofEpochMilli(e.startMs).atZone(now.zone).format(DateTimeFormatter.ofPattern("EEE h:mm a", Locale.ENGLISH))
                }
                listOfNotNull("In use now: " + (inUse.joinToString(", ").ifBlank { "nothing" }), ContextText.list("Recent uses", log, 3)).joinToString("\n")
            }
            "apps" -> {
                val scores = runCatching { SpotlightHistory(c).scores() }.getOrDefault(emptyMap())
                val apps = env.launcher().visibleApps.sortedWith(compareByDescending<com.jake.duolauncher.AppEntry> { scores[it.id] ?: 0.0 }.thenBy { it.label })
                ContextText.list("Installed apps (most used first)", apps.map { it.label }, 40)
            }
            "layout" -> {
                val s = env.launcher()
                val label = s.apps.associate { it.id to it.label }
                listOfNotNull(
                    ContextText.list("Dock", s.dock.filterNotNull().mapNotNull { label[it] }, 6),
                    ContextText.list("Folders", s.folders.map { f -> "${f.title} (" + f.appIds.mapNotNull { label[it] }.take(6).joinToString(", ") + ")" }, 6),
                    ContextText.list("Hidden apps", s.hiddenApps.mapNotNull { label[it] }, 8),
                ).joinToString("\n").ifBlank { null }
            }
            "screentime" -> if (!hasUsageAccess(c)) null else readUsageToday(c).let { u ->
                "Screen time today: ${ScreenTime.formatDuration(ScreenTime.totalMinutes(u))}" +
                    ScreenTime.topApps(u, 3).joinToString(prefix = "; most: ", separator = ", ") { "${ScreenTime.displayName(it)} ${ScreenTime.formatDuration(it.minutes)}" }
            }
            "playing" -> NotificationFeed.nowPlaying?.let { ContextText.nowPlaying(it.title, it.artist, it.playing, null) }
            "live" -> listOfNotNull(
                NotificationFeed.ongoingCall?.let { "On a call with ${it.caller} for ${ContextText.duration(System.currentTimeMillis() - it.startedAt)}" },
            ).plus(NotificationFeed.liveUpdates.take(3).map { u -> "${u.appLabel}: " + listOfNotNull(u.title, u.shortText ?: u.text).joinToString(", ") })
                .joinToString("\n").ifBlank { null }
            "notes" -> listOfNotNull(
                unoSlots(env, UnoWidgets.NOTE).mapNotNull { WidgetData.get(it, "note").takeIf { n -> n.isNotBlank() }?.let { n -> "Note: \"" + n.replace('\n', ';') + "\"" } }.joinToString("\n"),
                unoSlots(env, UnoWidgets.CHECKLIST).mapNotNull { ContextText.checklist(Checklist.parse(WidgetData.get(it, "items")).map { i -> i.text to i.done }) }.joinToString("\n"),
                unoSlots(env, UnoWidgets.COUNTER).map { "Counter \"${WidgetData.get(it, "name", "Counter")}\": ${WidgetData.getInt(it, "count")}" }.joinToString("\n"),
            ).filter { it.isNotBlank() }.joinToString("\n").ifBlank { null }
            "contacts" -> if (env.extras()?.contactSearch != true) null else {
                val words = question.split(Regex("[^\\p{L}']+")).filter { it.length >= 3 && it.lowercase() !in STOP }.take(3)
                words.flatMap { ContactsSearch.search(c, it).map { r -> r.name } }.let { ContextText.list("Contacts matching names in the question", it, 5) }
            }
            "unread" -> {
                val label = env.launcher().apps.associate { it.component.packageName to it.label }
                NotificationFeed.badges.entries.filter { it.value > 0 }.map { (key, n) -> key.substringAfter(':').let { pkg -> "${label[pkg] ?: pkg} $n" } }
                    .let { ContextText.list("Unread notifications", it, 8) }
            }
            "notifications" -> {
                val nowMs = System.currentTimeMillis()
                RecentNotifications.recent(nowMs).map { e -> ContextText.notification(e.app, e.title, e.text, (nowMs - e.at) / 60_000) }.joinToString("\n").ifBlank { null }
            }
            "settings" -> SettingsSearch.search(question).take(4).map { "\"${it.title}\" is on the ${it.page.name.lowercase().replace('_', ' ')} page of Uno's settings" }
                .let { ContextText.list("Uno settings that match", it, 4) }
            "feed" -> env.feed().take(5).map { "${it.sourceLabel}: ${it.title}" }.joinToString("\n").ifBlank { null }
            "clipboard" -> c.getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(c)?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { "\"" + it.take(300) + "\"" }
            else -> null
        }
    }

    private val STOP = setOf("call", "text", "message", "phone", "the", "and", "what", "when", "who", "how", "for", "number", "email", "send", "tell",
        "birthday", "contact", "contacts", "please", "can", "you", "my", "her", "his", "their", "about", "with", "from")

    private val OUTPUTS = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_HDMI)

    private fun outputKind(type: Int) = when (type) {
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"; AudioDeviceInfo.TYPE_HDMI -> "HDMI"; else -> "Bluetooth"
    }

    /** Uno widgets of [id] on Home, placed or stacked behind another, by their data slot. */
    private fun unoSlots(env: ContextEnv, id: Int): List<Int> {
        val placements = env.launcher().widgetPlacements
        val placed = placements.filter { it.id == id }.map { it.slot }
        val stacked = WidgetStacks.stacks.values.flatten().filter { com.jake.duolauncher.StackRules.isUno(it) && com.jake.duolauncher.StackRules.unoWidgetId(it) == id }
            .map { com.jake.duolauncher.StackRules.unoSlot(it) }
        return (placed + stacked).distinct()
    }

    private fun sky(env: ContextEnv, now: ZonedDateTime): String? {
        val a = env.appearance()
        val sun = if (a?.latitude != null && a.longitude != null) runCatching {
            val s = solarSchedule(now.toLocalDate(), a.latitude, a.longitude, now.zone)
            val f = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
            when {
                s.sunrise != null && s.sunset != null -> "Sunrise ${s.sunrise.format(f)}, sunset ${s.sunset.format(f)} today"
                else -> "The sun does not ${if (s.polar.name == "DAY") "set" else "rise"} today"
            }
        }.getOrNull() else null
        val cycle = MoonPhase.cycle(now)
        val moon = "Moon: ${MoonPhase.name(cycle)}, ${(MoonPhase.illumination(now) * 100).toInt()}% lit; ${MoonPhase.nextText(MoonPhase.next(now)).replaceFirstChar { it.lowercase() }}"
        return listOfNotNull(sun, moon).joinToString("\n")
    }

    private fun lastLocation(c: Context, now: ZonedDateTime): String? {
        if (!granted(c, Manifest.permission.ACCESS_FINE_LOCATION) && !granted(c, Manifest.permission.ACCESS_COARSE_LOCATION)) return null
        val lm = c.getSystemService(LocationManager::class.java) ?: return null
        @Suppress("MissingPermission")
        val best = lm.allProviders.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time } ?: return null
        val ageMin = (System.currentTimeMillis() - best.time) / 60_000
        return "Last known location: %.3f, %.3f (about %d m accurate, %s)".format(Locale.ROOT, best.latitude, best.longitude, best.accuracy.toInt(),
            if (ageMin < 1) "just now" else if (ageMin < 120) "$ageMin min ago" else "${ageMin / 60} h ago")
    }

    private fun network(c: Context, env: ContextEnv): String {
        val cm = c.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val airplane = Settings.Global.getInt(c.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        val via = when {
            caps == null -> "no connection"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "other"
        }
        val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val carrier = c.getSystemService(TelephonyManager::class.java)?.networkOperatorName?.takeIf { it.isNotBlank() }
        @Suppress("DEPRECATION")
        val ssid = runCatching { c.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)?.connectionInfo?.ssid }.getOrNull()
            ?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        return "Network: " + listOfNotNull(via + (ssid?.let { " \"$it\"" } ?: ""), if (online) "internet working" else if (caps != null) "no internet" else null,
            "VPN on".takeIf { vpn }, "airplane mode on".takeIf { airplane }, carrier?.let { "carrier $it" }).joinToString(", ")
    }

    private fun calendar(c: Context, now: ZonedDateTime): String? {
        if (!granted(c, Manifest.permission.READ_CALENDAR)) return null
        val from = now.toLocalDate().atStartOfDay(now.zone).toInstant().toEpochMilli()
        val to = from + 8L * 24 * 60 * 60 * 1000
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, from); android.content.ContentUris.appendId(it, to)
        }.build()
        val rows = ArrayList<String>()
        c.contentResolver.query(uri, arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
            null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cur ->
            val f = DateTimeFormatter.ofPattern(if (is24(c)) "EEE d MMM HH:mm" else "EEE d MMM h:mm a", Locale.ENGLISH)
            val day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
            while (cur.moveToNext() && rows.size < 10) {
                val title = cur.getString(0)?.takeIf { it.isNotBlank() } ?: "(no title)"
                val begin = Instant.ofEpochMilli(cur.getLong(1))
                val allDay = cur.getInt(2) == 1
                val at = if (allDay) begin.atZone(ZoneId.of("UTC")).toLocalDate().format(day) + ", all day" else begin.atZone(now.zone).format(f)
                rows += "$at: $title"
            }
        }
        return if (rows.isEmpty()) "Calendar: nothing in the next 7 days" else "Calendar (next 7 days):\n" + rows.joinToString("\n")
    }
}
