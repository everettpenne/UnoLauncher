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
    val transition = rememberInfiniteTransition(label = "liquid-battery")
    val phase by transition.animateFloat(0f, 2f * PI.toFloat(),
        infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "phase")
    GlassCard(onClick = onOptions, modifier = Modifier.testTag("uno-liquid-battery-$slot")) {
        val ink = LocalGlassInk.current
        CardTitle(Icons.Rounded.BatteryChargingFull, "Battery")
        val fill = LiquidBattery.fill(reading.percent)
        val liquidColor = when {
            reading.charging -> IosGreen
            reading.percent in 0..20 -> IosOrange
            else -> ink.primary
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().testTag("liquid-battery-canvas-$slot")) {
                val inset = 1.5.dp.toPx()
                val left = inset; val top = inset; val right = size.width - inset; val bottom = size.height - inset
                val corner = 14.dp.toPx()
                clipPath(Path().apply { addRoundRect(RoundRect(left, top, right, bottom, corner, corner)) }) {
                    val points = LiquidBattery.surfacePoints(fill, tilt, LiquidBattery.AMPLITUDE, phase)
                    val surface = points.map { (x, y) ->
                        Offset(left + x * (right - left), bottom - y * (bottom - top))
                    }
                    val liquid = Path().apply {
                        moveTo(left, bottom)
                        surface.forEach { lineTo(it.x, it.y) }
                        lineTo(right, bottom)
                        close()
                    }
                    drawPath(liquid, Brush.verticalGradient(
                        0f to liquidColor.copy(alpha = .95f), 1f to liquidColor.copy(alpha = .55f),
                        startY = surface.first().y, endY = bottom))
                    // The waterline: a thin light edge where the liquid meets the air.
                    val surfaceLine = Path().apply {
                        moveTo(surface.first().x, surface.first().y)
                        surface.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(surfaceLine, Color.White.copy(alpha = .4f), style = Stroke(1.5.dp.toPx()))
                }
            }
            Text(if (reading.percent >= 0) "${reading.percent}%" else "—",
                color = onInk(ink), fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("liquid-battery-percent"))
        }
        Text(reading.toFull ?: if (reading.charging) "Charging" else "On battery",
            color = ink.soft(), fontSize = 11.sp, maxLines = 1,
            modifier = Modifier.align(Alignment.CenterHorizontally))
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
