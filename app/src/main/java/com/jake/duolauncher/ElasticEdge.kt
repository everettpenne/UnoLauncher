package com.jake.duolauncher

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity

/**
 * iOS-style rubber-banding at the ends of a scrollable: the content resists in proportion to how
 * far it is pulled past an edge, with diminishing returns, and springs back on release.
 *
 * The curve is the classic iOS one: resisted = (1 - 1/((d*c/dimension) + 1)) * dimension, where
 * [RESISTANCE] (c) is 0.55. As the finger keeps pulling, the visible offset approaches `dimension`
 * (never exceeds it), so a harder pull meets proportionally more resistance instead of running away.
 *
 * A surface opts in with one line:
 *
 *     LazyColumn(
 *         Modifier.elasticVerticalEdges(state, atStart = { !listState.canScrollBackward },
 *             atEnd = { !listState.canScrollForward })
 *     ) { ... }
 *
 * The connection consumes the overscroll before Android's own stretch/glow edge effect sees it, so
 * this is the only rubber band shown. With Android's animations switched off the overscroll snaps
 * straight back instead of springing.
 */
internal object ElasticEdgeMath {
    /** iOS's rubber-band constant: how strongly a pull is resisted. 1 is linear, smaller is more resistance. */
    const val RESISTANCE = 0.55f

    /** The spring that returns the edge to rest. A touch under-damped so the edge settles with a small
     * iOS-like rebound rather than a dead stop. */
    const val DAMPING_RATIO = 0.65f
    const val STIFFNESS = 350f

    /**
     * The resisted offset for a total [distance] pulled past an edge, in the same unit as [dimension]
     * (the scrollable's viewport size). Sign follows [distance]; magnitude is capped at [dimension].
     */
    fun resist(distance: Float, dimension: Float): Float {
        if (dimension <= 0f) return 0f
        val d = kotlin.math.abs(distance)
        val resisted = (1f - 1f / (d * RESISTANCE / dimension + 1f)) * dimension
        return resisted * kotlin.math.sign(distance)
    }

    /**
     * The un-resisted distance that produces [offset]: the inverse of [resist]. A drag that interrupts the
     * spring-back re-anchors on this, so the curve continues from the visible offset rather than restarting
     * from zero. The magnitude is capped just under [dimension] (the curve's asymptote), where the inverse
     * runs away.
     */
    fun unresist(offset: Float, dimension: Float): Float {
        if (dimension <= 0f) return 0f
        val magnitude = kotlin.math.abs(offset).coerceIn(0f, dimension * .999f)
        val distance = dimension / RESISTANCE * (1f / (1f - magnitude / dimension) - 1f)
        return distance * kotlin.math.sign(offset)
    }
}

/** Holds the live rubber-band offset (pixels; positive = pulled past the top, negative = past the bottom). */
@Stable
internal class ElasticOverscrollState internal constructor(
    private val snapOnRelease: () -> Boolean,
) {
    /** The resisted offset in pixels, ready to be applied as a `translationY`. */
    val offset: Float get() = offsetState.floatValue

    private val offsetState = mutableFloatStateOf(0f)

    /** The total un-resisted drag distance accumulated while over an edge; feeds the diminishing-returns curve. */
    private var raw = 0f

    /** Applies [delta] (pixels, sign-carrying) to the rubber band and returns how much of it was resisted. */
    internal fun applyDrag(delta: Float, dimension: Float): Float {
        // Re-anchor from the visible offset, not the accumulated raw distance. If the finger lands again while the
        // spring-back is still running, the previous release() reset raw to zero while the offset was still part-way
        // back, so `raw += delta` alone would snap the edge to the small resisted value of a fresh pull. Continuing
        // from the current offset keeps the curve (and the finger's grip) continuous.
        raw = ElasticEdgeMath.unresist(offsetState.floatValue, dimension) + delta
        val next = ElasticEdgeMath.resist(raw, dimension)
        val consumed = next - offsetState.floatValue
        offsetState.floatValue = next
        return consumed
    }

    /** Springs (or snaps, with animations off) the edge back to rest and forgets the accumulated drag. */
    internal suspend fun release() {
        raw = 0f
        val from = offsetState.floatValue
        if (snapOnRelease() || from == 0f) { offsetState.floatValue = 0f; return }
        animate(from, 0f, animationSpec = spring(dampingRatio = ElasticEdgeMath.DAMPING_RATIO, stiffness = ElasticEdgeMath.STIFFNESS)) { value, _ ->
            offsetState.floatValue = value
        }
    }
}

@Composable
internal fun rememberElasticOverscrollState(animationsOn: Boolean = true): ElasticOverscrollState {
    val snap = rememberUpdatedState(!animationsOn)
    return remember { ElasticOverscrollState({ snap.value }) }
}

/** A vertical scrollable's own height, used as the curve's `dimension` so a pull of one viewport resists the same everywhere. */
private class ElasticVerticalOverscroll(
    private val state: ElasticOverscrollState,
    private val atStart: () -> Boolean,
    private val atEnd: () -> Boolean,
    private val dimension: () -> Float,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
        val dim = dimension()
        if (dim <= 0f) return Offset.Zero
        // Dragging a finger down while at the very top pulls the content past its start edge.
        return if (available.y > 0f && atStart()) Offset(0f, state.applyDrag(available.y, dim)) else Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
        val dim = dimension()
        if (dim <= 0f) return Offset.Zero
        // Dragging a finger up while at the very bottom pulls the content past its end edge.
        return if (available.y < 0f && atEnd()) Offset(0f, state.applyDrag(available.y, dim)) else Offset.Zero
    }

    // A drag end (fling or not) dispatches these, so the spring-back lives here and never races the finger.
    override suspend fun onPreFling(available: Velocity): Velocity { state.release(); return Velocity.Zero }
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity { state.release(); return Velocity.Zero }
}

/** Opt-in elastic edges for a vertical scrollable. See [ElasticEdgeMath] for the tuning. */
@Composable
internal fun Modifier.elasticVerticalEdges(
    state: ElasticOverscrollState,
    atStart: () -> Boolean,
    atEnd: () -> Boolean,
): Modifier {
    val currentAtStart by rememberUpdatedState(atStart)
    val currentAtEnd by rememberUpdatedState(atEnd)
    val viewportPx = remember { mutableFloatStateOf(0f) }
    val connection = remember(state) {
        ElasticVerticalOverscroll(state, { currentAtStart() }, { currentAtEnd() }, { viewportPx.floatValue })
    }
    return this
        .onSizeChanged { viewportPx.floatValue = it.height.toFloat() }
        .graphicsLayer { translationY = state.offset }
        .nestedScroll(connection)
}
