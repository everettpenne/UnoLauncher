package com.jake.duolauncher.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.jake.duolauncher.appearance.LocalWallpaperAccent

/** The one splash of colour a widget uses: the wallpaper's accent when "Color from wallpaper" is on, otherwise the widget's own [fallback]. */
@Composable
internal fun widgetAccent(fallback: Color): Color = LocalWallpaperAccent.current?.accent ?: fallback

/** The accents the library widgets use when there is no wallpaper accent, so each widget has its own colour and the set reads as a family. */
internal object WidgetColors {
    val Warm = Color(0xFFFF9F0A)
    val Gold = Color(0xFFFFD60A)
    val Teal = Color(0xFF30D5C8)
    val Blue = Color(0xFF3AA6FF)
    val Violet = Color(0xFF7D7AFF)
    val Rose = Color(0xFFFF5E8A)
    /** iOS's Calendar red. */
    val Red = Color(0xFFFF3B30)
    val Green = Color(0xFF34C759)
}
