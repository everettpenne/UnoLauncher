package com.jake.duolauncher

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.HandlerThread
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/** What an interaction should feel and sound like. One place decides, so a single switch quiets everything. */
internal enum class Cue(val haptic: HapticFeedbackType?, val sound: Int?) {
    /** A light tick: sliders, rails, small controls. */
    TICK(HapticFeedbackType.TextHandleMove, null),
    /** A page settled. */
    PAGE(HapticFeedbackType.TextHandleMove, AudioManager.FX_KEY_CLICK),
    /** Something opened or closed: the control panel, the island. */
    OPEN(HapticFeedbackType.TextHandleMove, AudioManager.FX_KEYPRESS_STANDARD),
    /** A mode flipped: Focus, a switch. */
    TOGGLE(HapticFeedbackType.ContextClick, AudioManager.FX_KEYPRESS_RETURN),
    /** An app is launching. */
    LAUNCH(HapticFeedbackType.ContextClick, null),
    /** A finished timer, a confirmation. */
    CONFIRM(HapticFeedbackType.Confirm, AudioManager.FX_KEYPRESS_RETURN),
}

/** Haptics are on by default and sounds are off. Sounds are Android's own touch sound effects, played through the
 * audio service, so there are no bundled audio files, they follow the system "Touch sounds" setting and volume, and
 * they are silent in silent mode. The audio call happens on its own thread (a binder call, never on the UI thread).
 */
internal object UnoFeedback {
    @Volatile var hapticsOn = true
    @Volatile var soundsOn = false
    private var worker: Handler? = null
    private var audio: AudioManager? = null

    fun configure(context: Context, haptics: Boolean, sounds: Boolean) {
        hapticsOn = haptics; soundsOn = sounds
        if (sounds && worker == null) {
            audio = context.applicationContext.getSystemService(AudioManager::class.java)
            worker = Handler(HandlerThread("uno-feedback").apply { start() }.looper)
        }
    }

    /** Whether [cue] should produce a haptic / a sound with these switches. Pure, for testing. */
    fun plays(cue: Cue, haptics: Boolean, sounds: Boolean): Pair<Boolean, Boolean> =
        (haptics && cue.haptic != null) to (sounds && cue.sound != null)

    /** Same cue from non-Compose code, using a View for the haptic. */
    fun play(cue: Cue, view: android.view.View) {
        val (feels, sounds) = plays(cue, hapticsOn, soundsOn)
        if (feels) view.performHapticFeedback(when (cue) {
            Cue.TICK, Cue.PAGE, Cue.OPEN -> android.view.HapticFeedbackConstants.CLOCK_TICK
            Cue.TOGGLE, Cue.LAUNCH -> android.view.HapticFeedbackConstants.CONTEXT_CLICK
            Cue.CONFIRM -> android.view.HapticFeedbackConstants.CONFIRM
        })
        if (sounds) cue.sound?.let { fx -> worker?.post { runCatching { audio?.playSoundEffect(fx, .6f) } } }
    }

    fun play(cue: Cue, haptic: HapticFeedback?) {
        val (feels, sounds) = plays(cue, hapticsOn, soundsOn)
        if (feels && haptic != null) cue.haptic?.let { haptic.performHapticFeedback(it) }
        if (sounds) cue.sound?.let { fx -> worker?.post { runCatching { audio?.playSoundEffect(fx, .6f) } } }
    }
}
