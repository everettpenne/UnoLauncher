package com.jake.duolauncher

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager
import androidx.core.content.ContextCompat

/** The everywhere-overlay as a plain application overlay (needs "display over other apps").
 *
 * Android layers application overlays beneath the status bar, and the status bar owns touches in its own strip, so this
 * island is covered by the status icons and cannot be tapped where the camera is. It is the fallback for when
 * [SystemShadeAccessibilityService] (whose overlay sits above the status bar) is not enabled; when that service is
 * connected it hosts the island and this service stops itself.
 */
class IslandOverlayService : Service() {
    private var host: IslandOverlayHost? = null
    private var permissionReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (SystemShadeAccessibilityService.hostsIsland()) { stopSelf(); return }
        // If the user revokes "display over other apps", the overlay stops immediately.
        permissionReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (!Settings.canDrawOverlays(this@IslandOverlayService)) stopSelf()
            }
        }.also {
            ContextCompat.registerReceiver(this, it,
                IntentFilter("android.app.action.MANAGE_OVERLAY_PERMISSION_CHANGED"),
                ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        host = IslandOverlayHost(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            needsOverlayPermission = true, onRefused = { stopSelf() }).also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (SystemShadeAccessibilityService.hostsIsland()) { stopSelf(); return START_NOT_STICKY }
        host?.start()
        return START_STICKY
    }

    override fun onDestroy() {
        permissionReceiver?.let { runCatching { unregisterReceiver(it) } }
        permissionReceiver = null
        host?.stop()
        host = null
        super.onDestroy()
    }
}
