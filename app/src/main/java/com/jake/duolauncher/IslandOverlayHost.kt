package com.jake.duolauncher

import android.app.KeyguardManager
import android.content.Context
import android.graphics.PixelFormat
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
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** The everywhere-overlay window: the island above other apps. Hosted by [IslandOverlayService] (a plain
 * application overlay, which Android layers beneath the status bar) or by [SystemShadeAccessibilityService]
 * (an accessibility overlay, which sits above the status bar and so can be seen and tapped there).
 *
 * Touch safety is the core design constraint. The window is sized to exactly the island's
 * current bounds (never full-screen), is NOT_FOCUSABLE and NOT_TOUCH_MODAL, and is shrunk to
 * 1x1 px whenever it must not be shown. Touches outside the island's own bounds therefore
 * pass to whatever is beneath it — the window cannot intercept or fake anything else, and it
 * never draws on the lock screen or while the display is off.
 */
internal class IslandOverlayHost(
    private val context: Context,
    private val windowType: Int,
    /** True for the accessibility overlay, which needs no "display over other apps" permission. */
    private val needsOverlayPermission: Boolean,
    /** Called when the window manager refuses the window, so the owner can stop. */
    private val onRefused: () -> Unit,
) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var started = false

    val isShowing: Boolean get() = composeView != null

    fun start() {
        if (started) return
        started = true
        windowManager = context.getSystemService(WindowManager::class.java)
        savedStateController.performRestore(null)
        addWindow()
    }

    private fun addWindow() {
        val manager = windowManager ?: return
        // A tap anywhere outside the island, in another window, collapses it (ACTION_OUTSIDE is delivered because the
        // window asks to watch outside touches; it carries no position, so nothing about the other window leaks).
        val view = ComposeView(context)
        view.setOnTouchListener { _, ev ->
            if (ev.actionMasked == android.view.MotionEvent.ACTION_OUTSIDE && IslandRuntime.state.expanded) {
                IslandRuntime.state.collapse()
            }
            false
        }
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            OverlayPolicy.WINDOW_FLAGS,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        try {
            manager.addView(view, params)
        } catch (e: Exception) {
            // Permission missing or the window manager refuses overlays: never keep a dead window.
            started = false
            onRefused(); return
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
            context.resources.getDimensionPixelSize(context.resources.getIdentifier("status_bar_height", "dimen", "android"))
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
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        view.setContent {
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            androidx.compose.runtime.LaunchedEffect(configuration) { environment = buildEnvironment() }
            var visible by androidx.compose.runtime.remember { mutableStateOf(true) }
            var islandScale by androidx.compose.runtime.remember {
                mutableStateOf(context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getFloat("islandScale", .5f).coerceIn(0f, 1f))
            }
            LaunchedEffect(Unit) {
                var lastVisible = true
                var lastWidth = -1
                while (true) {
                    val enabled = context.getSharedPreferences("extras", Context.MODE_PRIVATE).getBoolean("islandEverywhere", false)
                    visible = OverlayPolicy.shouldShow(enabled, (!needsOverlayPermission || Settings.canDrawOverlays(context)),
                        keyguard.isKeyguardLocked, power.isInteractive)
                    islandScale = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
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
                        // The window is anchored at the point that stays put while the island grows and shrinks: its
                        // horizontal centre when the camera is on top (the island is symmetric about the camera). The window
                        // then resizes around that anchor in the same layout pass as the content, where moving it with
                        // updateViewLayout each frame lagged the shrinking content and made the island jump sideways as it
                        // collapsed. A camera on a side edge keeps top-left placement.
                        val centred = frame.side == IslandSide.TOP
                        val gravity = if (centred) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.TOP or Gravity.START
                        val x = if (centred) (frame.left + frame.width / 2f - environment.screenWidth / 2f).roundToInt()
                            else frame.left.toInt()
                        val y = frame.top.toInt()
                        if (p.gravity != gravity || p.x != x || p.y != y) {
                            runCatching {
                                manager.updateViewLayout(view, p.apply { this.gravity = gravity; this.x = x; this.y = y })
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

    fun stop() {
        started = false
        val view = composeView
        composeView = null
        layoutParams = null
        if (view != null) runCatching { windowManager?.removeView(view) }
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        IslandRuntime.updateOverlay(false)
    }
}
