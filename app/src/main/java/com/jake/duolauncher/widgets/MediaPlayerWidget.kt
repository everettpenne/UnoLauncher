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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 *
 * The look follows the artwork: it is blurred and dimmed into a wash behind the card (so every track gets its own colours), the
 * text and controls are white on it, and the layout adapts to the size it is given: a compact card (title, artist, progress, controls),
 * or, when it is wide, the artwork as a tile beside them with the elapsed and remaining time.
 */
@Composable
internal fun MediaPlayerFace(slot: Int, onOptions: () -> Unit) {
    val context = LocalContext.current
    val previewing = LocalWidgetPreview.current
    val live = NotificationFeed.nowPlaying
    // The picker's preview plays a made-up track, so it shows the widget as it looks with music on, not an empty card.
    val sample = remember(previewing) { if (previewing) MediaView("Midnight Dunes", "The Glass Orchestra", PreviewSamples.artwork(), true, 83_000L, 214_000L,
        SystemClock.elapsedRealtime(), 1f, null) else null }
    val np = sample ?: live?.toView()
    val playing = np?.playing == true
    // Ticks each second while playing (the seek bar advances); once when paused.
    val tick by produceState(0L, playing) {
        if (!playing) { value = System.currentTimeMillis(); return@produceState }
        while (true) { value = System.currentTimeMillis(); delay(1_000L) }
    }
    val art = remember(np?.art) { np?.art?.asImageBitmap() }
    GlassCard(onClick = { val controller = live?.controller; if (controller != null) openPlayer(context, controller) else onOptions() },
        modifier = Modifier.testTag("uno-media-player-$slot"), padding = 0.dp) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val glassInk = LocalGlassInk.current
            val onArt = np != null && art != null
            if (onArt) ArtWash(art!!)
            val ink = if (onArt) Color.White else glassInk.primary
            val soft = if (onArt) Color.White.copy(alpha = .74f) else glassInk.soft()
            if (np == null) {
                EmptyPlayer(context, ink, soft)
            } else if (maxWidth >= 240.dp) {
                val tile = minOf(maxHeight - 16.dp, 92.dp)
                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    ArtTile(art, ink, Modifier.size(tile))
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                        TrackText(np, ink, soft, titleSize = 16.sp)
                        SeekBar(np, playing, tick, ink, showTimes = true)
                        Controls(context, playing, ink, onArt, Modifier.fillMaxWidth())
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    TrackText(np, ink, soft, titleSize = 13.sp)
                    SeekBar(np, playing, tick, ink, showTimes = false)
                    Controls(context, playing, ink, onArt, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** What the widget draws about the music: the live [NowPlaying] or, in the picker's preview, a sample. [controller] is null for the sample. */
internal data class MediaView(val title: String?, val artist: String?, val art: android.graphics.Bitmap?, val playing: Boolean, val positionMs: Long,
    val durationMs: Long, val positionUpdatedAtMs: Long, val speed: Float, val controller: android.media.session.MediaController?)

private fun NowPlaying.toView() = MediaView(title, artist, art, playing, positionMs, durationMs, positionUpdatedAtMs, speed, controller)

/** The artwork, blurred and dimmed, filling the card: each track brings its own colours to the glass. */
@Composable
private fun ArtWash(art: ImageBitmap) {
    Box(Modifier.fillMaxSize().clipToBounds()) {
        Image(art, null, Modifier.fillMaxSize().blur(30.dp, BlurredEdgeTreatment.Unbounded), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .22f), Color.Black.copy(alpha = .58f)))))
    }
}

@Composable
private fun ArtTile(art: ImageBitmap?, ink: Color, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(ink.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
        if (art != null) Image(art, "Album art", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Rounded.MusicNote, null, tint = ink, modifier = Modifier.size(34.dp))
    }
}

@Composable
private fun TrackText(now: MediaView, ink: Color, soft: Color, titleSize: androidx.compose.ui.unit.TextUnit) {
    Column {
        Text(MediaWidget.displayTitle(now.title, now.playing), color = ink, fontSize = titleSize, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("media-title"))
        Text(now.artist ?: "", color = soft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Controls(context: Context, playing: Boolean, ink: Color, onArt: Boolean, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        MediaControl(Icons.Rounded.SkipPrevious, "Previous", ink, size = 44.dp) { mediaCommand(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        MediaControl(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", ink, size = 48.dp,
            filled = true, iconColor = if (onArt) Color.Black.copy(alpha = .86f) else Color.White) { mediaCommand(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        MediaControl(Icons.Rounded.SkipNext, "Next", ink) { mediaCommand(context, KeyEvent.KEYCODE_MEDIA_NEXT) }
    }
}

@Composable
private fun EmptyPlayer(context: Context, ink: Color, soft: Color) {
    val detailsOn = context.getSharedPreferences("extras", Context.MODE_PRIVATE).getBoolean("mediaDetails", false)
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(ink.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, null, tint = soft, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(com.jake.duolauncher.R.string.nothing_playing), color = ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(stringResource(when (MediaWidget.emptyHint(NotificationFeed.connected, detailsOn)) {
            MediaWidget.EmptyHint.NEEDS_ACCESS -> com.jake.duolauncher.R.string.media_widget_needs_access
            MediaWidget.EmptyHint.NEEDS_SETTING -> com.jake.duolauncher.R.string.media_widget_needs_setting
            MediaWidget.EmptyHint.IDLE -> com.jake.duolauncher.R.string.media_widget_idle
        }), color = soft, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("media-empty-hint"))
    }
}

/** A thin seek bar with a knob: tap to jump (a drag would fight the Home pager's page swipe). The filled part follows the position the
 *  listener last reported, moved on by the playback speed. A screen reader gets its value and can set it. With [showTimes], the elapsed
 *  and remaining time sit at its ends. */
@Composable
private fun SeekBar(now: MediaView, playing: Boolean, tick: Long, tint: Color, showTimes: Boolean) {
    @Suppress("UNUSED_EXPRESSION") tick   // read, so the bar is redrawn each second while playing
    val duration = now.durationMs
    val fraction = MediaWidget.fraction(now.positionMs, now.positionUpdatedAtMs, now.speed, playing,
        SystemClock.elapsedRealtime(), duration) ?: return
    var width by remember { mutableIntStateOf(1) }
    val seek = { target: Float -> runCatching { now.controller?.transportControls?.seekTo(MediaWidget.seekTarget(target, duration)) }; Unit }
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showTimes) Text(MediaWidget.timeLabel((duration * fraction).toLong()), color = tint.copy(alpha = .8f), fontSize = 10.sp,
            modifier = Modifier.padding(end = 8.dp))
        Box(Modifier.weight(1f).fillMaxHeight().onSizeChanged { width = it.width }
            .pointerInput(duration, now.controller) { detectTapGestures { seek(it.x / width.coerceAtLeast(1)) } }
            .semantics {
                contentDescription = "Seek"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { target -> seek(target); true }
            }.testTag("media-seek"), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(tint.copy(alpha = .26f)))
            Box(Modifier.fillMaxWidth(fraction).height(3.dp).clip(RoundedCornerShape(50)).background(tint))
            // The knob rides the end of the filled part.
            Box(Modifier.fillMaxWidth(fraction.coerceAtLeast(.001f)).wrapContentWidth(Alignment.End)) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(tint))
            }
        }
        if (showTimes) Text(MediaWidget.remainingLabel((duration * (1f - fraction)).toLong()), color = tint.copy(alpha = .8f), fontSize = 10.sp,
            modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun MediaControl(icon: ImageVector, label: String, tint: Color, size: Dp = 44.dp, filled: Boolean = false,
    iconColor: Color = tint, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).then(if (filled) Modifier.background(tint.copy(alpha = .94f)) else Modifier)
        .clickable(onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = iconColor, modifier = Modifier.size(size * if (filled) .56f else .52f))
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

    /** The time left, shown with a leading minus ("-2:10"). */
    fun remainingLabel(ms: Long): String = "\u2212" + timeLabel(ms)

    /** "1:23" or "1:02:03" for a position in milliseconds (never negative). */
    fun timeLabel(ms: Long): String {
        val total = ms.coerceAtLeast(0L) / 1000L
        val h = total / 3600; val m = (total % 3600) / 60; val sec = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    /** A track without a title still reads as playing or paused, not blank. */
    fun displayTitle(title: String?, playing: Boolean): String =
        title?.trim()?.takeIf { it.isNotEmpty() } ?: if (playing) "Playing" else "Paused"
}
