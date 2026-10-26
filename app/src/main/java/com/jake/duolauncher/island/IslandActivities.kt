package com.jake.duolauncher.island

import com.jake.duolauncher.panel.*

import com.jake.duolauncher.*
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.HandlerThread
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** What a short island event is about; the island maps each to an icon and tint. */
internal enum class IslandSymbol(val tint: Color) {
    APP(Color.White),
    CHARGING(IosGreen),
    RINGER(Color.White),
    SILENT(Color(0xFFFF9F0A)),
    VIBRATE(Color.White),
    AIRPLANE(Color(0xFFFF9F0A)),
    FOCUS(Color(0xFF7D7AFF)),
    TIMER(Color(0xFFFF9F0A)),
    NOTIFICATION(Color.White),
    VPN(Color(0xFF30D158)),
    USB(Color(0xFFFF9F0A)),
    HEADPHONES(Color.White),
    BLUETOOTH(Color(0xFF0A84FF)),
    RECORDING(Color(0xFFFF453A)),
    LOW_BATTERY(Color(0xFFFF453A)),
}

/** A brief event shown in the collapsed island, like iOS's ringer, charging and Focus flashes. */
internal data class IslandEvent(val title: String, val symbol: IslandSymbol)

/** Which audio outputs the island announces, and what to call them. Pure, so the choices are tested. */
internal object AudioDevices {
    /** A name for a newly connected or removed audio output, or null for ones that are not worth an event (the phone's own
     * speaker and earpiece, HDMI, and so on). Bluetooth and USB devices use their own product name when they give one.
     */
    fun label(type: Int, productName: CharSequence?): String? {
        val name = productName?.toString()?.trim().orEmpty()
        return when (type) {
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, android.media.AudioDeviceInfo.TYPE_BLE_HEADSET,
            android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER, android.media.AudioDeviceInfo.TYPE_BLE_BROADCAST ->
                name.ifEmpty { "Bluetooth audio" }
            android.media.AudioDeviceInfo.TYPE_HEARING_AID -> name.ifEmpty { "Hearing aid" }
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES, android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Headphones"
            android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> name.ifEmpty { "USB headset" }
            else -> null
        }
    }
}

/**
 * The battery's moments, as iOS shows them: plugging in (with the level), reaching full while plugged in, and running low (20 % and 10 %,
 * once each on the way down). Fed from the battery broadcast; the first reading is the state the phone is already in, so it is no event.
 */
internal object BatteryMoments {
    data class Reading(val percent: Int, val plugged: Boolean)
    val LOW_LEVELS = listOf(20, 10)

    fun event(previous: Reading?, now: Reading): IslandEvent? = when {
        previous == null -> null
        now.plugged && !previous.plugged -> IslandEvents.charging(now.percent)
        now.plugged && previous.percent < 100 && now.percent >= 100 -> IslandEvents.charged()
        !now.plugged -> LOW_LEVELS.firstOrNull { previous.percent > it && now.percent <= it }?.let { IslandEvents.lowBattery(now.percent) }
        else -> null
    }
}

/** Charge-time wording for the open island. */
internal object ChargeText {
    /** "40 min to full" or "1 h 5 min to full" from the system's estimate in ms, or null when it has none (-1) or the time is
     * under a minute or absurd (over a day).
     */
    fun toFull(remainingMs: Long): String? {
        if (remainingMs < 60_000L || remainingMs > 24L * 60 * 60_000L) return null
        val minutes = ((remainingMs + 30_000L) / 60_000L).toInt()
        return if (minutes < 60) "$minutes min to full"
        else "${minutes / 60} h${if (minutes % 60 != 0) " ${minutes % 60} min" else ""} to full"
    }
}

/** Wording for system events, kept apart from the receivers so it can be unit-tested. */
internal object IslandEvents {
    fun ringer(mode: Int): IslandEvent = when (mode) {
        AudioManager.RINGER_MODE_SILENT -> IslandEvent("Silent", IslandSymbol.SILENT)
        AudioManager.RINGER_MODE_VIBRATE -> IslandEvent("Vibrate", IslandSymbol.VIBRATE)
        else -> IslandEvent("Ringer", IslandSymbol.RINGER)
    }

    fun airplane(on: Boolean) = IslandEvent(if (on) "Airplane" else "Airplane off", IslandSymbol.AIRPLANE)

    /** [filter] is a [NotificationManager] interruption filter; everything but "all" is Do Not Disturb. */
    fun doNotDisturb(filter: Int): IslandEvent =
        if (filter == NotificationManager.INTERRUPTION_FILTER_ALL || filter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN)
            IslandEvent("Do Not Disturb off", IslandSymbol.FOCUS)
        else IslandEvent("Do Not Disturb", IslandSymbol.FOCUS)

    fun vpn(on: Boolean) = IslandEvent(if (on) "VPN on" else "VPN off", IslandSymbol.VPN)

    /** A USB data connection starting or ending. Plain charging has its own event; this is the link that carries data. */
    fun usbData(on: Boolean) = IslandEvent(if (on) "USB data connected" else "USB data off", IslandSymbol.USB)

    fun audioDevice(connected: Boolean, label: String) =
        IslandEvent(if (connected) "$label connected" else "$label disconnected", IslandSymbol.HEADPHONES)

    fun charging(percent: Int?) = IslandEvent(if (percent != null) "Charging $percent%" else "Charging", IslandSymbol.CHARGING)
    fun charged() = IslandEvent("Charged", IslandSymbol.CHARGING)
    fun lowBattery(percent: Int) = IslandEvent("Low Battery $percent%", IslandSymbol.LOW_BATTERY)
}

/** Whether [symbol] describes the ringer rather than a Focus change. */
internal fun IslandSymbol.isRinger() = this == IslandSymbol.RINGER || this == IslandSymbol.SILENT || this == IslandSymbol.VIBRATE

/** When the island offers playback controls: while audio plays, and for a while after it stops so a
 * paused track can be resumed from the island (a paused player no longer counts as "active").
 */
internal object IslandPlayback {
    /** How long paused or finished media stays on the pill itself, with still bars, before the pill goes back to its resting size. */
    const val LINGER_MS = 6_000L
    /** How long paused media that is worth coming back to stays on the open island by default. Settable (see [resumeWindowMs]). */
    const val DEFAULT_RESUME_MS = 120_000L
    /** Content shorter than this is not "something to return to": a clip, a notification sound. */
    const val MIN_RESUMABLE_CONTENT_MS = 30_000L
    /** Within this much of the end the media is counted as finished, not paused. */
    const val END_SLACK_MS = 2_000L
    /** A player that says "playing" while no audio is coming out (a muted autoplay video) stops counting as playing after this long. */
    const val SILENT_GRACE_MS = 4_000L

    /** What the island does about media right now. */
    enum class Phase {
        /** Audio is playing: the pill is wide, with moving bars. */
        PLAYING,
        /** Just stopped or paused (or finished): still bars on the pill for a few seconds, then it goes. */
        LINGER,
        /** Paused with a title, a position and more than 30 seconds of content: off the pill, a glow round the camera, and the card stays
         * on the open island until the window ends. */
        RESUMABLE,
        GONE,
    }

    /** The minutes choices for how long paused media stays on the open island. */
    val RESUME_CHOICES_MIN = listOf(1, 2, 10, 30)
    fun resumeWindowMs(minutes: Int): Long = (if (minutes in RESUME_CHOICES_MIN) minutes else 2) * 60_000L

    /** Whether paused media is worth coming back to: it has a title, it is paused part-way through (a position, not at the very end),
     * and it is longer than [MIN_RESUMABLE_CONTENT_MS]. A finished video, a stopped player and a short clip are not. */
    fun resumable(title: String?, stateCode: Int?, positionMs: Long, durationMs: Long): Boolean =
        !title.isNullOrBlank() && stateCode == android.media.session.PlaybackState.STATE_PAUSED &&
            positionMs > 0L && durationMs > MIN_RESUMABLE_CONTENT_MS && positionMs < durationMs - END_SLACK_MS

    fun phase(nowMs: Long, playing: Boolean, lastPlayingAtMs: Long, resumable: Boolean, resumeWindowMs: Long = DEFAULT_RESUME_MS): Phase {
        if (playing) return Phase.PLAYING
        if (lastPlayingAtMs <= 0L) return Phase.GONE
        val since = nowMs - lastPlayingAtMs
        return when {
            since <= LINGER_MS -> Phase.LINGER
            resumable && since <= resumeWindowMs -> Phase.RESUMABLE
            else -> Phase.GONE
        }
    }

    /** How long from [nowMs] until the phase changes by itself (the end of the linger, or of the window), or null when it will not. */
    fun nextChangeMs(nowMs: Long, playing: Boolean, lastPlayingAtMs: Long, resumable: Boolean, resumeWindowMs: Long = DEFAULT_RESUME_MS): Long? {
        if (playing || lastPlayingAtMs <= 0L) return null
        val since = nowMs - lastPlayingAtMs
        return when {
            since < LINGER_MS -> LINGER_MS - since
            resumable && since < resumeWindowMs -> resumeWindowMs - since
            else -> null
        }
    }

    /** Whether a session that says "playing" really is: audio is coming out, or went quiet only a moment ago. */
    fun sessionAudible(audioActive: Boolean, silentSinceMs: Long, nowMs: Long): Boolean =
        audioActive || silentSinceMs <= 0L || nowMs - silentSinceMs < SILENT_GRACE_MS

    /** Whether a media session's playback state means "sound is, or is about to be, coming out". Buffering counts: a player that
     * is starting, or catching up after a seek, reports it, and treating it as paused made the island flicker to "paused". */
    fun sessionPlaying(state: Int?): Boolean = state == android.media.session.PlaybackState.STATE_PLAYING ||
        state == android.media.session.PlaybackState.STATE_BUFFERING ||
        state == android.media.session.PlaybackState.STATE_FAST_FORWARDING ||
        state == android.media.session.PlaybackState.STATE_REWINDING

    /** The player's own session is the truest answer when there is one (it says "paused" the instant you pause, and "playing" before
     * the audio system has reported anything); without one, the audio system's answer stands. */
    fun effective(audioActive: Boolean, sessionPlaying: Boolean?, sessionAudible: Boolean = true): Boolean =
        if (sessionPlaying == null) audioActive else sessionPlaying && sessionAudible
}

/** Sends a notification's or session's own tap action. From Android 14 the sender has to say that it is allowed to bring an activity to
 * the front (a PendingIntent sent without that is quietly dropped when the sending app is not the one on screen, which is the case for
 * the island over other apps, where the sender is not the app on screen, and reports success all the same). This is the only place the
 * allowance is attached, and every caller is a tap handler on the island (a notification, the music card, a call, a Live Update): the
 * user's own action. Nothing else in the app sends a PendingIntent with it, and nothing sends one without a tap. */
internal fun sendTapAction(context: Context, action: android.app.PendingIntent?): Boolean {
    if (action == null) return false
    // Opened from the island: that app's notifications are wanted, so its drop-downs are not quieted (PeekSmarts).
    action.creatorPackage?.let { PeekLearning.opened(context, it) }
    return runCatching {
        val options = android.app.ActivityOptions.makeBasic()
        // Allowed to start the notification's screen from the island: always (Android 16's name for it), or the older "allowed" before that.
        if (android.os.Build.VERSION.SDK_INT >= 36) options.setPendingIntentBackgroundActivityStartMode(
            android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
        else if (android.os.Build.VERSION.SDK_INT >= 34) @Suppress("DEPRECATION") options.setPendingIntentBackgroundActivityStartMode(
            android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        action.send(context, 0, null, null, null, null, options.toBundle())
    }.isSuccess
}

/** Opens the app that is playing: its own now-playing screen (the session's activity) when it has one, else the app's front door. */
internal fun openPlayer(context: Context, controller: android.media.session.MediaController) {
    if (sendTapAction(context, controller.sessionActivity)) return
    context.packageManager.getLaunchIntentForPackage(controller.packageName)?.let {
        runCatching { context.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** Sends a media key to whichever player is active. [AudioManager.dispatchMediaKeyEvent] needs no
 * permission, and no notification access: the island can pause, resume and skip, but it cannot
 * read a title or artwork, which would need that access.
 */
internal fun sendMediaKey(context: Context, keyCode: Int) {
    val audio = context.getSystemService(AudioManager::class.java) ?: return
    // A binder call, so not on the main thread.
    Thread {
        // Real timestamps: a key event stamped 0 reads as stale and the media session service can drop it, which is why the
        // pause button did nothing.
        val now = android.os.SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now + 20, KeyEvent.ACTION_UP, keyCode, 0))
    }.start()
}

/** Plays, pauses or skips. With notification access the island knows the playing session, and the session's own
 * transport controls are exact (pause really pauses, play after a pause really plays); without it, or if the session
 * refuses, it falls back to the media key.
 */
internal fun mediaCommand(context: Context, keyCode: Int) {
    issueMediaCommand(context, keyCode)
    // Whatever was done, look at what the audio is doing a moment later so the play/pause icon follows it.
    IslandRuntime.state.recheckPlayback?.invoke()
}

private fun issueMediaCommand(context: Context, keyCode: Int) {
    val controller = NotificationFeed.nowPlaying?.controller
    if (controller != null) {
        val ok = runCatching {
            val controls = controller.transportControls
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> controls.skipToPrevious()
                KeyEvent.KEYCODE_MEDIA_NEXT -> controls.skipToNext()
                else -> if (controller.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING) controls.pause()
                    else controls.play()
            }
        }.isSuccess
        if (ok) return
    }
    val audio = context.getSystemService(AudioManager::class.java)
    // Without the session (no notification access) the media key is the only handle, and newer Android versions can ignore
    // it from an app. A playing app does listen to audio focus, so pausing is done by taking focus away from it.
    if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && audio != null && audio.isMusicActive) {
        pauseByAudioFocus(context, audio)
        return
    }
    sendMediaKey(context, keyCode)
}

/** Pauses whatever is playing by requesting permanent audio focus and giving it straight back. A player that honours focus
 * (almost all do) pauses on the loss and, because the loss is permanent, does not resume when the focus is abandoned. If it is
 * still playing afterwards, the focus request was refused or ignored, and the media key is tried instead.
 */
private fun pauseByAudioFocus(context: Context, audio: AudioManager) {
    Thread {
        val request = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { }
            .build()
        runCatching { audio.requestAudioFocus(request) }
        Thread.sleep(700)
        runCatching { audio.abandonAudioFocusRequest(request) }
        if (audio.isMusicActive) sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }.start()
}

/** Opens [component]'s app from the island. A background start can be refused; that is simply a no-op. */
internal fun launchIslandApp(context: Context, component: android.content.ComponentName) {
    runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER).setComponent(component)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
    }
}

/** Feeds the island with what the system reports: whether audio is playing; ringer, airplane, Do Not Disturb, VPN, USB and audio
 * device changes; and the camera, microphone and screen-recording marks. All of it is permission-free (Bluetooth names aside) and runs
 * only while an island host holds it (see [IslandEventsOwner]): Home while the launcher is in front, the overlay while the island is
 * allowed over apps. It used to run inside the island's composable, so it stopped whenever no island was drawn (a sheet open on Home, the
 * overlay hidden for a full-screen app) and a camera that came on meanwhile was timed from when the island came back.
 *
 * Every call into the audio and notification services is a binder round trip that can stall for
 * seconds when those services are busy, so none of it happens on the main thread: registration, the
 * initial query, the playback callback and the receiver all run on a private worker thread, and only
 * the resulting state writes (which are thread-safe) reach the UI.
 */
internal class IslandSystemEvents(private val context: Context, private val state: IslandState) : AutoCloseable {
    private val stop: () -> Unit

    init {
        val worker = HandlerThread("island-system").apply { start() }
        val handler = Handler(worker.looper)
        val audio = context.getSystemService(AudioManager::class.java)
        val notifications = context.getSystemService(NotificationManager::class.java)
        // isMusicActive is read when Android reports a playback change, and at that instant it can still say "active" for a
        // player that has only just paused; no further callback follows, so the island kept showing "playing" (pause icon,
        // wiggling bars) until something else changed. Read it again a moment later, so the settled answer is what sticks.
        fun syncPlaying() = state.setPlaying(System.currentTimeMillis(), audio.isMusicActive)
        val recheck = Runnable { syncPlaying() }
        fun syncSoonAndLater() {
            syncPlaying()
            handler.removeCallbacks(recheck)
            handler.postDelayed(recheck, 350L)
            handler.postDelayed(recheck, 1_200L)
        }
        state.recheckPlayback = { handler.post { syncSoonAndLater() } }
        val playback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) = syncSoonAndLater()
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                // The ringer-mode and airplane-mode broadcasts are sticky: registering a receiver is handed the current value at
                // once. That is the state the phone is already in, not a change, and it was shown as an event every time the
                // island appeared (each unlock), so a phone left on vibrate announced "vibrate" on every unlock.
                if (isInitialStickyBroadcast) return
                when (intent?.action) {
                    // The ringer mode rides along in the broadcast, so no service call is needed for it.
                    AudioManager.RINGER_MODE_CHANGED_ACTION -> state.showEvent(IslandEvents.ringer(
                        intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)))
                    Intent.ACTION_AIRPLANE_MODE_CHANGED -> state.showEvent(
                        IslandEvents.airplane(intent.getBooleanExtra("state", false)))
                    NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> state.showEvent(
                        IslandEvents.doNotDisturb(notifications.currentInterruptionFilter))
                }
            }
        }
        // Camera and microphone indicators. Both are public callbacks that need no permission: Android tells any app when a
        // camera device becomes busy or an app starts recording, without saying which app or what was captured. Nothing is
        // kept. Registered only while the island is on screen and only if the user has not turned the indicators off.
        val indicators = context.getSharedPreferences("extras", Context.MODE_PRIVATE).getBoolean("privacyIndicators", true)
        val cameras = context.getSystemService(android.hardware.camera2.CameraManager::class.java)
        val busyCameras = mutableSetOf<String>()
        val torchOn = mutableSetOf<String>()
        // The island's own flashlight is not "an app using the camera".
        fun syncCamera() { state.cameraActive = busyCameras.isNotEmpty() && torchOn.isEmpty() }
        val cameraCallback = object : android.hardware.camera2.CameraManager.AvailabilityCallback() {
            override fun onCameraUnavailable(cameraId: String) { busyCameras += cameraId; syncCamera() }
            override fun onCameraAvailable(cameraId: String) { busyCameras -= cameraId; syncCamera() }
        }
        val torchCallback = object : android.hardware.camera2.CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (enabled) torchOn += cameraId else torchOn -= cameraId
                syncCamera()
            }
        }
        val recordingCallback = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<android.media.AudioRecordingConfiguration>?) {
                state.micActive = configs.orEmpty().any { !it.isClientSilenced }
            }
        }
        // VPN and USB-data alerts, from public callbacks that need no extra permission. Android replays the current state to a new
        // listener, so the first answer after registering is the state the phone is already in and is not shown as a change.
        val alerts = context.getSharedPreferences("extras", Context.MODE_PRIVATE).getBoolean("securityAlerts", true)
        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
        val registeredAt = android.os.SystemClock.elapsedRealtime()
        val vpnNetworks = mutableSetOf<android.net.Network>()
        val vpnCallback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                val wasUp = vpnNetworks.isNotEmpty()
                vpnNetworks += network
                if (!wasUp && android.os.SystemClock.elapsedRealtime() - registeredAt > 1_500L) state.showEvent(IslandEvents.vpn(true))
            }
            override fun onLost(network: android.net.Network) {
                if (vpnNetworks.remove(network) && vpnNetworks.isEmpty()) state.showEvent(IslandEvents.vpn(false))
            }
        }
        var usbDataWas = false
        val usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val extras = intent?.extras ?: return
                val functions = extras.keySet().filter { it != "connected" && it != "configured" && extras.getBoolean(it, false) }.toSet()
                val data = UsbState.dataActive(extras.getBoolean("connected"), extras.getBoolean("configured"), functions)
                val changed = data != usbDataWas
                usbDataWas = data
                if (changed && !isInitialStickyBroadcast) state.showEvent(IslandEvents.usbData(data))
            }
        }
        if (alerts) {
            runCatching {
                connectivity?.registerNetworkCallback(android.net.NetworkRequest.Builder()
                    .addTransportType(android.net.NetworkCapabilities.TRANSPORT_VPN)
                    .removeCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), vpnCallback, handler)
            }
            runCatching {
                ContextCompat.registerReceiver(context, usbReceiver, IntentFilter("android.hardware.usb.action.USB_STATE"), null,
                    handler, ContextCompat.RECEIVER_NOT_EXPORTED)
            }
        }
        // Bluetooth devices connecting or disconnecting (a watch, a keyboard, a car). Needs the Nearby devices permission to read a device's
        // name, so it is only registered when the person switched it on and Android has granted it. Audio devices keep their own alert above.
        val extrasPrefs = context.getSharedPreferences("extras", Context.MODE_PRIVATE)
        val bluetoothOn = extrasPrefs.getBoolean("bluetoothAlerts", false) &&
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val bluetoothShownAt = HashMap<String, Long>()
        val bluetoothReceiver = object : BroadcastReceiver() {
            // The permission is checked here on every broadcast (it can be revoked while the island is up); a device's name, class and address
            // are only read after that.
            @android.annotation.SuppressLint("MissingPermission")
            override fun onReceive(c: Context?, intent: Intent?) {
                if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
                val device = intent?.let { androidx.core.content.IntentCompat.getParcelableExtra(it, android.bluetooth.BluetoothDevice.EXTRA_DEVICE, android.bluetooth.BluetoothDevice::class.java) } ?: return
                val connected = intent.action == android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED
                val major = runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull() ?: 0
                if (!BluetoothEvents.shouldShow(major, extrasPrefs.getBoolean("audioAlerts", true))) return
                val key = runCatching { device.address }.getOrNull() ?: return
                val now = android.os.SystemClock.elapsedRealtime()
                if (BluetoothEvents.isRepeat(bluetoothShownAt[key], now)) return
                bluetoothShownAt[key] = now
                state.showEvent(BluetoothEvents.event(connected, runCatching { device.name }.getOrNull()))
            }
        }
        if (bluetoothOn) runCatching {
            ContextCompat.registerReceiver(context, bluetoothReceiver, IntentFilter().apply {
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }, null, handler, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        // Headphones and Bluetooth audio connecting. AudioDeviceCallback needs no permission; Android replays the devices already
        // connected the moment a callback is registered, which is not an event, so anything in the first moments is ignored.
        val audioAlertsOn = context.getSharedPreferences("extras", Context.MODE_PRIVATE).getBoolean("audioAlerts", true)
        val audioRegisteredAt = android.os.SystemClock.elapsedRealtime()
        val deviceCallback = object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out android.media.AudioDeviceInfo>?) {
                if (android.os.SystemClock.elapsedRealtime() - audioRegisteredAt < 1_500L) return
                added.orEmpty().firstOrNull { it.isSink && AudioDevices.label(it.type, it.productName) != null }?.let {
                    state.showEvent(IslandEvents.audioDevice(true, AudioDevices.label(it.type, it.productName)!!))
                }
            }
            override fun onAudioDevicesRemoved(removed: Array<out android.media.AudioDeviceInfo>?) {
                if (android.os.SystemClock.elapsedRealtime() - audioRegisteredAt < 1_500L) return
                removed.orEmpty().firstOrNull { it.isSink && AudioDevices.label(it.type, it.productName) != null }?.let {
                    state.showEvent(IslandEvents.audioDevice(false, AudioDevices.label(it.type, it.productName)!!))
                }
            }
        }
        // Screen recording (Android 15+): Android tells a window when it is being captured. DETECT_SCREEN_RECORDING is a normal,
        // install-time permission; the answer is only "recorded" or "not", never by what.
        val windows = context.getSystemService(android.view.WindowManager::class.java)
        val recordingConsumer = java.util.function.Consumer<Int> { stateCode ->
            state.recordingActive = stateCode == android.view.WindowManager.SCREEN_RECORDING_STATE_VISIBLE
        }
        val recordingExecutor = java.util.concurrent.Executor { handler.post(it) }
        if (indicators && android.os.Build.VERSION.SDK_INT >= 35) {
            runCatching { state.recordingActive = windows.addScreenRecordingCallback(recordingExecutor, recordingConsumer) ==
                android.view.WindowManager.SCREEN_RECORDING_STATE_VISIBLE }
        }
        handler.post {
            if (audioAlertsOn) runCatching { audio.registerAudioDeviceCallback(deviceCallback, handler) }
            audio.registerAudioPlaybackCallback(playback, handler)
            state.setPlaying(System.currentTimeMillis(), audio.isMusicActive)
            if (indicators) {
                runCatching { cameras?.registerTorchCallback(torchCallback, handler) }
                runCatching { cameras?.registerAvailabilityCallback(cameraCallback, handler) }
                runCatching {
                    audio.registerAudioRecordingCallback(recordingCallback, handler)
                    state.micActive = audio.activeRecordingConfigurations.any { !it.isClientSilenced }
                }
            }
        }
        // Plugging in, reaching full and running low (see BatteryMoments). The battery broadcast is sticky: its first delivery is the
        // current state, which only seeds the comparison. This used to be Home's alone (and flashed again each time Home reappeared while
        // charging); here it is seen over other apps too, and once.
        var lastBattery: BatteryMoments.Reading? = null
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent ?: return
                val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                if (level < 0) return
                val now = BatteryMoments.Reading(level * 100 / scale, intent.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0)
                val event = BatteryMoments.event(lastBattery, now)
                lastBattery = now
                if (event != null) state.showEvent(event)
            }
        }
        ContextCompat.registerReceiver(context, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), null, handler,
            ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }, null, handler, ContextCompat.RECEIVER_NOT_EXPORTED)
        stop = {
            state.recheckPlayback = null
            context.unregisterReceiver(receiver)
            runCatching { context.unregisterReceiver(batteryReceiver) }
            runCatching { context.unregisterReceiver(usbReceiver) }
            runCatching { context.unregisterReceiver(bluetoothReceiver) }
            runCatching { connectivity?.unregisterNetworkCallback(vpnCallback) }
            handler.removeCallbacksAndMessages(null)
            handler.post {
                runCatching { audio.unregisterAudioDeviceCallback(deviceCallback) }
                if (android.os.Build.VERSION.SDK_INT >= 35) runCatching { windows.removeScreenRecordingCallback(recordingConsumer) }
                state.recordingActive = false
                audio.unregisterAudioPlaybackCallback(playback)
                runCatching { cameras?.unregisterAvailabilityCallback(cameraCallback) }
                runCatching { cameras?.unregisterTorchCallback(torchCallback) }
                runCatching { audio.unregisterAudioRecordingCallback(recordingCallback) }
                state.micActive = false; state.cameraActive = false
                worker.quitSafely()
            }
        }
    }

    override fun close() = stop()
}

/**
 * The one owner of [IslandSystemEvents]: hosts hold it by name and the events run while any does, once. Main thread only. A change to
 * one of the settings the events read at registration restarts them, so a switch takes effect without the island having to reappear.
 */
internal object IslandEventsOwner {
    private val holders = mutableSetOf<String>()
    private var events: IslandSystemEvents? = null
    private var appContext: Context? = null
    private val KEYS = setOf("privacyIndicators", "securityAlerts", "bluetoothAlerts", "audioAlerts")
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in KEYS && events != null) { events?.close(); events = appContext?.let { IslandSystemEvents(it, IslandRuntime.state) } }
    }

    val running: Boolean get() = events != null

    fun hold(holder: String, context: Context) {
        if (!holders.add(holder) || events != null) return
        val app = context.applicationContext
        appContext = app
        app.getSharedPreferences("extras", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefsListener)
        events = IslandSystemEvents(app, IslandRuntime.state)
    }

    fun release(holder: String) {
        if (!holders.remove(holder) || holders.isNotEmpty()) return
        appContext?.getSharedPreferences("extras", Context.MODE_PRIVATE)?.unregisterOnSharedPreferenceChangeListener(prefsListener)
        events?.close(); events = null
    }
}

/** Holds the island's system events for as long as this is composed and [active] (see [IslandEventsOwner]). */
@Composable
internal fun HoldIslandEvents(holder: String, active: Boolean) {
    val context = LocalContext.current
    DisposableEffect(holder, active) {
        if (active) IslandEventsOwner.hold(holder, context)
        onDispose { if (active) IslandEventsOwner.release(holder) }
    }
}

