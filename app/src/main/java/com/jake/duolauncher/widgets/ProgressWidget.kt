package com.jake.duolauncher.widgets

import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** The Day, month and year widget: its face (the drawing) and its own helpers. Registered in [UnoWidgets]. */

@Composable
internal fun ProgressFace(slot: Int, onOptions: () -> Unit) {
    val progress = TimeProgress.of(rememberNow(60_000))
    GlassCard(onClick = onOptions, modifier = Modifier.testTag("uno-progress-$slot")) {
        val ink = LocalGlassInk.current
        CardTitle(Icons.Rounded.Timelapse, "${LocalDate.now().year} · ${progress.daysLeftInYear} days left", widgetAccent(WidgetColors.Teal))
        Column(verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterVertically), modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp)) {
            listOf(Triple("Day", progress.day, WidgetColors.Warm), Triple("Month", progress.month, WidgetColors.Teal),
                Triple("Year", progress.year, WidgetColors.Violet)).forEach { (label, fraction, hue) ->
                val colour = widgetAccent(hue).let { if (LocalWallpaperAccent.current != null) hue else it }
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(label, color = ink.primary, style = UnoType.caption2.copy(fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
                        Text("${(fraction * 100).toInt()}%", color = ink.soft(), style = UnoType.caption2)
                    }
                    Box(Modifier.padding(top = 3.dp).fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)).background(ink.soft(.2f))) {
                        Box(Modifier.fillMaxWidth(fraction.coerceIn(.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50))
                            .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(colour.copy(alpha = .55f), colour))))
                    }
                }
            }
        }
    }
}
