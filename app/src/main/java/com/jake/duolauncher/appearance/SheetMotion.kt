package com.jake.duolauncher.appearance

/**
 * The tuning for a bottom sheet's dismiss feel, kept in one place so a single number can be changed
 * instead of hunting through Material's internals.
 *
 * Material3's `ModalBottomSheet` (with `skipPartiallyExpanded = true`, as every Uno sheet uses)
 * already provides most of the iOS feel out of the box, and these are the numbers behind it:
 *
 *  - It tracks the finger 1:1 while dragging (its `AnchoredDraggableState` applies the drag delta directly).
 *  - A partial drag without a fling springs back; its settle is seeded with the drag's own
 *    `lastVelocity` (`SheetState.animateTo(target, velocity = lastVelocity)`), so the return carries
 *    the finger's speed — the interruptible spring the iOS feel needs, not a fixed animation.
 *  - Its internal velocity threshold is 125 dp/s and its positional threshold 56 dp (Material defaults),
 *    which is already forgiving for a downward flick.
 *
 * The one place Material's stock sheet is too eager to dismiss is *content* scroll: scrolling a long
 * settings page up past its top hands the leftover scroll/fling to the sheet and closes it by accident.
 * [SheetDismissGuard] absorbs that; the constant below lets a deliberate, fast downward *flick* still
 * dismiss the sheet, so "a downward flick with sufficient velocity dismisses" holds without the
 * accidental-close regression.
 */
internal object SheetMotion {
    /**
     * A downward fling this fast (dp/s) is treated as a deliberate "throw the sheet away" and passes
     * through the dismiss guard; anything slower is absorbed so a lazy scroll-past-top never closes it.
     * 700 dp/s is a clear flick on a phone (about a half-screen swipe in under a third of a second)
     * while a normal reading scroll ends well below it.
     */
    const val FLING_DISMISS_VELOCITY_DP = 700f

    /** Whether a downward fling at [velocityDpPerSec] (dp/s) should dismiss the sheet. Pure, for tests. */
    fun flingDismisses(velocityDpPerSec: Float): Boolean = velocityDpPerSec >= FLING_DISMISS_VELOCITY_DP

    /**
     * Rubber-band resistance for dragging a sheet *up* past its resting (expanded) position, the same
     * diminishing-returns curve as the scroll edges. Material's `skipPartiallyExpanded` sheets have no
     * position above expanded, so this is not wired up today; it lives here so a future half-height
     * sheet shares one curve instead of inventing its own.
     */
    const val OVER_DRAG_RESISTANCE = 0.35f

    /** The resisted upward over-drag for a total [distance] past the rest, capped at [maxOverDrag]. */
    fun resistUpwardDrag(distance: Float, maxOverDrag: Float): Float {
        if (maxOverDrag <= 0f) return 0f
        val d = kotlin.math.abs(distance)
        val resisted = (1f - 1f / (d * OVER_DRAG_RESISTANCE / maxOverDrag + 1f)) * maxOverDrag
        return resisted * kotlin.math.sign(distance)
    }
}
