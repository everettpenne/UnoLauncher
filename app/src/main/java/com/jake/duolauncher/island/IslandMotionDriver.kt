package com.jake.duolauncher.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Every spring the island uses, in one place, from its theme: the morph (open and close), the widening for an event, the shrink to a ring,
 * the edges the status bar grid gives it, the pulse, the press, and the capsules' and dots' comings and goings. With Android's animations
 * off, each is a snap.
 */
internal class IslandMotionSpecs(private val theme: IslandTheme, private val animate: Boolean) {
    private fun <T> or(spec: AnimationSpec<T>): AnimationSpec<T> = if (animate) spec else snap()
    fun height(opening: Boolean): AnimationSpec<Float> = or(if (opening) theme.heightSpring() else theme.closeSpring())
    fun width(opening: Boolean): AnimationSpec<Float> = or(if (opening) theme.widthSpring() else theme.closeSpring())
    val event: AnimationSpec<Float> get() = or(theme.spring(.6f, 420f))
    val compact: AnimationSpec<Float> get() = or(theme.spring(.8f, 380f))
    val edge: AnimationSpec<Float> get() = or(theme.spring(.55f, 380f))
    val press: AnimationSpec<Float> get() = or(theme.spring(.4f, 500f))
    val pulseUp: AnimationSpec<Float> get() = or(theme.spring(.45f, 700f))
    val pulseDown: AnimationSpec<Float> get() = or(theme.spring(.4f, 260f))
    // Playful: the capsules pop out on a looser spring, glide to a new place with a little overshoot and squish deeper under a finger.
    val capsuleAppear: AnimationSpec<Float> get() = or(theme.spring(if (theme.playful) .36f else .5f, if (theme.playful) 260f else 240f))
    val capsuleLeave: AnimationSpec<Float> get() = or(tween(240, easing = FastOutSlowInEasing))
    val capsuleGlide: AnimationSpec<Float> get() = or(theme.spring(if (theme.playful) .58f else .75f, 380f))
    val dot: AnimationSpec<Float> get() = or(theme.spring(.7f, 520f))
    val pressCapsule: AnimationSpec<Float> get() = or(androidx.compose.animation.core.spring(if (theme.playful) .42f else .6f, 520f))
}

/** What the island is heading for, all at once: its morph (from the state), and what composition works out (the event's width, the ring, the
 * grid's edges, the press). [screen] changes when the screen it is measured against does; the edges then jump, not spring. */
internal data class MotionTarget(
    val height: Float, val width: Float, val opening: Boolean, val fingerDriven: Boolean,
    val eventWidthDp: Float, val compactness: Float, val edgeLeft: Float, val edgeRight: Float,
    val pressX: Float, val pressY: Float, val screen: Any?, val animate: Boolean,
)

/** One of the island's animated values. */
internal enum class MotionChannel { HEIGHT, WIDTH, EVENT, COMPACT, EDGE_LEFT, EDGE_RIGHT, PRESS_X, PRESS_Y }

/** What to do with a channel when the target changes: leave it running, spring it to the new value, or put it there at once. */
internal enum class MotionAction { KEEP, ANIMATE, SNAP }

/** Which channels move, and how, when the target goes from [previous] to [next]. Pure, so the rules are unit-tested. */
internal object MotionPlan {
    fun of(previous: MotionTarget?, next: MotionTarget): Map<MotionChannel, MotionAction> {
        fun value(t: MotionTarget, c: MotionChannel) = when (c) {
            MotionChannel.HEIGHT -> t.height; MotionChannel.WIDTH -> t.width; MotionChannel.EVENT -> t.eventWidthDp
            MotionChannel.COMPACT -> t.compactness; MotionChannel.EDGE_LEFT -> t.edgeLeft; MotionChannel.EDGE_RIGHT -> t.edgeRight
            MotionChannel.PRESS_X -> t.pressX; MotionChannel.PRESS_Y -> t.pressY
        }
        return MotionChannel.entries.associateWith { c ->
            when {
                previous == null -> MotionAction.SNAP                                   // the first frame is simply where it belongs
                !next.animate -> if (value(previous, c) != value(next, c)) MotionAction.SNAP else MotionAction.KEEP
                // A finger drives the morph: it follows the finger exactly, with no spring behind it.
                next.fingerDriven && (c == MotionChannel.HEIGHT || c == MotionChannel.WIDTH) ->
                    if (value(previous, c) != value(next, c) || !previous.fingerDriven) MotionAction.SNAP else MotionAction.KEEP
                // A new screen (the first real reading at start-up, a rotation): the size-dependent values jump, or the island would fly.
                next.screen != previous.screen && c in SCREEN_DEPENDENT -> MotionAction.SNAP
                // Let go after a pull: the morph springs from where the finger left it, even if the target is where it already was heading.
                previous.fingerDriven && !next.fingerDriven && (c == MotionChannel.HEIGHT || c == MotionChannel.WIDTH) -> MotionAction.ANIMATE
                value(previous, c) != value(next, c) || previous.opening != next.opening && (c == MotionChannel.HEIGHT || c == MotionChannel.WIDTH) -> MotionAction.ANIMATE
                else -> MotionAction.KEEP
            }
        }
    }

    private val SCREEN_DEPENDENT = setOf(MotionChannel.EDGE_LEFT, MotionChannel.EDGE_RIGHT, MotionChannel.EVENT, MotionChannel.COMPACT)
}

/**
 * The island's one motion driver. It owns every value that moves the island's body (how open it is, both ways; how much an event widens it;
 * how far it has shrunk to a ring; the grid's edge corrections; the pulse; the press) and moves each toward the [MotionTarget] with its spring
 * from [IslandMotionSpecs]. A target that changes part-way only restarts the channels it changes, and each restarts from where it is and how fast
 * it is going, so an interruption (a tap mid-morph, an activity ending mid-swap, a pull released) carries on smoothly instead of jumping or
 * starting cold. It runs outside composition: the island reads its values only where it lays out and draws.
 */
internal class IslandMotionDriver(expanded: Boolean) {
    val height = Animatable(if (expanded) 1f else 0f)
    val width = Animatable(if (expanded) 1f else 0f)
    val eventWidthDp = Animatable(0f)
    val compactness = Animatable(0f)
    val edgeLeft = Animatable(0f)
    val edgeRight = Animatable(0f)
    val pressX = Animatable(1f)
    val pressY = Animatable(1f)
    val pulse = Animatable(1f)

    private var last: MotionTarget? = null
    private val jobs = HashMap<MotionChannel, Job>()
    private var pulseJob: Job? = null

    private fun animatable(c: MotionChannel) = when (c) {
        MotionChannel.HEIGHT -> height; MotionChannel.WIDTH -> width; MotionChannel.EVENT -> eventWidthDp; MotionChannel.COMPACT -> compactness
        MotionChannel.EDGE_LEFT -> edgeLeft; MotionChannel.EDGE_RIGHT -> edgeRight; MotionChannel.PRESS_X -> pressX; MotionChannel.PRESS_Y -> pressY
    }

    /** Moves toward [t]. [releaseVelocity] (progress per second, from a finger let go) seeds the height spring once. */
    fun drive(scope: CoroutineScope, t: MotionTarget, specs: IslandMotionSpecs, releaseVelocity: Float = 0f) {
        val plan = MotionPlan.of(last, t)
        last = t
        for ((c, action) in plan) {
            if (action == MotionAction.KEEP) continue
            val anim = animatable(c)
            val target = when (c) {
                MotionChannel.HEIGHT -> t.height; MotionChannel.WIDTH -> t.width; MotionChannel.EVENT -> t.eventWidthDp
                MotionChannel.COMPACT -> t.compactness; MotionChannel.EDGE_LEFT -> t.edgeLeft; MotionChannel.EDGE_RIGHT -> t.edgeRight
                MotionChannel.PRESS_X -> t.pressX; MotionChannel.PRESS_Y -> t.pressY
            }
            jobs[c]?.cancel()
            jobs[c] = scope.launch {
                if (action == MotionAction.SNAP) anim.snapTo(target)
                else {
                    val spec = when (c) {
                        MotionChannel.HEIGHT -> specs.height(t.opening); MotionChannel.WIDTH -> specs.width(t.opening)
                        MotionChannel.EVENT -> specs.event; MotionChannel.COMPACT -> specs.compact
                        MotionChannel.EDGE_LEFT, MotionChannel.EDGE_RIGHT -> specs.edge
                        MotionChannel.PRESS_X, MotionChannel.PRESS_Y -> specs.press
                    }
                    // Carry on from the current speed (Animatable keeps it across an interruption), or the finger's, if it just let go.
                    val v = if (c == MotionChannel.HEIGHT && releaseVelocity != 0f) releaseVelocity else anim.velocity
                    anim.animateTo(target, spec, initialVelocity = v)
                }
            }
        }
    }

    /** A small spring pulse (an event arriving, a capsule melting in): out to [peak] and back to 1. A new one takes over from the last. */
    fun kick(scope: CoroutineScope, peak: Float, specs: IslandMotionSpecs) {
        pulseJob?.cancel()
        pulseJob = scope.launch {
            pulse.animateTo(peak, specs.pulseUp)
            pulse.animateTo(1f, specs.pulseDown)
        }
    }
}

/** What composition works out for the driver, kept as state so the driver's collector sees changes without the island recomposing for it. */
internal class MotionInputs {
    var eventWidthDp by mutableFloatStateOf(0f)
    var compactness by mutableFloatStateOf(0f)
    var edgeLeft by mutableFloatStateOf(0f)
    var edgeRight by mutableFloatStateOf(0f)
    var pressX by mutableFloatStateOf(1f)
    var pressY by mutableFloatStateOf(1f)
    var screen by mutableStateOf<Any?>(null)
}
