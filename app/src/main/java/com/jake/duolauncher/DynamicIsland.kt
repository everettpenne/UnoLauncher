package com.jake.duolauncher

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
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

    fun showLaunch(app: AppEntry) {
        flashTitle = app.label
        flashIcon = app.icon.asImageBitmap()
    }

    fun showCharging(charging: Boolean) {
        if (charging && flashTitle == null) flashTitle = "Charging"
    }

    fun dismissFlash() { flashTitle = null; flashIcon = null }
    fun toggle() { expanded = !expanded; dismissFlash() }
    fun collapse() { expanded = false }
}

/** A liquid-glass dynamic island that wraps the front-camera hole. Collapsed it is a capsule
 * centered on the hole, with the time to its left and charging/battery to its right; it expands
 * downward from the hole into a live panel (time, battery, the top feed headline, and quick
 * actions) and flashes app launches and charging events. Devices without a cutout get a plain
 * capsule below the status bar. The island is launcher-local: it draws on Home only and never
 * overlays other apps.
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
    onSearch: () -> Unit,
    onOpenFeed: () -> Unit,
    onCustomize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now by produceState(LocalDateTime.now()) {
        while (true) { value = LocalDateTime.now(); delay(30_000L) }
    }
    val view = LocalView.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val d = density.density
    // The cutout is read from the live window and refreshed on every layout, so it is correct once
    // the view attaches and again after rotation, folding, or a display-size change.
    var environment by remember { mutableStateOf(IslandEnvironment(null, view.rootView.width.toFloat(), 0f)) }
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    LaunchedEffect(configuration) { environment = readIslandEnvironment(view, dockWidthPx) }

    val progress by animateFloatAsState(if (state.expanded) 1f else 0f,
        spring(dampingRatio = 0.5f, stiffness = 300f), label = "island expand")
    val flashActive = state.flashTitle != null
    val frame = IslandGeometry.frame(environment, d, progress, sizeScale)
    val corner = minOf(frame.height / 2f, 30f * d) / d

    // Flash returns to the collapsed clock after a beat.
    LaunchedEffect(state.flashTitle) {
        if (state.flashTitle != null) { delay(2600); state.dismissFlash() }
    }
    // An idle expanded island tucks itself back in.
    LaunchedEffect(state.expanded) {
        if (state.expanded) { delay(5000); state.collapse() }
    }

    Box(modifier.fillMaxSize().onGloballyPositioned {
        origin = it.positionInWindow()
        val read = readIslandEnvironment(view, dockWidthPx)
        if (read != environment) environment = read
    }) {
        // The island's window-pixel position, translated into this parent's own frame.
        Box(Modifier
            .offset { androidx.compose.ui.unit.IntOffset(
                (frame.left - origin.x).roundToInt(), (frame.top - origin.y).roundToInt()) }
            .size(width = (frame.width / d).dp, height = (frame.height / d).dp)
            // Black at high opacity so the island reads as one piece with the camera hole.
            .then(if (glass != null) Modifier.liquidGlass(glass.backdrop,
                RoundedCornerShape(corner.dp), Color.Black.copy(alpha = .82f), blurRadius = 2f,
                settings = glass.settings) else Modifier
                .background(Color.Black.copy(alpha = .94f), RoundedCornerShape(corner.dp)))
            .clip(RoundedCornerShape(corner.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                state.toggle()
            }
            .testTag("dynamic-island")) {
            val hole = frame.hole
            val gap = 4.dp
            // Collapsed face. With a hole the content is split around it; without one it is centered.
            Row(Modifier.fillMaxSize().alpha(if (progress < .5f) 1f else 0f),
                verticalAlignment = Alignment.CenterVertically) {
                val leading: @Composable () -> Unit = {
                    if (flashActive) {
                        state.flashIcon?.let { Image(it, null, Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))) }
                            ?: Icon(Icons.Rounded.BatteryChargingFull, null, tint = IosGreen, modifier = Modifier.size(18.dp))
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
            // Expanded face: live panel, starting below the camera hole.
            Column(Modifier.fillMaxSize().alpha(if (progress >= .5f) 1f else 0f)
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
                Spacer(Modifier.height(8.dp))
                feedHeadline?.let { headline ->
                    Text(headline, color = Color.White.copy(alpha = .95f), fontSize = 13.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IslandAction(Icons.Rounded.Search, "Search", onSearch)
                    IslandAction(Icons.Rounded.RssFeed, "Feed", onOpenFeed)
                    IslandAction(Icons.Rounded.Tune, "Customize", onCustomize)
                }
            }
        }
    }
}

@Composable
private fun IslandAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(percent = 50))
        .clickable { onClick() }.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(15.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}
