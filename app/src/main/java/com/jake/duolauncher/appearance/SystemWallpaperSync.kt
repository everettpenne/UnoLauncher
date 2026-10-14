package com.jake.duolauncher.appearance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps Android's home and lock screen wallpaper in step with the launcher's day or night look, when "also set Android's home and lock
 * screen" is on and the background is one of the built-in scenes (a photo is the same by day and night, so it is left alone).
 *
 * The launcher used to send the wallpaper only at the moment a background was chosen, so when the phone went from day to night (the
 * system theme changing, or the sunrise and sunset schedule) the launcher itself changed but the lock screen kept the old scene.
 * The change is now noticed wherever it happens: the Application watches configuration changes (the system's light/dark switch) and
 * the minute tick (the sunrise and sunset schedule), so it works with the launcher out of sight, and the activity also asks when it
 * comes to the front. The scene last sent is remembered, so the wallpaper is set once per change and not on every tick.
 */
internal object SystemWallpaperSync {
    private const val LAST_DARK = "systemWallpaperDark"
    private val busy = AtomicBoolean(false)

    /** Pure: whether the system wallpaper should be re-sent now. [lastSentDark] is null when it has never been recorded. */
    fun needsPush(syncOn: Boolean, builtinScene: Boolean, lastSentDark: Boolean?, dark: Boolean): Boolean =
        syncOn && builtinScene && lastSentDark != dark

    /** Registers for the minute tick for as long as the process lives. Call once, from Application.onCreate. */
    fun start(app: android.app.Application) {
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { check(app) }
        }, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** Re-sends the wallpaper if the day or night look has changed since it was last sent. Cheap when nothing is to be done. */
    fun check(context: Context, systemDark: Boolean? = null) {
        val app = context.applicationContext
        val prefs = launcherBackgroundPreferences(app)
        if (!prefs.getBoolean("systemWallpaperSync", false)) return
        val builtinScene = !launcherBackgroundEnabled(app)
        val dark = AppearanceStore(app).also { store ->
            store.refresh(systemDark ?: (app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES))
        }.state.dark
        val last = if (prefs.contains(LAST_DARK)) prefs.getBoolean(LAST_DARK, false) else null
        if (!needsPush(true, builtinScene, last, dark)) return
        if (!busy.compareAndSet(false, true)) return
        val kind = builtinBackground(app)
        Thread {
            try { push(app, kind, dark) } finally { busy.set(false) }
        }.start()
    }

    /** Draws [kind] for day or night at the screen's size and hands it to Android for both home and lock screens. Blocking. */
    fun push(context: Context, kind: BuiltinBackground, dark: Boolean): Result<Unit> = runCatching {
        val app = context.applicationContext
        val metrics = app.resources.displayMetrics
        val bitmap = DuneSceneCache.systemBitmap(kind, minOf(metrics.widthPixels, metrics.heightPixels),
            maxOf(metrics.widthPixels, metrics.heightPixels), dark)
        android.app.WallpaperManager.getInstance(app).setBitmap(bitmap, null, true,
            android.app.WallpaperManager.FLAG_SYSTEM or android.app.WallpaperManager.FLAG_LOCK)
        launcherBackgroundPreferences(app).edit().putBoolean(LAST_DARK, dark).apply()
    }
}
