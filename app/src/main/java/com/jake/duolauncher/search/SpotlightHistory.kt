package com.jake.duolauncher.search

import android.content.Context

/**
 * Which apps are opened from Spotlight, and how often. Spotlight uses it to float the apps you open most to the top of
 * equally good matches and to suggest them before anything is typed. It lives only on the device, in the launcher's own
 * preferences, and one entry per app is all it keeps ("count:lastLaunchMs").
 */
internal class SpotlightHistory(context: Context) {
    private val prefs = context.getSharedPreferences("spotlight_history", Context.MODE_PRIVATE)

    fun record(appId: String, nowMs: Long = System.currentTimeMillis()) {
        val (count, _) = read(appId)
        prefs.edit().putString(appId, "${count + 1}:$nowMs").apply()
    }

    /** Frecency per app id, taken once when Spotlight opens. */
    fun scores(nowMs: Long = System.currentTimeMillis()): Map<String, Double> =
        prefs.all.mapNotNull { (id, _) -> read(id).let { (count, last) -> id to SpotlightRank.frecency(count, last, nowMs) } }.toMap()

    private fun read(id: String): Pair<Int, Long> {
        val parts = prefs.getString(id, null)?.split(':') ?: return 0 to 0L
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) to (parts.getOrNull(1)?.toLongOrNull() ?: 0L)
    }
}
