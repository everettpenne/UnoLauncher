package com.jake.duolauncher

/**
 * The dock and status rail's horizontal slide as the pager crosses the two edges of the Home span.
 *
 * Logical pages (see [LauncherPager]): feed = -1, Home = 0..homePages-1, app list = homePages.
 * [position] is the pager's fractional logical position ([LauncherPager.position]); [homePages] is
 * the number of visible Home pages, which is also the app list's logical index; [pageWidthPx] is
 * one page's width in pixels (`maxWidth - dockWidth - 28 dp`).
 *
 * Home-to-Home keeps the dock and rail pinned (0). Scrolling toward the feed slides them right,
 * off the screen together with Home 0; scrolling toward the app list slides them left, off the
 * screen together with the last Home page. Pure arithmetic, so it is unit-tested, and it is read
 * inside a `graphicsLayer { translationX = ... }` block so it re-evaluates every frame as the
 * pager scrolls without recomposing the screen.
 */
internal object EdgeDockShift {
    fun shiftPx(position: Float, homePages: Int, pageWidthPx: Float): Float {
        if (homePages <= 0 || pageWidthPx <= 0f) return 0f
        val lastHome = (homePages - 1).toFloat()
        return when {
            position <= 0f -> -position * pageWidthPx
            position >= lastHome -> -(position - lastHome) * pageWidthPx
            else -> 0f
        }
    }
}
