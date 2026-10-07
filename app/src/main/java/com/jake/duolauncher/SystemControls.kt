package com.jake.duolauncher

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicInteger

/** The live values behind the control panel, and the actions that change them.
 *
 * Every call into the audio, camera, settings and notification services is a binder round trip that
 * can stall for seconds when those services are busy (the island's ANRs came from exactly this), so
 * all of it runs on one private worker thread. The UI only reads and writes snapshot state, and a
 * slider drag is coalesced so a fast drag queues at most one pending write per control.
 */
@Stable
internal class SystemControls internal constructor(
    private val context: Context,
    private val handler: Handler,
) {
    var volume by mutableIntStateOf(0); private set
    var volumeMax by mutableIntStateOf(1); private set
    var ringerMode by mutableIntStateOf(AudioManager.RINGER_MODE_NORMAL); private set
    var hasDndAccess by mutableStateOf(false); private set
    var torchOn by mutableStateOf(false); private set
    var torchAvailable by mutableStateOf(false); private set
    var brightness by mutableIntStateOf(128); private set
    var canWriteBrightness by mutableStateOf(false); private set
    var playing by mutableStateOf(false); private set

    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val cameras = context.getSystemService(CameraManager::class.java)
    private var torchId: String? = null

    /** When the user last moved a slider, so the echo of our own write doesn't snap the thumb back. */
    @Volatile private var lastUserChangeMs = 0L
    private fun userIsDragging() = System.currentTimeMillis() - lastUserChangeMs < ECHO_QUIET_MS

    private val pendingVolume = AtomicInteger(NONE)
    private val pendingBrightness = AtomicInteger(NONE)

    /** Reads everything once; called on the worker when the panel opens and when a source changes. */
    internal fun refresh() {
        volumeMax = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        if (!userIsDragging()) volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        ringerMode = audio.ringerMode
        hasDndAccess = notifications.isNotificationPolicyAccessGranted
        canWriteBrightness = Settings.System.canWrite(context)
        if (!userIsDragging()) brightness = readBrightness()
        playing = audio.isMusicActive
    }

    private fun readBrightness() = Settings.System.getInt(context.contentResolver,
        Settings.System.SCREEN_BRIGHTNESS, 128)

    internal fun pickTorch() {
        torchId = runCatching {
            ControlLogic.pickTorchCamera(cameras.cameraIdList.map { id ->
                val c = cameras.getCameraCharacteristics(id)
                TorchCamera(id, c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK)
            })
        }.getOrNull()
        torchAvailable = torchId != null
    }

    internal val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) { torchOn = enabled; torchAvailable = true }
        }
        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == torchId) torchAvailable = false
        }
    }

    internal val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            playing = audio.isMusicActive
        }
    }

    internal val brightnessObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            if (!userIsDragging()) brightness = readBrightness()
        }
    }

    /** Slider position in 0..1. The thumb moves at once; the write follows on the worker. */
    fun setVolume(fraction: Float) {
        val target = ControlLogic.volumeFor(fraction, volumeMax)
        lastUserChangeMs = System.currentTimeMillis()
        volume = target
        coalesce(pendingVolume, target) { audio.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0) }
    }

    fun setBrightness(fraction: Float) {
        if (!canWriteBrightness) return
        val target = ControlLogic.brightnessFor(fraction)
        lastUserChangeMs = System.currentTimeMillis()
        brightness = target
        coalesce(pendingBrightness, target) { value ->
            val resolver = context.contentResolver
            // A manual value is ignored while automatic brightness is on, so moving the slider is
            // taken to mean "I want this level" and turns automatic brightness off.
            runCatching {
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
            }
        }
    }

    /** Applies the newest pending value on the worker, however many were pushed while it was busy. */
    private fun coalesce(slot: AtomicInteger, value: Int, apply: (Int) -> Unit) {
        if (slot.getAndSet(value) == NONE) handler.post {
            val latest = slot.getAndSet(NONE)
            if (latest != NONE) runCatching { apply(latest) }
        }
    }

    /** Returns what the caller should do next when the mode can't be set from here. */
    fun requestRinger(target: Int): RingerRequest {
        val request = ControlLogic.ringerRequest(ringerMode, target, hasDndAccess)
        if (request == RingerRequest.SET) {
            ringerMode = target
            handler.post { runCatching { audio.ringerMode = target }.onFailure { refresh() } }
        }
        return request
    }

    fun toggleTorch() {
        val id = torchId ?: return
        val next = !torchOn
        handler.post {
            try { cameras.setTorchMode(id, next) }
            catch (_: CameraAccessException) { torchAvailable = false }
            catch (_: IllegalArgumentException) { torchAvailable = false }
        }
    }

    fun mediaKey(keyCode: Int) = sendMediaKey(context, keyCode)

    private companion object {
        const val NONE = Int.MIN_VALUE
        const val ECHO_QUIET_MS = 500L
    }
}

/** Starts the controls when the panel enters composition and tears every listener down when it
 * leaves, so nothing runs while the panel is closed.
 */
@Composable
internal fun rememberSystemControls(): SystemControls {
    val context = LocalContext.current
    val worker = remember { HandlerThread("control-panel").apply { start() } }
    val handler = remember(worker) { Handler(worker.looper) }
    val controls = remember(context, handler) { SystemControls(context, handler) }
    DisposableEffect(controls) {
        val audio = context.getSystemService(AudioManager::class.java)
        val cameras = context.getSystemService(CameraManager::class.java)
        val resolver = context.contentResolver
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) { controls.refresh() }
        }
        handler.post {
            runCatching { controls.refresh() }
            runCatching { controls.pickTorch() }
            runCatching { cameras.registerTorchCallback(controls.torchCallback, handler) }
            runCatching { audio.registerAudioPlaybackCallback(controls.playbackCallback, handler) }
            runCatching { resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, controls.brightnessObserver) }
        }
        // VOLUME_CHANGED_ACTION is not a public constant, but every Android version broadcasts it.
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }, null, handler, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            context.unregisterReceiver(receiver)
            handler.post {
                runCatching { cameras.unregisterTorchCallback(controls.torchCallback) }
                runCatching { audio.unregisterAudioPlaybackCallback(controls.playbackCallback) }
                runCatching { resolver.unregisterContentObserver(controls.brightnessObserver) }
                worker.quitSafely()
            }
        }
    }
    return controls
}
