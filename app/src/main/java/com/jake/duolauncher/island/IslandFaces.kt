package com.jake.duolauncher.island

import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.panel.*
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import android.content.Context
import android.view.KeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import kotlin.math.abs
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur as backdropBlur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import kotlin.math.roundToInt
import kotlin.math.exp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// What the island shows inside it: the collapsed face, the notification card and its reply, the privacy row, the search field. (Split out of DynamicIsland.kt; see that file for the island itself.)

/** The collapsed pill's face: the two slots either side of the camera hole. It reads the live frame, so it follows the pill as it morphs; it is
 * composed only while the island is collapsed or part way open, and is small.
 */
@Composable
internal fun CollapsedFace(frameState: State<IslandFrame>, d: Float, alphaOf: () -> Float, blurOk: Boolean,
    leading: @Composable () -> Unit, trailing: @Composable () -> Unit) {
    val frame = frameState.value
    val hole = frame.hole
    val gap = 4.dp
    val sideways = frame.side != IslandSide.TOP
    Row(Modifier.fillMaxSize().morphFace(alphaOf, blurOk), verticalAlignment = Alignment.CenterVertically) {
                if (hole != null && sideways) {
                    // Camera on a side edge: everything sits on the inward side of the hole, and only when there is room
                    // (an event has widened the pill). The idle pill is just the black capsule and its ring.
                    val inwardDp = (if (frame.side == IslandSide.LEFT) frame.width - hole.right else hole.left) / d
                    if (frame.side == IslandSide.LEFT) Spacer(Modifier.width((hole.right / d).dp + gap))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (inwardDp >= 56f) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            leading(); trailing()
                        }
                    }
                    if (frame.side == IslandSide.RIGHT) Spacer(Modifier.width(((frame.width - hole.left) / d).dp + gap))
                } else if (hole != null) {
                    // In the wide pill the leading glyph sits concentrically with the pill's left end cap (its centre at half the
                    // pill's height), not centred in the whole left slot; the compact pill's left slot is already tight enough.
                    val wide = frame.width > frame.height * 1.05f
                    Box(Modifier.width(((hole.left / d).dp - gap).coerceAtLeast(0.dp)),
                        contentAlignment = if (wide) Alignment.CenterStart else Alignment.Center) {
                        if (wide) Box(Modifier.padding(start = ((GLYPH_INSET_PX / 2f) / d).dp)) { leading() } else leading()
                    }
                    Spacer(Modifier.width((hole.width / d).dp + gap * 2))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { trailing() }
                } else {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        leading(); trailing()
                    }
                }
    }
}

/** A turn-by-turn instruction on the arrival card: the arrow, the distance and the street, set large enough to read while driving. */
@Composable
internal fun RowScope.NavigationCardBody(peek: PeekNotification) {
    peek.icon?.let { icon -> Image(remember(icon) { icon.asImageBitmap() }, null, Modifier.size(NavigationText.ARROW_DP.dp).testTag("island-nav-arrow")) }
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
        Text(peek.title.orEmpty(), color = themeInk, fontSize = NavigationText.DISTANCE_SP.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("island-peek-title"))
        peek.text?.let { Text(it, color = themeInk, fontSize = NavigationText.INSTRUCTION_SP.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("island-peek-text")) }
    }
}

/** The notification that arrived in the last 30 seconds, held on the open island after its card has gone: tap to open it, or use its buttons. */
@Composable
internal fun RecentNotificationRow(peek: PeekNotification, state: IslandState, canReply: Boolean) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(themeInk.copy(alpha = .08f)).padding(10.dp).testTag("island-recent")) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
            if (!sendTapAction(context, peek.open))
                context.packageManager.getLaunchIntentForPackage(peek.packageName)?.let { runCatching { context.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            state.clearRecent(); state.collapse()
        }.testTag("island-recent-open"), verticalAlignment = Alignment.CenterVertically) {
            peek.icon?.let { icon -> Image(remember(icon) { icon.asImageBitmap() }, null, Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))); Spacer(Modifier.width(10.dp)) }
            Column(Modifier.weight(1f)) {
                Text(peek.appLabel, color = themeInk.copy(alpha = .65f), fontSize = 11.sp, maxLines = 1)
                peek.title?.let { Text(it, color = themeInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                peek.text?.let { Text(it, color = themeInk.copy(alpha = .85f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        if (peek.actions.isNotEmpty()) PeekActionsRow(peek, state, canReply)
    }
}

/** Where the island's body ends while it is only part open (a pull, a peek): the face is laid out at its full size and the body clips it, which cuts
 * a line of text in half at the edge. [bodyHeightPx] is the body's height now, [fullHeightPx] its height fully open, [fadePx] how far above the
 * edge the face fades out.
 */
internal class BodyFade(val bodyHeightPx: () -> Float, val fullHeightPx: Float, val fadePx: Float)

/** Fades what is drawn out over the last [BodyFade.fadePx] above the body's bottom edge, only while the body is shorter than its full height. */
internal fun Modifier.fadeAtBodyBottom(fade: BodyFade): Modifier {
    // The layer paint is made once for the modifier's lifetime, not per frame while the body is shorter than its full height.
    val layerPaint = androidx.compose.ui.graphics.Paint()
    // The fade is the same vertical black→transparent ramp over the body's bottom [BodyFade.fadePx]; only where that ramp sits
    // moves as the body grows. Building a four-stop gradient on every frame allocated a Brush and a Shader per animation frame
    // (the one remaining per-frame allocation in the morph path), so the ramp is built once and the mask is drawn as a solid span
    // plus this fixed ramp.
    val fadeBrush = androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent)
    return drawWithContent {
        val bottom = fade.bodyHeightPx()
        if (bottom >= fade.fullHeightPx - 1f) { drawContent(); return@drawWithContent }
        drawIntoCanvas { canvas ->
            val bounds = androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)
            canvas.saveLayer(bounds, layerPaint)
            drawContent()
            val fadeTop = (bottom - fade.fadePx).coerceAtLeast(0f)
            drawRect(Color.Black, topLeft = Offset(0f, 0f), size = Size(size.width, fadeTop),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
            drawRect(fadeBrush, topLeft = Offset(0f, fadeTop), size = Size(size.width, (bottom - fadeTop).coerceAtLeast(0f)),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
            canvas.restore()
        }
    }
}

/** Brings a face in and out of the island as it morphs: it fades, grows from a little smaller and sharpens out of a blur as the body opens around it,
 * and does the reverse as it closes. (iOS does the same: the content is part of the liquid, not a picture laid over it.) With animations off it only fades.
 */
internal fun Modifier.morphFace(alphaOf: () -> Float, blurOk: Boolean, fade: BodyFade? = null): Modifier = then(if (fade == null) Modifier else Modifier.fadeAtBodyBottom(fade)).graphicsLayer {
    val a = alphaOf()
    alpha = a
    val scale = if (blurOk) .94f + .06f * a else 1f
    scaleX = scale; scaleY = scale
    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, .2f)
    renderEffect = if (blurOk && a > .01f && a < .98f) {
        val radius = (1f - a) * 12.dp.toPx()
        androidx.compose.ui.graphics.BlurEffect(radius, radius, androidx.compose.ui.graphics.TileMode.Decal)
    } else null
}

/** The notification's own buttons, under its card. A reply opens a text field in place; any other button is sent as the notification would send it. */
@Composable
internal fun PeekActionsRow(peek: PeekNotification, state: IslandState, canReply: Boolean) {
    val context = LocalContext.current
    var replying by remember(peek) { mutableStateOf<PeekAction?>(null) }
    DisposableEffect(replying) { state.peekEditing = replying != null; onDispose { state.peekEditing = false } }
    val target = replying
    Spacer(Modifier.height(6.dp))
    if (target != null) {
        PeekReplyField(target) { text -> if (sendReply(context, target, text)) UnoFeedback.play(Cue.OPEN, null); state.clearRecent(); state.collapse() }
        return
    }
    Row(Modifier.fillMaxWidth().height(PEEK_ACTIONS_ROW_DP.dp - 6.dp).testTag("island-peek-actions"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        peek.actions.forEach { action ->
            Box(Modifier.weight(1f).fillMaxHeight().clip(IslandThemes.active.controlShape()).background(themeInk.copy(alpha = .14f))
                .clickable {
                    IslandTools.touch()
                    if (action.reply != null) {
                        // Over other apps the island cannot take the keyboard, so a reply opens the notification instead.
                        if (canReply) replying = action else { sendTapAction(context, peek.open); state.clearRecent(); state.collapse() }
                    } else { sendTapAction(context, action.intent); state.clearRecent(); state.collapse() }
                }
                .semantics { role = androidx.compose.ui.semantics.Role.Button; contentDescription = action.title }
                .testTag("island-peek-action"), contentAlignment = Alignment.Center) {
                Text(action.title, color = themeInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}

@Composable
internal fun PeekReplyField(action: PeekAction, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(Modifier.fillMaxWidth().height(REPLY_ROW_DP.dp).clip(IslandThemes.active.controlShape()).background(themeInk.copy(alpha = .14f))
        .padding(start = 14.dp, end = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) Text(action.reply?.label?.toString() ?: "Reply", color = themeInk.copy(alpha = .55f), fontSize = 14.sp, maxLines = 1)
            androidx.compose.foundation.text.BasicTextField(text, { text = it.take(500); IslandTools.touch() },
                Modifier.fillMaxWidth().focusRequester(focus).testTag("island-reply-field"), singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = themeInk, fontSize = 14.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(themeInk),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { if (text.isNotBlank()) onSend(text.trim()) }))
        }
        // A full 44 dp touch target around the 20 dp send glyph, so the reply's most-used control is easy to hit.
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(enabled = text.isNotBlank()) { onSend(text.trim()) }
            .semantics { contentDescription = "Send reply" }.testTag("island-reply-send"), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.Send, null, tint = themeInk.copy(alpha = if (text.isNotBlank()) 1f else .35f), modifier = Modifier.size(20.dp))
        }
    }
}

/** Sends [text] through [action]'s reply input, as the notification's own reply box would. */
internal fun sendReply(context: android.content.Context, action: PeekAction, text: String): Boolean {
    val input = action.reply ?: return false
    return runCatching {
        val fill = android.content.Intent()
        android.app.RemoteInput.addResultsToIntent(arrayOf(input), fill, android.os.Bundle().apply { putCharSequence(input.resultKey, text) })
        android.app.RemoteInput.setResultsSource(fill, android.app.RemoteInput.SOURCE_FREE_FORM_INPUT)
        action.intent.send(context, 0, fill)
    }.isSuccess
}

/** The open island's privacy row: which of the camera, microphone and screen recording are in use, for how long, and a way to review them. */
@Composable
internal fun PrivacyRow(line: PrivacyLine.Line, camera: Boolean, mic: Boolean, recording: Boolean, onReview: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(PRIVACY_ROW_DP.dp - 6.dp).testTag("island-privacy-row"), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (camera) Icon(Icons.Rounded.Videocam, null, tint = IosGreen, modifier = Modifier.size(18.dp))
            if (mic) Icon(Icons.Rounded.Mic, null, tint = PRIVACY_MIC, modifier = Modifier.size(18.dp))
            if (recording) Icon(Icons.Rounded.FiberManualRecord, null, tint = IslandSymbol.RECORDING.tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(line.title, color = themeInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (line.subtitle.isNotEmpty()) Text(line.subtitle, color = themeInk.copy(alpha = .72f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.padding(start = 8.dp).height(30.dp).clip(IslandThemes.active.controlShape()).background(themeInk.copy(alpha = .14f))
            .clickable(onClick = onReview).padding(horizontal = 12.dp).semantics { contentDescription = "Review privacy settings" }.testTag("island-privacy-review"),
            contentAlignment = Alignment.Center) { Text(stringResource(com.jake.duolauncher.R.string.island_review), color = themeInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
    }
}

/** A search field in the open island: type, press Enter, and the words go to the browser's search results. The magnifier opens the
 * full search (apps, contacts, answers) instead.
 */
@Composable
internal fun IslandSearchField(onSubmit: (String) -> Unit, onFullSearch: () -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    Row(modifier.height(44.dp).clip(IslandThemes.active.controlShape()).background(themeInk.copy(alpha = .14f)).padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(IslandThemes.active.controlShape()).clickable(onClick = onFullSearch).semantics { contentDescription = "Search apps and more" },
            contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Search, null, tint = themeInk.copy(alpha = .85f), modifier = Modifier.size(18.dp)) }
        Box(Modifier.weight(1f).padding(start = 2.dp), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) Text(stringResource(com.jake.duolauncher.R.string.search_the_web), color = themeInk.copy(alpha = .55f), fontSize = 14.sp, maxLines = 1)
            androidx.compose.foundation.text.BasicTextField(text, { text = it.take(200) }, Modifier.fillMaxWidth().testTag("island-search-field"),
                singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = themeInk, fontSize = 14.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(themeInk),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                    val query = text.trim()
                    if (query.isNotEmpty()) { onSubmit(query); text = ""; keyboard?.hide() }
                }))
        }
    }
}
