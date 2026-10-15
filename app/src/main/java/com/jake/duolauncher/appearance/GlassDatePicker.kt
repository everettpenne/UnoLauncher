package com.jake.duolauncher.appearance

import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** The days of a month laid out in week rows, kept pure so it is tested. */
internal object MonthGrid {
    /** [month]'s days in rows of seven, starting each week on [firstDay]; null pads the days outside the month. */
    fun weeks(month: YearMonth, firstDay: DayOfWeek): List<List<LocalDate?>> {
        val lead = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
        val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        return cells.chunked(7).map { week -> week + List(7 - week.size) { null } }
    }

    /** The seven weekday initials, starting at [firstDay]. */
    fun weekdayLabels(firstDay: DayOfWeek, locale: Locale = Locale.getDefault()): List<String> =
        (0 until 7).map { firstDay.plus(it.toLong()).getDisplayName(TextStyle.NARROW, locale) }

    /** [month] moved by [delta] months, never before year 1900 or after 2200 (a bound a countdown never needs to pass). */
    fun shift(month: YearMonth, delta: Long): YearMonth = month.plusMonths(delta).let {
        when { it.year < 1900 -> YearMonth.of(1900, 1); it.year > 2200 -> YearMonth.of(2200, 12); else -> it }
    }
}

/** A glass date picker: month and year arrows, a week grid, and Cancel / OK. [onPicked] gets the chosen day. */
@Composable
internal fun GlassDatePickerDialog(initial: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit, today: LocalDate = LocalDate.now()) {
    var month by remember { mutableStateOf(YearMonth.from(initial)) }
    var chosen by remember { mutableStateOf(initial) }
    val firstDay = java.time.temporal.WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val (accent, accentInk) = run { val a = LocalWallpaperAccent.current; (a?.accent ?: com.jake.duolauncher.panel.IosOrange) to (a?.ink ?: androidx.compose.ui.graphics.Color.Black) }
    GlassDialog(onDismissRequest = onDismiss, title = "Choose a date", actions = {
        GlassButton("Cancel", onDismiss)
        GlassButton("OK", { onPicked(chosen) }, prominent = true, tag = "date-ok")
    }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(onClick = { month = MonthGrid.shift(month, -12) }, modifier = Modifier.testTag("date-prev-year")) { Text(stringResource(com.jake.duolauncher.R.string.text)) }
            GlassIconButton(onClick = { month = MonthGrid.shift(month, -1) }, modifier = Modifier.testTag("date-prev-month")) { Text(stringResource(com.jake.duolauncher.R.string.text_2)) }
            Text("${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}", Modifier.weight(1f).testTag("date-title"),
                style = UnoType.callout.copy(fontWeight = FontWeight.Medium), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            GlassIconButton(onClick = { month = MonthGrid.shift(month, 1) }, modifier = Modifier.testTag("date-next-month")) { Text(stringResource(com.jake.duolauncher.R.string.text_3)) }
            GlassIconButton(onClick = { month = MonthGrid.shift(month, 12) }, modifier = Modifier.testTag("date-next-year")) { Text(stringResource(com.jake.duolauncher.R.string.text_4)) }
        }
        Row(Modifier.fillMaxWidth()) {
            MonthGrid.weekdayLabels(firstDay).forEach { Text(it, Modifier.weight(1f), style = UnoType.caption1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        MonthGrid.weeks(month, firstDay).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            val on = day == chosen
                            Box(Modifier.size(40.dp).clip(CircleShape)
                                .background(if (on) accent else androidx.compose.ui.graphics.Color.Transparent)
                                .then(if (day == today && !on) Modifier.border(BorderStroke(1.dp, accent), CircleShape) else Modifier)
                                .clickable(role = Role.Button) { chosen = day }
                                .semantics { contentDescription = day.toString() }.testTag("date-day-$day"),
                                contentAlignment = Alignment.Center) {
                                Text(day.dayOfMonth.toString(), color = if (on) accentInk else androidx.compose.ui.graphics.Color.Unspecified,
                                    style = UnoType.subheadline.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal))
                            }
                        }
                    }
                }
            }
        }
    }
}
