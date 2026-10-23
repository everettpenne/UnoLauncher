package com.jake.duolauncher.widgets

import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The Checklist widget: its face (the drawing) and its own helpers. Registered in [UnoWidgets].
 *
 * A small to-do list kept on this phone. Tap a task to tick it off (done ones are struck through and can be cleared
 * from the add sheet); tap anywhere else to add one. The list lives in [WidgetData], is pruned when the widget leaves
 * Home, and travels with settings backups. Nothing is read or sent anywhere.
 */
@Composable
internal fun ChecklistFace(slot: Int, onOptions: () -> Unit) {
    val items = Checklist.parse(WidgetData.get(slot, "items"))
    var adding by remember { mutableStateOf(false) }
    val accent = widgetAccent(WidgetColors.Blue)
    GlassCard(onClick = { adding = true }, modifier = Modifier.testTag("uno-checklist-$slot")) {
        val ink = LocalGlassInk.current
        val (done, total) = Checklist.doneCount(items)
        CardTitle(Icons.Rounded.CheckCircle, "Checklist", widgetAccent(WidgetColors.Blue))
        if (items.isEmpty()) {
            Text(stringResource(com.jake.duolauncher.R.string.tap_to_add_a_task), color = ink.soft(), style = UnoType.footnote)
        } else {
            Text("$done of $total done", color = ink.soft(), style = UnoType.caption2, maxLines = 1)
            Column(Modifier.weight(1f).fillMaxWidth().padding(top = 3.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                items.take(Checklist.SHOWN).forEachIndexed { index, item ->
                    Row(Modifier.fillMaxWidth().clip(Corner.medium)
                        .clickable { WidgetData.set(slot, "items", Checklist.serialize(Checklist.toggle(items, index))) }
                        .padding(vertical = 3.dp, horizontal = 2.dp).testTag("checklist-item-$index"),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (item.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            if (item.done) "Done" else "Not done",
                            tint = if (item.done) accent else ink.soft(), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(item.text, color = if (item.done) ink.soft() else ink.primary, style = UnoType.footnote, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (item.done) TextDecoration.LineThrough else null,
                            modifier = Modifier.weight(1f))
                    }
                }
                if (items.size > Checklist.SHOWN) {
                    Text("+${items.size - Checklist.SHOWN} more", color = ink.soft(), fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
    if (adding) {
        var text by remember { mutableStateOf("") }
        GlassDialog(onDismissRequest = { adding = false }, title = "Checklist", actions = {
            GlassButton("Done", { adding = false }, prominent = true, tag = "checklist-done")
        }) {
            GlassTextField(text, { text = it }, placeholder = "New task", singleLine = true, tag = "checklist-new-task")
            GlassButton("Add", {
                val next = Checklist.add(items, text)
                if (next != items) { WidgetData.set(slot, "items", Checklist.serialize(next)); text = "" }
            }, enabled = text.isNotBlank(), tag = "checklist-add")
            if (items.any { it.done }) GlassRow("Clear completed", onClick = {
                WidgetData.set(slot, "items", Checklist.serialize(Checklist.clearDone(items)))
            }, modifier = Modifier.testTag("checklist-clear-done"))
        }
    }
}

/** The Checklist widget's rules, kept apart from the drawing so each is unit-tested. */
internal object Checklist {
    const val MAX_ITEMS = 12
    const val SHOWN = 6
    const val MAX_TEXT = 40

    /** One task: its words and whether it is done. */
    data class Item(val text: String, val done: Boolean)

    /** "1|Done task\n0|Open task" style storage: the first character is the done flag, then a bar, then the words.
     *  Malformed and blank lines are skipped, and the list never holds more than [MAX_ITEMS]. */
    fun parse(raw: String?): List<Item> = raw.orEmpty().split('\n').mapNotNull { line ->
        if (line.length < 3 || line[1] != '|' || line[0] !in "01") return@mapNotNull null
        val text = line.substring(2).take(MAX_TEXT).trim()
        if (text.isEmpty()) null else Item(text, line[0] == '1')
    }.take(MAX_ITEMS)

    fun serialize(items: List<Item>): String = items.joinToString("\n") { (if (it.done) "1" else "0") + "|" + it.text }

    private fun clean(text: String): String = text.replace("\n", " ").replace("|", " ").trim().take(MAX_TEXT)

    fun toggle(items: List<Item>, index: Int): List<Item> =
        if (index in items.indices) items.mapIndexed { i, item -> if (i == index) item.copy(done = !item.done) else item } else items

    /** Adds a task, unless it is blank, a repeat, or the list is full; the words are tidied of newlines and bars first. */
    fun add(items: List<Item>, text: String): List<Item> {
        val cleaned = clean(text)
        if (cleaned.isEmpty() || items.size >= MAX_ITEMS || items.any { it.text.equals(cleaned, ignoreCase = true) }) return items
        return items + Item(cleaned, done = false)
    }

    fun removeAt(items: List<Item>, index: Int): List<Item> =
        if (index in items.indices) items.toMutableList().also { it.removeAt(index) } else items

    fun clearDone(items: List<Item>): List<Item> = items.filter { !it.done }

    /** (done, total), for the "3 of 5 done" line. */
    fun doneCount(items: List<Item>): Pair<Int, Int> = items.count { it.done } to items.size
}
