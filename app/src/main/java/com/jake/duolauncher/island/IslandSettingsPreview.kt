package com.jake.duolauncher.island

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jake.duolauncher.IslandWideMode
import com.jake.duolauncher.R
import com.jake.duolauncher.appearance.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** What the settings preview is showing: one situation the island meets, chosen to show off the setting just changed. */
internal enum class PreviewScene { IDLE, MUSIC, LIVE, THREE, NOTIFICATION, PRIVACY, OPEN }

/**
 * Lets a setting ask the preview to demonstrate it: a row calls [show] with the situation that shows its effect (the wide-mode choices show music,
 * the compact-text choices show a Live Update, and so on), and the preview plays the island into it with the island's own motion. Changing the
 * motion or a colour replays the current scene, so the new feel can be watched rather than imagined.
 */
@Stable
internal class IslandPreviewController {
    var scene by mutableStateOf(PreviewScene.IDLE)
        private set
    /** Bumped on every request, so asking for the scene already showing still replays it. */
    var replay by mutableIntStateOf(0)
        private set

    fun show(next: PreviewScene) { scene = next; replay++ }
    fun replay() { replay++ }
}

internal val LocalIslandPreview = staticCompositionLocalOf<IslandPreviewController?> { null }

/** The settings the preview draws with. Everything here is read from the person's current choices, so the preview is what they will get. */
internal data class PreviewSettings(
    val scale: Float,
    val wideMode: IslandWideMode,
    val compactLevel: Int,
    val peekLevel: Int,
    val peekActions: Boolean,
    val shortcuts: Boolean,
    val privacyIndicators: Boolean,
    val liveUpdates: Boolean,
)

/**
 * A faithful small copy of the island over a mock status bar: the same geometry as the real one ([IslandGeometry.frame], with this phone's own
 * camera cut-out and screen width, drawn a little smaller to fit the sheet), the same theme colours and shapes, and the same springs. It never
 * touches the real island's state: no listeners, no windows, nothing published to the overlay.
 */
@Composable
internal fun IslandSettingsPreview(controller: IslandPreviewController, settings: PreviewSettings, modifier: Modifier = Modifier) {
    val theme = IslandThemes.active
    val density = LocalDensity.current
    val context = LocalContext.current
    val view = LocalView.current
    val metrics = context.resources.displayMetrics
    val d = metrics.density
    // This phone's real camera hole and status bar, so the preview is the island's real size and shape; a phone with none gets a centred hole.
    val insets = view.rootWindowInsets
    val cutout = remember(insets) {
        insets?.displayCutout?.boundingRects?.map { PxRect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
            ?.let { IslandGeometry.pickCutout(it, metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat()) }
            ?.takeIf { IslandGeometry.sideOf(it, metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat()) == IslandSide.TOP }
            ?: (metrics.widthPixels / 2f).let { c -> PxRect(c - 13f * d, 12f * d, c + 13f * d, 38f * d) }
    }
    val statusBarPx = (insets?.getInsets(android.view.WindowInsets.Type.statusBars())?.top ?: (50f * d).toInt()).toFloat().coerceAtLeast(cutout.bottom + 6f * d)
    val env = remember(cutout, statusBarPx) { IslandEnvironment(cutout, metrics.widthPixels.toFloat(), statusBarPx, screenHeight = metrics.heightPixels.toFloat()) }

    var stageWidthPx by remember { mutableFloatStateOf(metrics.widthPixels * .9f) }
    val k = (stageWidthPx / env.screenWidth).coerceIn(.3f, 1f)            // screen px → stage px

    val scene = controller.scene
    val target = remember(scene, settings, env) { targetFor(scene, settings, env, d) }
    val left = remember { Animatable(target.left) }
    val width = remember { Animatable(target.width) }
    val height = remember { Animatable(target.height) }
    val pulse = remember { Animatable(1f) }
    val open = scene == PreviewScene.OPEN || scene == PreviewScene.NOTIFICATION && settings.peekLevel > 0
    LaunchedEffect(target, controller.replay, theme.motion) {
        coroutineScope {
            // An opening scene first settles back onto the camera, so each change plays the whole movement with the chosen motion.
            if (open && height.value > target.height * .8f) {
                val rest = targetFor(PreviewScene.IDLE, settings, env, d)
                launch { left.animateTo(rest.left, theme.closeSpring()) }; launch { width.animateTo(rest.width, theme.closeSpring()) }
                height.animateTo(rest.height, theme.closeSpring())
            }
            launch { left.animateTo(target.left, theme.widthSpring()) }
            launch { width.animateTo(target.width, theme.widthSpring()) }
            launch { height.animateTo(target.height, theme.heightSpring()) }
            if (!open) launch { pulse.animateTo(theme.motion.pulse, tween(110)); pulse.animateTo(1f, theme.heightSpring()) }
        }
    }

    val stageHeight = with(density) { ((maxOf(height.value, target.height) + cutout.top + 18f * d) * k).toDp() }.coerceAtLeast(64.dp)
    Column(modifier.fillMaxWidth().testTag("island-preview"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(stageHeight).clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF3E5468), Color(0xFF7C8E9C), Color(0xFFB9A58A))))
            .onSizeChanged { stageWidthPx = it.width.toFloat() }
            .semantics { contentDescription = "Island preview" }) {
            StatusBarMock(env, k, theme.ink)
            val px = { v: Float -> with(density) { (v * k).toDp() } }
            val frameLeft = left.value; val frameW = width.value; val frameH = height.value
            val top = cutout.centerY - (targetFor(PreviewScene.IDLE, settings, env, d).height / 2f)
            if (scene == PreviewScene.THREE) {
                // the second and third activities sit beside the island, as round capsules the pill's height
                val gap = 6f * d
                Capsule(px(frameLeft - gap - frameH), px(top), px(frameH), theme.timer, Icons.Rounded.Timer, theme.fill)
                Capsule(px(frameLeft + frameW + gap), px(top), px(frameH), theme.playback, Icons.Rounded.MusicNote, theme.fill)
            }
            val corner = if (frameH > (cutout.height + 30f * d)) (30f * theme.panel.scale).coerceAtMost(frameH / d / 2f).dp * k else px(frameH / 2f)
            Box(Modifier.offset(px(frameLeft), px(top)).size(px(frameW), px(frameH))
                .graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
                .clip(RoundedCornerShape(corner)).background(theme.fill)) {
                // the camera, as it shows through the pill
                Box(Modifier.offset(px(cutout.left - frameLeft), px(cutout.top - top)).size(px(cutout.width), px(cutout.height))
                    .clip(CircleShape).background(Color(0xFF15181C)))
                Crossfade(scene, label = "preview-scene") { s -> SceneFace(s, settings, theme, k, cutout.height, bodyTop = px(cutout.bottom - top + 8f * d)) }
            }
        }
        Text(caption(scene, settings), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("island-preview-caption"))
        val names = sceneNames()
        GlassChips(PreviewScene.entries.toList(), scene, { names.getValue(it) }, { controller.show(it) }, tagPrefix = "island-preview-scene", key = { it.name })
    }
}

private data class PreviewFrame(val left: Float, val width: Float, val height: Float)

/** The island's frame (in screen px) for a scene, from the real geometry: what the island would be on this phone with these settings. */
private fun targetFor(scene: PreviewScene, s: PreviewSettings, env: IslandEnvironment, d: Float): PreviewFrame {
    val wide = s.wideMode.wideFactor
    val f = when (scene) {
        PreviewScene.IDLE, PreviewScene.PRIVACY, PreviewScene.THREE -> IslandGeometry.frame(env, d, 0f, s.scale, wideFactor = 1f)
        // Music keeps the wide pill unless wide mode is off (then, over other apps, it is the plain pill with the art and the bars).
        PreviewScene.MUSIC -> IslandGeometry.frame(env, d, 0f, s.scale, wideFactor = if (s.wideMode == IslandWideMode.OFF) 1f else wide)
        PreviewScene.LIVE -> IslandGeometry.frame(env, d, 0f, s.scale,
            extraWidthDp = if (!s.liveUpdates) 0f else when (s.compactLevel) { 0 -> 0f; 1 -> IslandGeometry.eventExtraWidthDp(5, s.scale); else -> IslandGeometry.eventExtraWidthDp(16, s.scale) },
            wideFactor = 1f)
        PreviewScene.NOTIFICATION -> if (s.peekLevel == 0) IslandGeometry.frame(env, d, 0f, s.scale, extraWidthDp = IslandGeometry.eventExtraWidthDp(16, s.scale))
            else IslandGeometry.frame(env, d, 1f, s.scale, extraBodyDp = if (s.peekLevel == 2) 26f else 0f)
        PreviewScene.OPEN -> IslandGeometry.frame(env, d, 1f, s.scale, extraBodyDp = if (s.shortcuts) 48f else 0f)
    }
    return PreviewFrame(f.left, f.width, f.height)
}

@Composable
private fun sceneNames(): Map<PreviewScene, String> = mapOf(
    PreviewScene.IDLE to stringResource(R.string.island_preview_idle), PreviewScene.MUSIC to stringResource(R.string.island_preview_music),
    PreviewScene.LIVE to stringResource(R.string.island_preview_live), PreviewScene.THREE to stringResource(R.string.island_preview_three),
    PreviewScene.NOTIFICATION to stringResource(R.string.island_preview_notification), PreviewScene.PRIVACY to stringResource(R.string.island_preview_privacy),
    PreviewScene.OPEN to stringResource(R.string.island_preview_open))

@Composable
private fun caption(scene: PreviewScene, s: PreviewSettings): String = when (scene) {
    PreviewScene.IDLE -> stringResource(R.string.island_preview_caption_idle, (s.scale * 100).toInt())
    PreviewScene.MUSIC -> stringResource(when (s.wideMode) {
        IslandWideMode.OFF -> R.string.island_preview_caption_music_off; IslandWideMode.WIDE -> R.string.island_preview_caption_music_wide
        IslandWideMode.EXTRA_WIDE -> R.string.island_preview_caption_music_extra })
    PreviewScene.LIVE -> if (!s.liveUpdates) stringResource(R.string.island_preview_caption_live_off)
        else stringResource(when (s.compactLevel) { 0 -> R.string.island_preview_caption_live_glyph; 1 -> R.string.island_preview_caption_live_status; else -> R.string.island_preview_caption_live_title })
    PreviewScene.THREE -> stringResource(R.string.island_preview_caption_three)
    PreviewScene.NOTIFICATION -> stringResource(when (s.peekLevel) { 0 -> R.string.island_preview_caption_peek_name; 1 -> R.string.island_preview_caption_peek_title; else -> R.string.island_preview_caption_peek_text })
    PreviewScene.PRIVACY -> stringResource(if (s.privacyIndicators) R.string.island_preview_caption_privacy_on else R.string.island_preview_caption_privacy_off)
    PreviewScene.OPEN -> stringResource(if (s.shortcuts) R.string.island_preview_caption_open_shortcuts else R.string.island_preview_caption_open)
}

/** A plain status bar behind the island: the time on the left, signal, Wi-Fi and battery on the right, in the island's ink. */
@Composable
private fun StatusBarMock(env: IslandEnvironment, k: Float, ink: Color) {
    val density = LocalDensity.current
    val barHeight = with(density) { (env.statusBarHeight * k).toDp() }
    Row(Modifier.fillMaxWidth().height(barHeight).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.island_preview_time), color = Color.White, style = UnoType.footnote.tight, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        listOf(Icons.Rounded.SignalCellularAlt, Icons.Rounded.Wifi, Icons.Rounded.BatteryFull).forEach {
            Icon(it, null, tint = Color.White, modifier = Modifier.padding(start = 3.dp).size(14.dp))
        }
    }
}

@Composable
private fun Capsule(x: Dp, y: Dp, size: Dp, tint: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, fill: Color) {
    Box(Modifier.offset(x, y).size(size).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * .55f))
    }
}

/** What the island shows in each scene: the art and bars for music, the glyph and text for a Live Update, the card for a notification... */
@Composable
private fun SceneFace(scene: PreviewScene, s: PreviewSettings, theme: IslandTheme, k: Float, holePx: Float, bodyTop: Dp) {
    val density = LocalDensity.current
    val glyph = (with(density) { (holePx * k).toDp() } * 1.05f).coerceIn(14.dp, 30.dp)
    val ink = theme.ink
    when (scene) {
        PreviewScene.IDLE -> Unit
        PreviewScene.MUSIC -> Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(glyph).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(listOf(theme.playback, theme.focus))))
            Spacer(Modifier.weight(1f))
            Equalizer(theme.playback, glyph)
        }
        PreviewScene.LIVE -> Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.DeliveryDining, null, tint = theme.highlight, modifier = Modifier.size(glyph))
            Spacer(Modifier.weight(1f))
            if (s.liveUpdates && s.compactLevel > 0) Text(stringResource(if (s.compactLevel == 1) R.string.island_preview_eta else R.string.island_preview_delivery),
                color = ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Clip)
        }
        PreviewScene.THREE -> Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.DeliveryDining, null, tint = theme.highlight, modifier = Modifier.size(glyph))
            Spacer(Modifier.weight(1f))
        }
        PreviewScene.PRIVACY -> Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (s.privacyIndicators) Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF30D158)))
        }
        PreviewScene.NOTIFICATION -> if (s.peekLevel == 0) Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(glyph).clip(theme.iconShapeOrCircle()).background(Color(0xFF34C759)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ChatBubble, null, tint = Color.White, modifier = Modifier.size(glyph * .6f))
            }
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.island_preview_app), color = ink, style = UnoType.caption1.tight, fontWeight = FontWeight.SemiBold, maxLines = 1)
        } else Column(Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = bodyTop, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(28.dp).clip(theme.iconShapeOrCircle()).background(Color(0xFF34C759)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ChatBubble, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.island_preview_sender), color = ink, style = UnoType.footnote.tight, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (s.peekLevel == 2) Text(stringResource(R.string.island_preview_message), color = ink.copy(alpha = .75f), style = UnoType.caption1.tight, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    else Text(stringResource(R.string.island_preview_app), color = ink.copy(alpha = .6f), style = UnoType.caption2.tight, maxLines = 1)
                }
            }
            if (s.peekActions) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.island_preview_reply, R.string.island_preview_mark_read).forEach { label ->
                    Box(Modifier.clip(theme.controlShape()).background(theme.chip()).padding(horizontal = 12.dp, vertical = 5.dp)) {
                        Text(stringResource(label), color = ink, style = UnoType.caption2.tight)
                    }
                }
            }
        }
        PreviewScene.OPEN -> Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = bodyTop, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.island_preview_time), color = ink, style = UnoType.title2.tight, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.island_preview_date), color = ink.copy(alpha = .65f), style = UnoType.caption2.tight)
                }
                Box(Modifier.clip(theme.controlShape()).background(theme.chip()).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Icon(Icons.Rounded.Search, null, tint = ink, modifier = Modifier.size(16.dp))
                }
            }
            if (s.shortcuts) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Color(0xFF0A84FF) to Icons.Rounded.Phone, Color(0xFF34C759) to Icons.Rounded.ChatBubble,
                    Color(0xFFFF9F0A) to Icons.Rounded.PhotoCamera, Color(0xFF5E5CE6) to Icons.Rounded.Map).forEach { (c, icon) ->
                    Box(Modifier.size(34.dp).clip(theme.iconShapeOrCircle()).background(c), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** Three bars that rise and fall, the island's playing mark. */
@Composable
private fun Equalizer(color: Color, size: Dp) {
    val t = rememberInfiniteTransition(label = "eq")
    val phases = listOf(0, 180, 90).map { delay ->
        t.animateFloat(.3f, 1f, infiniteRepeatable(tween(420, delayMillis = delay), RepeatMode.Reverse), label = "bar$delay")
    }
    Row(Modifier.size(size), horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        phases.forEach { h -> Box(Modifier.width(size / 6).fillMaxHeight(h.value).clip(RoundedCornerShape(2.dp)).background(color)) }
    }
}

/** The theme's own app-icon outline, so the preview's app glyphs match the island's. */
private fun IslandTheme.iconShapeOrCircle(): androidx.compose.ui.graphics.Shape = iconShape.shape()
