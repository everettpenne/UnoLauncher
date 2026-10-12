package com.jake.duolauncher.widgets

import com.jake.duolauncher.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The Note widget: its face (the drawing) and its own helpers. Registered in [UnoWidgets]. */

@Composable
internal fun NoteFace(slot: Int, onOptions: () -> Unit) {
    val note = WidgetData.get(slot, "note")
    var editing by remember { mutableStateOf(false) }
    GlassCard(onClick = { editing = true }, modifier = Modifier.testTag("uno-note-$slot")) {
        val ink = LocalGlassInk.current
        CardTitle(Icons.Rounded.EditNote, "Note")
        Text(note.ifBlank { "Tap to write a note" }, color = if (note.isBlank()) ink.soft() else ink.primary, fontSize = 13.sp,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp))
    }
    if (editing) {
        var draft by remember { mutableStateOf(note) }
        AlertDialog(onDismissRequest = { editing = false }, title = { Text("Note") }, text = {
            Column {
                OutlinedTextField(draft, { draft = WidgetText.note(it) }, Modifier.fillMaxWidth().heightIn(min = 140.dp).testTag("note-field"), maxLines = 10)
                Text("${draft.length} / ${WidgetText.MAX_NOTE}. Kept on this phone only.", fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }, confirmButton = { TextButton(onClick = { WidgetData.set(slot, "note", draft); editing = false }, Modifier.testTag("note-save")) { Text("Done") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } })
    }
}
