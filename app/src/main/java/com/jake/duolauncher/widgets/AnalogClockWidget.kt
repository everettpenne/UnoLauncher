package com.jake.duolauncher.widgets

import com.jake.duolauncher.*
import com.jake.duolauncher.appearance.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

/** The Analog clock widget: a dial with an hour hand and a minute hand, and today's date. Registered in [UnoWidgets]. */

@Composable
internal fun AnalogClockFace(slot: Int, onOptions: () -> Unit) {
    // Redraws on the minute, when the minute hand moves; there is no seconds hand to keep redrawing.
    val now by produceState(LocalDateTime.now()) {
        while (true) { value = LocalDateTime.now(); delay(AnalogClock.msToNextMinute(value.toLocalTime()) + 20L) }
    }
    GlassCard(onClick = onOptions, modifier = Modifier.testTag("uno-analog-clock-$slot")) {
        val ink = LocalGlassInk.current
        CardTitle(Icons.Rounded.Schedule, "Clock")
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            val hour = AnalogClock.hourAngle(now.toLocalTime())
            val minute = AnalogClock.minuteAngle(now.toLocalTime())
            val accentColor = widgetAccent(WidgetColors.Warm)
            val hourColor = ink.primary; val minuteColor = ink.primary; val tick = ink.soft(.6f); val rim = ink.soft(.35f)
            Canvas(Modifier.fillMaxHeight().aspectRatio(1f)) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(ink.soft(.16f), ink.soft(.06f)), c - Offset(r * .3f, r * .35f), r * 1.5f), r, c)
                drawCircle(rim, r, c, style = Stroke(1.dp.toPx()))
                // Twelve ticks, longer at 12, 3, 6 and 9.
                for (i in 0 until 12) {
                    val a = Math.toRadians(i * 30.0 - 90.0)
                    val long = i % 3 == 0
                    val from = r * (if (long) .82f else .88f); val to = r * .95f
                    drawLine(if (i == 0) accentColor else tick, Offset(c.x + cos(a).toFloat() * from, c.y + sin(a).toFloat() * from),
                        Offset(c.x + cos(a).toFloat() * to, c.y + sin(a).toFloat() * to),
                        strokeWidth = (if (long) 2.dp else 1.dp).toPx(), cap = StrokeCap.Round)
                }
                fun hand(angle: Float, length: Float, width: Float, color: androidx.compose.ui.graphics.Color) {
                    val a = Math.toRadians(angle - 90.0)
                    drawLine(color, c, Offset(c.x + cos(a).toFloat() * r * length, c.y + sin(a).toFloat() * r * length), strokeWidth = width, cap = StrokeCap.Round)
                }
                hand(hour, .5f, 4.dp.toPx(), hourColor)
                hand(minute, .74f, 2.5.dp.toPx(), minuteColor)
                drawCircle(accentColor, 4.dp.toPx(), c)
                drawCircle(androidx.compose.ui.graphics.Color.White.copy(alpha = .9f), 1.5.dp.toPx(), c)
            }
        }
        Text(now.format(DateTimeFormatter.ofPattern("EEE, MMM d")), color = ink.soft(), style = UnoType.caption2, maxLines = 1,
            modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}
