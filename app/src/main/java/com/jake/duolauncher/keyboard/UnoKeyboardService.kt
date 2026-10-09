package com.jake.duolauncher.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

    // Spelling help. The word list is read from the app's own assets on a background thread; until it is ready there
    // are simply no suggestions. Nothing here is saved: the sets below live only as long as this process.
    @Volatile private var engine: WordEngine? = null
    private val ignored = mutableSetOf<String>()
    private var lastFix: Pair<String, String>? = null
    private val mirror = TextMirror()
    private val expectedCursor = ExpectedCursor()
    private var cursor = 0
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    // Suggestions are computed off the main thread so a keystroke never waits for a dictionary scan; only the newest request is shown.
    private val suggestWorker = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "uno-keyboard-suggest").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } }
    private val suggestSeq = java.util.concurrent.atomic.AtomicInteger()
    private var autocorrectOn = true
    private var suggestionsOn = true
    private var allowSuggestions = false
    private var allowAutocorrect = false
    private val recents = EmojiRecents()
    /** True while text is selected, known from the selection updates, so a backspace need not ask the app. */
    private var hasSelection = false
    /** The correction for the word being typed, worked out on the worker with the suggestions so the space bar can use it at once. */
    @Volatile private var fixCache: Pair<String, String?>? = null
    private var clipboard: ClipboardManager? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { updatePasteAvailable() }

    override fun onCreate() {
        super.onCreate()
        savedState.performAttach(); savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
        val prefs = getSharedPreferences("extras", Context.MODE_PRIVATE)
        clipboard = getSystemService(ClipboardManager::class.java)
        runCatching { clipboard?.addPrimaryClipChangedListener(clipListener) }
        UnoFeedback.configure(this, prefs.getBoolean("haptics", true), prefs.getBoolean("sounds", false))
        KeyHaptics.configure(this, if (prefs.getBoolean("haptics", true)) prefs.getFloat("kbHapticStrength", HapticProfile.DEFAULT_STRENGTH) else 0f)
        Thread {
            // Loading the word lists is background work: it must never take the processor from the keyboard's first draw.
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            runCatching {
                ui.emoji = assets.open("keyboard/emoji.txt").bufferedReader().useLines { EmojiData.parse(it) }
            }
            runCatching {
                val words = assets.open("keyboard/en_words.txt").bufferedReader().useLines { WordEngine.parseWords(it) }
                val next = assets.open("keyboard/en_next.txt").bufferedReader().useLines { WordEngine.parseFollowers(it) }
                engine = WordEngine(words, next)
                composeView?.post { refreshSuggestions() }
            }
        }.start()
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

    /** Landscape never switches to the fullscreen editor: the keyboard stays a keyboard and the app stays visible above it. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    /** Whether the clipboard holds text, from its description alone: the text itself is read only when Paste is tapped. */
    private fun updatePasteAvailable() {
        val cm = clipboard ?: return
        ui.pasteAvailable = runCatching { cm.hasPrimaryClip() && cm.primaryClipDescription?.hasMimeType("text/*") == true }.getOrDefault(false)
    }

    /** Shown unless a physical keyboard is attached and Android's "Show on-screen keyboard" setting for it is off. */
    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown() // keeps the base class's bookkeeping; its answer is replaced by the rule below
        val config = resources.configuration
        if (config.keyboard == android.content.res.Configuration.KEYBOARD_NOKEYS ||
            config.hardKeyboardHidden == android.content.res.Configuration.HARDKEYBOARDHIDDEN_YES) return true
        return runCatching { android.provider.Settings.Secure.getInt(contentResolver, "show_ime_with_hard_keyboard", 0) != 0 }
            .getOrDefault(false)
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        inputType = info.inputType
        ui.enter = KeyboardModel.enterKind(info.imeOptions)
        ui.password = KeyboardModel.isPassword(info.inputType)
        ui.page = KeyboardModel.startPage(info.inputType)
        ui.accent = readAccent()
        val prefs = getSharedPreferences("extras", Context.MODE_PRIVATE)
        autocorrectOn = prefs.getBoolean("kbAutocorrect", true)
        suggestionsOn = prefs.getBoolean("kbSuggestions", true)
        ui.numberRow = prefs.getBoolean("kbNumberRow", false)
        ui.oneHanded = prefs.getInt("kbOneHanded", 0).coerceIn(0, 2)
        // The switcher key is only worth having when Android has another keyboard to go to.
        ui.globe = if (Build.VERSION.SDK_INT >= 28) runCatching { shouldOfferSwitchingToNextInputMethod() }.getOrDefault(true) else true
        hasSelection = info.initialSelStart != info.initialSelEnd
        updatePasteAvailable()
        ui.spaceCursor = prefs.getBoolean("kbSpaceCursor", true)
        allowSuggestions = KeyboardModel.allowsSuggestions(info.inputType)
        allowAutocorrect = KeyboardModel.allowsAutocorrect(info.inputType)
        ui.showStrip = suggestionsOn && allowSuggestions
        ignored.clear(); lastFix = null; fixCache = null
        KeyHaptics.configure(this, if (prefs.getBoolean("haptics", true)) prefs.getFloat("kbHapticStrength", HapticProfile.DEFAULT_STRENGTH) else 0f)
        // One read of the field now; after this the mirror follows our own edits and only external changes are re-read.
        expectedCursor.clear(); cursor = info.initialSelStart
        if (info.initialSelStart == info.initialSelEnd) resyncMirror() else mirror.invalidate()
        ui.shift = KeyboardModel.shiftFor(if (capsNow()) 1 else 0, ShiftState.OFF)
        refreshSuggestions()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candStart: Int, candEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candStart, candEnd)
        hasSelection = newSelStart != newSelEnd
        if (newSelStart == newSelEnd && expectedCursor.isOurs(newSelStart)) return   // the echo of something we typed
        // The cursor moved or the text changed from outside (a tap in the field, a paste, the app editing it).
        cursor = newSelStart
        if (newSelStart == newSelEnd) resyncMirror() else mirror.invalidate()
        ui.shift = KeyboardModel.shiftFor(if (capsNow()) 1 else 0, ui.shift)
        refreshSuggestions()
    }

    private fun readAccent(): Int? =
        if (getSharedPreferences("appearance", Context.MODE_PRIVATE).getBoolean("wallpaperColor", false))
            runCatching { com.jake.duolauncher.WallpaperAccents.compute(this, ui.dark(this)).accent.toArgb() }.getOrNull() else null

    private fun resyncMirror() {
        val before = currentInputConnection?.getTextBeforeCursor(80, 0)
        if (before == null) mirror.invalidate() else mirror.reset(before)
    }

    /** The text before the cursor, from memory when possible. */
    private fun textBefore(): String =
        if (mirror.valid) mirror.get() else (currentInputConnection?.getTextBeforeCursor(80, 0)?.toString() ?: "")

    private fun capsNow(): Boolean {
        if (inputType and 0x4000 == 0 && inputType and 0x2000 == 0 && inputType and 0x1000 == 0) return false
        return if (mirror.valid) KeyboardModel.capsFromText(mirror.get(), inputType)
        else (currentInputConnection?.getCursorCapsMode(inputType) ?: 0) != 0
    }

    private fun editedText(typed: String, removed: Int = 0) {
        if (removed > 0) { mirror.delete(removed); cursor -= removed }
        mirror.commit(typed); cursor += typed.length; expectedCursor.expect(cursor)
    }

    private fun refreshSuggestions() {
        val e = engine
        if (!ui.showStrip || e == null) { ui.suggestions = emptyList(); return }
        val before = textBefore(); val shift = ui.shift != ShiftState.OFF; val skip = ignored.toSet()
        val seq = suggestSeq.incrementAndGet()
        suggestWorker.execute {
            val ctx = Suggest.parse(before)
            // The correction is worked out here, once, and kept for the space bar to use without scanning the dictionary itself.
            val fix = if (ctx.current.isEmpty()) null else Suggest.autocorrect(e, ctx, skip)
            fixCache = ctx.current to fix
            val result = Suggest.suggestions(e, ctx, skip, capitalise = shift || (ctx.atSentenceStart && ctx.current.isEmpty()), fix = fix)
            main.post { if (seq == suggestSeq.get()) ui.suggestions = result }
        }
    }

    /** Replaces the word before the cursor with its correction, if it has one. Returns whether it did. */
    private fun applyAutocorrect(): Boolean {
        val e = engine ?: return false
        if (!autocorrectOn || !allowAutocorrect) return false
        val ic = currentInputConnection ?: return false
        val ctx = Suggest.parse(textBefore())
        if (ctx.current.isEmpty()) return false
        // Use what the worker already found for this word; only if it has not caught up is the dictionary scanned here.
        val cached = fixCache
        val fix = (if (cached != null && cached.first == ctx.current) cached.second else Suggest.autocorrect(e, ctx, ignored)) ?: return false
        ic.deleteSurroundingText(ctx.current.length, 0)
        ic.commitText(fix, 1)
        editedText(fix, removed = ctx.current.length)
        lastFix = ctx.current to fix
        return true
    }

    private fun refreshShift() { ui.shift = KeyboardModel.shiftFor(if (capsNow()) 1 else 0, ui.shift) }

    internal val actions = object : KeyboardActions {
        override fun type(text: String) {
            val ic = currentInputConnection ?: return
            // A full stop, comma or bracket straight after a word finishes it, so it is corrected first.
            if (text.length == 1 && text[0] in ".,!?;:)") applyAutocorrect() else lastFix = null
            ic.commitText(text, 1)
            editedText(text)
            ui.shift = KeyboardModel.afterLetter(ui.shift)
            if (text.firstOrNull()?.isLetter() != true) refreshShift()
            refreshSuggestions()
        }
        override fun replaceLast(text: String) {
            val ic = currentInputConnection ?: return
            ic.deleteSurroundingText(1, 0); ic.commitText(text, 1)
            editedText(text, removed = 1); lastFix = null; refreshSuggestions()
        }
        override fun backspace(held: Int) {
            val ic = currentInputConnection ?: return
            val fix = lastFix
            if (fix != null && held == 0) {
                // Backspace straight after an autocorrection undoes it, and that word is left alone from then on.
                val before = textBefore()
                val spaced = before.endsWith(fix.second + " ")
                if (spaced || before.endsWith(fix.second)) {
                    val n = fix.second.length + if (spaced) 1 else 0
                    ic.deleteSurroundingText(n, 0); ic.commitText(fix.first, 1)
                    editedText(fix.first, removed = n)
                    ignored += fix.first.lowercase(); lastFix = null; fixCache = null
                    refreshShift(); refreshSuggestions(); return
                }
            }
            lastFix = null
            if (hasSelection) { ic.commitText("", 1); hasSelection = false; mirror.invalidate(); resyncMirror(); cursor = -1 }
            else {
                // One character at a time (a whole emoji or flag counts as one), then whole words once held a while. The text is
                // needed to know how many units that is, so when the mirror has lost it a single unit is deleted without asking.
                val n = if (mirror.valid || held >= KeyboardModel.WORD_DELETE_AFTER) KeyboardModel.deleteStep(textBefore(), held).coerceAtLeast(1) else 1
                ic.deleteSurroundingText(n, 0); mirror.delete(n); cursor -= n; expectedCursor.expect(cursor)
            }
            refreshShift(); refreshSuggestions()
        }
        override fun space() {
            val ic = currentInputConnection ?: return
            val corrected = applyAutocorrect()
            val change = KeyboardModel.doubleSpace(textBefore())
            if (change != null && !corrected) { ic.deleteSurroundingText(change.first + 1, 0); ic.commitText(change.second, 1); editedText(change.second, removed = change.first + 1); lastFix = null }
            else { ic.commitText(" ", 1); editedText(" ") }
            refreshShift(); refreshSuggestions()
        }
        override fun enter() {
            val ic = currentInputConnection ?: return
            applyAutocorrect(); lastFix = null
            if (ui.enter.sendsAction) ic.performEditorAction(currentInputEditorInfo.imeOptions and 0xff)
            else ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)).also { ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)) }
            // What the app does with Return varies (a newline, an action, nothing), so re-read the field once.
            mirror.invalidate(); expectedCursor.clear()
            main.post { resyncMirror(); refreshShift(); refreshSuggestions() }
        }
        override fun pick(suggestion: Suggestion) {
            val ic = currentInputConnection ?: return
            val ctx = Suggest.parse(textBefore())
            when (suggestion.kind) {
                SuggestionKind.NEXT -> { ic.commitText(suggestion.text + " ", 1); editedText(suggestion.text + " ") }
                SuggestionKind.TYPED -> { ignored += ctx.current.lowercase(); fixCache = null; ic.commitText(" ", 1); editedText(" ") }
                SuggestionKind.CORRECTION, SuggestionKind.COMPLETION -> {
                    ic.deleteSurroundingText(ctx.current.length, 0); ic.commitText(suggestion.text + " ", 1)
                    editedText(suggestion.text + " ", removed = ctx.current.length)
                }
            }
            lastFix = null; refreshShift(); refreshSuggestions()
        }
        override fun moveCursor(delta: Int) {
            val ic = currentInputConnection ?: return
            val key = if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
            repeat(kotlin.math.abs(delta)) {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key)); ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
            }
            mirror.invalidate()   // the cursor is somewhere new; the selection update re-reads the text around it
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
        override fun emoji(text: String) {
            val ic = currentInputConnection ?: return
            lastFix = null
            ic.commitText(text, 1); editedText(text)
            recents.add(text); ui.recents = recents.list()
            refreshSuggestions()
        }
        override fun paste() {
            val ic = currentInputConnection ?: return
            // The clipboard is read here, on the tap, and nowhere else.
            val text = runCatching { clipboard?.primaryClip?.getItemAt(0)?.coerceToText(this@UnoKeyboardService)?.toString() }.getOrNull()
            if (text.isNullOrEmpty()) return
            lastFix = null
            ic.commitText(text, 1); mirror.invalidate(); expectedCursor.clear()
            main.post { resyncMirror(); refreshShift(); refreshSuggestions() }
        }
        override fun setOneHanded(mode: Int) {
            ui.oneHanded = mode
            getSharedPreferences("extras", Context.MODE_PRIVATE).edit().putInt("kbOneHanded", mode).apply()
        }
        override fun haptic(kind: HapticKind) { KeyHaptics.fire(kind); UnoFeedback.sound(Cue.KEY) }
    }

    override fun onDestroy() {
        runCatching { clipboard?.removePrimaryClipChangedListener(clipListener) }
        suggestWorker.shutdown()
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onDestroy()
    }
}

/** What the keys can do. The service implements it; the UI only calls it. Marked stable so a key whose own inputs did not change
 * is not redrawn when something else does.
 */
@androidx.compose.runtime.Stable
internal interface KeyboardActions {
    fun type(text: String); fun replaceLast(text: String); fun backspace(held: Int); fun space(); fun enter(); fun globe(); fun shift(); fun page(target: KeyPage); fun haptic(kind: HapticKind)
    fun pick(suggestion: Suggestion); fun moveCursor(delta: Int)
    fun emoji(text: String); fun paste(); fun setOneHanded(mode: Int)
}

/** The alternatives strip shown while a key with accents or symbol variants is held: the choices, the one under the finger (-1 for
 * none yet) and where the key is in the window. Drawn in the keyboard's own window, never as a popup window of its own.
 */
internal data class AccentState(val options: List<String>, val index: Int, val bounds: androidx.compose.ui.geometry.Rect)

/** The key being held, for the magnified preview: its label and where it is in the window. */
internal data class KeyPreview(val label: String, val bounds: androidx.compose.ui.geometry.Rect)

/** The keyboard's observable state. Marked stable (every field that changes is observable state) so the keys are skipped, not
 * recomposed, when it is passed down and an unrelated field changes.
 */
@androidx.compose.runtime.Stable
internal class KeyboardUiState {
    var page by mutableStateOf(KeyPage.LETTERS)
    var shift by mutableStateOf(ShiftState.OFF)
    var enter by mutableStateOf(EnterKind.RETURN)
    var password by mutableStateOf(false)
    var preview by mutableStateOf<KeyPreview?>(null)
    var accents by mutableStateOf<AccentState?>(null)
    var width = 0f
    var suggestions by mutableStateOf<List<Suggestion>>(emptyList())
    var showStrip by mutableStateOf(false)
    var numberRow by mutableStateOf(false)
    var spaceCursor by mutableStateOf(true)
    /** Another keyboard exists to switch to, so the globe key is shown. */
    var globe by mutableStateOf(true)
    /** 0 full width, 1 pushed to the left, 2 to the right. */
    var oneHanded by mutableIntStateOf(0)
    var pasteAvailable by mutableStateOf(false)
    var recents by mutableStateOf<List<String>>(emptyList())
    var emoji by mutableStateOf<List<EmojiCategory>>(emptyList())
    /** The wallpaper accent as ARGB, when "Color from wallpaper" is on. */
    var accent by mutableStateOf<Int?>(null)

    fun dark(context: Context) = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
}
