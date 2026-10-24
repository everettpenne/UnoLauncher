package com.jake.duolauncher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.jake.duolauncher.appearance.GlassButton
import com.jake.duolauncher.island.GridCalibration
import com.jake.duolauncher.island.StatusBarCalibration
import com.jake.duolauncher.island.StatusBarGrid

/**
 * "Line up with the status bar": the island keeps its edges in the gaps between the status bar's icons (see StatusBarGrid), using spacing
 * measured on a Pixel 10a. This page draws guides where it thinks the gaps are, over the real status bar, and lets the person move them until
 * every guide sits between two icons; the island uses the result.
 */
@Composable
internal fun StatusBarCalibrationSettings() {
    val context = LocalContext.current
    StatusBarCalibration.init(context)
    val cal = StatusBarCalibration.current
    DisposableEffect(Unit) { StatusBarCalibration.guidesShown = true; onDispose { StatusBarCalibration.guidesShown = false } }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // Automatic alignment: the optional status bar reader (see island/StatusBarReader.kt). While it has a reading, the guides below are not used.
    val reading = com.jake.duolauncher.island.StatusBarLive.reading
    val running = com.jake.duolauncher.island.StatusBarReaderService.isConnected()
    val enabled = remember(running) { com.jake.duolauncher.island.StatusBarReaderService.isEnabled(context) }
    Text(stringResource(R.string.status_auto_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.status_auto_note), style = MaterialTheme.typography.bodySmall, color = muted)
    Text(when {
            running && reading != null -> androidx.compose.ui.res.pluralStringResource(R.plurals.status_auto_reading, reading.icons.size, reading.icons.size)
            running -> stringResource(R.string.status_auto_waiting)
            enabled -> stringResource(R.string.status_auto_blocked)
            else -> stringResource(R.string.status_auto_off)
        }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("status-auto-state"))
    GlassButton(stringResource(R.string.status_auto_open), {
        runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }, Modifier.fillMaxWidth().heightIn(min = 48.dp), tag = "status-auto-open")
    Spacer(Modifier.height(8.dp))
    StatusBarTidySection()
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.status_manual_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.status_grid_intro), style = MaterialTheme.typography.bodySmall, color = muted)
    CalibrationStepper(stringResource(R.string.status_grid_left), cal.leftShift, "grid-left") { StatusBarCalibration.set(cal.copy(leftShift = it)) }
    CalibrationStepper(stringResource(R.string.status_grid_spacing), cal.pitchDelta, "grid-pitch") { StatusBarCalibration.set(cal.copy(pitchDelta = it)) }
    CalibrationStepper(stringResource(R.string.status_grid_right), cal.rightShift, "grid-right") { StatusBarCalibration.set(cal.copy(rightShift = it)) }
    GlassButton(stringResource(R.string.status_grid_reset), { StatusBarCalibration.set(GridCalibration()) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), tag = "grid-reset")
    StatusBarGuides(cal)
}

/** A label, then -1 -0.25 [value] +0.25 +1 (dp). */
@Composable
private fun CalibrationStepper(label: String, value: Float, tag: String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            GlassButton("\u22121", { onChange(value - 1f) }, Modifier.weight(1f).heightIn(min = 44.dp), tag = "$tag-m1")
            GlassButton("\u2212\u00bc", { onChange(value - .25f) }, Modifier.weight(1f).heightIn(min = 44.dp), tag = "$tag-mq")
            Text(String.format(java.util.Locale.ROOT, "%+.2f", value), Modifier.weight(1.3f).testTag("$tag-value"), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            GlassButton("+\u00bc", { onChange(value + .25f) }, Modifier.weight(1f).heightIn(min = 44.dp), tag = "$tag-pq")
            GlassButton("+1", { onChange(value + 1f) }, Modifier.weight(1f).heightIn(min = 44.dp), tag = "$tag-p1")
        }
    }
}

/** Thin lines over the status bar where the gaps should be: drawn in a popup pinned to the screen's top-left corner, under the status bar's icons. */
@Composable
private fun StatusBarGuides(cal: GridCalibration) {
    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val cal24 = android.text.format.DateFormat.is24HourFormat(context)
    val shift = StatusBarGrid.clockShiftDp(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR), cal24)
    val xs = StatusBarGrid.leftGaps(shift, cal).take(6) + StatusBarGrid.rightGaps(widthDp, cal)
    val corner = object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
    }
    Popup(popupPositionProvider = corner, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
        Canvas(Modifier.fillMaxWidth().height(52.dp)) {
            xs.forEach { x -> drawLine(Color(0xFFFF2D9B), androidx.compose.ui.geometry.Offset(x.dp.toPx(), 0f), androidx.compose.ui.geometry.Offset(x.dp.toPx(), size.height), strokeWidth = 1.5.dp.toPx()) }
        }
    }
}

/**
 * Android's status bar can hide some of its icons through the hidden icon list (the `icon_blacklist` secure setting, read by SystemUI's tuner
 * and kept by GrapheneOS). A launcher cannot write it without a system permission, but one adb command from a computer can. This builds that
 * command from the icons the person picks, with the command that undoes it. Pure, so it is unit-tested.
 */
internal object StatusBarTidy {
    /** SystemUI's slot names, and what each is called here. */
    val SLOTS = listOf("alarm_clock" to "Alarm", "bluetooth" to "Bluetooth", "headset" to "Headphones", "rotate" to "Rotation",
        "volume" to "Vibrate or silent", "zen" to "Do Not Disturb", "nfc" to "NFC", "hotspot" to "Hotspot", "cast" to "Cast",
        "vpn" to "VPN", "managed_profile" to "Work profile", "data_saver" to "Data Saver")

    fun command(slots: Collection<String>): String? =
        slots.filter { s -> SLOTS.any { it.first == s } }.sortedBy { s -> SLOTS.indexOfFirst { it.first == s } }.takeIf { it.isNotEmpty() }
            ?.let { "adb shell settings put secure icon_blacklist ${it.joinToString(",")}" }

    const val RESET = "adb shell settings delete secure icon_blacklist"
}

@Composable
private fun StatusBarTidySection() {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var chosen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(listOf<String>()) }
    Text(stringResource(R.string.status_tidy_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.status_tidy_note), style = MaterialTheme.typography.bodySmall, color = muted)
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        StatusBarTidy.SLOTS.forEach { (slot, label) ->
            com.jake.duolauncher.appearance.GlassChip(slot in chosen, { chosen = if (slot in chosen) chosen - slot else chosen + slot },
                label = { Text(label) }, modifier = Modifier.testTag("tidy-$slot"))
        }
    }
    fun copy(text: String) {
        context.getSystemService(android.content.ClipboardManager::class.java)
            ?.setPrimaryClip(android.content.ClipData.newPlainText("adb command", text))
    }
    StatusBarTidy.command(chosen)?.let { cmd ->
        Text(cmd, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
            modifier = Modifier.testTag("tidy-command"))
        GlassButton(stringResource(R.string.status_tidy_copy), { copy(cmd) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), tag = "tidy-copy")
    }
    Text(stringResource(R.string.status_tidy_reset, StatusBarTidy.RESET), style = MaterialTheme.typography.bodySmall, color = muted)
}
