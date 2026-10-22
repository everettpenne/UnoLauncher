package com.jake.duolauncher.keyboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.inline.InlinePresentationSpec
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.jake.duolauncher.lenientFloat
import java.util.concurrent.ConcurrentHashMap

/** Uno Keyboard: a liquid-glass on-screen keyboard.
 *
 * Everything it does happens on the phone. Its code has no network access (a test enforces that). It learns the words you use, how
 * you write them and what you put after what, and keeps that, with the clips you pin and your recent emoji, in the app's own
 * no-backup storage ([PersonalStore]); you can turn learning off or forget it all in Settings. It never learns from a password, email,
 * web address or number field, or from a field that asks keyboards not to (incognito tabs), and its clipboard history skips copies
 * Android marks as sensitive and forgets the rest after an hour unless you pin them. Text goes straight to the app you are typing in
 * through [InputConnection].
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
    private var imeOptions = 0
    private var lastShiftTapAt = 0L
    private var composeView: ComposeView? = null

    // Spelling help. The word lists are read from the app's own assets on a background thread; until they are ready there are simply no
    // suggestions. [english] is the dictionary; [none] is an empty one used on layouts it does not fit, so only learned words are offered.
    @Volatile private var english: WordEngine? = null
    private val none = WordEngine(emptyMap(), emptyMap())
    @Volatile private var emojiIndex: EmojiIndex? = null
    private val ignored = mutableSetOf<String>()
    private var lastFix: Pair<String, String>? = null
    private val mirror = TextMirror()
    private val expectedCursor = ExpectedCursor()
    private var cursor = 0
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    // Suggestions are computed off the main thread so a keystroke never waits for a dictionary scan; only the newest request is shown.
    private val suggestWorker = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "uno-keyboard-suggest").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } }
    private val suggestSeq = java.util.concurrent.atomic.AtomicInteger()

    // Settings, read each time a field opens.
    private var autocorrectOn = true
    private var suggestionsOn = true
    private var doubleSpaceOn = true
    private var autoCapsOn = true
    private var learnOn = true
    private var clipHistoryOn = true
    private var emojiSuggestOn = true
    private var swipeOn = true
    private var soundOn = false
    private var glassOn = true
    private var layouts = listOf(KeyLayouts.QWERTY)

    // What this field allows.
    private var allowSuggestions = false
    private var allowAutocorrect = false
    /** Whether what is typed here may be learned (see [LearningPolicy]). */
    private var learning = false

    /** True while text is selected, known from the selection updates, so a backspace need not ask the app. */
    private var hasSelection = false
    /** The correction for the word being typed, worked out on the worker with the suggestions so the space bar can use it at once. */
    @Volatile private var fixCache: Pair<String, String?>? = null
    private var clipboard: ClipboardManager? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { updatePasteAvailable(); recordClip() }
    private var audio: AudioManager? = null

    /** Where each letter of the word being typed was touched, oldest first: the touch model for autocorrect. */
    private val touches = ArrayList<Pt?>()
    /** After a swipe, a space is owed before the next word (and not before punctuation): Gboard's "phantom space". */
    private var phantomSpace = false
    /** The space after a picked suggestion was put there by the keyboard, so a full stop or comma typed next takes its place. */
    private var autoSpace = false
    /** The word the last swipe typed, and its other readings, so Delete takes it whole and the strip can swap it. */
    private var lastSwipe: String? = null
    private var swipeAlternatives: List<String> = emptyList()
    private var swipeShift = ShiftState.OFF
    /** Shift as it was before the last letter, and how much that letter's tap committed, so a swipe that began on it can undo both. */
    private var shiftBeforeType = ShiftState.OFF
    private var lastTypeLength = 0
    /** The clip the strip has already offered and had used, so the same one is not offered again. */
    private var clipOfferedAt = 0L
    private var voice: Pair<String, InputMethodSubtype?>? = null
    private var blurListenerAdded = false

    override fun onCreate() {
        super.onCreate()
        savedState.performAttach(); savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
        val prefs = getSharedPreferences("extras", Context.MODE_PRIVATE)
        clipboard = getSystemService(ClipboardManager::class.java)
        audio = getSystemService(AudioManager::class.java)
        runCatching { clipboard?.addPrimaryClipChangedListener(clipListener) }
        configureHaptics(prefs)
        PersonalStore.load(this) { main.post { ui.recents = PersonalStore.emoji.list(); ui.clips = PersonalStore.clips.list() } }
        Thread {
            // Loading the word lists is background work: it must never take the processor from the keyboard's first draw.
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            runCatching { ui.emoji = assets.open("keyboard/emoji.txt").bufferedReader().useLines { EmojiData.parse(it) } }
            runCatching {
                val index = assets.open("keyboard/emoji_words.txt").bufferedReader().useLines { EmojiIndex.parse(it) }
                emojiIndex = index; main.post { ui.emojiIndex = index }
            }
            runCatching {
                val words = assets.open("keyboard/en_words.txt").bufferedReader().useLines { WordEngine.parseWords(it) }
                val next = assets.open("keyboard/en_next.txt").bufferedReader().useLines { WordEngine.parseFollowers(it) }
                english = WordEngine(words, next).also { it.personal = none.personal; it.neighbours = none.neighbours }
                composeView?.post { refreshSuggestions() }
            }
        }.start()
    }

    private fun configureHaptics(prefs: android.content.SharedPreferences) {
        KeyHaptics.configure(this, if (prefs.getBoolean("haptics", true)) prefs.lenientFloat("kbHapticStrength", HapticProfile.DEFAULT_STRENGTH) else 0f,
            prefs.getInt("kbHapticFeel", HapticFeel.CRISP.ordinal))
    }

    override fun onCreateInputView(): View {
        // A Compose view needs lifecycle owners on its window; an input method has none, so this service is the owner.
        window.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this); decor.setViewTreeSavedStateRegistryOwner(this); decor.setViewTreeViewModelStoreOwner(this)
        }
        setUpGlassWindow()
        registry.currentState = Lifecycle.State.RESUMED
        return ComposeView(this).also { view ->
            view.setViewTreeLifecycleOwner(this); view.setViewTreeSavedStateRegistryOwner(this); view.setViewTreeViewModelStoreOwner(this)
            view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            view.setContent { KeyboardScreen(ui, actions) }
            composeView = view
        }
    }

    /** The keyboard's window is see-through and blurs the app behind it, like iOS's glass keyboard, where Android allows cross-window
     * blur (it switches it off in battery saver, and some phones never have it); otherwise the panel is simply drawn more opaque.
     */
    private fun setUpGlassWindow() {
        val w = window.window ?: return
        val wm = getSystemService(WindowManager::class.java) ?: return
        val radius = resources.displayMetrics.density * PANEL_CORNER_DP
        w.setFormat(PixelFormat.TRANSLUCENT)
        // The blur takes its corners from the window background, so the background is a clear shape with the panel's corners.
        w.setBackgroundDrawable(GradientDrawable().apply { setColor(0); cornerRadius = radius })
        fun apply(enabled: Boolean) {
            val on = enabled && glassOn
            runCatching { w.setBackgroundBlurRadius(if (on) (resources.displayMetrics.density * 28).toInt() else 0) }
            ui.blurred = on
        }
        apply(wm.isCrossWindowBlurEnabled)
        if (!blurListenerAdded) { blurListenerAdded = true; runCatching { wm.addCrossWindowBlurEnabledListener(mainExecutor) { apply(it) } } }
    }

    /** Landscape never switches to the fullscreen editor: the keyboard stays a keyboard and the app stays visible above it. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    /** Whether the clipboard holds text, from its description alone: the text itself is read only when Paste is tapped or, with the
     * history on, by [recordClip].
     */
    private fun updatePasteAvailable() {
        val cm = clipboard ?: return
        ui.pasteAvailable = runCatching { cm.hasPrimaryClip() && cm.primaryClipDescription?.hasMimeType("text/*") == true }.getOrDefault(false)
    }

    /** Adds what was just copied to the clipboard history, when the history is on. A copy Android marks as sensitive (a password manager
     * sets that) is never read, nor is anything copied while a password field is open.
     */
    private fun recordClip() {
        if (!clipHistoryOn || ui.password) return
        val cm = clipboard ?: return
        val desc = runCatching { cm.primaryClipDescription }.getOrNull() ?: return
        if (!desc.hasMimeType("text/*") && !desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)) return
        if (desc.extras?.getBoolean(EXTRA_IS_SENSITIVE, false) == true) return
        val text = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() }.getOrNull() ?: return
        PersonalStore.clips.add(text)
        ui.clips = PersonalStore.clips.list()
        refreshSuggestions()
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
        imeOptions = info.imeOptions
        ui.enter = KeyboardModel.enterKind(info.imeOptions)
        ui.password = KeyboardModel.isPassword(info.inputType)
        ui.page = KeyboardModel.startPage(info.inputType)
        ui.accent = readAccent()
        ui.panel = KeyPanel.NONE; ui.tools = false; ui.emojiSearch = null; ui.selecting = false
        val prefs = getSharedPreferences("extras", Context.MODE_PRIVATE)
        autocorrectOn = prefs.getBoolean("kbAutocorrect", true)
        suggestionsOn = prefs.getBoolean("kbSuggestions", true)
        doubleSpaceOn = prefs.getBoolean("kbDoubleSpace", true)
        autoCapsOn = prefs.getBoolean("kbAutoCaps", true)
        learnOn = prefs.getBoolean("kbLearn", true)
        clipHistoryOn = prefs.getBoolean("kbClipHistory", true)
        emojiSuggestOn = prefs.getBoolean("kbEmojiSuggestions", true)
        swipeOn = prefs.getBoolean("kbSwipe", true)
        soundOn = prefs.getBoolean("kbSound", prefs.getBoolean("sounds", false))
        ui.classic = prefs.getInt("kbStyle", 0) == 1
        // The classic look is opaque, so it never asks the window to blur.
        val glass = prefs.getBoolean("kbGlass", true) && !ui.classic
        if (glass != glassOn) { glassOn = glass; setUpGlassWindow() }
        layouts = KeyLayouts.parse(prefs.getString("kbLayouts", null))
        if (ui.layout !in layouts) setLayout(layouts.first())
        ui.layoutCount = layouts.size
        ui.numberRow = prefs.getBoolean("kbNumberRow", false)
        ui.oneHanded = prefs.getInt("kbOneHanded", 0).coerceIn(0, 2)
        ui.emojiKey = prefs.getBoolean("kbEmojiKey", true)
        ui.keyPreviews = prefs.getBoolean("kbKeyPreview", true)
        ui.heightScale = prefs.lenientFloat("kbHeight", 1f).coerceIn(.8f, 1.3f)
        // The switcher key is worth having when there is another layout, or another keyboard, to go to.
        ui.otherKeyboards = runCatching { shouldOfferSwitchingToNextInputMethod() }.getOrDefault(true)
        voice = findVoiceInput(); ui.voice = voice != null
        hasSelection = info.initialSelStart != info.initialSelEnd
        updatePasteAvailable()
        ui.clips = PersonalStore.clips.list()
        ui.spaceCursor = prefs.getBoolean("kbSpaceCursor", true)
        allowSuggestions = KeyboardModel.allowsSuggestions(info.inputType)
        allowAutocorrect = KeyboardModel.allowsAutocorrect(info.inputType)
        learning = LearningPolicy.allowed(info.inputType, info.imeOptions, learnOn)
        // Learned words are offered wherever suggestions are (they are the person's own); they are only *added to* where learning is allowed.
        val personal = if (learnOn) PersonalStore.model else null
        english?.personal = personal; none.personal = personal
        ui.showStrip = suggestionsOn && allowSuggestions
        ui.swipe = swipeOn && allowSuggestions && !ui.password
        ignored.clear(); lastFix = null; fixCache = null; touches.clear(); phantomSpace = false; autoSpace = false; lastSwipe = null
        ui.inline = emptyList()
        configureHaptics(prefs)
        // One read of the field now; after this the mirror follows our own edits and only external changes are re-read.
        expectedCursor.clear(); cursor = info.initialSelStart
        if (info.initialSelStart == info.initialSelEnd) resyncMirror() else mirror.invalidate()
        ui.shift = KeyboardModel.shiftFor(if (capsNow()) 1 else 0, ShiftState.OFF)
        refreshSuggestions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        ui.inline = emptyList()
        if (PersonalStore.loaded) PersonalStore.saveNow()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candStart: Int, candEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candStart, candEnd)
        hasSelection = newSelStart != newSelEnd
        if (newSelStart == newSelEnd && expectedCursor.isOurs(newSelStart)) return   // the echo of something we typed
        // The cursor moved or the text changed from outside (a tap in the field, a paste, the app editing it).
        cursor = newSelStart
        touches.clear(); phantomSpace = false; autoSpace = false; lastSwipe = null
        if (newSelStart == newSelEnd) resyncMirror() else mirror.invalidate()
        ui.shift = KeyboardModel.shiftFor(if (capsNow()) 1 else 0, ui.shift)
        refreshSuggestions()
    }

    private fun readAccent(): Int? =
        if (getSharedPreferences("appearance", Context.MODE_PRIVATE).getBoolean("wallpaperColor", false))
            runCatching { com.jake.duolauncher.appearance.WallpaperAccents.compute(this, ui.dark(this)).accent.toArgb() }.getOrNull() else null

    private fun resyncMirror() {
        val before = currentInputConnection?.getTextBeforeCursor(80, 0)
        if (before == null) mirror.invalidate() else mirror.reset(before)
    }

    /** The text before the cursor, from memory when possible. */
    private fun textBefore(): String =
        if (mirror.valid) mirror.get() else (currentInputConnection?.getTextBeforeCursor(80, 0)?.toString() ?: "")

    private fun capsNow(): Boolean {
        if (!autoCapsOn) return false
        if (inputType and 0x4000 == 0 && inputType and 0x2000 == 0 && inputType and 0x1000 == 0) return false
        return if (mirror.valid) KeyboardModel.capsFromText(mirror.get(), inputType)
        else (currentInputConnection?.getCursorCapsMode(inputType) ?: 0) != 0
    }

    private fun editedText(typed: String, removed: Int = 0) {
        if (removed > 0) { mirror.delete(removed); cursor -= removed }
        mirror.commit(typed); cursor += typed.length; expectedCursor.expect(cursor)
    }

    /**
     * Replaces the [removed] characters before the cursor with [text], as one edit. Returns false, changing nothing, when the app's text is not
     * what the keyboard thinks it is.
     *
     * Bug fix (reported in Signal): typing a long message, once the box grew to a second line, corrections turned the text into gibberish and
     * the box flickered between that and what was typed. Root cause: an edit was sent as two steps (delete, then type) with nothing around
     * them, so an app that rewrites its text box as it changes (Signal re-processes its compose text, and re-lays it out when it gains a
     * line) acted on the half-done edit; and the keyboard, deleting from its own remembered copy of the text, removed whatever characters
     * were there by then. Each side then "fixed" the other. Now every edit is one batch edit the app sees whole, and before anything is
     * deleted the app's real text is read and checked against the copy; if they differ the copy is re-read and nothing is touched.
     */
    private fun commit(ic: InputConnection, text: String, removed: Int = 0): Boolean {
        ic.beginBatchEdit()
        try {
            if (removed > 0) {
                if (!matchesApp(ic, removed)) { resyncAfterMismatch(); return false }
                ic.deleteSurroundingText(removed, 0)
            }
            ic.commitText(text, 1)
            editedText(text, removed)
            return true
        } finally { ic.endBatchEdit() }
    }

    /** Deletes the [count] characters before the cursor, as one edit, after checking them as [commit] does. */
    private fun deleteBefore(ic: InputConnection, count: Int, check: Boolean = true): Boolean {
        if (count <= 0) return true
        ic.beginBatchEdit()
        try {
            if (check && !matchesApp(ic, count)) { resyncAfterMismatch(); return false }
            ic.deleteSurroundingText(count, 0); mirror.delete(count); cursor -= count; expectedCursor.expect(cursor)
            return true
        } finally { ic.endBatchEdit() }
    }

    /** Whether the [count] characters before the cursor in the app are the ones the keyboard's copy says (always true when the copy was just read). */
    private fun matchesApp(ic: InputConnection, count: Int): Boolean {
        if (!mirror.valid) return true
        val mine = mirror.get().takeLast(count)
        val theirs = ic.getTextBeforeCursor(count, 0)?.toString() ?: return false
        return theirs == mine
    }

    private fun resyncAfterMismatch() {
        mirror.invalidate(); expectedCursor.clear(); touches.clear(); lastFix = null; lastSwipe = null; phantomSpace = false; autoSpace = false
        main.post { resyncMirror(); refreshShift(); refreshSuggestions() }
    }

    // ---- layouts ----------------------------------------------------------------------------------------------------------------

    private fun setLayout(layout: KeyLayout) {
        ui.layout = layout
        ui.keyCentres.clear()
        val n = KeyLayouts.neighbours(layout)
        english?.neighbours = n; none.neighbours = n
    }

    /** The dictionary that fits the layout in use: English on the English layouts, and on others only the learned words. */
    private fun engine(): WordEngine? = if (ui.layout.english) english else none

    private fun geometry(): KeyGeometry = KeyGeometry(HashMap(ui.keyCentres), ui.keyWidth, ui.keyHeight)

    /** The touches for the word being typed, one per letter, oldest first; unknown ones (the word was there before) are null. */
    private fun touchesFor(word: String): List<Pt?> {
        val have = touches.takeLast(word.length)
        return List(word.length - have.size) { null } + have
    }

    // ---- learning -------------------------------------------------------------------------------------------------------------

    private fun today() = (System.currentTimeMillis() / 86_400_000L).toInt()

    /** Teaches the personal model the word just finished (as it now stands, after any correction) and the words before it. */
    private fun learnCurrent(explicit: Boolean = false) {
        if (!learning) return
        val ctx = Suggest.parse(textBefore())
        if (ctx.current.isEmpty() || !ctx.plainWord) return
        val word = ctx.current
        val known = (english?.contains(word.lowercase()) == true)
        val model = PersonalStore.model
        model.learnWord(word, today(), inDictionary = known, explicit = explicit)
        model.learnSequence(ctx.previous2, ctx.previous, word)
        PersonalStore.saveSoon()
    }

    // ---- suggestions ----------------------------------------------------------------------------------------------------------

    private fun refreshSuggestions() {
        val e = engine()
        if (!ui.showStrip || e == null || ui.emojiSearch != null) { ui.suggestions = emptyList(); return }
        val before = textBefore(); val shift = ui.shift != ShiftState.OFF; val skip = ignored.toSet()
        val sentenceCaps = inputType and 0x4000 != 0 && autoCapsOn
        val canFix = autocorrectOn && allowAutocorrect && ui.layout.english
        val geometry = geometry()
        val wordTouches = touchesFor(Suggest.parse(before).current)
        val fixToUndo = lastFix
        val clip = if (clipHistoryOn) PersonalStore.clips.fresh()?.takeIf { it.at != clipOfferedAt } else null
        val emoji = if (emojiSuggestOn) emojiIndex else null
        val seq = suggestSeq.incrementAndGet()
        suggestWorker.execute {
            val ctx = Suggest.parse(before)
            // The correction is worked out here, once, and kept for the space bar to use without scanning the dictionary itself.
            val fix = if (ctx.current.isEmpty() || !canFix) null
                else Suggest.autocorrect(e, ctx, skip, capitalise = sentenceCaps && ctx.atSentenceStart, touches = wordTouches, geometry = geometry)
            fixCache = ctx.current to fix
            var result = Suggest.suggestions(e, ctx, skip, capitalise = shift || (ctx.atSentenceStart && ctx.current.isEmpty()), fix = fix)
            if (ctx.current.isEmpty()) {
                // Just after a correction, the word it replaced is offered first, to put back with one tap (as iOS does).
                if (fixToUndo != null && before.endsWith(fixToUndo.second + " ")) result = listOf(Suggestion(fixToUndo.first, SuggestionKind.REVERT)) + result.take(2)
                else if (clip != null) result = listOf(Suggestion(clip.text, SuggestionKind.CLIP)) + result.take(2)
            }
            if (emoji != null) result = Suggest.withEmoji(result, ctx, emoji)
            main.post { if (seq == suggestSeq.get()) ui.suggestions = result }
        }
    }

    /** Replaces the word before the cursor with its correction, if it has one. Returns whether it did. */
    private fun applyAutocorrect(): Boolean {
        val e = engine() ?: return false
        if (!autocorrectOn || !allowAutocorrect || !ui.layout.english) return false
        val ic = currentInputConnection ?: return false
        val ctx = Suggest.parse(textBefore())
        if (ctx.current.isEmpty()) return false
        // Use what the worker already found for this word; only if it has not caught up is the dictionary scanned here.
        val cached = fixCache
        val sentenceCaps = inputType and 0x4000 != 0 && autoCapsOn
        val fix = (if (cached != null && cached.first == ctx.current) cached.second
            else Suggest.autocorrect(e, ctx, ignored, capitalise = sentenceCaps && ctx.atSentenceStart, touches = touchesFor(ctx.current), geometry = geometry())) ?: return false
        if (!commit(ic, fix, removed = ctx.current.length)) return false
        lastFix = ctx.current to fix
        // Felt as well as seen: a soft double pulse just after the key's own tick.
        main.postDelayed({ KeyHaptics.fire(HapticKind.AUTOCORRECT) }, 45)
        return true
    }

    private fun refreshShift() { ui.shift = KeyboardModel.shiftFor(if (capsNow()) 1 else 0, ui.shift) }

    /** Ends the word being typed: corrects it if it needs it and learns it. Returns whether it was corrected. */
    private fun finishWord(): Boolean {
        val corrected = applyAutocorrect()
        learnCurrent()
        return corrected
    }

    // ---- swipe typing -----------------------------------------------------------------------------------------------------------

    private fun decodeSwipe(path: List<Pt>): List<String> {
        val e = engine() ?: return emptyList()
        val ctx = Suggest.parse(textBefore())
        val follow = e.nextWords(ctx.current.ifEmpty { null } ?: ctx.previous, 8, ctx.previous2).map { it.lowercase() }
        val geometry = geometry()
        return GestureDecoder.decode(path, geometry, { c -> e.wordsStartingWith(c) },
            bonus = { w -> val r = follow.indexOf(w); (if (r >= 0) 2.4 - r * .25 else 0.0) + (if (e.personal?.knows(w, e.inDictionary(w)) == true) .8 else 0.0) })
    }

    private fun shownSwipeWord(raw: String): String {
        val e = english
        val bare = if (e != null && (!e.inDictionary(raw) || raw == "i")) WordEngine.CONTRACTIONS[raw] ?: raw else raw
        val form = PersonalStore.model.formOf(bare)?.takeIf { e?.inDictionary(bare) != true } ?: bare
        return when (swipeShift) {
            ShiftState.LOCKED -> form.uppercase()
            ShiftState.ONCE -> form.replaceFirstChar { it.uppercase() }
            ShiftState.OFF -> form
        }
    }

    private fun commitSwipe(words: List<String>) {
        val ic = currentInputConnection ?: return
        val raw = words.firstOrNull() ?: return
        val before = textBefore()
        // The word before it is finished (and learned) first, then this one goes after a space unless it starts a line or follows an opener.
        if (before.isNotEmpty() && before.last().isLetter()) learnCurrent()
        val needsSpace = before.isNotEmpty() && !before.last().isWhitespace() && before.last() !in "([{\"“‘/@#"
        val word = shownSwipeWord(raw)
        commit(ic, (if (needsSpace) " " else "") + word)
        touches.clear()
        lastSwipe = word; swipeAlternatives = words.drop(1).map(::shownSwipeWord).filter { it != word }.take(3)
        phantomSpace = true; autoSpace = false; lastFix = null
        KeyHaptics.fire(HapticKind.SWIPE_DONE)
        ui.shift = KeyboardModel.afterLetter(swipeShift).let { if (it == ShiftState.LOCKED) it else ShiftState.OFF }
        ui.suggestions = swipeAlternatives.map { Suggestion(it, SuggestionKind.GESTURE) }
    }

    /** A voice keyboard turned on in Android's settings (FUTO Voice Input, Sayboard and the like), for the strip's microphone: one whose
     * subtype is a "voice" mode, or failing that one that names itself a voice input. Null when there is none, and the button is hidden.
     */
    private fun findVoiceInput(): Pair<String, InputMethodSubtype?>? = runCatching {
        val imm = getSystemService(InputMethodManager::class.java) ?: return@runCatching null
        val others = imm.enabledInputMethodList.filter { it.packageName != packageName }
        others.firstNotNullOfOrNull { imi -> imm.getEnabledInputMethodSubtypeList(imi, true).firstOrNull { it.mode == "voice" }?.let { imi.id to it } }
            ?: others.firstOrNull { it.packageName.contains("voice", ignoreCase = true) }?.let { it.id to null }
    }.getOrNull()

    // ---- autofill (passwords, addresses from the person's autofill service) ---------------------------------------------------

    /** Asks the autofill service (Bitwarden, KeePassDX, Proton Pass…) for chips to show in the strip, as Gboard does. */
    // RestrictedApi: setBackground and setPadding are inherited from a base builder lint sees as library-private, but they are the
    // documented public way to style ViewStyle (AndroidX's own inline-suggestion samples call them); lint's check is a false positive.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        val d = resources.displayMetrics.density
        val dark = ui.dark(this)
        val style = runCatching {
            InlineSuggestionUi.newStyleBuilder()
                .setChipStyle(androidx.autofill.inline.common.ViewStyle.Builder()
                    .setBackground(android.graphics.drawable.Icon.createWithResource(this, com.jake.duolauncher.R.drawable.kb_inline_chip))
                    .setPadding((12 * d).toInt(), 0, (12 * d).toInt(), 0).build())
                .setTitleStyle(androidx.autofill.inline.common.TextViewStyle.Builder()
                    .setTextColor(if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()).setTextSize(15f).build())
                .setSubtitleStyle(androidx.autofill.inline.common.TextViewStyle.Builder()
                    .setTextColor(if (dark) 0xB3FFFFFF.toInt() else 0x99000000.toInt()).setTextSize(12f).build())
                .build()
        }.getOrNull() ?: return null
        val styles = UiVersions.newStylesBuilder().addStyle(style).build()
        val spec = InlinePresentationSpec.Builder(Size((60 * d).toInt(), (34 * d).toInt()), Size((260 * d).toInt(), (34 * d).toInt()))
            .setStyle(styles).build()
        return InlineSuggestionsRequest.Builder(listOf(spec)).setMaxSuggestionCount(6).build()
    }

    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        val list = response.inlineSuggestions
        if (list.isEmpty()) { ui.inline = emptyList(); return true }
        val views = arrayOfNulls<View>(list.size)
        var remaining = list.size
        list.forEachIndexed { i, s ->
            runCatching {
                s.inflate(this, Size(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), mainExecutor) { v ->
                    views[i] = v
                    if (--remaining == 0) ui.inline = views.filterNotNull()
                }
            }.onFailure { if (--remaining == 0) ui.inline = views.filterNotNull() }
        }
        return true
    }

    // ---- what the keys do ------------------------------------------------------------------------------------------------------

    internal val actions = object : KeyboardActions {
        override fun type(text: String, at: Pt?) {
            val search = ui.emojiSearch
            if (search != null) { ui.emojiSearch = search + text; ui.shift = KeyboardModel.afterLetter(ui.shift); return }
            val ic = currentInputConnection ?: return
            shiftBeforeType = ui.shift
            val before = textBefore()
            val first = text.firstOrNull()
            var typed = 0
            val punctuation = text.length == 1 && text[0] in ".,!?;:)"
            if (punctuation) {
                // A full stop, comma or bracket straight after a word finishes it, so it is corrected (and learned) first.
                finishWord()
                // After a picked suggestion's space, the punctuation takes the space's place and puts it after itself ("word. ").
                val now = textBefore()
                if (autoSpace && now.endsWith(" ") && text[0] != ')' && commit(ic, text + " ", removed = 1)) typed = text.length + 1
                else { commit(ic, text); typed = text.length }
                touches.clear()
            } else {
                lastFix = null
                // A word starting after a swipe gets the space the swipe owed it.
                if (phantomSpace && first != null && first.isLetterOrDigit()) { commit(ic, " "); typed += 1 }
                if (before.isEmpty() || !before.last().isLetter() || phantomSpace) touches.clear()
                commit(ic, text); typed += text.length
                if (first?.isLetter() == true && text.length == 1) touches += at else touches.clear()
            }
            lastTypeLength = typed
            phantomSpace = false; autoSpace = false; lastSwipe = null
            ui.shift = KeyboardModel.afterLetter(ui.shift)
            ui.page = KeyboardModel.pageAfterTyping(ui.page, text)
            if (first?.isLetter() != true) refreshShift()
            refreshSuggestions()
        }
        override fun replaceLast(text: String) {
            val search = ui.emojiSearch
            if (search != null) { ui.emojiSearch = search.dropLast(1) + text; return }
            val ic = currentInputConnection ?: return
            commit(ic, text, removed = 1); lastFix = null
            touches.removeLastOrNull(); touches += null
            refreshSuggestions()
        }
        override fun backspace(held: Int) {
            val search = ui.emojiSearch
            if (search != null) { ui.emojiSearch = search.dropLast(1); return }
            val ic = currentInputConnection ?: return
            if (held == KeyboardModel.WORD_DELETE_AFTER) KeyHaptics.fire(HapticKind.DELETE_WORD)
            val fix = lastFix
            if (fix != null && held == 0) {
                // Backspace straight after an autocorrection undoes it, and that word is left alone, and learned, from then on.
                val before = textBefore()
                val spaced = before.endsWith(fix.second + " ")
                if (spaced || before.endsWith(fix.second)) {
                    val n = fix.second.length + if (spaced) 1 else 0
                    if (commit(ic, fix.first, removed = n)) {
                        ignored += fix.first.lowercase(); fixCache = null
                        learnCurrent(explicit = true)
                    }
                    lastFix = null
                    refreshShift(); refreshSuggestions(); return
                }
            }
            lastFix = null; autoSpace = false
            val swiped = lastSwipe
            if (swiped != null && held == 0 && textBefore().endsWith(swiped)) {
                // Delete straight after a swipe takes the whole word it typed, as Gboard does.
                deleteBefore(ic, swiped.length)
                lastSwipe = null; phantomSpace = false; touches.clear()
                refreshShift(); refreshSuggestions(); return
            }
            lastSwipe = null; phantomSpace = false
            if (hasSelection) { ic.commitText("", 1); hasSelection = false; mirror.invalidate(); resyncMirror(); cursor = -1; touches.clear() }
            else {
                // One character at a time (a whole emoji or flag counts as one), then whole words once held a while. The text is
                // needed to know how many units that is, so when the mirror has lost it a single unit is deleted without asking.
                val n = if (mirror.valid || held >= KeyboardModel.WORD_DELETE_AFTER) KeyboardModel.deleteStep(textBefore(), held).coerceAtLeast(1) else 1
                deleteBefore(ic, n, check = false)
                if (n == 1) touches.removeLastOrNull() else touches.clear()
            }
            refreshShift(); refreshSuggestions()
        }
        override fun space() {
            if (ui.emojiSearch != null) { ui.emojiSearch = ui.emojiSearch + " "; return }
            val ic = currentInputConnection ?: return
            phantomSpace = false; lastSwipe = null
            val corrected = finishWord()
            val change = if (doubleSpaceOn) KeyboardModel.doubleSpace(textBefore()) else null
            if (change == null || corrected || !commit(ic, change.second, removed = change.first + 1)) commit(ic, " ") else lastFix = null
            autoSpace = false; touches.clear()
            refreshShift(); refreshSuggestions()
        }
        override fun enter() {
            if (ui.emojiSearch != null) { ui.emojiSearch = null; ui.page = KeyPage.EMOJI; return }
            val ic = currentInputConnection ?: return
            finishWord(); lastFix = null; phantomSpace = false; autoSpace = false; lastSwipe = null; touches.clear()
            if (ui.enter.sendsAction) ic.performEditorAction(currentInputEditorInfo.imeOptions and 0xff)
            else ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)).also { ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)) }
            // What the app does with Return varies (a newline, an action, nothing), so re-read the field once.
            mirror.invalidate(); expectedCursor.clear()
            main.post { resyncMirror(); refreshShift(); refreshSuggestions() }
        }
        override fun pick(suggestion: Suggestion) {
            val ic = currentInputConnection ?: return
            val ctx = Suggest.parse(textBefore())
            var spaced = true
            when (suggestion.kind) {
                SuggestionKind.NEXT -> { commit(ic, suggestion.text); learnCurrent(); commit(ic, " ") }
                SuggestionKind.TYPED -> { ignored += ctx.current.lowercase(); fixCache = null; learnCurrent(explicit = true); commit(ic, " ") }
                SuggestionKind.CORRECTION, SuggestionKind.COMPLETION -> {
                    if (!commit(ic, suggestion.text, removed = ctx.current.length)) return
                    learnCurrent(); commit(ic, " ")
                }
                SuggestionKind.EMOJI -> {
                    val lead = if (ctx.current.isNotEmpty()) { learnCurrent(); " " } else ""
                    commit(ic, lead + suggestion.text + " ")
                    PersonalStore.emoji.add(suggestion.text); ui.recents = PersonalStore.emoji.list()
                }
                SuggestionKind.REVERT -> {
                    val fix = lastFix
                    val before = textBefore()
                    if (fix != null && before.endsWith(fix.second + " ")) {
                        if (commit(ic, fix.first, removed = fix.second.length + 1)) { ignored += fix.first.lowercase(); learnCurrent(explicit = true); commit(ic, " ") }
                    }
                }
                SuggestionKind.GESTURE -> {
                    val old = lastSwipe
                    if (old != null && textBefore().endsWith(old)) {
                        if (!commit(ic, suggestion.text, removed = old.length)) return
                        swipeAlternatives = (swipeAlternatives - suggestion.text) + old
                        lastSwipe = suggestion.text; phantomSpace = true
                        ui.suggestions = swipeAlternatives.map { Suggestion(it, SuggestionKind.GESTURE) }
                        KeyHaptics.fire(HapticKind.SELECT)
                        return
                    }
                    spaced = false
                }
                SuggestionKind.CLIP -> {
                    clipOfferedAt = PersonalStore.clips.fresh()?.at ?: 0L
                    commit(ic, suggestion.text); spaced = false
                    mirror.invalidate(); expectedCursor.clear(); main.post { resyncMirror(); refreshShift(); refreshSuggestions() }
                }
            }
            lastFix = null; lastSwipe = null; phantomSpace = false; touches.clear()
            autoSpace = spaced && suggestion.kind != SuggestionKind.REVERT
            refreshShift(); refreshSuggestions()
        }
        override fun moveCursor(delta: Int) {
            val ic = currentInputConnection ?: return
            val key = if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
            val meta = if (ui.selecting) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
            repeat(kotlin.math.abs(delta)) { sendKey(ic, key, meta) }
            mirror.invalidate()   // the cursor is somewhere new; the selection update re-reads the text around it
        }
        override fun globe() {
            if (layouts.size > 1) {
                // Several layouts: the globe goes round them (and a long-press opens Android's keyboard list).
                setLayout(layouts[(layouts.indexOf(ui.layout) + 1) % layouts.size])
                refreshSuggestions(); return
            }
            val switched = switchToNextInputMethod(false)
            if (!switched) getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }
        override fun keyboardPicker() { getSystemService(InputMethodManager::class.java)?.showInputMethodPicker() }
        override fun chooseLayout(id: String) { KeyLayouts.byId(id)?.let { setLayout(it); ui.panel = KeyPanel.NONE; refreshSuggestions() } }
        override fun shift() {
            val now = System.currentTimeMillis()
            ui.shift = KeyboardModel.tapShift(ui.shift, doubleTap = now - lastShiftTapAt < 350L)
            if (ui.shift == ShiftState.LOCKED) KeyHaptics.fire(HapticKind.SHIFT_LOCK)
            lastShiftTapAt = now
        }
        override fun page(target: KeyPage) { ui.page = target; ui.panel = KeyPanel.NONE; if (target != KeyPage.LETTERS) ui.emojiSearch = null }
        override fun emoji(text: String) {
            val ic = currentInputConnection ?: return
            lastFix = null; lastSwipe = null; autoSpace = false
            commit(ic, (if (phantomSpace) " " else "") + text); phantomSpace = false; touches.clear()
            PersonalStore.emoji.add(text); ui.recents = PersonalStore.emoji.list()
            if (learnOn) PersonalStore.saveSoon()
            refreshSuggestions()
        }
        override fun emojiSearch(open: Boolean) {
            if (open) { ui.emojiSearch = ""; ui.page = KeyPage.LETTERS; ui.shift = ShiftState.OFF } else { ui.emojiSearch = null; ui.page = KeyPage.EMOJI }
            refreshSuggestions()
        }
        override fun paste() {
            val ic = currentInputConnection ?: return
            // The clipboard is read here, on the tap (and, with the history on, when something is copied: see recordClip).
            val text = runCatching { clipboard?.primaryClip?.getItemAt(0)?.coerceToText(this@UnoKeyboardService)?.toString() }.getOrNull()
            if (text.isNullOrEmpty()) return
            insertExternal(ic, text)
        }
        override fun pasteClip(text: String) { val ic = currentInputConnection ?: return; insertExternal(ic, text); ui.panel = KeyPanel.NONE }
        override fun pinClip(text: String, pinned: Boolean) { PersonalStore.clips.setPinned(text, pinned); ui.clips = PersonalStore.clips.list(); PersonalStore.saveSoon() }
        override fun removeClip(text: String) { PersonalStore.clips.remove(text); ui.clips = PersonalStore.clips.list(); PersonalStore.saveSoon() }
        override fun clearClips() { PersonalStore.clips.clearRecent(); ui.clips = PersonalStore.clips.list() }
        override fun edit(op: EditOp) {
            val ic = currentInputConnection ?: return
            val shift = if (ui.selecting) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
            when (op) {
                EditOp.LEFT -> sendKey(ic, KeyEvent.KEYCODE_DPAD_LEFT, shift)
                EditOp.RIGHT -> sendKey(ic, KeyEvent.KEYCODE_DPAD_RIGHT, shift)
                EditOp.UP -> sendKey(ic, KeyEvent.KEYCODE_DPAD_UP, shift)
                EditOp.DOWN -> sendKey(ic, KeyEvent.KEYCODE_DPAD_DOWN, shift)
                EditOp.HOME -> sendKey(ic, KeyEvent.KEYCODE_MOVE_HOME, shift)
                EditOp.END -> sendKey(ic, KeyEvent.KEYCODE_MOVE_END, shift)
                EditOp.SELECT -> { ui.selecting = !ui.selecting; return }
                EditOp.SELECT_ALL -> { ic.performContextMenuAction(android.R.id.selectAll); ui.selecting = false }
                EditOp.COPY -> { ic.performContextMenuAction(android.R.id.copy); ui.selecting = false }
                EditOp.CUT -> { ic.performContextMenuAction(android.R.id.cut); ui.selecting = false }
                EditOp.PASTE -> { paste(); return }
                EditOp.UNDO -> sendKey(ic, KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
                EditOp.REDO -> sendKey(ic, KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)
            }
            mirror.invalidate(); expectedCursor.clear(); touches.clear(); lastSwipe = null; phantomSpace = false
            main.post { resyncMirror(); refreshShift(); refreshSuggestions() }
        }
        override fun panel(panel: KeyPanel) {
            ui.panel = if (ui.panel == panel) KeyPanel.NONE else panel
            ui.tools = false
            if (ui.panel == KeyPanel.CLIPBOARD) ui.clips = PersonalStore.clips.list()
            if (ui.panel == KeyPanel.NONE) ui.selecting = false
        }
        override fun tools(open: Boolean) { ui.tools = open; if (!open && ui.panel != KeyPanel.NONE) ui.panel = KeyPanel.NONE }
        override fun voice() { voice?.let { (id, subtype) -> runCatching { if (subtype != null) switchInputMethod(id, subtype) else switchInputMethod(id) } } }
        override fun openSettings() {
            runCatching {
                startActivity(android.content.Intent(this@UnoKeyboardService, com.jake.duolauncher.MainActivity::class.java)
                    .putExtra("duo_destination", "keyboard").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            requestHideSelf(0)
        }
        override fun swipeStart() {
            // The letter the swipe began on was typed when the finger landed; the swipe types a whole word instead.
            val ic = currentInputConnection ?: return
            val n = lastTypeLength.coerceAtMost(textBefore().length).coerceAtLeast(0)
            if (n > 0) deleteBefore(ic, n)
            lastTypeLength = 0; touches.clear()
            swipeShift = shiftBeforeType; ui.shift = shiftBeforeType
            ui.swiping = true
        }
        override fun swipeMove(path: List<Pt>) {
            val seq = suggestSeq.incrementAndGet()
            suggestWorker.execute {
                if (seq != suggestSeq.get()) return@execute    // a newer request is queued; skip this one
                val best = decodeSwipe(path).firstOrNull()?.let(::shownSwipeWord)
                main.post { if (seq == suggestSeq.get() && ui.swiping) ui.suggestions = listOfNotNull(best?.let { Suggestion(it, SuggestionKind.GESTURE) }) }
            }
        }
        override fun swipeEnd(path: List<Pt>) {
            val seq = suggestSeq.incrementAndGet()
            suggestWorker.execute {
                val words = decodeSwipe(path)
                main.post { ui.swiping = false; if (seq == suggestSeq.get() || words.isNotEmpty()) commitSwipe(words) }
            }
        }
        override fun setOneHanded(mode: Int) {
            ui.oneHanded = mode
            getSharedPreferences("extras", Context.MODE_PRIVATE).edit().putInt("kbOneHanded", mode).apply()
        }
        override fun haptic(kind: HapticKind) {
            KeyHaptics.fire(kind)
            if (soundOn) audio?.let { a ->
                val fx = when (kind) {
                    HapticKind.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                    HapticKind.DELETE, HapticKind.DELETE_REPEAT -> AudioManager.FX_KEYPRESS_DELETE
                    HapticKind.RETURN -> AudioManager.FX_KEYPRESS_RETURN
                    HapticKind.LETTER, HapticKind.MODIFIER -> AudioManager.FX_KEYPRESS_STANDARD
                    else -> null
                }
                if (fx != null) runCatching { a.playSoundEffect(fx, -1f) }
            }
        }
    }

    /** Text from outside the keyboard (a paste or a clip): the field is re-read afterwards, since the keyboard did not type it. */
    private fun insertExternal(ic: InputConnection, text: String) {
        lastFix = null; lastSwipe = null; phantomSpace = false; autoSpace = false; touches.clear()
        ic.commitText(text, 1); mirror.invalidate(); expectedCursor.clear()
        main.post { resyncMirror(); refreshShift(); refreshSuggestions() }
    }

    private fun sendKey(ic: InputConnection, code: Int, meta: Int) {
        val now = android.os.SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
    }

    override fun onDestroy() {
        runCatching { clipboard?.removePrimaryClipChangedListener(clipListener) }
        if (PersonalStore.loaded) PersonalStore.saveNow()
        suggestWorker.shutdown()
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onDestroy()
    }

    private companion object {
        /** ClipDescription.EXTRA_IS_SENSITIVE (Android 13), by name so older versions read the same flag apps already set. */
        const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
    }
}

/** The panels that can take the keys' place. */
internal enum class KeyPanel { NONE, CLIPBOARD, EDIT, LAYOUTS }

/** What the text-editing panel's buttons do. */
internal enum class EditOp { LEFT, RIGHT, UP, DOWN, HOME, END, SELECT, SELECT_ALL, COPY, CUT, PASTE, UNDO, REDO }

/** What the keys can do. The service implements it; the UI only calls it. Marked stable so a key whose own inputs did not change
 * is not redrawn when something else does. Most members have empty defaults, so a preview or a test implements only what it needs.
 */
@androidx.compose.runtime.Stable
internal interface KeyboardActions {
    /** Types [text]; [at] is where on the screen the key was touched, for autocorrect's touch model. */
    fun type(text: String, at: Pt? = null)
    fun replaceLast(text: String); fun backspace(held: Int); fun space(); fun enter(); fun globe(); fun shift(); fun page(target: KeyPage); fun haptic(kind: HapticKind)
    fun pick(suggestion: Suggestion); fun moveCursor(delta: Int)
    fun emoji(text: String); fun paste(); fun setOneHanded(mode: Int)
    fun keyboardPicker() {}
    fun chooseLayout(id: String) {}
    fun emojiSearch(open: Boolean) {}
    fun pasteClip(text: String) {}
    fun pinClip(text: String, pinned: Boolean) {}
    fun removeClip(text: String) {}
    fun clearClips() {}
    fun edit(op: EditOp) {}
    fun panel(panel: KeyPanel) {}
    fun tools(open: Boolean) {}
    fun voice() {}
    fun openSettings() {}
    fun swipeStart() {}
    fun swipeMove(path: List<Pt>) {}
    fun swipeEnd(path: List<Pt>) {}
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
    /** Another keyboard exists to switch to. */
    var otherKeyboards by mutableStateOf(true)
    /** 0 full width, 1 pushed to the left, 2 to the right. */
    var oneHanded by mutableIntStateOf(0)
    var pasteAvailable by mutableStateOf(false)
    var recents by mutableStateOf<List<String>>(emptyList())
    var emoji by mutableStateOf<List<EmojiCategory>>(emptyList())
    var emojiIndex by mutableStateOf<EmojiIndex?>(null)
    /** The emoji search being typed, or null when not searching. */
    var emojiSearch by mutableStateOf<String?>(null)
    /** The wallpaper accent as ARGB, when "Color from wallpaper" is on. */
    var accent by mutableStateOf<Int?>(null)
    var layout by mutableStateOf(KeyLayouts.QWERTY)
    var layoutCount by mutableIntStateOf(1)
    var emojiKey by mutableStateOf(true)
    var keyPreviews by mutableStateOf(true)
    var heightScale by mutableFloatStateOf(1f)
    /** Uno 0.97's flat look instead of liquid glass (Settings > Keyboard > Key style); only the drawing changes. */
    var classic by mutableStateOf(false)
    /** The window blurs what is behind it, so the panel can be drawn see-through. */
    var blurred by mutableStateOf(false)
    var panel by mutableStateOf(KeyPanel.NONE)
    /** The strip shows the tool buttons instead of suggestions. */
    var tools by mutableStateOf(false)
    var clips by mutableStateOf<List<Clip>>(emptyList())
    var voice by mutableStateOf(false)
    /** Arrow keys in the editing panel extend the selection. */
    var selecting by mutableStateOf(false)
    /** Autofill chips from the person's autofill service, shown in the strip in place of suggestions. */
    var inline by mutableStateOf<List<View>>(emptyList())
    var swipe by mutableStateOf(false)
    var swiping by mutableStateOf(false)
    /** The swipe trail being drawn, in window pixels. */
    val trail = mutableStateListOf<androidx.compose.ui.geometry.Offset>()
    /** Where each letter key is, reported by the keys as they are laid out (not observable: read when a word is checked). */
    val keyCentres = ConcurrentHashMap<Char, Pt>()
    @Volatile var keyWidth = 0f
    @Volatile var keyHeight = 0f

    /** Whether the globe key is shown: there is another layout or another keyboard to go to. */
    val globe get() = layoutCount > 1 || otherKeyboards

    fun dark(context: Context) = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
}

/** The rounding of the keyboard panel's top corners, which the window's blur follows. */
internal const val PANEL_CORNER_DP = 26f
