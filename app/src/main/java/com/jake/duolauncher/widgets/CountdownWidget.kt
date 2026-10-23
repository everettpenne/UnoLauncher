package com.jake.duolauncher.widgets

import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** The Countdown widget: its face (the drawing) and its own helpers. Registered in [UnoWidgets]. */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CountdownFace(slot: Int, onOptions: () -> Unit) {
    val today = rememberNow(60_000).toLocalDate()
    val name = WidgetData.get(slot, "name", "Countdown")
    val set = Countdown.parse(WidgetData.get(slot, "date"))
    val yearly = WidgetData.get(slot, "yearly") == "1"
    // A yearly countdown (a birthday) rolls on to the next time its day comes round; the date shown is that next one.
    val target = set?.let { Countdown.target(today, it, yearly) }
    var editing by remember { mutableStateOf(false) }
    GlassCard(onClick = { editing = true }, modifier = Modifier.testTag("uno-countdown-$slot")) {
        val ink = LocalGlassInk.current
        CardTitle(Icons.Rounded.Event, name, widgetAccent(WidgetColors.Warm))
        if (target == null) Text(stringResource(com.jake.duolauncher.R.string.tap_to_set_a_date), color = ink.soft(), style = UnoType.footnote)
        else {
            val (big, unit) = Countdown.label(today, target)
            Column {
                Text(big, color = widgetAccent(WidgetColors.Warm), style = WidgetType.number(42.sp), maxLines = 1)
                if (unit.isNotEmpty()) Text(unit, color = ink.soft(), style = UnoType.footnote)
            }
            Text(target.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")) + if (yearly) " · every year" else "", color = ink.soft(), style = UnoType.caption2, maxLines = 1)
        }
    }
    if (editing) {
        var draftName by remember { mutableStateOf(name) }
        var draftDate by remember { mutableStateOf(set) }
        var draftYearly by remember { mutableStateOf(yearly) }
        var picking by remember { mutableStateOf(false) }
        GlassDialog(onDismissRequest = { editing = false }, title = "Countdown", actions = {
            GlassButton("Cancel", { editing = false })
            GlassButton("Save", {
                WidgetData.set(slot, "name", draftName.ifBlank { "Countdown" })
                WidgetData.set(slot, "date", draftDate?.toString().orEmpty())
                WidgetData.set(slot, "yearly", if (draftYearly) "1" else "")
                editing = false
            }, prominent = true, tag = "countdown-save")
        }) {
            GlassTextField(draftName, { draftName = WidgetText.name(it) }, placeholder = "Name", singleLine = true, tag = "countdown-name")
            GlassButton(draftDate?.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")) ?: "Choose a date", { picking = true },
                Modifier.fillMaxWidth(), tag = "countdown-date")
            GlassSwitchRow("Repeat every year", draftYearly, { draftYearly = it }, tag = "countdown-yearly")
        }
        if (picking) GlassDatePickerDialog(initial = draftDate ?: today, onPicked = { draftDate = it; picking = false }, onDismiss = { picking = false })
    }
}
