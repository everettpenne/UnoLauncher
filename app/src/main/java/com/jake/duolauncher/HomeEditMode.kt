package com.jake.duolauncher

import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.appearance.*

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Edit Home, the launcher's version of iOS's jiggle mode. Icons wiggle and carry a remove badge, a drag starts the moment a finger
 * moves (no long press), empty cells show where an icon can go, a + adds apps or a widget, and Done (or Back) leaves.
 * Everything here is presentation: the layout rules are the ones in HomeEditing.kt.
 */
internal val LocalHomeEditing = compositionLocalOf { false }

internal class HomeEditActions(val remove: (AppEntry) -> Unit)
internal val LocalHomeEditActions = compositionLocalOf<HomeEditActions?> { null }

internal object HomeEditLogic {
    const val WIGGLE_DEGREES = 1.6f

    /** The tilt of an icon at [phase] (0..1 through one wiggle), out of step with its neighbours by [seed] and leaning the other way
     * on every second icon, so a screen of icons does not move as one block. */
    fun wiggleAngle(phase: Float, seed: Int): Float {
        val offset = Math.floorMod(seed, 7) / 7f
        val direction = if (Math.floorMod(seed, 2) == 0) 1f else -1f
        return WIGGLE_DEGREES * direction * sin(2.0 * PI * (phase + offset)).toFloat()
    }

    /** How far [point] is from [rect] (0 inside it). */
    fun distance(point: Offset, rect: Rect): Float {
        val dx = maxOf(rect.left - point.x, 0f, point.x - rect.right)
        val dy = maxOf(rect.top - point.y, 0f, point.y - rect.bottom)
        return hypot(dx, dy)
    }

    /** The cell nearest [point] among [cells], if one is within [maxDistance]; a drop that just misses a cell lands in the nearest one
     * instead of being thrown away, as iOS does. */
    fun <T> nearest(point: Offset, cells: Collection<Pair<T, Rect>>, maxDistance: Float): T? =
        cells.minByOrNull { distance(point, it.second) }?.takeIf { distance(point, it.second) <= maxDistance }?.first
}

/** The wiggle itself. Each icon runs its own short loop, so its phase is its own. */
@Composable
internal fun Modifier.homeWiggle(active: Boolean, seed: Int): Modifier {
    if (!active) return this
    val transition = rememberInfiniteTransition(label = "home wiggle")
    val phase = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(240 + Math.floorMod(seed, 5) * 25, easing = LinearEasing), RepeatMode.Restart), label = "wiggle")
    return graphicsLayer { rotationZ = HomeEditLogic.wiggleAngle(phase.value, seed) }
}

/** The small round "minus" on an icon's corner. The touch target is bigger than the circle. */
@Composable
internal fun RemoveBadge(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.size(44.dp).clickable(onClick = onClick).semantics { contentDescription = "Remove $label from Home" }, contentAlignment = Alignment.TopStart) {
        Box(Modifier.padding(4.dp).size(22.dp).background(Color(0xFF3A3A3C), CircleShape).border(1.5.dp, Color.White.copy(alpha = .9f), CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Remove, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

/** The bar across the top while editing: + on the left, Done on the right. */
@Composable
internal fun HomeEditBar(glass: PageGlass?, onAddApps: () -> Unit, onAddWidget: () -> Unit, onAddPage: () -> Unit, onRemovePage: (() -> Unit)?, onMovePageEarlier: (() -> Unit)?, onMovePageLater: (() -> Unit)?, onDone: () -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Box {
            FilledTonalIconButton(onClick = { menu = true }, Modifier.size(44.dp).testTag("home-edit-add")) {
                Icon(Icons.Rounded.Add, "Add to Home")
            }
            GlassDropdownMenu(menu, { menu = false }, glass = glass) {
                GlassMenuItem(text = { Text(stringResource(com.jake.duolauncher.R.string.add_apps)) }, onClick = { menu = false; onAddApps() }, modifier = Modifier.testTag("home-edit-add-apps"))
                GlassMenuItem(text = { Text(stringResource(com.jake.duolauncher.R.string.add_widget)) }, onClick = { menu = false; onAddWidget() }, modifier = Modifier.testTag("home-edit-add-widget"))
                GlassMenuItem(text = { Text(stringResource(com.jake.duolauncher.R.string.add_page)) }, onClick = { menu = false; onAddPage() }, modifier = Modifier.testTag("home-edit-add-page"))
                if (onMovePageEarlier != null) GlassMenuItem(text = { Text(stringResource(com.jake.duolauncher.R.string.move_page_earlier)) }, onClick = { menu = false; onMovePageEarlier() }, modifier = Modifier.testTag("home-edit-page-earlier"))
                if (onMovePageLater != null) GlassMenuItem(text = { Text(stringResource(com.jake.duolauncher.R.string.move_page_later)) }, onClick = { menu = false; onMovePageLater() }, modifier = Modifier.testTag("home-edit-page-later"))
                if (onRemovePage != null) GlassMenuItem(text = { Text(stringResource(com.jake.duolauncher.R.string.remove_empty_page)) }, onClick = { menu = false; onRemovePage() }, modifier = Modifier.testTag("home-edit-remove-page"))
            }
        }
        FilledTonalButton(onClick = onDone, Modifier.heightIn(min = 44.dp).testTag("home-edit-done")) { Text(stringResource(com.jake.duolauncher.R.string.done)) }
    }
}
