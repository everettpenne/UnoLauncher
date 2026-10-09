package com.jake.duolauncher

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
}

/** A brief event shown in the collapsed island, like iOS's ringer, charging and Focus flashes. */
internal data class IslandEvent(val title: String, val symbol: IslandSymbol)

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
            IslandEvent("Focus off", IslandSymbol.FOCUS)
        else IslandEvent("Do Not Disturb", IslandSymbol.FOCUS)

    fun vpn(on: Boolean) = IslandEvent(if (on) "VPN on" else "VPN off", IslandSymbol.VPN)

    /** A USB data connection starting or ending. Plain charging has its own event; this is the link that carries data. */
    fun usbData(on: Boolean) = IslandEvent(if (on) "USB data connected" else "USB data off", IslandSymbol.USB)

    fun charging(percent: Int?) = IslandEvent(if (percent != null) "Charging $percent%" else "Charging", IslandSymbol.CHARGING)
}

/** Whether [symbol] describes the ringer rather than a Focus change. */
internal fun IslandSymbol.isRinger() = this == IslandSymbol.RINGER || this == IslandSymbol.SILENT || this == IslandSymbol.VIBRATE

/** When the island offers playback controls: while audio plays, and for a while after it stops so a
 * paused track can be resumed from the island (a paused player no longer counts as "active").
 */
internal object IslandPlayback {
    const val RESUME_WINDOW_MS = 90_000L

    /** [nowMs] comes from a clock that ticks every few seconds, so right after a pause it can be older than [lastPlayingAtMs]. That
     * used to count as "outside the window" and hid the controls until the next tick, with the island still open and empty; anything
     * not yet past the window, however recent, is inside it.
     */
    fun controlsVisible(nowMs: Long, playing: Boolean, lastPlayingAtMs: Long): Boolean =
        playing || (lastPlayingAtMs > 0L && nowMs - lastPlayingAtMs <= RESUME_WINDOW_MS)
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

/** Feeds the island with what the system reports while it is on screen: whether audio is playing, and
 * ringer, airplane and Do Not Disturb changes. All of it is permission-free and unregistered the
 * moment the island leaves composition, so nothing runs while Home is not visible.
 *
 * Every call into the audio and notification services is a binder round trip that can stall for
 * seconds when those services are busy, so none of it happens on the main thread: registration, the
 * initial query, the playback callback and the receiver all run on a private worker thread, and only
 * the resulting state writes (which are thread-safe) reach the UI.
 */
@Composable
internal fun IslandSystemEvents(state: IslandState) {
    val context = LocalContext.current
    DisposableEffect(context) {
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
                val functions = extras.keySet().filter { it != "connected" && it != "configured" && extras.get(it) == true }.toSet()
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
        handler.post {
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
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }, null, handler, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            state.recheckPlayback = null
            context.unregisterReceiver(receiver)
            runCatching { context.unregisterReceiver(usbReceiver) }
            runCatching { connectivity?.unregisterNetworkCallback(vpnCallback) }
            handler.removeCallbacksAndMessages(null)
            handler.post {
                audio.unregisterAudioPlaybackCallback(playback)
                runCatching { cameras?.unregisterAvailabilityCallback(cameraCallback) }
                runCatching { cameras?.unregisterTorchCallback(torchCallback) }
                runCatching { audio.unregisterAudioRecordingCallback(recordingCallback) }
                state.micActive = false; state.cameraActive = false
                worker.quitSafely()
            }
        }
    }
}
