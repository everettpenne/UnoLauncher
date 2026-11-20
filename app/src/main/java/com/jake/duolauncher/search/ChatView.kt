package com.jake.duolauncher.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jake.duolauncher.R
import com.jake.duolauncher.appearance.Corner
import com.jake.duolauncher.appearance.UnoType

/**
 * Every size in the conversation, in dp. None is a number picked for one phone: each comes from what this phone reports about itself
 * ([ChatLayout.metrics]), so the layout holds on a small phone, a tablet, a large text size and a square-cornered screen alike.
 */
internal data class ChatMetrics(
    /** Where text starts on the leading side: the header, the replies and what is typed all begin on this one line. */
    val edge: Float,
    /** Where shapes end on both sides: bubbles, the live cards, the field and its button share this margin. */
    val inset: Float,
    /** The smallest thing a finger is asked to hit, as the system defines it. */
    val touch: Float,
    val bubbleMax: Float, val replyMax: Float,
    val bubblePadH: Float, val bubblePadV: Float, val bubbleCorner: Float,
    /** Between a question and its answer, and between one exchange and the next: the second is wider, so exchanges read as pairs. */
    val gapWithin: Float, val gapBetween: Float,
    val fieldHeight: Float, val sendSize: Float, val icon: Float,
    val grabberWidth: Float, val grabberThick: Float,
    /** How far the handle is pulled before letting go opens the conversation. */
    val commitPull: Float,
) {
    /** The strip at the bottom that a swipe up closes from: the field's whole row, down to the body's edge. */
    val closeZone: Float get() = fieldHeight + 2 * inset
}

internal object ChatLayout {
    /** How far in from a rounded corner's box its curve has gone by the diagonal: content this far in clears the corner. */
    private const val CORNER_REACH = 0.2929f   // 1 - 1/sqrt(2)
    /** A comfortable line of reading text is 45 to 75 characters. A character averages about half an em and a line is about 1.3 em
     *  tall, so in line-heights that is roughly 17 to 29. Bubbles sit at the low end and replies toward the middle. */
    private const val BUBBLE_LINES_WIDE = 20f
    private const val REPLY_LINES_WIDE = 26f

    /**
     * [widthDp]: the body's width. [lineDp]: the height of a line of body text at the person's text size. [cornerDp]: the radius of the
     * body's corners (which follow the display's). [touchDp]: the system's minimum touch target. Everything else follows from these.
     */
    fun metrics(widthDp: Float, lineDp: Float, cornerDp: Float, touchDp: Float): ChatMetrics {
        val u = lineDp.coerceAtLeast(1f)
        val touch = touchDp.coerceAtLeast(u)
        // The field is as tall as a finger needs, or as two lines of the person's text if that is more.
        val field = maxOf(touch, 2f * u)
        // Shapes sit far enough in that the field's round end is concentric with the body's corner; on a square-cornered body, half a line.
        val inset = maxOf(cornerDp - field / 2f, u / 2f).coerceAtMost(widthDp / 8f)
        // Text clears the corner's curve, and is never tighter to the side than the shapes are plus the padding inside one.
        val padH = 0.65f * u
        val edge = maxOf(cornerDp * CORNER_REACH + u / 2f, inset + padH).coerceAtMost(widthDp / 5f)
        val padV = 0.35f * u
        val across = (widthDp - edge - inset).coerceAtLeast(touch)
        return ChatMetrics(
            edge = edge, inset = inset, touch = touch,
            // A bubble leaves at least a finger's width clear on the far side, so whose words they are is never in doubt.
            bubbleMax = minOf(across - touch, BUBBLE_LINES_WIDE * u).coerceAtLeast(touch),
            replyMax = minOf(across - u, REPLY_LINES_WIDE * u).coerceAtLeast(touch),
            bubblePadH = padH, bubblePadV = padV,
            // Half a one-line bubble's height: one line is a pill, and more lines keep the same curve.
            bubbleCorner = (u + 2f * padV) / 2f,
            gapWithin = 0.3f * u, gapBetween = 0.9f * u,
            fieldHeight = field, sendSize = field - 0.5f * u, icon = u,
            grabberWidth = 0.75f * touch, grabberThick = 0.18f * u,
            commitPull = touch,
        )
    }

    /** How long between two turns before the time is said again. Nearer than this they read as one sitting. */
    const val STAMP_GAP_MS = 5 * 60_000L

    /** Whether the turn at [index] carries its time: the last one always, and any after which the conversation paused. */
    fun stamped(turns: List<ChatTurn>, index: Int): Boolean =
        index == turns.lastIndex || turns[index + 1].atMs - turns[index].atMs >= STAMP_GAP_MS
}

/** Deciding what a pull on the conversation's handle does. Pure, so it is unit-tested. */
internal object ChatPull {
    /** Whether the conversation is open after a pull of [dragDp] (down is positive) let go while it was [open]: past [commitDp], it changes. */
    fun settles(open: Boolean, dragDp: Float, commitDp: Float): Boolean = if (open) dragDp > -commitDp else dragDp >= commitDp

    /** How much the card has grown under a finger pulling the handle down while shut: one for one, and nothing for a pull up. */
    fun grownDp(open: Boolean, dragDp: Float): Float = if (open) 0f else dragDp.coerceAtLeast(0f)

    /** When a turn was, in a few words: "now", "5 min ago", "3 h ago", "yesterday", "12 days ago". */
    fun ago(atMs: Long, nowMs: Long): String {
        val minutes = ((nowMs - atMs) / 60_000L).coerceAtLeast(0L)
        return when {
            minutes < 1 -> "now"
            minutes < 60 -> "$minutes min ago"
            minutes < 24 * 60 -> "${minutes / 60} h ago"
            minutes < 48 * 60 -> "yesterday"
            else -> "${minutes / (24 * 60)} days ago"
        }
    }
}

/**
 * The handle under search's results: pull it down (or tap it) and the card springs open into the conversation. [onPull] is told how far
 * the finger has pulled while it is held, so the card grows under it.
 */
@Composable
internal fun ChatHandle(ink: Color, m: ChatMetrics, onPull: (Float) -> Unit, onOpen: () -> Unit) {
    val label = stringResource(R.string.spotlight_chat_open)
    Column(Modifier.fillMaxWidth().heightIn(min = m.touch.dp).clip(Corner.medium)
        .clickable(role = Role.Button, onClickLabel = label) { onPull(0f); onOpen() }
        .pointerInput(Unit) {
            var dragged = 0f
            detectVerticalDragGestures(
                onDragStart = { dragged = 0f },
                onDragEnd = { onPull(0f); if (ChatPull.settles(false, dragged / density, m.commitPull)) onOpen() },
                onDragCancel = { onPull(0f) },
            ) { change, dy -> change.consume(); dragged += dy; onPull(ChatPull.grownDp(false, dragged / density)) }
        }
        .testTag("spotlight-chat-handle"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(m.grabberWidth.dp, m.grabberThick.dp).background(ink.copy(alpha = .35f), Corner.pill))
        Text(label, Modifier.padding(top = m.grabberThick.dp), color = ink.copy(alpha = .5f), style = UnoType.caption2)
    }
}

/**
 * The conversation's header, which sits in search's bar where the field was: the list of earlier chats, this one's name, a new chat, and
 * delete. Deleting takes a second tap on the same control, so a slip does not lose a conversation.
 */
@Composable
internal fun ChatHeader(ink: Color, m: ChatMetrics, listing: Boolean, onListing: (Boolean) -> Unit) {
    val context = LocalContext.current
    val chat = ChatStore.active
    var armed by remember(chat?.id, listing) { mutableStateOf(false) }
    // The glyphs are a line of text tall and sit in boxes a finger can hit; the row is inset so the first word starts on the text
    // edge and the last glyph ends where the shapes below it end.
    val slack = ((m.touch - m.icon) / 2f).coerceAtLeast(0f)
    Row(Modifier.fillMaxWidth().padding(start = m.edge.dp, end = (m.inset - slack).coerceAtLeast(0f).dp).testTag("spotlight-chat-header"),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.heightIn(min = m.touch.dp).clip(Corner.medium).clickable(role = Role.Button) { onListing(!listing) }.testTag("spotlight-chat-list"),
            contentAlignment = Alignment.CenterStart) {
            Text(stringResource(if (listing) R.string.spotlight_chat_back else R.string.spotlight_chat_all), color = ink,
                style = UnoType.subheadline.copy(fontWeight = FontWeight.SemiBold))
        }
        Text(if (listing) "" else chat?.title ?: stringResource(R.string.spotlight_chat_new), Modifier.weight(1f).padding(horizontal = m.bubblePadH.dp),
            color = ink.copy(alpha = .6f), style = UnoType.footnote, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!listing) {
            Box(Modifier.size(m.touch.dp).clip(Corner.medium).clickable(role = Role.Button) { ChatStore.startNew() }.testTag("spotlight-chat-new"),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, stringResource(R.string.spotlight_chat_new), Modifier.size(m.icon.dp), tint = ink.copy(alpha = .8f))
            }
            if (chat != null) {
                if (armed) Box(Modifier.heightIn(min = m.touch.dp).clip(Corner.medium).clickable(role = Role.Button) { ChatStore.delete(context, chat.id) }
                    .padding(horizontal = m.bubblePadH.dp).testTag("spotlight-chat-delete-sure"), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.spotlight_chat_delete_sure), color = DELETE, style = UnoType.caption1.copy(fontWeight = FontWeight.SemiBold))
                }
                else Box(Modifier.size(m.touch.dp).clip(Corner.medium).clickable(role = Role.Button) { armed = true }.testTag("spotlight-chat-delete"),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.spotlight_chat_delete), Modifier.size(m.icon.dp), tint = ink.copy(alpha = .8f))
                }
            }
        }
    }
}

/**
 * The conversation: what was said and what came back, oldest at the top and the newest at the bottom, just above where the next thing
 * is typed, with the turn in flight (the same cards search shows, [live]) last. Or, [listing], the earlier conversations to open or delete.
 */
@Composable
internal fun ChatBody(modifier: Modifier, ink: Color, m: ChatMetrics, listing: Boolean, onListing: (Boolean) -> Unit, liveFor: String?, live: LazyListScope.() -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { ChatStore.load(context); AssistantLog.load(context) }
    // What a first tap on a delete armed: a conversation's id, or ALL. A second tap on the same thing deletes.
    var armed by remember(listing) { mutableStateOf<Long?>(null) }
    val chat = ChatStore.active
    val now = remember(ChatStore.chats, listing) { System.currentTimeMillis() }
    if (listing) {
        val chats = ChatLog.listed(ChatStore.chats)
        val side = androidx.compose.foundation.layout.PaddingValues(horizontal = m.inset.dp)
        LazyColumn(modifier.fillMaxWidth().testTag("spotlight-chats"), contentPadding = side, verticalArrangement = Arrangement.spacedBy(m.gapWithin.dp)) {
            if (chats.isEmpty()) item("none") { Text(stringResource(R.string.spotlight_chat_none), Modifier.padding(start = (m.edge - m.inset).dp, top = m.gapBetween.dp), color = ink.copy(alpha = .6f), style = UnoType.body) }
            items(chats, key = { it.id }) { c ->
                Row(Modifier.animateItem().fillMaxWidth().heightIn(min = m.touch.dp).clip(Corner.medium).clickable { ChatStore.open(c.id); onListing(false) }
                    .padding(start = (m.edge - m.inset).dp).testTag("spotlight-chat-row"), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = m.gapWithin.dp)) {
                        Text(c.title, color = ink, style = UnoType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${ChatPull.ago(c.lastMs, now)} · ${c.turns.size}", color = ink.copy(alpha = .5f), style = UnoType.caption2)
                    }
                    if (armed == c.id) Box(Modifier.heightIn(min = m.touch.dp).clip(Corner.medium).clickable(role = Role.Button) { ChatStore.delete(context, c.id); armed = null }
                        .padding(horizontal = m.bubblePadH.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.spotlight_chat_delete_sure), color = DELETE, style = UnoType.caption1.copy(fontWeight = FontWeight.SemiBold))
                    }
                    else Box(Modifier.size(m.touch.dp).clip(Corner.medium).clickable(role = Role.Button) { armed = c.id }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.spotlight_chat_delete), Modifier.size(m.icon.dp), tint = ink.copy(alpha = .6f))
                    }
                }
            }
            if (chats.isNotEmpty()) item("all") {
                Box(Modifier.padding(top = m.gapBetween.dp).fillMaxWidth().heightIn(min = m.touch.dp).clip(Corner.medium)
                    .clickable(role = Role.Button) { if (armed == ALL) { ChatStore.deleteAll(context); armed = null } else armed = ALL }
                    .padding(start = (m.edge - m.inset).dp).testTag("spotlight-chat-delete-all"), contentAlignment = Alignment.CenterStart) {
                    Text(stringResource(if (armed == ALL) R.string.spotlight_chat_delete_all_sure else R.string.spotlight_chat_delete_all, chats.size), color = DELETE,
                        style = UnoType.subheadline.copy(fontWeight = FontWeight.SemiBold))
                }
            }
        }
        return
    }
    val turns = chat?.turns.orEmpty()
    // The newest turn is drawn by the live cards while it is the one on screen (they keep Undo, copy and the letter cascade).
    val past = if (liveFor != null && turns.lastOrNull()?.said == liveFor) turns.dropLast(1) else turns
    // It opens at the newest (no flash of the oldest first), and the newest is kept in view as turns arrive and as the keyboard
    // comes and goes: at the bottom, just above the field.
    val list = remember(chat?.id) { androidx.compose.foundation.lazy.LazyListState(firstVisibleItemIndex = past.size * 2 + 8) }
    LaunchedEffect(past.size, liveFor, chat?.id, list.layoutInfo.viewportSize) {
        val count = list.layoutInfo.totalItemsCount
        if (count > 0) list.scrollToItem(count - 1, scrollOffset = 100_000)
    }
    // Bottom-weighted: a short conversation sits against the field, not at the top of an empty body. No fixed gap between items: each
    // item brings its own space above it, so a question sits close under nothing and its answer close under it.
    LazyColumn(modifier.fillMaxWidth().testTag("spotlight-chat"), state = list,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = m.inset.dp, end = m.inset.dp, bottom = m.gapWithin.dp),
        verticalArrangement = Arrangement.Bottom) {
        if (past.isEmpty() && liveFor == null) item("empty") {
            Text(stringResource(R.string.spotlight_chat_empty), Modifier.fillMaxWidth().padding(bottom = m.gapBetween.dp), color = ink.copy(alpha = .6f), style = UnoType.body,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        past.forEachIndexed { i, turn ->
            item("said-$i-${turn.atMs}") { Said(Modifier.animateItem(), turn.said.lineSequence().last().removePrefix("Answer: "), ink, m, first = i == 0) }
            item("reply-$i-${turn.atMs}") {
                // The reply's words start on the text edge, the same line the header and the field's text start on.
                Column(Modifier.animateItem().padding(start = (m.edge - m.inset).dp, top = m.gapWithin.dp).widthIn(max = m.replyMax.dp).testTag("spotlight-chat-reply")) {
                    Text(turn.reply, color = ink.copy(alpha = if (turn.kind == ChatTurn.Kind.REFUSED) .7f else 1f), style = UnoType.body)
                    // The time is said where it means something: after a pause, and on the last.
                    if (ChatLayout.stamped(past, i)) Text(ChatPull.ago(turn.atMs, now), color = ink.copy(alpha = .4f), style = UnoType.caption2)
                    // A turn the log has can be judged here too, long after its card has gone (its entry has the turn's own time).
                    RateButtons(turn.atMs, ink, Modifier.offset(x = (-(m.touch - m.icon) / 2f).dp), touchDp = m.touch, iconDp = m.icon)
                }
            }
        }
        if (liveFor != null) {
            item("live-said") { Said(Modifier.animateItem(), liveFor, ink, m, first = past.isEmpty()) }
            item("live-gap") { Spacer(Modifier.height(m.gapWithin.dp)) }
            live()
        }
    }
}

/**
 * The thumbs under a response: up for one that acted correctly or helpfully, down for one that was not what was meant. They are there
 * only while the assistant log is on and has this response in it ([id] is its entry's), since the log is where the verdict goes; a
 * second tap on a thumb takes it back. One place holds the verdict ([AssistantLog]), so the card in search and the same turn in the
 * conversation always agree.
 */
@Composable
internal fun RateButtons(id: Long?, ink: Color, modifier: Modifier = Modifier, touchDp: Float = 40f, iconDp: Float = 18f) {
    val entry = AssistantLog.entry(id) ?: return
    val context = LocalContext.current
    Row(modifier.testTag("assistant-rate"), verticalAlignment = Alignment.CenterVertically) {
        @Composable fun thumb(value: Int, on: androidx.compose.ui.graphics.vector.ImageVector, off: androidx.compose.ui.graphics.vector.ImageVector, label: Int, tag: String) {
            val chosen = entry.rating == value
            Box(Modifier.size(touchDp.dp).clip(Corner.medium)
                .clickable(role = Role.Button, onClickLabel = stringResource(label)) { AssistantLog.rate(context, entry.id, value) }
                .testTag(if (chosen) "$tag-on" else tag), contentAlignment = Alignment.Center) {
                Icon(if (chosen) on else off, stringResource(label), Modifier.size(iconDp.dp), tint = ink.copy(alpha = if (chosen) 1f else .55f))
            }
        }
        thumb(1, Icons.Rounded.ThumbUp, Icons.Outlined.ThumbUp, R.string.assistant_rate_up, "assistant-rate-up")
        thumb(-1, Icons.Rounded.ThumbDown, Icons.Outlined.ThumbDown, R.string.assistant_rate_down, "assistant-rate-down")
    }
}

/** What the person said: a bubble against the trailing edge, no wider than reads as theirs. */
@Composable
private fun Said(modifier: Modifier, text: String, ink: Color, m: ChatMetrics, first: Boolean) {
    Row(modifier.fillMaxWidth().padding(top = if (first) 0.dp else m.gapBetween.dp), horizontalArrangement = Arrangement.End) {
        Text(text, color = ink, style = UnoType.body,
            modifier = Modifier.widthIn(max = m.bubbleMax.dp).background(ink.copy(alpha = .16f), com.jake.duolauncher.appearance.ContinuousShape(m.bubbleCorner.dp))
                .padding(horizontal = m.bubblePadH.dp, vertical = m.bubblePadV.dp).testTag("spotlight-chat-said"))
    }
}

/**
 * Where the next thing is typed, at the bottom of the conversation, just above the keyboard: a pill with the assistant's light round it
 * while the model works, and a send button that lights when there is something to send. Sending empties it.
 */
@Composable
internal fun ChatComposer(text: String, onText: (String) -> Unit, ink: Color, m: ChatMetrics, onSend: () -> Unit) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val ready = text.isNotBlank()
    // The button is inset by the same amount all round, so it is concentric with the field's end; the text starts on the text edge.
    val ring = (m.fieldHeight - m.sendSize) / 2f
    val shape = com.jake.duolauncher.appearance.ContinuousShape((m.fieldHeight / 2f).dp)
    Row(Modifier.fillMaxWidth().heightIn(min = m.fieldHeight.dp).background(ink.copy(alpha = .10f), shape)
        .assistantLight(rememberAssistantMood(), shape).padding(start = (m.edge - m.inset).dp, end = ring.dp), verticalAlignment = Alignment.Bottom) {
        Box(Modifier.weight(1f).heightIn(min = m.fieldHeight.dp).padding(vertical = m.bubblePadV.dp), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) Text(stringResource(R.string.spotlight_chat_field), color = ink.copy(alpha = .55f), style = UnoType.body, maxLines = 1)
            androidx.compose.foundation.text.BasicTextField(text, onText,
                // Enter on a keyboard with keys sends, as the on-screen Send does; with Shift it is a new line.
                Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { event ->
                    if (event.key == Key.Enter && !event.isShiftPressed) { if (event.type == KeyEventType.KeyDown) onSend(); true } else false
                }.testTag("spotlight-chat-field"), textStyle = UnoType.body.copy(color = ink),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(ink), maxLines = 4,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { onSend() }))
        }
        Box(Modifier.padding(bottom = ring.dp).size(m.sendSize.dp).clip(Corner.pill).background(if (ready) ink else ink.copy(alpha = .14f))
            .clickable(enabled = ready, role = Role.Button, onClickLabel = stringResource(R.string.spotlight_chat_send), onClick = onSend)
            .testTag("spotlight-chat-send"), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ArrowUpward, stringResource(R.string.spotlight_chat_send), Modifier.size(m.icon.dp),
                tint = if (ready) Color.Black else ink.copy(alpha = .5f))
        }
    }
}

private const val ALL = -1L
private val DELETE = Color(0xFFFF6B5E)
