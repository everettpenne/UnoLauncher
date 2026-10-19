package com.jake.duolauncher.widgets

import androidx.compose.ui.graphics.drawscope.clipPath
import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import java.time.ZoneOffset

/** The Moon widget: its face (the drawing) and its own helpers. Registered in [UnoWidgets]. */

@Composable
internal fun MoonFace(slot: Int, onOptions: () -> Unit) {
    val now = rememberNow(10 * 60_000L)
    val at = now.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC)
    // The picker's preview shows a fixed, photogenic gibbous moon rather than tonight's.
    val previewing = LocalWidgetPreview.current
    val cycle = if (previewing) SAMPLE_CYCLE else MoonPhase.cycle(at)
    val litFraction = if (previewing) SAMPLE_LIT else MoonPhase.illumination(at)
    GlassCard(onClick = onOptions, modifier = Modifier.testTag("uno-moon-$slot")) {
        val ink = LocalGlassInk.current
        CardTitle(Icons.Rounded.Bedtime, "Moon")
        val haloColor = widgetAccent(WidgetColors.Gold)
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxHeight().aspectRatio(1f)) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                // A soft halo, then the dark side as a faint sphere.
                drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(haloColor.copy(alpha = .22f), Color.Transparent), c, r * 1.55f), r * 1.55f, c)
                drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(ink.soft(.34f), ink.soft(.16f)), c - Offset(r * .3f, r * .3f), r * 1.4f), r, c)
                drawCircle(ink.soft(.45f), r, c, style = Stroke(1.dp.toPx()))
                val k = cos(2.0 * Math.PI * cycle).toFloat()
                val rx = r * kotlin.math.abs(k)
                val lit = Path().apply {
                    arcTo(Rect(c.x - r, c.y - r, c.x + r, c.y + r), -90f, 180f, true)
                    arcTo(Rect(c.x - rx, c.y - r, c.x + rx, c.y + r), 90f, if (k > 0f) -180f else 180f, false)
                    close()
                }
                // The lit side is on the right while the moon waxes and on the left while it wanes.
                scale(if (MoonPhase.waxing(cycle)) 1f else -1f, 1f, pivot = c) {
                    drawPath(lit, androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFFFFFCEB), Color(0xFFE9E3C4)), c + Offset(r * .25f, -r * .25f), r * 1.5f), style = Fill)
                    // A few soft seas on the lit part, so it reads as a moon and not a white shape.
                    clipPath(lit) {
                        for ((dx, dy, rr) in listOf(Triple(.12f, -.28f, .22f), Triple(-.18f, .12f, .17f), Triple(.30f, .22f, .12f)))
                            drawCircle(Color(0xFFB9B294).copy(alpha = .30f), r * rr, c + Offset(r * dx, r * dy))
                    }
                }
            }
        }
        Text(MoonPhase.name(cycle), color = ink.primary, style = UnoType.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), maxLines = 1, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("${(litFraction * 100).toInt()}% lit", color = ink.soft(), style = UnoType.caption2, maxLines = 1,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        // What is coming: the nearer of the next full and the next new moon.
        Text(MoonPhase.nextText(MoonPhase.next(at)), color = ink.soft(), fontSize = 10.sp, maxLines = 1,
            modifier = Modifier.align(Alignment.CenterHorizontally).testTag("moon-next-$slot"))
    }
}

private fun cos(x: Double) = kotlin.math.cos(x)

/** The preview's moon: a waxing gibbous, part lit, that always looks good. */
private const val SAMPLE_CYCLE = 0.35
private const val SAMPLE_LIT = 0.794
