package com.jake.duolauncher.appearance

/**
 * The wallpaper's drift for depth. The wallpaper is drawn [OVERSCAN] larger than the screen on each side, and as the pages scroll it
 * slides the opposite way, so it moves less than the pages in front of it. The slide per page is capped so that a long run of pages
 * still stays inside the overscan, and the whole range is centred, so the middle page shows the wallpaper as the scene was drawn.
 * Pure arithmetic, so it is unit-tested.
 */
internal object WallpaperDepth {
    /** How much larger than the screen it is drawn, as a fraction of the width on each side. */
    const val OVERSCAN = .045f

    /** The most it slides between neighbouring pages, as a fraction of the width. */
    const val MAX_PER_PAGE = .032f

    class Drift(val scale: Float, val translationX: Float)

    /**
     * [position] is where the pager is, in pages (2.5 is halfway between the third and fourth), [pageCount] how many pages it has and
     * [widthPx] the width being drawn. The translation is how far to move the wallpaper, in pixels.
     */
    fun drift(position: Float, pageCount: Int, widthPx: Float): Drift {
        val scale = 1f + 2f * OVERSCAN
        if (pageCount <= 1 || widthPx <= 0f) return Drift(scale, 0f)
        val room = OVERSCAN * widthPx
        val perPage = minOf(MAX_PER_PAGE * widthPx, 2f * room / (pageCount - 1))
        val centred = position.coerceIn(0f, (pageCount - 1).toFloat()) - (pageCount - 1) / 2f
        return Drift(scale, (-centred * perPage).coerceIn(-room, room))
    }
}
