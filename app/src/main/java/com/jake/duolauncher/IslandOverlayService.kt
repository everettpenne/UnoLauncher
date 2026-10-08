package com.jake.duolauncher

import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

/** The everywhere-overlay: the island as a window above other apps.
 *
 * Touch safety is the core design constraint. The window is sized to exactly the island's
 * current bounds (never full-screen), is NOT_FOCUSABLE and NOT_TOUCH_MODAL, and is shrunk to
 * 1x1 px whenever it must not be shown. Touches outside the island's own bounds therefore
 * pass to whatever is beneath it — the window cannot intercept or fake anything else, and it
 * never draws on the lock screen or while the display is off.
 */
class IslandOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var permissionReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        savedStateController.performRestore(null)
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
        addWindow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (composeView == null) addWindow()
        return START_STICKY
    }

    private fun addWindow() {
        val manager = windowManager ?: return
        val view = ComposeView(this)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            OverlayPolicy.WINDOW_FLAGS,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        try {
            manager.addView(view, params)
        } catch (e: Exception) {
            // Permission missing or the window manager refuses overlays: never keep a dead window.
            stopSelf(); return
        }
        composeView = view
        layoutParams = params
        IslandRuntime.updateOverlay(true)

        // Overlay windows receive neither status-bar nor cutout insets reliably, so the
        // island's environment is built from the display itself: real metrics for the size,
        // Display.getCutout for the camera, and the system status-bar height for the fallback.
        // The display is read again each time, not once: turning the phone sideways swaps its width and height, and
        // the camera moves to a side edge, so a size captured at start would leave the island where it was.
        fun realMetrics() = android.util.DisplayMetrics().also {
            @Suppress("DEPRECATION")
            manager.defaultDisplay.getRealMetrics(it)
        }
        val statusBarPx = runCatching {
            resources.getDimensionPixelSize(resources.getIdentifier("status_bar_height", "dimen", "android"))
        }.getOrDefault(0)
        fun buildEnvironment(): IslandEnvironment {
            val m = realMetrics()
            return IslandEnvironment(
                cutout = readDisplayCutout(manager.defaultDisplay, m.widthPixels.toFloat(), m.heightPixels.toFloat()),
                screenWidth = m.widthPixels.toFloat(),
                statusBarHeight = statusBarPx.toFloat(),
                dockWidthPx = 0f,
                screenHeight = m.heightPixels.toFloat())
        }

        var environment by androidx.compose.runtime.mutableStateOf(buildEnvironment())
        val keyguard = getSystemService(KeyguardManager::class.java)
        val power = getSystemService(PowerManager::class.java)
        view.setContent {
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            androidx.compose.runtime.LaunchedEffect(configuration) { environment = buildEnvironment() }
            var visible by androidx.compose.runtime.remember { mutableStateOf(true) }
            var islandScale by androidx.compose.runtime.remember {
                mutableStateOf(getSharedPreferences("appearance", MODE_PRIVATE).getFloat("islandScale", .5f).coerceIn(0f, 1f))
            }
            LaunchedEffect(Unit) {
                var lastVisible = true
                var lastWidth = -1
                while (true) {
                    val enabled = getSharedPreferences("extras", MODE_PRIVATE).getBoolean("islandEverywhere", false)
                    visible = OverlayPolicy.shouldShow(enabled, Settings.canDrawOverlays(this@IslandOverlayService),
                        keyguard.isKeyguardLocked, power.isInteractive)
                    islandScale = getSharedPreferences("appearance", MODE_PRIVATE)
                        .getFloat("islandScale", .5f).coerceIn(0f, 1f)
                    // Keep the window 1x1 when hidden: present but untouchable and invisible.
                    // Only touch the layout when something actually changed, so an update can
                    // never feed a relayout loop.
                    val p = layoutParams ?: break
                    val wanted = if (visible) WindowManager.LayoutParams.WRAP_CONTENT else 1
                    if (visible != lastVisible || wanted != lastWidth) {
                        runCatching {
                            manager.updateViewLayout(view, p.apply { width = wanted; height = wanted })
                        }
                        lastVisible = visible; lastWidth = wanted
                    }
                    delay(2_000L)
                }
            }
            if (visible) {
                DynamicIsland(
                    state = IslandRuntime.state,
                    glass = null,
                    deviceStatus = rememberBatteryStatus(),
                    feedHeadline = null,
                    sizeScale = islandScale,
                    dockWidthPx = 0f,
                    environmentOverride = environment,
                    anchoredToWindow = true,
                    showActions = false,
                    onFrameChanged = { frame ->
                        val p = layoutParams ?: return@DynamicIsland
                        if (p.x != frame.left.toInt() || p.y != frame.top.toInt()) {
                            runCatching {
                                manager.updateViewLayout(view, p.apply {
                                    x = frame.left.toInt(); y = frame.top.toInt()
                                })
                            }
                        }
                    },
                    onSearch = {},
                    onOpenFeed = {},
                    onCustomize = {},
                )
            }
        }
    }

    override fun onDestroy() {
        permissionReceiver?.let { runCatching { unregisterReceiver(it) } }
        permissionReceiver = null
        val view = composeView
        composeView = null
        layoutParams = null
        if (view != null) runCatching { windowManager?.removeView(view) }
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        IslandRuntime.updateOverlay(false)
        super.onDestroy()
    }
}
