package com.jake.duolauncher.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon

/** Debug builds only: posts or clears a Live Update so the island's handling can be tried on an emulator.
 *
 *   adb shell am broadcast -n com.jake.duolauncher/.debug.DebugLiveUpdateReceiver --es title "Ride to the airport" \
 *       --es short "12 min" --ei progress 40 --ei max 100
 *   adb shell am broadcast -n com.jake.duolauncher/.debug.DebugLiveUpdateReceiver --ez clear true
 */
@android.annotation.SuppressLint("NewApi")
class DebugLiveUpdateReceiver : BroadcastReceiver() {
    // Only ever driven by adb on an Android 16 emulator; older versions have no ProgressStyle to post.
    override fun onReceive(context: Context, intent: Intent) {
        // --ez camera true / --ez mic true stand in for the privacy indicators, to get several live activities at once.
        if (intent.hasExtra("camera")) com.jake.duolauncher.IslandRuntime.state.cameraActive = intent.getBooleanExtra("camera", false)
        if (intent.hasExtra("mic")) com.jake.duolauncher.IslandRuntime.state.micActive = intent.getBooleanExtra("mic", false)
        if (!intent.hasExtra("title") && !intent.hasExtra("clear") && !intent.hasExtra("short")) return
        if (android.os.Build.VERSION.SDK_INT < 36) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (intent.getBooleanExtra("clear", false)) { manager.cancel(ID); return }
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Debug live updates", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0, Intent(context, com.jake.duolauncher.MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val max = intent.getIntExtra("max", 100)
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(Icon.createWithResource(context, android.R.drawable.ic_menu_directions))
            .setContentTitle(intent.getStringExtra("title") ?: "Ride to the airport")
            .setContentText(intent.getStringExtra("text") ?: "Driver is 4 minutes away")
            .setOngoing(true).setContentIntent(open)
            // What Notification.Builder.setRequestPromotedOngoing(true) (API 37) sets; written directly as the SDK built against lacks it.
            .addExtras(android.os.Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
            .setStyle(Notification.ProgressStyle().setProgress(intent.getIntExtra("progress", 40)).setProgressSegments(
                listOf(Notification.ProgressStyle.Segment(max))))
        intent.getStringExtra("short")?.let { builder.setShortCriticalText(it) }
        manager.notify(ID, builder.build())
    }
    private companion object { const val ID = 4242; const val CHANNEL = "debug-live" }
}
