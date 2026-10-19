package com.jake.duolauncher

import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.island.*
import com.jake.duolauncher.feed.*
import com.jake.duolauncher.search.*
import com.jake.duolauncher.panel.*
import com.jake.duolauncher.appearance.*
import com.jake.duolauncher.folders.*
import com.jake.duolauncher.widgets.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
internal fun GlassCard(modifier: Modifier = Modifier, padding: androidx.compose.ui.unit.Dp = 14.dp, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val glass = LocalPageGlass.current
    val previewing = LocalWidgetPreview.current
    // Built-in widget text follows the wallpaper behind it: white on dark, dark ink on pale.
    val ink = rememberAdaptiveInk(glass?.tint ?: Glass, if (glass != null) .14f else .24f)
    CompositionLocalProvider(LocalGlassInk provides ink.glassInk) {
        if (glass != null) {
            Column(modifier.fillMaxSize().then(ink.track).liquidGlass(glass.backdrop, Corner.large, glass.tint.copy(alpha = .14f),
                blurRadius = 1f, settings = glass.settings).clip(Corner.large).then(if (previewing) Modifier else Modifier.clickable(onClick = onClick))
                .padding(padding), verticalArrangement = Arrangement.SpaceBetween, content = content)
        } else {
            Surface(modifier.fillMaxSize().then(ink.track).clip(Corner.large).then(if (previewing) Modifier else Modifier.clickable(onClick = onClick)),
                color = Glass.copy(alpha = .24f), shape = Corner.large, border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .18f))) {
                Column(Modifier.padding(padding), verticalArrangement = Arrangement.SpaceBetween, content = content)
            }
        }
    }
}

@Composable
internal fun currentTime(everyMs: Long = 60_000L): LocalDateTime {
    // The clock card shows minutes and the date card a day, so a tick every second only made them recompose 59 times a minute for
    // nothing: this ticks just after each minute (or [everyMs]) changes.
    val time by produceState(LocalDateTime.now(), everyMs) {
        while (true) { value = LocalDateTime.now(); delay((everyMs + 50L) - (System.currentTimeMillis() % everyMs)) }
    }
    return time
}

@Composable
internal fun ClockCard(onClick: () -> Unit) {
    val time = currentTime()
    val format = if (android.text.format.DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm"
    GlassCard(onClick = onClick) {
        val ink = LocalGlassInk.current
        Icon(Icons.Rounded.Schedule, "Clock widget; tap to replace", tint = ink.primary, modifier = Modifier.size(20.dp))
        Text(time.format(DateTimeFormatter.ofPattern(format)), color = ink.primary, fontWeight = FontWeight.Light, fontSize = 30.sp, maxLines = 1)
        Text(stringResource(com.jake.duolauncher.R.string.local_time), color = ink.soft(.92f), style = UnoType.caption1.copy(fontWeight = FontWeight.Medium))
    }
}

@Composable
internal fun DateCard(onClick: () -> Unit) {
    val date = currentTime()
    GlassCard(onClick = onClick) {
        val ink = LocalGlassInk.current
        Text(date.format(DateTimeFormatter.ofPattern("EEEE")), color = ink.primary, style = UnoType.caption1, maxLines = 1)
        Text(date.dayOfMonth.toString(), color = ink.primary, fontWeight = FontWeight.Light, fontSize = 40.sp, lineHeight = 42.sp)
        Text(date.format(DateTimeFormatter.ofPattern("MMMM")), color = ink.soft(.92f), style = UnoType.footnote.copy(fontWeight = FontWeight.Medium))
    }
}

@Composable
internal fun ExpandedCard(onClick: () -> Unit) {
    val date = currentTime()
    GlassCard(onClick = onClick) {
        val ink = LocalGlassInk.current
        Column {
            Text(date.format(DateTimeFormatter.ofPattern("EEEE")), color = ink.primary, style = UnoType.title2)
            Text(date.format(DateTimeFormatter.ofPattern("MMMM d")), color = ink.soft(), style = UnoType.callout)
        }
        Column {
            Icon(Icons.Rounded.Widgets, null, tint = ink.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(com.jake.duolauncher.R.string.a_little_more_room), color = ink.primary, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(com.jake.duolauncher.R.string.add_a_calendar_photos_or_another), color = ink.soft(.85f), style = UnoType.subheadline)
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = onClick) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(com.jake.duolauncher.R.string.add_widget)) }
        }
    }
}
