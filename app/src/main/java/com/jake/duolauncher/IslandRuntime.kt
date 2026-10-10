package com.jake.duolauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.awaitCancellation

/** Process-wide island state, shared by Home and the everywhere-overlay so the two surfaces can
 * never show two islands with different state. Owned by nobody in particular: whoever renders
 * the island reads this.
 */
internal object IslandRuntime {
    val state = IslandState()
    var overlayActive by mutableStateOf(false)
        private set

    /** Whether the system status bar is showing, approximated: false while the display is landscape (see FullScreenPolicy). */
    /** Room the pop-out bubbles need to the right of the pill, in pixels, so the touch window covers them. */
    @Volatile var bubbleExtraPx = 0
    var statusBarVisible by mutableStateOf(true)
    
    fun updateOverlay(active: Boolean) {
        overlayActive = active
        if (!active) { state.collapse(); state.dismissFlash() }
    }
}

/** When to get out of the way of an app that has taken the whole screen (a full-screen video, a game). The island cannot see other
 * apps' windows, but it can see whether the status bar is showing: an app that goes full screen hides it. The island waits a
 * moment after the bar disappears before hiding, so a bar that is only briefly away (an app swapping screens, the keyboard
 * opening, a swipe that reveals it) does not make it flicker, and comes back at once when the bar returns.
 */
internal object FullScreenPolicy {
    const val HIDE_AFTER_MS = 500L

    /** True once the status bar has been gone for [HIDE_AFTER_MS]; false whenever it is showing. */
    fun shouldHide(statusBarVisible: Boolean, goneForMs: Long): Boolean = !statusBarVisible && goneForMs >= HIDE_AFTER_MS
}

/** Pure gate for the everywhere-overlay: enabled, permission held, screen awake, and keyguard
 * unlocked. The overlay never draws on the lock screen or while the display is off.
 */
internal object OverlayPolicy {
    /** Window flags for the island overlay.
     *
     * FLAG_LAYOUT_IN_SCREEN and FLAG_LAYOUT_NO_LIMITS are what make the window's x and y absolute screen pixels. Without
     * them the window's parent frame starts below the status bar, so the island (positioned from Display.getCutout, which is in
     * screen pixels) landed a status-bar height below the camera. It stays NOT_FOCUSABLE and NOT_TOUCH_MODAL so touches
     * outside the island's own bounds pass through, and is never FLAG_FULLSCREEN or touchable beyond its size.
     * WATCH_OUTSIDE_TOUCH tells the window (without a position) that a touch landed elsewhere, so an expanded island can
     * tuck itself away when the user taps outside it.
     */
    const val WINDOW_FLAGS: Int = android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
        android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

    fun shouldShow(islandEverywhere: Boolean, canDrawOverlays: Boolean, keyguardLocked: Boolean,
        interactive: Boolean): Boolean = islandEverywhere && canDrawOverlays && !keyguardLocked && interactive
}

/** Battery state for the overlay window, read from the sticky broadcast; the overlay has no
 * activity lifecycle, so it observes this directly instead of a DeviceStatusMonitor.
 */
@Composable
internal fun rememberBatteryStatus(): DeviceStatus {
    val context = LocalContext.current
    val status by produceState(DeviceStatus()) {
        fun read(intent: Intent): DeviceStatus {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val charge = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            return DeviceStatus(
                battery = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null,
                charging = charge == BatteryManager.BATTERY_STATUS_CHARGING || charge == BatteryManager.BATTERY_STATUS_FULL)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) value = read(intent)
            }
        }
        val sticky = ContextCompat.registerReceiver(context, receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        if (sticky != null) value = read(sticky)
        try { awaitCancellation() } finally { runCatching { context.unregisterReceiver(receiver) } }
    }
    return status
}
