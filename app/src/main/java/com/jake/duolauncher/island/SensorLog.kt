package com.jake.duolauncher.island

import android.content.Context

/**
 * An optional, on-phone record of when the camera, the microphone and screen recording were in use, for the person who wants to look back at
 * what happened while they were away. Off unless the person turns it on; nothing in it names an app (Android does not say which app used the
 * camera or microphone), only what was used, when, and for how long. It never leaves the phone and holds the last [CAP] uses.
 */
internal object SensorLog {
    const val CAMERA = "camera"
    const val MIC = "mic"
    const val SCREEN = "screen"
    const val CAP = 60
    /** A use shorter than this is a camera app opening and closing its preview, not something worth listing. */
    const val MIN_MS = 1_500L

    data class Entry(val kind: String, val startMs: Long, val durationMs: Long)

    /** What changed when the set of kinds in use became [active] at [nowMs]: the sessions now open, and the ones that just ended. */
    class Step(val open: Map<String, Long>, val finished: List<Entry>)

    fun step(open: Map<String, Long>, active: Set<String>, nowMs: Long): Step {
        val finished = open.filterKeys { it !in active }.map { (kind, start) -> Entry(kind, start, (nowMs - start).coerceAtLeast(0L)) }
            .filter { it.durationMs >= MIN_MS }
        val next = active.associateWith { open[it] ?: nowMs }
        return Step(next, finished)
    }

    /** [entries] newest first, with [added] put on the front and the list kept to [cap]. */
    fun append(entries: List<Entry>, added: List<Entry>, cap: Int = CAP): List<Entry> = (added.sortedByDescending { it.startMs } + entries).take(cap)

    fun encode(entries: List<Entry>): String = entries.joinToString("\n") { "${it.kind}|${it.startMs}|${it.durationMs}" }

    fun decode(text: String?): List<Entry> = text.orEmpty().lineSequence().mapNotNull { line ->
        val p = line.split('|')
        if (p.size != 3 || p[0] !in setOf(CAMERA, MIC, SCREEN)) return@mapNotNull null
        val start = p[1].toLongOrNull() ?: return@mapNotNull null
        val dur = p[2].toLongOrNull() ?: return@mapNotNull null
        Entry(p[0], start, dur)
    }.toList()

    fun label(kind: String) = when (kind) { CAMERA -> "Camera"; MIC -> "Microphone"; else -> "Screen recording" }

    /** "1 min 20 s", "45 s": how long a use lasted, in the fewest words. */
    fun length(ms: Long): String {
        val s = (ms / 1000L).coerceAtLeast(1L)
        return if (s < 60L) "$s s" else if (s % 60L == 0L) "${s / 60L} min" else "${s / 60L} min ${s % 60L} s"
    }

    private const val PREFS = "sensor_log"
    private const val KEY = "entries"
    private const val ENABLED = "extras"
    private var open: Map<String, Long> = emptyMap()

    fun enabled(context: Context): Boolean = context.getSharedPreferences(ENABLED, Context.MODE_PRIVATE).getBoolean("sensorLog", false)

    fun entries(context: Context): List<Entry> = decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    fun clear(context: Context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply(); open = emptyMap() }

    /** Called whenever what is in use changes. Does nothing (and keeps nothing) unless the person turned the log on. */
    @Synchronized
    fun record(context: Context, camera: Boolean, mic: Boolean, screen: Boolean, nowMs: Long = System.currentTimeMillis()) {
        if (!enabled(context)) { open = emptyMap(); return }
        val active = buildSet { if (camera) add(CAMERA); if (mic) add(MIC); if (screen) add(SCREEN) }
        val step = step(open, active, nowMs)
        open = step.open
        if (step.finished.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY, encode(append(decode(prefs.getString(KEY, null)), step.finished))).apply()
    }
}
