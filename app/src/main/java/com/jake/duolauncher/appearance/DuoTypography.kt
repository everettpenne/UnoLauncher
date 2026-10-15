package com.jake.duolauncher.appearance

import com.jake.duolauncher.*
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

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

/**
 * Uno's type scale: Apple's HIG / SF Pro sizes, weights, line heights and tracking, set in Inter.
 * These are the one source of truth for type; screens should pull from here (directly, or through
 * the [DuoTypography] mapping below) instead of inventing sizes. The scale is iOS's eleven steps:
 *
 * | Token         | Size | Weight | Line | Tracking | iOS role |
 * | ---           | ---  | ---    | ---  | ---      | ---      |
 * | largeTitle    | 34   | 400    | 41   | +0.011   | Large Title |
 * | title1        | 28   | 400    | 34   | +0.013   | Title 1 |
 * | title2        | 22   | 400    | 28   | +0.016   | Title 2 |
 * | title3        | 20   | 400    | 25   | +0.019   | Title 3 |
 * | headline      | 17   | 600    | 22   | -0.024   | Headline |
 * | body          | 17   | 400    | 22   | -0.024   | Body |
 * | callout       | 16   | 400    | 21   | -0.020   | Callout |
 * | subheadline   | 15   | 400    | 20   | -0.016   | Subheadline |
 * | footnote      | 13   | 400    | 18   | -0.006   | Footnote |
 * | caption1      | 12   | 400    | 16   | 0        | Caption 1 |
 * | caption2      | 11   | 400    | 13   | +0.007   | Caption 2 |
 *
 * Sizes are sp (they respect the system font scale); tracking is in em so it scales with the size.
 * iOS's weights are Regular for nearly everything except Headline (Semibold); the medium (500) and
 * semibold (600) forms used for compact labels and buttons are taken from these tokens with
 * `copy(fontWeight = …)`, so a weight change stays anchored to the same size and rhythm.
 */
internal object UnoType {
    private fun style(size: Int, lineHeight: Int, trackingEm: Double, weight: Int = 400) = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight(weight),
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = trackingEm.em,
    )

    val largeTitle  = style(34, 41,  0.011)
    val title1      = style(28, 34,  0.013)
    val title2      = style(22, 28,  0.016)
    val title3      = style(20, 25,  0.019)
    val headline    = style(17, 22, -0.024, weight = 600)
    val body        = style(17, 22, -0.024)
    val callout     = style(16, 21, -0.020)
    val subheadline = style(15, 20, -0.016)
    val footnote    = style(13, 18, -0.006)
    val caption1    = style(12, 16,  0.0)
    val caption2    = style(11, 13,  0.007)
}

/**
 * Maps the iOS scale onto Material's `Typography` slots so existing `MaterialTheme.typography.*`
 * references keep working while their size, weight, line height and tracking follow Uno's scale.
 * The mapping is by the role each slot actually plays in this app (see `docs/design.md`, Typography):
 *
 * | Material slot  | iOS token (weight)        | Role in Uno |
 * | ---            | ---                       | --- |
 * | displayLarge   | largeTitle                | (spare) |
 * | displayMedium  | title1                    | (spare) |
 * | displaySmall   | title2                    | (spare) |
 * | headlineLarge  | title3                    | (spare) |
 * | headlineMedium | title1                    | page title (Discover, My feed) |
 * | headlineSmall  | title2                    | sheet title (All apps, Widgets, Welcome) |
 * | titleLarge     | title3 (600)              | dialog title |
 * | titleMedium    | headline                  | section header |
 * | titleSmall     | subheadline (500)         | group label / row title |
 * | bodyLarge      | body                      | body text, action-sheet row |
 * | bodyMedium     | callout                   | secondary body, guidance, empty state |
 * | bodySmall      | footnote                  | help line, subtitle, note |
 * | labelLarge     | subheadline (600)         | emphasised label / status |
 * | labelMedium    | caption1 (500)            | eyebrow (uppercase), meta |
 * | labelSmall     | caption2 (500)            | tiny meta |
 */
internal val DuoTypography = Typography(
    displayLarge   = UnoType.largeTitle,
    displayMedium  = UnoType.title1,
    displaySmall   = UnoType.title2,
    headlineLarge  = UnoType.title3,
    headlineMedium = UnoType.title1,
    headlineSmall  = UnoType.title2,
    titleLarge     = UnoType.title3.copy(fontWeight = FontWeight.SemiBold),
    titleMedium    = UnoType.headline,
    titleSmall     = UnoType.subheadline.copy(fontWeight = FontWeight.Medium),
    bodyLarge      = UnoType.body,
    bodyMedium     = UnoType.callout,
    bodySmall      = UnoType.footnote,
    labelLarge     = UnoType.subheadline.copy(fontWeight = FontWeight.SemiBold),
    labelMedium    = UnoType.caption1.copy(fontWeight = FontWeight.Medium),
    labelSmall     = UnoType.caption2.copy(fontWeight = FontWeight.Medium),
)
