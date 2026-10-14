package com.jake.duolauncher.widgets

import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The Screen time widget: its face (the drawing) and its own helpers. Registered in [UnoWidgets].
 *
 * Today's screen time and the three apps you used most, with a bar toward a daily goal you set. It reads the phone's
 * own usage stats, which need Android's special "Usage access" grant: the widget asks for it the first time, and
 * without it shows nothing but the ask. The numbers never leave the phone. (The Usage access permission itself is a
 * launcher-lane change: the manifest, PRIVACY.md and the permission ledger.)
 */
@Composable
internal fun ScreenTimeFace(slot: Int, onOptions: () -> Unit) {
    val context = LocalContext.current
    val previewing = LocalWidgetPreview.current
    var editingGoal by remember { mutableStateOf(false) }
    val goalMin = ScreenTime.goalMinutes(WidgetData.get(slot, "goal"))
    val allowed = previewing || hasUsageAccess(context)
    val live by produceState(emptyList<ScreenTime.AppUsage>(), allowed) {
        while (true) {
            value = if (hasUsageAccess(context)) withContext(Dispatchers.IO) { readUsageToday(context) } else emptyList()
            delay(5 * 60_000L)
        }
    }
    val usages = if (previewing) ScreenTime.SAMPLE_USAGES else live
    val accent = widgetAccent(WidgetColors.Violet)
    GlassCard(onClick = { if (!hasUsageAccess(context)) openUsageAccess(context) else editingGoal = true },
        modifier = Modifier.testTag("uno-screentime-$slot")) {
        val ink = LocalGlassInk.current
        CardTitle(Icons.Rounded.HourglassTop, "Screen time")
        if (!allowed) {
            Text(stringResource(com.jake.duolauncher.R.string.screen_time_allow), color = ink.soft(), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            GlassButton("Allow", { openUsageAccess(context) }, tag = "screentime-allow")
        } else {
            val total = ScreenTime.totalMinutes(usages)
            val over = ScreenTime.overGoal(total, goalMin)
            Text(ScreenTime.formatDuration(total), color = ink.primary, fontSize = 30.sp, fontWeight = FontWeight.Light,
                maxLines = 1, modifier = Modifier.testTag("screentime-total"))
            Text(stringResource(com.jake.duolauncher.R.string.screen_time_today), color = ink.soft(), fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(progress = { ScreenTime.goalProgress(total, goalMin) },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                color = if (over) widgetAccent(WidgetColors.Warm) else accent,
                trackColor = ink.soft(.20f), gapSize = 0.dp, drawStopIndicator = {})
            Text("of your ${ScreenTime.formatDuration(goalMin)} goal", color = ink.soft(), fontSize = 11.sp, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            ScreenTime.topApps(usages).forEach { app ->
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp).testTag("screentime-app-${app.packageName}"),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(ScreenTime.displayName(app), color = ink.soft(), fontSize = 12.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(ScreenTime.formatDuration(app.minutes), color = ink.primary, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
    if (editingGoal) {
        GlassDialog(onDismissRequest = { editingGoal = false }, title = "Daily goal", actions = {
            GlassButton("Done", { editingGoal = false }, prominent = true, tag = "screentime-goal-done")
        }) {
            GlassNote("Show a bar toward this much screen time each day.")
            listOf(60, 120, 180, 240, 360).forEach { minutes ->
                GlassRow(ScreenTime.formatDuration(minutes), onClick = {
                    WidgetData.set(slot, "goal", minutes.toString()); editingGoal = false
                }, modifier = Modifier.testTag("screentime-goal-$minutes"),
                trailing = { if (minutes == goalMin) Icon(Icons.Rounded.Check, "Selected", tint = accent) })
            }
        }
    }
}

/** Whether the user has granted Usage access (a special app access set in Android's settings, not a runtime dialog). */
internal fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
    return appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
        AppOpsManager.MODE_ALLOWED
}

/** Opens Android's Usage access settings so the grant can be switched on. */
private fun openUsageAccess(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** The apps in the foreground so far today, from the phone's usage stats. Guarded: with no grant it reads as empty. */
private fun readUsageToday(context: Context): List<ScreenTime.AppUsage> {
    if (!hasUsageAccess(context)) return emptyList()
    return runCatching {
        val manager = context.getSystemService(UsageStatsManager::class.java)
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, System.currentTimeMillis()) ?: return emptyList()
        val pm = context.packageManager
        stats.filter { it.totalTimeInForeground > 0L }.map {
            ScreenTime.AppUsage(it.packageName,
                runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it.packageName, 0)).toString() }.getOrNull(),
                (it.totalTimeInForeground / 60_000L).toInt())
        }
    }.getOrDefault(emptyList())
}

/** The Screen time widget's rules, kept apart from the drawing (and from Android) so each is unit-tested. */
internal object ScreenTime {
    const val TOP = 3
    const val DEFAULT_GOAL_MINUTES = 180

    /** One app's use today: its package, a display label if the phone has one, and whole minutes in the foreground. */
    data class AppUsage(val packageName: String, val label: String?, val minutes: Int)

    /** A plausible day for the picker's preview, so it shows the look without a grant. */
    val SAMPLE_USAGES = listOf(
        AppUsage("com.example.browser", "Browser", 74),
        AppUsage("com.example.chat", "Messages", 38),
        AppUsage("com.example.maps", "Maps", 21),
    )

    /** The most-used apps worth showing: at least a minute each, one row per app, most-used first, at most [count]. */
    fun topApps(usages: List<AppUsage>, count: Int = TOP): List<AppUsage> =
        usages.filter { it.minutes >= 1 }.distinctBy { it.packageName }.sortedByDescending { it.minutes }.take(count)

    fun totalMinutes(usages: List<AppUsage>): Int = usages.sumOf { it.minutes }

    /** The app's name, falling back to the last part of its package when it has no label. */
    fun displayName(usage: AppUsage): String =
        usage.label?.trim()?.takeIf { it.isNotEmpty() } ?: usage.packageName.substringAfterLast('.')

    /** "0 min", "45 min", "2 h" or "3 h 12 min". */
    fun formatDuration(minutes: Int): String = when {
        minutes <= 0 -> "0 min"
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }

    /** The daily goal in minutes, from the saved setting, or the default when unset or out of range. */
    fun goalMinutes(raw: String?): Int = raw?.trim()?.toIntOrNull()?.takeIf { it in 15..1440 } ?: DEFAULT_GOAL_MINUTES

    /** How far through the goal today is, 0 to 1. */
    fun goalProgress(totalMinutes: Int, goalMinutes: Int): Float =
        if (goalMinutes <= 0) 0f else (totalMinutes.toFloat() / goalMinutes).coerceIn(0f, 1f)

    fun overGoal(totalMinutes: Int, goalMinutes: Int): Boolean = goalMinutes > 0 && totalMinutes > goalMinutes
}
