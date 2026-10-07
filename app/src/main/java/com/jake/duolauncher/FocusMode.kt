package com.jake.duolauncher

import android.content.Context
import android.media.AudioManager

/** Focus's optional ringer change. Turning Focus on sets the ringer to vibrate (only if it was ringing,
 * and without touching Do Not Disturb, so no special access is needed); turning it off puts back what
 * was there, but only if it is still on vibrate, so a ringer the user changed in the meantime is left alone.
 */
internal object FocusMode {
    private const val PREV = "focusPrevRinger"

    fun applyRinger(context: Context, on: Boolean, vibrate: Boolean) {
        if (!vibrate && on) return
        val app = context.applicationContext
        // AudioManager calls are binder round trips; never on the main thread.
        Thread {
            val audio = app.getSystemService(AudioManager::class.java) ?: return@Thread
            val prefs = app.getSharedPreferences("extras", Context.MODE_PRIVATE)
            runCatching {
                if (on) {
                    if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                        prefs.edit().putInt(PREV, AudioManager.RINGER_MODE_NORMAL).apply()
                        audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    }
                } else if (prefs.contains(PREV)) {
                    val previous = prefs.getInt(PREV, AudioManager.RINGER_MODE_NORMAL)
                    prefs.edit().remove(PREV).apply()
                    if (audio.ringerMode == AudioManager.RINGER_MODE_VIBRATE) audio.ringerMode = previous
                }
            }
        }.start()
    }
}
