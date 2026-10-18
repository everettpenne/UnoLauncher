package com.jake.duolauncher

import com.jake.duolauncher.island.*

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
import androidx.core.graphics.drawable.toBitmap

/** What is playing, as far as media sessions say (only while the user has allowed notification access
 * and turned on media details).
 */
/** A live call as the phone app's call notification presents it. Displayed in the island while it
 * lasts; the caller text and start time are held in memory only and never stored.
 */
internal data class OngoingCall(val caller: String, val packageName: String, val startedAt: Long,
    val openIntent: android.app.PendingIntent?,
    /** The call notification's own hang-up action, when it has one (an ongoing call); the island's End button sends it. */
    val hangUp: android.app.PendingIntent? = null)

/** Pure rules for recognising a live call from a notification's public fields. */
internal object CallLogic {
    /** The caller when [category]/[title] describe a live call notification, else null.
     * Ongoing only, from another app, in the call category, with a short caller string.
     */
    fun callerFrom(category: String?, title: CharSequence?, ongoing: Boolean, packageName: String,
        ownPackage: String): String? {
        if (!ongoing || packageName == ownPackage || category != Notification.CATEGORY_CALL) return null
        val text = title?.toString()?.trim().orEmpty()
        if (text.isBlank() || text.length > 80) return null
        return text
    }
}

internal data class NowPlaying(
    val title: String?,
    val artist: String?,
    val art: Bitmap?,
    val playing: Boolean,
    val controller: MediaController,
    /** For deciding whether paused media is worth coming back to (see IslandPlayback.resumable). */
    val stateCode: Int? = null,
    val positionMs: Long = -1L,
    val durationMs: Long = -1L,
    /** When [positionMs] was reported (SystemClock.elapsedRealtime), and how fast the player moves: together they let a screen draw the
     *  moving position without asking the player again (a binder call that must not be made while drawing). */
    val positionUpdatedAtMs: Long = 0L,
    val speed: Float = 1f,
)

/** Process-wide, read-only view of the listener's findings, for Compose to read. */
internal object NotificationFeed {
    var badges by mutableStateOf<Map<String, Int>>(emptyMap())
    /** Whether badges draw as a dot rather than a count. Set by the listener from the settings. */
    var badgeDots by mutableStateOf(false)
    var nowPlaying by mutableStateOf<NowPlaying?>(null)
    var ongoingCall by mutableStateOf<OngoingCall?>(null)
    /** Android 16 Live Updates currently posted, newest first. */
    var liveUpdates by mutableStateOf<List<LiveUpdate>>(emptyList())
    var connected by mutableStateOf(false)
    /** Set by the activity; called with an app's name when a new notification should peek in the island. */
    @Volatile var onPeek: ((PeekNotification) -> Unit)? = null
}

/** Opt-in notification access, used for three things and nothing else: unread counts for icon badges,
 * the current track's title and artwork, and a brief "app name" peek in the island. Nothing is stored,
 * logged or sent anywhere, and chat and message notifications are never read for their text (the only text taken is a promoted Live Update's, a navigation instruction's and, if the user turns it on, a peek card's). Until the user turns access on in Android's
 * settings this service is never bound; each feature also has its own switch in Customize.
 *
 * Every call into the system (active notifications, media sessions) runs on a private worker thread.
 */
class UnoNotificationListener : NotificationListenerService() {
    private var worker: HandlerThread? = null
    private var handler: Handler? = null
    private var sessions: MediaSessionManager? = null
    private var component: ComponentName? = null
    private val watched = HashMap<android.media.session.MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private var latest: List<MediaController> = emptyList()
    private var chosenToken: android.media.session.MediaSession.Token? = null
    private val lastPeek = mutableMapOf<String, Long>()
    private val prefs by lazy { getSharedPreferences("extras", Context.MODE_PRIVATE) }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler?.post { recount(); syncMedia(); syncLiveUpdates() }
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
            recount(); syncLiveUpdates()
            runCatching { sessions?.addOnActiveSessionsChangedListener(sessionsListener, component, handler) }
            syncMedia()
            scheduleSweep()
        }
    }

    override fun onListenerDisconnected() { teardown() }
    override fun onDestroy() { teardown(); super.onDestroy() }

    private fun teardown() {
        handler?.removeCallbacks(sweep)
        NotificationFeed.connected = false
        NotificationFeed.badges = emptyMap()
        NotificationFeed.nowPlaying = null
        NotificationFeed.ongoingCall = null
        NotificationFeed.liveUpdates = emptyList()
        runCatching { prefs.unregisterOnSharedPreferenceChangeListener(prefListener) }
        runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionsListener) }
        unwatchAll()
        worker?.quitSafely(); worker = null; handler = null
    }

    // A download posts a fresh notification every second and a busy phone posts bursts: each one used to recount badges and re-read every
    // active notification. The reads now wait a moment and run once for the whole burst; the peek and the call check stay immediate.
    private val syncAll = Runnable { recount(); syncLiveUpdates() }
    private fun scheduleSync() { handler?.let { it.removeCallbacks(syncAll); it.postDelayed(syncAll, SYNC_DELAY_MS) } }

    // A safety net for Live Updates: an app can end its activity (or die) without a removal event reaching this
    // listener, or a notification can be removed while the listener is re-binding. Every so often re-read the
    // active notifications and drop anything whose notification is gone, so a navigation icon never lingers.
    private val sweep = Runnable { syncLiveUpdates(); scheduleSweep() }
    private fun scheduleSweep() { handler?.let { it.removeCallbacks(sweep); it.postDelayed(sweep, LiveUpdateLogic.SAFETY_SWEEP_MS) } }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handler?.post { peek(sbn); trackCall(sbn); scheduleSync() }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        handler?.post { untrackCall(sbn); scheduleSync() }
    }

    /** Reads what the island shows as activities out of the active notifications: Live Updates (the ones Android 16 has promoted),
     * navigation instructions, and progress tasks such as downloads. Only the title, body text, short status, progress, clock and small
     * icon are taken, each kind only while its own switch is on, and ordinary chat and message notifications are never read for their text.
     */
    private fun syncLiveUpdates() {
        val liveOn = prefs.getBoolean("liveUpdates", true)
        val navOn = prefs.getBoolean("navigationTasks", true)
        val tasksOn = prefs.getBoolean("progressTasks", true)
        if (!liveOn && !navOn && !tasksOn) { NotificationFeed.liveUpdates = emptyList(); return }
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val presentKeys = active.map { it.key }.toSet()
        // Debug builds also show their own (the test poster); a release build never lists its own notifications.
        val debuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        val muted = IslandLinks.parse(prefs.getString("islandMuted", null))
        val found = active.filter { IslandLinks.allowed(it.packageName, muted) && (debuggable || it.packageName != packageName) }
            .mapNotNull { sbn -> runCatching {
                val n = sbn.notification; val e = n.extras
                val promoted = LiveUpdateLogic.isLiveUpdate(n.flags)
                val source = LiveUpdateLogic.classify(promoted, n.category, LiveUpdateLogic.isOngoing(n.flags),
                    e.getInt(Notification.EXTRA_PROGRESS, 0), e.getInt(Notification.EXTRA_PROGRESS_MAX, 0),
                    e.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false), liveOn, navOn, tasksOn) ?: return@runCatching null
                val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
                if (title.isEmpty()) return@runCatching null
                val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }
                    .getOrDefault(sbn.packageName)
                val chrono = if (e.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)) n.`when` else null
                // The short status exists only on Android 16; older versions have no such field to read.
                val shortCritical = if (android.os.Build.VERSION.SDK_INT >= 36) n.shortCriticalText else null
                LiveUpdateLogic.build(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    appLabel = label,
                    postTime = sbn.postTime,
                    source = source,
                    promoted = promoted,
                    category = n.category,
                    title = title,
                    shortCriticalText = shortCritical,
                    text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                    progress = e.getInt(Notification.EXTRA_PROGRESS, 0),
                    progressMax = e.getInt(Notification.EXTRA_PROGRESS_MAX, 0),
                    indeterminate = e.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false),
                    chronometerBase = chrono,
                    countDown = e.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false),
                    // The app's own colour if it gave one, else the colour of its icon.
                    color = n.color.takeIf { it != 0 } ?: AppTints.of(sbn.packageName) { runCatching { packageManager.getApplicationIcon(sbn.packageName).toBitmap(48, 48) }.getOrNull() } ?: 0,
                ).copy(
                    icon = smallIconOf(n),
                    openIntent = n.contentIntent,
                    // The turn arrow of a navigation instruction is its large icon; no other kind of activity has its large icon read.
                    largeIcon = if (source == ActivitySource.NAVIGATION) runCatching { n.getLargeIcon()?.loadDrawable(this)?.toBitmap(NavigationText.ARROW_PX, NavigationText.ARROW_PX) }.getOrNull() else null,
                )
            }.getOrNull() }
        NotificationFeed.liveUpdates = LiveUpdateLogic.present(found, presentKeys)
        checkNavigationCue(found)
    }

    private var lastNavigation: NavigationCue.Snapshot? = null
    private var lastNavigationCueAt = 0L

    /** Drops the island down for a moment when the navigation instruction changed in a way worth a glance (see [NavigationCue]). The
     * card is the one the island already uses for a notification, handed straight to the island's own state, so it works whether or not the
     * Home screen is the thing on screen. */
    private fun checkNavigationCue(found: List<LiveUpdate>) {
        val nav = found.firstOrNull { it.source == ActivitySource.NAVIGATION }
        if (nav == null || !prefs.getBoolean("navigationCues", true)) { lastNavigation = null; return }
        val now = NavigationCue.Snapshot(nav.key, nav.text, arrowSignature(nav.largeIcon), NavigationCue.parseMeters(nav.title))
        val elapsed = android.os.SystemClock.elapsedRealtime()
        val reason = NavigationCue.reason(lastNavigation, now, elapsed - lastNavigationCueAt)
        lastNavigation = now
        if (reason == null) return
        lastNavigationCueAt = elapsed
        val card = PeekNotification(nav.packageName, nav.appLabel, nav.title, nav.text, nav.largeIcon ?: nav.icon, nav.openIntent,
            NavigationCue.SECONDS, nav.color.takeIf { it != 0 }, navigation = true)
        // Spotlight is the island's search function; while it is open the island stays closed, so a turn cue is held off rather than
        // dropped into an island that isn't there (the arbiter covers notification cards; this path bypasses it, so guard it the same way).
        Handler(android.os.Looper.getMainLooper()).post { if (!IslandRuntime.spotlightOpen) IslandRuntime.state.showPeekCard(card) }
    }

    /** A fingerprint of an arrow image (a 16 by 16 shrink of it), so a different arrow is noticed without comparing every pixel. */
    private fun arrowSignature(bitmap: Bitmap?): Int? = bitmap?.let {
        runCatching {
            val small = Bitmap.createScaledBitmap(it, 16, 16, true)
            val pixels = IntArray(16 * 16).also { px -> small.getPixels(px, 0, 16, 0, 0, 16, 16) }
            pixels.contentHashCode()
        }.getOrNull()
    }

    /** The notification's small icon (a white glyph on transparent) as a small bitmap, or null. */
    private fun smallIconOf(n: Notification): Bitmap? = runCatching {
        val drawable = n.smallIcon?.loadDrawable(this) ?: return@runCatching null
        drawable.toBitmap(48, 48)
    }.getOrNull()

    private fun trackCall(sbn: StatusBarNotification) {
        if (!prefs.getBoolean("callDetails", true)) { NotificationFeed.ongoingCall = null; return }
        val caller = CallLogic.callerFrom(sbn.notification.category, sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE),
            sbn.isOngoing, sbn.packageName, packageName)
        if (caller != null) {
            val n = sbn.notification
            val hangUpAt = CallActions.hangUpIndex(n.extras.getInt("android.callType", 0), n.actions?.size ?: 0)
            NotificationFeed.ongoingCall = OngoingCall(caller, sbn.packageName, sbn.postTime, n.contentIntent,
                hangUpAt?.let { n.actions[it].actionIntent })
        }
    }

    private fun untrackCall(sbn: StatusBarNotification) {
        if (NotificationFeed.ongoingCall?.packageName == sbn.packageName) {
            NotificationFeed.ongoingCall = null
        }
    }

    private fun recount() {
        NotificationFeed.badgeDots = prefs.getBoolean("badgeDots", false)
        if (!prefs.getBoolean("badges", false)) { NotificationFeed.badges = emptyMap(); return }
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val muted = BadgeMute.parse(prefs.getString("badgeMuted", null))
        NotificationFeed.badges = BadgeLogic.counts(active.filter { it.packageName != packageName && it.packageName !in muted }.map {
            ListedNotification(it.packageName,
                ongoing = it.notification.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0,
                groupSummary = it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0)
        })
    }

    private fun peek(sbn: StatusBarNotification) {
        if (!prefs.getBoolean("notificationPeek", false) || sbn.packageName == packageName) return
        if (!IslandLinks.allowed(sbn.packageName, IslandLinks.parse(prefs.getString("islandMuted", null)))) return
        val flags = sbn.notification.flags
        if (flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_GROUP_SUMMARY) != 0) return
        val now = System.currentTimeMillis()
        // One peek per app every few seconds, so a chat that sends ten messages doesn't flicker the island.
        if (now - (lastPeek[sbn.packageName] ?: 0L) < PEEK_GAP_MS) return
        lastPeek[sbn.packageName] = now
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrNull() ?: return
        // The notification's own words are read only when the person chose a level that shows them, and then only to show them (or to
        // check them against the blocked words); nothing is kept.
        val level = PeekLevel.of(prefs.getInt("peekLevel", 0))
        val shown = if (level == PeekLevel.NAME) null else {
            val e = sbn.notification.extras
            val text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: e.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            PeekContent.visible(level, e.getCharSequence(Notification.EXTRA_TITLE)?.toString(), text,
                PeekContent.parseWords(prefs.getString("peekBlocked", null)))
        }
        val seconds = PeekTimeouts.seconds(PeekTimeouts.parse(prefs.getString("peekTimeouts", null)), sbn.packageName)
        val icon = runCatching { packageManager.getApplicationIcon(sbn.packageName).toBitmap(96, 96) }.getOrNull()
        val tint = AppTints.of(sbn.packageName) { icon }
        // The notification's own buttons go on the card only where its words do: a card that is just the app's name stays just that.
        val actions = if (shown != null && prefs.getBoolean("peekActions", true)) peekActions(sbn.notification) else emptyList()
        NotificationFeed.onPeek?.invoke(PeekNotification(sbn.packageName, label, shown?.first, shown?.second, icon,
            sbn.notification.contentIntent, seconds, tint, actions))
    }

    /** The buttons a notification offers, as card actions: a reply with free-form text first, then the others (see [PeekActionRules]). */
    private fun peekActions(n: Notification): List<PeekAction> {
        val all = n.actions.orEmpty()
        val raw = all.map { a ->
            val inputs = a.remoteInputs.orEmpty()
            val typed = inputs.any { it.allowFreeFormInput }
            // A button that asks for a choice rather than text cannot be sent from here, so it is left out.
            RawAction(a.title?.toString(), a.actionIntent != null && (inputs.isEmpty() || typed), typed, a.isContextual)
        }
        return PeekActionRules.pick(raw).map { i ->
            val a = all[i]
            PeekAction(PeekActionRules.label(a.title.toString()), a.actionIntent, a.remoteInputs.orEmpty().firstOrNull { it.allowFreeFormInput })
        }
    }

    private fun syncMedia(list: List<MediaController>? = null) {
        if (!prefs.getBoolean("mediaDetails", false)) { unwatchAll(); NotificationFeed.nowPlaying = null; return }
        val controllers = list ?: runCatching { sessions?.getActiveSessions(component) }.getOrNull().orEmpty()
        latest = controllers
        // Every session is watched, not just the one on show. It used to follow only the first one it chose, so a second player
        // that started while the first sat paused went unnoticed until the list of sessions itself changed, and the island kept
        // showing the paused one (or nothing playing) while music was audible.
        val gone = watched.keys.filter { token -> controllers.none { it.sessionToken == token } }
        gone.forEach { token -> watched.remove(token)?.let { (controller, callback) -> runCatching { controller.unregisterCallback(callback) } } }
        controllers.forEach { controller ->
            val token = controller.sessionToken ?: return@forEach
            if (token in watched) return@forEach
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) { handler?.post { choose() } }
                override fun onPlaybackStateChanged(state: PlaybackState?) { handler?.post { choose() } }
                override fun onSessionDestroyed() { handler?.post { syncMedia() } }
            }
            watched[token] = controller to callback
            runCatching { controller.registerCallback(callback, handler) }
        }
        choose()
    }

    /** Picks what the island shows: whatever is playing now, else the one it was already showing, else the first. */
    private fun choose() {
        val playing = latest.firstOrNull { IslandPlayback.sessionPlaying(it.playbackState?.state) }
        val keep = latest.firstOrNull { it.sessionToken == chosenToken }
        val chosen = playing ?: keep ?: latest.firstOrNull()
        chosenToken = chosen?.sessionToken
        if (chosen == null) NotificationFeed.nowPlaying = null else publish(chosen)
    }

    private fun publish(controller: MediaController) {
        val meta = controller.metadata
        NotificationFeed.nowPlaying = NowPlaying(
            title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            art = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART),
            playing = IslandPlayback.sessionPlaying(controller.playbackState?.state),
            controller = controller,
            stateCode = controller.playbackState?.state,
            positionMs = controller.playbackState?.position ?: -1L,
            durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L,
            positionUpdatedAtMs = controller.playbackState?.lastPositionUpdateTime ?: 0L,
            speed = controller.playbackState?.playbackSpeed ?: 1f)
    }

    private fun unwatchAll() {
        watched.values.forEach { (controller, callback) -> runCatching { controller.unregisterCallback(callback) } }
        watched.clear(); latest = emptyList(); chosenToken = null
    }

    private companion object { const val PEEK_GAP_MS = 4_000L; const val SYNC_DELAY_MS = 150L }
}
