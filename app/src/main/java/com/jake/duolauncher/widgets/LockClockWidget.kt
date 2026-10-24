package com.jake.duolauncher.widgets

import com.jake.duolauncher.*
import com.jake.duolauncher.appearance.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The lock screen's look: a typeface and a colour, as iOS's lock screen editor offers. Pure, so the choices and the wording are tested. */
internal object LockClock {
    enum class Style(val label: String) { CLASSIC("Classic"), HEAVY("Heavy"), THIN("Thin"), SERIF("Serif"), MONO("Mono") }
    enum class Tint(val label: String) { WHITE("White"), ACCENT("Accent") }

    fun style(raw: String): Style = Style.entries.firstOrNull { it.name == raw } ?: Style.CLASSIC
    fun tint(raw: String): Tint = Tint.entries.firstOrNull { it.name == raw } ?: Tint.WHITE

    /** "9:41" (no leading zero, no AM/PM, as on iOS's lock screen) or "21:41". */
    fun time(at: LocalDateTime, is24Hour: Boolean): String =
        at.format(DateTimeFormatter.ofPattern(if (is24Hour) "H:mm" else "h:mm", Locale.getDefault()))

    /** "Saturday 24 October", in the phone's language and order. */
    fun date(at: LocalDateTime): String = at.format(DateTimeFormatter.ofPattern(
        android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), Locale.getDefault()))

    fun weight(style: Style): FontWeight = when (style) {
        Style.HEAVY -> FontWeight.Black
        Style.THIN -> FontWeight.Thin
        Style.SERIF, Style.MONO -> FontWeight.Medium
        Style.CLASSIC -> FontWeight.SemiBold
    }

    fun family(style: Style): FontFamily? = when (style) {
        Style.SERIF -> FontFamily.Serif
        Style.MONO -> FontFamily.Monospace
        else -> null
    }
}

/**
 * The Lock Screen clock widget: today's date over a large time, straight on the wallpaper with no card, as iOS's lock screen draws it.
 * A tap opens its editor (typeface and colour). It is a widget, not a fixed header, so it is placed, moved, sized and removed like any
 * other, and the Home grid's layout (and every icon's drop position) is unchanged. Registered in [UnoWidgets].
 */
@Composable
internal fun LockClockFace(slot: Int, onOptions: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Redrawn on the minute, as the analog clock is; there are no seconds to show.
    val now by produceState(LocalDateTime.now()) {
        while (true) { value = LocalDateTime.now(); kotlinx.coroutines.delay(AnalogClock.msToNextMinute(value.toLocalTime()) + 20L) }
    }
    val is24 = remember { android.text.format.DateFormat.is24HourFormat(context) }
    val style = LockClock.style(WidgetData.get(slot, "style"))
    val tint = LockClock.tint(WidgetData.get(slot, "tint"))
    var editing by remember { mutableStateOf(false) }
    val time = LockClock.time(now, is24)
    val date = LockClock.date(now)
    Box(Modifier.fillMaxSize().clickable { editing = true }.testTag("uno-lock-clock-$slot")
        .semantics(mergeDescendants = true) { contentDescription = "$date, $time" }, contentAlignment = Alignment.Center) {
        LockClockText(time, date, style, tint, Modifier.fillMaxSize())
    }
    if (editing) {
        var draftStyle by remember { mutableStateOf(style) }
        var draftTint by remember { mutableStateOf(tint) }
        GlassDialog(onDismissRequest = { editing = false }, title = "Lock Screen Clock", actions = {
            GlassButton("Cancel", { editing = false })
            GlassButton("Done", {
                WidgetData.set(slot, "style", draftStyle.name)
                WidgetData.set(slot, "tint", draftTint.name)
                editing = false
            }, prominent = true, tag = "lock-clock-save")
        }) {
            // The preview sits on a dark stand-in for the wallpaper, so white is seen as it will be.
            Box(Modifier.fillMaxWidth().height(120.dp).background(Color(0xFF1C1C1E), Corner.medium), contentAlignment = Alignment.Center) {
                LockClockText(time, date, draftStyle, draftTint, Modifier.fillMaxSize().padding(8.dp))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LockClock.Style.entries.forEach { s ->
                    GlassChip(draftStyle == s, { draftStyle = s }, { Text(s.label, fontWeight = LockClock.weight(s), fontFamily = LockClock.family(s)) },
                        Modifier.testTag("lock-clock-style-${s.name.lowercase()}"))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LockClock.Tint.entries.forEach { t ->
                    GlassChip(draftTint == t, { draftTint = t }, { Text(t.label) }, Modifier.testTag("lock-clock-tint-${t.name.lowercase()}"))
                }
            }
        }
    }
}

/** The date and the time, the time as large as the space allows. */
@Composable
private fun LockClockText(time: String, date: String, style: LockClock.Style, tint: LockClock.Tint, modifier: Modifier) {
    val colour = if (tint == LockClock.Tint.ACCENT) SettingsAccent else Color.White
    val shadow = Shadow(Color.Black.copy(alpha = .28f), Offset(0f, 2f), 10f)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        // As big as fits: about three-fifths of the height, and narrow enough for "12:59" across the width.
        val timeSize = with(density) { minOf(maxHeight * .58f, maxWidth * (if (style == LockClock.Style.MONO) .27f else .33f)).toSp() }
        val dateSize = with(density) { (maxHeight * .13f).coerceIn(12.dp, 20.dp).toSp() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date, color = Color.White.copy(alpha = .92f), maxLines = 1, textAlign = TextAlign.Center,
                style = TextStyle(fontSize = dateSize, fontWeight = FontWeight.SemiBold, shadow = shadow))
            Text(time, color = colour, maxLines = 1, textAlign = TextAlign.Center,
                style = TextStyle(fontSize = timeSize, lineHeight = timeSize, fontWeight = LockClock.weight(style),
                    fontFamily = LockClock.family(style), shadow = shadow, letterSpacing = if (style == LockClock.Style.THIN) 0.sp else (-1).sp))
        }
    }
}
