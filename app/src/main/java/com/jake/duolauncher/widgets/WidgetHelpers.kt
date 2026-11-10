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
import androidx.compose.ui.text.withStyle
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
    val onCard = com.jake.duolauncher.LocalWidgetStyle.current == com.jake.duolauncher.WidgetStyle.CARD
    val tint = accent?.let { WidgetType.legibleAccent(it, ink.primary, onCard) } ?: ink.soft()
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, color = tint, style = UnoType.caption2.copy(fontWeight = if (accent != null) FontWeight.SemiBold else UnoType.caption2.fontWeight),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A big time, its AM or PM set small and soft beside the digits, as the open island and iOS's widgets set it. */
@Composable
internal fun WidgetTime(time: String, color: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.TextUnit = WidgetType.HERO_TIME) {
    val (digits, meridiem) = OpenIslandLayout.splitMeridiem(time)
    val soft = LocalGlassInk.current.soft()
    Text(androidx.compose.ui.text.buildAnnotatedString {
        append(digits)
        if (meridiem.isNotEmpty()) withStyle(androidx.compose.ui.text.SpanStyle(fontSize = size * .44f, fontWeight = FontWeight.Medium, letterSpacing = 0.sp, color = soft)) { append(meridiem) }
    }, color = color, style = WidgetType.number(size), maxLines = 1, modifier = modifier)
}

/** iOS widget type: the large figure (a time, a count, a percentage) is semibold, tightly tracked and with figures of one width, where
 * these widgets used thin Light figures. Pure, so the accent rule is unit-tested. */
internal object WidgetType {
    fun number(size: androidx.compose.ui.unit.TextUnit) = androidx.compose.ui.text.TextStyle(fontSize = size, lineHeight = size * 1.06f,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.03f * size.value).sp, fontFeatureSettings = "tnum")

    /** The family's type, so the widgets read as one set: one size for the big number a small widget is about (a count, days to go), one for a big
     *  time (its AM or PM set small beside it, see [WidgetTime]), and one style for the supporting line under them. They had grown five sizes of
     *  big number between them (22 to 46 sp) and two of caption. */
    val HERO = 40.sp
    val HERO_TIME = 34.sp
    val hero get() = number(HERO)
    val caption get() = UnoType.caption1
    /** A word set beside the big number, on its baseline ("23 days"). */
    val unit get() = UnoType.footnote.copy(fontWeight = FontWeight.Medium)

    /** [accent] as a header colour over glass whose ink is [ink]: darkened toward black when the ink is dark (a light wallpaper). */
    fun legibleAccent(accent: Color, ink: Color, card: Boolean = false): Color = when {
        // On a white card (as on iOS) a header keeps its system colour; only a very pale one (gold, teal) is deepened so it still reads.
        card -> if (ink.luminance() < .5f && accent.luminance() > .55f) androidx.compose.ui.graphics.lerp(accent, Color.Black, .3f) else accent
        ink.luminance() < .5f -> androidx.compose.ui.graphics.lerp(accent, Color.Black, .35f)
        else -> accent
    }
}
