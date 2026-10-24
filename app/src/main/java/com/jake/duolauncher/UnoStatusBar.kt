package com.jake.duolauncher

import android.app.AlarmManager
import android.app.NotificationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Uno's own status bar for Home on the iOS layout (Launcher settings › Home › Uno's status bar). Android's is hidden on Home and this one is drawn
 * around the island the way an iPhone's is: the time centred in the space left of the island, and the icons (alarm, Do Not Disturb, VPN, then
 * signal, Wi-Fi and the battery) centred in the space right of it. It is laid out from the island, so nothing can sit under it and the island is
 * centred on the camera by construction.
 *
 * What it shows comes from the same sources as the status rail ([DeviceStatus]) plus three that need no permission: whether an alarm is set,
 * whether Do Not Disturb is on, and whether a VPN is active. Android's camera and microphone indicators are not drawn here; the island always
 * shows its own privacy dots. Other apps keep Android's status bar.
 */
internal object UnoStatusBarLayout {
    /** The space between the island and what is drawn beside it, and the least space to the screen's edge, in dp. */
    const val GAP_DP = 8f
    const val EDGE_DP = 12f

    /** The two spaces beside the island ([pillLeftDp]..[pillRightDp]) on a screen [widthDp] wide, as (left start, left end, right start, right
     * end), in dp. Without an island (null) the middle 120 dp is kept clear, as for a camera. Pure, so it is unit-tested. */
    fun ears(widthDp: Float, pillLeftDp: Float?, pillRightDp: Float?): FloatArray {
        val l = pillLeftDp ?: (widthDp / 2f - 60f)
        val r = pillRightDp ?: (widthDp / 2f + 60f)
        return floatArrayOf(EDGE_DP, (l - GAP_DP).coerceAtLeast(EDGE_DP), (r + GAP_DP).coerceAtMost(widthDp - EDGE_DP), widthDp - EDGE_DP)
    }
}

private data class BarExtras(val alarm: Boolean, val doNotDisturb: Boolean, val vpn: Boolean)

@Composable
internal fun UnoStatusBar(status: DeviceStatus, darkInk: Boolean, pillLeftDp: Float?, pillRightDp: Float?, heightDp: Float, widthDp: Float,
    modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val ink = if (darkInk) Color.Black else Color.White
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val time by produceState(LocalTime.now()) { while (true) { value = LocalTime.now(); delay(1_000L * (61 - LocalTime.now().second)) } }
    // Looked up now and then while Home shows the bar; none of them needs a permission.
    val extras by produceState(BarExtras(false, false, false)) {
        while (true) {
            value = runCatching {
                val connectivity = context.getSystemService(ConnectivityManager::class.java)
                BarExtras(
                    alarm = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock != null,
                    doNotDisturb = context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
                        .let { it != null && it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN },
                    vpn = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true)
            }.getOrDefault(BarExtras(false, false, false))
            delay(15_000L)
        }
    }
    val ears = UnoStatusBarLayout.ears(widthDp, pillLeftDp, pillRightDp)
    Box(modifier.fillMaxWidth().height(heightDp.dp).testTag("uno-status-bar")) {
        Box(Modifier.offset(x = ears[0].dp).width((ears[1] - ears[0]).dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text(time.format(DateTimeFormatter.ofPattern(ClockFormat.timePattern(is24))), color = ink, maxLines = 1,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum", letterSpacing = (-.2).sp),
                modifier = Modifier.testTag("uno-status-time"))
        }
        Row(Modifier.offset(x = ears[2].dp).width((ears[3] - ears[2]).dp).fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            if (extras.alarm) Icon(Icons.Rounded.Alarm, "Alarm set", tint = ink, modifier = Modifier.size(13.dp))
            if (extras.doNotDisturb) Icon(Icons.Rounded.Bedtime, "Do Not Disturb", tint = ink, modifier = Modifier.size(13.dp))
            if (extras.vpn) Icon(Icons.Rounded.VpnKey, "VPN", tint = ink, modifier = Modifier.size(13.dp))
            if (status.airplane) Icon(Icons.Rounded.AirplanemodeActive, "Airplane mode", tint = ink, modifier = Modifier.size(14.dp))
            else CellBars(status.cellularLevel, ink)
            if (status.wifiConnected) WifiFan(status.wifiLevel, ink)
            BarBattery(status.battery, status.charging, ink)
        }
    }
}

/** Four rising bars, lit to the cellular level; dim when there is no signal reading. */
@Composable
private fun CellBars(level: Int?, ink: Color) {
    Canvas(Modifier.size(width = 17.dp, height = 11.dp).semantics { contentDescription = level?.let { "Cellular signal $it of 4" } ?: "No cellular signal" }) {
        val w = size.width / 4f
        for (i in 0 until 4) {
            val h = size.height * (.35f + .65f * i / 3f)
            drawRoundRect(ink.copy(alpha = if (level != null && i < level) 1f else .3f), topLeft = Offset(i * w + w * .12f, size.height - h),
                size = Size(w * .76f, h), cornerRadius = CornerRadius(w * .25f))
        }
    }
}

/** Three arcs and a dot, lit to the Wi-Fi level. */
@Composable
private fun WifiFan(level: Int?, ink: Color) {
    Canvas(Modifier.size(width = 15.dp, height = 11.dp).semantics { contentDescription = "Wi-Fi ${level ?: 4} of 4" }) {
        val lit = (level ?: 4).coerceIn(0, 4)
        val cx = size.width / 2f; val base = size.height * .95f
        val stroke = size.height * .16f
        for (i in 1..3) {
            val r = size.height * (.28f + .3f * (i - 1))
            drawArc(ink.copy(alpha = if (lit > i) 1f else .3f), 225f, 90f, false, Offset(cx - r, base - r), Size(r * 2, r * 2),
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        drawCircle(ink.copy(alpha = if (lit > 0) 1f else .3f), stroke * .75f, Offset(cx, base - stroke * .4f))
    }
}

/** The battery as iOS draws it: an outline with a nub, filled to the level (green while charging, with a bolt; red at 20 % or less). */
@Composable
private fun BarBattery(level: Int?, charging: Boolean, ink: Color) {
    Canvas(Modifier.size(width = 25.dp, height = 12.dp).semantics { contentDescription = "Battery ${level ?: 0} percent${if (charging) ", charging" else ""}" }) {
        val nub = 1.6.dp.toPx(); val stroke = 1.dp.toPx()
        val bodyW = size.width - nub - 1.dp.toPx()
        drawRoundRect(ink.copy(alpha = .4f), Offset(stroke / 2f, stroke / 2f), Size(bodyW - stroke, size.height - stroke),
            CornerRadius(3.5.dp.toPx()), style = Stroke(stroke))
        drawRoundRect(ink.copy(alpha = .4f), Offset(bodyW + 1.dp.toPx() - stroke / 2f, size.height * .33f), Size(nub, size.height * .34f), CornerRadius(nub))
        val inset = 2.dp.toPx()
        val fill = when { charging -> Color(0xFF34C759); level != null && level <= 20 -> Color(0xFFFF3B30); else -> ink }
        val w = (bodyW - 2 * inset) * ((level ?: 0) / 100f).coerceIn(0f, 1f)
        if (w > 0f) drawRoundRect(fill, Offset(inset, inset), Size(w, size.height - 2 * inset), CornerRadius(2.dp.toPx()))
        if (charging) {
            val cx = bodyW / 2f; val h = size.height
            val bolt = Path().apply {
                moveTo(cx + h * .1f, h * .12f); lineTo(cx - h * .22f, h * .55f); lineTo(cx - h * .02f, h * .55f)
                lineTo(cx - h * .1f, h * .88f); lineTo(cx + h * .22f, h * .45f); lineTo(cx + h * .02f, h * .45f); close()
            }
            drawPath(bolt, if (darkFill(fill)) Color.White else Color.Black)
        }
    }
}

private fun darkFill(c: Color) = (.299f * c.red + .587f * c.green + .114f * c.blue) < .6f
