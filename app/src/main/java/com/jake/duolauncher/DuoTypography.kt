package com.jake.duolauncher

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/** Inter (SIL Open Font License 1.1, https://github.com/rsms/inter), bundled as one variable font
 * so the whole launcher reads in a single typeface on every Pixel, with no dependence on the
 * system font and no font download (the app contacts no server for it). One file serves every
 * weight the UI uses; each `Font` entry pins the variable weight axis to that weight.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
internal val InterFamily = FontFamily(
    listOf(300, 400, 500, 600, 700).map { weight ->
        Font(R.font.inter_variable, FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    }
)

/** Material's type scale, with Inter as its family. Sizes and spacing stay Material's. */
internal fun Typography.withFontFamily(family: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

internal val DuoTypography = Typography().withFontFamily(InterFamily)
