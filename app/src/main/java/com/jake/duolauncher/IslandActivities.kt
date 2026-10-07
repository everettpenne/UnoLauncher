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

    fun charging(percent: Int?) = IslandEvent(if (percent != null) "Charging $percent%" else "Charging", IslandSymbol.CHARGING)
}

/** Whether [symbol] describes the ringer rather than a Focus change. */
internal fun IslandSymbol.isRinger() = this == IslandSymbol.RINGER || this == IslandSymbol.SILENT || this == IslandSymbol.VIBRATE

/** When the island offers playback controls: while audio plays, and for a while after it stops so a
 * paused track can be resumed from the island (a paused player no longer counts as "active").
 */
internal object IslandPlayback {
    const val RESUME_WINDOW_MS = 90_000L

    fun controlsVisible(nowMs: Long, playing: Boolean, lastPlayingAtMs: Long): Boolean =
        playing || (lastPlayingAtMs > 0L && nowMs - lastPlayingAtMs in 0..RESUME_WINDOW_MS)
}

/** Sends a media key to whichever player is active. [AudioManager.dispatchMediaKeyEvent] needs no
 * permission, and no notification access: the island can pause, resume and skip, but it cannot
 * read a title or artwork, which would need that access.
 */
internal fun sendMediaKey(context: Context, keyCode: Int) {
    val audio = context.getSystemService(AudioManager::class.java) ?: return
    // A binder call, so not on the main thread.
    Thread {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }.start()
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
        val playback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                state.setPlaying(System.currentTimeMillis(), audio.isMusicActive)
            }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
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
        handler.post {
            audio.registerAudioPlaybackCallback(playback, handler)
            state.setPlaying(System.currentTimeMillis(), audio.isMusicActive)
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }, null, handler, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            context.unregisterReceiver(receiver)
            handler.post { audio.unregisterAudioPlaybackCallback(playback); worker.quitSafely() }
        }
    }
}
