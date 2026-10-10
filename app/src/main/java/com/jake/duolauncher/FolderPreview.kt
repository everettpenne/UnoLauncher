package com.jake.duolauncher

/** How a folder tile's preview is laid out; pure so the rules are unit-tested. */
internal object FolderPreview {
    /** How many icons the tile shows: four for small folders (2x2), up to nine (3x3) once there are five or more. */
    fun capacity(appCount: Int): Int = if (appCount <= 4) 4 else 9

    /** Columns for the icons actually shown. */
    fun columns(shown: Int): Int = if (shown <= 4) 2 else 3

    /** An icon's edge as a fraction of the tile: the grid leaves a margin and a gap around the icons. */
    fun iconFraction(columns: Int): Float = if (columns == 2) .36f else .25f

    /** The badge for a whole folder: every unread count inside it, never negative. */
    fun unread(counts: List<Int>): Int = counts.sumOf { it.coerceAtLeast(0) }
}
