package com.jake.duolauncher.widgets

import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/** The Month widget: its face (the drawing) and its own helpers. Registered in [UnoWidgets]. */

@Composable
internal fun MonthFace(slot: Int, onOptions: () -> Unit) {
    val today = rememberNow(60_000).toLocalDate()
    val month = YearMonth.from(today)
    val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val cells = remember(month, first) { MonthGrid.cells(month, first) }
    GlassCard(onClick = onOptions, padding = 10.dp, modifier = Modifier.testTag("uno-month-$slot")) {
        val ink = LocalGlassInk.current
        Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), color = ink.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            MonthGrid.headers(first).forEach { day ->
                Text(day.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()), color = ink.soft(.6f), fontSize = 9.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth()) {
            cells.chunked(7).forEach { week ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    week.forEach { day ->
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            if (day != null) {
                                val isToday = day == today.dayOfMonth
                                Box(Modifier.size(15.dp).then(if (isToday) Modifier.background(ink.primary, CircleShape) else Modifier),
                                    contentAlignment = Alignment.Center) {
                                    Text(day.toString(), color = if (isToday) onInk(ink) else ink.primary, fontSize = 9.sp, lineHeight = 10.sp,
                                        fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
