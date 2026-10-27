package com.jake.duolauncher

import android.content.SharedPreferences

/**
 * Notices that notification access has gone. An update (or Android re-applying a restricted setting) can switch it off without telling anyone, and the
 * island then quietly stops showing titles, artwork, calls and Live Updates. Whenever access is seen on it is remembered; when it is seen off after
 * having been on, that is "lost", and stays lost (so a person who dismisses the notice is asked once, not at every launch) until they turn it back on.
 * A person who never turned it on is never nagged. Pure over a preferences object, so it is unit-tested.
 */
internal object NotificationAccessWatch {
    private const val WAS_ON = "wasOn"
    private const val ACKNOWLEDGED = "acknowledged"

    /** Records [on] and returns whether access has just been found lost (was on, now off, and not yet acknowledged). */
    fun lost(prefs: SharedPreferences, on: Boolean): Boolean {
        if (on) { prefs.edit().putBoolean(WAS_ON, true).putBoolean(ACKNOWLEDGED, false).apply(); return false }
        return prefs.getBoolean(WAS_ON, false) && !prefs.getBoolean(ACKNOWLEDGED, false)
    }

    /** The person has seen the notice (turned it on or declined): do not show it again until access is on and lost once more. */
    fun acknowledge(prefs: SharedPreferences) { prefs.edit().putBoolean(ACKNOWLEDGED, true).apply() }
}
