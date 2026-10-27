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
import androidx.compose.material.icons.rounded.MusicNote
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

// The collapsed island's live activity slots and capsule glyphs, a Live Update's glyph and card, and the helpers that animate them. (Split out of DynamicIsland.kt; see that file for the island itself.)

/** What the pill's glyph and detail do when the activity in front changes: the old one shrinks and fades away, the new one grows in a beat later,
 * without the layout resizing under them. With animations off, nothing animates. */
internal fun pillSwap(animate: Boolean): androidx.compose.animation.ContentTransform =
    if (!animate) androidx.compose.animation.ContentTransform(androidx.compose.animation.EnterTransition.None, androidx.compose.animation.ExitTransition.None)
    else androidx.compose.animation.ContentTransform(
        targetContentEnter = androidx.compose.animation.fadeIn(tween(170, delayMillis = 70)) + androidx.compose.animation.scaleIn(tween(170, delayMillis = 70), initialScale = .7f),
        initialContentExit = androidx.compose.animation.fadeOut(tween(110)) + androidx.compose.animation.scaleOut(tween(110), targetScale = .7f),
        sizeTransform = null)

/** Blurs content while it is entering or leaving an [androidx.compose.animation.AnimatedContent], so a swap melts from one thing to the next instead of cutting
 *  (HyperIsland's blur-in). Playful theme only, and only where the system can blur a layer (Android 12 and later). */
@Composable
internal fun Modifier.swapBlur(scope: androidx.compose.animation.AnimatedVisibilityScope, enabled: Boolean): Modifier {
    if (!enabled || android.os.Build.VERSION.SDK_INT < 31) return this
    val radius by scope.transition.animateFloat({ tween(200) }, label = "swap blur") { state ->
        if (state == androidx.compose.animation.EnterExitState.Visible) 0f else 9f
    }
    return this.graphicsLayer {
        if (radius > .1f) renderEffect = androidx.compose.ui.graphics.BlurEffect(radius * density, radius * density, androidx.compose.ui.graphics.TileMode.Decal)
    }
}

/** The things the island shows, plus the ones just gone that are still playing their exit; [gone] says an exit has finished. */
internal class Lingered<T>(val held: List<Held<T>>, val gone: (T) -> Unit)

/** [current] as [Held] entries, keeping each thing that leaves until its exit calls [Lingered.gone]. With [keepGhosts] false (the island is
 * open or busy, so nothing is shown to animate) leavers are dropped at once. */
@Composable
internal fun <T> rememberLingering(current: List<T>, keepGhosts: Boolean): Lingered<T> {
    val box = remember { arrayOfNulls<List<Held<T>>>(1) }
    var tick by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val observed = tick
    val held = Lingering.merge(if (keepGhosts) box[0] ?: emptyList() else emptyList(), current)
    box[0] = held
    return Lingered(held) { item -> box[0] = Lingering.drop(box[0] ?: emptyList(), item); tick++ }
}

/** One text size for the detail beside the camera, whichever activity it is: the timer, the call, the stopwatch, a Live Update. */
internal const val SLOT_TEXT_SP = 12
internal val PRIVACY_MIC = Color(0xFFFF9F0A)
/** A detached capsule's body: a stadium with fully rounded ends, like the pill but small. */
internal val capsuleShape = RoundedCornerShape(percent = 50)
/** An app icon or thumbnail on the pill is this share of the pill's height, centred in its rounded end, as iOS's compact activities
 * are. It was the height less 14 px: on a Pixel 10a that filled the pill to within 2 dp of its edge, so a call or timer glyph looked
 * oversized next to its small trailing text and crowded the pill's end. */
internal const val GLYPH_FRACTION = .62f
/** The smallest a glyph circle may be, in px, whatever the pill's height. */
internal const val MIN_GLYPH_PX = 12f

/**
 * The words a live activity shows beside the camera (the call's length, the timer's time left, the stopwatch, a Live Update's status or
 * title), or null when it shows none (music shows bars; the privacy marks are dots). One source for drawing it and for measuring it, so
 * the pill is always sized for what it says (see IslandPillWidth).
 */
internal fun liveDetailText(kind: LiveKind, clockMs: Long, update: LiveUpdate?, liveCompactLevel: Int, compact: Boolean = false): String? =
    liveDetailFull(kind, clockMs, update, liveCompactLevel)?.let { if (compact) CompactText.shorten(it) else it }

private fun liveDetailFull(kind: LiveKind, clockMs: Long, update: LiveUpdate?, liveCompactLevel: Int): String? = when (kind) {
    LiveKind.CALL -> NotificationFeed.ongoingCall?.let { IslandClock.countdown((clockMs - it.startedAt).coerceAtLeast(0L)) }
        ?: if (LiveKind.CALL in IslandDebug.forced) "1:23" else ""
    LiveKind.TIMER -> if (IslandTools.timerActive || LiveKind.TIMER !in IslandDebug.forced)
        IslandClock.countdown(IslandClock.remainingMs(clockMs, IslandTools.timerEndAt)) else "9:59"
    LiveKind.STOPWATCH -> if (IslandTools.swRunning || LiveKind.STOPWATCH !in IslandDebug.forced)
        IslandClock.stopwatch(IslandTools.elapsedMs(clockMs)) else IslandClock.stopwatch(12_300L)
    LiveKind.UPDATE -> update?.let { LiveCompactText.choose(liveCompactLevel, LiveUpdateLogic.glance(it, clockMs), it.title) }
    else -> null
}

/** One live activity as it appears in a side of the collapsed pill: its small glyph, or its detail (text or bars). */
@Composable
internal fun LiveSlot(kind: LiveKind, show: SlotShow, clockMs: Long, playing: Boolean, tint: Color,
    art: androidx.compose.ui.graphics.ImageBitmap?, multi: Boolean, liveCompactLevel: Int, update: LiveUpdate? = null,
    glyphSize: androidx.compose.ui.unit.Dp, plain: Boolean = false, compact: Boolean = false) {
    val glyph = show == SlotShow.GLYPH
    when (kind) {
        LiveKind.CAMERA -> if (glyph) Icon(Icons.Rounded.Videocam, "Camera in use", tint = IosGreen,
            modifier = Modifier.size(glyphSize).testTag("island-camera"))
            else Text(stringResource(com.jake.duolauncher.R.string.camera), color = IosGreen, style = UnoType.caption1.tight, fontWeight = FontWeight.SemiBold, maxLines = 1)
        LiveKind.MIC -> if (glyph) Icon(Icons.Rounded.Mic, "Microphone in use", tint = PRIVACY_MIC,
            modifier = Modifier.size(glyphSize).testTag("island-mic"))
            else Text(stringResource(com.jake.duolauncher.R.string.mic), color = PRIVACY_MIC, style = UnoType.caption1.tight, fontWeight = FontWeight.SemiBold, maxLines = 1)
        LiveKind.RECORDING -> if (glyph) Icon(Icons.Rounded.FiberManualRecord, "Screen is being recorded", tint = IslandSymbol.RECORDING.tint,
            modifier = Modifier.size(glyphSize).testTag("island-recording"))
            else Text(stringResource(com.jake.duolauncher.R.string.recording), color = IslandSymbol.RECORDING.tint, style = UnoType.caption1.tight, fontWeight = FontWeight.SemiBold, maxLines = 1)
        LiveKind.CALL -> if (glyph) Icon(Icons.Rounded.Call, null, tint = IosGreen, modifier = Modifier.size(glyphSize))
            // How long the call has lasted, in green, like the other things that count time (the timer, the stopwatch). The caller's name
            // is on the open island's call card: a name cut to five letters says little, a clock says how long.
            else Text(liveDetailText(kind, clockMs, update, liveCompactLevel, compact).orEmpty(), color = IosGreen,
                fontSize = SLOT_TEXT_SP.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                modifier = Modifier.padding(end = 4.dp).testTag("island-call"))
        LiveKind.TIMER -> if (glyph) Icon(Icons.Rounded.Timer, null, tint = IslandThemes.active.timer, modifier = Modifier.size(glyphSize))
            else Text(liveDetailText(kind, clockMs, update, liveCompactLevel, compact).orEmpty(), color = IslandThemes.active.timer,
                fontSize = SLOT_TEXT_SP.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.testTag("island-timer"))
        LiveKind.STOPWATCH -> if (glyph) Icon(Icons.Rounded.Timer, null, tint = themeInk, modifier = Modifier.size(glyphSize))
            else Text(liveDetailText(kind, clockMs, update, liveCompactLevel, compact).orEmpty(), color = themeInk,
                fontSize = SLOT_TEXT_SP.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.testTag("island-stopwatch"))
        LiveKind.UPDATE -> if (update != null) {
            val driving = update.source == ActivitySource.NAVIGATION
            if (glyph) UpdateGlyph(update, glyphSize, plain)
            else {
                // The words beside the glyph, as the person chose: none (glyph alone), the short status, or the title.
                val text = liveDetailText(kind, clockMs, update, liveCompactLevel, compact)
                if (text != null) Text(text, color = updateTint(update), fontSize = (if (driving) NavigationText.PILL_SP else SLOT_TEXT_SP).sp,
                    fontWeight = if (driving) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 4.dp).testTag("island-live-update"))
            }
        }
        LiveKind.MEDIA -> if (glyph) {
            // The artwork when there is any, as a circle sized to sit concentrically inside the pill; otherwise the bars stand in.
            if (art != null && !plain) Image(art, null, Modifier.size(glyphSize).clip(CircleShape))
            else if (multi) EqualizerBars(if (playing) tint else tint.copy(alpha = .55f), animate = playing)
            // No artwork: a note, so the pill is not empty on one side with only the bars on the other.
            else Icon(androidx.compose.material.icons.Icons.Rounded.MusicNote, null, Modifier.size(glyphSize * .85f),
                tint = if (playing) tint else tint.copy(alpha = .7f))
        } else {
            // Stays while a paused player can still be resumed, but only moves while audio is playing.
            EqualizerBars(if (playing) tint else tint.copy(alpha = .55f),
                Modifier.testTag(if (playing) "island-playing" else "island-paused"), animate = playing)
        }
    }
}




/** The colour a Live Update asks for, or the island's green when it asks for none or one too dark to read on black. */
internal fun updateTint(update: LiveUpdate): Color {
    val c = Color(update.color)
    val requested = if (update.color == 0 || c.alpha < .5f || (c.red * .299f + c.green * .587f + c.blue * .114f) < .35f) null else c.copy(alpha = 1f)
    return IslandThemes.active.accent(update.packageName, requested)
}

/** " 42%" after a card's status when the theme asks for percentages and the update has measurable progress; otherwise nothing. */
internal fun percentage(update: LiveUpdate): String {
    if (!IslandThemes.active.showPercentage) return ""
    val f = LiveUpdateLogic.fraction(update.progress, update.progressMax, update.indeterminate) ?: return ""
    return " ${(f * 100).toInt().coerceIn(0, 100)}%"
}

/** The app's small icon in a soft circle of its colour: what stands in for the app on the pill and the card. */
@Composable
internal fun UpdateGlyph(update: LiveUpdate, size: androidx.compose.ui.unit.Dp, plain: Boolean = false) {
    val tint = updateTint(update)
    val fraction = LiveUpdateLogic.fraction(update.progress, update.progressMax, update.indeterminate)
    val ringColour = IslandThemes.active.progress(tint, fraction != null && fraction >= 1f)
    Box(Modifier.size(size).clip(IslandThemes.active.iconShape.shape()).background(tint.copy(alpha = .28f))
        // A download or other task with a progress bar wears it as a ring round its icon.
        .drawWithContent {
            drawContent()
            if (fraction != null) {
                val stroke = 2.2.dp.toPx()
                drawArc(ringColour, -90f, 360f * fraction, false, androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
        }, contentAlignment = Alignment.Center) {
        val icon = remember(update.icon) { update.icon?.asImageBitmap() }
        // A navigation instruction shows its turn arrow, in the arrow's own colours, instead of the maps app's logo.
        val arrow = remember(update.largeIcon) { update.largeIcon?.asImageBitmap() }
        if (update.source == ActivitySource.NAVIGATION && arrow != null) Image(arrow, null, Modifier.size(size * .92f).testTag("island-update-arrow"))
        // A plain glyph (an extra capsule) is the activity's own symbol, not the app's logo: the arrow above is the one picture that says something.
        else if (plain) Icon(if (update.source == ActivitySource.PROGRESS) Icons.Rounded.Refresh else Icons.Rounded.RssFeed, null, tint = tint, modifier = Modifier.size(size * .6f))
        else if (icon != null) Image(icon, null, Modifier.size(size * .66f), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint))
        else Icon(Icons.Rounded.RssFeed, null, tint = tint, modifier = Modifier.size(size * .6f))
    }
}

/** The open island's card for a Live Update: the app's icon, its title and text, its short status or clock, and a progress bar. */
@Composable
internal fun UpdateCard(update: LiveUpdate, clockMs: Long, onOpen: () -> Unit) {
    val tint = updateTint(update)
    Column(Modifier.fillMaxWidth().clip(islandCardShape()).clickable(onClick = onOpen).testTag("island-update")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val navigating = update.source == ActivitySource.NAVIGATION && update.largeIcon != null
            // A theme can put the turn arrow on the right and the distance on the left (HyperBridge's "swap sides").
            val swapped = navigating && IslandThemes.active.swapNavigation
            if (!swapped) { UpdateGlyph(update, if (navigating) NavigationText.ARROW_DP.dp else 34.dp); Spacer(Modifier.width(if (navigating) 14.dp else 10.dp)) }
            Column(Modifier.weight(1f)) {
                // A turn-by-turn instruction is read at a glance from a dashboard: the distance is set very large and the street under it large too.
                Text(update.title, color = if (navigating) IslandThemes.active.navigationColor ?: themeInk else themeInk,
                    fontSize = if (navigating) NavigationText.DISTANCE_SP.sp else 15.sp, fontWeight = if (navigating) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(update.text ?: update.appLabel, color = if (navigating) themeInk else themeInk.copy(alpha = .72f),
                    fontSize = if (navigating) NavigationText.INSTRUCTION_SP.sp else 12.sp, fontWeight = if (navigating) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = if (navigating) 2 else 1, overflow = TextOverflow.Ellipsis)
            }
            // A navigation card already leads with the distance, so it does not repeat it on the right.
            if (!navigating) Text(LiveUpdateLogic.glance(update, clockMs) + percentage(update), color = tint, style = UnoType.subheadline.tight, fontWeight = FontWeight.SemiBold, maxLines = 1,
                modifier = Modifier.padding(start = 8.dp))
            if (swapped) { Spacer(Modifier.width(10.dp)); UpdateGlyph(update, NavigationText.ARROW_DP.dp) }
        }
        LiveUpdateLogic.fraction(update.progress, update.progressMax, update.indeterminate)?.let { f ->
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(themeInk.copy(alpha = .18f)).testTag("island-update-progress")) {
                Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(IslandThemes.active.progress(tint, f >= 1f)))
            }
        }
    }
}
