package com.jake.duolauncher

/**
 * The dock and status rail's horizontal slide as the pager crosses the two edges of the Home span.
 *
 * Logical pages (see [LauncherPager]): feed = -1, Home = 0..homePages-1, app list = homePages.
 * [position] is the pager's fractional logical position ([LauncherPager.position]); [homePages] is
 * the number of visible Home pages, which is also the app list's logical index; [screenWidthPx] is
 * the full window width in pixels. The dock and rail sit on the RIGHT edge, so to leave the screen
 * cleanly in EITHER direction they must travel the full width: one page width clears the right edge
 * but leaves them sitting on the left edge (drawn over the app list), which is wrong.
 *
 * Home-to-Home keeps the dock and rail pinned (0). Scrolling toward the feed slides them right,
 * off the screen together with Home 0; scrolling toward the app list slides them left, fully off
 * the screen together with the last Home page. Pure arithmetic, so it is unit-tested, and it is
 * read inside a `graphicsLayer { translationX = ... }` block so it re-evaluates every frame as the
 * pager scrolls without recomposing the screen.
 */
internal object EdgeDockShift {
    fun shiftPx(position: Float, homePages: Int, screenWidthPx: Float): Float {
        if (homePages <= 0 || screenWidthPx <= 0f) return 0f
        val lastHome = (homePages - 1).toFloat()
        return when {
            position <= 0f -> -position * screenWidthPx
            position >= lastHome -> -(position - lastHome) * screenWidthPx
            else -> 0f
        }
    }
}
