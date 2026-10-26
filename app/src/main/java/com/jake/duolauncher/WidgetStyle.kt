package com.jake.duolauncher

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.jake.duolauncher.appearance.AdaptiveInk
import com.jake.duolauncher.appearance.DuoAppearanceRuntime

/**
 * How a Home widget is drawn. [CARD] is a plain, softly translucent card like an iOS widget: it costs almost nothing to draw, so pages swipe
 * smoothly. [GLASS] is the live liquid glass that bends the wallpaper behind the widget; because that is redone every frame while the widget
 * moves, a page swipe with glass widgets missed about a quarter of its frames on a Pixel 10a (about 1% with no glass).
 */
enum class WidgetStyle { CARD, GLASS }

/** What decides a widget's style, pure so it is unit-tested. */
internal object WidgetStyleRules {
    /** The widget's own choice if it made one, else the global default; with glass switched off everywhere a widget is always a card. */
    fun resolve(own: WidgetStyle?, default: WidgetStyle, glassAvailable: Boolean): WidgetStyle =
        if (!glassAvailable) WidgetStyle.CARD else own ?: default

    const val PREFIX = "widgetStyle_"
    fun key(slot: Int) = "$PREFIX$slot"
    fun parse(value: String?): WidgetStyle? = WidgetStyle.entries.firstOrNull { it.name == value }
}

/**
 * Each widget's own style, kept in the "appearance" preferences under `widgetStyle_<slot>` so a layout backup carries it with the other
 * appearance settings. A widget with no entry follows the global default (Appearance › Widgets).
 */
internal object WidgetStyles {
    private val overrides = mutableStateMapOf<Int, WidgetStyle>()

    fun load(context: Context) {
        val all = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).all
        val next = all.mapNotNull { (key, value) ->
            if (!key.startsWith(WidgetStyleRules.PREFIX)) return@mapNotNull null
            val slot = key.removePrefix(WidgetStyleRules.PREFIX).toIntOrNull() ?: return@mapNotNull null
            val style = WidgetStyleRules.parse(value as? String) ?: return@mapNotNull null
            slot to style
        }.toMap()
        overrides.keys.toList().filter { it !in next }.forEach { overrides.remove(it) }
        overrides.putAll(next)
    }

    fun own(slot: Int): WidgetStyle? = overrides[slot]

    /** Sets a widget's style, or clears its own choice with null so it follows the default again. */
    fun set(context: Context, slot: Int, style: WidgetStyle?) {
        val edit = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
        if (style == null) { edit.remove(WidgetStyleRules.key(slot)); overrides.remove(slot) }
        else { edit.putString(WidgetStyleRules.key(slot), style.name); overrides[slot] = style }
        edit.apply()
    }
}

/** The style the widget being drawn has, or null outside a widget slot (a picker preview), where a widget draws as it always did. */
internal val LocalWidgetStyle = compositionLocalOf<WidgetStyle?> { null }
/** True inside a [WidgetSlot], which paints the widget's background itself, so the card inside it need not. */
internal val LocalSlotPaints = compositionLocalOf { false }

/**
 * The card behind a widget, as an iOS 26 Home Screen widget is: near-white in light mode and deep graphite in dark, almost opaque (the
 * wallpaper only just shows through), a hairline edge and no shadow, no blur and no shader. Content sits 16 dp in (see [GlassCard]).
 */
@Composable
internal fun Modifier.widgetCard(shape: Shape): Modifier {
    val dark = DuoAppearanceRuntime.dark
    val fill = if (dark) Color(0xFF1E1E20).copy(alpha = .94f) else Color(0xFFFFFFFF).copy(alpha = .95f)
    val edge = if (dark) Color.White.copy(alpha = .09f) else Color.Black.copy(alpha = .05f)
    return this.background(fill, shape).border(.5.dp, edge, shape)
}

/** The ink on a card: it never depends on the wallpaper, only on light or dark. */
@Composable
internal fun cardInk(): AdaptiveInk = AdaptiveInk(
    if (DuoAppearanceRuntime.dark) Color.White else Color(0xFF0B0B0D), Modifier)
