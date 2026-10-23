package com.jake.duolauncher.widgets

import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** Drawing helpers shared by the Uno widgets. A widget file may use these; it must not depend on another widget's file. */

@Composable
internal fun rememberNow(periodMs: Long): LocalDateTime {
    val now by produceState(LocalDateTime.now(), periodMs) { while (true) { value = LocalDateTime.now(); delay(periodMs) } }
    return now
}

internal fun onInk(ink: GlassInk) = if (ink.primary.luminance() > .5f) Color.Black else Color.White

/**
 * A widget's header, as iOS draws one: a small symbol and the name in the widget's own colour ([accent]), semibold, the way the Calendar
 * widget's day is red and Reminders' list is blue. Without an accent it is the soft ink it used to be. On a light wallpaper (dark ink) the
 * accent is darkened a third of the way to black, so a gold or green header still reads on pale glass.
 */
@Composable
internal fun CardTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, accent: Color? = null) {
    val ink = LocalGlassInk.current
    val tint = accent?.let { WidgetType.legibleAccent(it, ink.primary) } ?: ink.soft()
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, color = tint, style = UnoType.caption2.copy(fontWeight = if (accent != null) FontWeight.SemiBold else UnoType.caption2.fontWeight),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** iOS widget type: the large figure (a time, a count, a percentage) is semibold, tightly tracked and with figures of one width, where
 * these widgets used thin Light figures. Pure, so the accent rule is unit-tested. */
internal object WidgetType {
    fun number(size: androidx.compose.ui.unit.TextUnit) = androidx.compose.ui.text.TextStyle(fontSize = size, lineHeight = size * 1.06f,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.03f * size.value).sp, fontFeatureSettings = "tnum")

    /** [accent] as a header colour over glass whose ink is [ink]: darkened toward black when the ink is dark (a light wallpaper). */
    fun legibleAccent(accent: Color, ink: Color): Color =
        if (ink.luminance() < .5f) androidx.compose.ui.graphics.lerp(accent, Color.Black, .35f) else accent
}
