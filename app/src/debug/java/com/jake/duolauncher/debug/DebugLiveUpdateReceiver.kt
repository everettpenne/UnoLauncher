package com.jake.duolauncher.debug

import com.jake.duolauncher.island.*

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
        if (intent.hasExtra("camera")) com.jake.duolauncher.island.IslandRuntime.state.cameraActive = intent.getBooleanExtra("camera", false)
        if (intent.hasExtra("recording")) com.jake.duolauncher.island.IslandRuntime.state.recordingActive = intent.getBooleanExtra("recording", false)
        if (intent.hasExtra("mic")) com.jake.duolauncher.island.IslandRuntime.state.micActive = intent.getBooleanExtra("mic", false)
        // --es inject call|nav|download|clear puts that activity straight into the island's state (no notification, so no listener or Android 16 needed),
        // to see its card on any phone: call --es caller "Mina Okafor"; nav --es title "300 m" --es text "Turn right onto Main Street"; download --ei progress 60.
        intent.getStringExtra("inject")?.let {
            inject(context, it, intent)
            // --ez card true then opens that activity's long-press card at once (the same card a long press on the pill opens), for a steady screenshot.
            if (intent.getBooleanExtra("card", false)) {
                val kind = when (it) { "call" -> com.jake.duolauncher.island.LiveKind.CALL; "nav", "download", "update" -> com.jake.duolauncher.island.LiveKind.UPDATE; else -> null }
                com.jake.duolauncher.island.IslandQuickActions.card(context, kind, System.currentTimeMillis())?.let { card ->
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ com.jake.duolauncher.island.IslandRuntime.state.showPeekCard(card) }, 600L)
                }
            }
            return
        }
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
    private fun inject(context: Context, what: String, intent: Intent) {
        val feed = com.jake.duolauncher.NotificationFeed
        fun update(source: com.jake.duolauncher.island.ActivitySource, title: String, text: String?, progress: Int, large: android.graphics.Bitmap?, short: String?) =
            com.jake.duolauncher.island.LiveUpdate("debug-$what", "com.example.debug", "Debug", title, text, short, progress, 100, false, null, false, null, 0xFF30B0C7.toInt(),
                System.currentTimeMillis(), null, large, source)
        when (what) {
            "clear" -> { feed.ongoingCall = null; feed.liveUpdates = emptyList() }
            "call" -> feed.ongoingCall = com.jake.duolauncher.OngoingCall(intent.getStringExtra("caller") ?: "Mina Okafor", "com.example.dialer",
                System.currentTimeMillis() - 83_000L, null, null)
            "nav" -> feed.liveUpdates = listOf(update(com.jake.duolauncher.island.ActivitySource.NAVIGATION, intent.getStringExtra("title") ?: "300 m",
                intent.getStringExtra("text") ?: "Turn right onto Main Street", 0, turnArrow(), "12 min"))
            "download" -> feed.liveUpdates = listOf(update(com.jake.duolauncher.island.ActivitySource.PROGRESS, intent.getStringExtra("title") ?: "Downloading episode", null,
                intent.getIntExtra("progress", 60), null, null))
            "update" -> feed.liveUpdates = listOf(update(com.jake.duolauncher.island.ActivitySource.PROMOTED, intent.getStringExtra("title") ?: "Ride to the airport",
                intent.getStringExtra("text") ?: "Driver is 4 minutes away", intent.getIntExtra("progress", 40), null, intent.getStringExtra("short") ?: "4 min"))
        }
    }

    /** A white right-turn arrow, as a navigation app's large icon. */
    private fun turnArrow(): android.graphics.Bitmap = android.graphics.Bitmap.createBitmap(224, 224, android.graphics.Bitmap.Config.ARGB_8888).also { b ->
        val c = android.graphics.Canvas(b); val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        val path = android.graphics.Path().apply { moveTo(80f, 200f); lineTo(80f, 110f); quadTo(80f, 70f, 120f, 70f); lineTo(140f, 70f); lineTo(140f, 40f); lineTo(196f, 90f)
            lineTo(140f, 140f); lineTo(140f, 110f); lineTo(120f, 110f); lineTo(120f, 200f); close() }
        c.drawPath(path, p)
    }

    private companion object { const val ID = 4242; const val CHANNEL = "debug-live" }
}
