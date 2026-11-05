package com.jake.duolauncher.island

import android.app.PendingIntent
import android.app.RemoteInput

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

/**
 * Apple's split, as rubber: a second activity is pulled out of the pill's own end. The end stretches out into a tip on a fat join that narrows to a
 * waist as the pull goes on ([waist]) and snaps at [PINCH] of the way; the tip then pops off on its own into its place, a little big before it
 * settles ([size]), and the pill recoils ([bodyPull]). Going back the same plays in reverse. [t] is how far the capsule has gone (0 inside the
 * pill's end, 1 at its place; a spring may carry it a little past). Pure, so it is unit-tested.
 */
internal object LiquidSplit {
    /** Where along its travel the join snaps. */
    const val PINCH = .72f

    private fun smooth(u: Float): Float { val c = u.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }

    /** How taut the pull is: 0 at rest or once snapped, 1 at its most stretched, just before it snaps. */
    fun tension(t: Float): Float = if (!(t > 0f) || t >= PINCH) 0f else smooth(t / PINCH)

    /**
     * Where the join leaves the pill's end, as an angle (radians) from the line of the pull: wide at first, so the end itself is drawn out on a fat
     * join with no crease, then closing in quickly over the last of the pull, a waist that narrows to a thread. Null once it has snapped.
     */
    fun waist(t: Float): Float? {
        if (t >= PINCH) return null
        val u = t.coerceAtLeast(0f) / PINCH
        val deg = if (u < .7f) 78f - 20f * smooth(u / .7f) else 58f - 50f * smooth((u - .7f) / .3f)
        return Math.toRadians(deg.toDouble()).toFloat()
    }

    /**
     * The join's outline between the pill's end (a circle at [c1x], [c1y], radius [r1]) and the tip (a circle at [c2x], [c2y], radius [r2]), the
     * tip toward [dir] (1 right, -1 left), leaving the end at [angle] (see [waist]). Each flank is one cubic tangent to both circles, so the end, the
     * waist and the tip are one smooth shape. It works however much the two circles overlap, as they do while the end first stretches. The front of
     * the tip and the inside of the end are closed with straight lines: the pill and the tip are drawn over them.
     */
    fun join(c1x: Float, c1y: Float, r1: Float, c2x: Float, c2y: Float, r2: Float, angle: Float, dir: Float,
        sink: com.jake.duolauncher.appearance.PathSink) {
        val a = angle; val b = a * .9f + .15f       // the tip takes the join a little further round than the end does
        val sa = kotlin.math.sin(a); val ca = kotlin.math.cos(a); val sb = kotlin.math.sin(b); val cb = kotlin.math.cos(b)
        val px = c1x + dir * r1 * ca; val py = r1 * sa                        // y as distance from the axis; the other flank is its mirror
        val qx = c2x - dir * r2 * cb; val qy = r2 * sb
        val h = kotlin.math.hypot(qx - px, (c2y - qy) - (c1y - py)).coerceAtLeast(.001f) * .45f
        fun flank(side: Float, from: Boolean) {
            // Upper flank (side -1) and lower (side 1): leaving the end along its tangent, toward the axis; meeting the tip along its own, away.
            val p1x = px + dir * sa * h; val p1y = c1y + side * (py - ca * h)
            val p2x = qx - dir * sb * h; val p2y = c2y + side * (qy - cb * h)
            if (from) sink.cubicTo(p1x, p1y, p2x, p2y, qx, c2y + side * qy)
            else sink.cubicTo(p2x, p2y, p1x, p1y, px, c1y + side * py)
        }
        sink.moveTo(px, c1y - py)
        flank(-1f, true)
        sink.lineTo(qx, c2y + qy)
        flank(1f, false)
        sink.close()
    }

    /** Where the tip has got to between the pill's end (0) and its place (1): drawn steadily out of the end to [HELD] of the way by the snap, then
     *  popping the rest. Past 1 (a spring's overshoot) it follows [t]. */
    fun travel(t: Float): Float = when {
        !(t > 0f) -> 0f
        t < PINCH -> HELD * Math.pow((t / PINCH).toDouble(), 1.2).toFloat()
        t <= 1f -> HELD + (1f - HELD) * (t - PINCH) / (1f - PINCH)
        else -> t
    }
    /** d[travel]/d[t], to turn the animation's speed into the tip's own. */
    fun travelRate(t: Float): Float = when {
        !(t > 0f) -> 0f
        t < PINCH -> HELD * 1.2f * Math.pow((t / PINCH).toDouble(), .2).toFloat() / PINCH
        t <= 1f -> (1f - HELD) / (1f - PINCH)
        else -> 1f
    }
    private const val HELD = .62f

    /** The tip's size as a share of the capsule's: most of it from the start (it is the pill's own end being drawn out), whole at its place, and a
     *  touch big on the spring's overshoot, so it pops. */
    fun size(t: Float): Float = when {
        !(t > 0f) -> .7f
        t < PINCH -> .7f + .18f * t / PINCH
        else -> (.88f + .12f * (t - PINCH) / (1f - PINCH)).coerceAtMost(1.15f)
    }

    /**
     * The tip's stretch along its path ([first]) and across it ([second]). While held it stays round, so the join meets it smoothly; once it pops
     * it is drawn out by its own [speed] (its [travel] per second, either way), keeping about its area, so it is longest while fastest and comes
     * round as it slows into its place, the spring's settle a wobble.
     */
    fun stretch(t: Float, speed: Float = 0f): Pair<Float, Float> {
        if (t < PINCH) return 1f to 1f
        val along = 1f + (kotlin.math.abs(speed) * .045f).coerceAtMost(.22f)
        return along to Math.pow(along.toDouble(), -.7).toFloat()
    }

    /** How the pill is pulled by the capsules leaving it: its [left] and [right] ends move out by these shares of its width, and it is [thin]
     *  (its height's scale) while pulled, as rubber thins where it is stretched. */
    data class BodyPull(val left: Float, val right: Float, val thin: Float) {
        val stretch get() = 1f + left + right
    }

    /** [BodyPull] for pulls of [onLeft] and [onRight] (each capsule's spring-followed tension, summed per side). The spring overshoots below zero
     *  after the snap, so an end recoils a little past its rest (and the pill swells a touch) before it settles. */
    fun bodyPull(onLeft: Float, onRight: Float): BodyPull {
        fun end(g: Float) = (BODY_REACH * g).coerceIn(-BODY_REACH * .4f, BODY_REACH * 1.3f)
        return BodyPull(end(onLeft), end(onRight), 1f - BODY_THIN * (onLeft + onRight).coerceIn(-.5f, 1.2f))
    }
    /** At full tension a pulled end reaches out by this share of the pill's width (about 5 dp on a Pixel 10a's idle pill), and it thins by [BODY_THIN]. */
    private const val BODY_REACH = .05f
    private const val BODY_THIN = .08f

    /** How much of the capsule's symbol shows: none while it is the pill's end being pulled, all once it has popped into its place. */
    fun glyphAlpha(t: Float): Float = ((t - .75f) / .2f).coerceIn(0f, 1f)
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
