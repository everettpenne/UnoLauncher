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

/** A Material shape a small Uno widget can take in place of its rounded square (Appearance › Style set to Balanced or Expressive). */
enum class WidgetShape(val label: String) { DEFAULT("Default"), COOKIE("Cookie"), SUNNY("Sunny") }

/** Which widgets may take a shape, and how far in their content must sit to stay inside it. Pure, so it is unit-tested. */
internal object WidgetShapeRules {
    const val PREFIX = "widgetShape_"
    fun key(slot: Int) = "$PREFIX$slot"
    fun parse(value: String?): WidgetShape? = WidgetShape.entries.firstOrNull { it.name == value }

    /** Uno's own widgets only (an app's widget draws its own background), and not the folder or a widget waiting to be restored. */
    fun eligible(id: Int): Boolean = id <= CLOCK_WIDGET && id != NEEDS_BINDING_WIDGET && id != FOLDER_WIDGET

    /** The shape a widget is drawn in: its own choice, when the style allows shapes, it is eligible, and it is not a stack. */
    fun resolve(own: WidgetShape?, id: Int, stacked: Boolean, shapesAllowed: Boolean): WidgetShape =
        if (own == null || !shapesAllowed || stacked || !eligible(id)) WidgetShape.DEFAULT else own

    /** The content's box is exactly the square inside the dips' circle. (At 1.1 a corner of it, where a widget's first and last lines start, crossed a dip.) */
    const val ROOM = 1f

    /**
     * How much smaller a widget's content is drawn to sit inside a lobed shape in a [side]-wide box: its usual area is the box less [padding]
     * on each side; the shape's is the square that fits the circle its dips touch ([outer] × [inner] of the side), times [ROOM]. Never enlarged.
     */
    fun contentScale(side: Float, outer: Float, inner: Float, padding: Float): Float {
        val natural = side - 2f * padding
        if (natural <= 0f) return 1f
        val inside = side * outer * inner * 1.41421356f * ROOM
        return (inside / natural).coerceIn(.4f, 1f)
    }
}

/** Each widget's own shape, kept beside its style in the "appearance" preferences under `widgetShape_<slot>`. */
internal object WidgetShapes {
    private val shapes = mutableStateMapOf<Int, WidgetShape>()
    private var loaded = false

    fun load(context: Context) { if (!loaded) reload(context) }

    /** Reads the saved shapes again (after a settings backup is restored). */
    fun reload(context: Context) {
        loaded = true
        shapes.clear()
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).all.forEach { (key, value) ->
            if (!key.startsWith(WidgetShapeRules.PREFIX)) return@forEach
            val slot = key.removePrefix(WidgetShapeRules.PREFIX).toIntOrNull() ?: return@forEach
            WidgetShapeRules.parse(value as? String)?.takeIf { it != WidgetShape.DEFAULT }?.let { shapes[slot] = it }
        }
    }

    fun own(slot: Int): WidgetShape? = shapes[slot]

    /** Sets a widget's shape; Default (or null) clears it. */
    fun set(context: Context, slot: Int, shape: WidgetShape?) {
        val edit = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
        if (shape == null || shape == WidgetShape.DEFAULT) { edit.remove(WidgetShapeRules.key(slot)); shapes.remove(slot) }
        else { edit.putString(WidgetShapeRules.key(slot), shape.name); shapes[slot] = shape }
        edit.apply()
    }
}

/** The lobed shape the widget being drawn has, or null for the usual rounded square. Its slot paints and clips it; the card inside only keeps its content in. */
internal val LocalWidgetShape = compositionLocalOf<com.jake.duolauncher.appearance.LobedShape?> { null }
