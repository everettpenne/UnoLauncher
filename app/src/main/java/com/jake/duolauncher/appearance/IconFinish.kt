package com.jake.duolauncher.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The part of the Liquid Glass icon look that cannot be baked into an icon's bitmap: a shallow shadow lifting it off the wallpaper. The rim
 * that catches the light and the glow over its top are drawn into the bitmap with the icon (see IconLighting), and the same setting, Launcher
 * settings › App icons › Icon lighting, turns both on. Snapshot state, so a change redraws icons without recomposing Home.
 */
internal object IconFinish {
    private val onState = mutableStateOf(true)
    var on: Boolean
        get() = onState.value
        set(value) { onState.value = value }
}

/** The icon's shadow, in its own squircle ([Corner.icon]), when icon lighting is on. */
internal fun Modifier.glassIcon(lift: Boolean = true): Modifier =
    if (!IconFinish.on || !lift) this
    else shadow(5.dp, Corner.icon, clip = false, ambientColor = Color.Black.copy(alpha = .35f), spotColor = Color.Black.copy(alpha = .35f))
