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
        val accent = widgetAccent(WidgetColors.Rose)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(month.format(DateTimeFormatter.ofPattern("MMMM")), color = ink.primary, style = UnoType.footnote.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
            Text("\u00A0${month.year}", color = ink.soft(), style = UnoType.caption2, maxLines = 1)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            MonthGrid.headers(first).forEach { day ->
                val weekend = day == java.time.DayOfWeek.SATURDAY || day == java.time.DayOfWeek.SUNDAY
                Text(day.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()), color = ink.soft(if (weekend) .45f else .7f), fontSize = 9.sp,
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
                                Box(Modifier.size(17.dp).then(if (isToday) Modifier.background(accent, CircleShape) else Modifier),
                                    contentAlignment = Alignment.Center) {
                                    Text(day.toString(), color = if (isToday) androidx.compose.ui.graphics.Color.White else ink.primary, fontSize = 9.sp, lineHeight = 10.sp,
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
