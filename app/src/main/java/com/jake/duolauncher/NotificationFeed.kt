package com.jake.duolauncher

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.HandlerThread
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What is playing, as far as media sessions say (only while the user has allowed notification access
 * and turned on media details).
 */
internal data class NowPlaying(
    val title: String?,
    val artist: String?,
    val art: Bitmap?,
    val playing: Boolean,
    val controller: MediaController,
)

/** Process-wide, read-only view of the listener's findings, for Compose to read. */
internal object NotificationFeed {
    var badges by mutableStateOf<Map<String, Int>>(emptyMap())
    var nowPlaying by mutableStateOf<NowPlaying?>(null)
    var connected by mutableStateOf(false)
    /** Set by the activity; called with an app's name when a new notification should peek in the island. */
    @Volatile var onPeek: ((packageName: String, label: String) -> Unit)? = null
}

/** Opt-in notification access, used for three things and nothing else: unread counts for icon badges,
 * the current track's title and artwork, and a brief "app name" peek in the island. Nothing is stored,
 * logged or sent anywhere, and message text is never read. Until the user turns access on in Android's
 * settings this service is never bound; each feature also has its own switch in Customize.
 *
 * Every call into the system (active notifications, media sessions) runs on a private worker thread.
 */
class UnoNotificationListener : NotificationListenerService() {
    private var worker: HandlerThread? = null
    private var handler: Handler? = null
    private var sessions: MediaSessionManager? = null
    private var component: ComponentName? = null
    private var tracked: MediaController? = null
    private var trackedCallback: MediaController.Callback? = null
    private val lastPeek = mutableMapOf<String, Long>()
    private val prefs by lazy { getSharedPreferences("extras", Context.MODE_PRIVATE) }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler?.post { recount(); syncMedia() }
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { syncMedia(it) }

    override fun onListenerConnected() {
        val thread = HandlerThread("notification-feed").apply { start() }
        worker = thread
        handler = Handler(thread.looper)
        component = ComponentName(this, UnoNotificationListener::class.java)
        sessions = getSystemService(MediaSessionManager::class.java)
        NotificationFeed.connected = true
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        handler?.post {
            recount()
            runCatching { sessions?.addOnActiveSessionsChangedListener(sessionsListener, component, handler) }
            syncMedia()
        }
    }

    override fun onListenerDisconnected() { teardown() }
    override fun onDestroy() { teardown(); super.onDestroy() }

    private fun teardown() {
        NotificationFeed.connected = false
        NotificationFeed.badges = emptyMap()
        NotificationFeed.nowPlaying = null
        runCatching { prefs.unregisterOnSharedPreferenceChangeListener(prefListener) }
        runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionsListener) }
        untrack()
        worker?.quitSafely(); worker = null; handler = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handler?.post { recount(); peek(sbn) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) { handler?.post { recount() } }

    private fun recount() {
        if (!prefs.getBoolean("badges", false)) { NotificationFeed.badges = emptyMap(); return }
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        NotificationFeed.badges = BadgeLogic.counts(active.filter { it.packageName != packageName }.map {
            ListedNotification(it.packageName,
                ongoing = it.notification.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0,
                groupSummary = it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0)
        })
    }

    private fun peek(sbn: StatusBarNotification) {
        if (!prefs.getBoolean("notificationPeek", false) || sbn.packageName == packageName) return
        val flags = sbn.notification.flags
        if (flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_GROUP_SUMMARY) != 0) return
        val now = System.currentTimeMillis()
        // One peek per app every few seconds, so a chat that sends ten messages doesn't flicker the island.
        if (now - (lastPeek[sbn.packageName] ?: 0L) < PEEK_GAP_MS) return
        lastPeek[sbn.packageName] = now
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrNull() ?: return
        NotificationFeed.onPeek?.invoke(sbn.packageName, label)
    }

    private fun syncMedia(list: List<MediaController>? = null) {
        if (!prefs.getBoolean("mediaDetails", false)) { untrack(); NotificationFeed.nowPlaying = null; return }
        val controllers = list ?: runCatching { sessions?.getActiveSessions(component) }.getOrNull().orEmpty()
        val chosen = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: controllers.firstOrNull()
        if (chosen == null) { untrack(); NotificationFeed.nowPlaying = null; return }
        if (tracked?.sessionToken != chosen.sessionToken) {
            untrack()
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = publish(chosen)
                override fun onPlaybackStateChanged(state: PlaybackState?) = publish(chosen)
                override fun onSessionDestroyed() { handler?.post { syncMedia() } }
            }
            tracked = chosen; trackedCallback = callback
            runCatching { chosen.registerCallback(callback, handler) }
        }
        publish(chosen)
    }

    private fun publish(controller: MediaController) {
        val meta = controller.metadata
        NotificationFeed.nowPlaying = NowPlaying(
            title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            art = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART),
            playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING,
            controller = controller)
    }

    private fun untrack() {
        runCatching { trackedCallback?.let { tracked?.unregisterCallback(it) } }
        tracked = null; trackedCallback = null
    }

    private companion object { const val PEEK_GAP_MS = 4_000L }
}
