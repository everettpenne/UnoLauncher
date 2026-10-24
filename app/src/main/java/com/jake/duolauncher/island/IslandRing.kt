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
import androidx.compose.ui.graphics.drawscope.Stroke

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

    /** The arc (start..end as distances along the outline) for [fraction] of a loop. The outline begins at the top centre and runs
     * clockwise, so its bottom centre is half way round; the arc is centred there and grows evenly up both sides, meeting at the top when
     * full, like a level rising in the pill. It used to start at the top centre and run clockwise, which left the pill lopsided: one side
     * lit, the other dark.
     */
    fun arc(length: Float, fraction: Float): ClosedFloatingPointRange<Float>? {
        if (length <= 0f || fraction <= 0f) return null
        val half = length * fraction.coerceIn(0f, 1f) / 2f
        return (length / 2f - half)..(length / 2f + half)
    }
}

/** Draws [ring] as a progress arc hugging the island's outline, clockwise from the top centre, over a faint track. */
/** [cornerPx] is read as the ring draws, so a morphing island's ring follows its corner without recomposing. */
internal fun Modifier.islandRing(ring: RingState?, cornerPx: () -> Float, strokePx: Float): Modifier =
    if (ring == null) this else {
        // The ring's three paths are made once for the modifier's lifetime and rewound on each draw. Building them inside the draw
        // lambda allocated a Path, a PathMeasure and a segment Path on every animation frame while the ring's corner followed a morph.
        val outline = Path()
        val measure = PathMeasure()
        val piece = Path()
        drawWithContent {
            val inset = strokePx / 2f + 1f
            val w = size.width - inset * 2; val h = size.height - inset * 2
            if (w <= 0f || h <= 0f) { drawContent(); return@drawWithContent }
            val radius = (cornerPx() - inset).coerceIn(0f, minOf(w, h) / 2f)
            // Built by hand so it starts at the top centre and runs clockwise (a library round-rect starts elsewhere).
            outline.rewind()
            val cx = inset + w / 2f; val l = inset; val t = inset; val rt = inset + w; val bt = inset + h; val d = radius * 2f
            outline.moveTo(cx, t)
            outline.lineTo(rt - radius, t)
            outline.arcTo(androidx.compose.ui.geometry.Rect(rt - d, t, rt, t + d), -90f, 90f, false)
            outline.lineTo(rt, bt - radius)
            outline.arcTo(androidx.compose.ui.geometry.Rect(rt - d, bt - d, rt, bt), 0f, 90f, false)
            outline.lineTo(l + radius, bt)
            outline.arcTo(androidx.compose.ui.geometry.Rect(l, bt - d, l + d, bt), 90f, 90f, false)
            outline.lineTo(l, t + radius)
            outline.arcTo(androidx.compose.ui.geometry.Rect(l, t, l + d, t + d), 180f, 90f, false)
            outline.close()
            drawContent()
            measure.setPath(outline, false)
            drawPath(outline, ring.color.copy(alpha = .22f), style = Stroke(strokePx))
            IslandRingLogic.arc(measure.length, ring.fraction)?.let { range ->
                piece.rewind()
                if (measure.getSegment(range.start, range.endInclusive, piece, true))
                    drawPath(piece, ring.color, style = Stroke(strokePx, cap = StrokeCap.Round))
            }
        }
    }
