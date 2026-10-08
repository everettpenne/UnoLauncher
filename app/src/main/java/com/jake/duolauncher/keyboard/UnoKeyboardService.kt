package com.jake.duolauncher.keyboard

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import com.jake.duolauncher.Cue
import com.jake.duolauncher.UnoFeedback

/** Uno Keyboard: a liquid-glass-styled on-screen keyboard.
 *
 * It is strictly local. Nothing typed is stored, logged, learned or sent anywhere: there is no dictionary, no
 * prediction and no network code in this package (enforced by a test). Everything goes straight to the app you are
 * typing in through [InputConnection]. Because it has no suggestions or autocorrect, it types exactly what you press.
 */
class UnoKeyboardService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    internal val ui = KeyboardUiState()
    private var inputType = 0
    private var lastShiftTapAt = 0L
    private var composeView: ComposeView? = null

    override fun onCreate() {
        super.onCreate()
        savedState.performAttach(); savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
        val prefs = getSharedPreferences("extras", Context.MODE_PRIVATE)
        UnoFeedback.configure(this, prefs.getBoolean("haptics", true), prefs.getBoolean("sounds", false))
    }

    override fun onCreateInputView(): View {
        // A Compose view needs lifecycle owners on its window; an input method has none, so this service is the owner.
        window.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this); decor.setViewTreeSavedStateRegistryOwner(this); decor.setViewTreeViewModelStoreOwner(this)
        }
        registry.currentState = Lifecycle.State.RESUMED
        return ComposeView(this).also { view ->
            view.setViewTreeLifecycleOwner(this); view.setViewTreeSavedStateRegistryOwner(this); view.setViewTreeViewModelStoreOwner(this)
            view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            view.setContent { KeyboardScreen(ui, actions) }
            composeView = view
        }
    }

    /** Shown unless a physical keyboard is attached and Android's "Show on-screen keyboard" setting for it is off. */
    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown() // keeps the base class's bookkeeping; its answer is replaced by the rule below
        val config = resources.configuration
        if (config.keyboard == android.content.res.Configuration.KEYBOARD_NOKEYS ||
            config.hardKeyboardHidden == android.content.res.Configuration.HARDKEYBOARDHIDDEN_YES) return true
        return android.provider.Settings.Secure.getInt(contentResolver, "show_ime_with_hard_keyboard", 0) != 0
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        inputType = info.inputType
        ui.enter = KeyboardModel.enterKind(info.imeOptions)
        ui.password = KeyboardModel.isPassword(info.inputType)
        ui.page = KeyboardModel.startPage(info.inputType)
        ui.shift = KeyboardModel.shiftFor(capsMode(), ShiftState.OFF)
        ui.accent = readAccent()
    }

    private fun capsMode(): Int = currentInputConnection?.getCursorCapsMode(inputType) ?: 0

    private fun refreshShift() { ui.shift = KeyboardModel.shiftFor(capsMode(), ui.shift) }

    private fun readAccent(): Int? =
        if (getSharedPreferences("appearance", Context.MODE_PRIVATE).getBoolean("wallpaperColor", false))
            runCatching { com.jake.duolauncher.WallpaperAccents.compute(this, ui.dark(this)).accent.toArgb() }.getOrNull() else null

    internal val actions = object : KeyboardActions {
        override fun type(text: String) {
            currentInputConnection?.commitText(text, 1)
            ui.shift = KeyboardModel.afterLetter(ui.shift)
            if (text.firstOrNull()?.isLetter() != true) refreshShift()
        }
        override fun backspace() {
            val ic = currentInputConnection ?: return
            if (!ic.getSelectedText(0).isNullOrEmpty()) ic.commitText("", 1) else ic.deleteSurroundingText(1, 0)
            refreshShift()
        }
        override fun space() {
            val ic = currentInputConnection ?: return
            val change = KeyboardModel.doubleSpace(ic.getTextBeforeCursor(2, 0) ?: "")
            if (change != null) { ic.deleteSurroundingText(change.first + 1, 0); ic.commitText(change.second, 1) } else ic.commitText(" ", 1)
            refreshShift()
        }
        override fun enter() {
            val ic = currentInputConnection ?: return
            if (ui.enter.sendsAction) ic.performEditorAction(currentInputEditorInfo.imeOptions and 0xff)
            else ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)).also { ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)) }
            refreshShift()
        }
        override fun globe() {
            val switched = if (Build.VERSION.SDK_INT >= 28) switchToNextInputMethod(false) else false
            if (!switched) getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }
        override fun shift() {
            val now = System.currentTimeMillis()
            ui.shift = KeyboardModel.tapShift(ui.shift, doubleTap = now - lastShiftTapAt < 350L)
            lastShiftTapAt = now
        }
        override fun page(target: KeyPage) { ui.page = target }
        override fun tick(view: View) = UnoFeedback.play(Cue.KEY, view)
    }

    override fun onDestroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onDestroy()
    }
}

/** What the keys can do. The service implements it; the UI only calls it. */
internal interface KeyboardActions {
    fun type(text: String); fun backspace(); fun space(); fun enter(); fun globe(); fun shift(); fun page(target: KeyPage); fun tick(view: View)
}

/** The keyboard's observable state. */
internal class KeyboardUiState {
    var page by mutableStateOf(KeyPage.LETTERS)
    var shift by mutableStateOf(ShiftState.OFF)
    var enter by mutableStateOf(EnterKind.RETURN)
    var password by mutableStateOf(false)
    /** The wallpaper accent as ARGB, when "Color from wallpaper" is on. */
    var accent by mutableStateOf<Int?>(null)

    fun dark(context: Context) = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
}
