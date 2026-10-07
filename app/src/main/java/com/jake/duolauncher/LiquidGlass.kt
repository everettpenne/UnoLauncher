package com.jake.duolauncher

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight

/** Records Home's wallpaper and page content so glass surfaces can refract it. */
@Composable
internal fun rememberHomeBackdrop(): LayerBackdrop = rememberLayerBackdrop()

/** Records a subtree into [backdrop] without applying any glass of its own. */
@Composable
internal fun Modifier.recordBackdrop(backdrop: LayerBackdrop): Modifier = layerBackdrop(backdrop)

/** Liquid-glass surface: vibrancy, blur, and lens refraction over the recorded backdrop. */
@Composable
internal fun Modifier.liquidGlass(
    backdrop: LayerBackdrop,
    shape: Shape,
    tint: Color = Glass.copy(alpha = .3f),
    blurRadius: Float = 2f,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        vibrancy()
        blur(blurRadius.dp.toPx())
        lens(12f.dp.toPx(), 24f.dp.toPx())
    },
    highlight = { Highlight.Plain },
    onDrawSurface = { drawRect(tint) },
)
