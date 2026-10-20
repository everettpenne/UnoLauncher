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

// The open island's rows: playback, the seek bar, a call, the action buttons, and the music bars. (Split out of DynamicIsland.kt; see that file for the island itself.)

/** Four bars that rise and fall out of step: the iOS "audio is playing" mark. The animation drives
 * a layer scale, so it never recomposes while it runs.
 */
@Composable
internal fun EqualizerBars(color: Color, modifier: Modifier = Modifier, barCount: Int = 4, animate: Boolean = true) {
    if (!animate) {
        // Paused: the bars sit at rest, with no animation running at all.
        val rest = listOf(.45f, .85f, .6f, .75f)
        Row(modifier.height(14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            repeat(barCount) { index ->
                Box(Modifier.width(2.5.dp).fillMaxHeight().graphicsLayer { scaleY = rest[index % rest.size] }
                    .background(color, RoundedCornerShape(percent = 50)))
            }
        }
        return
    }
    val transition = rememberInfiniteTransition(label = "equalizer")
    val periods = listOf(520, 380, 610, 450)
    val scales = (0 until barCount).map { index ->
        transition.animateFloat(.28f, 1f,
            infiniteRepeatable(tween(periods[index % periods.size], easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "bar $index")
    }
    Row(modifier.height(14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        scales.forEach { scale ->
            Box(Modifier.width(2.5.dp).fillMaxHeight()
                .graphicsLayer { scaleY = scale.value }
                .background(color, RoundedCornerShape(percent = 50)))
        }
    }
}

/** The music card in the open island: artwork and track above, the three controls below. */
@Composable
internal fun PlaybackRow(playing: Boolean, tint: Color, title: String?, artist: String? = null,
    art: androidx.compose.ui.graphics.ImageBitmap? = null, onPrevious: () -> Unit, onPlayPause: () -> Unit,
    onNext: () -> Unit, progress: Float? = null, durationMs: Long? = null, onSeek: ((Float) -> Unit)? = null, onOpenPlayer: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().testTag("island-media")) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .then(if (onOpenPlayer != null) Modifier.clickable(onClick = onOpenPlayer).testTag("island-open-player") else Modifier),
            verticalAlignment = Alignment.CenterVertically) {
            // The cover when the player gives one; otherwise the bars, which are still while paused and move while playing.
            Box(Modifier.size(MEDIA_ART_DP.dp).clip(RoundedCornerShape(12.dp)).background(themeInk.copy(alpha = .1f)),
                contentAlignment = Alignment.Center) {
                if (art != null) Image(art, null, Modifier.fillMaxSize().testTag("island-art"),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                else EqualizerBars(if (playing) tint else tint.copy(alpha = .55f), animate = playing)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title ?: if (playing) "Playing" else "Paused", color = themeInk, fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("island-track"))
                artist?.let { Text(it, color = themeInk.copy(alpha = .72f), fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        // The track's position, which can be tapped or dragged to seek (the interactive progress of HyperOS's island). Only when the
        // player reports a duration.
        if (progress != null && onSeek != null) SeekBar(progress, tint, onSeek, durationMs) else Spacer(Modifier.height(14.dp))
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            MediaButton(Icons.Rounded.SkipPrevious, "Previous", onPrevious)
            MediaButton(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (playing) "Pause" else "Play", onPlayPause, tint = tint, size = 48.dp, iconSize = 30.dp)
            MediaButton(Icons.Rounded.SkipNext, "Next", onNext)
        }
    }
}

/** A thin track position bar: tap to jump, drag to scrub, with the time played on its left and the time left on its right. The seek is sent once
 * the finger lifts.
 */
@Composable
internal fun SeekBar(progress: Float, tint: Color, onSeek: (Float) -> Unit, durationMs: Long? = null) {
    var width by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = (dragging ?: progress).coerceIn(0f, 1f)
    val timeStyle = androidx.compose.ui.text.TextStyle(color = themeInk.copy(alpha = .6f), fontSize = 11.sp, fontFeatureSettings = "tnum")
    Row(Modifier.fillMaxWidth().height(14.dp), verticalAlignment = Alignment.CenterVertically) {
        // Digits of equal width (tnum), so the labels do not shimmer as the seconds change.
        if (durationMs != null && durationMs > 0) Text(MediaTimes.elapsed(shown, durationMs), style = timeStyle, maxLines = 1,
            modifier = Modifier.widthIn(min = 30.dp).testTag("island-elapsed"))
        Box(Modifier.weight(1f).fillMaxHeight().onSizeChanged { width = it.width.toFloat() }
            .pointerInput(Unit) { detectTapGestures { onSeek((it.x / width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragStart = { dragging = (it.x / width).coerceIn(0f, 1f) },
                    onDragEnd = { dragging?.let(onSeek); dragging = null }, onDragCancel = { dragging = null }) { change, _ ->
                    dragging = (change.position.x / width).coerceIn(0f, 1f)
                }
            }.testTag("island-seek"), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(themeInk.copy(alpha = .18f))) {
                Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(tint, RoundedCornerShape(50)))
            }
        }
        if (durationMs != null && durationMs > 0) Text(MediaTimes.remaining(shown, durationMs), style = timeStyle, maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.widthIn(min = 34.dp).testTag("island-remaining"))
    }
}

/** Fraction along the current track for the seek bar, or null when the player reports no duration. */
internal fun mediaFraction(playing: Boolean, @Suppress("UNUSED_PARAMETER") tick: Long): Float? {
    val controller = NotificationFeed.nowPlaying?.controller ?: return null
    val state = controller.playbackState ?: return null
    val duration = controller.metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: return null
    return MediaProgress.fraction(state.position, state.lastPositionUpdateTime, state.playbackSpeed, playing,
        android.os.SystemClock.elapsedRealtime(), duration)
}

/** The current track's length, or null when the player does not say. */
internal fun mediaDurationMs(): Long? =
    NotificationFeed.nowPlaying?.controller?.metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0L }

internal fun mediaSeek(fraction: Float) {
    val controller = NotificationFeed.nowPlaying?.controller ?: return
    val duration = controller.metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: return
    runCatching { controller.transportControls.seekTo(MediaProgress.seekTarget(fraction, 1f, duration)) }
}

/** The live call card: caller and elapsed time; tapping opens the phone app's call screen. */
@Composable
internal fun CallRow(call: OngoingCall, nowMs: Long, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp))
        .clickable(onClick = onClick).testTag("island-call-row"), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Call, null, tint = IosGreen, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(call.caller, color = themeInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("On call · ${IslandClock.countdown((nowMs - call.startedAt).coerceAtLeast(0L))}",
                color = IosGreen, fontSize = 11.sp, maxLines = 1)
        }
        call.hangUp?.let { end ->
            // The call app's own hang-up action, sent as it would be from its notification.
            Box(Modifier.padding(end = 6.dp).height(26.dp).clip(RoundedCornerShape(50)).background(Color(0xFFFF453A))
                .clickable { runCatching { end.send() } }.padding(horizontal = 12.dp).testTag("island-call-end"),
                contentAlignment = Alignment.Center) { Text(stringResource(com.jake.duolauncher.R.string.end), color = themeInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open call", tint = themeInk.copy(alpha = .7f),
            modifier = Modifier.size(14.dp).padding(end = 4.dp))
    }
}

@Composable
internal fun MediaButton(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = themeInk,
    size: androidx.compose.ui.unit.Dp = 44.dp, iconSize: androidx.compose.ui.unit.Dp = 24.dp) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .84f else 1f, IslandThemes.active.spring(.55f, 700f), label = "media press")
    Box(Modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }.clip(IslandThemes.active.controlShape())
        .clickable(interactionSource = source, indication = null) {
            UnoFeedback.play(Cue.TICK, haptic)
            onClick()
        }, contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
internal fun IslandAction(icon: ImageVector, label: String, showLabel: Boolean, onClick: () -> Unit) {
    if (!showLabel) {
        Box(Modifier.size(44.dp).clip(IslandThemes.active.controlShape()).clickable(onClick = onClick)
            .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = themeInk, modifier = Modifier.size(20.dp))
        }
        return
    }
    Row(Modifier.clip(IslandThemes.active.controlShape())
        .clickable { onClick() }.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, label, tint = themeInk, modifier = Modifier.size(15.dp))
        Text(label, color = themeInk, fontSize = 12.sp)
    }
}
