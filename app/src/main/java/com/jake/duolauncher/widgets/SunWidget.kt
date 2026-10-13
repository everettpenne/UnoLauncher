package com.jake.duolauncher.widgets

import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The Sunrise and sunset widget: its face (the drawing) and its own helpers. Registered in [UnoWidgets]. */

@Composable
internal fun SunFace(slot: Int, onOptions: () -> Unit) {
    val context = LocalContext.current
    val today = rememberNow(10 * 60_000L).toLocalDate()
    val previewing = LocalWidgetPreview.current
    val schedule = remember(today, previewing) {
        val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val sample = previewing
        val lat = if (sample) PreviewSamples.SAMPLE_LATITUDE else prefs.getString("lat", null)?.toDoubleOrNull()
        val lon = if (sample) PreviewSamples.SAMPLE_LONGITUDE else prefs.getString("lon", null)?.toDoubleOrNull()
        if (lat == null || lon == null) null else runCatching { solarSchedule(today, lat, lon, ZoneId.systemDefault()) }.getOrNull()
    }
    val format = DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a")
    GlassCard(onClick = onOptions, modifier = Modifier.testTag("uno-sun-$slot")) {
        val ink = LocalGlassInk.current
        CardTitle(Icons.Rounded.WbSunny, "Sun")
        when {
            schedule == null -> Text(stringResource(com.jake.duolauncher.R.string.set_a_place_in_wallpaper_glass), color = ink.soft(), fontSize = 12.sp)
            schedule.polar == PolarDaylight.DAY -> Text(stringResource(com.jake.duolauncher.R.string.the_sun_does_not_set_today), color = ink.primary, fontSize = 14.sp)
            schedule.polar == PolarDaylight.NIGHT -> Text(stringResource(com.jake.duolauncher.R.string.the_sun_does_not_rise_today), color = ink.primary, fontSize = 14.sp)
            else -> {
                val rise = requireNotNull(schedule.sunrise); val set = requireNotNull(schedule.sunset)
                val sunNow = rememberNow(60_000L).atZone(rise.zone)
                val across = ((sunNow.toEpochSecond() - rise.toEpochSecond()).toFloat() / (set.toEpochSecond() - rise.toEpochSecond()).coerceAtLeast(1L)).coerceIn(0f, 1f)
                val accent = widgetAccent(WidgetColors.Warm)
                // The sun's path over the day: a dome from sunrise to sunset, with the sun where it is now (below the horizon at night).
                Box(Modifier.weight(1f).fillMaxWidth().padding(top = 2.dp)) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val pad = 6.dp.toPx()
                        val w = size.width - pad * 2; val h = size.height - 6.dp.toPx()
                        val horizon = h * .86f
                        val dome = androidx.compose.ui.graphics.Path().apply {
                            moveTo(pad, horizon)
                            cubicTo(pad + w * .18f, horizon - h * .96f, pad + w * .82f, horizon - h * .96f, pad + w, horizon)
                        }
                        drawPath(dome, ink.soft(.32f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))))
                        drawLine(ink.soft(.4f), androidx.compose.ui.geometry.Offset(0f, horizon), androidx.compose.ui.geometry.Offset(size.width, horizon), 1.dp.toPx())
                        // The point at [t] along the dome (a cubic Bezier), and the glowing sun on it.
                        val t = across; val u = 1f - t
                        val x = u*u*u*pad + 3*u*u*t*(pad + w*.18f) + 3*u*t*t*(pad + w*.82f) + t*t*t*(pad + w)
                        val y = u*u*u*horizon + 3*u*u*t*(horizon - h*.96f) + 3*u*t*t*(horizon - h*.96f) + t*t*t*horizon
                        val centre = androidx.compose.ui.geometry.Offset(x, y)
                        drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(accent.copy(alpha = .55f), androidx.compose.ui.graphics.Color.Transparent), centre, 18.dp.toPx()), 18.dp.toPx(), centre)
                        drawCircle(accent, 5.dp.toPx(), centre)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(rise.format(format), color = ink.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(set.format(format), color = ink.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
                Text(SunText.dayLength(rise, set), color = ink.soft(), fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
