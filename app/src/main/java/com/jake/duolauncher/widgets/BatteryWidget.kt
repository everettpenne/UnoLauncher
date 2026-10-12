package com.jake.duolauncher.widgets

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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The Battery widget: its face (the drawing) and its own helpers. Registered in [UnoWidgets]. */

private data class BatteryReading(val level: Int, val charging: Boolean, val toFull: String?)

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
    val reading by produceState(BatteryReading(-1, false, null)) {
        while (true) { value = withContext(Dispatchers.Default) { readBattery(context) }; delay(30_000) }
    }
    GlassCard(onClick = onOptions, modifier = Modifier.testTag("uno-battery-$slot")) {
        val ink = LocalGlassInk.current
        CardTitle(Icons.Rounded.BatteryChargingFull, "Battery")
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            val tint = when { reading.charging -> IosGreen; reading.level in 0..20 -> IosOrange; else -> ink.primary }
            Canvas(Modifier.fillMaxHeight().aspectRatio(1f)) {
                val stroke = 5.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(ink.soft(.22f), -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                if (reading.level >= 0) drawArc(tint, -90f, 360f * reading.level / 100f, false, Offset(inset, inset), arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Text(if (reading.level >= 0) "${reading.level}%" else "—", color = ink.primary, fontSize = 13.sp, fontWeight = FontWeight.Normal)
        }
        Text(reading.toFull ?: if (reading.charging) "Charging" else "On battery", color = ink.soft(), fontSize = 11.sp, maxLines = 1,
            modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}
