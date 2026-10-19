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
    /** What tapping the flashed app icon does: the notification's own tap action when it has one, else opening the app. */
    var flashOpen by mutableStateOf<android.app.PendingIntent?>(null)
        private set
    var flashComponent by mutableStateOf<android.content.ComponentName?>(null)
        private set
    var flashSymbol by mutableStateOf<IslandSymbol?>(null)
        private set
    /** The colour of the app icon behind a notification flash, which rings the pill while it shows. */
    var flashTint by mutableStateOf<Color?>(null)
        private set
    /** Bumped on every flash, so the same event twice in a row still pulses and times out afresh. */
    var flashKey by mutableIntStateOf(0)
        private set
    /** The collapsed pill in window coordinates, as the island itself laid it out around the camera; search grows out of it. */
    var restPill by mutableStateOf<IslandFrame?>(null)
    var playing by mutableStateOf(false)
        private set
    var lastPlayingAt by mutableLongStateOf(0L)
        private set
    var audioSilentSince by mutableLongStateOf(0L)
        private set
    /** Set by [IslandSystemEvents] while it listens: asks it to read the audio state again shortly (after a media command). */
    @Volatile var recheckPlayback: (() -> Unit)? = null

    /** The microphone or camera is in use by some app (an Android privacy indicator, shown in the island). */
    var micActive by mutableStateOf(false)
    var cameraActive by mutableStateOf(false)
    /** The screen is being recorded (Android 15+), shown as a red mark. */
    var recordingActive by mutableStateOf(false)
    /** While the small island is being pulled down: how open it is (0..1), following the finger; null when nothing is pulling it. */
    var pullFraction by mutableStateOf<Float?>(null)
    /** The finger's release velocity (px/s, down = positive) from the last pull or swipe-up; seeds the open/close spring and is
     * consumed once by the animation collector, so the island keeps the flick's momentum instead of starting each spring cold. */
    var settleVelocity by mutableFloatStateOf(0f)
    /** Held part-way open after a pull (the peek): enough of the panel to glance at, tucked away again by itself after a few seconds. */
    var peeked by mutableStateOf(false)
    /** A finger is resting on the bottom of the open island, which keeps it open for as long as it stays there. */
    var holdingOpen by mutableStateOf(false)
    /** The last notification card, and when it arrived: for [RecentNotice.WINDOW_MS] it stays one tap away on the open island. */
    var recentCard by mutableStateOf<PeekNotification?>(null)
        private set
    var recentAt by mutableLongStateOf(0L)
        private set
    fun clearRecent() { recentCard = null; recentAt = 0L }
    /** For tests: pretends the last notification arrived at [ms], so the half-minute window can be checked without waiting. */
    internal fun setRecentAtForTest(ms: Long) { recentAt = ms }
    /** When the camera, microphone or screen recording last became in use (0 when none is), for the privacy row's "For 0:42". */
    var privacySince by mutableLongStateOf(0L)
    /** A reply is being typed on the notification card: the card waits for it instead of tucking away on its timer. */
    var peekEditing by mutableStateOf(false)
    /** How far a flick has turned the order of the live activities, so a different one can be put in front. */
    var activityShift by mutableIntStateOf(0)
        private set
    fun rotateActivities() { activityShift++ }
    fun rotateBy(places: Int) { activityShift += places }
    /** The activity the person put in front (by tapping its capsule or flicking), pinned by kind so it stays there while others come and go. */
    var frontKind by mutableStateOf<LiveKind?>(null)
        private set
    fun bringToFront(kind: LiveKind?) { frontKind = kind; swapAtMs = System.currentTimeMillis() }
    /** When the front activity was last changed: the capsule that splits off the pill waits a beat for the one merging into it. */
    var swapAtMs = 0L
        private set

    private class Flash(val title: String, val icon: androidx.compose.ui.graphics.ImageBitmap?,
        val component: android.content.ComponentName?, val symbol: IslandSymbol, val tint: Color? = null,
        val open: android.app.PendingIntent? = null)
    private val queued = ArrayDeque<Flash>()

    private fun present(flash: Flash) {
        flashTitle = flash.title
        flashIcon = flash.icon
        flashComponent = flash.component
        flashOpen = flash.open
        flashSymbol = flash.symbol
        flashTint = flash.tint
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

    private fun clearFlash() { flashTitle = null; flashIcon = null; flashSymbol = null; flashComponent = null; flashOpen = null; flashTint = null }

    val queuedFlashCount: Int get() = queued.size

    fun showLaunch(app: AppEntry) {
        // The user just launched this: it replaces whatever is showing or waiting.
        queued.clear()
        present(Flash(app.label, app.image, null, IslandSymbol.APP))
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
    fun showPeek(app: AppEntry, label: String, open: android.app.PendingIntent? = null) {
        offer(Flash(label, app.image,
            app.component.takeIf { app.user == android.os.Process.myUserHandle() && it.packageName.isNotEmpty() },
            IslandSymbol.APP, AppTints.of(app.packageName) { app.icon }?.let { Color(it) }, open))
    }

    /** Whether the flash on show is a notification's (its app's icon) with somewhere to go when tapped. */
    fun flashOpensSomething() = flashTitle != null && flashSymbol == IslandSymbol.APP && (flashOpen != null || flashComponent != null)

    fun showCharging(percent: Int?) {
        if (flashTitle == null) showEvent(IslandEvents.charging(percent))
    }

    /** Records whether audio is playing; the stop time is what starts the resume window. */
    fun setPlaying(nowMs: Long, value: Boolean) {
        if (value || playing) lastPlayingAt = nowMs
        // When the audio went quiet (kept from the first answer, since the system is asked again a moment later); 0 while it plays.
        if (value) audioSilentSince = 0L else if (playing || audioSilentSince == 0L) audioSilentSince = nowMs
        playing = value
    }

    private var wasPlaying = false
    /** The island's answer to "is it playing" changed (or was re-confirmed): a start or a stop both restart the resume window. */
    fun notePlaybackChange(nowMs: Long, playingNow: Boolean) {
        if (playingNow || wasPlaying) lastPlayingAt = nowMs
        wasPlaying = playingNow
    }

    /** For tests: pretends the media last played at [ms], so the lingering and the window can be checked without waiting minutes. */
    internal fun setLastPlayingAtForTest(ms: Long) { lastPlayingAt = ms }

    /** The player has gone away: nothing to resume, so the controls leave at once. */
    fun endPlayback() { lastPlayingAt = 0L; wasPlaying = false }

    fun dismissFlash() { queued.clear(); clearFlash() }
    /** A new notification shown as a card dropping down from the island (see [PeekNotification]); null when there is none. */
    var peekCard by mutableStateOf<PeekNotification?>(null)
        private set
    /** Bumped on every card, so a second card restarts the wait before the island tucks away. */
    var peekKey by mutableIntStateOf(0)
        private set

    /** Drops the card down, unless the person already has the island open for something else (their music, a timer). */
    fun showPeekCard(peek: PeekNotification) {
        // Remembered even when the card itself cannot be shown now (the island is open for something else, or a timer is ringing), so it is
        // still there to look at afterwards. A turn-by-turn instruction is not a notification to come back to; its own card is on the panel.
        if (!peek.navigation) { recentCard = peek; recentAt = System.currentTimeMillis() }
        if (expanded && peekCard == null) return
        // A ringing timer has the island until it is dealt with.
        if (IslandTools.ringing) return
        dismissFlash()
        peekCard = peek; peekKey++; expanded = true; peeked = false
    }

    fun toggle() { expanded = if (peeked) true else !expanded; peeked = false; peekCard = null; peekEditing = false; dismissFlash() }
    fun collapse() { expanded = false; peeked = false; peekCard = null; peekEditing = false }
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
    IslandSymbol.BLUETOOTH -> Icons.Rounded.BluetoothConnected
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
    /** The corner radius in pixels. A function, read where the island draws, so the radius follows the morph without recomposing. */
    cornerPx: () -> Float,
    rimWidth: Dp = 2.5.dp,
    settings: GlassSettings,
): Modifier {
    val fill = themeFill
    if (backdrop == null) return drawBehind { drawRoundRect(fill, cornerRadius = CornerRadius(cornerPx())) }
    return drawBackdrop(
        backdrop = backdrop,
        shape = { RoundedCornerShape(cornerPx()) },
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
            drawRect(fill.copy(alpha = .55f))
            val rim = rimWidth.toPx()
            drawRoundRect(fill, topLeft = Offset(rim, rim),
                size = Size(size.width - 2 * rim, size.height - 2 * rim),
                cornerRadius = CornerRadius((cornerPx() - rim).coerceAtLeast(0f)))
        },
    )
}

/** What the island is heading for: a finger's pull, the open panel, the peek (and how far it rests). The animation follows changes in this. */
private data class IslandTarget(val pull: Float?, val expanded: Boolean, val peeked: Boolean, val peek: Float)

/** Counts how often the island composes, so a test can hold the morph to a handful of compositions instead of one per frame. */
internal object IslandDiagnostics { @Volatile var compositions = 0 }

/** Gives the island body [frame]'s size, read when it is laid out, so the morph resizes it without recomposing the island. */
private fun Modifier.islandSized(frame: () -> IslandFrame): Modifier = layout { measurable, _ ->
    val f = frame()
    val w = f.width.roundToInt().coerceAtLeast(0); val h = f.height.roundToInt().coerceAtLeast(0)
    val placeable = measurable.measure(Constraints.fixed(w, h))
    layout(w, h) { placeable.place(0, 0) }
}

/** Lays a face out once, at the size of the fully open island, and lets the growing body clip it. The text inside is measured one time instead
 * of being wrapped again on every frame of the morph (the old fill-the-body layout re-wrapped every line each frame, which is what made opening
 * hitch), and it no longer squeezes into a half-open body. It stays anchored the way the body grows: centred under a top camera, against the
 * outer edge for a camera on a side.
 */
private fun Modifier.openSizedFace(open: IslandFrame, frame: () -> IslandFrame): Modifier = layout { measurable, _ ->
    val w = open.width.roundToInt().coerceAtLeast(0); val h = open.height.roundToInt().coerceAtLeast(0)
    val placeable = measurable.measure(Constraints.fixed(w, h))
    val f = frame()
    val bodyW = f.width.roundToInt().coerceAtLeast(0); val bodyH = f.height.roundToInt().coerceAtLeast(0)
    layout(bodyW, bodyH) {
        placeable.place(when (open.side) { IslandSide.LEFT -> 0; IslandSide.RIGHT -> bodyW - w; IslandSide.TOP -> (bodyW - w) / 2 }, 0)
    }
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
    /** The status rail is on screen beside the island and already shows the time and the battery level; see docs/design.md (one owner). */
    statusRailShown: Boolean = false,
    showActions: Boolean = true,
    /** Over other apps: the island's frame, and the fixed window rectangle it is drawn in (null when the window wraps it). */
    onFrameChanged: ((IslandFrame, PxRect?) -> Unit)? = null,
    onSearch: () -> Unit,
    /** When set, the open island has a search field: Enter sends what was typed here (a web search in the browser). */
    onWebSearch: ((String) -> Unit)? = null,
    /** Keep the pill wide when idle, instead of shrinking to a ring round the camera over other apps. */
    alwaysWide: Boolean = false,
    /** Scales the collapsed pill's wide side slots (1f = today's width, 1.5f = Extra wide); see IslandGeometry.frame. */
    wideFactor: Float = 1f,
    /** True when this island renders *below* the system status bar, so the strip's touches belong to SystemUI and a pill pull
     *  must only engage from the part of the pill below the strip. False for the accessibility overlay, which sits above it. */
    belowStatusBar: Boolean = false,
    /** How long paused media worth returning to stays on the open island (and glows round the camera). */
    resumeWindowMs: Long = IslandPlayback.DEFAULT_RESUME_MS,
    onOpenFeed: () -> Unit,
    onCustomize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) { IslandWidgetState.load(context) }
    // The panel's clock follows the phone's 12/24-hour setting, like the status rail's.
    val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
    val timeFormatter = remember(is24Hour) { DateTimeFormatter.ofPattern(ClockFormat.timePattern(is24Hour, meridiem = true)) }
    val now by produceState(LocalDateTime.now()) {
        while (true) { value = LocalDateTime.now(); delay(30_000L) }
    }
    // Wall clock for the resume window; coarse, since the window is 90 seconds long.
    // Ticks faster only while something on the island is counting.
    val clockMs by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(if (IslandTools.swRunning) 100L else if (IslandTools.timerActive) 500L else if ((state.expanded || state.peeked) && (state.playing || state.cameraActive || state.micActive || state.recordingActive || state.recentCard != null)) 1_000L else if (LiveUpdateLogic.needsSecondTick(NotificationFeed.liveUpdates) || NotificationFeed.ongoingCall != null) 1_000L else 5_000L)
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
    // The next alarm, asked of the system (no permission needed) when the island opens; kept while it closes so it does not blink away.
    val nextAlarm by produceState<String?>(null, state.expanded) {
        if (state.expanded) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching { context.getSystemService(android.app.AlarmManager::class.java).nextAlarmClock?.triggerTime }.getOrNull()
                ?.let { NextAlarmText.format(it, System.currentTimeMillis(), is24Hour) }
        }
    }
    // How long the camera, microphone or screen recording has been in use, and the app that was in front when it started. Android does not say which
    // app is using them; the app in front is only context, and only when the person has allowed usage access (Uno never asks for it for this).
    val anyPrivacy = state.cameraActive || state.micActive || state.recordingActive
    LaunchedEffect(anyPrivacy) { state.privacySince = if (anyPrivacy) System.currentTimeMillis() else 0L }
    val inFront by produceState<String?>(null, anyPrivacy) {
        value = null
        if (anyPrivacy) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { runCatching { ForegroundNow.label(context) }.getOrNull() }
    }
    // The apps picked for the control panel's shortcuts, one tap from the open island. Looked up ahead of time (at start and whenever the
    // island is closed), never while it is opening: loading icons mid-animation made opening stutter and changed the island's height half way.
    LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { IslandQuick.refreshIfChanged(context) } }
    LaunchedEffect(state.expanded) {
        if (!state.expanded) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { IslandQuick.refreshIfChanged(context) }
    }
    val quickApps = IslandQuick.cached
    // A timer whose alarm never reached us (the process was stopped, or Android delayed it) must still finish: once the
    // clock passes its end it rings from here. onAlarm ignores a second trigger, so this cannot double up.
    LaunchedEffect(clockMs) {
        if (IslandTools.timerActive && !IslandTools.ringing && clockMs >= IslandTools.timerEndAt) IslandTools.onAlarm(context)
    }
    IslandSystemEvents(state)
    SideEffect { IslandDiagnostics.compositions++ }
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
    LaunchedEffect(environment, sizeScale, d, wideFactor) { state.restPill = IslandGeometry.frame(environment, d, 0f, sizeScale, wideFactor = wideFactor) }
    LaunchedEffect(configuration, environmentOverride) {
        environment = environmentOverride ?: readIslandEnvironment(view, dockWidthPx, initialWidth, initialHeight)
    }

    // Android's animations switched off (Developer options, or "Remove animations"): the island snaps open and shut instead of springing.
    val animationsOn = remember { android.provider.Settings.Global.getFloat(context.contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
    val theme = IslandThemes.active
    // Opening is lively; closing is critically damped, so the island settles onto the camera without overshooting it. Height and width
    // are two springs (see IslandMotion), and either may overshoot the panel's size: that is what makes a bounce, a stretch or a splash.
    //
    // These are kept as State and read only where the island lays out and draws (see frameState below), never in composition. Reading them
    // here recomposed the whole island, with all its faces, on every frame of the morph.
    //
    // The target is the whole panel (1), the peek (a fraction, worked out from what the panel holds), nothing (0), or wherever the finger has
    // pulled it. The peek's size is known only once the panel's content is, further down, so it arrives through [peekState] (set after composition).
    // The widths come out sooner than the heights: a peek is already panel-wide, only not as tall.
    //
    // The animation runs outside composition: a collector turns the state (a pull, expanded, peeked) into targets and drives two Animatables, so a
    // finger dragging the island changes layout and drawing only. Reading the pull in composition (as this did) recomposed the whole island,
    // faces and all, on nearly every touch event, which is what made a pull stutter.
    val peekState = remember { mutableFloatStateOf(.5f) }
    val progress = remember { Animatable(if (state.expanded) 1f else 0f) }
    val widthAnim = remember { Animatable(if (state.expanded) 1f else 0f) }
    val progressState = progress.asState()
    val widthState = widthAnim.asState()
    // The open panel's height in px, to turn the finger's release velocity (px/s) into the 0..1 progress-per-second the spring wants.
    val openHeightPx = remember(environment, d, sizeScale) { IslandGeometry.frame(environment, d, 1f, sizeScale).height }.coerceAtLeast(1f)
    LaunchedEffect(theme, animationsOn) {
        snapshotFlow { IslandTarget(state.pullFraction, state.expanded, state.peeked, peekState.floatValue) }.collectLatest { t ->
            val height = t.pull ?: if (t.expanded) 1f else if (t.peeked) t.peek else 0f
            val wide = if (t.pull != null) (t.pull * 2.5f).coerceAtMost(1f) else if (t.expanded || t.peeked) 1f else 0f
            // The finger's release velocity (px/s, down = positive) is consumed once and seeds the height spring, so a fast
            // swipe-up tucks the island away with momentum and a pull released mid-motion carries on instead of starting cold.
            val releaseVelocity = state.settleVelocity
            state.settleVelocity = 0f
            if (!animationsOn || t.pull != null) { progress.snapTo(height); widthAnim.snapTo(wide) }
            else {
                val opening = t.expanded || t.peeked
                coroutineScope {
                    launch { progress.animateTo(height, if (opening) theme.heightSpring() else theme.closeSpring(), initialVelocity = releaseVelocity / openHeightPx) }
                    launch { widthAnim.animateTo(wide, if (opening) theme.widthSpring() else theme.closeSpring()) }
                }
            }
        }
    }
    // True while a finger is pulling the island; read in composition, it changes twice per pull instead of on every move.
    val pulling by remember { derivedStateOf { state.pullFraction != null } }
    // A coupled motion (jelly) takes its width from its height, the opposite way; the others spring the width on its own.
    val coupling = theme.motion.coupling
    // What is written on the body appears once the body is open in both directions, so a droplet that has dropped but not yet spread
    // does not show a panel's worth of controls squeezed into a narrow drop.
    val revealState = remember(coupling) { derivedStateOf {
        val p = progressState.value
        minOf(p, if (coupling > 0f) IslandMotion.coupledWidth(p, coupling) else widthState.value)
    } }
    // Which faces exist at all changes twice per morph; composition watches these, not the animation.
    val showCollapsedFace by remember(coupling) { derivedStateOf { revealState.value < .35f } }
    val showOpenFace by remember(coupling) { derivedStateOf { revealState.value > .08f } }
    val collapsedAlpha = remember(coupling) { { (1f - revealState.value / .3f).coerceIn(0f, 1f) } }
    // The face is fully there by the time the body is as open as the peek rests (and no later than 40 %), so a held peek is never half faded.
    //
    // The face is also held back until the body is nearly as wide as the panel: it is laid out at the panel's width, so in a narrower body its
    // first words were cut off at the sides (and blurred), as the island opened.
    val expandedAlpha = remember(coupling) { {
        val end = minOf(.4f, peekState.floatValue * .92f)
        val tall = ((revealState.value - (end - .2f)) / .2f).coerceIn(0f, 1f)
        val p = progressState.value
        val wide = ((if (coupling > 0f) IslandMotion.coupledWidth(p, coupling) else widthState.value) - .72f) / .28f
        tall * wide.coerceIn(0f, 1f)
    } }
    val pastHalfOpen by remember { derivedStateOf { progressState.value >= .5f } }
    val flashActive = state.flashTitle != null
    // Tapping a notification's flash: its own tap action, or the app when it has none. The values are read before the flash is cleared.
    val openFlash: () -> Unit = {
        val open = state.flashOpen
        val component = state.flashComponent
        UnoFeedback.play(Cue.OPEN, haptic)
        state.dismissFlash(); state.collapse()
        if (!sendTapAction(context, open)) component?.let { launchIslandApp(context, it) }
    }
    val nowPlaying = NotificationFeed.nowPlaying
    // The paused controls leave exactly when their window ends, not at the next tick of a slow clock: these are told the moment to look again.
    var windowTick by remember { mutableLongStateOf(0L) }
    var silenceTick by remember { mutableLongStateOf(0L) }
    val phaseNow = maxOf(clockMs, windowTick, silenceTick)
    // Playing means the player says so (its session answers a pause at once) and audio is coming out; with no session, audio alone decides.
    // A session that says "playing" over silence (a muted autoplay video) stops counting after a few seconds.
    val audible = IslandPlayback.sessionAudible(state.playing, state.audioSilentSince, phaseNow)
    val isPlaying = IslandPlayback.effective(state.playing, nowPlaying?.playing, audible)
    LaunchedEffect(state.audioSilentSince, nowPlaying?.playing) {
        if (nowPlaying?.playing == true && !state.playing && state.audioSilentSince > 0L) {
            delay(IslandPlayback.SILENT_GRACE_MS + 50L)
            silenceTick = System.currentTimeMillis()
        }
    }
    // Whether the paused media is worth coming back to (a title, a position, more than 30 seconds of it, not at the end).
    val resumable = nowPlaying != null && IslandPlayback.resumable(nowPlaying.title, nowPlaying.stateCode, nowPlaying.positionMs, nowPlaying.durationMs)
    // The panel reserves room for the Search / Feed / Customize buttons; over other apps they are not shown, so that room goes.
    val actionsTrimDp = if (showActions) 0f else ACTIONS_ROW_DP
    LaunchedEffect(isPlaying, state.playing, resumable, resumeWindowMs) {
        // The session and the audio system both feed "is it playing"; the lingering starts when the answer last was yes.
        state.notePlaybackChange(System.currentTimeMillis(), isPlaying)
        while (true) {
            val wait = IslandPlayback.nextChangeMs(System.currentTimeMillis(), isPlaying, state.lastPlayingAt, resumable, resumeWindowMs) ?: break
            delay(wait + 50L)
            windowTick = System.currentTimeMillis()
        }
    }
    // A player that closes (its session ends) takes its controls with it unless something is still playing.
    val sessionPresent = nowPlaying != null
    LaunchedEffect(sessionPresent) { if (!sessionPresent && !state.playing) state.endPlayback() }
    val mediaPhase = IslandPlayback.phase(phaseNow, isPlaying, state.lastPlayingAt, resumable, resumeWindowMs)
    // The card in the open island stays for as long as there is anything to resume; the pill itself only carries playing and lingering media.
    val mediaVisible = mediaPhase != IslandPlayback.Phase.GONE
    val mediaOnPill = mediaPhase == IslandPlayback.Phase.PLAYING || mediaPhase == IslandPlayback.Phase.LINGER
    val art = NotificationFeed.nowPlaying?.art
    val artImage = remember(art) { art?.asImageBitmap() }
    // The island borrows a colour from the album artwork for the bars and the play button.
    val artTint = remember(art) { art?.let { it.artTintArgb() }?.let { Color(it) } } ?: IslandThemes.active.playback
    // The play/pause icon flips the moment it is tapped; the real state takes over when it arrives (or after a moment).
    var pendingPlaying by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(isPlaying) { pendingPlaying = null }
    LaunchedEffect(pendingPlaying) { if (pendingPlaying != null) { delay(1_500L); pendingPlaying = null } }
    val shownPlaying = pendingPlaying ?: isPlaying
    // Whether the actions have room for their text labels at this panel width (Inter runs wider than the
    // system font); narrower panels, or a larger system font, get icon-only buttons, with the label kept for accessibility.
    val actionLabelsFit = remember(environment, d, sizeScale, configuration.fontScale) {
        PanelHeight.actionsFit(IslandGeometry.frame(environment, d, 1f, sizeScale).width / d, configuration.fontScale)
    }
    IslandDebug.init(context)
    val forced = IslandDebug.forced
    val callActive = NotificationFeed.ongoingCall != null || LiveKind.CALL in forced
    val updates = if (LiveKind.UPDATE in forced) listOf(IslandDebug.dummyUpdate(clockMs)) + NotificationFeed.liveUpdates else NotificationFeed.liveUpdates
    // How much the collapsed pill shows of a Live Update: 0 glyph only, 1 the short status, 2 the title (see ExtrasStore.liveCompactLevel).
    // Read straight from the extras prefs, the way the overlay host reads the other island choices, so it follows a settings change.
    val liveCompactLevel by produceState(initialValue = 1) {
        val prefs = context.getSharedPreferences("extras", Context.MODE_PRIVATE)
        fun read() = prefs.getInt("liveCompactLevel", 1).coerceIn(0, 2)
        value = read()
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "liveCompactLevel") value = read()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    // Everything live on the collapsed pill, most important first, and which goes in which slot (see IslandLive).
    val canonicalKinds = IslandLive.active(call = callActive,
        timer = IslandTools.timerActive || LiveKind.TIMER in forced, stopwatch = IslandTools.swRunning || LiveKind.STOPWATCH in forced,
        media = mediaOnPill || LiveKind.MEDIA in forced,
        update = updates.any { !LiveUpdateLogic.isStale(it, clockMs) })
    // A pinned activity that has ended is forgotten, so it does not jump to the front again if it restarts much later.
    LaunchedEffect(canonicalKinds) { if (state.frontKind != null && state.frontKind !in canonicalKinds) state.bringToFront(null) }
    val liveKinds = IslandLive.withFront(canonicalKinds, state.frontKind)
    val plan = IslandLive.plan(liveKinds)
    // The dots beside the pill: what the phone really reports, plus any forced on from the debug page.
    val privacyNow = IslandLive.privacy(state.cameraActive || LiveKind.CAMERA in forced, state.micActive || LiveKind.MIC in forced,
        state.recordingActive || LiveKind.RECORDING in forced)
    // A camera on a side edge has no room beside it unless the pill widens, so live content asks for some.
    val cameraOnSide = environment.cutout?.let {
        IslandGeometry.sideOf(it, environment.screenWidth, environment.screenHeight) != IslandSide.TOP } == true
    val eventTargetDp = if (flashActive) IslandGeometry.eventExtraWidthDp((state.flashTitle ?: "").length, sizeScale, configuration.fontScale)
        else if (cameraOnSide && liveKinds.isNotEmpty()) 60f else 0f
    val eventWidthState = animateFloatAsState(eventTargetDp,
        if (!animationsOn) snap() else theme.spring(.6f, 420f), label = "island event width")
    // Over other apps the system status bar already shows the time and battery right beside the camera, so while the island has
    // nothing to say it shrinks to a ring around the camera instead of covering those icons. Anything live (an event, a timer,
    // a call, playback) widens it again.
    val hasLiveContent = flashActive || IslandTools.ringing || liveKinds.isNotEmpty()
    val compactTarget = if (anchoredToWindow && !hasLiveContent && !alwaysWide) 1f else 0f
    val compactnessState = animateFloatAsState(compactTarget, if (!animationsOn) snap() else theme.spring(.8f, 380f), label = "island compact")
    // What the open island is showing, held while it closes. collapse() clears the card at once, but the island takes a
    // moment to shrink; reading it live made the face (and the body's height) switch to the ordinary open island, with the app shortcuts
    // and settings, for that moment before it reached the camera. While closing, the face and size it had when it was open are kept.
    var heldPeek by remember { mutableStateOf<PeekNotification?>(null) }
    if (state.expanded) { heldPeek = state.peekCard }
    val peekShown = if (state.expanded) state.peekCard else heldPeek
    // A notification from the last 30 seconds is held for the open island (its card has long since gone), and a navigation instruction gets a tall card.
    // The held notification goes the moment its half minute ends, not at the next tick of a slow clock.
    var recentTick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.recentAt) {
        if (state.recentAt > 0L) {
            delay((RecentNotice.WINDOW_MS - (System.currentTimeMillis() - state.recentAt)).coerceAtLeast(0L) + 50L)
            recentTick = System.currentTimeMillis()
        }
    }
    val recent = state.recentCard
    val recentLive = recent != null && RecentNotice.live(state.recentAt, maxOf(clockMs, recentTick, state.recentAt))
    val navCard = updates.firstOrNull()?.let { it.source == ActivitySource.NAVIGATION && it.largeIcon != null } == true
    val updateRowDp = if (navCard) NavigationText.CARD_ROW_DP else UPDATE_ROW_DP
    // The open panel's first line names why it is showing what it is showing: the one live thing that has the island right now.
    val narration = IslandNarration.label(
        cardShown = peekShown != null,
        callActive = callActive,
        navigating = navCard,
        ringing = IslandTools.ringing,
        timerActive = IslandTools.timerActive,
        stopwatchRunning = IslandTools.swRunning,
        mediaShown = mediaVisible,
        liveUpdate = updates.isNotEmpty(),
        charging = deviceStatus.charging == true,
    )
    // The reply field is a little taller than the plain action buttons, so its send button keeps a full 44 dp touch target.
    val actionsRowDp = if (state.peekEditing) REPLY_ROW_DP else PEEK_ACTIONS_ROW_DP
    val extraBody = if (peekShown != null) (if (peekShown.actions.isNotEmpty()) actionsRowDp else 0f) + (if (peekShown.navigation) NavigationText.TURN_CARD_EXTRA_DP else 0f)
        else (if (anyPrivacy) PRIVACY_ROW_DP else 0f) +
        (if (recentLive) RECENT_ROW_DP + (if (recent!!.actions.isNotEmpty()) actionsRowDp else 0f) else 0f) +
        (if (mediaVisible) MEDIA_ROW_DP else 0f) + (if (callActive) CALL_ROW_DP else 0f) + (if (updates.isNotEmpty()) updateRowDp else 0f) + widgetRowDp() +
        (if (quickApps.isNotEmpty()) QUICK_ROW_DP else 0f) +
        (if (narration != null) NARRATION_ROW_DP else 0f) +
        // The headline and a larger system font need room the panel's base height does not have; without it the Search / Feed / Settings row
        // was cut off the bottom of the open island whenever the feed had a headline to show.
        PanelHeight.extraDp(feedHeadline != null, configuration.fontScale) - actionsTrimDp
    // The fully open island at its current content. Which side the camera is on, where it sits in the island and how big the open panel is do
    // not change during the morph, so the faces are laid out from this one frame. [frameState] is the live frame; it is read only when the
    // island lays out, draws and positions itself.
    val openFrame = remember(environment, d, sizeScale, extraBody) { IslandGeometry.frame(environment, d, 1f, sizeScale, extraBodyDp = extraBody) }
    // While the island shows an app's icon and name (an app opening, or a notification's app) it springs open wider and a touch left, and
    // goes back to its resting width when that is dismissed. The live activities have tucked into it for the time (see detachedCapsules).
    val showingApp = flashActive && state.flashSymbol == IslandSymbol.APP
    val mainBase = if (showingApp) IslandLayoutDefaults.MAIN_SHOWING_APP else IslandLayoutDefaults.MAIN
    val mainSpring = if (!animationsOn) snap<Float>() else theme.spring(.55f, 380f)
    val mainDw by animateFloatAsState(mainBase.dw + IslandDebug.pill.dw, mainSpring, label = "main width")
    val mainDx by animateFloatAsState(mainBase.dx + IslandDebug.pill.dx, mainSpring, label = "main across")
    val mainDy = mainBase.dy + IslandDebug.pill.dy
    val frameState = remember(environment, d, sizeScale, extraBody, coupling, wideFactor) { derivedStateOf {
        val p = progressState.value
        IslandGeometry.frame(environment, d, if (coupling > 0f) IslandMotion.coupledWidth(p, coupling) else widthState.value, sizeScale,
            extraBodyDp = extraBody, extraWidthDp = eventWidthState.value, compactness = compactnessState.value, wideFactor = wideFactor, heightProgress = p)
            // The main island's shipped width and place (a little wider than automatic; wider still while it shows an app's icon and name,
            // see [mainDw]), plus any hand adjustment from the debug page of a debug build.
            .let { withDebugAdjust(it, 1f - p, mainDw, mainDx, mainDy, d, environment.screenWidth) }
    } }
    // The collapsed pill's top edge in window pixels: below it (in the launcher's own space) a pull belongs to the island; above it the
    // system status-bar strip owns the touch, so a pull must not engage there when the island renders below SystemUI.
    val collapsedTopPx = remember(environment, d, sizeScale) { IslandGeometry.frame(environment, d, 0f, sizeScale).top }
    // The collapsed pill's height in px. App icons and thumbnails are circles sized from this (diameter = height - ~8 px), so they stay
    // concentric inside the pill and scale with the island size setting. The detached capsules share this height, so their glyphs match.
    val collapsedHeightPx = remember(environment, d, sizeScale) { IslandGeometry.frame(environment, d, 0f, sizeScale).height }
    val glyphDiameterDp = ((collapsedHeightPx - GLYPH_INSET_PX).coerceAtLeast(MIN_GLYPH_PX) / d).dp
    val capsuleHeightDp = (collapsedHeightPx / d).dp
    // How far open the peek rests: enough of the open panel to read its top (the time, then its first row, if it has one). The animation above
    // reads this through [peekState], so the panel's own content decides how much a peek shows.
    run {
        val collapsedHeight = collapsedHeightPx
        val firstRowDp = when {
            recentLive -> RECENT_ROW_DP + (if (recent!!.actions.isNotEmpty()) PEEK_ACTIONS_ROW_DP else 0f)
            anyPrivacy -> PRIVACY_ROW_DP
            callActive -> CALL_ROW_DP
            updates.isNotEmpty() -> updateRowDp
            mediaVisible -> 76f
            else -> 0f
        }
        val faceTopPx = openFrame.hole?.bottom ?: collapsedHeight
        val wanted = (faceTopPx / d + 6f + (if (narration != null) NARRATION_ROW_DP else 0f) + 44f + firstRowDp + 10f) * d
        val fraction = ((wanted - collapsedHeight) / (openFrame.height - collapsedHeight).coerceAtLeast(1f)).coerceIn(.25f, .85f)
        SideEffect { peekState.floatValue = fraction }
    }
    // What the touch window is told. It used to follow the island's size on every animation frame, which is a call to the window
    // manager per frame while the island opens and closes. Now it is told the size the island is heading for as soon as a change
    // starts, and kept at the largest size seen until the animation has settled, then snapped to the settled size.
    val headingFor = remember(environment, d, sizeScale, extraBody, state.expanded, state.peeked, eventTargetDp, compactTarget, wideFactor) {
        IslandGeometry.frame(environment, d, if (state.expanded) 1f else if (state.peeked) peekState.floatValue else 0f, sizeScale, extraBodyDp = extraBody,
            extraWidthDp = eventTargetDp, compactness = compactTarget, wideFactor = wideFactor) }
    val reported = remember { arrayOfNulls<IslandFrame>(1) }
    val bodyFade = remember(openFrame, d) { BodyFade({ frameState.value.height }, openFrame.height, 10f * d) }
    val morphSpeed = remember { MorphVelocity() }
    val squashGain = ((theme.motion.pulse - 1f).coerceIn(0f, .3f) / .1f).coerceAtLeast(.25f)
    // A capsule is as round as its shorter side allows; turned sideways the pill is taller than it is wide.
    val panelScale = theme.panel.scale
    val cornerPx = remember(panelScale, d) { derivedStateOf {
        val f = frameState.value
        val half = minOf(f.width, f.height) / 2f
        if (panelScale < 1f) minOf(half, IslandGeometry.CORNER_CAP_DP * d) * panelScale else minOf(half, IslandGeometry.CORNER_CAP_DP * d * panelScale)
    } }
    val sideways = openFrame.side != IslandSide.TOP
    // The colour fade for music: while the music is one of the things the pill shows (or the panel is open), and nothing else has the island.
    val glowAlpha = animateFloatAsState(
        IslandGlow.target(mediaVisible && (state.expanded || liveKinds.take(2).contains(LiveKind.MEDIA)), isPlaying,
            flashActive || IslandTools.ringing || peekShown != null), tween(650), label = "island media glow")
    // The glow's flame stirs only while audio is really playing (not paused or lingering), and not at all with Android's animations off.
    val flameAnimated = isPlaying && mediaVisible && animationsOn
    val flameT by rememberFlameClock(flameAnimated)
    // The glow's two gradients are cached for the track's lifetime. The animated alpha is applied at draw time (never baked into the
    // colours), and the tongue is a unit gradient scaled at draw time, so its moving centre and breathing height never rebuild it.
    // Building both brushes (and their colour lists) on every frame while audio played is what this replaces.
    val glowWash = remember(artTint) {
        androidx.compose.ui.graphics.Brush.verticalGradient(
            listOf(Color.Transparent, artTint.copy(alpha = .16f), artTint.copy(alpha = .55f)))
    }
    val glowTongue = remember(artTint, flameAnimated) {
        androidx.compose.ui.graphics.Brush.radialGradient(
            listOf(artTint.copy(alpha = .5f * if (flameAnimated) 1f else .6f), Color.Transparent),
            center = Offset.Zero, radius = 1f)
    }
    // A progress ring around the collapsed pill: a running timer, or the battery level while charging.
    // The status rail owns the battery level when it is shown, so the island draws a level ring only while
    // charging with the rail off (IslandEventArbiter.chargingRingSurface); the charging icon stays either way.
    val ring = IslandRingLogic.choose(flashActive, pastHalfOpen || state.expanded || state.peeked || pulling, IslandTools.ringing,
        IslandTools.timerActive, 1f - IslandClock.progress(clockMs, IslandTools.timerEndAt, IslandTools.timerTotal),
        IslandEventArbiter.chargingRingSurface(statusRailShown, deviceStatus.charging == true), deviceStatus.battery, IslandThemes.active.timer, IslandThemes.active.charging, state.flashTint)
    // A new event gives the island a small spring pulse, like iOS's.
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(state.flashKey) {
        if (state.flashKey > 0) {
            pulse.snapTo(1f)
            pulse.animateTo(IslandThemes.active.motion.pulse, IslandThemes.active.spring(.45f, 700f))
            pulse.animateTo(1f, IslandThemes.active.spring(.4f, 260f))
        }
    }
    // A capsule melting into the pill (an activity ended, or was brought to the front) gives it the same small pulse, a touch softer.
    var absorbTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(absorbTick) {
        if (absorbTick > 0 && animationsOn) {
            pulse.snapTo(1f)
            pulse.animateTo(1f + (IslandThemes.active.motion.pulse - 1f) * .7f, IslandThemes.active.spring(.45f, 700f))
            pulse.animateTo(1f, IslandThemes.active.spring(.4f, 260f))
        }
    }
    // Flash returns to the collapsed clock after a beat.
    LaunchedEffect(state.flashKey) {
        if (state.flashTitle != null) { delay(2600); state.advanceFlash() }
    }
    // An idle open island tucks itself back in. A finger resting on its bottom holds it open, and a pull in progress is not idle.
    LaunchedEffect(state.expanded, state.peeked, pulling, state.holdingOpen, IslandTools.interactions, isPlaying, state.peekKey, state.peekEditing) {
        if (state.holdingOpen || pulling) return@LaunchedEffect
        // The peek is only a glance: it goes by itself after a few seconds.
        if (state.peeked && !state.expanded) { delay(IslandDrag.PEEK_HOLD_MS); state.peeked = false; return@LaunchedEffect }
        // Longer while music plays, since that is when the buttons are being used; any button press restarts the wait. A notification card
        // stays for the time chosen for its app.
        if (state.expanded) {
            val peek = state.peekCard
            delay(if (peek != null && state.peekEditing) 30_000L else if (peek != null) peek.seconds * 1000L else if (isPlaying) 12_000L else 5000L)
            state.collapse()
        }
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
    val windowRect: PxRect? = if (anchoredToWindow && openFrame.side == IslandSide.TOP) {
        // The biggest the island gets, in both directions: worked out once for the environment, not on every animation frame.
        // Sized for the island at its most overshot (a bounce carries it past the panel), so a bounce is never clipped by the window.
        val open = remember(environment, d, sizeScale, actionsTrimDp) { IslandGeometry.frame(environment, d, IslandGeometry.MAX_OVERSHOOT, sizeScale,
            extraBodyDp = MEDIA_ROW_DP + CALL_ROW_DP + NavigationText.CARD_ROW_DP + RECENT_ROW_DP + PEEK_ACTIONS_ROW_DP + IslandWidgetState.ROW_DP + 6f + QUICK_ROW_DP + PanelHeight.MAX_EXTRA_DP + PRIVACY_ROW_DP - actionsTrimDp, heightProgress = IslandGeometry.MAX_OVERSHOOT) }
        val wide = remember(environment, d, sizeScale, wideFactor) { IslandGeometry.frame(environment, d, 0f, sizeScale, extraWidthDp = MAX_EVENT_EXTRA_DP, wideFactor = wideFactor) }
        val slack = IslandDebug.slackDp() * d
        val pad = 10f * d + slack
        // The live frame is always inside the open and the widest pill, so it needs no say here (and the rectangle does not change per frame).
        // Room is kept on the left for the left-hand capsule, and (with the debug controls in use) all round for hand-set positions.
        PxRect(
            minOf(open.left, wide.left - (capsuleHeightDp.value + IslandBubbles.GAP_DP) * d) - pad, minOf(open.top, wide.top) - slack,
            maxOf(open.left + open.width, wide.left + wide.width + (IslandBubbles.reserveDp(1 + IslandBubbles.MAX, capsuleHeightDp.value) + IslandPrivacy.widthDp(3)) * d) + pad,
            maxOf(open.top + open.height, wide.top + wide.height) + pad)
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
    // Everything on the island sets its own font size, but a Text that does not also set a line height takes the theme's body one (22 sp, made for
    // 17 sp text). A 12 sp date or a 13 sp headline then took a row 22 dp tall, and the open island ran out of room before its last row.
    // With the line height left to the font, each row is as tall as its text, which is what the panel's height budget assumes.
    CompositionLocalProvider(androidx.compose.material3.LocalTextStyle provides
        androidx.compose.material3.LocalTextStyle.current.copy(lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified)) {
    Box(modifier.then(wrapperModifier), contentAlignment = Alignment.TopStart) {
        LaunchedEffect(headingFor, windowRect) {
            // Told when the frame the touch window should have changes; during a morph that is only while the island is growing.
            var first = true
            snapshotFlow { frameState.value }.collect { live ->
                val next = if (live.nearly(headingFor)) headingFor else (reported[0] ?: live).union(live).union(headingFor)
                if (first || next != reported[0]) { reported[0] = next; onFrameChanged?.invoke(next, windowRect) }
                first = false
            }
        }
        // On Home, a tap anywhere off the expanded island tucks it away. (The overlay window cannot cover the screen, so it
        // hears of outside touches from the window manager instead; see IslandOverlayHost.)
        if ((state.expanded || state.peeked) && !anchoredToWindow) Box(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { state.collapse() }
        }.testTag("island-scrim"))
        // The island's window-pixel position, translated into this parent's own frame. The
        // everywhere-overlay positions its window itself and renders the island at the origin.
        // Detached capsules beside the collapsed pill: the trailing activity, then the live activities that do not fit
        // (Apple's trailing capsule plus Uno's extra "bubbles", all as the same capsule). The pill itself carries only the leading.
        val capsulesShown = !(state.expanded || state.peeked || pulling || flashActive || IslandTools.ringing)
        // Camera, microphone and screen recording are dots beside the pill, always, and not activities: the pill and the capsules show
        // what the person is doing, the dots show what the phone is doing. The right-hand capsules start after the dots.
        val privacyKinds = privacyNow
        val dotsDp = IslandPrivacy.widthDp(privacyKinds.size)
        // With three or more live, the second is a capsule on the pill's left and the others go to its right; only as many circles as fit
        // before the screen's edge are drawn on the right (a fourth would run off it), the rest stay reachable by a flick.
        val detachedCapsules = if (!capsulesShown) emptyList() else IslandBubbles.layout(liveKinds).let { all ->
            all.filter { it.left } + all.filter { !it.left }.take(
                IslandBubbles.capacity((environment.screenWidth - (headingFor.left + headingFor.width)) / d, capsuleHeightDp.value, dotsDp))
        }
        val rightCapsules = detachedCapsules.filter { !it.left }
        val leftAdjust = IslandDebug.effective(DebugTarget.LEFT); val rightAdjust = IslandDebug.effective(DebugTarget.RIGHT); val extraAdjust = IslandDebug.effective(DebugTarget.EXTRA)
        // The touch window is exactly what is drawn beside the pill: room for the right-hand capsules, for the left one, and for a capsule
        // set higher or lower than the pill's row. Nothing more, or it would take touches meant for the app underneath.
        IslandRuntime.bubbleExtraPx = rightCapsules.lastOrNull()?.let { last ->
            ((IslandBubbles.rightOffsetDp(last.slot, capsuleHeightDp.value, dotsDp, rightAdjust, extraAdjust) + capsuleHeightDp.value +
                IslandBubbles.groupFor(last, leftAdjust, rightAdjust, extraAdjust).dw) * d).roundToInt().coerceAtLeast(0)
        } ?: 0
        IslandRuntime.bubbleLeftPx = if (detachedCapsules.any { it.left })
            ((IslandBubbles.leftOffsetDp(leftAdjust) + capsuleHeightDp.value + leftAdjust.dw) * d).roundToInt().coerceAtLeast(0) else 0
        IslandRuntime.touchAbovePx = detachedCapsules.maxOfOrNull { -IslandBubbles.groupFor(it, leftAdjust, rightAdjust, extraAdjust).dy }?.coerceAtLeast(0f)?.times(d)?.roundToInt() ?: 0
        IslandRuntime.touchBelowPx = detachedCapsules.maxOfOrNull { IslandBubbles.groupFor(it, leftAdjust, rightAdjust, extraAdjust).dy }?.coerceAtLeast(0f)?.times(d)?.roundToInt() ?: 0
        LaunchedEffect(detachedCapsules.size, privacyKinds.size) { onFrameChanged?.invoke(reported[0] ?: headingFor, windowRect) }
        val heldDots = rememberLingering(privacyKinds, keepGhosts = capsulesShown)
        heldDots.held.forEach { dot ->
            val kind = dot.item; val index = dot.index
            key(kind) {
                // A dot springs up from nothing when the camera, microphone or recording starts, and back down to nothing when it stops.
                val grow = remember { Animatable(if (animationsOn) 0f else 1f) }
                LaunchedEffect(dot.leaving) {
                    grow.animateTo(if (dot.leaving) 0f else 1f, if (animationsOn) theme.spring(.7f, 520f) else snap())
                    if (dot.leaving) heldDots.gone(kind)
                }
                Box(Modifier
                    .offset {
                        val frame = frameState.value
                        val x = frame.left + frame.width + (IslandPrivacy.GAP_DP + index * IslandPrivacy.STEP_DP) * d
                        val y = frame.top + (frame.height - IslandPrivacy.DOT_DP * d) / 2f
                        androidx.compose.ui.unit.IntOffset(
                            (x - (windowRect?.left ?: origin.x)).roundToInt(), (y - (windowRect?.top ?: origin.y)).roundToInt())
                    }
                    .graphicsLayer { val sc = grow.value.coerceAtLeast(0f); scaleX = sc; scaleY = sc; alpha = grow.value.coerceIn(0f, 1f) }
                    .size(IslandPrivacy.DOT_DP.dp).clip(CircleShape)
                    .background(when (kind) { LiveKind.CAMERA -> IosGreen; LiveKind.MIC -> PRIVACY_MIC; else -> IslandSymbol.RECORDING.tint })
                    .semantics { contentDescription = when (kind) { LiveKind.CAMERA -> "Camera in use"; LiveKind.MIC -> "Microphone in use"; else -> "Screen is being recorded" } }
                    .testTag(when (kind) { LiveKind.CAMERA -> "island-camera"; LiveKind.MIC -> "island-mic"; else -> "island-recording" }))
            }
        }
        val heldCapsules = rememberLingering(detachedCapsules, keepGhosts = capsulesShown)
        heldCapsules.held.forEach { capsule ->
            val shape = capsule.item; val kind = shape.kind
            key(kind) {
                // A capsule leaves the pill like a drop pulling away: it starts inside the pill's end, travels out on a springy overshoot,
                // and for a moment stays joined to the pill by a neck that stretches thin and pinches off (see Metaball).
                val appear = remember { Animatable(if (animationsOn) 0f else 1f) }
                // When the activity ends or is brought to the front, this plays backwards: the capsule slides back into the pill on the same neck
                // and melts into it, and the pill pulses. A capsule that splits off right after one merged in waits a beat, so the two read
                // as one hand-over: the front one merges, then the old front detaches.
                LaunchedEffect(capsule.leaving) {
                    if (!animationsOn) {
                        appear.snapTo(if (capsule.leaving) 0f else 1f)
                        if (capsule.leaving) { absorbTick++; heldCapsules.gone(shape) }
                    } else if (capsule.leaving) {
                        appear.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
                        absorbTick++; heldCapsules.gone(shape)
                    } else {
                        if (appear.value < .05f && System.currentTimeMillis() - state.swapAtMs < 400L) delay(150L)
                        appear.animateTo(1f, theme.spring(.5f, 240f))
                    }
                }
                // What a capsule shows while it leaves is what it showed last: its live data may already be gone.
                val lastUpdate = remember { arrayOfNulls<LiveUpdate>(1) }
                if (!capsule.leaving) lastUpdate[0] = updates.firstOrNull()
                val shownUpdate = if (capsule.leaving) lastUpdate[0] else updates.firstOrNull()
                val capsuleH = collapsedHeightPx
                // When the order changes (a tap or a flick) each capsule glides to its new place and grows or shrinks to the new width,
                // instead of jumping, so the person can follow where each activity went.
                val glide = if (!animationsOn) snap<Float>() else theme.spring(.75f, 380f)
                val adjust = IslandBubbles.groupFor(shape, IslandDebug.effective(DebugTarget.LEFT), IslandDebug.effective(DebugTarget.RIGHT), IslandDebug.effective(DebugTarget.EXTRA))
                val offsetDp by animateFloatAsState(
                    if (shape.left) IslandBubbles.leftOffsetDp(IslandDebug.effective(DebugTarget.LEFT))
                    else IslandBubbles.rightOffsetDp(shape.slot, capsuleHeightDp.value, dotsDp, IslandDebug.effective(DebugTarget.RIGHT), IslandDebug.effective(DebugTarget.EXTRA)), glide, label = "capsule slot")
                val widthDp by animateFloatAsState(IslandBubbles.widthDp(0, capsuleHeightDp.value) + adjust.dw, glide, label = "capsule width")
                val capsuleW = widthDp * d
                val capsuleTouch = remember { MutableInteractionSource() }
                val capsulePressed by capsuleTouch.collectIsPressedAsState()
                val pressScale by animateFloatAsState(if (capsulePressed) .9f else 1f, spring(dampingRatio = .6f, stiffness = 500f), label = "capsule press")
                val fill = themeFill
                // The neck's path is made once and rewound each frame, instead of allocating a Path per frame while the capsule pulls away.
                val neckPath = remember { androidx.compose.ui.graphics.Path() }
                fun capsuleX(frame: IslandFrame): Float {
                    val finalX = if (shape.left) frame.left - offsetDp * d - capsuleW else frame.left + frame.width + offsetDp * d
                    val startX = if (shape.left) frame.left + frame.height / 2f - capsuleW / 2f else frame.left + frame.width - frame.height / 2f - capsuleW / 2f
                    return startX + (finalX - startX) * appear.value
                }
                Box(Modifier
                    .offset {
                        val frame = frameState.value
                        val y = frame.top + (frame.height - capsuleH) / 2f + adjust.dy * d
                        androidx.compose.ui.unit.IntOffset(
                            (capsuleX(frame) - (windowRect?.left ?: origin.x)).roundToInt(), (y - (windowRect?.top ?: origin.y)).roundToInt())
                    }
                    .drawBehind {
                        val t = appear.value
                        if (t < .99f) {
                            val frame = frameState.value
                            val r1 = frame.height / 2f
                            val c1 = Metaball.Pt((if (shape.left) frame.left + r1 else frame.left + frame.width - r1) - capsuleX(frame), capsuleH / 2f - adjust.dy * d)
                            val c2 = Metaball.Pt(capsuleH / 2f, capsuleH / 2f)
                            val grow = (.55f + .45f * t).coerceAtLeast(.4f)
                            Metaball.neck(c1, r1, c2, capsuleH / 2f * grow, reach = ((1f - t) * 2f).coerceIn(0f, 1.5f))?.let { n ->
                                neckPath.rewind()
                                neckPath.moveTo(n.p1a.x, n.p1a.y); neckPath.cubicTo(n.h1.x, n.h1.y, n.h2.x, n.h2.y, n.p2a.x, n.p2a.y)
                                neckPath.lineTo(n.p2b.x, n.p2b.y); neckPath.cubicTo(n.h3.x, n.h3.y, n.h4.x, n.h4.y, n.p1b.x, n.p1b.y); neckPath.close()
                                drawPath(neckPath, fill)
                            }
                        }
                    }
                    .graphicsLayer { val sc = (.55f + .45f * appear.value).coerceAtLeast(.4f) * pressScale; scaleX = sc; scaleY = sc; alpha = (appear.value / .35f).coerceIn(0f, 1f) }
                    .size(widthDp.dp, capsuleHeightDp)
                    .clip(capsuleShape)
                    .background(themeFill)
                    .clickable(interactionSource = capsuleTouch, indication = null, onClickLabel = "Bring ${IslandLive.label(kind)} to the front", role = Role.Button) {
                        if (!capsule.leaving) { UnoFeedback.play(Cue.TICK, haptic); state.bringToFront(kind) }
                    }
                    .semantics { contentDescription = "Show ${IslandLive.label(kind)}" }
                    .testTag(if (shape.left) "island-capsule-left-${kind.name.lowercase()}" else if (shape.slot == 0) "island-capsule-${kind.name.lowercase()}" else "island-bubble-${kind.name.lowercase()}"),
                    contentAlignment = Alignment.Center) {
                    // Every capsule is a circle with the activity's symbol only, never an app's logo or album art: a wide capsule would sit over
                    // the status bar's wifi, signal and battery icons. Its time or status shows on the pill, or on the open island.
                    LiveSlot(kind, SlotShow.GLYPH, clockMs, isPlaying, artTint, artImage, multi = true, liveCompactLevel = liveCompactLevel, update = shownUpdate, glyphSize = glyphDiameterDp, plain = true)
                }
            }
        }
        // A screen reader is told when something new shows on the island — a flash event, a notification card, or a change in the
        // live facts (a call, a timer, music starting or ending) — through one hidden node, so each thing is said exactly once and the
        // ticking times inside the island are never read out again each second. The phrase is held until the next change.
        val card = state.peekCard
        val cardPhrase = card?.let { listOfNotNull(it.appLabel, it.title).joinToString(": ") }
        val liveFacts = IslandLiveFacts(
            ringing = IslandTools.ringing,
            call = callActive,
            timer = IslandTools.timerActive,
            playing = isPlaying,
        )
        var lastFlash by remember { mutableStateOf(state.flashTitle) }
        var lastCardKey by remember { mutableIntStateOf(-1) }
        var prevFacts by remember { mutableStateOf(liveFacts) }
        var announcement by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(state.flashTitle, state.peekKey, liveFacts) {
            val flash = state.flashTitle
            if (flash != null && flash != lastFlash) {
                lastFlash = flash; announcement = flash
            } else if (cardPhrase != null && state.peekKey != lastCardKey) {
                lastCardKey = state.peekKey; announcement = cardPhrase
            } else {
                IslandAnnounce.transition(prevFacts, liveFacts)?.let { announcement = it }
            }
            if (flash == null) lastFlash = null
            prevFacts = liveFacts
        }
        announcement?.let { ann ->
            Box(Modifier.size(1.dp).semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite; contentDescription = ann })
        }
        // A press squashes the island wide and flat, like a thumb on a water balloon, and it springs back with the motion's own wobble
        // (a calm theme barely moves; a jelly one jiggles).
        val islandTouch = remember { MutableInteractionSource() }
        val islandPressed by islandTouch.collectIsPressedAsState()
        val squash = IslandThemes.active.motion.pulse - 1f
        val pressX by animateFloatAsState(if (islandPressed && !pulling) 1f + squash * .55f else 1f, IslandThemes.active.spring(.4f, 500f), label = "island press x")
        val pressY by animateFloatAsState(if (islandPressed && !pulling) 1f - squash * .45f else 1f, IslandThemes.active.spring(.4f, 500f), label = "island press y")
        Box(Modifier
            .offset { val frame = frameState.value; if (anchoredToWindow) {
                androidx.compose.ui.unit.IntOffset(
                    windowRect?.let { (frame.left - it.left).roundToInt() } ?: 0,
                    windowRect?.let { (frame.top - it.top).roundToInt() } ?: 0)
            } else androidx.compose.ui.unit.IntOffset(
                (frame.left - origin.x).roundToInt(), (frame.top - origin.y).roundToInt()) }
            .graphicsLayer {
                // The morph's own speed stretches it a little as it opens (taller, narrower) and squashes it as it closes, so it moves like a
                // body of liquid with volume rather than a rectangle being resized. Calm themes barely move; jelly ones wobble.
                val settledAt = if (state.expanded) 1f else 0f
                // A finger drives the morph while pulling, and its speed is the finger's (uneven from one touch event to the next), so there is
                // no stretch then; the speed is told to rest so it does not carry into the release.
                val fingerDriven = state.pullFraction != null
                val stretch = if (animationsOn && !fingerDriven) MorphVelocity.stretch(morphSpeed.update(progressState.value, System.nanoTime(), settledAt), squashGain)
                    else { morphSpeed.update(progressState.value, System.nanoTime(), progressState.value); 0f }
                scaleX = pulse.value * pressX * (1f - stretch * .8f); scaleY = pulse.value * pressY * (1f + stretch)
            }
            .islandSized { frameState.value }
            .islandBody(glass?.backdrop, { cornerPx.value }, settings = glass?.settings ?: GlassSettings.Default)
            .islandRing(ring, { cornerPx.value }, 2.5f * d)
            .graphicsLayer { shape = RoundedCornerShape(cornerPx.value); clip = true }
            // The playing track's colour rising from the island's bottom edge, swaying like a low flame while audio really plays.
            .drawBehind {
                val a = glowAlpha.value
                if (a > .01f) drawBottomGlow(glowWash, glowTongue, a, flameT, flameAnimated)
            }
            .pointerInput(state.expanded, state.peeked, liveKinds, flashActive) {
                // Pulling the small island down (a drag that starts on it) opens it part-way, and letting go holds that peek; pulling on a peek takes
                // it further, and swiping up closes it. A finger resting on the bottom of the open island holds it open until it lifts. A sideways flick
                // turns the live activities so another is in front, or sends a flash away. Watched in the Initial pass and consumed once the finger has
                // moved past the touch slop, so the click below is cancelled instead of firing at the end of a swipe; a plain tap never gets past the
                // slop and is left alone. The decision uses the whole movement from touch-down, so a quick flick with few move events still counts.
                //
                // Android can take a downward drag that starts at the very top of the screen for the notification shade and cancel it for us (seen
                // on the emulator: the touch is cancelled where the status bar strip ends). A cancel arrives here as a release at the last place the
                // finger was, and [IslandDrag.peekOutcome] reads that, so a pull cut short that way still lands in the peek. The launcher's own top-edge
                // pull is kept off the pill separately: LauncherScreen's canStartDownwardSwipe excludes it (IslandEventArbiter.pressOnIslandPill), so
                // a pill drag is never also a shade/panel/search pull. The status bar strip itself belongs to the system and cannot be suppressed.
                val slop = viewConfiguration.touchSlop
                val threshold = 26.dp.toPx()
                val pullSlop = IslandDrag.PULL_SLOP_DP.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val tracker = VelocityTracker().apply { addPointerInputChange(down) }
                    // Below SystemUI the strip's touches belong to the system, which opens the shade from them; a pill pull must
                    // only engage from the part of the pill in launcher-owned space (below the strip). The accessibility overlay
                    // sits above the strip, so there the whole pill pulls.
                    val stripBottomLocalY = if (belowStatusBar) (environment.statusBarHeight - collapsedTopPx).coerceAtLeast(0f) else 0f
                    val canPull = !state.expanded && !flashActive && state.peekCard == null &&
                        down.position.y >= stripBottomLocalY
                    val canHold = (state.expanded || state.peeked) && IslandDrag.holdInZone(down.position.y, size.height.toFloat(), d)
                    // Set when the pull actually engages, below: where the island is right now, so a pull that lands
                    // mid-collapse (or mid-peek) continues from its current height instead of jumping to the start.
                    var pullFrom = 0f
                    var closeFrom = 0f
                    var moved = false
                    var pulling = false
                    var closing = false
                    var holding = false
                    var last = down.position
                    var lastEventTime = down.uptimeMillis
                    // The finger's pull position, low-passed so sub-frame touch jitter never twitches the island's bottom edge.
                    var fingerFilter: IslandDrag.Filter? = null
                    val downAt = System.currentTimeMillis()
                    try {
                        while (true) {
                            val waitMs = if (canHold && !holding && !moved) IslandDrag.HOLD_AFTER_MS - (System.currentTimeMillis() - downAt) else -1L
                            val event = if (waitMs >= 0L) withTimeoutOrNull(waitMs.coerceAtLeast(1L)) { awaitPointerEvent(PointerEventPass.Initial) }
                                else awaitPointerEvent(PointerEventPass.Initial)
                            if (event == null) {
                                // The finger has rested on the bottom long enough: the island stays open for as long as it stays there.
                                holding = true; state.holdingOpen = true; UnoFeedback.play(Cue.TICK, haptic)
                                continue
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPointerInputChange(change)
                            last = change.position
                            val dt = ((change.uptimeMillis - lastEventTime).coerceAtLeast(0L)) / 1000f
                            lastEventTime = change.uptimeMillis
                            val delta = last - down.position
                            if (holding) { change.consume(); if (!change.pressed) break; continue }
                            if (!moved && (abs(delta.x) > slop || abs(delta.y) > slop)) moved = true
                            if (canPull && moved && !pulling && delta.y > pullSlop && delta.y > abs(delta.x)) {
                                pulling = true
                                // Continue from wherever the island is right now, so a grab mid-collapse re-opens
                                // from its current height (and a grab on a peek from the peek). The release spring is
                                // still seeded with the finger's own velocity, so both directions carry momentum.
                                pullFrom = progress.value
                                if (animationsOn) fingerFilter = IslandDrag.Filter()
                            }
                            // A slow, deliberate swipe up on the open island (or a peek) closes it by following the finger: the
                            // bottom edge tracks the drag, so closing no longer depends on a fast flick past a fixed distance. The
                            // hold below only claims a finger that stays within the slop, so a drag that starts on the handle and
                            // moves up reaches this instead of being swallowed by the hold.
                            if ((state.expanded || state.peeked) && moved && !pulling && !closing && -delta.y > pullSlop && abs(delta.y) > abs(delta.x)) {
                                closing = true
                                closeFrom = progress.value
                                if (animationsOn) fingerFilter = IslandDrag.Filter()
                            }
                            if (pulling) {
                                val dy = fingerFilter?.step(dt, delta.y) ?: delta.y
                                state.pullFraction = IslandDrag.peekFraction(dy, d, peekState.floatValue, from = pullFrom)
                                change.consume()
                            } else if (closing) {
                                // The finger's upward travel is low-passed, as the pull-down is, so the bottom edge follows without
                                // the sub-frame twitch; the release decision below reads the raw tracker, so a short, fast flick is
                                // not misread as slow by the filter.
                                val dy = fingerFilter?.step(dt, delta.y) ?: delta.y
                                state.pullFraction = IslandDrag.closeFraction(-dy, openHeightPx, from = closeFrom)
                                change.consume()
                            } else if (moved && !(state.expanded && abs(delta.x) > abs(delta.y))) change.consume()
                            if (!change.pressed) break
                        }
                    } finally {
                        if (holding) { state.holdingOpen = false; IslandTools.touch() }
                        if (pulling) {
                            val fraction = state.pullFraction ?: 0f
                            state.pullFraction = null
                            // The filtered pull's speed seeds the spring that lands the island, so letting go mid-pull carries on
                            // smoothly instead of starting cold (or carrying the raw finger's jitter).
                            state.settleVelocity = fingerFilter?.velocity() ?: tracker.calculateVelocity().y
                            when (IslandDrag.peekOutcome(fraction, peekState.floatValue)) {
                                IslandDrag.PeekOutcome.COLLAPSE -> state.collapse()
                                IslandDrag.PeekOutcome.PEEK -> { UnoFeedback.play(Cue.TICK, haptic); state.peeked = true }
                                IslandDrag.PeekOutcome.OPEN -> { UnoFeedback.play(Cue.OPEN, haptic); state.peeked = false; state.expanded = true }
                            }
                        } else if (closing) {
                            // The upward travel since touch-down and the raw release speed (positive = up) decide the release: a slow
                            // drag past the distance closes, and so does a short flick with speed; anything short and slow springs back.
                            val upPx = down.position.y - last.y
                            val velocityY = tracker.calculateVelocity().y
                            state.pullFraction = null
                            if (IslandDrag.closeDecide(upPx / d, -velocityY, slop / d) == IslandDrag.CloseOutcome.CLOSE) {
                                state.settleVelocity = velocityY
                                UnoFeedback.play(Cue.TICK, haptic); state.collapse()
                            }
                            // Otherwise the island springs back to open: expanded/peeked is left as it was, so the collector below
                            // animates from the finger's height back up to the open target.
                        }
                    }
                    if (holding || pulling || closing || !moved) return@awaitEachGesture
                    val dx = last.x - down.position.x; val dy = last.y - down.position.y
                    when {
                        // An upward flick that never engaged tracking: a flash on the collapsed pill (the open island's swipe-up is
                        // handled by the tracked close above, so it never reaches this release check).
                        abs(dy) >= abs(dx) && dy <= -threshold && flashActive -> {
                            UnoFeedback.play(Cue.TICK, haptic); state.advanceFlash()
                        }
                        abs(dx) > abs(dy) && abs(dx) >= threshold && !state.expanded -> {
                            if (flashActive) { UnoFeedback.play(Cue.TICK, haptic); state.advanceFlash() }
                            else if (liveKinds.size >= 2) { UnoFeedback.play(Cue.TICK, haptic); state.bringToFront(IslandLive.nextFront(canonicalKinds, liveKinds.first())) }
                        }
                    }
                }
            }
            .combinedClickable(interactionSource = islandTouch, indication = null,
                onClickLabel = if (state.expanded) "Close" else "Open",
                onClick = {
                    // A ringing timer is silenced by a tap before anything else.
                    if (IslandTools.ringing) { UnoFeedback.play(Cue.OPEN, haptic); IslandTools.stopRinging(context) }
                    // A tap anywhere on a notification's flash opens that notification (the icon alone is a small target for a
                    // thumb, and a tap that just missed it used to open the panel and lose the flash instead).
                    else if (state.flashOpensSomething()) openFlash()
                    else { UnoFeedback.play(Cue.OPEN, haptic); state.toggle() }
                })
            .semantics {
                // The role and the click labels let a screen reader say "double tap to open" itself; the description says what is live.
                role = androidx.compose.ui.semantics.Role.Button
                stateDescription = if (state.expanded) "Open" else "Closed"
                contentDescription = IslandSpeech.describe(state.flashTitle, IslandTools.ringing, callActive, IslandTools.timerActive,
                    IslandTools.swRunning, isPlaying, charging = deviceStatus.charging == true)
            }
            .testTag("dynamic-island")) {
            val openHole = openFrame.hole
            // Where the open face starts. Camera on top: below the hole. Camera on a side edge: beside it, on the inward side, so the
            // panel opens away from the edge the camera is on. These come from the open frame: the camera's place does not move as it grows.
            val faceTop = if (openHole != null && !sideways) (openHole.bottom / d).dp + 6.dp else 12.dp
            val faceStart = if (openHole != null && openFrame.side == IslandSide.LEFT) (openHole.right / d).dp + 10.dp else 18.dp
            val faceEnd = if (openHole != null && openFrame.side == IslandSide.RIGHT) ((openFrame.width - openHole.left) / d).dp + 10.dp else 18.dp
            // Collapsed face. With a hole the content is split around it; without one it is centered.
            // Only the face that is showing is composed. An invisible panel would still own its buttons'
            // touch targets, and Compose pads small targets to 48 dp, so the hidden Search button caught
            // taps meant for the pill itself.
            if (showCollapsedFace && !(compactTarget == 1f && !sideways)) {
                val leading: @Composable () -> Unit = {
                    if (flashActive) {
                        val symbol = state.flashSymbol
                        val bitmap = state.flashIcon
                        val component = state.flashComponent
                        val open = state.flashOpen
                        // The app's icon opens what the notification points at (its own tap action, as the card does), or the app when
                        // it has none (a child click, so it wins over the pill's own expand tap).
                        if (bitmap != null) Box(Modifier.size(glyphDiameterDp).clip(CircleShape)
                            .then(if (component != null || open != null) Modifier.clickable { openFlash() }.testTag("island-peek-app") else Modifier),
                            contentAlignment = Alignment.Center) {
                            Image(bitmap, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        }
                        else if (symbol != null) Icon(symbol.icon(), null, tint = IslandThemes.active.tint(symbol), modifier = Modifier.size(glyphDiameterDp))
                    } else if (IslandTools.ringing) {
                        Icon(Icons.Rounded.Alarm, null, tint = IslandThemes.active.timer, modifier = Modifier.size(18.dp))
                    } else androidx.compose.animation.AnimatedContent(plan.leading, transitionSpec = { pillSwap(animationsOn) }, label = "pill glyph") { kind ->
                        if (kind != null) LiveSlot(kind, SlotShow.GLYPH, clockMs, isPlaying, artTint, artImage, multi = liveKinds.size >= 2, liveCompactLevel = liveCompactLevel, update = updates.firstOrNull(), glyphSize = glyphDiameterDp)
                    }
                    // Idle: nothing here. The time is in the status bar and on the expanded panel
                    // (IslandEventArbiter.timeOwner); the collapsed island never shows it.
                }
                val trailing: @Composable () -> Unit = {
                    if (flashActive) {
                        Text(state.flashTitle ?: "", color = themeInk, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 4.dp))
                    } else if (IslandTools.ringing) {
                        Text(stringResource(com.jake.duolauncher.R.string.timer_done), color = IslandThemes.active.timer, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, modifier = Modifier.testTag("island-timer-done"))
                    } else if (plan.leading != null) {
                        // The leading activity's detail, on the other side of the camera from its glyph. The trailing activity is
                        // not inside the pill: it is a detached capsule to the right (rendered below).
                        androidx.compose.animation.AnimatedContent(plan.leading, transitionSpec = { pillSwap(animationsOn) }, label = "pill detail") { kind ->
                            if (kind != null) LiveSlot(kind, SlotShow.DETAIL, clockMs, isPlaying, artTint, artImage, multi = liveKinds.size >= 2, liveCompactLevel = liveCompactLevel, update = updates.firstOrNull(), glyphSize = glyphDiameterDp)
                        }
                    } else if (recentLive) {
                        // A notification came in the last half minute: a dot in its app's colour says it can still be looked at.
                        Box(Modifier.size(7.dp).clip(CircleShape).background(recent?.tint?.let { Color(it) } ?: themeInk).testTag("island-recent-dot"))
                    } else if (deviceStatus.charging == true) {
                        Icon(Icons.Rounded.BatteryChargingFull, null, tint = IslandThemes.active.charging, modifier = Modifier.size(16.dp))
                    }
                    // The battery level is no longer written on the pill, wide or not: the status bar owns it, and the island speaks
                    // about the battery only while charging (above) and on the open panel.
                }
                CollapsedFace(frameState, d, collapsedAlpha, animationsOn, leading, trailing)
            }
            // A new notification as a card: icon, who or what, and the text if the level allows. Tap opens it; swipe up or wait to dismiss.
            peekShown?.let { peek ->
                if (showOpenFace) Column(Modifier.openSizedFace(openFrame) { frameState.value }.morphFace(expandedAlpha, animationsOn, bodyFade)
                    // The app's colour glows out from behind its icon into the island's own black. It is drawn across the whole body, before
                    // the padding, so it has no card edge of its own: the notification is part of the island, not a panel laid on it.
                    .then(IslandThemes.active.wash(peek.packageName, peek.tint)?.let { c -> Modifier.drawBehind {
                        val centre = Offset(faceStart.toPx() + 22.dp.toPx(), size.height * .62f)
                        val reach = size.width * .62f
                        drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(
                            listOf(c.copy(alpha = .36f), c.copy(alpha = .12f), Color.Transparent), centre, reach), reach, centre)
                    } } ?: Modifier)
                    .padding(start = faceStart, end = faceEnd, bottom = 12.dp, top = faceTop)) {
                    Row(Modifier.weight(1f).fillMaxWidth()
                        // No ripple plate over the island: the island's own press squash is the feedback.
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            // The notification's own tap action; with none, the app itself.
                            if (!sendTapAction(context, peek.open))
                                context.packageManager.getLaunchIntentForPackage(peek.packageName)?.let { runCatching { context.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                            state.clearRecent(); state.collapse()
                        }.testTag("island-peek-card"), verticalAlignment = Alignment.CenterVertically) {
                        if (peek.navigation) NavigationCardBody(peek) else {
                        peek.icon?.let { icon -> Image(remember(icon) { icon.asImageBitmap() }, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(peek.appLabel, color = themeInk.copy(alpha = .65f), fontSize = 11.sp, maxLines = 1)
                            peek.title?.let { Text(it, color = themeInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("island-peek-title")) }
                            peek.text?.let { Text(it, color = themeInk.copy(alpha = .85f), fontSize = 13.sp, maxLines = 2,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("island-peek-text")) }
                        }
                        }
                    }
                    // The notification's own buttons. A reply is typed here only on Home: the window over other apps cannot take the keyboard.
                    if (peek.actions.isNotEmpty()) PeekActionsRow(peek, state, canReply = !anchoredToWindow)
                }
            }
            // The handle at the bottom of the open island: a finger resting on it keeps the island open.
            if (state.expanded || state.peeked) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp).size(width = 34.dp, height = 4.dp)
                .clip(CircleShape).background(themeInk.copy(alpha = if (state.holdingOpen) .85f else .22f)).testTag("island-hold-handle"))
            // Expanded face: live panel, starting below the camera hole.
            if (showOpenFace && peekShown == null) Column(Modifier.openSizedFace(openFrame) { frameState.value }.morphFace(expandedAlpha, animationsOn, bodyFade)
                .padding(start = faceStart, end = faceEnd, bottom = 12.dp, top = faceTop).testTag("island-main-face")) {
                if (narration != null) {
                    Text(narration, color = themeInk.copy(alpha = .6f), fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("island-narration"))
                    Spacer(Modifier.height(4.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(now.format(timeFormatter), color = themeInk,
                            fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(now.format(DateTimeFormatter.ofPattern("EEE, MMM d")), color = themeInk.copy(alpha = .85f), fontSize = 12.sp, maxLines = 1)
                            nextAlarm?.let { alarm ->
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Rounded.Alarm, "Next alarm", tint = IslandThemes.active.timer, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(alarm, color = IslandThemes.active.timer.copy(alpha = .9f), fontSize = 12.sp, maxLines = 1,
                                    modifier = Modifier.testTag("island-next-alarm"))
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (deviceStatus.battery != null) "${deviceStatus.battery}%" else "—",
                            color = if (deviceStatus.charging == true) IslandThemes.active.charging else themeInk,
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        chargeLeft?.let { Text(it, color = IslandThemes.active.charging.copy(alpha = .85f), fontSize = 11.sp, maxLines = 1,
                            modifier = Modifier.testTag("island-charge-left")) }
                    }
                }
                if (recentLive && recent != null) {
                    Spacer(Modifier.height(6.dp))
                    RecentNotificationRow(recent, state, canReply = !anchoredToWindow)
                }
                PrivacyLine.describe(state.cameraActive, state.micActive, state.recordingActive, state.privacySince, clockMs, inFront)?.let { line ->
                    Spacer(Modifier.height(6.dp))
                    PrivacyRow(line, state.cameraActive, state.micActive, state.recordingActive, onReview = {
                        runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_PRIVACY_SETTINGS)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        state.collapse()
                    })
                }
                NotificationFeed.ongoingCall?.let { call ->
                    Spacer(Modifier.height(6.dp))
                    CallRow(call, clockMs, onClick = {
                        sendTapAction(context, call.openIntent); state.collapse()
                    })
                }
                updates.firstOrNull()?.let { u ->
                    Spacer(Modifier.height(6.dp))
                    UpdateCard(u, clockMs, onOpen = { sendTapAction(context, u.openIntent); state.collapse() })
                }
                if (IslandWidgetState.id >= 0) {
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
                        durationMs = mediaDurationMs(),
                        onSeek = { fraction -> mediaSeek(fraction) },
                        // Tapping the cover or the title opens the player: the app's own "now playing" screen when it offers one,
                        // otherwise the app itself (many players give no session activity).
                        onOpenPlayer = NotificationFeed.nowPlaying?.controller?.let { controller ->
                            { openPlayer(context, controller); state.collapse() }
                        })
                }
                Spacer(Modifier.height(8.dp))
                feedHeadline?.let { headline ->
                    Text(headline, color = themeInk.copy(alpha = .95f), fontSize = 13.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                }
                if (quickApps.isNotEmpty()) Row(Modifier.fillMaxWidth().height(QUICK_ROW_DP.dp - 6.dp).padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    quickApps.forEach { app ->
                        Image(app.icon, app.label, Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).clickable {
                            launchIslandApp(context, app.component); state.collapse()
                        }.testTag("island-quick-app"))
                    }
                }
                if (showActions) Row(Modifier.testTag("island-actions"), horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (onWebSearch != null) {
                        IslandSearchField(onWebSearch, onSearch, Modifier.weight(1f))
                        IslandAction(Icons.Rounded.RssFeed, "Feed", false, onOpenFeed)
                        IslandAction(Icons.Rounded.Tune, "Settings", false, onCustomize)
                    } else {
                        IslandAction(Icons.Rounded.Search, "Search", actionLabelsFit, onSearch)
                        IslandAction(Icons.Rounded.RssFeed, "Feed", actionLabelsFit, onOpenFeed)
                        IslandAction(Icons.Rounded.Tune, "Settings", actionLabelsFit, onCustomize)
                    }
                }
            }
        }
    }
    }
}

/** The collapsed pill's face: the two slots either side of the camera hole. It reads the live frame, so it follows the pill as it morphs; it is
 * composed only while the island is collapsed or part way open, and is small.
 */
@Composable
private fun CollapsedFace(frameState: State<IslandFrame>, d: Float, alphaOf: () -> Float, blurOk: Boolean,
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
private fun RowScope.NavigationCardBody(peek: PeekNotification) {
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
private fun RecentNotificationRow(peek: PeekNotification, state: IslandState, canReply: Boolean) {
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
private class BodyFade(val bodyHeightPx: () -> Float, val fullHeightPx: Float, val fadePx: Float)

/** Fades what is drawn out over the last [BodyFade.fadePx] above the body's bottom edge, only while the body is shorter than its full height. */
private fun Modifier.fadeAtBodyBottom(fade: BodyFade): Modifier {
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
private fun Modifier.morphFace(alphaOf: () -> Float, blurOk: Boolean, fade: BodyFade? = null): Modifier = then(if (fade == null) Modifier else Modifier.fadeAtBodyBottom(fade)).graphicsLayer {
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
private fun PeekActionsRow(peek: PeekNotification, state: IslandState, canReply: Boolean) {
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
private fun PeekReplyField(action: PeekAction, onSend: (String) -> Unit) {
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
private fun PrivacyRow(line: PrivacyLine.Line, camera: Boolean, mic: Boolean, recording: Boolean, onReview: () -> Unit) {
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
private fun IslandSearchField(onSubmit: (String) -> Unit, onFullSearch: () -> Unit, modifier: Modifier = Modifier) {
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

/** How long after a Focus change a ringer change is treated as its side effect. */
internal const val FOCUS_SIDE_EFFECT_MS = 1_500L

/** The most an event can widen the collapsed pill by, in dp (see IslandGeometry.eventExtraWidthDp). */
internal const val MAX_EVENT_EXTRA_DP = 140f

/** How many flashes can wait behind the one on screen; beyond that the oldest waiting one is dropped. */
internal const val MAX_QUEUED_FLASHES = 3

/** Extra panel height, in dp, when the music card is showing: artwork and track above, the controls below. */
internal const val MEDIA_ROW_DP = 126f
/** Extra panel height, in dp, for the notification card's buttons (or its reply field), and for the privacy row. */
internal const val PEEK_ACTIONS_ROW_DP = 42f
/** The reply field's row height, in dp: tall enough that its send button keeps a 44 dp touch target. */
internal const val REPLY_ROW_DP = 44f
internal const val PRIVACY_ROW_DP = 46f
/** The height the open panel reserves for its action buttons, in dp; taken back where they are not shown. */
internal const val ACTIONS_ROW_DP = 44f
/** The quick-launch strip of apps in the open island. */
internal const val QUICK_ROW_DP = 52f
/** Extra panel height, in dp, for the narration line that names why the panel is showing what it is. */
internal const val NARRATION_ROW_DP = 18f
/** Extra panel height, in dp, when a Live Update card is showing. */
internal const val UPDATE_ROW_DP = 76f
/** A held notification on the open island: its spacer, padding and two lines (its buttons add [PEEK_ACTIONS_ROW_DP]). */
internal const val RECENT_ROW_DP = 76f

/** The open island's hosted widget row; see IslandWidget. */
private fun widgetRowDp(): Float = if (IslandWidgetState.id >= 0) IslandWidgetState.ROW_DP + 6f else 0f
/** The artwork tile in the music card, in dp. */
internal const val MEDIA_ART_DP = 56f
/** Extra panel height, in dp, when the call card is showing. */
internal const val CALL_ROW_DP = 48f
internal val PLAYBACK_PINK = Color(0xFFFF375F)

/** The active island theme's colours, read where the island draws (see IslandTheme.kt). */
private val themeInk: Color @Composable @ReadOnlyComposable get() = IslandThemes.active.ink
private val themeFill: Color @Composable @ReadOnlyComposable get() = IslandThemes.active.fill

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
private fun SeekBar(progress: Float, tint: Color, onSeek: (Float) -> Unit, durationMs: Long? = null) {
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
private fun mediaFraction(playing: Boolean, @Suppress("UNUSED_PARAMETER") tick: Long): Float? {
    val controller = NotificationFeed.nowPlaying?.controller ?: return null
    val state = controller.playbackState ?: return null
    val duration = controller.metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: return null
    return MediaProgress.fraction(state.position, state.lastPositionUpdateTime, state.playbackSpeed, playing,
        android.os.SystemClock.elapsedRealtime(), duration)
}

/** The current track's length, or null when the player does not say. */
private fun mediaDurationMs(): Long? =
    NotificationFeed.nowPlaying?.controller?.metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0L }

private fun mediaSeek(fraction: Float) {
    val controller = NotificationFeed.nowPlaying?.controller ?: return
    val duration = controller.metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: return
    runCatching { controller.transportControls.seekTo(MediaProgress.seekTarget(fraction, 1f, duration)) }
}

/** The live call card: caller and elapsed time; tapping opens the phone app's call screen. */
@Composable
private fun CallRow(call: OngoingCall, nowMs: Long, onClick: () -> Unit) {
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
private fun MediaButton(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = themeInk,
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
private fun IslandAction(icon: ImageVector, label: String, showLabel: Boolean, onClick: () -> Unit) {
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

/** What the pill's glyph and detail do when the activity in front changes: the old one shrinks and fades away, the new one grows in a beat later,
 * without the layout resizing under them. With animations off, nothing animates. */
private fun pillSwap(animate: Boolean): androidx.compose.animation.ContentTransform =
    if (!animate) androidx.compose.animation.ContentTransform(androidx.compose.animation.EnterTransition.None, androidx.compose.animation.ExitTransition.None)
    else androidx.compose.animation.ContentTransform(
        targetContentEnter = androidx.compose.animation.fadeIn(tween(170, delayMillis = 70)) + androidx.compose.animation.scaleIn(tween(170, delayMillis = 70), initialScale = .7f),
        initialContentExit = androidx.compose.animation.fadeOut(tween(110)) + androidx.compose.animation.scaleOut(tween(110), targetScale = .7f),
        sizeTransform = null)

/** The things the island shows, plus the ones just gone that are still playing their exit; [gone] says an exit has finished. */
private class Lingered<T>(val held: List<Held<T>>, val gone: (T) -> Unit)

/** [current] as [Held] entries, keeping each thing that leaves until its exit calls [Lingered.gone]. With [keepGhosts] false (the island is
 * open or busy, so nothing is shown to animate) leavers are dropped at once. */
@Composable
private fun <T> rememberLingering(current: List<T>, keepGhosts: Boolean): Lingered<T> {
    val box = remember { arrayOfNulls<List<Held<T>>>(1) }
    var tick by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val observed = tick
    val held = Lingering.merge(if (keepGhosts) box[0] ?: emptyList() else emptyList(), current)
    box[0] = held
    return Lingered(held) { item -> box[0] = Lingering.drop(box[0] ?: emptyList(), item); tick++ }
}

/** One text size for the detail beside the camera, whichever activity it is: the timer, the call, the stopwatch, a Live Update. */
private const val SLOT_TEXT_SP = 12
private val PRIVACY_MIC = Color(0xFFFF9F0A)
/** A detached capsule's body: a stadium with fully rounded ends, like the pill but small. */
private val capsuleShape = RoundedCornerShape(percent = 50)
/** How much smaller than the pill's height an app icon or thumbnail is, in px: a ~4 px inset each side, so the circle sits
 * concentrically inside the pill. */
private const val GLYPH_INSET_PX = 14f
/** The smallest a glyph circle may be, in px, whatever the pill's height. */
private const val MIN_GLYPH_PX = 12f

/** One live activity as it appears in a side of the collapsed pill: its small glyph, or its detail (text or bars). */
@Composable
private fun LiveSlot(kind: LiveKind, show: SlotShow, clockMs: Long, playing: Boolean, tint: Color,
    art: androidx.compose.ui.graphics.ImageBitmap?, multi: Boolean, liveCompactLevel: Int, update: LiveUpdate? = null,
    glyphSize: androidx.compose.ui.unit.Dp, plain: Boolean = false) {
    val glyph = show == SlotShow.GLYPH
    when (kind) {
        LiveKind.CAMERA -> if (glyph) Icon(Icons.Rounded.Videocam, "Camera in use", tint = IosGreen,
            modifier = Modifier.size(glyphSize).testTag("island-camera"))
            else Text(stringResource(com.jake.duolauncher.R.string.camera), color = IosGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        LiveKind.MIC -> if (glyph) Icon(Icons.Rounded.Mic, "Microphone in use", tint = PRIVACY_MIC,
            modifier = Modifier.size(glyphSize).testTag("island-mic"))
            else Text(stringResource(com.jake.duolauncher.R.string.mic), color = PRIVACY_MIC, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        LiveKind.RECORDING -> if (glyph) Icon(Icons.Rounded.FiberManualRecord, "Screen is being recorded", tint = IslandSymbol.RECORDING.tint,
            modifier = Modifier.size(glyphSize).testTag("island-recording"))
            else Text(stringResource(com.jake.duolauncher.R.string.recording), color = IslandSymbol.RECORDING.tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        LiveKind.CALL -> if (glyph) Icon(Icons.Rounded.Call, null, tint = IosGreen, modifier = Modifier.size(glyphSize))
            // How long the call has lasted, in green, like the other things that count time (the timer, the stopwatch). The caller's name
            // is on the open island's call card: a name cut to five letters says little, a clock says how long.
            else Text(NotificationFeed.ongoingCall?.let { IslandClock.countdown((clockMs - it.startedAt).coerceAtLeast(0L)) } ?: if (LiveKind.CALL in IslandDebug.forced) "1:23" else "", color = IosGreen,
                fontSize = SLOT_TEXT_SP.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                modifier = Modifier.padding(end = 4.dp).testTag("island-call"))
        LiveKind.TIMER -> if (glyph) Icon(Icons.Rounded.Timer, null, tint = IslandThemes.active.timer, modifier = Modifier.size(glyphSize))
            else Text(if (IslandTools.timerActive || LiveKind.TIMER !in IslandDebug.forced) IslandClock.countdown(IslandClock.remainingMs(clockMs, IslandTools.timerEndAt)) else "9:59", color = IslandThemes.active.timer,
                fontSize = SLOT_TEXT_SP.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.testTag("island-timer"))
        LiveKind.STOPWATCH -> if (glyph) Icon(Icons.Rounded.Timer, null, tint = themeInk, modifier = Modifier.size(glyphSize))
            else Text(if (IslandTools.swRunning || LiveKind.STOPWATCH !in IslandDebug.forced) IslandClock.stopwatch(IslandTools.elapsedMs(clockMs)) else IslandClock.stopwatch(12_300L), color = themeInk,
                fontSize = SLOT_TEXT_SP.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.testTag("island-stopwatch"))
        LiveKind.UPDATE -> if (update != null) {
            val driving = update.source == ActivitySource.NAVIGATION
            if (glyph) UpdateGlyph(update, glyphSize, plain)
            else {
                // The words beside the glyph, as the person chose: none (glyph alone), the short status, or the title.
                val text = LiveCompactText.choose(liveCompactLevel, LiveUpdateLogic.glance(update, clockMs), update.title)
                if (text != null) Text(text, color = updateTint(update), fontSize = (if (driving) NavigationText.PILL_SP else SLOT_TEXT_SP).sp,
                    fontWeight = if (driving) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 4.dp).testTag("island-live-update"))
            }
        }
        LiveKind.MEDIA -> if (glyph) {
            // The artwork when there is any, as a circle sized to sit concentrically inside the pill; otherwise the bars stand in.
            if (art != null && !plain) Image(art, null, Modifier.size(glyphSize).clip(CircleShape))
            else if (multi) EqualizerBars(if (playing) tint else tint.copy(alpha = .55f), animate = playing)
        } else {
            // Stays while a paused player can still be resumed, but only moves while audio is playing.
            EqualizerBars(if (playing) tint else tint.copy(alpha = .55f),
                Modifier.testTag(if (playing) "island-playing" else "island-paused"), animate = playing)
        }
    }
}




/** The colour a Live Update asks for, or the island's green when it asks for none or one too dark to read on black. */
private fun updateTint(update: LiveUpdate): Color {
    val c = Color(update.color)
    val requested = if (update.color == 0 || c.alpha < .5f || (c.red * .299f + c.green * .587f + c.blue * .114f) < .35f) null else c.copy(alpha = 1f)
    return IslandThemes.active.accent(update.packageName, requested)
}

/** " 42%" after a card's status when the theme asks for percentages and the update has measurable progress; otherwise nothing. */
private fun percentage(update: LiveUpdate): String {
    if (!IslandThemes.active.showPercentage) return ""
    val f = LiveUpdateLogic.fraction(update.progress, update.progressMax, update.indeterminate) ?: return ""
    return " ${(f * 100).toInt().coerceIn(0, 100)}%"
}

/** The app's small icon in a soft circle of its colour: what stands in for the app on the pill and the card. */
@Composable
private fun UpdateGlyph(update: LiveUpdate, size: androidx.compose.ui.unit.Dp, plain: Boolean = false) {
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
private fun UpdateCard(update: LiveUpdate, clockMs: Long, onOpen: () -> Unit) {
    val tint = updateTint(update)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen).testTag("island-update")) {
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
            if (!navigating) Text(LiveUpdateLogic.glance(update, clockMs) + percentage(update), color = tint, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
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
