package com.jake.duolauncher.widgets

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
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** The Day, month and year widget: its face (the drawing) and its own helpers. Registered in [UnoWidgets]. */

@Composable
internal fun ProgressFace(slot: Int, onOptions: () -> Unit) {
    val progress = TimeProgress.of(rememberNow(60_000))
    GlassCard(onClick = onOptions, modifier = Modifier.testTag("uno-progress-$slot")) {
        val ink = LocalGlassInk.current
        CardTitle(Icons.Rounded.Timelapse, "${LocalDate.now().year} · ${progress.daysLeftInYear} days left")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f).padding(top = 6.dp)) {
            listOf("Day" to progress.day, "Month" to progress.month, "Year" to progress.year).forEach { (label, fraction) ->
                Column {
                    Row { Text(label, color = ink.primary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text("${(fraction * 100).toInt()}%", color = ink.soft(), fontSize = 11.sp) }
                    LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                        color = ink.primary, trackColor = ink.soft(.22f), gapSize = 0.dp, drawStopIndicator = {})
                }
            }
        }
    }
}
