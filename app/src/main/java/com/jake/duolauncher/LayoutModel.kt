package com.jake.duolauncher

/** Jake's reference measures 76px dock artwork against 106px home artwork. */
fun dockIconSize(homeIconSize: Float) = homeIconSize * (76f / 106f)

data class LayoutPreset(
    val iconSize: Float = 66f,
    val rowGap: Float = 8f,
    val dockWidth: Float = 68f,
    val dockPosition: Float = 0.56f,
    val dockAlignToGrid: Boolean = true,
) {
    fun sanitized() = copy(
        iconSize = iconSize.coerceIn(40f, 68f),
        rowGap = rowGap.coerceIn(0f, 28f),
        dockWidth = dockWidth.coerceIn(56f, 84f),
        dockPosition = dockPosition.coerceIn(0.25f, 0.75f),
    )
}

data class HomeGeometry(
    val expanded: Boolean,
    val homeWidth: Float,
    val gridWidth: Float,
    val iconSize: Float,
    val rowHeight: Float,
    val widgetHeight: Float,
    val contentTop: Float,
    val dockTop: Float,
    val dockHeight: Float,
    val dockRowHeight: Float,
    /** Where the grid starts, from the page's left edge: the space for the long-press margin beside it on the side layout. */
    val gridStart: Float = 16f,
    /** The space between two widgets side by side, and one above another (a widget is its cells less these). */
    val widgetGapX: Float = 10f,
    val widgetGapY: Float = 18f,
    /** The iOS layout (see [HomeStyle]): a uniform grid, the dock along the bottom. */
    val ios: Boolean = false,
    /** iOS layout only: the bottom dock's height, its gap from the bottom of the safe area and from the screen's sides, and the
     * Search pill's height and its gap above the dock. */
    val dockBarHeight: Float = 0f,
    val dockBarBottom: Float = 0f,
    val dockBarSide: Float = 0f,
    val pillHeight: Float = 0f,
    val pillGap: Float = 0f,
    /** The icon box the grid and the widgets are aligned to: a widget's top and bottom are the top and bottom of the icons in its first and last rows.
     *  [iconSize] is the icon as drawn, which the Icon Size setting scales within that box without moving anything else; 0 means the same as [iconSize]. */
    val iconBox: Float = 0f,
) {
    /** The icon box (see [iconBox]). */
    val box: Float get() = if (iconBox > 0f) iconBox else iconSize
    /** How far the drawn icon is inset from the top of its cell, so a scaled icon stays centred on the box. */
    val iconInset: Float get() = ((box - iconSize) / 2f).coerceAtLeast(0f)
    /** iOS layout: the height at the bottom of the safe area the dock and the Search pill take, which pages leave free. */
    val bottomBarReserve: Float get() = dockBarBottom + dockBarHeight + pillGap + pillHeight + pillGap
}

/**
 * The iPhone's Home grid, scaled to a phone [width] x [height] dp (the safe area). Apple's 393 pt iPhone has a 4-column grid whose widgets
 * are 158 x 158 (small), 338 x 158 (medium) and 338 x 354 (large), 27.5 pt in from each side, 22 pt apart across and 38 pt apart down, with
 * 60 pt icons. From those: a column pitch of 90 and a row pitch of 98, so two rows less one gap is a square small widget, and four columns
 * less a gap is the medium and large widgets' width. Everything is scaled by width/393 (within 0.85 to 1.15), and on a screen too short for six
 * such rows (a foldable's cover) the row pitch shrinks to fit, the icon with it if the name no longer fits under it; the widgets then are a
 * little wider than tall rather than running under the dock.
 *
 * It reuses [HomeGeometry]'s fields so the rest of Home needs no second layout: the widget band's pitch, (widgetHeight + widgetGapY) / 2, is made
 * equal to the row pitch, which turns the side layout's two-pitch grid into a uniform one everywhere it is measured.
 */
fun iosHomeGeometry(width: Float, height: Float, labels: Boolean, labelHeight: Float = 20f, iconScale: Float = 1f): HomeGeometry {
    val s = (width / 393f).coerceIn(.85f, 1.15f)
    val margin = 27.5f * s
    val gapX = 22f * s
    val pitchX = (width - 2f * margin + gapX) / GRID_COLUMNS
    val gridWidth = pitchX * GRID_COLUMNS
    val gridStart = margin - gapX / 2f
    // The bottom: the dock (an icon and its padding), its gap above the safe area's bottom, and the Search pill above it.
    var icon = 60f * s
    val dockBottom = 6f * s
    val dockHeight = icon + 30f * s
    val pillHeight = 32f
    val pillGap = 12f * s
    val contentTop = 14f * s
    val reserve = dockBottom + dockHeight + pillGap + pillHeight + pillGap
    val fitPitch = (height - contentTop - reserve) / GRID_ROWS
    val pitchY = minOf(98f * s, fitPitch).coerceAtLeast(48f)
    // The name must fit under the icon in its row, with a little air.
    val nameRoom = if (labels) maxOf(20f, labelHeight) + 6f else 8f
    icon = minOf(icon, pitchY - nameRoom).coerceAtLeast(36f)
    val smallWidth = 2f * pitchX - gapX
    val gapY = maxOf(14f * s, 2f * pitchY - smallWidth)
    val widgetHeight = 2f * pitchY - gapY
    val box = icon
    icon = box * iconScale.coerceIn(.6f, 1.03f)
    return HomeGeometry(expanded = false, homeWidth = width, gridWidth = gridWidth, iconSize = icon, iconBox = box, rowHeight = pitchY,
        widgetHeight = widgetHeight, contentTop = contentTop, dockTop = 0f, dockHeight = dockHeight, dockRowHeight = dockHeight,
        gridStart = gridStart, widgetGapX = gapX, widgetGapY = gapY, ios = true, dockBarHeight = dockHeight, dockBarBottom = dockBottom,
        dockBarSide = 10f * s, pillHeight = pillHeight, pillGap = pillGap)
}

/**
 * iOS widget sizes. Apple's Home takes widgets at three sizes only: small (2 x 2 cells), medium (4 x 2) and large (4 x 4). On the iOS layout a
 * widget is placed and resized at one of them. Pure, so it is unit-tested.
 */
object IosWidgetSizes {
    val SMALL = WidgetSpan(2, 2)
    val MEDIUM = WidgetSpan(4, 2)
    val LARGE = WidgetSpan(4, 4)
    val ALL = listOf(SMALL, MEDIUM, LARGE)

    fun label(span: WidgetSpan): String? = when (span) { SMALL -> "Small"; MEDIUM -> "Medium"; LARGE -> "Large"; else -> null }

    /** Whether [span] is within what the widget allows ([constraints]; null allows any). */
    fun fits(span: WidgetSpan, constraints: WidgetSpanConstraints?): Boolean = constraints == null ||
        (span.width in constraints.minimum.width..constraints.maximum.width && span.height in constraints.minimum.height..constraints.maximum.height)

    /** The iOS sizes the widget allows. */
    fun allowed(constraints: WidgetSpanConstraints?): List<WidgetSpan> = ALL.filter { fits(it, constraints) }

    /**
     * The iOS size closest to [wanted] that the widget allows, preferring one at least as big (a widget is never squeezed below what it asked
     * for when a larger iOS size fits). A widget that allows none of them (a fixed 3 x 1, say) keeps [wanted]: it is better shown at its own
     * size than not at all.
     */
    fun snap(wanted: WidgetSpan, constraints: WidgetSpanConstraints? = null): WidgetSpan =
        allowed(constraints).minByOrNull { s ->
            val short = (if (s.width < wanted.width) 1 else 0) + (if (s.height < wanted.height) 1 else 0)
            short * 100 + kotlin.math.abs(s.width - wanted.width) + kotlin.math.abs(s.height - wanted.height) * 2
        } ?: wanted
}

/** Advance old defaults without changing individually tuned values. */
fun upgradePreset(preset: LayoutPreset, schema: Int, expanded: Boolean): LayoutPreset = when {
    schema < 2 -> preset.copy(
        iconSize = if (preset.iconSize == if (expanded) 58f else 54f) 66f else preset.iconSize,
        rowGap = if (preset.rowGap == 12f) 8f else preset.rowGap,
        dockWidth = if (preset.dockWidth == 64f) 68f else preset.dockWidth,
    )
    schema == 2 && preset.iconSize == 60f -> preset.copy(iconSize = 66f)
    else -> preset
}

fun homeGeometry(width: Float, height: Float, preset: LayoutPreset, labels: Boolean, statusHeight: Float = 0f, labelHeight: Float = 20f, inLibrary: Boolean = false, homeBottomSpace: Float = 44f): HomeGeometry {
    val p = preset.sanitized()
    val expanded = width >= 650f
    val homeWidth = if (expanded) minOf(460f, width * 0.56f) else width
    val gridWidth = (homeWidth - p.dockWidth - 44f).coerceAtLeast(192f)
    val cellWidth = gridWidth / 4f
    // A widget is its columns less the gap between widgets, and its height runs from the top of the icons in its first row to the bottom of the
    // icons in its last (not to the bottom of the names under them), as on iOS. The icon box is what the grid is built on; the Icon Size setting
    // draws the icon smaller or larger inside it without moving the grid or the widgets.
    val gapX = 8f
    val box = minOf(66f, (cellWidth - 14f).coerceAtLeast(32f))
    val icon = box * (p.iconSize / 66f).coerceIn(.6f, 1.03f)
    // The row is as tall as the icon and its name need, or as tall as makes a 2 x 2 widget exactly square (two columns less the gap, against a
    // row plus an icon), whichever is more. Row Spacing adds to it above its default of 8 dp.
    val labelRoom = if (labels) maxOf(20f, labelHeight) else 20f
    val row = maxOf(box + labelRoom, 2f * cellWidth - gapX - box) + (p.rowGap - 8f).coerceAtLeast(0f)
    val gapY = row - box
    val widget = row + box
    // Below the widget band are the app rows: the grid's rows less the band's two.
    val appRows = GRID_ROWS - 2
    // The grid starts a short way under the status bar, as iOS's does (about 30 dp), and what is left over goes below it. It used to be centred in
    // the leftover room, up to 72 dp down, which left a dead band between the status bar and the first widgets.
    val contentTop = ((height - 2f * row - appRows * row - homeBottomSpace) / 4f).coerceIn(16f, 30f)
    // Search reclaims the redundant bottom controls' space for all four dock apps.
    // Extremely short windows still scroll rather than reduce touch targets below 48dp.
    val topLimit = maxOf(8f, statusHeight)
    val bottomReserve = if (inLibrary) 12f else 124f
    // Outer dock edges span the first through third icon images, excluding the last label.
    val desiredHeight = if (p.dockAlignToGrid) 2f * row + box else 256f
    val dockHeight = minOf(desiredHeight, (height - topLimit - bottomReserve).coerceAtLeast(76f))
    val dockRowHeight = ((dockHeight - 16f) / 4f).coerceAtLeast(48f)
    // Use the home position as the anchor, so removing library buttons does not
    // move a low-positioned dock on ordinary page swipes. Move up only to fit.
    val homeDockHeight = minOf(desiredHeight, (height - topLimit - 124f).coerceAtLeast(76f))
    val homeDockTop = (if (p.dockAlignToGrid) contentTop + 2f * row else height * p.dockPosition - homeDockHeight / 2f)
        .coerceIn(topLimit, maxOf(topLimit, height - homeDockHeight - 124f))
    val dockTop = homeDockTop.coerceIn(topLimit, maxOf(topLimit, height - dockHeight - bottomReserve))
    return HomeGeometry(expanded, homeWidth, gridWidth, icon, row, widget, contentTop, dockTop, dockHeight, dockRowHeight,
        widgetGapX = gapX, widgetGapY = gapY, iconBox = box)
}

/** Keep stored order stable across installs, removals and configuration changes. */
fun reconcileOrder(saved: List<String>, installed: List<String>): List<String> {
    val present = installed.toSet()
    return (saved.filter { it in present } + installed).distinct()
}

/** Installing an app must never create a home-screen pin. */
fun reconcilePins(saved: List<String>, installed: List<String>): List<String> {
    val available = installed.toSet()
    return saved.filter { it in available }.distinct()
}

fun migrateHomePins(legacy: List<String>, installed: List<String>, suggested: List<String>): List<String> {
    val surviving = reconcilePins(legacy, installed)
    val oldSet = surviving.toSet()
    val wasReordered = surviving.isNotEmpty() && surviving != installed.filter { it in oldSet }
    return if (wasReordered) surviving.take(16) else reconcilePins(suggested, installed).take(16)
}

fun homePageCount(cellCount: Int) = maxOf(1, (cellCount + HOME_CELLS - 1) / HOME_CELLS)

fun moveApp(order: List<String>, id: String, offset: Int): List<String> {
    val from = order.indexOf(id)
    if (from < 0) return order
    val to = (from + offset).coerceIn(0, order.lastIndex)
    return order.toMutableList().apply { add(to, removeAt(from)) }
}
