package com.jake.duolauncher.island

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

/** One hand adjustment of an island, in dp. [dw] is added to its automatic width. [dx] moves the main island sideways, or, for a capsule,
 * is added to its gap from the island, outward. [dy] moves it down. All zero is the automatic layout.
 */
internal data class DebugGroup(val dw: Float = 0f, val dx: Float = 0f, val dy: Float = 0f) {
    val isDefault: Boolean get() = dw == 0f && dx == 0f && dy == 0f
    fun clamped(): DebugGroup = DebugGroup(dw.coerceIn(DW_RANGE), dx.coerceIn(DX_RANGE), dy.coerceIn(DY_RANGE))
    companion object {
        val DW_RANGE = -40f..120f
        val DX_RANGE = -60f..120f
        val DY_RANGE = -40f..40f
    }
}

/** The layout the island ships with, in the same terms as a hand adjustment (see [DebugGroup]), found with the debug page. Hand adjustments are added on top. */
internal object IslandLayoutDefaults {
    /** The main island at rest: a little wider than the automatic width. */
    val MAIN = DebugGroup(dw = 15f)
    /** While the island shows an app's icon and name (a launch or a notification): it springs wider and a touch to the left, then returns to [MAIN]. */
    val MAIN_SHOWING_APP = DebugGroup(dw = 62f, dx = -3f)
    val LEFT = DebugGroup(dw = 10f, dx = -2f)
    val RIGHT = DebugGroup(dw = 10f, dx = -2f)
    val EXTRA = DebugGroup(dw = 10f, dx = -2f)
}

/** The islands you can adjust by hand. */
internal enum class DebugTarget(val label: String, val key: String) {
    PILL("Main island", "pill"), LEFT("Left island", "left"), RIGHT("Right island", "right"), EXTRA("Extra islands (4th and later)", "extra"),
}

/**
 * Debug controls for the island's layout: hand-set width and position for the main island and each capsule, and dummy live activities forced
 * on so they can be seen without the real thing running. The numbers are kept (in their own preferences file) and are meant to be read out as a
 * default setting; see [summary]. Nothing here changes behaviour while every value is zero and nothing is forced.
 */
internal object IslandDebug {
    var pill by mutableStateOf(DebugGroup()); private set
    var left by mutableStateOf(DebugGroup()); private set
    var right by mutableStateOf(DebugGroup()); private set
    var extra by mutableStateOf(DebugGroup()); private set
    /** The activities (and the camera, microphone and recording dots) forced on with dummy content. */
    var forced by mutableStateOf(emptySet<LiveKind>()); private set

    private var prefs: android.content.SharedPreferences? = null

    /** Loads the saved values the first time it is called; later calls do nothing. Only a debuggable build has any: in a release build every
     * value stays at its default and nothing is forced, whatever an old preferences file says. */
    fun init(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        val p = app.getSharedPreferences("islandDebug", Context.MODE_PRIVATE)
        prefs = p
        if (app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        DebugTarget.entries.forEach { t ->
            val g = DebugGroup(p.getFloat("${t.key}.dw", 0f), p.getFloat("${t.key}.dx", 0f), p.getFloat("${t.key}.dy", 0f)).clamped()
            assign(t, g)
        }
        forced = p.getString("forced", "").orEmpty().split(',').mapNotNull { n -> LiveKind.entries.firstOrNull { it.name == n } }.toSet()
    }

    /** What [target] is laid out with: its shipped default plus the hand adjustment. */
    fun effective(target: DebugTarget): DebugGroup {
        val base = when (target) { DebugTarget.PILL -> IslandLayoutDefaults.MAIN; DebugTarget.LEFT -> IslandLayoutDefaults.LEFT
            DebugTarget.RIGHT -> IslandLayoutDefaults.RIGHT; DebugTarget.EXTRA -> IslandLayoutDefaults.EXTRA }
        val g = group(target)
        return DebugGroup(base.dw + g.dw, base.dx + g.dx, base.dy + g.dy)
    }

    fun group(target: DebugTarget): DebugGroup = when (target) { DebugTarget.PILL -> pill; DebugTarget.LEFT -> left; DebugTarget.RIGHT -> right; DebugTarget.EXTRA -> extra }

    private fun assign(target: DebugTarget, g: DebugGroup) {
        when (target) { DebugTarget.PILL -> pill = g; DebugTarget.LEFT -> left = g; DebugTarget.RIGHT -> right = g; DebugTarget.EXTRA -> extra = g }
    }

    fun set(target: DebugTarget, g: DebugGroup) {
        val c = g.clamped()
        assign(target, c)
        prefs?.edit()?.putFloat("${target.key}.dw", c.dw)?.putFloat("${target.key}.dx", c.dx)?.putFloat("${target.key}.dy", c.dy)?.apply()
    }

    fun force(kind: LiveKind, on: Boolean) {
        forced = if (on) forced + kind else forced - kind
        prefs?.edit()?.putString("forced", forced.joinToString(",") { it.name })?.apply()
    }

    fun reset() {
        DebugTarget.entries.forEach { set(it, DebugGroup()) }
        forced = emptySet(); prefs?.edit()?.putString("forced", "")?.apply()
    }

    /** How far, in dp, any debug value can move something beyond its automatic place: the overlay window is made this much larger all round. */
    fun slackDp(): Float = DebugTarget.entries.sumOf { (abs(effective(it).dw) + abs(effective(it).dx) + abs(effective(it).dy)).toDouble() }.toFloat()
        .coerceAtMost(300f) + abs(IslandLayoutDefaults.MAIN_SHOWING_APP.dw) / 2f + 16f

    /** The values as one line to read out: for each island its width, x and y adjustments in dp, then what is forced on. */
    fun summary(): String = summaryOf(pill, left, right, extra, forced)

    internal fun summaryOf(pill: DebugGroup, left: DebugGroup, right: DebugGroup, extra: DebugGroup, forced: Set<LiveKind>): String {
        fun g(name: String, v: DebugGroup) = "$name w${num(v.dw)} x${num(v.dx)} y${num(v.dy)}"
        return listOf(g("main", pill), g("left", left), g("right", right), g("extra", extra),
            "forced " + (if (forced.isEmpty()) "none" else forced.sortedBy { it.ordinal }.joinToString(",") { it.name.lowercase() })).joinToString(" | ")
    }

    private fun num(v: Float): String = if (v == v.toInt().toFloat()) "%+d".format(v.toInt()) else "%+.1f".format(v)

    /** A dummy Live Update, as if a ride-share app had posted it. */
    fun dummyUpdate(nowMs: Long): LiveUpdate = LiveUpdate("debug", "debug.dummy", "Debug", "Debug live update", null, "12 min", 40, 100, false, null, false, null,
        0xFF1A73E8.toInt(), nowMs, null)
}
