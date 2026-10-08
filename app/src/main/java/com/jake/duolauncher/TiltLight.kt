package com.jake.duolauncher

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Turning gravity into a light direction. Pure, so the mapping is unit-tested. */
internal object TiltMath {
    /** Where the light sits with the phone upright: the upper left. */
    const val BASE_ANGLE = 45f
    /** The light never swings further than this from [BASE_ANGLE], so landscape doesn't spin the rim around. */
    const val MAX_SWING = 135f
    private const val G = 9.81f

    /** Light angle in degrees for a gravity reading in the device frame. The light is meant to stay put in the
     * room while the phone turns, so the rim rotates against the phone's roll. Lying flat there is no
     * "sideways", so the tilt fades out below about a third of g in the screen plane instead of jittering.
     */
    fun angle(gx: Float, gy: Float, gz: Float, base: Float = BASE_ANGLE, gain: Float = 1f): Float {
        val planar = hypot(gx, gy)
        val weight = ((planar / G - .25f) / .35f).coerceIn(0f, 1f)
        val roll = Math.toDegrees(atan2(gx.toDouble(), gy.toDouble())).toFloat()
        return (base + roll * weight * gain).coerceIn(base - MAX_SWING, base + MAX_SWING)
    }

    /** How many times the phone's roll is multiplied: a slider position in 0..1 gives 1x to 6x. A natural hand tilt is
     * only 10 to 20 degrees, which at 1x moves a thin rim too little to notice.
     */
    fun gainFor(strength: Float): Float = 1f + 5f * strength.coerceIn(0f, 1f)

    /** Exponential smoothing, so sensor noise doesn't make the highlight shimmer. */
    fun smooth(previous: Float, next: Float, alpha: Float = .3f): Float = previous + (next - previous) * alpha

    /** Whether a new angle is different enough to be worth redrawing every glass surface for. */
    fun worthUpdating(current: Float, next: Float, threshold: Float = 2f): Boolean = abs(next - current) >= threshold
}

/** Follows the phone's tilt with the glass highlight while [enabled] and Home is on screen. Reads gravity (or the
 * accelerometer where there is no gravity sensor) at a low rate and only wakes the drawing when the light has moved
 * a visible amount. It listens only between onStart and onStop, so nothing runs while Home is not visible, and it
 * needs no permission (GrapheneOS's per-app Sensors toggle, if off, simply leaves the light where it is).
 */
@Composable
internal fun TiltHighlight(enabled: Boolean, strength: Float = DEFAULT_STRENGTH) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentStrength = androidx.compose.runtime.rememberUpdatedState(strength)
    // The rim's extra strength follows the slider live; it needs no sensor, so it is set outside the listener.
    androidx.compose.runtime.SideEffect { GlassRim.boost = if (enabled) strength.coerceIn(0f, 1f) else 0f }
    DisposableEffect(enabled, lifecycle) {
        if (!enabled) {
            GlassRim.angle = TiltMath.BASE_ANGLE
            return@DisposableEffect onDispose { }
        }
        GlassRim.samples.intValue = 0
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) return@DisposableEffect onDispose { }
        var smoothed = GlassRim.angle
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                GlassRim.samples.intValue++
                val target = TiltMath.angle(event.values[0], event.values[1], event.values[2],
                    gain = TiltMath.gainFor(currentStrength.value))
                smoothed = TiltMath.smooth(smoothed, target)
                if (TiltMath.worthUpdating(GlassRim.angle, smoothed)) GlassRim.angle = smoothed
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        var registered = false
        fun register() { if (!registered) { registered = manager.registerListener(listener, sensor, SAMPLING_US) } }
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
        onDispose { lifecycle.removeObserver(observer); unregister() }
    }
}

private const val SAMPLING_US = 50_000
internal const val DEFAULT_STRENGTH = .6f
