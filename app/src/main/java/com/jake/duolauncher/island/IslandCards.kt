package com.jake.duolauncher.island

import com.jake.duolauncher.*
import com.jake.duolauncher.panel.*
import com.jake.duolauncher.appearance.*
import androidx.compose.animation.core.animateFloat
import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/*
 * The island's middle level: a card for one live activity, between the pill and the full panel. Each kind has its own template (the music
 * player, the call, the timer ring, the stopwatch, a Live Update or a download), all drawn on the island's black so the camera hole stays hidden;
 * the activity's colour appears only as the glow behind the card and in its accents.
 *
 * The Playful theme setting adds Material 3 Expressive touches: digits that roll like an odometer, buttons that morph between a circle and a
 * rounded square as they change meaning, wavy progress, a burst when something finishes. With it off the same cards are still.
 */
internal object IslandCards {
    /** Height the card adds to the island body, beyond the ordinary one-row notification card, for each kind. */
    fun extraDp(kind: LiveKind?, navigation: Boolean = false): Float = when (kind) {
        LiveKind.MEDIA -> 62f
        LiveKind.CALL -> 44f
        LiveKind.TIMER, LiveKind.STOPWATCH -> 24f
        LiveKind.UPDATE -> if (navigation) 52f else 44f
        else -> 0f
    }

    /** How many of [segments] equal parts of a bar are filled for [progress] of [max] (a part is lit once its whole share is reached). */
    fun filledSegments(progress: Int, max: Int, segments: Int): Int =
        if (max <= 0 || segments <= 0) 0 else ((progress.coerceIn(0, max).toLong() * segments) / max).toInt()

    /** The ring's remaining share for a timer: 1 at the start, 0 at the end. */
    fun ringRemaining(remainingMs: Long, totalMs: Long): Float =
        if (totalMs <= 0L) 0f else (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
}

/** Digits that roll like an odometer: each character that changes slides up and out as the new one slides in. Still when [playful] is off. */
@Composable
internal fun RollingText(text: String, style: TextStyle, color: Color, playful: Boolean, modifier: Modifier = Modifier) {
    val tabular = style.copy(fontFeatureSettings = "tnum")
    if (!playful) { Text(text, style = tabular, color = color, maxLines = 1, modifier = modifier); return }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { i, ch ->
            // Keyed from the right so the seconds keep their column when the minutes grow a digit.
            key(text.length - i) {
                AnimatedContent(ch, transitionSpec = {
                    (slideInVertically(tween(260)) { it / 2 } + fadeIn(tween(200))) togetherWith (slideOutVertically(tween(260)) { -it / 2 } + fadeOut(tween(160)))
                }, label = "digit") { c -> Text(c.toString(), style = tabular, color = color, maxLines = 1) }
            }
        }
    }
}

/** A round control that changes shape with its meaning in the playful theme (a circle while it plays, a rounded square while paused). */
@Composable
internal fun MorphButton(icon: ImageVector, label: String, tint: Color, glyph: Color, morphed: Boolean, playful: Boolean,
    size: Dp = 52.dp, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val squish by animateFloatAsState(if (pressed) .88f else 1f, IslandThemes.active.spring(.5f, 700f), label = "morph press")
    val corner by animateFloatAsState(if (playful && morphed) .3f else .5f, IslandThemes.active.spring(.45f, 380f), label = "morph corner")
    Box(Modifier.size(size).graphicsLayer { scaleX = squish; scaleY = squish }.clip(RoundedCornerShape(percent = (corner * 100f).toInt()))
        .background(tint).clickable(interactionSource = source, indication = null) { UnoFeedback.play(Cue.TICK, haptic); onClick() }
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = glyph, modifier = Modifier.size(size * .54f))
    }
}

/** A small text button in the card's row: a faint pill, or a solid one in [fill]. */
@Composable
internal fun CardPill(label: String, modifier: Modifier = Modifier, icon: ImageVector? = null, fill: Color? = null, ink: Color = themeInk,
    tag: String? = null, description: String? = null, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val squish by animateFloatAsState(if (pressed) .93f else 1f, IslandThemes.active.spring(.5f, 700f), label = "pill press")
    Row(modifier.height(40.dp).graphicsLayer { scaleX = squish; scaleY = squish }.clip(IslandThemes.active.controlShape())
        .background(fill ?: themeInk.copy(alpha = .14f)).clickable(interactionSource = source, indication = null) {
            UnoFeedback.play(Cue.TICK, haptic); onClick() }
        .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier).padding(horizontal = if (label.isEmpty()) 0.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) { Icon(icon, null, tint = ink, modifier = Modifier.size(18.dp)); if (label.isNotEmpty()) Spacer(Modifier.width(6.dp)) }
        if (label.isNotEmpty()) Text(label, color = ink, style = UnoType.subheadline.tight, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** The wavy-or-flat line of a download, and a circle of progress for a timer, share this one drawing of "how far along". */
@Composable
internal fun SegmentedBar(filled: Int, segments: Int, fraction: Float, accent: Color, playful: Boolean, modifier: Modifier = Modifier) {
    val rest = themeInk.copy(alpha = .16f)
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val gap = 4.dp.toPx()
        val seg = (size.width - gap * (segments - 1)) / segments
        val y = size.height / 2f
        val stroke = 6.dp.toPx()
        for (i in 0 until segments) {
            val x0 = i * (seg + gap)
            // The segment being filled shows its own share, so the bar moves smoothly rather than in lumps.
            val share = (fraction * segments - i).coerceIn(0f, 1f)
            drawLine(rest, Offset(x0, y), Offset(x0 + seg, y), stroke, StrokeCap.Round)
            if (share > 0f) drawLine(accent, Offset(x0, y), Offset(x0 + seg * share, y), stroke, StrokeCap.Round)
        }
        // The milestone dots sit on the seams; a reached one is lit.
        for (i in 1 until segments) {
            val cx = i * (seg + gap) - gap / 2f
            drawCircle(if (i <= filled) accent else rest, 2.dp.toPx(), Offset(cx, y - stroke / 2f - 3.dp.toPx()))
        }
        // The leading edge glows in the playful theme.
        if (playful && fraction in .01f..0.99f) {
            val idx = (fraction * segments).toInt().coerceAtMost(segments - 1)
            val share = (fraction * segments - idx).coerceIn(0f, 1f)
            val cx = idx * (seg + gap) + seg * share
            drawCircle(accent.copy(alpha = .35f), 9.dp.toPx(), Offset(cx, y))
            drawCircle(Color.White, 3.dp.toPx(), Offset(cx, y))
        }
    }
}

/** A circular timer ring with its digits in the middle. */
@Composable
internal fun TimerRing(remaining: Float, accent: Color, size: Dp, content: @Composable BoxScope.() -> Unit) {
    val rest = themeInk.copy(alpha = .14f)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 5.dp.toPx()
            val inset = w / 2f
            val arc = Size(this.size.width - w, this.size.height - w)
            drawArc(rest, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
            drawArc(accent, -90f, 360f * remaining, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/** The card shown for [kind] when the pill is long-pressed. It reads the live state itself, so its digits and bars keep moving. */
@Composable
internal fun ActivityCard(peek: PeekNotification, kind: LiveKind, state: IslandState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val playful = IslandThemes.active.playful
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(kind) { while (true) { nowMs = System.currentTimeMillis(); delay(if (kind == LiveKind.STOPWATCH) 100L else 250L) } }
    val accent = peek.tint?.let { Color(it) } ?: IslandThemes.active.highlight
    Column(modifier.fillMaxWidth().testTag("island-activity-card"), verticalArrangement = Arrangement.Center) {
        when (kind) {
            LiveKind.MEDIA -> MediaCard(accent, playful)
            LiveKind.CALL -> CallCard(peek, nowMs, state, playful)
            LiveKind.TIMER -> TimerCard(nowMs, playful)
            LiveKind.STOPWATCH -> StopwatchCard(nowMs, playful)
            LiveKind.UPDATE -> UpdateCard(peek, nowMs, playful)
            else -> Unit
        }
    }
}

@Composable
private fun MediaCard(accent: Color, playful: Boolean) {
    val context = LocalContext.current
    val now = NotificationFeed.nowPlaying
    val playing = now?.playing == true
    val art = remember(now?.art) { now?.art?.asImageBitmap() }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing) { while (playing) { tick = System.currentTimeMillis(); delay(500L) } }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(if (playful && playing) 18.dp else 12.dp)).background(themeInk.copy(alpha = .1f)),
                contentAlignment = Alignment.Center) {
                if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                else EqualizerBars(if (playing) accent else accent.copy(alpha = .55f), animate = playing)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(now?.title ?: "Playing", color = themeInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                now?.artist?.let { Text(it, color = themeInk.copy(alpha = .72f), style = UnoType.footnote.tight, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (art != null) EqualizerBars(if (playing) accent else accent.copy(alpha = .55f), animate = playing)
        }
        val progress = mediaFraction(playing, tick)
        if (progress != null) SeekBar(progress, accent, { mediaSeek(it) }, mediaDurationMs(), playing = playing) else Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            MediaButton(Icons.Rounded.SkipPrevious, "Previous", { IslandTools.touch(); mediaCommand(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS) })
            MorphButton(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", accent, Color.Black,
                morphed = !playing, playful = playful, size = 60.dp) { IslandTools.touch(); mediaCommand(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
            MediaButton(Icons.Rounded.SkipNext, "Next", { IslandTools.touch(); mediaCommand(context, KeyEvent.KEYCODE_MEDIA_NEXT) })
        }
    }
}

@Composable
private fun CallCard(peek: PeekNotification, nowMs: Long, state: IslandState, playful: Boolean) {
    val context = LocalContext.current
    val call = NotificationFeed.ongoingCall
    val caller = call?.caller ?: peek.title.orEmpty()
    // The avatar breathes between a circle and a rounded square while the call lasts, a cookie standing in for a contact photo.
    val phase = if (playful) {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "avatar")
        t.animateFloat(.28f, .5f, androidx.compose.animation.core.infiniteRepeatable(tween(2600), androidx.compose.animation.core.RepeatMode.Reverse), label = "avatar corner").value
    } else .5f
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(percent = (phase * 100f).toInt())).background(IosGreen.copy(alpha = .22f)), contentAlignment = Alignment.Center) {
                Text(caller.trim().firstOrNull()?.uppercase() ?: "", color = IosGreen, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(caller, color = themeInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val started = call?.startedAt ?: nowMs
                RollingText(IslandClock.countdown((nowMs - started).coerceAtLeast(0L)), UnoType.footnote.tight, IosGreen, playful)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            call?.openIntent?.let { open -> CardPill("Open", Modifier.weight(1f), Icons.AutoMirrored.Rounded.OpenInNew, tag = "island-card-open") {
                sendTapAction(context, open); state.collapse() } }
            call?.hangUp?.let { end -> CardPill("End", Modifier.weight(1f), Icons.Rounded.CallEnd, fill = Color(0xFFFF453A), tag = "island-card-end") {
                runCatching { end.send() }; state.collapse() } }
        }
    }
}

@Composable
private fun TimerCard(nowMs: Long, playful: Boolean) {
    val context = LocalContext.current
    val accent = IslandThemes.active.timer
    val remaining = (IslandTools.timerEndAt - nowMs).coerceAtLeast(0L)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TimerRing(IslandCards.ringRemaining(remaining, IslandTools.timerTotal), accent, 72.dp) {
            RollingText(MediaTimes.clock(remaining + 999L), UnoType.footnote.tight.copy(fontWeight = FontWeight.SemiBold), themeInk, playful)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(androidx.compose.ui.res.stringResource(com.jake.duolauncher.R.string.island_card_timer), color = themeInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardPill("+1 min", Modifier.weight(1f), tag = "island-card-plus") {
                    IslandTools.startTimer(context, (IslandTools.timerEndAt - System.currentTimeMillis()).coerceAtLeast(0L) + 60_000L) }
                CardPill("", Modifier.width(44.dp), Icons.Rounded.Close, tag = "island-card-cancel", description = "Cancel timer") { IslandTools.cancelTimer(context) }
            }
        }
    }
}

@Composable
private fun StopwatchCard(nowMs: Long, playful: Boolean) {
    val context = LocalContext.current
    val accent = IslandThemes.active.timer
    val running = IslandTools.swRunning
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(androidx.compose.ui.res.stringResource(com.jake.duolauncher.R.string.island_card_stopwatch), color = themeInk.copy(alpha = .7f), style = UnoType.caption1.tight)
            // The tenths change ten times a second, too fast to roll: only the whole seconds and up roll.
            val clock = IslandClock.stopwatch(IslandTools.elapsedMs(nowMs))
            val dot = clock.indexOf('.').takeIf { it >= 0 } ?: clock.length
            val big = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.Bottom) {
                RollingText(clock.substring(0, dot), big, themeInk, playful)
                Text(clock.substring(dot), style = big.copy(fontFeatureSettings = "tnum"), color = themeInk.copy(alpha = .6f), maxLines = 1)
            }
        }
        MorphButton(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (running) "Pause" else "Resume", accent, Color.Black,
            morphed = !running, playful = playful, size = 48.dp) { IslandTools.toggleStopwatch(context) }
        Spacer(Modifier.width(8.dp))
        MorphButton(Icons.Rounded.Refresh, "Reset", themeInk.copy(alpha = .16f), themeInk, morphed = false, playful = false, size = 48.dp) { IslandTools.resetStopwatch(context) }
    }
}

@Composable
private fun UpdateCard(peek: PeekNotification, nowMs: Long, playful: Boolean) {
    val context = LocalContext.current
    val update = NotificationFeed.liveUpdates.firstOrNull()
    val accent = peek.tint?.let { Color(it) } ?: IslandThemes.active.progressActive ?: IslandThemes.active.highlight
    val max = update?.progressMax ?: 0
    val fraction = if (update == null || max <= 0) 0f else (update.progress.toFloat() / max).coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, IslandThemes.active.spring(.8f, 120f), label = "update fraction")
    if (update?.source == ActivitySource.NAVIGATION) { NavigationCard(update, accent, playful); return }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            update?.icon?.let { Image(remember(it) { it.asImageBitmap() }, null, Modifier.size(36.dp).clip(IslandThemes.active.iconShape.shape())) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(update?.title ?: peek.title.orEmpty(), color = themeInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                (update?.text ?: peek.text)?.let { Text(it, color = themeInk.copy(alpha = .75f), style = UnoType.footnote.tight, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            update?.shortText?.let { Text(it, color = accent, style = UnoType.subheadline.tight, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        }
        if (update != null && max > 0 && !update.indeterminate) {
            Spacer(Modifier.height(10.dp))
            if (update.source == ActivitySource.PROGRESS) {
                // A download: a wavy ring with its percentage, which bursts when it reaches the end.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WavyRing(animated, accent, playful, Modifier.size(44.dp)) {
                        Text("${(animated * 100f).toInt()}", color = themeInk, style = UnoType.caption1.tight.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(12.dp))
                    SegmentedBar(IslandCards.filledSegments(update.progress, max, 4), 4, animated, accent, playful, Modifier.weight(1f))
                }
            } else SegmentedBar(IslandCards.filledSegments(update.progress, max, 4), 4, animated, accent, playful)
        }
    }
}

/** A circle of progress whose arc ripples while it fills (Material 3 Expressive's wavy ring) and flashes an outward burst when it completes. */
@Composable
internal fun WavyRing(fraction: Float, accent: Color, playful: Boolean, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val rest = themeInk.copy(alpha = .14f)
    val phase = if (playful && fraction < 1f) {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "ring wave")
        t.animateFloat(0f, (2 * Math.PI).toFloat(), androidx.compose.animation.core.infiniteRepeatable(tween(1500, easing = androidx.compose.animation.core.LinearEasing)), label = "ring phase").value
    } else 0f
    val done = fraction >= .999f
    val burst by animateFloatAsState(if (playful && done) 1f else 0f, tween(650), label = "ring burst")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 4.dp.toPx(); val c = center; val base = (size.minDimension - w * 2.4f) / 2f
            drawCircle(rest, base, c, style = Stroke(w))
            val sweep = 360f * fraction.coerceIn(0f, 1f)
            val amp = if (playful && !done) 1.6.dp.toPx() else 0f
            val path = androidx.compose.ui.graphics.Path()
            var a = 0f
            while (a <= sweep + .01f) {
                val rad = Math.toRadians((a - 90f).toDouble())
                val r = base + amp * kotlin.math.sin(phase + a * 9f * Math.PI.toFloat() / 180f)
                val x = c.x + (r * kotlin.math.cos(rad)).toFloat(); val y = c.y + (r * kotlin.math.sin(rad)).toFloat()
                if (a == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                a += 3f
            }
            if (sweep > 0f) drawPath(path, accent, style = Stroke(w, cap = StrokeCap.Round))
            if (burst in .01f..0.99f) drawCircle(accent.copy(alpha = (1f - burst) * .6f), base + burst * size.minDimension * .25f, c, style = Stroke(w * (1f - burst)))
        }
        content()
    }
}

/** Navigation: the turn arrow large on the left, the distance and the street beside it set big enough to read at a glance, the ETA at the right. */
@Composable
private fun NavigationCard(update: LiveUpdate, accent: Color, playful: Boolean) {
    val arrow = update.largeIcon ?: update.icon
    // The arrow sits on a soft tile of the route's colour so it reads as a sign; the tile breathes slightly in the playful theme.
    val breathe = if (playful) {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "arrow")
        t.animateFloat(.98f, 1.04f, androidx.compose.animation.core.infiniteRepeatable(tween(1400), androidx.compose.animation.core.RepeatMode.Reverse), label = "arrow scale").value
    } else 1f
    Row(Modifier.fillMaxWidth().testTag("island-nav-card"), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(68.dp).graphicsLayer { scaleX = breathe; scaleY = breathe }.clip(RoundedCornerShape(20.dp)).background(accent.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
            arrow?.let { Image(remember(it) { it.asImageBitmap() }, null, Modifier.size(52.dp)) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(update.title, color = themeInk, fontSize = NavigationText.DISTANCE_SP.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            update.text?.let { Text(it, color = themeInk.copy(alpha = .9f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        update.shortText?.let { Text(it, color = accent, style = UnoType.subheadline.tight, fontWeight = FontWeight.SemiBold, maxLines = 1) }
    }
}
