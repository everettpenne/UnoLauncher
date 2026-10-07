package com.jake.duolauncher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The page-dots capsule: Discover, one dot per Home page, All apps. With glass on it carries an
 * iOS 26-style selection lens. The lens rides the pager, so it glides between items as pages
 * scroll; resting a finger on the strip lifts it, and dragging along the strip scrubs through
 * the pages with the lens under the finger, magnifying the dot or icon it sits on.
 */
@Composable
internal fun PageStrip(
    pager: LauncherPager,
    homePages: Int,
    visibleHomePages: Int,
    showCompass: Boolean,
    glass: PageGlass?,
    onDiscover: () -> Unit,
    onLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    onBounds: (androidx.compose.ui.geometry.Rect) -> Unit = {},
) {
    val ink = rememberAdaptiveInk(glass?.tint ?: Glass, if (glass != null) .12f else .0f)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dotsMode = visibleHomePages <= PageStripLayout.MAX_DOTS
    val widths = remember(showCompass, visibleHomePages) { PageStripLayout.widths(showCompass, visibleHomePages) }
    // The strip's own content is recorded so the lens can magnify the icons under it, not only the wallpaper.
    val content = rememberLayerBackdrop()
    val lensBackdrop = if (glass != null) rememberCombinedBackdrop(glass.backdrop, content) else null
    var scrubbing by remember { mutableStateOf(false) }
    var scrubJob by remember { mutableStateOf<Job?>(null) }
    val lift by animateFloatAsState(if (scrubbing) 1f else 0f,
        spring(dampingRatio = .55f, stiffness = 380f), label = "lens lift")

    Box(modifier.onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .then(ink.track).then(if (glass != null) Modifier.liquidGlass(glass.backdrop,
            Corner.pill, glass.tint.copy(alpha = .12f), blurRadius = .75f, settings = glass.settings) else Modifier)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart) {
        Row(Modifier
                .then(if (lensBackdrop != null) Modifier.layerBackdrop(content) else Modifier)
                .then(if (dotsMode) Modifier.pointerInput(widths, showCompass) {
                    var last = -1
                    fun scrubTo(xPx: Float) {
                        val item = PageStripLayout.itemAt(xPx / density.density, widths)
                        if (item == last) return
                        last = item
                        val page = PageStripLayout.pageFor(item, showCompass)
                        scrubJob?.cancel()
                        scrubJob = scope.launch { pager.animateScrollToPage(page) }
                    }
                    // Initial pass, ahead of the buttons: once the finger has travelled past touch slop
                    // horizontally the drag is consumed, which cancels the button under it so a scrub
                    // never ends in an accidental tap. A press that never travels is left to the buttons.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var scrubbingNow = false
                        last = -1
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (scrubbingNow) change.consume()
                                break
                            }
                            val dx = change.position.x - down.position.x
                            val dy = change.position.y - down.position.y
                            if (!scrubbingNow && kotlin.math.abs(dx) > viewConfiguration.touchSlop &&
                                kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                                scrubbingNow = true
                                scrubbing = true
                                scrubTo(down.position.x)
                            }
                            if (scrubbingNow) {
                                change.consume()
                                scrubTo(change.position.x)
                            }
                        }
                        scrubbing = false
                    }
                } else Modifier),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (showCompass) IconButton(onClick = onDiscover, Modifier.size(PageStripLayout.ICON_DP.dp).testTag("discover-page-link")) {
                Icon(Icons.Rounded.Explore, "Discover", tint = ink.soft(.65f), modifier = Modifier.size(17.dp))
            }
            if (dotsMode) repeat(visibleHomePages) { index ->
                Box(Modifier.size(PageStripLayout.DOT_DP.dp).clip(CircleShape)
                    .clickable { scope.launch { pager.animateScrollToPage(index) } }
                    .semantics { contentDescription = if (index == homePages) "New home page" else "Home page ${index + 1}" },
                    contentAlignment = Alignment.Center) {
                    if (index == homePages) Icon(Icons.Rounded.Add, null, tint = ink.color, modifier = Modifier.size(14.dp))
                    else Box(Modifier.size(if (index == pager.currentPage) 6.dp else 4.dp)
                        .background(ink.color.copy(alpha = if (index == pager.currentPage) 1f else .4f), CircleShape))
                }
            } else Text("${minOf(pager.currentPage + 1, homePages)} / $homePages", color = ink.color, fontSize = 12.sp)
            IconButton(onClick = onLibrary, Modifier.size(PageStripLayout.ICON_DP.dp).testTag("library-page-link")) {
                Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, "All apps page",
                    tint = ink.color.copy(alpha = if (pager.currentPage == visibleHomePages) 1f else .6f),
                    modifier = Modifier.size(17.dp))
            }
        }
        if (glass != null && lensBackdrop != null && dotsMode) {
            val lensWidthPx = PageStripLayout.LENS_WIDTH_DP * density.density
            Box(Modifier
                // Read in the placement lambda, so a scrolling pager moves the lens without recomposing the strip.
                .offset {
                    val center = PageStripLayout.centerAt(pager.position, widths, showCompass) * density.density
                    IntOffset((center - lensWidthPx / 2f).roundToInt(), 0)
                }
                .size(PageStripLayout.LENS_WIDTH_DP.dp, PageStripLayout.LENS_HEIGHT_DP.dp)
                .glassLens(lensBackdrop, RoundedCornerShape(percent = 50), lift = lift, settings = glass.settings)
                .testTag("page-lens"))
        }
    }
}
