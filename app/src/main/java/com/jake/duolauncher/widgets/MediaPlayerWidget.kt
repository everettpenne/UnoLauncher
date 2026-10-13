package com.jake.duolauncher.widgets

import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import android.content.Context
import android.media.MediaMetadata
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** The Media player widget: its face (the drawing) and its own helpers. Registered in [UnoWidgets].
 *
 * It shows whatever is playing (title, artist, artwork) and drives it with play, pause, skip and a tap-to-seek bar.
 * The controls go through the island's `mediaCommand` (the session's own controls when the island knows the player, a media
 * key otherwise). The track details come from the same notification access and the same "Track title and artwork in media"
 * setting the island's music card uses, so when either is off the widget says which one to turn on. Nothing is read, stored or
 * sent beyond what the island already reads for its own media card.
 *
 * Nothing here asks the player anything while drawing: the position, duration and speed arrive on [NowPlaying] from the
 * listener's own thread, because a player query is a binder call that can stall for seconds (see docs/design.md).
 */
@Composable
internal fun MediaPlayerFace(slot: Int, onOptions: () -> Unit) {
    val context = LocalContext.current
    val np = NotificationFeed.nowPlaying
    val playing = np?.playing == true
    // Ticks each second while playing (the seek bar advances); once when paused.
    val tick by produceState(0L, playing) {
        if (!playing) { value = System.currentTimeMillis(); return@produceState }
        while (true) { value = System.currentTimeMillis(); delay(1_000L) }
    }
    GlassCard(onClick = { val controller = np?.controller; if (controller != null) openPlayer(context, controller) else onOptions() },
        modifier = Modifier.testTag("uno-media-player-$slot")) {
        val ink = LocalGlassInk.current
        CardTitle(Icons.Rounded.MusicNote, "Now playing")
        val now = np
        if (now == null) {
            val detailsOn = context.getSharedPreferences("extras", Context.MODE_PRIVATE).getBoolean("mediaDetails", false)
            Text(stringResource(com.jake.duolauncher.R.string.nothing_playing), color = ink.primary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(stringResource(when (MediaWidget.emptyHint(NotificationFeed.connected, detailsOn)) {
                MediaWidget.EmptyHint.NEEDS_ACCESS -> com.jake.duolauncher.R.string.media_widget_needs_access
                MediaWidget.EmptyHint.NEEDS_SETTING -> com.jake.duolauncher.R.string.media_widget_needs_setting
                MediaWidget.EmptyHint.IDLE -> com.jake.duolauncher.R.string.media_widget_idle
            }), color = ink.soft(), fontSize = 12.sp, modifier = Modifier.testTag("media-empty-hint"))
        } else {
            val art = remember(now.art) { now.art?.asImageBitmap() }
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(ink.soft(.18f)),
                    contentAlignment = Alignment.Center) {
                    if (art != null) Image(art, "Album art", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Rounded.MusicNote, null, tint = ink.primary, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(MediaWidget.displayTitle(now.title, now.playing), color = ink.primary, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("media-title"))
                    now.artist?.let {
                        Text(it, color = ink.soft(), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            SeekBar(now, playing, tick, ink.primary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                MediaControl(Icons.Rounded.SkipPrevious, "Previous", ink.primary) {
                    mediaCommand(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                }
                MediaControl(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (playing) "Pause" else "Play", ink.primary, size = 52.dp, iconScale = .55f) {
                    mediaCommand(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                }
                MediaControl(Icons.Rounded.SkipNext, "Next", ink.primary) {
                    mediaCommand(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                }
            }
        }
    }
}

/** A thin seek bar: tap to jump (a drag would fight the Home pager's page swipe). The filled part follows the position the listener
 *  last reported, moved on by the playback speed. A screen reader gets its value and can set it. */
@Composable
private fun SeekBar(now: NowPlaying, playing: Boolean, tick: Long, tint: androidx.compose.ui.graphics.Color) {
    @Suppress("UNUSED_EXPRESSION") tick   // read, so the bar is redrawn each second while playing
    val duration = now.durationMs
    val fraction = MediaWidget.fraction(now.positionMs, now.positionUpdatedAtMs, now.speed, playing,
        SystemClock.elapsedRealtime(), duration) ?: return
    var width by remember { mutableIntStateOf(1) }
    val seek = { target: Float -> runCatching { now.controller.transportControls.seekTo(MediaWidget.seekTarget(target, duration)) }; Unit }
    Box(Modifier.fillMaxWidth().height(48.dp).onSizeChanged { width = it.width }
        .pointerInput(duration, now.controller) { detectTapGestures { seek(it.x / width.coerceAtLeast(1)) } }
        .semantics {
            contentDescription = "Seek"
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            setProgress { target -> seek(target); true }
        }.testTag("media-seek"), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(tint.copy(alpha = .18f)))
        Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(RoundedCornerShape(50)).background(tint))
    }
}

@Composable
private fun MediaControl(icon: ImageVector, label: String, tint: androidx.compose.ui.graphics.Color,
    size: Dp = 48.dp, iconScale: Float = .5f, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).clickable(onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(size * iconScale))
    }
}

/** The Media player widget's rules, kept apart from the drawing so each is unit-tested. */
internal object MediaWidget {
    /** Fraction along the track, 0 to 1, or null when the player reports no duration. While playing, the position
     *  advances by the playback speed between the last update and [nowElapsedMs]; paused it stays put. */
    fun fraction(positionMs: Long, lastUpdateMs: Long, speed: Float, playing: Boolean, nowElapsedMs: Long,
        durationMs: Long): Float? {
        if (durationMs <= 0L) return null
        val base = if (playing) positionMs + ((nowElapsedMs - lastUpdateMs) * speed).toLong() else positionMs
        return (base.toFloat() / durationMs).coerceIn(0f, 1f)
    }

    /** Why there is nothing to show, so the widget can say what to turn on: notification access first, then the media-details setting. */
    enum class EmptyHint { NEEDS_ACCESS, NEEDS_SETTING, IDLE }

    fun emptyHint(accessConnected: Boolean, detailsOn: Boolean): EmptyHint = when {
        !accessConnected -> EmptyHint.NEEDS_ACCESS
        !detailsOn -> EmptyHint.NEEDS_SETTING
        else -> EmptyHint.IDLE
    }

    /** The position for a seek to [fraction] of a [durationMs] track. */
    fun seekTarget(fraction: Float, durationMs: Long): Long = (durationMs * fraction.coerceIn(0f, 1f)).toLong()

    /** A track without a title still reads as playing or paused, not blank. */
    fun displayTitle(title: String?, playing: Boolean): String =
        title?.trim()?.takeIf { it.isNotEmpty() } ?: if (playing) "Playing" else "Paused"
}
