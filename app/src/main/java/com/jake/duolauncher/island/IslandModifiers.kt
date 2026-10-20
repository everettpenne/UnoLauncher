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

// The island body's modifiers and small helpers: its glass body, its sizing during the morph, and the symbol for an event. (Split out of DynamicIsland.kt; see that file for the island itself.)

internal fun IslandSymbol.icon(): ImageVector = when (this) {
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
internal fun Modifier.islandBody(
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
internal data class IslandTarget(val pull: Float?, val expanded: Boolean, val peeked: Boolean, val peek: Float)

/** Counts how often the island composes, so a test can hold the morph to a handful of compositions instead of one per frame. */
internal object IslandDiagnostics { @Volatile var compositions = 0 }

/** Gives the island body [frame]'s size, read when it is laid out, so the morph resizes it without recomposing the island. */
internal fun Modifier.islandSized(frame: () -> IslandFrame): Modifier = layout { measurable, _ ->
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
internal fun Modifier.openSizedFace(open: IslandFrame, frame: () -> IslandFrame): Modifier = layout { measurable, _ ->
    val w = open.width.roundToInt().coerceAtLeast(0); val h = open.height.roundToInt().coerceAtLeast(0)
    val placeable = measurable.measure(Constraints.fixed(w, h))
    val f = frame()
    val bodyW = f.width.roundToInt().coerceAtLeast(0); val bodyH = f.height.roundToInt().coerceAtLeast(0)
    layout(bodyW, bodyH) {
        placeable.place(when (open.side) { IslandSide.LEFT -> 0; IslandSide.RIGHT -> bodyW - w; IslandSide.TOP -> (bodyW - w) / 2 }, 0)
    }
}
