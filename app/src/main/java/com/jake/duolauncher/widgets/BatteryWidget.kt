package com.jake.duolauncher.widgets

import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The Battery widget: its face (the drawing) and its own helpers. Registered in [UnoWidgets]. */

private data class BatteryReading(val level: Int, val charging: Boolean, val toFull: String?)

/** A steady sample for the picker's preview, rather than whatever the phone happens to be at. */
private val SAMPLE_READING = BatteryReading(78, charging = true, toFull = "1 h 12 min to full")

private fun readBattery(context: Context): BatteryReading {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
    val toFull = if (charging && status != BatteryManager.BATTERY_STATUS_FULL)
        runCatching { ChargeText.toFull(context.getSystemService(BatteryManager::class.java).computeChargeTimeRemaining()) }.getOrNull() else null
    return BatteryReading(percent, charging, toFull)
}

@Composable
internal fun BatteryFace(slot: Int, onOptions: () -> Unit) {
    val context = LocalContext.current
    val live by produceState(BatteryReading(-1, false, null)) {
        while (true) { value = withContext(Dispatchers.Default) { readBattery(context) }; delay(30_000) }
    }
    val reading = if (LocalWidgetPreview.current) SAMPLE_READING else live
    GlassCard(onClick = onOptions, modifier = Modifier.testTag("uno-battery-$slot")) {
        val ink = LocalGlassInk.current
        CardTitle(Icons.Rounded.BatteryChargingFull, "Battery")
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            val tint = when { reading.charging -> widgetAccent(WidgetColors.Green); reading.level in 0..20 -> widgetAccent(WidgetColors.Warm); else -> ink.primary }
            Canvas(Modifier.fillMaxHeight().aspectRatio(1f)) {
                val stroke = 8.dp.toPx()
                val inset = stroke * .9f
                val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                val topLeft = Offset(inset, inset)
                drawArc(ink.soft(.20f), -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                if (reading.level >= 0) {
                    val sweep = 360f * reading.level / 100f
                    // A soft glow under the arc, then the arc itself with a gradient that brightens toward its leading end.
                    drawArc(tint.copy(alpha = if (reading.charging || reading.level in 0..20) .22f else .08f), -90f, sweep, false, topLeft, arcSize, style = Stroke(stroke * 1.9f, cap = StrokeCap.Round))
                    rotate(-90f) {
                        drawArc(Brush.sweepGradient(0f to tint.copy(alpha = .55f), (sweep / 360f).coerceAtLeast(.02f) to tint, 1f to tint.copy(alpha = .55f)),
                            0f, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
            }
            Text(if (reading.level >= 0) "${reading.level}%" else "—", color = ink.primary, style = UnoType.body.copy(fontWeight = FontWeight.Light))
        }
        Text(reading.toFull ?: if (reading.charging) "Charging" else "On battery", color = ink.soft(), style = UnoType.caption2, maxLines = 1,
            modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}
