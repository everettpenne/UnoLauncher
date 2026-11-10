package com.jake.duolauncher.widgets

import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The Counter widget: its face (the drawing) and its own helpers. Registered in [UnoWidgets]. */

@Composable
internal fun CounterFace(slot: Int, onOptions: () -> Unit) {
    val count = WidgetData.getInt(slot, "count")
    val name = WidgetData.get(slot, "name", "Counter")
    var editing by remember { mutableStateOf(false) }
    GlassCard(onClick = { WidgetData.set(slot, "count", WidgetText.clampCount(count + 1L).toString()) },
        modifier = Modifier.testTag("uno-counter-$slot")) {
        val ink = LocalGlassInk.current
        // The take-one-away button sits in the corner without making the header row taller, so the name lines up with every other widget's.
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.padding(end = 30.dp).clip(Corner.small).clickable { editing = true }.testTag("counter-name")) { CardTitle(Icons.Rounded.Add, name, widgetAccent(WidgetColors.Blue)) }
            Box(Modifier.align(Alignment.CenterEnd).offset(x = 4.dp).size(26.dp).clip(CircleShape).background(ink.soft(.18f))
                .clickable { WidgetData.set(slot, "count", WidgetText.clampCount(count - 1L).toString()) }.testTag("counter-minus"),
                contentAlignment = Alignment.Center) { Text(stringResource(com.jake.duolauncher.R.string.text_6), color = ink.primary, style = UnoType.callout) }
        }
        Text(count.toString(), color = ink.primary, style = WidgetType.hero, maxLines = 1,
            modifier = Modifier.align(Alignment.CenterHorizontally).testTag("counter-value"))
        Text(stringResource(com.jake.duolauncher.R.string.tap_to_add_one), color = ink.soft(), style = WidgetType.caption, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
    if (editing) {
        var draft by remember { mutableStateOf(name) }
        GlassDialog(onDismissRequest = { editing = false }, title = "Counter", actions = {
            GlassButton("Reset to 0", { WidgetData.set(slot, "count", "0"); editing = false })
            GlassButton("Cancel", { editing = false })
            GlassButton("Save", { WidgetData.set(slot, "name", draft.ifBlank { "Counter" }); editing = false }, prominent = true)
        }) {
            GlassTextField(draft, { draft = WidgetText.name(it) }, placeholder = "Name", singleLine = true)
        }
    }
}
