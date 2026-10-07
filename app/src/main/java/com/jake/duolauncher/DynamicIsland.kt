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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
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

/** A liquid-glass dynamic island anchored to the camera cutout. Collapsed it is a small
 * capsule with the time; it expands into a live panel (time, battery, the top feed
 * headline, and quick actions) and flashes app launches and charging events. The island
 * is launcher-local: it draws on Home only and never overlays other apps.
 */
@Composable
internal fun DynamicIsland(
    state: IslandState,
    glass: PageGlass?,
    deviceStatus: DeviceStatus,
    feedHeadline: String?,
    onSearch: () -> Unit,
    onOpenFeed: () -> Unit,
    onCustomize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now by produceState(LocalDateTime.now()) {
        while (true) { value = LocalDateTime.now(); delay(30_000L) }
    }
    val view = LocalView.current
    val cutout = remember { view.rootWindowInsets?.displayCutout }
    val cutoutRect = cutout?.boundingRectTop
    val islandTop = ((cutoutRect?.bottom ?: 0) + 10).dp.coerceAtLeast(10.dp)
    val centerX = cutoutRect?.centerX()?.toFloat() ?: view.width / 2f
    val halfWidth = 200.dp
    val density = androidx.compose.ui.platform.LocalDensity.current

    val progress by animateFloatAsState(if (state.expanded) 1f else 0f,
        spring(dampingRatio = 0.5f, stiffness = 300f), label = "island expand")
    val flashActive = state.flashTitle != null
    val width = with(density) { lerp(172.dp, 336.dp, progress).toPx() }
    val height = with(density) { lerp(36.dp, 118.dp, progress).toPx() }
    val corner = lerp(18.dp, 30.dp, progress)

    // Flash returns to the collapsed clock after a beat.
    LaunchedEffect(state.flashTitle) {
        if (state.flashTitle != null) { delay(2600); state.dismissFlash() }
    }
    // An idle expanded island tucks itself back in.
    LaunchedEffect(state.expanded) {
        if (state.expanded) { delay(5000); state.collapse() }
    }

    Box(modifier
        .offset { androidx.compose.ui.unit.IntOffset((centerX - width / 2f).toInt(),
            with(density) { islandTop.roundToPx() }) }
        .size(width = with(density) { width.toDp() }, height = with(density) { height.toDp() })
        .then(if (glass != null) Modifier.liquidGlass(glass.backdrop,
            RoundedCornerShape(corner), glass.tint.copy(alpha = .34f), blurRadius = 2f,
            settings = glass.settings) else Modifier
            .background(Glass.copy(alpha = .85f), RoundedCornerShape(corner)))
        .clip(RoundedCornerShape(corner))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            state.toggle()
        }
        .testTag("dynamic-island")) {
        // Collapsed face: clock, or the flash (launch/charging) when one is active.
        Box(Modifier.fillMaxSize().alpha(if (progress < .5f) 1f else 0f),
            contentAlignment = Alignment.Center) {
            if (flashActive) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.flashIcon?.let { Image(it, null, Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))) }
                    Text(state.flashTitle ?: "", color = Color.White, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = Color.White,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (deviceStatus.charging == true) Icon(Icons.Rounded.BatteryChargingFull, null,
                        tint = IosGreen, modifier = Modifier.size(15.dp))
                }
            }
        }
        // Expanded face: live panel.
        Column(Modifier.fillMaxSize().alpha(if (progress >= .5f) 1f else 0f)
            .padding(horizontal = 18.dp, vertical = 12.dp)) {
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
