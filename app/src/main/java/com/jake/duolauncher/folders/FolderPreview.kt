package com.jake.duolauncher.folders

import com.jake.duolauncher.*
/** How a folder tile's preview is laid out; pure so the rules are unit-tested. */
internal object FolderPreview {
    /** How many icons the tile shows: always a 3x3 grid, as on iOS (a small folder fills it from the top left). */
    @Suppress("UNUSED_PARAMETER") fun capacity(appCount: Int): Int = 9

    /** Columns of the preview grid: always three. */
    @Suppress("UNUSED_PARAMETER") fun columns(shown: Int): Int = 3

    /** An icon's edge as a fraction of the tile: the grid leaves a margin and a gap around the icons. */
    @Suppress("UNUSED_PARAMETER") fun iconFraction(columns: Int): Float = ICON

    /** iOS's folder tile: a margin of 11 % of the tile all round and 3.5 % between icons, which leaves each of the nine at 23.7 %. */
    const val MARGIN = .11f
    const val GAP = .035f
    const val ICON = (1f - 2f * MARGIN - 2f * GAP) / 3f

    /** The badge for a whole folder: every unread count inside it, never negative. */
    fun unread(counts: List<Int>): Int = counts.sumOf { it.coerceAtLeast(0) }
}

/** How the open folder is laid out: nine apps to a page in a 3x3 grid, as on iOS, with dots when there is more than one page. */
internal object FolderPages {
    const val PER_PAGE = 9
    const val COLUMNS = 3

    fun pageCount(appCount: Int): Int = maxOf(1, (appCount + PER_PAGE - 1) / PER_PAGE)

    fun page(appIds: List<String>, page: Int): List<String> = appIds.drop(page * PER_PAGE).take(PER_PAGE)

    /** Rows the panel is sized for: those of the fullest page, so a small folder gets a small panel and every page of a big one the same. */
    fun rows(appCount: Int): Int = ((minOf(appCount, PER_PAGE) + COLUMNS - 1) / COLUMNS).coerceAtLeast(1)

    /** The page that holds the app at [index] in the folder. */
    fun pageOf(index: Int): Int = (index / PER_PAGE).coerceAtLeast(0)
}

/**
 * What a new folder is called. iOS names a folder after what is in it ("Social", "Games"); so does this, from the category each app
 * declares about itself, when the apps agree. Otherwise "Folder". Pure: the numbers are Android's ApplicationInfo.CATEGORY_* values.
 */
internal object FolderNaming {
    const val DEFAULT = "Folder"

    private val NAMES = mapOf(
        0 to "Games", 1 to "Music", 2 to "Video", 3 to "Photos", 4 to "Social", 5 to "News", 6 to "Maps", 7 to "Productivity", 8 to "Utilities")

    /** The name the apps' [categories] suggest: their shared category's name, or [DEFAULT] when they differ or declare none. */
    fun suggest(categories: List<Int?>): String {
        val known = categories.map { it ?: -1 }
        val shared = known.firstOrNull()?.takeIf { first -> first >= 0 && known.all { it == first } }
        return shared?.let(NAMES::get) ?: DEFAULT
    }
}

/** The name a new folder of [apps] gets, from the categories their packages declare (API 26+; an app that cannot be read counts as undeclared). */
internal fun suggestedFolderName(context: android.content.Context, apps: List<AppEntry>): String =
    FolderNaming.suggest(apps.map { app ->
        runCatching { context.packageManager.getApplicationInfo(app.component.packageName, 0).category }.getOrNull()
    })
