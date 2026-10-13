package com.jake.duolauncher.widgets

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*

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
        if (note.isBlank()) Text(androidx.compose.ui.res.stringResource(com.jake.duolauncher.R.string.tap_to_write_a_note), color = ink.soft(), fontSize = 13.sp, modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp))
        else Row(Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp)) {
            Box(Modifier.width(3.dp).fillMaxHeight().clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(widgetAccent(WidgetColors.Gold)))
            Column(Modifier.padding(start = 9.dp)) {
                // The first line reads as the title; the rest is the body.
                Text(note.lineSequence().first(), color = ink.primary, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                val body = note.lineSequence().drop(1).joinToString("\n").trim()
                if (body.isNotEmpty()) Text(body, color = ink.primary.copy(alpha = .82f), fontSize = 12.sp, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
    if (editing) {
        var draft by remember { mutableStateOf(note) }
        GlassDialog(onDismissRequest = { editing = false }, title = "Note", actions = {
            GlassButton("Cancel", { editing = false })
            GlassButton("Done", { WidgetData.set(slot, "note", draft); editing = false }, prominent = true, tag = "note-save")
        }) {
            GlassTextField(draft, { draft = WidgetText.note(it) }, minHeight = 140.dp, maxLines = 10, tag = "note-field")
            GlassNote("${draft.length} / ${WidgetText.MAX_NOTE}. Kept on this phone only.")
        }
    }
}
