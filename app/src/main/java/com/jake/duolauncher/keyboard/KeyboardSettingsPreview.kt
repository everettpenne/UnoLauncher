package com.jake.duolauncher.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jake.duolauncher.R

/** What the settings page's live keyboard is drawn with: the settings that change how the keyboard looks or feels. */
internal data class KeyboardLook(
    val layouts: String, val height: Float, val numberRow: Boolean, val emojiKey: Boolean, val keyPreviews: Boolean,
    val glass: Boolean, val oneHanded: Int, val spaceCursor: Boolean, val classic: Boolean = false,
)

/** The real keyboard, drawn on the settings page with the settings as they are now, so a change to its height, layout, keys or glass is
 * seen before it is used; and it types into a line above it (never into an app), with the haptics as set, so it can be felt too.
 */
@Composable
internal fun KeyboardSettingsPreview(look: KeyboardLook, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val ui = remember { KeyboardUiState().apply { showStrip = true; otherKeyboards = false; swipe = false } }
    var text by remember { mutableStateOf("") }
    val layouts = remember(look.layouts) { KeyLayouts.parse(look.layouts) }
    LaunchedEffect(look, layouts) {
        if (ui.layout !in layouts) ui.layout = layouts.first()
        ui.layoutCount = layouts.size; ui.heightScale = look.height; ui.numberRow = look.numberRow; ui.emojiKey = look.emojiKey
        ui.keyPreviews = look.keyPreviews; ui.blurred = look.glass; ui.oneHanded = look.oneHanded; ui.spaceCursor = look.spaceCursor
        ui.classic = look.classic
    }
    LaunchedEffect(Unit) {
        runCatching { ui.emoji = context.assets.open("keyboard/emoji.txt").bufferedReader().useLines { EmojiData.parse(it) } }
        ui.clips = PersonalStore.clips.list()
    }
    val actions = remember {
        object : KeyboardActions {
            override fun type(text0: String, at: Pt?) {
                text += text0
                ui.shift = KeyboardModel.afterLetter(ui.shift)
                ui.page = KeyboardModel.pageAfterTyping(ui.page, text0)
            }
            override fun replaceLast(text0: String) { text = text.dropLast(1) + text0 }
            override fun backspace(held: Int) {
                val n = KeyboardModel.deleteStep(text, held).coerceAtLeast(if (text.isEmpty()) 0 else 1)
                text = text.dropLast(n)
            }
            override fun space() { text += " " }
            override fun enter() { text = "" }
            override fun globe() { if (layouts.size > 1) ui.layout = layouts[(layouts.indexOf(ui.layout) + 1) % layouts.size] }
            override fun shift() { ui.shift = KeyboardModel.tapShift(ui.shift, doubleTap = false) }
            override fun page(target: KeyPage) { ui.page = target; ui.panel = KeyPanel.NONE }
            override fun haptic(kind: HapticKind) { KeyHaptics.fire(kind) }
            override fun pick(suggestion: Suggestion) {}
            override fun moveCursor(delta: Int) {}
            override fun emoji(text0: String) { text += text0 }
            override fun paste() {}
            override fun setOneHanded(mode: Int) { ui.oneHanded = mode }
            override fun chooseLayout(id: String) { KeyLayouts.byId(id)?.let { ui.layout = it }; ui.panel = KeyPanel.NONE }
            override fun panel(panel: KeyPanel) { ui.panel = if (ui.panel == panel) KeyPanel.NONE else panel; ui.tools = false }
            override fun tools(open: Boolean) { ui.tools = open; if (!open) ui.panel = KeyPanel.NONE }
            override fun pasteClip(text0: String) { text += text0; ui.panel = KeyPanel.NONE }
        }
    }
    Column(modifier.testTag("kb-preview"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.kb_try_it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))
            .padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(text.ifEmpty { stringResource(R.string.kb_try_placeholder) } + if (text.isNotEmpty()) "|" else "",
                color = if (text.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("kb-preview-text"))
        }
        KeyboardScreen(ui, actions, inSettings = true)
    }
}
