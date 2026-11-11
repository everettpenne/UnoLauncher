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
object AssistOverlay : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    private var view: ComposeView? = null
    private var manager: WindowManager? = null

    val isShowing: Boolean get() = view != null

    /** Shows search over the current app, with [screenText] as a fact for the assistant's answer. */
    fun show(context: Context, screenText: String?) {
        val app = context.applicationContext as? Application ?: return
        if (view != null) return
        manager = app.getSystemService(WindowManager::class.java)
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        val screen = ComposeView(app).apply {
            setViewTreeLifecycleOwner(this@AssistOverlay)
            setViewTreeSavedStateRegistryOwner(this@AssistOverlay)
            setViewTreeViewModelStoreOwner(this@AssistOverlay)
            // An overlay window sits under the status bar; pad by it rather than asking for insets, which report the whole screen here.
            setPadding(0, statusBarHeight(app), 0, 0)
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
                        glass = null,
                        apps = state.apps,
                        hiddenApps = state.hiddenApps,
                        feedEntries = feedState.shownEntries,
                        contactsEnabled = extrasState.contactSearch,
                        handoff = rememberHandoffResolver(extrasState),
                        onLaunch = { entry: AppEntry -> openApp(app, entry); hide() },
                        onOpenFeedLink = { link -> openLink(app, link); hide() },
                        onOpenContact = { contact -> ContactsSearch.open(app, contact); hide() },
                        onDismiss = { hide() },
                        visible = true,
                        // A tap focuses the field; asking for focus as the window appears makes the panel's scroll responder answer a
                        // bring-into-view before it is placed, which throws and kills the launcher.
                        autoFocus = false,
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
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
            title = "Uno search"
        }
        runCatching { manager?.addView(screen, params) ?: return }.onFailure { return }
        view = screen
    }

    /** Takes the window away and lets the app behind carry on exactly as it was. */
    fun hide() {
        val screen = view ?: return
        view = null
        runCatching { manager?.removeView(screen) }
        runCatching { screen.disposeComposition() }
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

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
