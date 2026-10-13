package com.jake.duolauncher.widgets

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
    val schedule = remember(today) {
        val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val lat = prefs.getString("lat", null)?.toDoubleOrNull(); val lon = prefs.getString("lon", null)?.toDoubleOrNull()
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(com.jake.duolauncher.R.string.sunrise), color = ink.soft(), fontSize = 11.sp)
                    Text(rise.format(format), color = ink.primary, fontSize = 22.sp, fontWeight = FontWeight.Light, maxLines = 1)
                    Text(stringResource(com.jake.duolauncher.R.string.sunset), color = ink.soft(), fontSize = 11.sp)
                    Text(set.format(format), color = ink.primary, fontSize = 22.sp, fontWeight = FontWeight.Light, maxLines = 1)
                }
                Text(SunText.dayLength(rise, set), color = ink.soft(), fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
