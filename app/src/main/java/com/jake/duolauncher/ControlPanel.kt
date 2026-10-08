package com.jake.duolauncher

import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight

/** The panel behind the right-hand swipe down: media, volume, brightness, ringer and flashlight, with
 * a hand-off to the system's own Quick Settings for everything this panel doesn't cover.
 *
 * It needs no accessibility service (only the hand-off does) and nothing in it leaves the device.
 * Everything runs through [SystemControls], which is only alive while this is composed.
 */
@Composable
internal fun ControlPanel(
    glass: PageGlass?,
    extras: ExtrasState,
    apps: List<AppEntry>,
    onToggleFocus: () -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    onCustomize: () -> Unit,
    onDismiss: () -> Unit,
    onSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controls = rememberSystemControls()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val tick = { UnoFeedback.play(Cue.TICK, haptic) }
    val shape = Corner.xlarge
    // The panel's surface takes the wallpaper hue too when "Color from wallpaper" is on, like every other glass surface.
    val accent = LocalWallpaperAccent.current
    val surface = MaterialTheme.colorScheme.surface.let { base ->
        if (accent != null) androidx.compose.ui.graphics.lerp(base, accent.glass, .5f) else base
    }.copy(alpha = .58f)
    val ink = MaterialTheme.colorScheme.onSurface
    val panelBackdrop = rememberLayerBackdrop()
    val panelGlass = glass?.let { PageGlass(panelBackdrop, surface, it.settings) }
    val noRipple = remember { MutableInteractionSource() }

    @Composable
    fun PanelTileView(tile: PanelTile, modifier: Modifier) = when (tile) {
        PanelTile.MEDIA -> MediaTile(controls, ink, tick, modifier)
        PanelTile.VOLUME -> SliderTile(Icons.AutoMirrored.Rounded.VolumeUp, "Volume",
            ControlLogic.volumeFraction(controls.volume, controls.volumeMax), ink, panelGlass, "cp-volume",
            detents = controls.volumeMax, modifier = modifier) { controls.setVolume(it) }
        PanelTile.BRIGHTNESS ->
            if (controls.canWriteBrightness) SliderTile(Icons.Rounded.WbSunny, "Brightness",
                ControlLogic.brightnessFraction(controls.brightness), ink, panelGlass, "cp-brightness", modifier = modifier) { controls.setBrightness(it) }
            else TileRow(ink, modifier.clickable {
                // Writing system brightness is a special access the user grants; ask only when they reach for it.
                context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                onDismiss()
            }.testTag("cp-brightness-access")) {
                Icon(Icons.Rounded.WbSunny, null, tint = ink.copy(alpha = .8f), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Allow brightness control", color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("Needs “Modify system settings” access", color = ink.copy(alpha = .6f), fontSize = 12.sp)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = ink.copy(alpha = .6f))
            }
        PanelTile.RINGER -> RingerTile(controls, ink, modifier, tick, onNeedAccess = {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            onDismiss()
        })
        PanelTile.FLASHLIGHT -> TorchTile(controls, ink, modifier, tick)
        PanelTile.FOCUS -> TileRow(ink, modifier.clickable { UnoFeedback.play(Cue.TOGGLE, haptic); onToggleFocus() }.testTag("cp-focus")) {
            Icon(Icons.Rounded.Bedtime, null, tint = if (extras.focusOn) IslandSymbol.FOCUS.tint else ink.copy(alpha = .8f),
                modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Focus", color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(if (extras.focusHidden.isEmpty()) "Choose apps to hide in Customize"
                    else if (extras.focusOn) "${extras.focusHidden.size} apps hidden" else "Hides ${extras.focusHidden.size} apps",
                    color = ink.copy(alpha = .6f), fontSize = 12.sp)
            }
            LiquidSwitchControl(extras.focusOn, { UnoFeedback.play(Cue.TOGGLE, haptic); onToggleFocus() }, panelGlass)
        }
        PanelTile.SHORTCUTS -> {
            val chosen = extras.shortcuts.mapNotNull { id -> apps.firstOrNull { it.id == id } }
            if (chosen.isEmpty()) TileRow(ink, modifier.clickable { onCustomize() }.testTag("cp-shortcuts")) {
                Icon(Icons.Rounded.Apps, null, tint = ink.copy(alpha = .8f), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text("Add shortcuts in Customize", color = ink.copy(alpha = .8f), fontSize = 14.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ChevronRight, null, tint = ink.copy(alpha = .6f))
            } else Row(modifier.fillMaxWidth().clip(TileShape).background(ink.copy(alpha = .08f)).padding(vertical = 8.dp)
                .testTag("cp-shortcuts"), horizontalArrangement = Arrangement.SpaceEvenly) {
                chosen.forEach { app ->
                    Image(app.icon.asImageBitmap(), app.label, Modifier.size(48.dp).clip(Corner.icon)
                        .clickable { onLaunchApp(app) }.testTag("cp-shortcut-${app.id}"))
                }
            }
        }
        PanelTile.SYSTEM -> TileRow(ink, modifier.clickable { onSystemSettings() }.testTag("cp-system-settings")) {
            Icon(Icons.Rounded.Tune, null, tint = ink.copy(alpha = .8f), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text("System settings", color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, tint = ink.copy(alpha = .6f))
        }
    }

    // The scrim: a tap or an upward swipe anywhere outside the panel closes it.
    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = .22f))
        .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss)
        .pointerInput(Unit) {
            var travelled = 0f
            detectVerticalDragGestures(onDragStart = { travelled = 0f },
                onDragEnd = { if (travelled < -48.dp.toPx()) onDismiss() }) { _, amount -> travelled += amount }
        }.testTag("control-panel-scrim"),
        contentAlignment = Alignment.TopCenter) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp).padding(top = 8.dp).widthIn(max = 460.dp).fillMaxWidth()
            .clickable(interactionSource = noRipple, indication = null) {} // taps on the panel don't dismiss it
            .then(if (glass != null) Modifier.drawBackdrop(
                backdrop = glass.backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    lens(lerp(8f, 44f, glass.settings.height).dp.toPx(),
                        lerp(16f, 132f, glass.settings.amount).dp.toPx(),
                        depthEffect = true, chromaticAberration = glass.settings.chromatic > 0.05f)
                },
                highlight = { GlassRim.Light },
                exportedBackdrop = panelBackdrop,
                onDrawSurface = { drawRect(surface) })
            else Modifier.background(MaterialTheme.colorScheme.surface, shape))
            .padding(14.dp).testTag("control-panel"),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val rows = PanelLayout.rows(PanelLayout.visible(extras.tileOrder, extras.hiddenTiles))
            rows.forEach { row ->
                if (row.size == 2) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { tile -> PanelTileView(tile, Modifier.weight(if (tile == PanelTile.RINGER) 3f else 1f)) }
                } else PanelTileView(row.first(), Modifier)
            }
        }
    }
}

private val TileShape: Shape = Corner.large

@Composable
private fun TileRow(ink: Color, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).clip(TileShape).background(ink.copy(alpha = .08f))
        .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
private fun MediaTile(controls: SystemControls, ink: Color, tick: () -> Unit, modifier: Modifier = Modifier) {
    // With notification access and media details on, the track's title and artwork are known and the
    // buttons drive that session directly; otherwise they send media keys to whatever is playing.
    val track = NotificationFeed.nowPlaying
    val playing = track?.playing ?: controls.playing
    val transport = { action: (android.media.session.MediaController.TransportControls) -> Unit, key: Int ->
        tick()
        if (track != null) Thread { runCatching { action(track.controller.transportControls) } }.start()
        else controls.mediaKey(key)
    }
    TileRow(ink, modifier.testTag("cp-media")) {
        val art = track?.art
        if (art != null) Image(art.asImageBitmap(), null, Modifier.size(40.dp).clip(Corner.small).testTag("cp-art"))
        else if (playing) EqualizerBars(PLAYBACK_PINK, Modifier.testTag("cp-playing"))
        else Icon(Icons.Rounded.MusicNote, null, tint = ink.copy(alpha = .6f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(track?.title ?: if (playing) "Playing" else "Media", color = ink, fontSize = 14.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.testTag("cp-track-title"))
            track?.artist?.let { Text(it, color = ink.copy(alpha = .6f), fontSize = 12.sp, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
        }
        PanelButton(Icons.Rounded.SkipPrevious, "Previous", ink) { transport({ it.skipToPrevious() }, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        PanelButton(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            if (playing) "Pause" else "Play", ink) { transport({ if (playing) it.pause() else it.play() }, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        PanelButton(Icons.Rounded.SkipNext, "Next", ink) { transport({ it.skipToNext() }, KeyEvent.KEYCODE_MEDIA_NEXT) }
    }
}

@Composable
private fun PanelButton(icon: ImageVector, label: String, ink: Color, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick)
        .semantics { contentDescription = label }.testTag("cp-${label.lowercase()}"), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun SliderTile(icon: ImageVector, label: String, fraction: Float, ink: Color, glass: PageGlass?,
    tag: String, modifier: Modifier = Modifier, detents: Int = 0, onChange: (Float) -> Unit) {
    TileRow(ink, modifier.testTag(tag).semantics { contentDescription = label }) {
        Icon(icon, null, tint = ink.copy(alpha = .8f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        LiquidSliderControl(fraction, 0f..1f, onChange, glass, Modifier.weight(1f), detents = detents)
    }
}

@Composable
private fun RingerTile(controls: SystemControls, ink: Color, modifier: Modifier, tick: () -> Unit, onNeedAccess: () -> Unit) {
    val segments = listOf(
        Triple(AudioManager.RINGER_MODE_NORMAL, Icons.Rounded.NotificationsActive, "Ring"),
        Triple(AudioManager.RINGER_MODE_VIBRATE, Icons.Rounded.Vibration, "Vibrate"),
        Triple(AudioManager.RINGER_MODE_SILENT, Icons.Rounded.NotificationsOff, "Silent"))
    Row(modifier.height(52.dp).clip(TileShape).background(ink.copy(alpha = .08f)).padding(4.dp)
        .testTag("cp-ringer"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        segments.forEach { (mode, icon, label) ->
            val selected = controls.ringerMode == mode
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(percent = 50))
                .background(if (selected) ink.copy(alpha = .85f) else Color.Transparent)
                .clickable {
                    tick()
                    if (controls.requestRinger(mode) == RingerRequest.NEEDS_DND_ACCESS) onNeedAccess()
                }.semantics { contentDescription = if (selected) "$label, selected" else label }
                .testTag("cp-ringer-${label.lowercase()}"), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.surface else ink, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun TorchTile(controls: SystemControls, ink: Color, modifier: Modifier, tick: () -> Unit) {
    val on = controls.torchOn
    val available = controls.torchAvailable
    Box(modifier.height(52.dp).clip(TileShape)
        .background(if (on) Color.White.copy(alpha = .92f) else ink.copy(alpha = .08f))
        .clickable(enabled = available) { tick(); controls.toggleTorch() }
        .semantics { contentDescription = if (on) "Flashlight, on" else if (available) "Flashlight, off" else "Flashlight, unavailable" }
        .testTag("cp-torch"), contentAlignment = Alignment.Center) {
        Icon(if (on) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff, null,
            tint = if (on) Color.Black else ink.copy(alpha = if (available) 1f else .35f), modifier = Modifier.size(24.dp))
    }
}
