package com.jake.duolauncher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jake.duolauncher.appearance.GlassButton
import com.jake.duolauncher.island.*
import kotlinx.coroutines.launch

/** The activities and dots that can be forced on, with the words the page uses for them. */
private val FORCEABLE = listOf(
    LiveKind.CALL to "Dummy call", LiveKind.TIMER to "Dummy timer", LiveKind.STOPWATCH to "Dummy stopwatch", LiveKind.MEDIA to "Dummy music",
    LiveKind.UPDATE to "Dummy live update", LiveKind.CAMERA to "Camera dot", LiveKind.MIC to "Microphone dot", LiveKind.RECORDING to "Screen recording dot",
)

/**
 * The island debug page: dummy live activities forced on, and a hand-set width and position for the main island and each capsule, with the
 * numbers shown (and copyable) so they can be read out as a default. Nothing here is used until a value is changed or something is forced.
 */
@Composable
internal fun IslandDebugSettings() {
    IslandDebug.init(LocalContext.current)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val note = MaterialTheme.typography.bodySmall
    Text(stringResource(R.string.island_debug_force_intro), style = note, color = muted)
    FORCEABLE.forEach { (kind, label) ->
        SettingsSwitch(label, kind in IslandDebug.forced, { IslandDebug.force(kind, it) }, "debug-force-${kind.name.lowercase()}")
    }
    HorizontalDividerSpace()
    Text(stringResource(R.string.island_debug_adjust_intro), style = note, color = muted)
    DebugTarget.entries.forEach { target ->
        val g = IslandDebug.group(target)
        val tag = "debug-${target.key}"
        Text(target.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        DebugStepper("Width", g.dw, "$tag-w") { IslandDebug.set(target, g.copy(dw = it)) }
        DebugStepper(if (target == DebugTarget.PILL) "Across (+ is right)" else if (target == DebugTarget.LEFT) "Gap from island (+ is further left)" else "Gap from island (+ is further right)",
            g.dx, "$tag-x") { IslandDebug.set(target, g.copy(dx = it)) }
        DebugStepper("Down (+ is lower)", g.dy, "$tag-y") { IslandDebug.set(target, g.copy(dy = it)) }
    }
    HorizontalDividerSpace()
    Text(stringResource(R.string.island_debug_numbers_intro), style = note, color = muted)
    SelectionContainer { Text(IslandDebug.summary(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("debug-summary")) }
    val clipboard = androidx.compose.ui.platform.LocalClipboard.current
    val clipScope = androidx.compose.runtime.rememberCoroutineScope()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        GlassButton("Copy numbers", { clipScope.launch { clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(android.content.ClipData.newPlainText("", IslandDebug.summary()))) } }, Modifier.weight(1f).heightIn(min = 48.dp), tag = "debug-copy")
        GlassButton("Reset all", { IslandDebug.reset() }, Modifier.weight(1f).heightIn(min = 48.dp), tag = "debug-reset")
    }
}

@Composable private fun HorizontalDividerSpace() { Spacer(Modifier.height(6.dp)) }

/** A label, then −5 −1 [value] +1 +5, so a value can be walked to where it looks right and read off. */
@Composable
private fun DebugStepper(label: String, value: Float, tag: String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            GlassButton("−5", { onChange(value - 5f) }, Modifier.weight(1f).heightIn(min = 44.dp), tag = "$tag-m5")
            GlassButton("−1", { onChange(value - 1f) }, Modifier.weight(1f).heightIn(min = 44.dp), tag = "$tag-m1")
            Text(shown(value), Modifier.weight(1.2f).testTag("$tag-value"), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            GlassButton("+1", { onChange(value + 1f) }, Modifier.weight(1f).heightIn(min = 44.dp), tag = "$tag-p1")
            GlassButton("+5", { onChange(value + 5f) }, Modifier.weight(1f).heightIn(min = 44.dp), tag = "$tag-p5")
        }
    }
}

private fun shown(v: Float): String = if (v == v.toInt().toFloat()) "%+d".format(v.toInt()).let { if (v == 0f) "0" else it } else "%+.1f".format(v)
