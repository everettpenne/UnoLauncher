package com.jake.duolauncher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/** Frame timing over the last second or two, kept as plain numbers so it is unit-tested. Intervals are between frame callbacks,
 * so a slow frame shows up as a long interval whether the cost was drawing or waiting for the system.
 */
internal class FrameStats(private val capacity: Int = 120, private val jankMs: Float = 24f) {
    private val intervals = FloatArray(capacity)
    private var count = 0
    private var next = 0
    private var lastNanos = 0L

    fun record(frameTimeNanos: Long) {
        if (lastNanos != 0L) {
            val ms = (frameTimeNanos - lastNanos) / 1_000_000f
            // A long idle gap (nothing was animating) is not a slow frame; only intervals the screen would have noticed count.
            if (ms < IDLE_GAP_MS) { intervals[next] = ms; next = (next + 1) % capacity; if (count < capacity) count++ }
        }
        lastNanos = frameTimeNanos
    }

    fun reset() { count = 0; next = 0; lastNanos = 0L }
    val samples: Int get() = count
    val averageMs: Float get() = if (count == 0) 0f else intervals.take(count).sum() / count
    val worstMs: Float get() = if (count == 0) 0f else intervals.take(count).max()
    val fps: Float get() = averageMs.let { if (it <= 0f) 0f else 1000f / it }
    val jankPercent: Int get() = if (count == 0) 0 else (100f * intervals.take(count).count { it > jankMs } / count).toInt()

    companion object { const val IDLE_GAP_MS = 250f }
}

/** What the HUD shows about the island overlay, gathered by the host. */
internal data class HudSnapshot(
    val screenW: Int, val screenH: Int, val cutout: String, val island: String, val drawWindow: String, val touchWindow: String,
    val expanded: Boolean, val statusBarVisible: Boolean, val hiddenForFullScreen: Boolean, val shown: Boolean,
    val liveCount: Int, val queued: Int, val insets: String = "",
) {
    fun lines(stats: FrameStats): List<String> = listOf(
        "fps %.0f  avg %.1fms  worst %.0fms  jank %d%%".format(stats.fps, stats.averageMs, stats.worstMs, stats.jankPercent),
        "screen ${screenW}x$screenH  cutout $cutout",
        "island $island",
        "draw window $drawWindow",
        "touch window $touchWindow",
        "expanded=$expanded bars=${if (statusBarVisible) "visible" else "hidden"} fullScreenHidden=$hiddenForFullScreen shown=$shown",
        "live=$liveCount queued=$queued",
        "insets $insets",
    )
}

/** A small readout over the island overlay for debuggable builds only: frame timing and window sizes. It is a non-touchable text
 * window, drawn only while the "Island debug HUD" switch in a debug build is on; a release build never starts it.
 */
internal class IslandDebugHud(private val context: Context, private val windowType: Int) {
    private val main = Handler(Looper.getMainLooper())
    private val stats = FrameStats()
    private var view: TextView? = null
    private var snapshot: HudSnapshot? = null
    private var running = false
    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            stats.record(frameTimeNanos)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
    private val refresh = object : Runnable {
        override fun run() {
            if (!running) return
            val s = snapshot
            view?.text = if (s == null) "waiting for the island…" else s.lines(stats).joinToString("\n")
            main.postDelayed(this, 250L)
        }
    }

    @SuppressLint("SetTextI18n")
    fun start() {
        if (running) return
        val manager = context.getSystemService(WindowManager::class.java) ?: return
        val text = TextView(context).apply {
            typeface = Typeface.MONOSPACE; textSize = 9.5f; setTextColor(Color.rgb(0x7C, 0xFF, 0xB2)); setBackgroundColor(Color.argb(200, 0, 0, 0))
            setPadding(12, 8, 12, 8)
        }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.BOTTOM or Gravity.START; x = 8; y = 220
        }
        if (runCatching { manager.addView(text, params) }.isFailure) return
        view = text; running = true; stats.reset()
        Choreographer.getInstance().postFrameCallback(frame); main.post(refresh)
    }

    fun update(next: HudSnapshot) { snapshot = next }

    fun stop() {
        running = false
        main.removeCallbacks(refresh)
        view?.let { v -> runCatching { context.getSystemService(WindowManager::class.java)?.removeView(v) } }
        view = null
    }

    companion object {
        /** Debug builds only, and only while the switch is on. */
        fun enabled(context: Context): Boolean =
            context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
                context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getBoolean("debugHud", false)
    }
}
