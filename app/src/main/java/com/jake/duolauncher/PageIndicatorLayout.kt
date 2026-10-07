package com.jake.duolauncher

/** The fixed pieces of the bottom strip (page dots, and the "Set as home app" button above them
 * until Uno is the Home app), so every page that sits above it reserves exactly the space the
 * strip occupies. Pages used to guess (16 dp, 44 dp, 88 dp), which is how content ended up
 * underneath the dots.
 */
internal object PageIndicatorLayout {
    /** Gap between the strip and the bottom of the safe area. */
    const val BOTTOM_MARGIN_DP = 8f
    /** Height of the dots capsule: its tallest children are the 32 dp Discover/All apps buttons. */
    const val CAPSULE_HEIGHT_DP = 32f
    /** Clear space between the strip and the content above it (the spacing scale's sibling gap). */
    const val CONTENT_GAP_DP = 8f
    /** The "Set as home app" button stacked above the capsule (48 dp touch target). */
    const val SETUP_BUTTON_DP = 48f

    /** Height content pages must leave free at the bottom of the safe area. */
    fun reserveDp(isDefaultHome: Boolean): Float =
        BOTTOM_MARGIN_DP + CAPSULE_HEIGHT_DP + CONTENT_GAP_DP + if (isDefaultHome) 0f else SETUP_BUTTON_DP
}
