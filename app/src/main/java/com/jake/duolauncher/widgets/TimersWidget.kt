package com.jake.duolauncher.widgets

import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** The Timers widget: its face (the drawing) and its own helpers. Registered in [UnoWidgets]. */

@Composable
internal fun TimersFace(slot: Int, onOptions: () -> Unit) {
    val context = LocalContext.current
    // Ticks twice a second only while a timer runs; a widget that is just showing the buttons has nothing to redraw.
    val now by produceState(System.currentTimeMillis(), IslandTools.timerActive) {
        while (true) { value = System.currentTimeMillis(); delay(if (IslandTools.timerActive) 500 else 600_000) }
    }
    GlassCard(onClick = onOptions, modifier = Modifier.testTag("uno-timers-$slot")) {
        val ink = LocalGlassInk.current
        CardTitle(Icons.Rounded.Timer, "Timer")
        if (IslandTools.timerActive) {
            Text(IslandClock.countdown(IslandClock.remainingMs(now, IslandTools.timerEndAt)), color = ink.primary, fontSize = 34.sp,
                fontWeight = FontWeight.Light, maxLines = 1, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("timer-remaining"))
            Box(Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(50)).background(ink.soft(.2f))
                .clickable { IslandTools.cancelTimer(context) }.padding(horizontal = 18.dp, vertical = 6.dp).testTag("timer-stop")) {
                Text(stringResource(com.jake.duolauncher.R.string.stop), color = ink.primary, fontSize = 13.sp)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TimerPresets.MINUTES.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { minutes ->
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(ink.soft(.18f))
                                .clickable { IslandTools.startTimer(context, minutes * 60_000L) }.padding(vertical = 8.dp)
                                .testTag("timer-start-$minutes"), contentAlignment = Alignment.Center) {
                                Text("$minutes min", color = ink.primary, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
