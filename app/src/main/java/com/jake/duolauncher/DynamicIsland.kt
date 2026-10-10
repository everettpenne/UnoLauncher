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
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.ui.input.pointer.PointerEventPass
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
    /** The app a peek came from, so tapping its icon on the island opens that app. */
    var flashComponent by mutableStateOf<android.content.ComponentName?>(null)
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
    /** Set by [IslandSystemEvents] while it listens: asks it to read the audio state again shortly (after a media command). */
    @Volatile var recheckPlayback: (() -> Unit)? = null

    /** The microphone or camera is in use by some app (an Android privacy indicator, shown in the island). */
    var micActive by mutableStateOf(false)
    var cameraActive by mutableStateOf(false)
    /** The screen is being recorded (Android 15+), shown as a red mark. */
    var recordingActive by mutableStateOf(false)
    /** How far a flick has turned the order of the live activities, so a different one can be put in front. */
    var activityShift by mutableIntStateOf(0)
        private set
    fun rotateActivities() { activityShift++ }
    fun rotateBy(places: Int) { activityShift += places }

    private class Flash(val title: String, val icon: androidx.compose.ui.graphics.ImageBitmap?,
        val component: android.content.ComponentName?, val symbol: IslandSymbol)
    private val queued = ArrayDeque<Flash>()

    private fun present(flash: Flash) {
        flashTitle = flash.title
        flashIcon = flash.icon
        flashComponent = flash.component
        flashSymbol = flash.symbol
        flashKey++
    }

    /** A ringer or Focus change replaces another one: only the latest mode matters. Everything else waits its turn. */
    private fun IslandSymbol.isMode() = isRinger() || this == IslandSymbol.FOCUS

    /** Shows [flash] now if the pill is free (or it is the same thing again, or a newer mode change); otherwise it queues
     * behind the one on screen, so two quick events are both seen instead of the second erasing the first.
     */
    private fun offer(flash: Flash) {
        val current = flashSymbol
        when {
            flashTitle == null -> present(flash)
            flashTitle == flash.title && current == flash.symbol -> present(flash)
            current != null && current.isMode() && flash.symbol.isMode() -> { queued.clear(); present(flash) }
            else -> { if (queued.size >= MAX_QUEUED_FLASHES) queued.removeFirst(); queued.addLast(flash) }
        }
    }

    /** Called when the one on screen has had its time: shows the next one waiting, or clears the pill. */
    fun advanceFlash() {
        val next = queued.removeFirstOrNull()
        if (next != null) present(next) else clearFlash()
    }

    private fun clearFlash() { flashTitle = null; flashIcon = null; flashSymbol = null; flashComponent = null }

    val queuedFlashCount: Int get() = queued.size

    fun showLaunch(app: AppEntry) {
        // The user just launched this: it replaces whatever is showing or waiting.
        queued.clear()
        present(Flash(app.label, app.icon.asImageBitmap(), null, IslandSymbol.APP))
    }

    private var lastFocusAt = 0L

    /** Shows [event]. Turning Do Not Disturb on also moves the ringer to silent, so a ringer event
     * arriving right after a Focus change is that change's side effect and is dropped, as iOS shows
     * only the Focus.
     */
    fun showEvent(event: IslandEvent, nowMs: Long = System.currentTimeMillis()) {
        if (event.symbol == IslandSymbol.FOCUS) lastFocusAt = nowMs
        else if (event.symbol.isRinger() && nowMs - lastFocusAt in 0..FOCUS_SIDE_EFFECT_MS) return
        offer(Flash(event.title, null, null, event.symbol))
    }

    /** A notification peek: the app's icon and name, but never the message. */
    fun showPeek(app: AppEntry, label: String) {
        offer(Flash(label, app.icon.asImageBitmap(),
            app.component.takeIf { app.user == android.os.Process.myUserHandle() && it.packageName.isNotEmpty() },
            IslandSymbol.APP))
    }

    fun showCharging(percent: Int?) {
        if (flashTitle == null) showEvent(IslandEvents.charging(percent))
    }

    /** Records whether audio is playing; the stop time is what starts the resume window. */
    fun setPlaying(nowMs: Long, value: Boolean) {
        if (value || playing) lastPlayingAt = nowMs
        playing = value
    }

    fun dismissFlash() { queued.clear(); clearFlash() }
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
    IslandSymbol.VPN -> Icons.Rounded.VpnKey
    IslandSymbol.USB -> Icons.Rounded.Usb
    IslandSymbol.HEADPHONES -> Icons.Rounded.Headphones
    IslandSymbol.RECORDING -> Icons.Rounded.FiberManualRecord
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
    /** Over other apps: the island's frame, and the fixed window rectangle it is drawn in (null when the window wraps it). */
    onFrameChanged: ((IslandFrame, PxRect?) -> Unit)? = null,
    onSearch: () -> Unit,
    onOpenFeed: () -> Unit,
    onCustomize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) { IslandWidgetState.load(context) }
    val now by produceState(LocalDateTime.now()) {
        while (true) { value = LocalDateTime.now(); delay(30_000L) }
    }
    // Wall clock for the resume window; coarse, since the window is 90 seconds long.
    // Ticks faster only while something on the island is counting.
    val clockMs by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(if (IslandTools.swRunning) 100L else if (IslandTools.timerActive) 500L else if (state.expanded && state.playing) 1_000L else if (LiveUpdateLogic.needsSecondTick(NotificationFeed.liveUpdates)) 1_000L else 5_000L)
        }
    }
    // How long until the battery is full, asked of the system only while the island is open on a charging phone.
    val chargeLeft by produceState<String?>(null, state.expanded, deviceStatus.charging, deviceStatus.battery) {
        value = null
        if (state.expanded && deviceStatus.charging == true) {
            val battery = context.getSystemService(android.os.BatteryManager::class.java)
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching { ChargeText.toFull(battery.computeChargeTimeRemaining()) }.getOrNull()
            }
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

    // Opening is lively; closing is critically damped, so the island settles onto the camera without overshooting it.
    val progress by animateFloatAsState(if (state.expanded) 1f else 0f,
        if (state.expanded) spring(dampingRatio = 0.62f, stiffness = 300f) else spring(dampingRatio = 1f, stiffness = 420f),
        label = "island expand")
    val collapsedAlpha = (1f - progress / .5f).coerceIn(0f, 1f)
    val expandedAlpha = ((progress - .5f) / .5f).coerceIn(0f, 1f)
    val flashActive = state.flashTitle != null
    // Playing means audio is active and, when the session is known, that it is not paused (the session answers a pause at once).
    val isPlaying = state.playing && (NotificationFeed.nowPlaying?.playing ?: true)
    // The panel reserves room for the Search / Feed / Customize buttons; over other apps they are not shown, so that room goes.
    val actionsTrimDp = if (showActions) 0f else ACTIONS_ROW_DP
    val mediaVisible = IslandPlayback.controlsVisible(clockMs, state.playing, state.lastPlayingAt)
    val art = NotificationFeed.nowPlaying?.art
    val artImage = remember(art) { art?.asImageBitmap() }
    // The island borrows a colour from the album artwork for the bars and the play button.
    val artTint = remember(art) { art?.let { it.artTintArgb() }?.let { Color(it) } } ?: PLAYBACK_PINK
    // The play/pause icon flips the moment it is tapped; the real state takes over when it arrives (or after a moment).
    var pendingPlaying by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(isPlaying) { pendingPlaying = null }
    LaunchedEffect(pendingPlaying) { if (pendingPlaying != null) { delay(1_500L); pendingPlaying = null } }
    val shownPlaying = pendingPlaying ?: isPlaying
    // Whether the actions have room for their text labels at this panel width (Inter runs wider than the
    // system font); narrower panels get icon-only buttons, with the label kept for accessibility.
    val actionLabelsFit = IslandGeometry.frame(environment, d, 1f, sizeScale).width / d - 36f >= ACTION_LABELS_MIN_DP
    val callActive = NotificationFeed.ongoingCall != null
    val updates = NotificationFeed.liveUpdates
    // Everything live on the collapsed pill, most important first, and which goes in which slot (see IslandLive).
    val liveKinds = IslandLive.rotated(IslandLive.active(camera = state.cameraActive, mic = state.micActive, call = callActive,
        timer = IslandTools.timerActive, stopwatch = IslandTools.swRunning, media = mediaVisible, update = updates.isNotEmpty(), recording = state.recordingActive), state.activityShift)
    val plan = IslandLive.plan(liveKinds)
    // A camera on a side edge has no room beside it unless the pill widens, so live content asks for some.
    val cameraOnSide = environment.cutout?.let {
        IslandGeometry.sideOf(it, environment.screenWidth, environment.screenHeight) != IslandSide.TOP } == true
    val eventTargetDp = if (flashActive) IslandGeometry.eventExtraWidthDp((state.flashTitle ?: "").length, sizeScale)
        else if (cameraOnSide && liveKinds.isNotEmpty()) 60f else 0f
    val eventWidth by animateFloatAsState(eventTargetDp,
        spring(dampingRatio = .6f, stiffness = 420f), label = "island event width")
    // Over other apps the system status bar already shows the time and battery right beside the camera, so while the island has
    // nothing to say it shrinks to a ring around the camera instead of covering those icons. Anything live (an event, a timer,
    // a call, playback) widens it again.
    val hasLiveContent = flashActive || IslandTools.ringing || liveKinds.isNotEmpty()
    val compactTarget = if (anchoredToWindow && !hasLiveContent) 1f else 0f
    val compactness by animateFloatAsState(compactTarget, spring(dampingRatio = .8f, stiffness = 380f), label = "island compact")
    val frame = IslandGeometry.frame(environment, d, progress, sizeScale,
        extraBodyDp = (if (mediaVisible) MEDIA_ROW_DP else 0f) + (if (callActive) CALL_ROW_DP else 0f) + (if (updates.isNotEmpty()) UPDATE_ROW_DP else 0f) + widgetRowDp(IslandTools.toolsOpen) - actionsTrimDp,
        extraWidthDp = eventWidth, compactness = compactness)
    // A capsule is as round as its shorter side allows; turned sideways the pill is taller than it is wide.
    val corner = minOf(minOf(frame.width, frame.height) / 2f, 30f * d) / d
    val sideways = frame.side != IslandSide.TOP
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
        if (state.flashTitle != null) { delay(2600); state.advanceFlash() }
    }
    // An idle expanded island tucks itself back in.
    LaunchedEffect(state.expanded, IslandTools.toolsOpen, IslandTools.interactions, isPlaying) {
        // Longer while music plays, since that is when the buttons are being used; any button press restarts the wait.
        if (state.expanded) { delay(if (IslandTools.toolsOpen) 20_000L else if (isPlaying) 12_000L else 5000L); state.collapse() }
    }

    // Anchored (overlay) mode renders only the island itself: the service positions a
    // WRAP_CONTENT window at the frame, so a fillMaxSize wrapper here would expand the window
    // to cover the screen and swallow every touch. Home mode keeps the full-size wrapper so
    // the island can offset itself to the cutout inside the launcher's window.
    //
    // While the island is open or moving (camera on top), the overlay window is held at the open island's size and the island is
    // drawn at its top centre. The window then does not resize on every animation frame, which made the collapse stutter and
    // lag the content; it is the tight collapsed size again once the island has settled.
    // Over other apps with the camera on top, the island is drawn in a window of one fixed size that covers everywhere the island
    // can be (open, and widened by the longest event), and is placed inside it by offset. The window never resizes while the island
    // moves: a window that changes size shows its old, smaller picture at the new window's corner until the new one arrives, and
    // the island opened from the wrong corner and collapsed through the wrong place. Touches are taken by a separate small window
    // that follows the island (see IslandOverlayHost), so the empty part of this one never blocks the app underneath.
    val windowRect: PxRect? = if (anchoredToWindow && frame.side == IslandSide.TOP) {
        val open = IslandGeometry.frame(environment, d, 1f, sizeScale, extraBodyDp = MEDIA_ROW_DP + CALL_ROW_DP + UPDATE_ROW_DP + IslandWidgetState.ROW_DP + 6f - actionsTrimDp)
        val wide = IslandGeometry.frame(environment, d, 0f, sizeScale, extraWidthDp = MAX_EVENT_EXTRA_DP)
        val pad = 10f * d
        PxRect(
            minOf(open.left, wide.left, frame.left) - pad, minOf(open.top, wide.top, frame.top),
            maxOf(open.left + open.width, wide.left + wide.width, frame.left + frame.width + IslandBubbles.reserveDp(IslandBubbles.MAX) * d) + pad,
            maxOf(open.top + open.height, wide.top + wide.height, frame.top + frame.height) + pad)
    } else null
    val wrapperModifier = if (anchoredToWindow) {
        if (windowRect != null) Modifier.size((windowRect.width / d).dp, (windowRect.height / d).dp) else Modifier
    } else Modifier.fillMaxSize().onGloballyPositioned {
            origin = it.positionInWindow()
            if (environmentOverride == null) {
                val read = readIslandEnvironment(view, dockWidthPx, initialWidth, initialHeight)
                if (read != environment) environment = read
            }
        }
    Box(modifier.then(wrapperModifier), contentAlignment = Alignment.TopStart) {
        LaunchedEffect(frame, windowRect) { onFrameChanged?.invoke(frame, windowRect) }
        // On Home, a tap anywhere off the expanded island tucks it away. (The overlay window cannot cover the screen, so it
        // hears of outside touches from the window manager instead; see IslandOverlayHost.)
        if (state.expanded && !anchoredToWindow) Box(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { state.collapse() }
        }.testTag("island-scrim"))
        // The island's window-pixel position, translated into this parent's own frame. The
        // everywhere-overlay positions its window itself and renders the island at the origin.
        // Pop-out bubbles beside the collapsed pill for the live activities that do not fit in it.
        val bubbleKinds = if (state.expanded || flashActive || IslandTools.ringing) emptyList() else IslandBubbles.extras(liveKinds)
        IslandRuntime.bubbleExtraPx = (IslandBubbles.reserveDp(bubbleKinds.size) * d).roundToInt()
        LaunchedEffect(bubbleKinds.size) { onFrameChanged?.invoke(frame, windowRect) }
        bubbleKinds.forEachIndexed { index, kind ->
            Box(Modifier
                .offset {
                    val x = frame.left + frame.width + (IslandBubbles.GAP_DP + index * (IslandBubbles.SIZE_DP + IslandBubbles.GAP_DP)) * d
                    val y = frame.top + (frame.height - IslandBubbles.SIZE_DP * d) / 2f
                    androidx.compose.ui.unit.IntOffset(
                        (x - (windowRect?.left ?: origin.x)).roundToInt(), (y - (windowRect?.top ?: origin.y)).roundToInt())
                }
                .size(IslandBubbles.SIZE_DP.dp).clip(CircleShape).background(Color.Black)
                .clickable { UnoFeedback.play(Cue.TICK, haptic); state.rotateBy(IslandBubbles.shiftFor(liveKinds, index)) }
                .semantics { contentDescription = "Show ${kind.name.lowercase()}" }
                .testTag("island-bubble-${kind.name.lowercase()}"), contentAlignment = Alignment.Center) {
                LiveSlot(kind, SlotShow.GLYPH, clockMs, isPlaying, artTint, artImage, multi = true, update = updates.firstOrNull())
            }
        }
        Box(Modifier
            .offset { if (anchoredToWindow) {
                androidx.compose.ui.unit.IntOffset(
                    windowRect?.let { (frame.left - it.left).roundToInt() } ?: 0,
                    windowRect?.let { (frame.top - it.top).roundToInt() } ?: 0)
            } else androidx.compose.ui.unit.IntOffset(
                (frame.left - origin.x).roundToInt(), (frame.top - origin.y).roundToInt()) }
            .graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
            .size(width = (frame.width / d).dp, height = (frame.height / d).dp)
            .islandBody(glass?.backdrop, corner.dp, settings = glass?.settings ?: GlassSettings.Default)
            .islandRing(ring, corner * d, 2.5f * d)
            .clip(RoundedCornerShape(corner.dp))
            .pointerInput(state.expanded, liveKinds.size, flashActive) {
                // Swipe up closes (or dismisses a flash), and a sideways flick turns the live activities so another is in front,
                // or sends a flash away. There is no swipe down to open: Android takes a downward drag that starts at the top of
                // the screen for the notification shade and cancels it for us (seen on the emulator: the touch is cancelled
                // exactly where the status bar strip ends), so the island opens with a tap or a long-press instead. Watched in the Initial pass and consumed once the finger has
                // moved past the touch slop, so the click below is cancelled instead of firing at the end of a swipe; a
                // plain tap or long-press never gets past the slop and is left alone. The decision uses the whole movement
                // from touch-down, so a quick flick with few move events still counts.
                val slop = viewConfiguration.touchSlop
                val threshold = 26.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var moved = false
                    var last = down.position
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        last = change.position
                        val delta = last - down.position
                        if (!moved && (abs(delta.x) > slop || abs(delta.y) > slop)) moved = true
                        // A sideways drag on the open island belongs to what is under the finger (the music seek bar).
                        if (moved && !(state.expanded && abs(delta.x) > abs(delta.y))) change.consume()
                        if (!change.pressed) break
                    }
                    if (!moved) return@awaitEachGesture
                    val dx = last.x - down.position.x; val dy = last.y - down.position.y
                    when {
                        abs(dy) >= abs(dx) && dy <= -threshold -> {
                            if (state.expanded) { UnoFeedback.play(Cue.TICK, haptic); state.collapse() }
                            else if (flashActive) { UnoFeedback.play(Cue.TICK, haptic); state.advanceFlash() }
                        }
                        abs(dx) > abs(dy) && abs(dx) >= threshold && !state.expanded -> {
                            if (flashActive) { UnoFeedback.play(Cue.TICK, haptic); state.advanceFlash() }
                            else if (liveKinds.size >= 2) { UnoFeedback.play(Cue.TICK, haptic); state.rotateActivities() }
                        }
                    }
                }
            }
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
            // Where the expanded content starts. Camera on top: below the hole. Camera on a side edge: beside it, on the
            // inward side, so the panel opens away from the edge the camera is on.
            val faceTop = if (hole != null && !sideways) (hole.bottom / d).dp + 6.dp else 12.dp
            val faceStart = if (hole != null && frame.side == IslandSide.LEFT) (hole.right / d).dp + 10.dp else 18.dp
            val faceEnd = if (hole != null && frame.side == IslandSide.RIGHT) ((frame.width - hole.left) / d).dp + 10.dp else 18.dp
            // Collapsed face. With a hole the content is split around it; without one it is centered.
            // Only the face that is showing is composed. An invisible panel would still own its buttons'
            // touch targets, and Compose pads small targets to 48 dp, so the hidden Search button caught
            // taps meant for the pill itself.
            if (progress < .6f && !(compactTarget == 1f && !sideways)) Row(Modifier.fillMaxSize().graphicsLayer { alpha = collapsedAlpha },
                verticalAlignment = Alignment.CenterVertically) {
                val leading: @Composable () -> Unit = {
                    if (flashActive) {
                        val symbol = state.flashSymbol
                        val bitmap = state.flashIcon
                        val component = state.flashComponent
                        // The app's icon opens the app (a child click, so it wins over the pill's own expand tap).
                        if (bitmap != null) Box(Modifier.size(36.dp).clip(CircleShape)
                            .then(if (component != null) Modifier.clickable {
                                UnoFeedback.play(Cue.OPEN, haptic)
                                state.dismissFlash(); state.collapse(); launchIslandApp(context, component)
                            }.testTag("island-peek-app") else Modifier), contentAlignment = Alignment.Center) {
                            Image(bitmap, null, Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)))
                        }
                        else if (symbol != null) Icon(symbol.icon(), null, tint = symbol.tint, modifier = Modifier.size(18.dp))
                    } else if (IslandTools.ringing) {
                        Icon(Icons.Rounded.Alarm, null, tint = IslandSymbol.TIMER.tint, modifier = Modifier.size(18.dp))
                    } else plan.leading?.let { (kind, show) ->
                        LiveSlot(kind, show, clockMs, isPlaying, artTint, artImage, multi = liveKinds.size >= 2, update = updates.firstOrNull())
                    }
                    // Idle: nothing here. The time is in the status bar and on the expanded panel.
                }
                val trailing: @Composable () -> Unit = {
                    if (flashActive) {
                        Text(state.flashTitle ?: "", color = Color.White, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 4.dp))
                    } else if (IslandTools.ringing) {
                        Text("Timer done", color = IslandSymbol.TIMER.tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, modifier = Modifier.testTag("island-timer-done"))
                    } else if (plan.trailing != null) {
                        val (kind, show) = plan.trailing
                        LiveSlot(kind, show, clockMs, isPlaying, artTint, artImage, multi = liveKinds.size >= 2, update = updates.firstOrNull())
                    } else if (deviceStatus.charging == true) {
                        Icon(Icons.Rounded.BatteryChargingFull, null, tint = IosGreen, modifier = Modifier.size(16.dp))
                    } else if (deviceStatus.battery != null) {
                        Text("${deviceStatus.battery}%", color = Color.White.copy(alpha = .9f), fontSize = 12.sp,
                            maxLines = 1)
                    }
                }
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
            if (progress > .4f && IslandTools.toolsOpen) Column(Modifier.fillMaxSize().graphicsLayer { alpha = expandedAlpha }
                .padding(start = faceStart, end = faceEnd, bottom = 12.dp, top = faceTop)) {
                IslandToolsFace(clockMs, torch)
            }
            // Expanded face: live panel, starting below the camera hole.
            if (progress > .4f && !IslandTools.toolsOpen) Column(Modifier.fillMaxSize().graphicsLayer { alpha = expandedAlpha }
                .padding(start = faceStart, end = faceEnd, bottom = 12.dp, top = faceTop)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = Color.White,
                            fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Text(now.format(DateTimeFormatter.ofPattern("EEE, MMM d")), color = Color.White.copy(alpha = .85f),
                            fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (deviceStatus.battery != null) "${deviceStatus.battery}%" else "—",
                            color = if (deviceStatus.charging == true) IosGreen else Color.White,
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        chargeLeft?.let { Text(it, color = IosGreen.copy(alpha = .85f), fontSize = 11.sp, maxLines = 1,
                            modifier = Modifier.testTag("island-charge-left")) }
                    }
                }
                NotificationFeed.ongoingCall?.let { call ->
                    Spacer(Modifier.height(6.dp))
                    CallRow(call, clockMs, onClick = {
                        runCatching { call.openIntent?.send() }; state.collapse()
                    })
                }
                updates.firstOrNull()?.let { u ->
                    Spacer(Modifier.height(6.dp))
                    UpdateCard(u, clockMs, onOpen = { runCatching { u.openIntent?.send() }; state.collapse() })
                }
                if (IslandWidgetState.id >= 0 && !IslandTools.toolsOpen) {
                    Spacer(Modifier.height(6.dp))
                    IslandWidgetPanel(Modifier.fillMaxWidth().height(IslandWidgetState.ROW_DP.dp))
                }
                if (mediaVisible) {
                    Spacer(Modifier.height(6.dp))
                    PlaybackRow(playing = shownPlaying, tint = artTint, title = NotificationFeed.nowPlaying?.title,
                        artist = NotificationFeed.nowPlaying?.artist,
                        art = artImage,
                        onPrevious = { IslandTools.touch(); mediaCommand(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
                        onPlayPause = {
                            IslandTools.touch()
                            pendingPlaying = !shownPlaying
                            mediaCommand(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                        },
                        onNext = { IslandTools.touch(); mediaCommand(context, KeyEvent.KEYCODE_MEDIA_NEXT) },
                        progress = mediaFraction(shownPlaying, clockMs),
                        onSeek = { fraction -> mediaSeek(fraction) },
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

/** The most an event can widen the collapsed pill by, in dp (see IslandGeometry.eventExtraWidthDp). */
internal const val MAX_EVENT_EXTRA_DP = 140f

/** How many flashes can wait behind the one on screen; beyond that the oldest waiting one is dropped. */
internal const val MAX_QUEUED_FLASHES = 3

/** Extra panel height, in dp, when the music card is showing: artwork and track above, the controls below. */
internal const val MEDIA_ROW_DP = 126f
/** The height the open panel reserves for its action buttons, in dp; taken back where they are not shown. */
internal const val ACTIONS_ROW_DP = 44f
/** Extra panel height, in dp, when a Live Update card is showing. */
internal const val UPDATE_ROW_DP = 76f

/** The open island's hosted widget row; see IslandWidget. */
private fun widgetRowDp(toolsOpen: Boolean): Float = if (IslandWidgetState.id >= 0 && !toolsOpen) IslandWidgetState.ROW_DP + 6f else 0f
/** The artwork tile in the music card, in dp. */
internal const val MEDIA_ART_DP = 56f
/** Extra panel height, in dp, when the call card is showing. */
internal const val CALL_ROW_DP = 40f
internal val PLAYBACK_PINK = Color(0xFFFF375F)

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
private fun PlaybackRow(playing: Boolean, tint: Color, title: String?, artist: String? = null,
    art: androidx.compose.ui.graphics.ImageBitmap? = null, onPrevious: () -> Unit, onPlayPause: () -> Unit,
    onNext: () -> Unit, progress: Float? = null, onSeek: ((Float) -> Unit)? = null, onOpenPlayer: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().testTag("island-media")) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .then(if (onOpenPlayer != null) Modifier.clickable(onClick = onOpenPlayer).testTag("island-open-player") else Modifier),
            verticalAlignment = Alignment.CenterVertically) {
            // The cover when the player gives one; otherwise the bars, which are still while paused and move while playing.
            Box(Modifier.size(MEDIA_ART_DP.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .1f)),
                contentAlignment = Alignment.Center) {
                if (art != null) Image(art, null, Modifier.fillMaxSize().testTag("island-art"),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                else EqualizerBars(if (playing) tint else tint.copy(alpha = .55f), animate = playing)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title ?: if (playing) "Playing" else "Paused", color = Color.White, fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("island-track"))
                artist?.let { Text(it, color = Color.White.copy(alpha = .72f), fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        // The track's position, which can be tapped or dragged to seek (the interactive progress of HyperOS's island). Only when the
        // player reports a duration.
        if (progress != null && onSeek != null) SeekBar(progress, tint, onSeek) else Spacer(Modifier.height(14.dp))
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            MediaButton(Icons.Rounded.SkipPrevious, "Previous", onPrevious)
            MediaButton(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (playing) "Pause" else "Play", onPlayPause, tint = tint, size = 48.dp, iconSize = 30.dp)
            MediaButton(Icons.Rounded.SkipNext, "Next", onNext)
        }
    }
}

/** A thin track position bar: tap to jump, drag to scrub. The seek is sent once the finger lifts. */
@Composable
private fun SeekBar(progress: Float, tint: Color, onSeek: (Float) -> Unit) {
    var width by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = (dragging ?: progress).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(14.dp).onSizeChanged { width = it.width.toFloat() }
        .pointerInput(Unit) { detectTapGestures { onSeek((it.x / width).coerceIn(0f, 1f)) } }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(onDragStart = { dragging = (it.x / width).coerceIn(0f, 1f) },
                onDragEnd = { dragging?.let(onSeek); dragging = null }, onDragCancel = { dragging = null }) { change, _ ->
                dragging = (change.position.x / width).coerceIn(0f, 1f)
            }
        }.testTag("island-seek"), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .18f))) {
            Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(tint, RoundedCornerShape(50)))
        }
    }
}

/** Fraction along the current track for the seek bar, or null when the player reports no duration. */
private fun mediaFraction(playing: Boolean, @Suppress("UNUSED_PARAMETER") tick: Long): Float? {
    val controller = NotificationFeed.nowPlaying?.controller ?: return null
    val state = controller.playbackState ?: return null
    val duration = controller.metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: return null
    return MediaProgress.fraction(state.position, state.lastPositionUpdateTime, state.playbackSpeed, playing,
        android.os.SystemClock.elapsedRealtime(), duration)
}

private fun mediaSeek(fraction: Float) {
    val controller = NotificationFeed.nowPlaying?.controller ?: return
    val duration = controller.metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: return
    runCatching { controller.transportControls.seekTo(MediaProgress.seekTarget(fraction, 1f, duration)) }
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
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open call", tint = Color.White.copy(alpha = .7f),
            modifier = Modifier.size(14.dp).padding(end = 4.dp))
    }
}

@Composable
private fun MediaButton(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = Color.White,
    size: androidx.compose.ui.unit.Dp = 44.dp, iconSize: androidx.compose.ui.unit.Dp = 24.dp) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .84f else 1f, spring(dampingRatio = .55f, stiffness = 700f), label = "media press")
    Box(Modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape)
        .clickable(interactionSource = source, indication = null) {
            UnoFeedback.play(Cue.TICK, haptic)
            onClick()
        }, contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(iconSize))
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


private val PRIVACY_MIC = Color(0xFFFF9F0A)

/** One live activity as it appears in a side of the collapsed pill: its small glyph, or its detail (text or bars). */
@Composable
private fun LiveSlot(kind: LiveKind, show: SlotShow, clockMs: Long, playing: Boolean, tint: Color,
    art: androidx.compose.ui.graphics.ImageBitmap?, multi: Boolean, update: LiveUpdate? = null) {
    val glyph = show == SlotShow.GLYPH
    when (kind) {
        LiveKind.CAMERA -> if (glyph) Icon(Icons.Rounded.Videocam, "Camera in use", tint = IosGreen,
            modifier = Modifier.size(16.dp).testTag("island-camera"))
            else Text("Camera", color = IosGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        LiveKind.MIC -> if (glyph) Icon(Icons.Rounded.Mic, "Microphone in use", tint = PRIVACY_MIC,
            modifier = Modifier.size(16.dp).testTag("island-mic"))
            else Text("Mic", color = PRIVACY_MIC, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        LiveKind.RECORDING -> if (glyph) Icon(Icons.Rounded.FiberManualRecord, "Screen is being recorded", tint = IslandSymbol.RECORDING.tint,
            modifier = Modifier.size(16.dp).testTag("island-recording"))
            else Text("Recording", color = IslandSymbol.RECORDING.tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        LiveKind.CALL -> if (glyph) Icon(Icons.Rounded.Call, null, tint = IosGreen, modifier = Modifier.size(14.dp))
            else Text(NotificationFeed.ongoingCall?.caller ?: "", color = Color.White, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 4.dp).testTag("island-call"))
        LiveKind.TIMER -> if (glyph) Icon(Icons.Rounded.Timer, null, tint = IslandSymbol.TIMER.tint, modifier = Modifier.size(16.dp))
            else Text(IslandClock.countdown(IslandClock.remainingMs(clockMs, IslandTools.timerEndAt)), color = IslandSymbol.TIMER.tint,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.testTag("island-timer"))
        LiveKind.STOPWATCH -> if (glyph) Icon(Icons.Rounded.Timer, null, tint = Color.White, modifier = Modifier.size(16.dp))
            else Text(IslandClock.stopwatch(IslandTools.elapsedMs(clockMs)), color = Color.White,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.testTag("island-stopwatch"))
        LiveKind.UPDATE -> if (update != null) {
            if (glyph) UpdateGlyph(update, 22.dp)
            else Text(LiveUpdateLogic.glance(update, clockMs), color = updateTint(update), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, modifier = Modifier.padding(end = 4.dp).testTag("island-live-update"))
        }
        LiveKind.MEDIA -> if (glyph) {
            // The artwork when there is any; otherwise the bars stand in on the left when something else holds the right.
            if (art != null) Image(art, null, Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)))
            else if (multi) EqualizerBars(if (playing) tint else tint.copy(alpha = .55f), animate = playing)
        } else {
            // Stays while a paused player can still be resumed, but only moves while audio is playing.
            EqualizerBars(if (playing) tint else tint.copy(alpha = .55f),
                Modifier.testTag(if (playing) "island-playing" else "island-paused"), animate = playing)
        }
    }
}

/** [ArtTint] on a small copy of the artwork, so it costs next to nothing. */
private fun android.graphics.Bitmap.artTintArgb(): Int? = runCatching {
    val small = android.graphics.Bitmap.createScaledBitmap(this, 24, 24, true)
    val pixels = IntArray(24 * 24)
    small.getPixels(pixels, 0, 24, 0, 0, 24, 24)
    if (small !== this) small.recycle()
    ArtTint.fromPixels(pixels)
}.getOrNull()


/** The colour a Live Update asks for, or the island's green when it asks for none or one too dark to read on black. */
private fun updateTint(update: LiveUpdate): Color {
    val c = Color(update.color)
    return if (update.color == 0 || c.alpha < .5f || (c.red * .299f + c.green * .587f + c.blue * .114f) < .35f) IosGreen else c.copy(alpha = 1f)
}

/** The app's small icon in a soft circle of its colour: what stands in for the app on the pill and the card. */
@Composable
private fun UpdateGlyph(update: LiveUpdate, size: androidx.compose.ui.unit.Dp) {
    val tint = updateTint(update)
    Box(Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = .28f)), contentAlignment = Alignment.Center) {
        val icon = remember(update.icon) { update.icon?.asImageBitmap() }
        if (icon != null) Image(icon, null, Modifier.size(size * .66f), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint))
        else Icon(Icons.Rounded.RssFeed, null, tint = tint, modifier = Modifier.size(size * .6f))
    }
}

/** The open island's card for a Live Update: the app's icon, its title and text, its short status or clock, and a progress bar. */
@Composable
private fun UpdateCard(update: LiveUpdate, clockMs: Long, onOpen: () -> Unit) {
    val tint = updateTint(update)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen).testTag("island-update")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            UpdateGlyph(update, 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(update.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(update.text ?: update.appLabel, color = Color.White.copy(alpha = .72f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(LiveUpdateLogic.glance(update, clockMs), color = tint, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                modifier = Modifier.padding(start = 8.dp))
        }
        LiveUpdateLogic.fraction(update.progress, update.progressMax, update.indeterminate)?.let { f ->
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .18f)).testTag("island-update-progress")) {
                Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(tint))
            }
        }
    }
}
