package com.jake.duolauncher.widgets

import com.jake.duolauncher.*
import androidx.compose.foundation.layout.*
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
    val target = Countdown.parse(WidgetData.get(slot, "date"))
    var editing by remember { mutableStateOf(false) }
    GlassCard(onClick = { editing = true }, modifier = Modifier.testTag("uno-countdown-$slot")) {
        val ink = LocalGlassInk.current
        CardTitle(Icons.Rounded.Event, name)
        if (target == null) Text("Tap to set a date", color = ink.soft(), fontSize = 13.sp)
        else {
            val (big, unit) = Countdown.label(today, target)
            Column {
                Text(big, color = ink.primary, fontSize = 38.sp, fontWeight = FontWeight.Light, lineHeight = 40.sp, maxLines = 1)
                if (unit.isNotEmpty()) Text(unit, color = ink.soft(), fontSize = 13.sp)
            }
            Text(target.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")), color = ink.soft(), fontSize = 11.sp, maxLines = 1)
        }
    }
    if (editing) {
        var draftName by remember { mutableStateOf(name) }
        var draftDate by remember { mutableStateOf(target) }
        var picking by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { editing = false }, title = { Text("Countdown") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(draftName, { draftName = WidgetText.name(it) }, Modifier.fillMaxWidth().testTag("countdown-name"),
                    singleLine = true, label = { Text("Name") })
                OutlinedButton(onClick = { picking = true }, Modifier.fillMaxWidth().testTag("countdown-date")) {
                    Text(draftDate?.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")) ?: "Choose a date")
                }
            }
        }, confirmButton = { TextButton(onClick = {
            WidgetData.set(slot, "name", draftName.ifBlank { "Countdown" })
            WidgetData.set(slot, "date", draftDate?.toString().orEmpty())
            editing = false
        }, Modifier.testTag("countdown-save")) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } })
        if (picking) {
            val state = rememberDatePickerState(initialSelectedDateMillis = (draftDate ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            DatePickerDialog(onDismissRequest = { picking = false }, confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { draftDate = java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                picking = false
            }) { Text("OK") } }, dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } }) { DatePicker(state) }
        }
    }
}
