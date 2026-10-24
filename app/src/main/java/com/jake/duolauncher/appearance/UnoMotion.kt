package com.jake.duolauncher.appearance

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * The springs Uno's own surfaces share, so a folder, search, the control panel and a widget stack all settle with one character, as iOS's do.
 * They were tuned one by one (0.72/420 for folders, 0.78/320 for search, the platform default for the panel and a tween for stacks).
 * The island has its own, themed motion (IslandTheme), and the glass sliders and switches keep theirs, which are part of the controls' feel.
 */
internal object UnoMotion {
    /** Something opening or growing into place (a folder, search): lively, a touch of overshoot. */
    const val OPEN_DAMPING = .78f
    const val OPEN_STIFFNESS = 380f
    /** Something sliding from one place to another (a panel, a widget stack's page): smooth, the faintest overshoot. */
    const val GLIDE_DAMPING = .86f
    const val GLIDE_STIFFNESS = 420f
    /** Coming to rest with no overshoot (springing back after a swipe, closing). */
    const val SETTLE_STIFFNESS = 600f
    /** A press dipping and springing back. */
    const val PRESS_DAMPING = .55f
    const val PRESS_STIFFNESS = 700f

    fun <T> open(): SpringSpec<T> = spring(OPEN_DAMPING, OPEN_STIFFNESS)
    fun <T> glide(): SpringSpec<T> = spring(GLIDE_DAMPING, GLIDE_STIFFNESS)
    fun <T> settle(): SpringSpec<T> = spring(1f, SETTLE_STIFFNESS)
    fun <T> press(): SpringSpec<T> = spring(PRESS_DAMPING, PRESS_STIFFNESS)
}
