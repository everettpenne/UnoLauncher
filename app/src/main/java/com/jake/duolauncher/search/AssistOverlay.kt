package com.jake.duolauncher.search

import android.app.Application
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
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
import com.jake.duolauncher.AppEntry
import com.jake.duolauncher.DuoTheme
import com.jake.duolauncher.ExtrasStore
import com.jake.duolauncher.FocusMode
import com.jake.duolauncher.LauncherModels
import com.jake.duolauncher.appearance.UnoMotion
import com.jake.duolauncher.island.IslandRuntime
import com.jake.duolauncher.appearance.AppearanceStore
import com.jake.duolauncher.appearance.DuoAppearanceRuntime
import com.jake.duolauncher.appearance.LocalWallpaperAccent
import com.jake.duolauncher.appearance.LocalWallpaperLuma
import com.jake.duolauncher.appearance.rememberWallpaperAccent
import com.jake.duolauncher.appearance.rememberWallpaperLuma
import com.jake.duolauncher.feed.FeedStore
import com.jake.duolauncher.feed.LocalFeedFollow
import com.jake.duolauncher.search.context.ContextEnv
import com.jake.duolauncher.widgets.UnoWidgets

/**
 * Uno's search, drawn as an overlay window over whatever app is in front — the way the island is drawn, and by the same machinery.
 *
 * The island's own windows are deliberately never focusable and never full-screen (a tap anywhere must reach the app below), so this is a
 * third window, made for the occasion: full screen, focusable so it can take the keyboard, and transparent around the panel. It is owned by
 * the application, not by an activity, so the activity that starts it can finish immediately and there is no second window to inset.
 *
 * The wallpaper's ink and accent are supplied here exactly as Home supplies them: without them the panel draws with nothing to draw in,
 * which is precisely how it once came up blank.
 */
object AssistOverlay {
    /** What a window's composition needs from an owner, made new for each opening: a lifecycle can't come back from DESTROYED and a saved
     *  state registry can be restored only once, so one shared owner crashed the second time search was opened. */
    private class Owner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
        private val savedState = SavedStateRegistryController.create(this)
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
        override val viewModelStore: ViewModelStore = ViewModelStore()
        fun start() { savedState.performRestore(null); registry.currentState = Lifecycle.State.RESUMED }
        fun stop() { registry.currentState = Lifecycle.State.DESTROYED; viewModelStore.clear() }
    }

    /** The window's root. There is no activity, so no back dispatcher: Back (once the keyboard has taken its own) closes search and leaves
     *  the app below as it was. Caught here, above the field, which would otherwise have it while it is focused. */
    private class Root(context: Context, val onBack: () -> Unit) : android.widget.FrameLayout(context) {
        override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
            if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                if (event.action == android.view.KeyEvent.ACTION_UP && !event.isCanceled) onBack()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    private var view: android.view.View? = null
    private var owner: Owner? = null
    private var composeView: ComposeView? = null
    private var screenOff: android.content.BroadcastReceiver? = null
    private var receiverContext: Context? = null
    private var manager: WindowManager? = null

    val isShowing: Boolean get() = view != null

    /** Shows search over the current app, with [screenText] as a fact for the assistant's answer. */
    /** Whether search can be put over the app in front: through the accessibility service's window, or with "display over other apps". */
    fun canShow(context: Context): Boolean =
        com.jake.duolauncher.SystemShadeAccessibilityService.connected() != null || android.provider.Settings.canDrawOverlays(context)

    private var shot: android.graphics.Bitmap? = null
    private var opening = false

    /**
     * Shows search over the current app, with [screenText] as a fact for the assistant's answer. With the accessibility service connected,
     * it first takes one screenshot for the glass to bend (a window can't sample another app's pixels), then opens in the service's window,
     * above the status bar as the expanded island is. Without one, it opens as a plain overlay with the system's live blur behind it.
     */
    fun show(context: Context, screenText: String?) {
        val app = context.applicationContext as? Application ?: return
        // A window the system took away (its service was stopped) is closed, whatever was last recorded.
        if (view?.isAttachedToWindow == false) remove()
        // Opened again while the last one is still closing: finish that close at once.
        if (closing) remove()
        if (view != null || opening) return
        // Never over the lock screen (AssistActivity asks for the unlock first; this is the backstop): the service's window would draw above it.
        if (app.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true) return
        val service = com.jake.duolauncher.SystemShadeAccessibilityService.connected()
        if (service == null) { open(app, null, screenText, null); return }
        opening = true
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        var settled = false
        fun settle(picture: android.graphics.Bitmap?) {
            if (settled) { picture?.recycle(); return }
            settled = true; opening = false
            open(app, service, screenText, picture)
        }
        // Never hold search up for a slow screenshot: after this long it opens with the live blur instead.
        main.postDelayed({ settle(null) }, SHOT_TIMEOUT_MS)
        runCatching {
            service.takeScreenshot(android.view.Display.DEFAULT_DISPLAY, service.mainExecutor,
                object : android.accessibilityservice.AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: android.accessibilityservice.AccessibilityService.ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        val picture = runCatching { android.graphics.Bitmap.wrapHardwareBuffer(buffer, result.colorSpace) }.getOrNull()
                        buffer.close()
                        settle(picture)
                    }
                    override fun onFailure(errorCode: Int) = settle(null)
                })
        }.onFailure { settle(null) }
    }

    private const val SHOT_TIMEOUT_MS = 400L

    private fun open(app: Application, service: android.accessibilityservice.AccessibilityService?, screenText: String?, picture: android.graphics.Bitmap?) {
        if (view != null) { picture?.recycle(); return }
        if (service == null && !android.provider.Settings.canDrawOverlays(app)) { picture?.recycle(); return }
        // The service's own window manager: an accessibility overlay can only be added through it.
        val host: Context = service ?: app
        manager = host.getSystemService(WindowManager::class.java)
        shot = picture
        val windowOwner = Owner().also { it.start(); owner = it }
        shown.value = true
        val screen = ComposeView(host).apply {
            setViewTreeLifecycleOwner(windowOwner)
            setViewTreeSavedStateRegistryOwner(windowOwner)
            setViewTreeViewModelStoreOwner(windowOwner)

        }
        screen.setContent {
            val model = remember { LauncherModels.of(app) }
            val extras = remember { ExtrasStore(app) }
            val feeds = remember { FeedStore(app, kotlinx.coroutines.MainScope()) }
            val appearance = remember { AppearanceStore(app) }
            val state = model.state.collectAsState().value
            val extrasState = extras.state
            val feedState = feeds.state.collectAsState().value
            val assistantOn = extrasState.localAssistant
            val assistant = remember(assistantOn) {
                if (assistantOn && LocalAssistant.isAvailable(app)) LocalAssistant(app) else null
            }
            // The glass: the screenshot when there is one; over Home without one, Home's own wallpaper picture, as the island uses; otherwise
            // none, and the window's live blur frosts the app instead.
            val glass = remember(picture) {
                val settings = com.jake.duolauncher.appearance.SharedWallpaperRuntime.settings
                val wallpaper = com.jake.duolauncher.appearance.SharedWallpaperRuntime.current
                when {
                    picture != null -> com.jake.duolauncher.appearance.PageGlass(ScreenBackdrop(picture), androidx.compose.ui.graphics.Color.Black, settings)
                    IslandRuntime.launcherInFront && wallpaper?.image != null ->
                        com.jake.duolauncher.appearance.PageGlass(com.jake.duolauncher.appearance.SharedWallpaperBackdrop(wallpaper), androidx.compose.ui.graphics.Color.Black, settings)
                    else -> null
                }
            }
            val grow = remember { androidx.compose.animation.core.Animatable(0f) }
            val open = shown.value
            androidx.compose.runtime.LaunchedEffect(open) {
                if (open) grow.animateTo(1f, androidx.compose.animation.core.spring(UnoMotion.OPEN_DAMPING, SpotlightEmerge.outlineOpenStiffness()))
                else {
                    // Back into the pill on the close spring, from wherever the opening had got to; then the window goes.
                    grow.animateTo(0f, SpotlightEmerge.closeSpring(.001f))
                    // Not from inside the composition being disposed: on the next message.
                    android.os.Handler(android.os.Looper.getMainLooper()).post { remove() }
                }
            }
            val contextEnv = remember(assistant, screenText) {
                assistant?.let {
                    ContextEnv(app, { state }, { extrasState }, { appearance.state }, { feedState.shownEntries }, { screenText })
                }
            }
            CompositionLocalProvider(
                LocalFeedFollow provides feeds::addFeed,
                LocalWallpaperLuma provides rememberWallpaperLuma(appearance.state.dark),
                LocalWallpaperAccent provides rememberWallpaperAccent(appearance.state.wallpaperColor, appearance.state.dark),
            ) {
                DuoTheme(DuoAppearanceRuntime.dark) {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                    Spotlight(
                        glass = glass,
                        apps = state.apps,
                        hiddenApps = state.hiddenApps,
                        feedEntries = feedState.shownEntries,
                        contactsEnabled = extrasState.contactSearch,
                        handoff = rememberHandoffResolver(extrasState),
                        onLaunch = { entry: AppEntry -> openApp(app, entry); hide() },
                        onOpenFeedLink = { link -> openLink(app, link); hide() },
                        onOpenContact = { contact -> ContactsSearch.open(app, contact); hide() },
                        onDismiss = { hide() },
                        visible = shown.value,
                        // Grows out of the island when there is one, as on Home (the island's frame is the process's, so it is there when
                        // Home has drawn it); otherwise the panel hangs from the top.
                        pill = remember(appearance.state.island) {
                            if (!appearance.state.island) null else IslandRuntime.state.restPill ?: restPill(app, appearance.state.islandScale)
                        },
                        // No glass can sample the app below, so the bar thins from black into the page instead, as Home's does into its glass.
                        seeThrough = glass == null,
                        progress = { grow.value },
                        noteSlot = state.widgetPlacements.firstOrNull { it.id == UnoWidgets.NOTE }?.slot,
                        focusOn = { extras.state.focusOn },
                        onToggleFocus = {
                            val next = !extras.state.focusOn
                            extras.setFocusOn(next)
                            FocusMode.applyRinger(app, next, extras.state.focusVibrate)
                        },
                        assistant = assistant,
                        contextEnv = contextEnv,
                    )
                    }
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            // Above the status bar with the service, as the expanded island is; a plain overlay sits beneath it.
            if (service != null) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Focusable (no FLAG_NOT_FOCUSABLE) so it can take the keyboard, and not modal so nothing is stolen behind it.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            // The keyboard overlays the panel rather than resizing a window it cannot resize: the panel hangs from the top.
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            // No screenshot to bend: the system's live blur frosts the app behind instead.
            if (picture == null) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = (app.resources.displayMetrics.density * 24).toInt()
            }
            title = "Uno search"
        }
        val root = Root(host) { hide() }.apply {
            setViewTreeLifecycleOwner(windowOwner); setViewTreeSavedStateRegistryOwner(windowOwner); setViewTreeViewModelStoreOwner(windowOwner)
            addView(screen, android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
        }
        runCatching { manager?.addView(root, params) ?: return }.onFailure { windowOwner.stop(); owner = null; return }
        view = root
        composeView = screen
        // The screen going off locks the phone: search closes with it, so it is never left open above the lock screen.
        screenOff = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: android.content.Intent?) = hide(animated = false)
        }.also { runCatching { app.registerReceiver(it, android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF)) } }
        receiverContext = app
        // As on Home, the status bar steps aside while search is open, so the bar's camera band is the island's own black.
        runCatching { root.windowInsetsController?.hide(android.view.WindowInsets.Type.statusBars()) }
    }

    /** False once search is closing: the panel springs back into the island pill, then the window goes (see [hide]). */
    private val shown = androidx.compose.runtime.mutableStateOf(true)
    private var closing = false

    /**
     * Closes search: the panel springs back into the island pill as Home's does, taking no more touches meanwhile (a tap already reaches the
     * app below), and the window goes once it is there. [animated] false takes it away at once (the screen went off, the service stopped).
     */
    fun hide(animated: Boolean = true) {
        if (view == null) return
        val animate = animated && android.provider.Settings.Global.getFloat(
            (receiverContext ?: return remove()).contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        if (!animate) { remove(); return }
        if (closing) return
        closing = true
        view?.let { v ->
            val params = v.layoutParams as? WindowManager.LayoutParams
            if (params != null) runCatching {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                manager?.updateViewLayout(v, params)
            }
        }
        shown.value = false
    }

    /** Takes the window away and lets the app behind carry on exactly as it was. */
    private fun remove() {
        closing = false
        val screen = view ?: return
        view = null
        runCatching { manager?.removeView(screen) }
        runCatching { composeView?.disposeComposition() }
        composeView = null
        owner?.stop(); owner = null
        screenOff?.let { r -> runCatching { receiverContext?.unregisterReceiver(r) } }
        screenOff = null; receiverContext = null
        shot?.recycle(); shot = null
    }

    /** The island's rest pill, worked out from the display as the island's own overlay does, for when Home has not drawn the island in this
     *  process (Home publishes the frame it draws; that is preferred, since it includes the person's adjustments). Overlay windows get no
     *  reliable cutout insets, so the camera comes from the display itself. */
    private fun restPill(context: Context, scale: Float): com.jake.duolauncher.island.IslandFrame? = runCatching {
        val display = context.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val metrics = android.util.DisplayMetrics().also { @Suppress("DEPRECATION") display.getRealMetrics(it) }
        val env = com.jake.duolauncher.island.IslandEnvironment(
            cutout = com.jake.duolauncher.island.readDisplayCutout(display, metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat()),
            screenWidth = metrics.widthPixels.toFloat(), statusBarHeight = statusBarHeight(context).toFloat(), screenHeight = metrics.heightPixels.toFloat())
        com.jake.duolauncher.island.IslandGeometry.frame(env, metrics.density, 0f, scale)
    }.getOrNull()

    private fun statusBarHeight(context: Context): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) runCatching { context.resources.getDimensionPixelSize(id) }.getOrDefault(0) else 0
    }

    private fun openApp(context: Context, entry: AppEntry) {
        runCatching {
            val user = context.getSystemService(android.os.UserManager::class.java).getUserForSerialNumber(entry.userSerial)
                ?: throw IllegalStateException("Profile is unavailable")
            context.getSystemService(android.content.pm.LauncherApps::class.java).startMainActivity(entry.component, user, null, null)
        }
    }

    private fun openLink(context: Context, link: String) {
        runCatching {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
