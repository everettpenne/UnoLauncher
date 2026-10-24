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

/** Shared island state owned by MainActivity, so app launches (which start there) can
 * flash the island without a Compose-local channel.
 */
@Stable

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
    /** Shrink to a ring round the camera when idle on Home too (the iOS layout, where the status bar shows beside the island). Android keeps its
     *  status bar icons clear of the camera, so a ring never covers one; the island widens only when it has something to show. */
    ringWhenIdle: Boolean = false,
    /** Home draws its own status bar around the island (the iOS layout's Uno status bar): there are no system icons to keep clear of, so the
     *  island is simply centred on the camera. */
    ownStatusBar: Boolean = false,
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
    // The island's one motion driver (see IslandMotionDriver): every value that moves its body, each sprung toward what the island is heading for
    // with its spring from IslandMotionSpecs, restarting only what changed and from where it is and how fast it is going.
    val motion = remember { IslandMotionDriver(state.expanded) }
    val motionInputs = remember { MotionInputs() }
    val motionSpecs = remember(theme, animationsOn) { IslandMotionSpecs(theme, animationsOn) }
    val progress = motion.height
    val progressState = motion.height.asState()
    val widthState = motion.width.asState()
    // The open panel's height in px, to turn the finger's release velocity (px/s) into the 0..1 progress-per-second the spring wants.
    val openHeightPx = remember(environment, d, sizeScale) { IslandGeometry.frame(environment, d, 1f, sizeScale).height }.coerceAtLeast(1f)
    LaunchedEffect(motionSpecs, openHeightPx) {
        snapshotFlow {
            val pull = state.pullFraction
            val t = IslandTarget(pull, state.expanded, state.peeked, peekState.floatValue)
            MotionTarget(
                height = pull ?: if (t.expanded) 1f else if (t.peeked) t.peek else 0f,
                width = if (pull != null) (pull * 2.5f).coerceAtMost(1f) else if (t.expanded || t.peeked) 1f else 0f,
                opening = t.expanded || t.peeked, fingerDriven = pull != null,
                eventWidthDp = motionInputs.eventWidthDp, compactness = motionInputs.compactness,
                edgeLeft = motionInputs.edgeLeft, edgeRight = motionInputs.edgeRight,
                pressX = motionInputs.pressX, pressY = motionInputs.pressY, screen = motionInputs.screen, animate = animationsOn)
        }.collect { t ->
            // The finger's release velocity (px/s, down = positive) is consumed once and seeds the height spring, so a fast swipe-up tucks the
            // island away with momentum and a pull released mid-motion carries on instead of starting cold.
            val releaseVelocity = state.settleVelocity
            state.settleVelocity = 0f
            motion.drive(this, t, motionSpecs, releaseVelocity / openHeightPx)
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
        // From 60 % of the panel's width: at .72 the body was an empty black slab for several frames of every open and close. The face is
        // still scaled in and sharpened from a blur over that range (morphFace), so its edges are not seen cut off.
        val wide = ((if (coupling > 0f) IslandMotion.coupledWidth(p, coupling) else widthState.value) - .6f) / .4f
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
    StatusBarCalibration.init(context)
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
    val eventWidthState = motion.eventWidthDp.asState()
    // Over other apps the system status bar already shows the time and battery right beside the camera, so while the island has
    // nothing to say it shrinks to a ring around the camera instead of covering those icons. Anything live (an event, a timer,
    // a call, playback) widens it again.
    val hasLiveContent = flashActive || IslandTools.ringing || liveKinds.isNotEmpty()
    val compactTarget = if ((anchoredToWindow || ringWhenIdle) && !hasLiveContent && !alwaysWide) 1f else 0f
    val compactnessState = motion.compactness.asState()
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
    val mainDy = mainBase.dy + IslandDebug.pill.dy
    // The collapsed island at the size it is heading for, before any adjustment: the edges it would have.
    val restTarget = remember(environment, d, sizeScale, eventTargetDp, compactTarget, wideFactor) {
        IslandGeometry.frame(environment, d, 0f, sizeScale, extraWidthDp = eventTargetDp, compactness = compactTarget, wideFactor = wideFactor) }
    // Where the system status bar's icons are, so no island edge cuts one in half (see [StatusBarGrid]). The grid is a Pixel's: it is used only
    // with a camera at the top of the screen, and not for the ring the island shrinks to over other apps when it has nothing to say.
    val gridOn = environment.cutout != null && openFrame.side == IslandSide.TOP && compactTarget < .5f && !ownStatusBar
    // With Uno's own status bar there is nothing to avoid: the symmetric layout with no icons centres the island and places its capsules.
    val centredOnly = ownStatusBar && environment.cutout != null && openFrame.side == IslandSide.TOP && compactTarget < .5f
    // Worked out once a minute, not on every tick of the island's clock (which is ten times a second while a stopwatch runs).
    val clockShiftDp = remember(clockMs / 60_000L, is24Hour) {
        StatusBarGrid.clockShiftDp(java.util.Calendar.getInstance().apply { timeInMillis = clockMs }.get(java.util.Calendar.HOUR), is24Hour)
    }
    // The capsules split off the pill only once the body has nearly landed back on the camera. They used to come out the moment a close began,
    // from the middle of the still-open panel's sides, and fly up to the status bar while the panel shrank past them.
    val nearlyShut by remember { derivedStateOf { progressState.value < .12f } }
    val capsulesWanted = !(state.expanded || state.peeked || pulling || flashActive || IslandTools.ringing) && nearlyShut
    val capsulePlan = if (capsulesWanted) IslandBubbles.layout(liveKinds) else emptyList()
    val capsuleMinDp = restTarget.height / d
    // The edges the island would have with its shipped width (and, showing an app, its wider one) and any hand adjustment.
    val wantLeftDp = (restTarget.left - (mainBase.dw + IslandDebug.pill.dw) * d / 2f + (mainBase.dx + IslandDebug.pill.dx) * d) / d
    val wantRightDp = (restTarget.left + restTarget.width + (mainBase.dw + IslandDebug.pill.dw) * d / 2f + (mainBase.dx + IslandDebug.pill.dx) * d) / d
    // The status bar's real gaps, when the optional alignment service has read them at this screen width (see StatusBarReader); otherwise
    // the grid measured on a Pixel 10a, moved by the calibration page.
    val measuredBar = StatusBarLive.usable(environment.screenWidth / d)
    val islandLayout = restTarget.hole.let { hole ->
        val measuredGaps = if (measuredBar != null && hole != null)
            StatusBarScan.gaps(measuredBar.icons, (restTarget.left + hole.left) / d, (restTarget.left + hole.right) / d) else null
        // Symmetric about the camera when a width fits that leaves every icon wholly covered or wholly clear on both sides (the icons as read,
        // or as the grid has them); otherwise each edge snaps to its own nearest gap, as before.
        if ((gridOn || centredOnly) && hole != null) StatusBarGrid.symmetric(wantLeftDp, wantRightDp, (restTarget.left + hole.left) / d, (restTarget.left + hole.right) / d,
            if (centredOnly) emptyList() else measuredBar?.icons ?: StatusBarGrid.gridIcons(environment.screenWidth / d, clockShiftDp, StatusBarCalibration.current), environment.screenWidth / d,
            wantLeft = capsulePlan.any { it.left }, wantRight = capsulePlan.any { !it.left }, dots = privacyNow.size, capsuleMinWidth = capsuleMinDp)
            ?: StatusBarGrid.layout(wantLeftDp, wantRightDp, environment.screenWidth / d, clockShiftDp,
            (restTarget.left + hole.left) / d, (restTarget.left + hole.right) / d,
            wantLeft = capsulePlan.any { it.left }, wantRight = capsulePlan.any { !it.left }, dots = privacyNow.size, capsuleMinWidth = capsuleMinDp,
            cal = StatusBarCalibration.current, measuredLeft = measuredGaps?.first, measuredRight = measuredGaps?.second)
        else StatusBarGrid.flush(if (compactTarget < .5f) wantLeftDp else restTarget.left / d, if (compactTarget < .5f) wantRightDp else (restTarget.left + restTarget.width) / d,
            capsulePlan.any { it.left }, capsulePlan.any { !it.left }, capsuleMinDp)
    }
    // How far each edge moves from where it would be, in px: animated, so the island springs to its snapped width and back.
    // Started afresh whenever the screen it is measured against changes (the first real reading at start-up, a rotation), so the island is simply
    // where it belongs then, instead of springing there from wherever a guess put it.
    // What composition works out, handed to the motion driver (which jumps the edges, rather than springing them, when the screen changes).
    SideEffect {
        motionInputs.eventWidthDp = eventTargetDp
        motionInputs.compactness = compactTarget
        motionInputs.edgeLeft = islandLayout.pillLeft * d - restTarget.left
        motionInputs.edgeRight = islandLayout.pillRight * d - (restTarget.left + restTarget.width)
        motionInputs.screen = environment to d
    }
    val frameState = remember(environment, d, sizeScale, extraBody, coupling, wideFactor) { derivedStateOf {
        val p = progressState.value
        val w = if (coupling > 0f) IslandMotion.coupledWidth(p, coupling) else widthState.value
        val base = IslandGeometry.frame(environment, d, w, sizeScale,
            extraBodyDp = extraBody, extraWidthDp = eventWidthState.value, compactness = compactnessState.value, wideFactor = wideFactor, heightProgress = p)
        // The grid's edge corrections are sideways, so they unwind with the width, and the hand-set drop is vertical, so it unwinds with the
        // height. Both used to follow the height: on a motion whose width is slower than its height (Droplet, or any pull, which leads with the
        // width), the pill's snapped edges were let go before it had widened, so it shifted sideways and shrank for a moment before it grew,
        // and on the way back the edges snapped out after it had landed.
        withDebugAdjust(withEdgeShift(base, 1f - w, motion.edgeLeft.value, motion.edgeRight.value), 1f - p, 0f, 0f, mainDy, d, environment.screenWidth)
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
        // A peek is the panel's full width and only part of its height, as the live frame is (its width target is 1, its height the peek).
        IslandGeometry.frame(environment, d, if (state.expanded || state.peeked) 1f else 0f, sizeScale, extraBodyDp = extraBody,
            extraWidthDp = eventTargetDp, compactness = compactTarget, wideFactor = wideFactor,
            heightProgress = if (state.expanded) 1f else if (state.peeked) peekState.floatValue else 0f) }
    // The frame the island settles to, with the same adjustments the live frame gets (the grid's edges, the debug offset): what the touch window should
    // be when nothing is moving. It must match the settled live frame; if it does not, the touch window never shrinks back (see the effect that reports it).
    // The edges follow the width and the drop the height, as in [frameState].
    val headingCollapsed = if (state.expanded) 0f else if (state.peeked) 1f - peekState.floatValue else 1f
    val headingNarrow = if (state.expanded || state.peeked) 0f else 1f
    val headingAdjusted = withDebugAdjust(
        withEdgeShift(headingFor, headingNarrow, islandLayout.pillLeft * d - restTarget.left, islandLayout.pillRight * d - (restTarget.left + restTarget.width)),
        headingCollapsed, 0f, 0f, mainDy, d, environment.screenWidth)
    // The settled pill, as drawn, for search to shrink back into (see IslandState.restPill). Only written when it changes.
    if (!state.expanded && !state.peeked) SideEffect { if (state.restPill != headingAdjusted) state.restPill = headingAdjusted }
    val reported = remember { arrayOfNulls<IslandFrame>(1) }
    val bodyFade = remember(openFrame, d) { BodyFade({ frameState.value.height }, openFrame.height, 10f * d) }
    val morphSpeed = remember { MorphVelocity() }
    val squashGain = ((theme.motion.pulse - 1f).coerceIn(0f, .3f) / .1f).coerceAtLeast(.25f)
    // A capsule is as round as its shorter side allows; turned sideways the pill is taller than it is wide.
    val panelScale = theme.panel.scale
    val cornerPx = remember(panelScale, d) { derivedStateOf {
        val f = frameState.value
        // Never negative: a frame measured before the screen's size is known (width 0) can come out narrower than nothing, and a negative
        // corner radius throws in the shape code, which crashed the island's own window. (IslandMorph.cornerPx keeps it at zero or more.)
        IslandMorph.cornerPx(f.width, f.height, d, panelScale)
    } }
    // The same outline as one shape for every size the body passes through, for its shadow.
    val bodyShape = remember(panelScale, d) { IslandBodyShape(d, panelScale) }
    val sideways = openFrame.side != IslandSide.TOP
    // The colour fade for music: while the music is one of the things the pill shows (or the panel is open), and nothing else has the island.
    val glowAlpha = animateFloatAsState(
        IslandGlow.target(mediaVisible && (state.expanded || plan.leading == LiveKind.MEDIA), isPlaying,
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
        // The timer rings the island only when the island is showing it; in a capsule, a ring round the island would be about something else.
        IslandTools.timerActive && plan.leading == LiveKind.TIMER, 1f - IslandClock.progress(clockMs, IslandTools.timerEndAt, IslandTools.timerTotal),
        IslandEventArbiter.chargingRingSurface(statusRailShown, deviceStatus.charging == true), deviceStatus.battery, IslandThemes.active.timer, IslandThemes.active.charging, state.flashTint)
    // A new event gives the island a small spring pulse, like iOS's.
    val pulse = motion.pulse
    LaunchedEffect(state.flashKey) {
        if (state.flashKey > 0) motion.kick(this, IslandThemes.active.motion.pulse, motionSpecs)
    }
    // A capsule melting into the pill (an activity ended, or was brought to the front) gives it the same small pulse, a touch softer.
    var absorbTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(absorbTick) {
        if (absorbTick > 0 && animationsOn) motion.kick(this, 1f + (IslandThemes.active.motion.pulse - 1f) * .7f, motionSpecs)
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
    // Whether anything is, or is about to be, taller than the collapsed pill: only a threshold, so the island recomposes when it is crossed and
    // not on every frame of the morph.
    val morphing by remember { derivedStateOf { progressState.value > .002f || state.pullFraction != null } }
    val windowTall = morphing || state.expanded || state.peeked || state.holdingOpen || peekShown != null
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
        // Nothing outside the screen is ever seen, so the window stops at its edges: unclipped it was 1740 x 3580 px on a 1080 x 2400 screen.
        val screenH = if (environment.screenHeight > 0f) environment.screenHeight else Float.MAX_VALUE
        // Its top-left corner is the screen's, always: a window that moves is animated by Android, and the island seemed to fly into place.
        val full = PxRect(0f, 0f,
            minOf(environment.screenWidth, maxOf(open.left + open.width, wide.left + wide.width + (IslandBubbles.reserveDp(1 + IslandBubbles.MAX, capsuleHeightDp.value) + IslandPrivacy.widthDp(3)) * d) + pad),
            minOf(screenH, maxOf(open.top + open.height, wide.top + wide.height) + pad))
        // Collapsed and still, the island needs only the top strip, so the window is only that tall: the same left, top and width, so the
        // picture never moves when it grows (it grows at the bottom, out of sight below the island), but a sliver of the screen instead of
        // all of it. A window as big as the screen was blended into every frame of whatever app was in front, and redrawn whole on every
        // frame the island animated (the music bars, the glow), which cost battery and smoothness everywhere. It grows to the full height the
        // moment the island starts to open (a pull, a tap, a card), with room to spare below the pill for the first frames of the morph.
        if (windowTall) full else PxRect(full.left, full.top, full.right,
            minOf(full.bottom, wide.top + wide.height + (IslandGeometry.MAX_OVERSHOOT - 1f) * wide.height + 64f * d))
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
        LaunchedEffect(headingAdjusted, windowRect) {
            // Told when the frame the touch window should have changes; during a morph that is only while the island is growing. Once the island has
            // settled the report is exactly the settled frame, so the touch window shrinks back to it: compared with a frame that differed from the
            // settled one (as it did when the island's width was adjusted), the report only ever grew, and the window kept covering the whole panel
            // after the first time the island opened, taking touches meant for the top of the app underneath.
            var first = true
            snapshotFlow { frameState.value }.collect { live ->
                state.liveFrame = live
                val next = if (live.nearly(headingAdjusted)) headingAdjusted else (reported[0] ?: live).union(live).union(headingAdjusted)
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
        val capsulesShown = capsulesWanted
        // Camera, microphone and screen recording are dots inside the island's right end, always, and not activities: the pill and the capsules
        // show what the person is doing, the dots show what the phone is doing. (Outside the island they would sit on top of status bar icons.)
        val privacyKinds = privacyNow
        // With two live the second is a capsule on the island's right; with three or more it is on the left and the third on the right. The
        // grid decides how wide each is and which icons it covers, so none is cut; a capsule with no room for it is not drawn.
        val detachedCapsules = capsulePlan.filter { if (it.left) islandLayout.left != null else islandLayout.right != null }
        val leftAdjust = IslandDebug.effective(DebugTarget.LEFT); val rightAdjust = IslandDebug.effective(DebugTarget.RIGHT); val extraAdjust = IslandDebug.effective(DebugTarget.EXTRA)
        // Where each capsule is, settled, in screen pixels: the touch window covers the island and these (see touchArea), so what takes touches
        // is worked out from the same boxes the capsules are drawn in.
        IslandRuntime.capsuleRectsPx = detachedCapsules.mapNotNull { c ->
            val box = (if (c.left) islandLayout.left else islandLayout.right) ?: return@mapNotNull null
            val adj = IslandBubbles.groupFor(c, leftAdjust, rightAdjust, extraAdjust)
            val left = (box.left - adj.dw / 2f + if (c.left) -adj.dx else adj.dx) * d
            val top = headingAdjusted.top + (headingAdjusted.height - collapsedHeightPx) / 2f + adj.dy * d
            PxRect(left, top, left + (box.width + adj.dw) * d, top + collapsedHeightPx)
        }
        LaunchedEffect(detachedCapsules.size, privacyKinds.size) { onFrameChanged?.invoke(reported[0] ?: headingAdjusted, windowRect) }
        val heldDots = rememberLingering(privacyKinds, keepGhosts = capsulesShown)
        heldDots.held.forEach { dot ->
            val kind = dot.item; val index = dot.index
            key(kind) {
                // A dot springs up from nothing when the camera, microphone or recording starts, and back down to nothing when it stops.
                val grow = remember { Animatable(if (animationsOn) 0f else 1f) }
                LaunchedEffect(dot.leaving) {
                    grow.animateTo(if (dot.leaving) 0f else 1f, motionSpecs.dot)
                    if (dot.leaving) heldDots.gone(kind)
                }
                Box(Modifier
                    .offset {
                        val frame = frameState.value
                        // Inside the island, against its right end, on the camera's line: dot [index] of [privacyKinds.size], counting from the left.
                        // They ride the body's swell and squash (an event's pulse, a press) about the camera, as the body does, so they stay put
                        // inside it; and as it opens they stay up beside the camera rather than sliding down the open panel's side.
                        val (cx, cy) = IslandMorph.dotCenter(frame, index, privacyKinds.size, d, cornerPx.value)
                        val (x, y) = IslandMorph.scaled(frame, cx, cy, pulse.value * motion.pressX.value, pulse.value * motion.pressY.value)
                        val half = IslandPrivacy.DOT_DP * d / 2f
                        androidx.compose.ui.unit.IntOffset(
                            (x - half - (windowRect?.left ?: origin.x)).roundToInt(), (y - half - (windowRect?.top ?: origin.y)).roundToInt())
                    }
                    // Drawn above the island, which is composed after them: they sit inside its right end.
                    .zIndex(2f)
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
                        appear.animateTo(0f, motionSpecs.capsuleLeave)
                        absorbTick++; heldCapsules.gone(shape)
                    } else {
                        if (appear.value < .05f && System.currentTimeMillis() - state.swapAtMs < 400L) delay(150L)
                        appear.animateTo(1f, motionSpecs.capsuleAppear)
                    }
                }
                // What a capsule shows while it leaves is what it showed last: its live data may already be gone.
                val lastUpdate = remember { arrayOfNulls<LiveUpdate>(1) }
                if (!capsule.leaving) lastUpdate[0] = updates.firstOrNull()
                val shownUpdate = if (capsule.leaving) lastUpdate[0] else updates.firstOrNull()
                val capsuleH = collapsedHeightPx
                // When the order changes (a tap or a flick) each capsule glides to its new place and grows or shrinks to the new width,
                // instead of jumping, so the person can follow where each activity went.
                val glide = motionSpecs.capsuleGlide
                val adjust = IslandBubbles.groupFor(shape, IslandDebug.effective(DebugTarget.LEFT), IslandDebug.effective(DebugTarget.RIGHT), IslandDebug.effective(DebugTarget.EXTRA))
                // The box the grid gives this capsule (kept for the exit animation, once the activity is gone and it has none), widened and
                // moved outward by any hand adjustment.
                val lastBox = remember { arrayOfNulls<StatusBarGrid.Span>(1) }
                (if (shape.left) islandLayout.left else islandLayout.right)?.let { lastBox[0] = it }
                val box = lastBox[0] ?: StatusBarGrid.Span(0f, capsuleMinDp)
                val boxLeftPx = (box.left - adjust.dw / 2f + if (shape.left) -adjust.dx else adjust.dx) * d
                val boxWidthPx = (box.width + adjust.dw) * d
                val slotX by animateFloatAsState(boxLeftPx, glide, label = "capsule slot")
                val widthPx by animateFloatAsState(boxWidthPx, glide, label = "capsule width")
                val widthDp = widthPx / d
                val capsuleW = widthPx
                val capsuleTouch = remember { MutableInteractionSource() }
                val capsulePressed by capsuleTouch.collectIsPressedAsState()
                val pressScale by animateFloatAsState(if (capsulePressed) .9f else 1f, motionSpecs.pressCapsule, label = "capsule press")
                val fill = themeFill
                // The neck's path is made once and rewound each frame, instead of allocating a Path per frame while the capsule pulls away.
                val neckPath = remember { androidx.compose.ui.graphics.Path() }
                // The round end of the pill a capsule comes out of: the pill's own end, settled; while the body is open, a pill-sized end at the
                // top corner, so the neck is drawn to the camera's line and not to a circle as tall as the open panel.
                fun endRadius(frame: IslandFrame) = minOf(frame.height, capsuleH) / 2f
                fun capsuleX(frame: IslandFrame): Float {
                    // Settled, the capsule is where the grid put it; it comes out of (and goes back into) the island's end on its side.
                    val r = endRadius(frame)
                    val startX = if (shape.left) frame.left + r - capsuleW / 2f else frame.left + frame.width - r - capsuleW / 2f
                    return startX + (slotX - startX) * appear.value
                }
                Box(Modifier
                    .offset {
                        val frame = frameState.value
                        // On the camera's line (the pill's middle, settled), so a capsule going back into an island that has started to open
                        // is drawn in at the top, where the pill was, not halfway down the growing panel.
                        val y = IslandMorph.stripCenterY(frame) - capsuleH / 2f + adjust.dy * d
                        androidx.compose.ui.unit.IntOffset(
                            (capsuleX(frame) - (windowRect?.left ?: origin.x)).roundToInt(), (y - (windowRect?.top ?: origin.y)).roundToInt())
                    }
                    .drawBehind {
                        val t = appear.value
                        if (t < .99f) {
                            val frame = frameState.value
                            val r1 = endRadius(frame)
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
        val openLeading: (() -> Unit)? = if (state.expanded || state.peeked || flashActive || IslandTools.ringing) null else when (plan.leading) {
            LiveKind.CALL -> NotificationFeed.ongoingCall?.openIntent?.let { intent -> { sendTapAction(context, intent); Unit } }
            LiveKind.MEDIA -> NotificationFeed.nowPlaying?.controller?.let { controller -> { openPlayer(context, controller) } }
            LiveKind.UPDATE -> updates.firstOrNull()?.openIntent?.let { intent -> { sendTapAction(context, intent); Unit } }
            else -> null
        }
        val islandTouch = remember { MutableInteractionSource() }
        val islandPressed by islandTouch.collectIsPressedAsState()
        val squash = IslandThemes.active.motion.pulse - 1f
        SideEffect {
            motionInputs.pressX = if (islandPressed && !pulling) 1f + squash * .55f else 1f
            motionInputs.pressY = if (islandPressed && !pulling) 1f - squash * .45f else 1f
        }
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
                scaleX = pulse.value * motion.pressX.value * (1f - stretch * .8f); scaleY = pulse.value * motion.pressY.value * (1f + stretch)
                // All of it about the camera: the body swells round the camera and squashes onto it, and a fast morph stretches the panel down
                // from it. About the body's centre (the default), the open panel's top lifted off the camera and dropped back on every press.
                val (fx, fy) = IslandMorph.pivot(frameState.value)
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(fx, fy)
                // The open island lifts off what is behind it: a soft shadow that grows as it opens (none on the pill, which sits in the status
                // bar). While music is playing the shadow takes the track's colour, so the open island glows faintly with it.
                val open = progressState.value.coerceIn(0f, 1f)
                shadowElevation = if (open > .01f) open * 16f * d else 0f
                shape = bodyShape
                if (open > .01f) {
                    val glow = glowAlpha.value
                    val tint = if (glow > .01f) androidx.compose.ui.graphics.lerp(Color.Black, artTint, (glow * 1.6f).coerceAtMost(1f)) else Color.Black
                    ambientShadowColor = tint; spotShadowColor = tint
                }
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
                // A long press on the pill goes straight to what its leading activity belongs to: the call screen, the player, the app behind a
                // Live Update. A tap still opens the island. Nothing to go to (idle, a timer, an event), and a long press is only a press.
                onLongClickLabel = if (openLeading != null) "Open ${plan.leading?.let { IslandLive.label(it) } ?: ""}".trim() else null,
                onLongClick = openLeading?.let { open -> { UnoFeedback.play(Cue.OPEN, haptic); open() } },
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
                            LiveSlot(kind, SlotShow.DETAIL, clockMs, isPlaying, artTint, artImage, multi = liveKinds.size >= 2, liveCompactLevel = liveCompactLevel, update = updates.firstOrNull(), glyphSize = glyphDiameterDp)
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
                            fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum", letterSpacing = (-.3).sp))
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (deviceStatus.battery != null) "${deviceStatus.battery}%" else "—",
                                color = if (deviceStatus.charging == true) IslandThemes.active.charging else themeInk,
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
                            Spacer(Modifier.width(6.dp))
                            BatteryGlyph(deviceStatus.battery, deviceStatus.charging == true)
                        }
                        chargeLeft?.let { Text(it, color = IslandThemes.active.charging.copy(alpha = .85f), fontSize = 11.sp, maxLines = 1,
                            modifier = Modifier.testTag("island-charge-left")) }
                    }
                }
                if (recentLive) {
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
                if (showActions) Row(Modifier.fillMaxWidth().testTag("island-actions"), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (onWebSearch != null) {
                        IslandSearchField(onWebSearch, onSearch, Modifier.weight(1f))
                        IslandAction(Icons.Rounded.RssFeed, "Feed", false, onOpenFeed)
                        IslandAction(Icons.Rounded.Tune, "Settings", false, onCustomize)
                    } else {
                        // Three equal chips across the panel; icon-only ones keep their own size and sit centred.
                        val share = if (actionLabelsFit) Modifier.weight(1f) else Modifier
                        if (!actionLabelsFit) Spacer(Modifier.weight(1f))
                        IslandAction(Icons.Rounded.Search, "Search", actionLabelsFit, onSearch, share)
                        IslandAction(Icons.Rounded.RssFeed, "Feed", actionLabelsFit, onOpenFeed, share)
                        IslandAction(Icons.Rounded.Tune, "Settings", actionLabelsFit, onCustomize, share)
                        if (!actionLabelsFit) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
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
internal fun widgetRowDp(): Float = if (IslandWidgetState.id >= 0) IslandWidgetState.ROW_DP + 6f else 0f
/** The artwork tile in the music card, in dp. */
internal const val MEDIA_ART_DP = 56f
/** Extra panel height, in dp, when the call card is showing. */
internal const val CALL_ROW_DP = 48f
internal val PLAYBACK_PINK = Color(0xFFFF375F)

/** The active island theme's colours, read where the island draws (see IslandTheme.kt). */
internal val themeInk: Color @Composable @ReadOnlyComposable get() = IslandThemes.active.ink
internal val themeFill: Color @Composable @ReadOnlyComposable get() = IslandThemes.active.fill
