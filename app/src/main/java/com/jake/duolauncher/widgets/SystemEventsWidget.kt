package com.jake.duolauncher.widgets

import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The Console widget: its face (the drawing) and its own helpers. Registered in [UnoWidgets].
 *
 * A live console of the *device* events the launcher can legitimately hear — screen, charger, battery, airplane,
 * ringer, Do Not Disturb, headphones and Bluetooth audio, VPN, USB data, app installs and updates, and the time zone —
 * as a timestamped scrolling log. These are all public, permission-free signals about the phone itself, never the
 * content of other apps, and the log is held only in memory (never written to storage and never sent anywhere). It is
 * honest about scope: Android gives a normal app no "all events" feed (logcat's READ_LOGS is privileged), so this is the
 * set that can be shown truthfully, captured only while Home is on screen.
 */
@Composable
internal fun ConsoleFace(slot: Int, onOptions: () -> Unit) {
    val context = LocalContext.current
    // Listen while this widget is composed (shared if two consoles are placed), so nothing runs while Home is gone.
    DisposableEffect(Unit) {
        ConsoleListeners.register(context)
        onDispose { ConsoleListeners.unregister() }
    }
    val revision = SystemEventLog.revision
    val previewing = LocalWidgetPreview.current
    val events = remember(revision, previewing) {
        if (previewing) sampleConsoleEvents() else SystemEventLog.latest()
    }
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val zone = ZoneId.systemDefault()
    val accent = widgetAccent(WidgetColors.Green)
    GlassCard(onClick = onOptions, modifier = Modifier.testTag("uno-console-$slot")) {
        val ink = LocalGlassInk.current
        CardTitle(Icons.Rounded.Terminal, "Console")
        if (events.isEmpty()) {
            Text(stringResource(com.jake.duolauncher.R.string.no_events_yet), color = ink.soft(), fontSize = 12.sp)
        } else Column(Modifier.weight(1f).fillMaxWidth().padding(top = 2.dp), verticalArrangement = Arrangement.Bottom) {
            events.forEachIndexed { index, event ->
                Row(Modifier.fillMaxWidth().testTag("console-line-$index"), verticalAlignment = Alignment.CenterVertically) {
                    Text(ConsoleTime.format(event.atMs, is24, zone), color = accent, fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Text(event.text, color = ink.primary, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A few plausible lines for the picker's preview, so it reads as a console. */
private fun sampleConsoleEvents(): List<ConsoleEvent> {
    val now = System.currentTimeMillis()
    return listOf(
        ConsoleEvent(now - 300_000, ConsoleEvents.power(true)),
        ConsoleEvent(now - 240_000, ConsoleEvents.screen(true)),
        ConsoleEvent(now - 120_000, ConsoleEvents.headphones(true)),
        ConsoleEvent(now - 60_000, ConsoleEvents.vpn(true)),
        ConsoleEvent(now - 30_000, ConsoleEvents.packageReplaced("com.example.app")),
    )
}

/** One console line: when it happened and what it says. */
internal data class ConsoleEvent(val atMs: Long, val text: String)

/** A bounded, thread-safe ring of the most recent events. Pure, so it is unit-tested. */
internal class EventRing(private val capacity: Int) {
    private val events = ArrayDeque<ConsoleEvent>()

    @Synchronized fun add(text: String, atMs: Long) {
        events.addLast(ConsoleEvent(atMs, text))
        while (events.size > capacity) events.removeFirst()
    }

    /** The newest [count] events, oldest first (so the newest lands at the bottom of the console). */
    fun latest(count: Int): List<ConsoleEvent> = synchronized(events) { events.toList().takeLast(count) }

    val size: Int get() = synchronized(events) { events.size }
}

/** The shared, in-memory log the console reads. Bump [revision] so the face redraws on each new line. */
internal object SystemEventLog {
    const val MAX = 60
    const val SHOWN = 9
    private val ring = EventRing(MAX)

    var revision by mutableIntStateOf(0)
        private set

    fun add(text: String, nowMs: Long = System.currentTimeMillis()) {
        ring.add(text, nowMs)
        revision++
    }

    fun latest(count: Int = SHOWN): List<ConsoleEvent> = ring.latest(count)
}

/** Wording for each device event, kept apart so it is unit-tested. */
internal object ConsoleEvents {
    fun screen(on: Boolean) = if (on) "Screen on" else "Screen off"
    fun power(connected: Boolean) = if (connected) "Charger connected" else "Charger disconnected"
    fun batteryLow(low: Boolean) = if (low) "Battery low" else "Battery OK"
    fun airplane(on: Boolean) = if (on) "Airplane mode on" else "Airplane mode off"
    fun ringer(mode: Int): String = when (mode) {
        AudioManager.RINGER_MODE_SILENT -> "Ringer silent"
        AudioManager.RINGER_MODE_VIBRATE -> "Ringer vibrate"
        else -> "Ringer on"
    }
    fun dnd(on: Boolean) = if (on) "Do Not Disturb on" else "Do Not Disturb off"
    fun headphones(connected: Boolean) = if (connected) "Headphones connected" else "Headphones disconnected"
    fun audio(label: String, connected: Boolean) = if (connected) "$label connected" else "$label disconnected"
    fun vpn(on: Boolean) = if (on) "VPN connected" else "VPN disconnected"
    fun usbData(on: Boolean) = if (on) "USB data connected" else "USB data off"
    fun packageAdded(pkg: String) = "Installed $pkg"
    fun packageRemoved(pkg: String) = "Removed $pkg"
    fun packageReplaced(pkg: String) = "Updated $pkg"
    fun timezone(zone: String) = "Time zone ${zone.ifBlank { "changed" }}"
}

/** The console timestamp, "14:23:05" or "2:23:05 PM". */
internal object ConsoleTime {
    fun format(epochMs: Long, is24: Boolean, zone: ZoneId): String =
        Instant.ofEpochMilli(epochMs).atZone(zone)
            .format(DateTimeFormatter.ofPattern(if (is24) "HH:mm:ss" else "h:mm:ss a"))
}

/** Registers every device listener while at least one console widget is showing, and removes them when the last one
 *  leaves. All callbacks run on the main thread so [SystemEventLog]'s Compose state is only ever written there. */
internal object ConsoleListeners {
    private var count = 0
    private var cleanup: (() -> Unit)? = null

    @Synchronized fun register(context: Context) {
        count++
        if (cleanup == null) cleanup = registerAll(context.applicationContext)
    }

    @Synchronized fun unregister() {
        count = (count - 1).coerceAtLeast(0)
        if (count == 0) { cleanup?.invoke(); cleanup = null }
    }

    /** The device events, none of which carry data. */
    internal fun deviceFilter() = IntentFilter().apply {
        addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
        addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED)
        addAction(Intent.ACTION_BATTERY_LOW); addAction(Intent.ACTION_BATTERY_OKAY)
        addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        addAction(AudioManager.ACTION_HEADSET_PLUG)
        addAction("android.hardware.usb.action.USB_STATE")
        addAction(Intent.ACTION_TIMEZONE_CHANGED)
    }

    /** App installs, removals and updates, which are announced with a package: URI. */
    internal fun packageFilter() = IntentFilter().apply {
        addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_REPLACED)
        addDataScheme("package")
    }

    private fun registerAll(context: Context): () -> Unit {
        val notifications = context.getSystemService(NotificationManager::class.java)
        var usbDataWas = false
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent == null || isInitialStickyBroadcast) return
                val text = when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> ConsoleEvents.screen(true)
                    Intent.ACTION_SCREEN_OFF -> ConsoleEvents.screen(false)
                    Intent.ACTION_POWER_CONNECTED -> ConsoleEvents.power(true)
                    Intent.ACTION_POWER_DISCONNECTED -> ConsoleEvents.power(false)
                    Intent.ACTION_BATTERY_LOW -> ConsoleEvents.batteryLow(true)
                    Intent.ACTION_BATTERY_OKAY -> ConsoleEvents.batteryLow(false)
                    Intent.ACTION_AIRPLANE_MODE_CHANGED -> ConsoleEvents.airplane(intent.getBooleanExtra("state", false))
                    AudioManager.RINGER_MODE_CHANGED_ACTION ->
                        ConsoleEvents.ringer(intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL))
                    NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> ConsoleEvents.dnd(
                        notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL)
                    AudioManager.ACTION_HEADSET_PLUG -> ConsoleEvents.headphones(intent.getIntExtra("state", 0) == 1)
                    "android.hardware.usb.action.USB_STATE" -> {
                        val functions = intent.extras?.keySet()
                            ?.filter { it != "connected" && it != "configured" && intent.extras?.get(it) == true }?.toSet() ?: emptySet()
                        val data = UsbState.dataActive(intent.getBooleanExtra("connected", false),
                            intent.getBooleanExtra("configured", false), functions)
                        if (data == usbDataWas) null else { usbDataWas = data; ConsoleEvents.usbData(data) }
                    }
                    Intent.ACTION_PACKAGE_ADDED -> ConsoleEvents.packageAdded(intent.data?.schemeSpecificPart.orEmpty())
                    Intent.ACTION_PACKAGE_REMOVED -> ConsoleEvents.packageRemoved(intent.data?.schemeSpecificPart.orEmpty())
                    Intent.ACTION_PACKAGE_REPLACED -> ConsoleEvents.packageReplaced(intent.data?.schemeSpecificPart.orEmpty())
                    Intent.ACTION_TIMEZONE_CHANGED -> ConsoleEvents.timezone(intent.getStringExtra("time-zone").orEmpty())
                    else -> null
                }
                text?.let { SystemEventLog.add(it) }
            }
        }
        // Two filters, one receiver: a data scheme applies to every action in its filter, so app installs (which carry a package: URI)
        // must not share a filter with screen, charger and the rest (which carry no data and would never match).
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, deviceFilter(),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, packageFilter(),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)

        // Bluetooth, USB and wired audio outputs connecting or leaving (permission-free; Android replays the current
        // devices on registration, which is state, not a change, so the first moments are ignored).
        val registeredAt = SystemClock.elapsedRealtime()
        val audio = context.getSystemService(AudioManager::class.java)
        val deviceCallback = object : android.media.AudioDeviceCallback() {
            private fun note(devices: Array<out AudioDeviceInfo>?, connected: Boolean) {
                if (SystemClock.elapsedRealtime() - registeredAt < 1_500L) return
                devices.orEmpty().firstOrNull { it.isSink && AudioDevices.label(it.type, it.productName) != null }
                    ?.let { SystemEventLog.add(ConsoleEvents.audio(AudioDevices.label(it.type, it.productName)!!, connected)) }
            }
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) = note(added, true)
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) = note(removed, false)
        }
        runCatching { audio.registerAudioDeviceCallback(deviceCallback, null) }

        // VPN connecting or dropping (same grace: Android replays the active VPN on registration).
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val vpnNetworks = mutableSetOf<Network>()
        val vpnCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val wasUp = vpnNetworks.isNotEmpty()
                vpnNetworks += network
                if (!wasUp && SystemClock.elapsedRealtime() - registeredAt > 1_500L) SystemEventLog.add(ConsoleEvents.vpn(true))
            }
            override fun onLost(network: Network) {
                if (vpnNetworks.remove(network) && vpnNetworks.isEmpty()) SystemEventLog.add(ConsoleEvents.vpn(false))
            }
        }
        runCatching {
            connectivity?.registerNetworkCallback(NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), vpnCallback,
                // Delivered on the main thread, like the other listeners, because the log is Compose state.
                android.os.Handler(android.os.Looper.getMainLooper()))
        }

        return {
            runCatching { context.unregisterReceiver(receiver) }
            runCatching { audio.unregisterAudioDeviceCallback(deviceCallback) }
            runCatching { connectivity?.unregisterNetworkCallback(vpnCallback) }
        }
    }
}
