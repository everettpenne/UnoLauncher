package com.jake.duolauncher

import android.media.AudioManager
import kotlin.math.pow
import kotlin.math.roundToInt

/** A camera as the torch picker sees it. */
internal data class TorchCamera(val id: String, val hasFlash: Boolean, val backFacing: Boolean)

/** What tapping a ringer segment should do. */
internal enum class RingerRequest { SET, NEEDS_DND_ACCESS, ALREADY }

/** Pure rules behind the control panel, kept apart from the system calls so they can be unit-tested. */
internal object ControlLogic {
    const val MIN_BRIGHTNESS = 1
    const val MAX_BRIGHTNESS = 255
    /** Perceived brightness is far from linear in panel output, so the slider bends toward the dark end. */
    private const val BRIGHTNESS_GAMMA = 2.2f

    fun volumeFraction(volume: Int, max: Int): Float =
        if (max <= 0) 0f else (volume.toFloat() / max).coerceIn(0f, 1f)

    fun volumeFor(fraction: Float, max: Int): Int =
        (fraction.coerceIn(0f, 1f) * max.coerceAtLeast(0)).roundToInt()

    /** Slider position (0..1) for a system brightness value. */
    fun brightnessFraction(raw: Int): Float {
        val span = (MAX_BRIGHTNESS - MIN_BRIGHTNESS).toFloat()
        val linear = ((raw - MIN_BRIGHTNESS) / span).coerceIn(0f, 1f)
        return linear.pow(1f / BRIGHTNESS_GAMMA)
    }

    /** System brightness for a slider position. Never 0: on many panels that is fully dark and hard to undo. */
    fun brightnessFor(fraction: Float): Int {
        val linear = fraction.coerceIn(0f, 1f).pow(BRIGHTNESS_GAMMA)
        return (MIN_BRIGHTNESS + linear * (MAX_BRIGHTNESS - MIN_BRIGHTNESS)).roundToInt()
            .coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS)
    }

    /** Silent also toggles Do Not Disturb, which Android only lets an app do with Do Not Disturb access. */
    fun ringerRequest(current: Int, target: Int, hasDndAccess: Boolean): RingerRequest = when {
        current == target -> RingerRequest.ALREADY
        target == AudioManager.RINGER_MODE_SILENT && !hasDndAccess -> RingerRequest.NEEDS_DND_ACCESS
        else -> RingerRequest.SET
    }

    /** The camera whose flash the torch tile drives: a back camera with a flash, else any with one. */
    fun pickTorchCamera(cameras: List<TorchCamera>): String? =
        (cameras.firstOrNull { it.hasFlash && it.backFacing } ?: cameras.firstOrNull { it.hasFlash })?.id
}
