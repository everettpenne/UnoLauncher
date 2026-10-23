package com.jake.duolauncher.island

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.lang.ref.WeakReference

/**
 * Where the status bar's icons really are, read from the status bar itself, so the island's edges fall in the real gaps between them on any
 * phone, at any font or display size and whatever icons are showing. [StatusBarGrid] was measured from screenshots of one Pixel's status bar
 * and moved by hand ([StatusBarCalibration]); this replaces both whenever a fresh reading is there.
 *
 * It comes from [StatusBarReaderService], a second, separate accessibility service the person turns on only if they want this. SystemUI exposes
 * each status bar icon to screen readers with its place on screen; the service takes the *bounds* of those icons and nothing else (it never
 * reads their text or descriptions), from SystemUI's status bar window alone, keeps the latest set in memory only, and sends nothing anywhere.
 */
internal data class StatusBarReading(
    /** Each icon (the clock, a notification icon, Wi-Fi, the battery...) as a span across the screen, in dp from the left edge, in order. */
    val icons: List<StatusBarGrid.Span>,
    /** The screen's width when it was read, in dp: a reading made at another width (a rotation, a fold) is not used. */
    val screenWidthDp: Float,
    val atMs: Long,
)

internal object StatusBarLive {
    /** The latest reading, or null when the service is off or has read nothing yet. Snapshot state, so the island lays itself out again when the
     * status bar changes (an icon appears, the hour gains a digit). */
    var reading by mutableStateOf<StatusBarReading?>(null)
        internal set

    /** The reading to use for a screen [screenWidthDp] wide, if it was read at that width and the service is still running. */
    fun usable(screenWidthDp: Float): StatusBarReading? =
        reading?.takeIf { StatusBarReaderService.isConnected() && kotlin.math.abs(it.screenWidthDp - screenWidthDp) < 2f && it.icons.isNotEmpty() }
}

/** The pure part: from rectangles to icons, and from icons to the gaps [StatusBarGrid.layout] puts edges in. Unit-tested. */
internal object StatusBarScan {
    /** Parts that overlap by more than this are one icon (a battery and its bolt drawn over it). Parts that only touch are not: each view's
     * bounds include its own padding, so the clock and the notification icon after it touch with no gap and are still two icons. */
    private const val MERGE_DP = .5f
    /** Where an edge goes beside the outermost icon of a side, past its edge. */
    private const val EDGE_ROOM_DP = 3f

    /**
     * The icons among [rects] (screen px, the visible leaf nodes of the status bar window): only small ones within the bar's height
     * ([barBottomPx]) count, so a container or the shade's panel never does. Overlapping ones are merged. Returned in dp, left to right.
     */
    fun icons(rects: List<PxRect>, screenWidthPx: Float, barBottomPx: Float, density: Float): List<StatusBarGrid.Span> {
        val spans = rects.filter {
            it.width >= 2f && it.height >= 2f && it.top < barBottomPx && it.bottom <= barBottomPx * 1.15f &&
                it.width < screenWidthPx * .3f && it.left >= 0f && it.right <= screenWidthPx
        }.map { StatusBarGrid.Span(it.left / density, it.right / density) }.sortedBy { it.left }
        val merged = ArrayList<StatusBarGrid.Span>()
        for (s in spans) {
            val last = merged.lastOrNull()
            if (last != null && s.left < last.right - MERGE_DP) merged[merged.lastIndex] = StatusBarGrid.Span(last.left, maxOf(last.right, s.right))
            else merged += s
        }
        return merged
    }

    /**
     * The gaps for [StatusBarGrid.layout], as its own grid gives them: on the left of the camera ([holeLeftDp]..[holeRightDp]) the middle of each
     * gap after the first icon (the clock), so the island never covers the clock, and room past the last; on the right, room before the first
     * icon, the middle of each gap, and room past the last. Null for a side with no icons, which then keeps the measured grid.
     */
    fun gaps(icons: List<StatusBarGrid.Span>, holeLeftDp: Float, holeRightDp: Float): Pair<List<Float>?, List<Float>?> {
        val centre = (holeLeftDp + holeRightDp) / 2f
        val left = icons.filter { (it.left + it.right) / 2f < centre }
        val right = icons.filter { (it.left + it.right) / 2f >= centre }
        val leftGaps = if (left.isEmpty()) null else
            (0 until left.size - 1).map { (left[it].right + left[it + 1].left) / 2f } + (left.last().right + EDGE_ROOM_DP)
        val rightGaps = if (right.isEmpty()) null else
            listOf(right.first().left - EDGE_ROOM_DP) + (0 until right.size - 1).map { (right[it].right + right[it + 1].left) / 2f } +
                (right.last().right + EDGE_ROOM_DP)
        return leftGaps to rightGaps
    }
}

/**
 * Reads where the status bar's icons are (see [StatusBarLive]). It is told only of changes to SystemUI's windows, waits a moment for a burst of
 * them to settle, then walks the status bar window's visible leaves and keeps their bounds. It never reads text, never acts, and never looks at
 * any other app's window.
 */
class StatusBarReaderService : AccessibilityService() {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val scan = Runnable { read() }

    override fun onServiceConnected() {
        instance = WeakReference(this)
        handler.postDelayed(scan, 300)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName != SYSTEM_UI) return
        handler.removeCallbacks(scan)
        handler.postDelayed(scan, SETTLE_MS)
    }

    private fun read() {
        val metrics = resources.displayMetrics
        val barPx = runCatching {
            resources.getDimensionPixelSize(resources.getIdentifier("status_bar_height", "dimen", "android"))
        }.getOrDefault(0).takeIf { it > 0 } ?: (24 * metrics.density).toInt()
        val bar = windows.orEmpty().firstOrNull { w ->
            val r = android.graphics.Rect().also { w.getBoundsInScreen(it) }
            w.type == AccessibilityWindowInfo.TYPE_SYSTEM && r.top == 0 && r.height() in 1..(barPx * 3 / 2) && r.width() >= metrics.widthPixels / 2
        } ?: return
        val root = bar.root ?: return
        val rects = ArrayList<PxRect>()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        val r = android.graphics.Rect()
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst(); visited++
            // A wrapper can report itself not visible while the icons inside it are (SystemUI's Compose host does), so every node's children
            // are walked, and only a visible leaf counts.
            if (node.childCount == 0) {
                if (!node.isVisibleToUser) continue
                node.getBoundsInScreen(r)
                rects += PxRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())
            } else for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        val icons = StatusBarScan.icons(rects, metrics.widthPixels.toFloat(), barPx.toFloat(), metrics.density)
        // An empty read (the bar hidden, mid-animation) keeps the last good one rather than wiping it.
        if (icons.isNotEmpty()) StatusBarLive.reading = StatusBarReading(icons, metrics.widthPixels / metrics.density, System.currentTimeMillis())
    }

    override fun onInterrupt() = Unit
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        handler.removeCallbacks(scan)
        if (instance.get() === this) instance.clear()
        StatusBarLive.reading = null
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        handler.removeCallbacks(scan)
        if (instance.get() === this) instance.clear()
        super.onDestroy()
    }

    companion object {
        private const val SYSTEM_UI = "com.android.systemui"
        private const val SETTLE_MS = 250L
        private const val MAX_NODES = 400
        private var instance = WeakReference<StatusBarReaderService>(null)
        internal fun isConnected() = instance.get() != null

        /** Whether the person has turned the service on in Android's accessibility settings (it may not be running yet). */
        internal fun isEnabled(context: Context): Boolean {
            val component = ComponentName(context, StatusBarReaderService::class.java)
            return context.getSystemService(AccessibilityManager::class.java).getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            ).any { info -> info.resolveInfo?.serviceInfo?.let { ComponentName(it.packageName, it.name) } == component }
        }
    }
}
