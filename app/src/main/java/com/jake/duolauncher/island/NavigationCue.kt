package com.jake.duolauncher.island

/**
 * When a turn-by-turn instruction deserves the island's attention. A maps app keeps one ongoing notification and rewrites it as the
 * route goes on (the distance to the turn, the next street, the turn arrow as its large icon). The island compares each version with
 * the last and drops down briefly when something worth a glance changed: the route started, the arrow or the street changed, or the
 * turn came within a few hundred metres. These rules are pure, so they are unit-tested apart from the notification service.
 */
internal object NavigationCue {
    /** How long the island stays dropped down for a cue. */
    const val SECONDS = 4

    /** The least time between two cues, so a chatty app cannot keep the island open (the first sight of a route is exempt). */
    const val MIN_GAP_MS = 6_000L

    /** Distances, in metres, that raise a cue when the turn comes within them. */
    val NEAR_METERS = listOf(200, 50)

    /** What one version of the instruction says: which notification, the street, a fingerprint of the arrow image and the distance. */
    data class Snapshot(val key: String, val street: String?, val arrow: Int?, val meters: Int?)

    enum class Reason { STARTED, TURN, STREET, NEAR }

    private val DISTANCE = Regex("""^\s*(\d+(?:[.,]\d+)?)\s*(km|kilometers?|kilometres?|mi|miles?|m|meters?|metres?|ft|feet|foot|yd|yards?)\b""",
        RegexOption.IGNORE_CASE)

    /** "300 m", "0.5 km", "1,2 km", "1.2 mi", "500 ft" read as metres, or null when the text does not start with a distance. */
    fun parseMeters(text: String?): Int? {
        val m = DISTANCE.find(text.orEmpty()) ?: return null
        val amount = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val factor = when (m.groupValues[2].lowercase()) {
            "km", "kilometer", "kilometers", "kilometre", "kilometres" -> 1000.0
            "mi", "mile", "miles" -> 1609.344
            "ft", "feet", "foot" -> .3048
            "yd", "yard", "yards" -> .9144
            else -> 1.0
        }
        return (amount * factor).toInt()
    }

    /** Why [now] deserves a cue given the [prev] version, or null. [sinceLastCueMs] is the time since the island was last dropped down for one. */
    fun reason(prev: Snapshot?, now: Snapshot, sinceLastCueMs: Long): Reason? {
        if (prev == null || prev.key != now.key) return Reason.STARTED
        if (sinceLastCueMs < MIN_GAP_MS) return null
        if (prev.arrow != null && now.arrow != null && prev.arrow != now.arrow) return Reason.TURN
        if (!prev.street.isNullOrBlank() && !now.street.isNullOrBlank() && prev.street != now.street) return Reason.STREET
        val before = prev.meters; val after = now.meters
        if (before != null && after != null && NEAR_METERS.any { before > it && after <= it }) return Reason.NEAR
        return null
    }
}
