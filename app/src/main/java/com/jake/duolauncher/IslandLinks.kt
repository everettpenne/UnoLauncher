package com.jake.duolauncher

/** Which apps may use the island, as in HyperOS's per-app control of its Super Island: every app is linked unless it has been
 * switched off. The switched-off list is kept as package names. Applies to Live Updates and new-notification peeks.
 */
internal object IslandLinks {
    fun parse(raw: String?): Set<String> = raw.orEmpty().split(',').map(String::trim).filter(String::isNotEmpty).toSet()
    fun serialize(muted: Set<String>): String = muted.sorted().joinToString(",")
    fun allowed(packageName: String, muted: Set<String>): Boolean = packageName !in muted
    fun toggle(muted: Set<String>, packageName: String): Set<String> = if (packageName in muted) muted - packageName else muted + packageName
}

/** Where a playing track is, for the seek bar on the island's music card. Works from what the media session reports (a position
 * stamped with the time it was true and a speed), so it needs no timer of its own.
 */
internal object MediaProgress {
    /** The position now, in ms, or null when the session gives no usable position. */
    fun positionNow(position: Long, updatedAt: Long, speed: Float, playing: Boolean, now: Long, duration: Long): Long? {
        if (position < 0 || duration <= 0) return null
        val elapsed = if (playing) ((now - updatedAt).coerceAtLeast(0L) * speed).toLong() else 0L
        return (position + elapsed).coerceIn(0L, duration)
    }

    /** 0..1 along the track, or null. */
    fun fraction(position: Long, updatedAt: Long, speed: Float, playing: Boolean, now: Long, duration: Long): Float? =
        positionNow(position, updatedAt, speed, playing, now, duration)?.let { it.toFloat() / duration }

    /** The position, in ms, that a tap or drag at [x] of [width] pixels asks for. */
    fun seekTarget(x: Float, width: Float, duration: Long): Long =
        if (width <= 0f || duration <= 0) 0L else ((x / width).coerceIn(0f, 1f) * duration).toLong()
}
