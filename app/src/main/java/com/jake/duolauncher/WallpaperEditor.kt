package com.jake.duolauncher

import com.jake.duolauncher.appearance.*
import com.jake.duolauncher.panel.LiquidSliderControl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The wallpaper editor, in the manner of iOS 26's: the photo fills the screen as it will on Home, with Cancel and Done in glass capsules at
 * the top. Swipe sideways to try the styles (Natural, Vivid, Black & White, Duotone, Color Wash, Studio Dim), whose name shows in a pill with
 * a dot for each; pinch and drag with two fingers to frame it. Along the bottom: Blur on or off, Adjust (brightness, colour, warmth and blur
 * by hand), and a preview of Home's icons over it. A built-in scene opens here too, with just Cancel and Done.
 *
 * Shown whenever a photo or a built-in scene is waiting to be applied (see [LauncherBackgroundController.previewPending] and
 * [LauncherBackgroundController.pendingBuiltin]); nothing changes the real background until Done.
 *
 * The photo here is drawn on the GPU with the same crop ([WallpaperAdjustMath.cropRect]) and colour matrix
 * ([WallpaperAdjustMath.colorMatrix]) that [WallpaperAdjustMath.bake] uses for the saved picture, so framing and styles follow the fingers
 * frame by frame; only the blur is drawn differently (a render-effect blur of the same radius, where the saved picture is scaled down and
 * up), which looks the same at a glance.
 */
@Composable
internal fun WallpaperEditor(backgrounds: LauncherBackgroundController, state: LauncherState, dark: Boolean) {
    val builtin = backgrounds.pendingBuiltin
    val photo = backgrounds.previewBitmap.takeIf { backgrounds.previewPending }
    if (builtin == null && !backgrounds.previewPending) return
    val cancel = { if (builtin != null) backgrounds.cancelBuiltin() else backgrounds.cancelPreview() }
    Dialog(onDismissRequest = cancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val backdrop = rememberLayerBackdrop()
        var showHome by rememberSaveable { mutableStateOf(true) }
        var adjusting by rememberSaveable { mutableStateOf(false) }
        val adjust = backgrounds.adjust
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("wallpaper-editor")) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                when {
                    builtin != null -> Canvas(Modifier.fillMaxSize()) { drawBuiltin(builtin, dark) }
                    photo != null -> EditablePhoto(photo, adjust, backgrounds::updateAdjust)
                    else -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                }
            }
            if (showHome) HomeOverlay(state, Modifier.fillMaxSize())

            val glass = remember(backdrop) { PageGlass(backdrop, Color.White.copy(alpha = .14f), GlassSettings.Default) }
            CompositionLocalProvider(LocalPageGlass provides glass, LocalKitInk provides Color.White) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = Space.L.dp, vertical = Space.S.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        EditorCapsule(stringResource(R.string.cancel), backdrop, if (builtin != null) "builtin-cancel" else "background-preview-cancel",
                            onClick = cancel)
                        Spacer(Modifier.weight(1f))
                        EditorCapsule(stringResource(R.string.wallpaper_done), backdrop, if (builtin != null) "builtin-apply" else "background-preview-apply",
                            prominent = true, busy = backgrounds.applying,
                            enabled = builtin != null || (photo != null && !backgrounds.applying)) {
                            if (builtin != null) backgrounds.applyBuiltin() else backgrounds.applyPreview()
                        }
                    }
                    if (photo != null) StyleName(adjust.style, backdrop, Modifier.align(Alignment.CenterHorizontally).padding(top = Space.M.dp))
                    if (builtin != null) EditorPill(builtin.label, backdrop, Modifier.align(Alignment.CenterHorizontally).padding(top = Space.M.dp))
                    backgrounds.errorMessage?.let { EditorPill(it, backdrop, Modifier.align(Alignment.CenterHorizontally).padding(top = Space.S.dp)) }
                    Spacer(Modifier.weight(1f))
                    AnimatedVisibility(adjusting && photo != null, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut() + slideOutVertically { it / 3 }) {
                        AdjustPanel(adjust, backdrop, backgrounds::updateAdjust)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = Space.M.dp), horizontalArrangement = Arrangement.spacedBy(Space.XL.dp, Alignment.CenterHorizontally)) {
                        if (photo != null) {
                            EditorCircle(Icons.Rounded.BlurOn, stringResource(R.string.wallpaper_blur), adjust.blur > 0f, backdrop, "wallpaper-blur-toggle") {
                                backgrounds.updateAdjust(adjust.copy(blur = if (adjust.blur > 0f) 0f else .45f))
                            }
                            EditorCircle(Icons.Rounded.Tune, stringResource(R.string.wallpaper_adjust_heading), adjusting, backdrop, "wallpaper-adjust-toggle") {
                                adjusting = !adjusting
                            }
                        }
                        EditorCircle(Icons.Rounded.GridView, stringResource(R.string.wallpaper_show_home), showHome, backdrop, "wallpaper-home-toggle") {
                            showHome = !showHome
                        }
                    }
                }
            }
        }
    }
}

/**
 * The photo, framed and toned as it will be saved. Two fingers pinch and drag the framing (the photo follows the fingers); one finger
 * swiping sideways moves to the next or previous style.
 */
@Composable
private fun EditablePhoto(photo: android.graphics.Bitmap, adjust: WallpaperAdjust, onAdjust: (WallpaperAdjust) -> Unit) {
    val image = remember(photo) { photo.asImageBitmap() }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val latest by rememberUpdatedState(adjust)
    val swipe = with(LocalDensity.current) { 64.dp.toPx() }
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val blurPx = WallpaperAdjustMath.blurFraction(adjust) * min(size.width, size.height)
    val filter = remember(adjust.brightness, adjust.saturation, adjust.warmth, adjust.style) {
        ColorFilter.colorMatrix(ColorMatrix(WallpaperAdjustMath.colorMatrix(adjust)))
    }
    Canvas(Modifier.fillMaxSize()
        .then(if (blurPx >= 1f) Modifier.blur(with(density) { blurPx.toDp() }) else Modifier)
        .pointerInput(photo) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var travel = 0f
                var framed = false
                do {
                    val event = awaitPointerEvent()
                    val down = event.changes.count { it.pressed }
                    if (down >= 2) {
                        framed = true
                        val a = latest
                        val zoom = (a.zoom * event.calculateZoom()).coerceIn(1f, WallpaperAdjust.MAX_ZOOM)
                        val pan = event.calculatePan()
                        val crop = WallpaperAdjustMath.cropRect(photo.width, photo.height, size.width.toFloat() / size.height.coerceAtLeast(1), a.copy(zoom = zoom))
                        // Screen pixels to photo pixels at this zoom, then to the fraction of the room the crop has to move in.
                        val k = crop.width / size.width.coerceAtLeast(1)
                        val slackX = (photo.width - crop.width) / 2f; val slackY = (photo.height - crop.height) / 2f
                        val panX = if (slackX > .5f) a.panX - pan.x * k / slackX else 0f
                        val panY = if (slackY > .5f) a.panY - pan.y * k / slackY else 0f
                        onAdjust(a.copy(zoom = zoom, panX = panX, panY = panY))
                        event.changes.forEach { it.consume() }
                    } else if (!framed) {
                        travel += event.changes.firstOrNull()?.positionChange()?.x ?: 0f
                    }
                } while (event.changes.any { it.pressed })
                if (!framed && abs(travel) > swipe) {
                    val styles = WallpaperStyle.entries
                    val next = (latest.style.ordinal + if (travel < 0) 1 else -1).coerceIn(0, styles.lastIndex)
                    if (next != latest.style.ordinal) { onAdjust(latest.copy(style = styles[next])); UnoFeedback.play(Cue.DETENT, haptic) }
                }
            }
        }
        .semantics { contentDescription = "Wallpaper photo"; stateDescription = adjust.style.label }) {
        size = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt())
        val crop = WallpaperAdjustMath.cropRect(photo.width, photo.height, this.size.width / this.size.height, adjust)
        drawImage(image, srcOffset = IntOffset(crop.left.roundToInt(), crop.top.roundToInt()),
            srcSize = IntSize(crop.width.roundToInt().coerceAtLeast(1), crop.height.roundToInt().coerceAtLeast(1)),
            dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()), colorFilter = filter)
    }
}

/** The style's name in a glass pill, with a dot for each style (the current one lit), as iOS shows while swiping. */
@Composable
private fun StyleName(style: WallpaperStyle, backdrop: com.kyant.backdrop.Backdrop, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        EditorPill(style.label, backdrop, Modifier.testTag("wallpaper-style"))
        Row(Modifier.padding(top = Space.S.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WallpaperStyle.entries.forEach { s ->
                val lit by animateFloatAsState(if (s == style) 1f else .4f, UnoMotion.settle(), label = "dot")
                Box(Modifier.size(6.dp).alpha(lit).background(Color.White, CircleShape))
            }
        }
    }
}

@Composable
private fun EditorPill(text: String, backdrop: com.kyant.backdrop.Backdrop, modifier: Modifier = Modifier) {
    Box(modifier.liquidGlass(backdrop, Corner.pill, Color.Black.copy(alpha = .25f), kind = GlassKind.REGULAR)
        .padding(horizontal = Space.M.dp, vertical = 6.dp)) {
        Text(text, color = Color.White, style = UnoType.footnote.copy(fontWeight = FontWeight.SemiBold))
    }
}

/** Cancel and Done: glass capsules, Done filled with the accent. */
@Composable
private fun EditorCapsule(text: String, backdrop: com.kyant.backdrop.Backdrop, tag: String, prominent: Boolean = false, busy: Boolean = false,
    enabled: Boolean = true, onClick: () -> Unit) {
    // Done is a solid accent capsule, as on iOS; a tint under the glass would mix the accent with the photo behind it.
    val surface = if (prominent) Modifier.clip(Corner.pill).background(SettingsAccent)
        else Modifier.liquidGlass(backdrop, Corner.pill, Color.Black.copy(alpha = .2f), kind = GlassKind.CLEAR)
    Box(Modifier.heightIn(min = 44.dp).then(surface)
        .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
        .padding(horizontal = 18.dp).testTag(tag), contentAlignment = Alignment.Center) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
        else Text(text, color = Color.White.copy(alpha = if (enabled) 1f else .5f), style = UnoType.body.copy(fontWeight = FontWeight.SemiBold))
    }
}

/** One of the round glass buttons along the bottom. [on] lights it in the accent. */
@Composable
private fun EditorCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, backdrop: com.kyant.backdrop.Backdrop,
    tag: String, onClick: () -> Unit) {
    Box(Modifier.size(52.dp).then(if (on) Modifier.clip(Corner.pill).background(SettingsAccent)
        else Modifier.liquidGlass(backdrop, Corner.pill, Color.Black.copy(alpha = .2f), kind = GlassKind.CLEAR))
        .clickable(role = androidx.compose.ui.semantics.Role.Switch, onClick = onClick)
        .semantics { contentDescription = label; stateDescription = if (on) "On" else "Off" }.testTag(tag), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White)
    }
}

/** The tone controls by hand, on a frosted glass card over the photo. */
@Composable
private fun AdjustPanel(adjust: WallpaperAdjust, backdrop: com.kyant.backdrop.Backdrop, onAdjust: (WallpaperAdjust) -> Unit) {
    val glass = LocalPageGlass.current
    Column(Modifier.fillMaxWidth().liquidGlass(backdrop, Corner.xlarge, Color.Black.copy(alpha = .35f), kind = GlassKind.REGULAR)
        .padding(Space.L.dp), verticalArrangement = Arrangement.spacedBy(Space.XS.dp)) {
        @Composable fun slider(label: String, value: String, v: Float, range: ClosedFloatingPointRange<Float>, tag: String, set: (Float) -> Unit) {
            Row { Text(label, Modifier.weight(1f), color = Color.White, style = UnoType.subheadline)
                Text(value, color = Color.White.copy(alpha = .7f), style = UnoType.subheadline) }
            LiquidSliderControl(v, range, set, glass, Modifier.testTag(tag).semantics { contentDescription = label })
        }
        slider(stringResource(R.string.wallpaper_brightness), signed(adjust.brightness), adjust.brightness, -1f..1f, "wallpaper-brightness") { onAdjust(adjust.copy(brightness = it)) }
        slider(stringResource(R.string.wallpaper_saturation), "${(adjust.saturation * 100).roundToInt()}%", adjust.saturation, 0f..2f, "wallpaper-saturation") { onAdjust(adjust.copy(saturation = it)) }
        slider(stringResource(R.string.wallpaper_warmth), signed(adjust.warmth), adjust.warmth, -1f..1f, "wallpaper-warmth") { onAdjust(adjust.copy(warmth = it)) }
        slider(stringResource(R.string.wallpaper_blur), "${(adjust.blur * 100).roundToInt()}%", adjust.blur, 0f..1f, "wallpaper-blur") { onAdjust(adjust.copy(blur = it)) }
        if (adjust.toneChanged) Text(stringResource(R.string.wallpaper_adjust_reset), Modifier.align(Alignment.End).clip(Corner.small)
            .clickable { onAdjust(adjust.copy(brightness = 0f, blur = 0f, saturation = 1f, warmth = 0f, style = WallpaperStyle.NATURAL)) }
            .padding(Space.S.dp).testTag("wallpaper-adjust-reset"), color = SettingsAccent, style = UnoType.subheadline.copy(fontWeight = FontWeight.SemiBold))
    }
}

private fun signed(v: Float): String = (v * 100).roundToInt().let { if (it > 0) "+$it%" else "$it%" }

/** Home's own apps over the wallpaper, faintly, so the photo can be framed around them: the first rows of the grid and the dock. */
@Composable
private fun HomeOverlay(state: LauncherState, modifier: Modifier) {
    val apps = remember(state.apps) { state.apps.associateBy { it.id } }
    val home = state.homeSlots.mapNotNull { id -> id?.let(apps::get) }.take(16)
    val dock = state.dock.mapNotNull { id -> id?.let(apps::get) }.take(4)
    Column(modifier.windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 28.dp).padding(top = 120.dp, bottom = 96.dp)
        .alpha(.92f).semantics { contentDescription = "Home preview" }) {
        home.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 26.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { Image(it.image, null, Modifier.size(58.dp).clip(Corner.icon)) }
                repeat(4 - row.size) { Spacer(Modifier.size(58.dp)) }
            }
        }
        Spacer(Modifier.weight(1f))
        if (dock.isNotEmpty()) Row(Modifier.fillMaxWidth().clip(Corner.xlarge).background(Color.White.copy(alpha = .18f)).padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            dock.forEach { Image(it.image, null, Modifier.size(54.dp).clip(Corner.icon)) }
        }
    }
}

@Composable private fun stringResource(id: Int) = androidx.compose.ui.res.stringResource(id)
