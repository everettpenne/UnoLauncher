package com.jake.duolauncher.appearance

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The base glyphs for hyper glass icons ([HyperGlassIcon]), drawn here as vectors on a 24 by 24 grid. They are never shown as pictures on
 * top of the glass: each is drawn once into the texture the glass shader reads, which is what lets the glass bend it. Their own colour
 * does not matter (the icon's glyph colour replaces it).
 */
internal object HyperGlyphs {
    private fun glyph(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(build).build()

    private fun ImageVector.Builder.fill(evenOdd: Boolean = false, block: PathBuilder.() -> Unit) =
        path(fill = SolidColor(Color.White), pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero, pathBuilder = block)

    private fun ImageVector.Builder.line(width: Float = 2.2f, block: PathBuilder.() -> Unit) =
        path(stroke = SolidColor(Color.White), strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, true, true, cx + r, cy)
        arcTo(r, r, 0f, true, true, cx - r, cy)
        close()
    }

    private fun PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, c: Float) {
        moveTo(l + c, t); lineTo(r - c, t); arcTo(c, c, 0f, false, true, r, t + c)
        lineTo(r, b - c); arcTo(c, c, 0f, false, true, r - c, b)
        lineTo(l + c, b); arcTo(c, c, 0f, false, true, l, b - c)
        lineTo(l, t + c); arcTo(c, c, 0f, false, true, l + c, t); close()
    }

    /** A speech bubble with its tail at the bottom left. */
    val Messages = glyph("Messages") {
        fill {
            roundRect(2f, 3f, 22f, 17.5f, 5f)
            moveTo(6f, 16f); lineTo(4.2f, 22f); lineTo(11.5f, 17f); close()
        }
    }

    /** A camera: the body with a bump for the viewfinder, and the lens as a ring cut out of it. */
    val Camera = glyph("Camera") {
        fill(evenOdd = true) {
            roundRect(2f, 6.5f, 22f, 20.5f, 3.5f)
            circle(12f, 13.5f, 4.6f)
        }
        // Its own path: in the body's even-odd fill, the strip where the two overlap would be cut out.
        fill { moveTo(8f, 7f); lineTo(9.6f, 3.8f); lineTo(14.4f, 3.8f); lineTo(16f, 7f); close() }
        fill { circle(12f, 13.5f, 2.5f) }
    }

    /** Two notes joined by a beam. */
    val Music = glyph("Music") {
        fill {
            circle(7f, 18f, 3.2f)
            circle(17.5f, 16f, 3.2f)
            moveTo(8.2f, 18f); lineTo(8.2f, 5.6f); lineTo(20.7f, 3f); lineTo(20.7f, 16f); lineTo(18.7f, 16f); lineTo(18.7f, 7f); lineTo(10.2f, 8.8f); lineTo(10.2f, 18f); close()
        }
    }

    /** A clock: a ring with its two hands. */
    val Clock = glyph("Clock") {
        fill(evenOdd = true) { circle(12f, 12f, 10f); circle(12f, 12f, 7.8f) }
        line { moveTo(12f, 6.8f); lineTo(12f, 12f); lineTo(15.8f, 14.4f) }
    }

    /** An envelope, its flap as a line across the front. */
    val Mail = glyph("Mail") {
        fill(evenOdd = true) { roundRect(2f, 4.5f, 22f, 19.5f, 3.2f); roundRect(4.1f, 6.6f, 19.9f, 17.4f, 1.4f) }
        line(2.1f) { moveTo(4.4f, 7.4f); lineTo(12f, 13.2f); lineTo(19.6f, 7.4f) }
    }

    /** A compass: a ring with a needle across it. */
    val Compass = glyph("Compass") {
        fill(evenOdd = true) { circle(12f, 12f, 10f); circle(12f, 12f, 7.9f) }
        fill { moveTo(16.4f, 7.6f); lineTo(13.5f, 13.5f); lineTo(7.6f, 16.4f); lineTo(10.5f, 10.5f); close() }
    }

    /** A play triangle with softened corners. */
    val Play = glyph("Play") {
        fill { moveTo(7f, 4.2f); lineTo(20.5f, 12f); lineTo(7f, 19.8f); close() }
        line(2.4f) { moveTo(7f, 4.2f); lineTo(20.5f, 12f); lineTo(7f, 19.8f); close() }
    }

    /** A handset, as one thick curved stroke with a pad at each end. */
    val Phone = glyph("Phone") {
        line(4.6f) { moveTo(6.2f, 5.2f); curveTo(6.2f, 13.2f, 10.8f, 17.8f, 18.8f, 17.8f) }
        fill { circle(6.2f, 5.6f, 3.1f); circle(18.4f, 17.8f, 3.1f) }
    }

    val all = listOf(Phone, Messages, Camera, Music, Clock, Mail, Compass, Play)
}
