package com.jake.duolauncher.widgets

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.*
import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId

/**
 * The Next alarm widget: the time of the next alarm set in the phone's clock app, and how long until it. It asks Android for exactly
 * that one answer ([AlarmManager.getNextAlarmClock]), which needs no permission, and shows it only here: nothing is kept or sent. Tapping
 * it opens the clock app where the alarm is set. Registered in [UnoWidgets].
 */
@Composable
internal fun NextAlarmFace(slot: Int, onOptions: () -> Unit) {
    val context = LocalContext.current
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var info by remember { mutableStateOf(readNextAlarm(context)) }
    // Android announces a change to the next alarm; the minute tick keeps "in 7 h 20 min" honest between changes.
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) { info = readNextAlarm(context); nowMs = System.currentTimeMillis() }
        }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000L); nowMs = System.currentTimeMillis(); info = readNextAlarm(context) } }
    GlassCard(onClick = { if (!sendTapAction(context, info?.showIntent)) onOptions() }, modifier = Modifier.testTag("uno-next-alarm-$slot")) {
        val ink = LocalGlassInk.current
        CardTitle(Icons.Rounded.Alarm, "Next alarm")
        val alarm = info
        val preview = LocalWidgetPreview.current
        if (alarm == null && !preview) {
            Text(stringResource(com.jake.duolauncher.R.string.no_alarm_set), color = ink.primary, fontSize = 18.sp, fontWeight = FontWeight.Light, modifier = Modifier.testTag("next-alarm-none"))
            Text(stringResource(com.jake.duolauncher.R.string.set_one_in_your_clock_app), color = ink.soft(), style = UnoType.caption1)
        } else {
            val zone = ZoneId.systemDefault()
            // The picker's preview shows a plausible alarm (tomorrow morning) rather than the person's own, or "No alarm set".
            val trigger = alarm?.triggerTime ?: java.time.ZonedDateTime.now(zone).plusDays(1).withHour(7).withMinute(30).withSecond(0).toInstant().toEpochMilli()
            val parts = NextAlarmText.describe(Instant.ofEpochMilli(nowMs).atZone(zone), Instant.ofEpochMilli(trigger).atZone(zone),
                android.text.format.DateFormat.is24HourFormat(context))
            Text(parts.day, color = ink.soft(), style = UnoType.caption1, maxLines = 1)
            Text(parts.time, color = ink.primary, fontSize = 34.sp, fontWeight = FontWeight.Light, maxLines = 1, modifier = Modifier.testTag("next-alarm-time"))
            Box(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(widgetAccent(WidgetColors.Warm).copy(alpha = .26f))
                .padding(horizontal = 9.dp, vertical = 3.dp)) {
                Text(parts.remaining, color = ink.primary, style = UnoType.caption2, maxLines = 1)
            }
        }
    }
}

private fun readNextAlarm(context: Context): AlarmManager.AlarmClockInfo? =
    runCatching { context.getSystemService(AlarmManager::class.java)?.nextAlarmClock }.getOrNull()
