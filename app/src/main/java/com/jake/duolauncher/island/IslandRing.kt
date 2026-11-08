package com.jake.duolauncher.island

import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath

/** What the ring around the collapsed island is currently showing, if anything. */
internal data class RingState(val fraction: Float, val color: Color)

internal object IslandRingLogic {
    /** The ring to show, in priority order: a running timer, then charging with the battery level. An event flash, the
     * expanded panel and a ringing timer (which has its own treatment) show none.
     */
    fun choose(
        flashActive: Boolean, expanded: Boolean, ringing: Boolean,
        timerActive: Boolean, timerFraction: Float,
        charging: Boolean, battery: Int?,
        timerColor: Color, chargeColor: Color,
        /** The colour of the app behind an event flash, which rings the pill for as long as the flash is up. */
        flashTint: Color? = null,
    ): RingState? = when {
        flashActive && flashTint != null && !expanded && !ringing -> RingState(1f, flashTint)
        flashActive || expanded || ringing -> null
        timerActive -> RingState(timerFraction.coerceIn(0f, 1f), timerColor)
        charging && battery != null -> RingState((battery / 100f).coerceIn(0f, 1f), chargeColor)
        else -> null
    }

    /** The arc (as distances along an outline of [length]) for [fraction] of a loop, centred at [centre] (the outline's bottom centre) so it
     * grows evenly up both sides, meeting at the top when full, like a level rising in the pill: one range, or two where it runs past the
     * outline's start, which may be anywhere. It used to start at the top centre and run clockwise, which left the pill lopsided. */
    /**
     * How much of the ring shows for the morph's [progress] (0 the pill, 1 open): all of it on the pill, none once the island is a fifth of the way
     * open, eased both ways. Before, the ring was all there or not at all, cut at half open, so closing showed a green outline round a body still
     * nearly the panel's size, which then shrank inside it.
     */
    fun shown(progress: Float): Float {
        val t = (1f - progress / .2f).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun arcs(length: Float, centre: Float, fraction: Float): List<ClosedFloatingPointRange<Float>> {
        if (length <= 0f || fraction <= 0f) return emptyList()
        if (fraction >= 1f) return listOf(0f..length)
        val half = length * fraction / 2f
        val c = ((centre % length) + length) % length
        val start = c - half; val end = c + half
        return when {
            start < 0f -> listOf(0f..end, (length + start)..length)
            end > length -> listOf(start..length, 0f..(end - length))
            else -> listOf(start..end)
        }
    }

    /** The distance along an outline of [length] where [distanceSq] (of the point there, from a target) is least: a coarse pass, then a fine one. */
    fun closestAlong(length: Float, distanceSq: (Float) -> Float): Float {
        if (length <= 0f) return 0f
        val coarse = 96
        var best = 0f; var bestD = Float.MAX_VALUE
        for (i in 0 until coarse) { val at = length * i / coarse; val dd = distanceSq(at); if (dd < bestD) { bestD = dd; best = at } }
        val step = length / coarse
        for (i in -16..16) { val at = ((best + step * i / 16f) % length + length) % length; val dd = distanceSq(at); if (dd < bestD) { bestD = dd; best = at } }
        return best
    }
}

/** Draws [ring] behind the island, peeking out from under its edge by [strokePx]: a faint track all the way round and the progress arc,
 * rising from the bottom centre evenly up both sides ([IslandRingLogic.arcs]). It follows the island's own outline ([IslandMorph.outline],
 * Apple's squircle pill), not a circular-cornered rectangle inset from it, which drifted off the pill's edge at its ends; and it is drawn under
 * the island, not over its edge, so the pill stays whole and black, ringed by a thin line of colour round it.
 * [cornerPx] is read as the ring draws, so a morphing island's ring follows its corner without recomposing. */
internal fun Modifier.islandRing(ring: RingState?, cornerPx: () -> Float, strokePx: Float, shown: () -> Float = { 1f }): Modifier =
    if (ring == null) this else {
        // Made once for the modifier's lifetime. The outline and where its bottom centre lies along it are worked out again only when the
        // island's size or corner changes (the ring shows on the resting pill, so that is rarely), not on every frame.
        val outline = Path()
        val measure = PathMeasure()
        val piece = Path()
        val sink = ComposePathSink(outline)
        var builtFor = Triple(-1f, -1f, -1f)
        var length = 0f
        var bottom = 0f
        drawWithContent {
            val w = size.width; val h = size.height
            val k = shown().coerceIn(0f, 1f)
            if (w <= 0f || h <= 0f || k <= .001f) { drawContent(); return@drawWithContent }
            val r = cornerPx().coerceIn(0f, minOf(w, h) / 2f)
            if (builtFor != Triple(w, h, r)) {
                outline.rewind()
                IslandMorph.outline(w, h, r, density, sink)
                measure.setPath(outline, false)
                length = measure.length
                bottom = IslandRingLogic.closestAlong(length) { at -> measure.getPosition(at).let { (it.x - w / 2f) * (it.x - w / 2f) + (it.y - h) * (it.y - h) } }
                builtFor = Triple(w, h, r)
            }
            // A stroke twice as wide as the band, centred on the edge, with the island's inside cut away: only the outer half shows. The body
            // is drawn over it anyway; the cut keeps the colour from showing through a glass island, which is not opaque.
            // [shown] slides the band out from under the body as it comes (its width grows from nothing) and fades it in with it.
            val band = strokePx * 2f * k
            clipPath(outline, ClipOp.Difference) {
                drawPath(outline, ring.color.copy(alpha = .22f * k), style = Stroke(band))
                for (range in IslandRingLogic.arcs(length, bottom, ring.fraction)) {
                    piece.rewind()
                    if (measure.getSegment(range.start, range.endInclusive, piece, true))
                        drawPath(piece, ring.color.copy(alpha = ring.color.alpha * k), style = Stroke(band, cap = StrokeCap.Round))
                }
            }
            drawContent()
        }
    }
