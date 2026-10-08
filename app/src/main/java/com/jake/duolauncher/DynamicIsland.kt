package com.jake.duolauncher

import android.view.KeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.OpenInNew
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Shared island state owned by MainActivity, so app launches (which start there) can
 * flash the island without a Compose-local channel.
 */
@Stable
internal class IslandState {
    var expanded by mutableStateOf(false)
    var flashTitle by mutableStateOf<String?>(null)
    var flashIcon by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
        private set
    var flashSymbol by mutableStateOf<IslandSymbol?>(null)
        private set
    /** Bumped on every flash, so the same event twice in a row still pulses and times out afresh. */
    var flashKey by mutableIntStateOf(0)
        private set
    var playing by mutableStateOf(false)
        private set
    var lastPlayingAt by mutableLongStateOf(0L)
        private set

    fun showLaunch(app: AppEntry) {
        flashTitle = app.label
        flashIcon = app.icon.asImageBitmap()
        flashSymbol = IslandSymbol.APP
        flashKey++
    }

    private var lastFocusAt = 0L

    /** Shows [event]. Turning Do Not Disturb on also moves the ringer to silent, so a ringer event
     * arriving right after a Focus change is that change's side effect and is dropped, as iOS shows
     * only the Focus.
     */
    fun showEvent(event: IslandEvent, nowMs: Long = System.currentTimeMillis()) {
        if (event.symbol == IslandSymbol.FOCUS) lastFocusAt = nowMs
        else if (event.symbol.isRinger() && nowMs - lastFocusAt in 0..FOCUS_SIDE_EFFECT_MS) return
        flashTitle = event.title
        flashIcon = null
        flashSymbol = event.symbol
        flashKey++
    }

    /** A notification peek: the app's icon and name, but never the message. */
    fun showPeek(app: AppEntry, label: String) {
        flashTitle = label
        flashIcon = app.icon.asImageBitmap()
        flashSymbol = IslandSymbol.APP
        flashKey++
    }

    fun showCharging(percent: Int?) {
        if (flashTitle == null) showEvent(IslandEvents.charging(percent))
    }

    /** Records whether audio is playing; the stop time is what starts the resume window. */
    fun setPlaying(nowMs: Long, value: Boolean) {
        if (value || playing) lastPlayingAt = nowMs
        playing = value
    }

    fun dismissFlash() { flashTitle = null; flashIcon = null; flashSymbol = null }
    fun toggle() { expanded = !expanded; dismissFlash(); IslandTools.toolsOpen = false }
    fun collapse() { expanded = false; IslandTools.toolsOpen = false }
}

private fun IslandSymbol.icon(): ImageVector = when (this) {
    IslandSymbol.CHARGING, IslandSymbol.APP -> Icons.Rounded.BatteryChargingFull
    IslandSymbol.RINGER -> Icons.Rounded.NotificationsActive
    IslandSymbol.SILENT -> Icons.Rounded.NotificationsOff
    IslandSymbol.VIBRATE -> Icons.Rounded.Vibration
    IslandSymbol.AIRPLANE -> Icons.Rounded.AirplanemodeActive
    IslandSymbol.FOCUS -> Icons.Rounded.Bedtime
    IslandSymbol.TIMER -> Icons.Rounded.Timer
    IslandSymbol.NOTIFICATION -> Icons.Rounded.NotificationsActive
}

/** The island's body: the lens-refracted glass rim the island is known for, around an interior of
 * true black. An OLED pixel at pure black is simply off, so the camera hole disappears into the
 * pill; any translucency would let the wallpaper through and show the hole's edge. Only a thin
 * outer ring keeps a see-through tint, so the refraction and highlight still read.
 */
@Composable
private fun Modifier.islandBody(
    backdrop: Backdrop?,
    cornerRadius: Dp,
    rimWidth: Dp = 2.5.dp,
    settings: GlassSettings,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    if (backdrop == null) return background(Color.Black, shape)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            backdropBlur(2.dp.toPx())
            lens(
                refractionHeight = androidx.compose.ui.util.lerp(8f, 44f, settings.height).coerceAtMost(72f).dp.toPx(),
                refractionAmount = androidx.compose.ui.util.lerp(16f, 132f, settings.amount).dp.toPx(),
                depthEffect = true,
                chromaticAberration = settings.chromatic > 0.05f,
            )
        },
        highlight = { GlassRim.Light },
        onDrawSurface = {
            drawRect(Color.Black.copy(alpha = .55f))
            val rim = rimWidth.toPx()
            drawRoundRect(Color.Black, topLeft = Offset(rim, rim),
                size = Size(size.width - 2 * rim, size.height - 2 * rim),
                cornerRadius = CornerRadius((cornerRadius.toPx() - rim).coerceAtLeast(0f)))
        },
    )
}

/** A dynamic island that wraps the front-camera hole. Collapsed it is a capsule centered on the
 * hole with the time on one side and battery, playback or an event on the other; it expands
 * downward from the hole into a live panel (time, battery, playback controls, the top feed
 * headline, and quick actions) and flashes app launches, charging, ringer, airplane and Do Not
 * Disturb changes. Devices without a cutout get a plain capsule below the status bar. The island
 * is launcher-local: it draws on Home only and never overlays other apps.
 *
 * Positioning is done entirely in window pixels from [IslandGeometry]. The call site sits inside
 * a safe-area-padded, aligned parent, so the island measures that parent's own window origin and
 * subtracts it; it must not be combined with `align` or the parent's insets, which is what used
 * to push it toward the right edge.
 */
@Composable
internal fun DynamicIsland(
    state: IslandState,
    glass: PageGlass?,
    deviceStatus: DeviceStatus,
    feedHeadline: String?,
    sizeScale: Float = .5f,
    dockWidthPx: Float = 0f,
    environmentOverride: IslandEnvironment? = null,
    anchoredToWindow: Boolean = false,
    showActions: Boolean = true,
    onFrameChanged: ((IslandFrame) -> Unit)? = null,
    onSearch: () -> Unit,
    onOpenFeed: () -> Unit,
    onCustomize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val now by produceState(LocalDateTime.now()) {
        while (true) { value = LocalDateTime.now(); delay(30_000L) }
    }
    // Wall clock for the resume window; coarse, since the window is 90 seconds long.
    // Ticks faster only while something on the island is counting.
    val clockMs by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(if (IslandTools.swRunning) 100L else if (IslandTools.timerActive) 500L else 5_000L)
        }
    }
    val torch = rememberTorch(active = state.expanded && IslandTools.toolsOpen)
    // A timer whose alarm never reached us (the process was stopped, or Android delayed it) must still finish: once the
    // clock passes its end it rings from here. onAlarm ignores a second trigger, so this cannot double up.
    LaunchedEffect(clockMs) {
        if (IslandTools.timerActive && !IslandTools.ringing && clockMs >= IslandTools.timerEndAt) IslandTools.onAlarm(context)
    }
    IslandSystemEvents(state)
    val view = LocalView.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val d = density.density
    // The cutout is read from the live window and refreshed on every layout, so it is correct once
    // the view attaches and again after rotation, folding, or a display-size change.
    val initialWidth = view.rootView.width
    val initialHeight = view.rootView.height
    var environment by remember { mutableStateOf(environmentOverride
        ?: IslandEnvironment(null, initialWidth.toFloat(), 0f)) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(configuration, environmentOverride) {
        environment = environmentOverride ?: readIslandEnvironment(view, dockWidthPx, initialWidth, initialHeight)
    }

    val progress by animateFloatAsState(if (state.expanded) 1f else 0f,
        spring(dampingRatio = 0.5f, stiffness = 300f), label = "island expand")
    val flashActive = state.flashTitle != null
    val mediaVisible = IslandPlayback.controlsVisible(clockMs, state.playing, state.lastPlayingAt)
    // Whether the actions have room for their text labels at this panel width (Inter runs wider than the
    // system font); narrower panels get icon-only buttons, with the label kept for accessibility.
    val actionLabelsFit = IslandGeometry.frame(environment, d, 1f, sizeScale).width / d - 36f >= ACTION_LABELS_MIN_DP
    val eventWidth by animateFloatAsState(
        if (flashActive) IslandGeometry.eventExtraWidthDp((state.flashTitle ?: "").length, sizeScale) else 0f,
        spring(dampingRatio = .6f, stiffness = 420f), label = "island event width")
    val callActive = NotificationFeed.ongoingCall != null
    val frame = IslandGeometry.frame(environment, d, progress, sizeScale,
        extraBodyDp = (if (mediaVisible) MEDIA_ROW_DP else 0f) + (if (callActive) CALL_ROW_DP else 0f),
        extraWidthDp = eventWidth)
    val corner = minOf(frame.height / 2f, 30f * d) / d
    // A progress ring around the collapsed pill: a running timer, or the battery level while charging.
    val ring = IslandRingLogic.choose(flashActive, progress >= .5f || state.expanded, IslandTools.ringing,
        IslandTools.timerActive, 1f - IslandClock.progress(clockMs, IslandTools.timerEndAt, IslandTools.timerTotal),
        deviceStatus.charging == true, deviceStatus.battery, IslandSymbol.TIMER.tint, IosGreen)
    // A new event gives the island a small spring pulse, like iOS's.
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(state.flashKey) {
        if (state.flashKey > 0) {
            pulse.snapTo(1f)
            pulse.animateTo(1.09f, spring(dampingRatio = .45f, stiffness = 700f))
            pulse.animateTo(1f, spring(dampingRatio = .4f, stiffness = 260f))
        }
    }
    // Flash returns to the collapsed clock after a beat.
    LaunchedEffect(state.flashKey) {
        if (state.flashTitle != null) { delay(2600); state.dismissFlash() }
    }
    // An idle expanded island tucks itself back in.
    LaunchedEffect(state.expanded, IslandTools.toolsOpen, IslandTools.interactions) {
        if (state.expanded) { delay(if (IslandTools.toolsOpen) 20_000L else 5000L); state.collapse() }
    }

    // Anchored (overlay) mode renders only the island itself: the service positions a
    // WRAP_CONTENT window at the frame, so a fillMaxSize wrapper here would expand the window
    // to cover the screen and swallow every touch. Home mode keeps the full-size wrapper so
    // the island can offset itself to the cutout inside the launcher's window.
    val wrapperModifier = if (anchoredToWindow) Modifier
        else Modifier.fillMaxSize().onGloballyPositioned {
            origin = it.positionInWindow()
            if (environmentOverride == null) {
                val read = readIslandEnvironment(view, dockWidthPx, initialWidth, initialHeight)
                if (read != environment) environment = read
            }
        }
    Box(modifier.then(wrapperModifier)) {
        LaunchedEffect(frame) { onFrameChanged?.invoke(frame) }
        // The island's window-pixel position, translated into this parent's own frame. The
        // everywhere-overlay positions its window itself and renders the island at the origin.
        Box(Modifier
            .offset { if (anchoredToWindow) androidx.compose.ui.unit.IntOffset.Zero else androidx.compose.ui.unit.IntOffset(
                (frame.left - origin.x).roundToInt(), (frame.top - origin.y).roundToInt()) }
            .graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
            .size(width = (frame.width / d).dp, height = (frame.height / d).dp)
            .islandBody(glass?.backdrop, corner.dp, settings = glass?.settings ?: GlassSettings.Default)
            .islandRing(ring, corner * d, 2.5f * d)
            .clip(RoundedCornerShape(corner.dp))
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.dismissFlash(); IslandTools.toolsOpen = true; state.expanded = true
                },
                onClick = {
                    UnoFeedback.play(Cue.OPEN, haptic)
                    // A ringing timer is silenced by a tap before anything else.
                    if (IslandTools.ringing) IslandTools.stopRinging(context) else state.toggle()
                })
            .testTag("dynamic-island")) {
            val hole = frame.hole
            val gap = 4.dp
            // Collapsed face. With a hole the content is split around it; without one it is centered.
            // Only the face that is showing is composed. An invisible panel would still own its buttons'
            // touch targets, and Compose pads small targets to 48 dp, so the hidden Search button caught
            // taps meant for the pill itself.
            if (progress < .5f) Row(Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically) {
                val leading: @Composable () -> Unit = {
                    if (flashActive) {
                        val symbol = state.flashSymbol
                        val bitmap = state.flashIcon
                        if (bitmap != null) Image(bitmap, null, Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)))
                        else if (symbol != null) Icon(symbol.icon(), null, tint = symbol.tint, modifier = Modifier.size(18.dp))
                    } else if (IslandTools.ringing) {
                        Icon(Icons.Rounded.Alarm, null, tint = IslandSymbol.TIMER.tint, modifier = Modifier.size(18.dp))
                    } else if (IslandTools.timerActive) {
                        Icon(Icons.Rounded.Timer, null, tint = IslandSymbol.TIMER.tint, modifier = Modifier.size(16.dp))
                    } else {
                        Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = Color.White,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
                val trailing: @Composable () -> Unit = {
                    if (flashActive) {
                        Text(state.flashTitle ?: "", color = Color.White, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 4.dp))
                    } else if (IslandTools.ringing) {
                        Text("Timer done", color = IslandSymbol.TIMER.tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, modifier = Modifier.testTag("island-timer-done"))
                    } else if (IslandTools.timerActive) {
                        Text(IslandClock.countdown(IslandClock.remainingMs(clockMs, IslandTools.timerEndAt)), color = IslandSymbol.TIMER.tint,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.testTag("island-timer"))
                    } else if (IslandTools.swRunning) {
                        Text(IslandClock.stopwatch(IslandTools.elapsedMs(clockMs)), color = Color.White,
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.testTag("island-stopwatch"))
                    } else if (state.playing) {
                        EqualizerBars(PLAYBACK_PINK, Modifier.testTag("island-playing"))
                    } else if (callActive) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(Icons.Rounded.Call, null, tint = IosGreen, modifier = Modifier.size(14.dp))
                            Text(NotificationFeed.ongoingCall?.caller ?: "", color = Color.White, fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("island-call"))
                        }
                    } else if (deviceStatus.charging == true) {
                        Icon(Icons.Rounded.BatteryChargingFull, null, tint = IosGreen, modifier = Modifier.size(16.dp))
                    } else if (deviceStatus.battery != null) {
                        Text("${deviceStatus.battery}%", color = Color.White.copy(alpha = .9f), fontSize = 12.sp,
                            maxLines = 1)
                    }
                }
                if (hole != null) {
                    Box(Modifier.width(((hole.left / d).dp - gap).coerceAtLeast(0.dp)), contentAlignment = Alignment.Center) { leading() }
                    Spacer(Modifier.width((hole.width / d).dp + gap * 2))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { trailing() }
                } else {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        leading(); trailing()
                    }
                }
            }
            // Tools face (long press): timer, stopwatch, flashlight.
            if (progress >= .5f && IslandTools.toolsOpen) Column(Modifier.fillMaxSize()
                .padding(start = 18.dp, end = 18.dp, bottom = 12.dp,
                    top = if (hole != null) (hole.bottom / d).dp + 6.dp else 12.dp)) {
                IslandToolsFace(clockMs, torch)
            }
            // Expanded face: live panel, starting below the camera hole.
            if (progress >= .5f && !IslandTools.toolsOpen) Column(Modifier.fillMaxSize()
                .padding(start = 18.dp, end = 18.dp, bottom = 12.dp,
                    top = if (hole != null) (hole.bottom / d).dp + 6.dp else 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = Color.White,
                            fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Text(now.format(DateTimeFormatter.ofPattern("EEE, MMM d")), color = Color.White.copy(alpha = .85f),
                            fontSize = 12.sp)
                    }
                    Text(if (deviceStatus.battery != null) "${deviceStatus.battery}%" else "—",
                        color = if (deviceStatus.charging == true) IosGreen else Color.White,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                NotificationFeed.ongoingCall?.let { call ->
                    Spacer(Modifier.height(6.dp))
                    CallRow(call, clockMs, onClick = {
                        runCatching { call.openIntent?.send() }; state.collapse()
                    })
                }
                if (mediaVisible) {
                    Spacer(Modifier.height(6.dp))
                    PlaybackRow(playing = state.playing, title = NotificationFeed.nowPlaying?.title,
                        artist = NotificationFeed.nowPlaying?.artist,
                        art = NotificationFeed.nowPlaying?.art?.asImageBitmap(),
                        onPrevious = { sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
                        onPlayPause = { sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) },
                        onNext = { sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT) },
                        onOpenPlayer = NotificationFeed.nowPlaying?.controller?.sessionActivity?.let { intent ->
                            { runCatching { intent.send() }; state.collapse() }
                        })
                }
                Spacer(Modifier.height(8.dp))
                feedHeadline?.let { headline ->
                    Text(headline, color = Color.White.copy(alpha = .95f), fontSize = 13.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                }
                if (showActions) Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IslandAction(Icons.Rounded.Search, "Search", actionLabelsFit, onSearch)
                    IslandAction(Icons.Rounded.RssFeed, "Feed", actionLabelsFit, onOpenFeed)
                    IslandAction(Icons.Rounded.Tune, "Customize", actionLabelsFit, onCustomize)
                }
            }
        }
    }
}

/** Inner panel width, in dp, needed to show all three action labels in Inter. */
private const val ACTION_LABELS_MIN_DP = 270f

/** How long after a Focus change a ringer change is treated as its side effect. */
internal const val FOCUS_SIDE_EFFECT_MS = 1_500L

/** Extra panel height, in dp, when the playback row is showing. */
internal const val MEDIA_ROW_DP = 40f
/** Extra panel height, in dp, when the call card is showing. */
internal const val CALL_ROW_DP = 40f
internal val PLAYBACK_PINK = Color(0xFFFF375F)

/** Four bars that rise and fall out of step: the iOS "audio is playing" mark. The animation drives
 * a layer scale, so it never recomposes while it runs.
 */
@Composable
internal fun EqualizerBars(color: Color, modifier: Modifier = Modifier, barCount: Int = 4) {
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

@Composable
private fun PlaybackRow(playing: Boolean, title: String?, artist: String? = null,
    art: androidx.compose.ui.graphics.ImageBitmap? = null, onPrevious: () -> Unit, onPlayPause: () -> Unit,
    onNext: () -> Unit, onOpenPlayer: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(34.dp).testTag("island-media"), verticalAlignment = Alignment.CenterVertically) {
        if (playing) EqualizerBars(PLAYBACK_PINK) else Icon(Icons.Rounded.Pause, null,
            tint = Color.White.copy(alpha = .6f), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        art?.let {
            Image(it, null, Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).testTag("island-art"))
            Spacer(Modifier.width(8.dp))
        }
        // With notification access the session knows its own app, so tapping the title opens the player.
        Column(Modifier.weight(1f).then(if (onOpenPlayer != null) Modifier.clickable(onClick = onOpenPlayer).testTag("island-open-player") else Modifier)) {
            Text(title ?: if (playing) "Playing" else "Paused", color = Color.White, fontSize = 13.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            artist?.let { Text(it, color = Color.White.copy(alpha = .72f), fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        MediaButton(Icons.Rounded.SkipPrevious, "Previous", onPrevious)
        MediaButton(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            if (playing) "Pause" else "Play", onPlayPause)
        MediaButton(Icons.Rounded.SkipNext, "Next", onNext)
    }
}

/** The live call card: caller and elapsed time; tapping opens the phone app's call screen. */
@Composable
private fun CallRow(call: OngoingCall, nowMs: Long, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(12.dp))
        .clickable(onClick = onClick).testTag("island-call-row"), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Call, null, tint = IosGreen, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(call.caller, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("On call · ${IslandClock.countdown((nowMs - call.startedAt).coerceAtLeast(0L))}",
                color = IosGreen, fontSize = 11.sp, maxLines = 1)
        }
        Icon(Icons.Rounded.OpenInNew, "Open call", tint = Color.White.copy(alpha = .7f),
            modifier = Modifier.size(14.dp).padding(end = 4.dp))
    }
}

@Composable
private fun MediaButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun IslandAction(icon: ImageVector, label: String, showLabel: Boolean, onClick: () -> Unit) {
    if (!showLabel) {
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick)
            .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        return
    }
    Row(Modifier.clip(RoundedCornerShape(percent = 50))
        .clickable { onClick() }.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(15.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

/** Timer presets or the running countdown, the stopwatch, and the flashlight. */
@Composable
private fun IslandToolsFace(clockMs: Long, torch: TorchState) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().height(40.dp).testTag("island-tools-timer"), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Rounded.Timer, null, tint = IslandSymbol.TIMER.tint, modifier = Modifier.size(18.dp))
        if (IslandTools.timerActive) {
            Text(if (IslandTools.ringing) "Done" else IslandClock.countdown(IslandClock.remainingMs(clockMs, IslandTools.timerEndAt)),
                color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            ToolButton(Icons.Rounded.Close, "Cancel timer") { IslandTools.cancelTimer(context) }
        } else listOf(1 to "1m", 5 to "5m", 10 to "10m", 30 to "30m").forEach { (minutes, label) ->
            Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(percent = 50)).background(Color.White.copy(alpha = .14f))
                .clickable { IslandTools.startTimer(context, minutes * 60_000L) }
                .semantics { contentDescription = "Start $minutes minute timer" }.testTag("island-timer-$minutes"),
                contentAlignment = Alignment.Center) {
                Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
    Row(Modifier.fillMaxWidth().height(44.dp).testTag("island-tools-stopwatch"), verticalAlignment = Alignment.CenterVertically) {
        Text(IslandClock.stopwatch(IslandTools.elapsedMs(clockMs)), color = Color.White, fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
        ToolButton(if (IslandTools.swRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
            if (IslandTools.swRunning) "Stop stopwatch" else "Start stopwatch") { IslandTools.toggleStopwatch(context) }
        if (IslandTools.stopwatchStarted && !IslandTools.swRunning) ToolButton(Icons.Rounded.Refresh, "Reset stopwatch") { IslandTools.resetStopwatch(context) }
        ToolButton(if (torch.on) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
            if (torch.on) "Flashlight, on" else "Flashlight", enabled = torch.available) { IslandTools.touch(); torch.toggle() }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick)
        .semantics { contentDescription = label }.testTag("island-tool-${label.lowercase().replace(' ', '-')}"), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White.copy(alpha = if (enabled) 1f else .35f), modifier = Modifier.size(22.dp))
    }
}
