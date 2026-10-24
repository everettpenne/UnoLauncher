package com.jake.duolauncher.island

import android.app.PendingIntent
import android.app.RemoteInput
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** One button on a notification card: what the app calls it, and what to send when it is tapped. A reply carries the input the app asked for. */
/** A button on a card: a notification's own ([intent], with [reply] for a typed answer), or one of Uno's ([run]) on a quick-actions card. */
internal class PeekAction(val title: String, val intent: PendingIntent?, val reply: RemoteInput? = null, val run: (() -> Unit)? = null)

/** What the notification offers, before it is turned into buttons. Kept apart from the Android types so the choice is unit-tested. */
internal data class RawAction(val title: String?, val sendable: Boolean, val freeFormReply: Boolean, val contextual: Boolean = false)

internal object PeekActionRules {
    const val MAX_ACTIONS = 3
    const val MAX_TITLE = 18

    /** The indexes of the actions to show, in order: a reply first (the one people want most), then the others as the app listed them.
     * Actions with no title, nothing to send, a repeated title, or that the system added itself (contextual suggestions) are left out.
     */
    fun pick(actions: List<RawAction>): List<Int> {
        val usable = actions.withIndex().filter { (_, a) -> a.sendable && !a.contextual && !a.title.isNullOrBlank() }
        val seen = HashSet<String>()
        val unique = usable.filter { (_, a) -> seen.add(a.title!!.trim().lowercase()) }
        return (unique.filter { it.value.freeFormReply } + unique.filter { !it.value.freeFormReply }).take(MAX_ACTIONS).map { it.index }
    }

    fun label(title: String): String = title.trim().take(MAX_TITLE)
}

/** Bluetooth devices connecting and disconnecting, as island events. */
internal object BluetoothEvents {
    /** Android's major device class for audio and video (headsets, speakers). Those already have their own alert from the audio-device callback. */
    const val MAJOR_AUDIO_VIDEO = 0x0400

    fun label(name: String?): String = name?.trim()?.takeIf { it.isNotEmpty() }?.take(24) ?: "Bluetooth device"

    fun event(connected: Boolean, name: String?) =
        IslandEvent(if (connected) "${label(name)} connected" else "${label(name)} disconnected", IslandSymbol.BLUETOOTH)

    /** Whether to announce a device of [majorClass]: audio devices only when the audio alerts are off, since they would be said twice. */
    fun shouldShow(majorClass: Int, audioAlertsOn: Boolean): Boolean = majorClass != MAJOR_AUDIO_VIDEO || !audioAlertsOn

    /** The same device is reported once per profile (and again on a flaky link); one flash per device in a few seconds is enough. */
    fun isRepeat(lastShownAt: Long?, nowMs: Long, windowMs: Long = 4_000L): Boolean = lastShownAt != null && nowMs - lastShownAt in 0 until windowMs
}

/** The privacy row on the open island: what is in use, for how long, and, when the phone's usage access allows, which app was in front. */
internal object PrivacyLine {
    class Line(val title: String, val subtitle: String)

    fun describe(camera: Boolean, mic: Boolean, recording: Boolean, sinceMs: Long, nowMs: Long, inFront: String?): Line? {
        val parts = buildList { if (camera) add("Camera"); if (mic) add("Microphone") }
        val title = when {
            parts.isNotEmpty() && recording -> parts.joinToString(" and ") + " in use, screen recorded"
            parts.isNotEmpty() -> parts.joinToString(" and ") + " in use"
            recording -> "Screen is being recorded"
            else -> return null
        }
        val duration = if (sinceMs > 0L && nowMs >= sinceMs) "For ${MediaTimes.clock(nowMs - sinceMs)}" else null
        // Android does not say which app is using the camera or microphone, so the app named is only the one that was in front when it started.
        val front = inFront?.let { "Started with $it in front" }
        return Line(title, listOfNotNull(duration, front).joinToString(" · "))
    }
}

/** The app in front, from the system's usage events (only when the person has granted usage access, which Uno never asks for here). */
internal object ForegroundApp {
    const val RESUMED = 1
    const val PAUSED = 2

    /** [events] are (package, type) pairs, oldest first. The app in front is the last one resumed that has not been paused since; null when the
     * last thing that happened was a pause (nothing is in front but Home) or the app is [own].
     */
    fun current(events: List<Pair<String, Int>>, own: String): String? {
        var front: String? = null
        for ((pkg, type) in events) {
            if (type == RESUMED) front = pkg else if (type == PAUSED && pkg == front) front = null
        }
        return front?.takeIf { it != own }
    }
}

/** The bridge of a gooey blob: where two circles that are close enough join. Drawn between the island and a bubble leaving it, it stretches
 * and pinches off as they part, which is the look of liquid separating rather than a circle sliding out from behind a pill.
 */
internal object Metaball {
    class Neck(val p1a: Pt, val h1: Pt, val h2: Pt, val p2a: Pt, val p2b: Pt, val h3: Pt, val h4: Pt, val p1b: Pt)
    data class Pt(val x: Float, val y: Float)

    private fun at(c: Pt, a: Float, r: Float) = Pt(c.x + cos(a) * r, c.y + sin(a) * r)

    /** The neck between circle 1 ([c1], [r1]) and circle 2 ([c2], [r2]), or null when they are too far apart (beyond [reach] times circle 2's
     * radius past touching), one is inside the other, or either is empty. [v] is how far the neck spreads, [handle] how round it is.
     */
    fun neck(c1: Pt, r1: Float, c2: Pt, r2: Float, reach: Float = 1.5f, v: Float = .5f, handle: Float = 2.4f): Neck? {
        if (r1 <= 0f || r2 <= 0f) return null
        val d = hypot(c2.x - c1.x, c2.y - c1.y)
        if (d > r1 + r2 + r2 * reach.coerceAtLeast(0f) || d <= abs(r1 - r2)) return null
        val u1: Float; val u2: Float
        if (d < r1 + r2) {
            u1 = acos(((r1 * r1 + d * d - r2 * r2) / (2f * r1 * d)).coerceIn(-1f, 1f))
            u2 = acos(((r2 * r2 + d * d - r1 * r1) / (2f * r2 * d)).coerceIn(-1f, 1f))
        } else { u1 = 0f; u2 = 0f }
        val between = atan2(c2.y - c1.y, c2.x - c1.x)
        val spread = acos(((r1 - r2) / d).coerceIn(-1f, 1f))
        val pi = Math.PI.toFloat(); val half = pi / 2f
        val a1 = between + u1 + (spread - u1) * v
        val a2 = between - u1 - (spread - u1) * v
        val a3 = between + pi - u2 - (pi - u2 - spread) * v
        val a4 = between - pi + u2 + (pi - u2 - spread) * v
        val p1a = at(c1, a1, r1); val p1b = at(c1, a2, r1)
        val p2a = at(c2, a3, r2); val p2b = at(c2, a4, r2)
        val total = r1 + r2
        val base = min(v * handle, hypot(p1a.x - p2a.x, p1a.y - p2a.y) / total)
        val d2 = base * min(1f, d * 2f / total)
        val k1 = r1 * d2; val k2 = r2 * d2
        return Neck(p1a, at(p1a, a1 - half, k1), at(p2a, a3 + half, k2), p2a, p2b, at(p2b, a4 - half, k2), at(p1b, a2 + half, k1), p1b)
    }
}

/** The app that was in front, read from the system's usage events. Only when the person has granted usage access; Uno does not ask for it here. */
internal object ForegroundNow {
    fun label(context: android.content.Context): String? {
        if (!com.jake.duolauncher.widgets.hasUsageAccess(context)) return null
        val usage = context.getSystemService(android.app.usage.UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val events = usage.queryEvents(now - 3 * 60_000L, now) ?: return null
        val seen = ArrayList<Pair<String, Int>>()
        val event = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> seen += event.packageName to ForegroundApp.RESUMED
                android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED -> seen += event.packageName to ForegroundApp.PAUSED
            }
        }
        val pkg = ForegroundApp.current(seen, context.packageName) ?: return null
        val pm = context.packageManager
        return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
    }
}

/** How fast the island's morph is moving, smoothed over a few frames, so a quick opening can stretch it a little and a quick closing can squash it.
 * A gap in the frames (the animation stopped and started again) forgets the old speed instead of carrying it into the next morph.
 */
internal class MorphVelocity {
    private var lastValue = 0f
    private var lastNanos = 0L
    private var speed = 0f

    /** The morph's speed, in progress per second, for a frame at [nowNanos] where the progress is [value]. [settledAt] pins it to zero once there. */
    fun update(value: Float, nowNanos: Long, settledAt: Float): Float {
        if (kotlin.math.abs(value - settledAt) < .002f) { speed = 0f; lastValue = value; lastNanos = nowNanos; return 0f }
        val dt = (nowNanos - lastNanos) / 1e9f
        if (lastNanos == 0L || dt > .1f) speed = 0f
        else if (dt >= .001f) speed += ((value - lastValue) / dt - speed) * .4f
        else return speed
        lastValue = value; lastNanos = nowNanos
        return speed
    }

    companion object {
        /** The stretch for a [speed]: positive stretches it taller (and thinner), negative squashes it. [gain] scales it to the island's theme. */
        fun stretch(speed: Float, gain: Float): Float = (speed * .045f * gain).coerceIn(-.06f, .06f)
    }
}

/** A notification that has just arrived stays one tap away for a little while after its card has gone. */
internal object RecentNotice {
    const val WINDOW_MS = 30_000L
    fun live(arrivedAt: Long, nowMs: Long): Boolean = arrivedAt > 0L && nowMs - arrivedAt in 0 until WINDOW_MS
    fun secondsLeft(arrivedAt: Long, nowMs: Long): Int = if (live(arrivedAt, nowMs)) (((WINDOW_MS - (nowMs - arrivedAt)) + 999L) / 1000L).toInt() else 0
}

/** Navigation instructions are read at a glance, on a dashboard, so they are set far larger than the island's other text. */
internal object NavigationText {
    const val ARROW_DP = 72f
    const val DISTANCE_SP = 34
    const val INSTRUCTION_SP = 20
    /** On the pill beside the camera: the arrow and the distance. */
    const val PILL_ARROW_DP = 24f
    const val PILL_SP = 15
    /** The open island's navigation card (arrow beside distance and instruction), and what a turn's card adds to the panel. */
    const val CARD_ROW_DP = 112f
    const val TURN_CARD_EXTRA_DP = 20f
    /** The arrow is read from the notification at this size (it used to be 96 px, which blurs when drawn at 72 dp). */
    const val ARROW_PX = 224
}
