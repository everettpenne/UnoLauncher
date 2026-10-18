package com.jake.duolauncher.widgets

import com.jake.duolauncher.island.*
import com.jake.duolauncher.panel.*
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.PI
import kotlinx.coroutines.delay

/** The Liquid battery widget: its face (the drawing) and its own helpers. Registered in [UnoWidgets].
 *
 * The charge is a body of liquid whose surface sits at the battery level, ripples gently, and tilts with the phone so
 * the fluid appears to stay level in the room. The tilt reuses the same gravity reading and the same "tilt-following
 * highlight" opt-in the launcher already has (off by default), and needs no permission; with it off the surface just
 * ripples. Only the battery level and the charge-time estimate are read — nothing is stored or sent.
 */
@Composable
internal fun LiquidBatteryFace(slot: Int, onOptions: () -> Unit) {
    val context = LocalContext.current
    val reading by produceState(BatterySnapshot(-1, false, null)) {
        while (true) { value = readBattery(context); delay(30_000) }
    }
    val tiltEnabled = remember { context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getBoolean("tiltHighlight", false) }
    val tilt = rememberSurfaceTilt(tiltEnabled)
    // A slow, always-on ripple so the surface reads as liquid even when the phone is flat and tilt-following is off.
    // The picker's preview holds still (a frozen frame is enough to show the look, and a list of animated cards is wasted work);
    // with Android's animations off (developer "remove animations") the surface holds the same still frame instead of rippling.
    val previewing = LocalWidgetPreview.current
    val animationsOn = remember {
        android.provider.Settings.Global.getFloat(context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    val phase: Float
    val rise: Float
    if (previewing || !animationsOn) { phase = 1.1f; rise = .35f }
    else {
        val transition = rememberInfiniteTransition(label = "liquid-battery")
        val ripple by transition.animateFloat(0f, 2f * PI.toFloat(),
            infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "phase")
        val bubbling by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "bubbles")
        phase = ripple; rise = bubbling
    }
    // The whole card is the vessel: the liquid fills it from the bottom, edge to edge.
    GlassCard(onClick = onOptions, modifier = Modifier.testTag("uno-liquid-battery-$slot"), padding = 0.dp) {
        val ink = LocalGlassInk.current
        val fill = LiquidBattery.fill(reading.percent)
        val accent = LocalWallpaperAccent.current?.accent
        val liquid = LiquidBattery.liquidColor(reading.percent, reading.charging, accent)
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize().testTag("liquid-battery-canvas-$slot")) {
                val w = size.width; val h = size.height
                fun surface(level: Float, amp: Float, ph: Float, tiltScale: Float) = LiquidBattery.surfacePoints(level, tilt * tiltScale, amp, ph)
                    .map { (x, y) -> Offset(x * w, h - y * h) }
                fun body(points: List<Offset>) = Path().apply {
                    moveTo(0f, h); points.forEach { lineTo(it.x, it.y) }; lineTo(w, h); close()
                }
                if (fill > 0f) {
                    // A second, slower wave behind the first gives the liquid depth.
                    val back = surface((fill + .018f).coerceAtMost(1f), LiquidBattery.AMPLITUDE * 1.4f, -phase * .7f + 1.3f, .8f)
                    drawPath(body(back), Brush.verticalGradient(listOf(liquid.copy(alpha = .38f), liquid.copy(alpha = .16f)),
                        startY = back.minOf { it.y }, endY = h))
                    val front = surface(fill, LiquidBattery.AMPLITUDE, phase, 1f)
                    val top = front.minOf { it.y }
                    drawPath(body(front), Brush.verticalGradient(listOf(liquid.copy(alpha = .94f), liquid.copy(alpha = .62f)), startY = top, endY = h))
                    // Light under the waterline, a bright meniscus on it, and a soft glint down the left side where the glass catches the light.
                    clipPath(body(front)) {
                        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .30f), Color.Transparent), startY = top, endY = top + 26.dp.toPx()))
                        drawRect(Brush.horizontalGradient(listOf(Color.White.copy(alpha = .20f), Color.Transparent), endX = w * .30f))
                        if (reading.charging) LiquidBattery.bubbles(rise).forEach { b ->
                            val bx = b.x * w; val by = h - (h - top) * b.y
                            drawCircle(Color.White.copy(alpha = .30f * (1f - b.y)), b.size * 4.dp.toPx(), Offset(bx, by))
                            drawCircle(Color.White.copy(alpha = .45f * (1f - b.y)), b.size * 4.dp.toPx(), Offset(bx, by), style = Stroke(1.dp.toPx()))
                        }
                    }
                    val line = Path().apply { moveTo(front.first().x, front.first().y); front.drop(1).forEach { lineTo(it.x, it.y) } }
                    drawPath(line, Color.White.copy(alpha = .75f), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            // The words: white where the liquid is behind them, the glass ink where it is not.
            val onLiquid = fill > .5f
            val primary = if (onLiquid) Color.White else ink.primary
            Row(Modifier.padding(start = 14.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (reading.charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd, null,
                    tint = if (fill > .84f) Color.White.copy(alpha = .85f) else ink.soft(), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(androidx.compose.ui.res.stringResource(com.jake.duolauncher.R.string.battery_widget_title), color = if (fill > .84f) Color.White.copy(alpha = .85f) else ink.soft(), fontSize = 12.sp)
            }
            val shadow = if (onLiquid) androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = .28f), Offset(0f, 2f), 8f) else null
            Text(if (reading.percent >= 0) "${reading.percent}%" else "—", color = primary, fontSize = 36.sp, fontWeight = FontWeight.Light,
                style = androidx.compose.ui.text.TextStyle(shadow = shadow),
                modifier = Modifier.align(Alignment.Center).offset(y = 6.dp).testTag("liquid-battery-percent"))
            Text(reading.toFull ?: if (reading.charging) "Charging" else "On battery",
                color = if (fill > .12f) Color.White.copy(alpha = .92f) else ink.soft(), fontSize = 11.sp, maxLines = 1,
                style = androidx.compose.ui.text.TextStyle(shadow = if (fill > .12f) androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = .25f), Offset(0f, 1f), 4f) else null),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
        }
    }
}

/** One battery reading, reduced to what the widget draws. */
private data class BatterySnapshot(val percent: Int, val charging: Boolean, val toFull: String?)

private fun readBattery(context: Context): BatterySnapshot {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    val toFull = if (charging && status != BatteryManager.BATTERY_STATUS_FULL)
        runCatching { ChargeText.toFull(context.getSystemService(BatteryManager::class.java).computeChargeTimeRemaining()) }.getOrNull() else null
    return BatterySnapshot(percent, charging, toFull)
}

/** Reads gravity (or the accelerometer where there is no gravity sensor) and returns a smoothed surface tilt in -1..1,
 *  only while the phone is on screen. It needs no permission; if GrapheneOS's Sensors toggle is off the sensor simply
 *  reports nothing and the surface stays flat. */
@Composable
private fun rememberSurfaceTilt(enabled: Boolean): Float {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var tilt by remember { mutableFloatStateOf(0f) }
    DisposableEffect(enabled, lifecycle) {
        if (!enabled) { tilt = 0f; return@DisposableEffect onDispose { } }
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) return@DisposableEffect onDispose { }
        var smoothed = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                smoothed = LiquidBattery.smooth(smoothed,
                    LiquidBattery.tilt(event.values[0], event.values[1], event.values[2]))
                tilt = smoothed
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        var registered = false
        fun register() { if (!registered) { registered = manager.registerListener(listener, sensor, 50_000) } }
        fun unregister() { if (registered) { manager.unregisterListener(listener); registered = false } }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> register()
                Lifecycle.Event.ON_STOP -> unregister()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) register()
        onDispose { lifecycle.removeObserver(observer); unregister(); tilt = 0f }
    }
    return tilt
}

/** The Liquid battery widget's rules, kept apart from the drawing so each is unit-tested. */
internal object LiquidBattery {
    const val SEGMENTS = 48
    const val AMPLITUDE = .035f
    const val MAX_TILT = .3f
    private const val G = 9.81f

    /** The liquid's colour: green while charging, orange when low, red when nearly empty, otherwise the wallpaper accent (or a clear blue). */
    fun liquidColor(percent: Int, charging: Boolean, accent: Color?): Color = when {
        charging -> Color(0xFF34C759)
        percent in 0..10 -> Color(0xFFFF453A)
        percent in 0..20 -> Color(0xFFFF9500)
        else -> accent ?: Color(0xFF38A8FF)
    }

    /** A bubble rising through the liquid: [x] across the width, [y] up from the bottom as a fraction of the liquid's depth, [size] relative. */
    data class Bubble(val x: Float, val y: Float, val size: Float)

    /** Six bubbles at fixed places across the width, each rising at its own speed and phase as [progress] (0..1) loops. */
    fun bubbles(progress: Float): List<Bubble> = listOf(
        Triple(.18f, 0f, .9f), Triple(.34f, .45f, .6f), Triple(.52f, .2f, 1.1f), Triple(.66f, .7f, .7f), Triple(.80f, .35f, .85f), Triple(.90f, .9f, .55f),
    ).map { (x, offset, size) ->
        val speed = 1f + offset * .6f
        Bubble(x + .02f * sin((progress * speed + offset) * 2f * PI.toFloat()), (progress * speed + offset) % 1f, size)
    }

    /** The fill level from a battery percent, empty for an unknown reading. */
    fun fill(percent: Int): Float = if (percent < 0) 0f else (percent / 100f).coerceIn(0f, 1f)

    /** The surface tilt in -1..1 from a gravity reading: the liquid stays level in the room, so its surface leans
     *  against the phone's roll. Lying flat there is no "sideways", so the tilt fades out below about a fifth of g. */
    fun tilt(gx: Float, gy: Float, gz: Float): Float {
        val planar = hypot(gx, gy)
        val weight = ((planar / G - .2f) / .4f).coerceIn(0f, 1f)
        val raw = Math.toDegrees(atan2((-gx).toDouble(), gy.toDouble())).toFloat() / 90f
        return (raw * weight).coerceIn(-1f, 1f)
    }

    /** Exponential smoothing, so sensor noise never makes the surface jitter. */
    fun smooth(previous: Float, next: Float, alpha: Float = .3f): Float = previous + (next - previous) * alpha

    /** The surface height (0 is the bottom, 1 the top) at [x] across the width: the fill level, tilted by the phone,
     *  and rippled by a gentle sine wave. */
    fun surfaceY(x: Float, fill: Float, tilt: Float, amplitude: Float, phase: Float): Float {
        val wave = amplitude * sin(2.0 * PI * x + phase).toFloat()
        return (fill + (x - .5f) * tilt.coerceIn(-1f, 1f) * MAX_TILT + wave).coerceIn(0f, 1f)
    }

    /** [SEGMENTS] + 1 points across the width, each an (x, y) pair in 0..1. */
    fun surfacePoints(fill: Float, tilt: Float, amplitude: Float, phase: Float,
        segments: Int = SEGMENTS): List<Pair<Float, Float>> =
        (0..segments).map { i -> val x = i.toFloat() / segments; x to surfaceY(x, fill, tilt, amplitude, phase) }
}
