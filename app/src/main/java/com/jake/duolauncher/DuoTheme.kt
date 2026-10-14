package com.jake.duolauncher

import com.jake.duolauncher.island.*
import com.jake.duolauncher.feed.*
import com.jake.duolauncher.search.*
import com.jake.duolauncher.panel.*
import com.jake.duolauncher.appearance.*
import com.jake.duolauncher.folders.*
import com.jake.duolauncher.widgets.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.LocalIndication
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** The theme for a saved appearance (light or dark, and Material You when it is on). */
@Composable
fun DuoTheme(appearance: AppearanceState, content: @Composable () -> Unit) = DuoTheme(appearance.dark, appearance.materialYou, content)

@Composable
fun DuoTheme(dark: Boolean = false, materialYou: Boolean = false, content: @Composable () -> Unit) {
    val palette = if (dark) DarkDuoPalette else LightDuoPalette
    CompositionLocalProvider(LocalDuoPalette provides palette) {
        val context = androidx.compose.ui.platform.LocalContext.current
        MaterialTheme(shapes = DuoMaterialShapes, typography = DuoTypography, colorScheme = if (materialYou && dark) androidx.compose.material3.dynamicDarkColorScheme(context)
        else if (materialYou) androidx.compose.material3.dynamicLightColorScheme(context)
        else if (dark) darkColorScheme(primary = Color(0xFF9BC5D7), onPrimary = Color(0xFF12303D),
            surface = Color(0xFF17272E), onSurface = palette.ink, secondary = Color(0xFFD1BE98),
            secondaryContainer = Color(0xFF314852), onSecondaryContainer = palette.ink)
        else lightColorScheme(primary = Color(0xFF30596D), onPrimary = Color.White,
            surface = Color(0xFFF4F7F8), onSurface = palette.ink, secondary = Color(0xFF84775F),
            secondaryContainer = Color(0xFFDCE8ED), onSecondaryContainer = palette.ink),
            // MaterialTheme installs its own ripple as LocalIndication, so the no-op has to be provided inside it.
            content = { CompositionLocalProvider(LocalIndication provides NoIndication, content = content) })
    }
}
