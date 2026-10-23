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
    /**
     * The collapsed pill in window coordinates, settled, exactly as the island draws it: with the status bar grid's edges, an event's width and
     * the hand adjustment. Search shrinks back into it. (It used to be the bare pill before any of those, so search grew from, and closed into,
     * a pill a few dp off the real one, and the island jumped when it took over again.)
     */
    var restPill by mutableStateOf<IslandFrame?>(null)
    /** Where the island's body is on screen this frame, whatever it is doing (open, part way, widened): search grows out of this. Not state, so
     * writing it every frame of a morph recomposes nothing. */
    @Volatile var liveFrame: IslandFrame? = null
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
