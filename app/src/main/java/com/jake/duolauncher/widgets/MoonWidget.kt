package com.jake.duolauncher.widgets

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
    val cycle = MoonPhase.cycle(at)
    GlassCard(onClick = onOptions, modifier = Modifier.testTag("uno-moon-$slot")) {
        val ink = LocalGlassInk.current
        CardTitle(Icons.Rounded.Bedtime, "Moon")
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxHeight().aspectRatio(1f)) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(ink.soft(.28f), r, c)
                drawCircle(ink.soft(.5f), r, c, style = Stroke(1.dp.toPx()))
                val k = cos(2.0 * Math.PI * cycle).toFloat()
                val rx = r * kotlin.math.abs(k)
                val lit = Path().apply {
                    arcTo(Rect(c.x - r, c.y - r, c.x + r, c.y + r), -90f, 180f, true)
                    arcTo(Rect(c.x - rx, c.y - r, c.x + rx, c.y + r), 90f, if (k > 0f) -180f else 180f, false)
                    close()
                }
                // The lit side is on the right while the moon waxes and on the left while it wanes.
                scale(if (MoonPhase.waxing(cycle)) 1f else -1f, 1f, pivot = c) { drawPath(lit, Color(0xFFF4F1DE), style = Fill) }
            }
        }
        Text(MoonPhase.name(cycle), color = ink.primary, fontSize = 12.sp, maxLines = 1, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("${(MoonPhase.illumination(at) * 100).toInt()}% lit", color = ink.soft(), fontSize = 11.sp, maxLines = 1,
            modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

private fun cos(x: Double) = kotlin.math.cos(x)
