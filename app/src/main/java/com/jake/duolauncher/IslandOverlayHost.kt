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
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** The everywhere-overlay: the island above other apps. Hosted by [IslandOverlayService] (a plain application overlay, which
 * Android layers beneath the status bar) or by [SystemShadeAccessibilityService] (an accessibility overlay, which sits above the
 * status bar and so can be seen and tapped there).
 *
 * With the camera on top the island is drawn by two windows. The drawing window is a fixed size, big enough for the open
 * island, and is NOT_TOUCHABLE: it never resizes while the island animates, because a window that changes size shows its old,
 * smaller picture anchored at the new window's corner until the new one arrives (the island opened from the wrong corner and
 * collapsed through the wrong place). A second, transparent window follows the island's current bounds and takes the touches;
 * it draws nothing, so resizing it every frame is invisible, and it forwards touches to the drawing window's view. Touches
 * anywhere else, inside the big drawing window or not, pass straight through to the app below. With the camera on a side edge
 * (phone turned sideways) a single window sized to the island is used instead.
 *
 * Touch safety stays the design constraint: nothing here is focusable or full-screen, and nothing draws on the lock screen or
 * while the display is off (both windows shrink to 1x1 and stop taking touches).
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
    private var touchView: TouchProxy? = null
    private var touchParams: WindowManager.LayoutParams? = null
    private var started = false

    // What the island last asked for, and whether it may be shown at all.
    private var shown = true
    private var lastFrame: IslandFrame? = null
    private var lastWindow: PxRect? = null

    val isShowing: Boolean get() = composeView != null

    fun start() {
        if (started) return
        started = true
        windowManager = context.getSystemService(WindowManager::class.java)
        savedStateController.performRestore(null)
        addWindow()
    }

    private fun collapseIfOpen() { if (IslandRuntime.state.expanded) IslandRuntime.state.collapse() }

    private fun addWindow() {
        val manager = windowManager ?: return
        // A tap anywhere outside the island, in another window, collapses it (ACTION_OUTSIDE is delivered because the
        // window asks to watch outside touches; it carries no position, so nothing about the other window leaks).
        val view = ComposeView(context)
        view.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) collapseIfOpen()
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
            // Starts at the top centre, where the island nearly always belongs, rather than at the screen's top-left corner (a
            // flash on every start). Not parked off-screen: a window that is fully off-screen is not drawn, so the island never
            // composed and never reported where it should go.
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
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
        // Debuggable builds only: a "debugCutout" = "left,top,right,bottom" pixel string in the appearance preferences stands in
        // for the camera, so an emulator without a realistic cutout can be given one (a Pixel's centred punch hole). Release
        // builds are not debuggable, so this never applies to them.
        fun debugCutout(): PxRect? {
            if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
            val raw = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("debugCutout", null) ?: return null
            val v = raw.split(',').mapNotNull { it.trim().toFloatOrNull() }
            return if (v.size == 4) PxRect(v[0], v[1], v[2], v[3]) else null
        }
        fun buildEnvironment(): IslandEnvironment {
            val m = realMetrics()
            return IslandEnvironment(
                cutout = debugCutout() ?: readDisplayCutout(manager.defaultDisplay, m.widthPixels.toFloat(), m.heightPixels.toFloat()),
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
                while (true) {
                    val enabled = context.getSharedPreferences("extras", Context.MODE_PRIVATE).getBoolean("islandEverywhere", false)
                    visible = OverlayPolicy.shouldShow(enabled, (!needsOverlayPermission || Settings.canDrawOverlays(context)),
                        keyguard.isKeyguardLocked, power.isInteractive)
                    islandScale = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
                        .getFloat("islandScale", .5f).coerceIn(0f, 1f)
                    // Keep the windows 1x1 and untouchable when hidden. applyLayout only touches a window whose layout actually
                    // changed, so this can never feed a relayout loop.
                    if (shown != visible) { shown = visible; applyLayout() }
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
                    onFrameChanged = { frame, window ->
                        lastFrame = frame; lastWindow = window
                        applyLayout()
                    },
                    onSearch = {},
                    onOpenFeed = {},
                    onCustomize = {},
                )
            }
        }
    }

    private val outsideFlag = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
    private val untouchable = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    /** Brings both windows to what the island last asked for. Each is only updated if something about it changed. */
    private fun applyLayout() {
        val manager = windowManager ?: return
        val view = composeView ?: return
        val params = layoutParams ?: return
        val frame = lastFrame
        val window = lastWindow
        when {
            !shown -> {
                setLayout(manager, view, params, flags = OverlayPolicy.WINDOW_FLAGS and outsideFlag.inv() or untouchable,
                    width = 1, height = 1, gravity = Gravity.TOP or Gravity.START, x = 0, y = 0)
                hideTouchWindow(manager)
            }
            frame != null && window != null -> {
                // Camera on top: a fixed-size drawing window, and a small touch window that follows the island.
                setLayout(manager, view, params, flags = OverlayPolicy.WINDOW_FLAGS and outsideFlag.inv() or untouchable,
                    width = window.width.roundToInt(), height = window.height.roundToInt(),
                    gravity = Gravity.TOP or Gravity.START, x = window.left.roundToInt(), y = window.top.roundToInt())
                val pad = 6f * context.resources.displayMetrics.density
                showTouchWindow(manager,
                    left = (frame.left - pad).roundToInt(), top = (frame.top - pad).roundToInt(),
                    width = (frame.width + 2f * pad).roundToInt(), height = (frame.height + 2f * pad).roundToInt())
            }
            frame != null -> {
                // Camera on a side edge: one window sized to the island, as the island moves it.
                setLayout(manager, view, params, flags = OverlayPolicy.WINDOW_FLAGS,
                    width = WindowManager.LayoutParams.WRAP_CONTENT, height = WindowManager.LayoutParams.WRAP_CONTENT,
                    gravity = Gravity.TOP or Gravity.START, x = frame.left.roundToInt(), y = frame.top.roundToInt())
                hideTouchWindow(manager)
            }
        }
    }

    private fun setLayout(manager: WindowManager, view: View, params: WindowManager.LayoutParams, flags: Int, width: Int,
        height: Int, gravity: Int, x: Int, y: Int) {
        if (params.flags == flags && params.width == width && params.height == height && params.gravity == gravity &&
            params.x == x && params.y == y) return
        params.flags = flags; params.width = width; params.height = height; params.gravity = gravity; params.x = x; params.y = y
        runCatching { manager.updateViewLayout(view, params) }
    }

    private fun showTouchWindow(manager: WindowManager, left: Int, top: Int, width: Int, height: Int) {
        val existing = touchView
        val flags = OverlayPolicy.WINDOW_FLAGS
        if (existing == null) {
            val v = TouchProxy(context, forward = ::forwardTouch, onOutside = ::collapseIfOpen)
            val p = WindowManager.LayoutParams(width, height, windowType, flags, PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.START
                x = left; y = top
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            if (runCatching { manager.addView(v, p) }.isSuccess) { touchView = v; touchParams = p }
        } else {
            val p = touchParams ?: return
            setLayout(manager, existing, p, flags, width, height, Gravity.TOP or Gravity.START, left, top)
        }
    }

    /** Takes the touch window out of the way: 1x1 and untouchable, so it can never catch anything. */
    private fun hideTouchWindow(manager: WindowManager) {
        val v = touchView ?: return
        val p = touchParams ?: return
        setLayout(manager, v, p, OverlayPolicy.WINDOW_FLAGS and outsideFlag.inv() or untouchable, 1, 1,
            Gravity.TOP or Gravity.START, 0, 0)
    }

    /** Passes a touch the touch window received to the drawing window's view, shifted into that view's coordinates. */
    private fun forwardTouch(event: MotionEvent) {
        val draw = composeView ?: return
        val pd = layoutParams ?: return
        val pt = touchParams ?: return
        val copy = MotionEvent.obtain(event)
        copy.offsetLocation((pt.x - pd.x).toFloat(), (pt.y - pd.y).toFloat())
        draw.dispatchTouchEvent(copy)
        copy.recycle()
    }

    fun stop() {
        started = false
        val view = composeView
        composeView = null
        layoutParams = null
        val touch = touchView
        touchView = null
        touchParams = null
        lastFrame = null; lastWindow = null
        if (touch != null) runCatching { windowManager?.removeView(touch) }
        if (view != null) runCatching { windowManager?.removeView(view) }
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        IslandRuntime.updateOverlay(false)
    }
}

/** A transparent window that only takes touches and hands them on. It draws nothing, so resizing it is invisible. */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
private class TouchProxy(context: Context, private val forward: (MotionEvent) -> Unit, private val onOutside: () -> Unit) :
    View(context) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) { onOutside(); return false }
        return super.dispatchTouchEvent(event)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean { forward(event); return true }
}
