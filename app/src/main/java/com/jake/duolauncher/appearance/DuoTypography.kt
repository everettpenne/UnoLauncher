package com.jake.duolauncher.appearance

import com.jake.duolauncher.*
import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
/** A scale style with the line height left to the font: for the island, whose open panel's height is budgeted on rows exactly as tall as their
 * text (see DynamicIsland's LocalTextStyle), so it keeps the scale's size, weight and tracking without its extra line height. */
internal val TextStyle.tight: TextStyle get() = copy(lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified)

/**
 * Which font the launcher's text is set in: Inter (bundled, the default), or the phone's own (Roboto on GrapheneOS and Pixels). Snapshot
 * state, so the whole launcher re-sets its text when it changes. Read once at start ([load], from the application) and saved on change.
 */
internal object UnoFonts {
    // In the "appearance" preferences, so a settings backup carries it. 0.134.0 kept it in a file of its own, read once if this has no value yet.
    private const val PREFS = "appearance"
    private const val KEY = "fontSystem"
    private const val OLD_PREFS = "uno_fonts"
    var system by androidx.compose.runtime.mutableStateOf(false)
        private set

    fun load(context: android.content.Context) {
        system = runCatching {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            if (prefs.contains(KEY)) prefs.getBoolean(KEY, false)
            else app.getSharedPreferences(OLD_PREFS, android.content.Context.MODE_PRIVATE).getBoolean("system", false).also { if (it) prefs.edit().putBoolean(KEY, true).apply() }
        }.getOrDefault(false)
    }

    fun setSystem(context: android.content.Context, value: Boolean) {
        system = value
        context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
    }

    /** The family body text is set in now. */
    val body: FontFamily get() = if (system) FontFamily.Default else InterFamily
}

/** The eleven steps of the scale in one family. Inter keeps iOS's tracking; the system font keeps its own spacing (none added), as Android sets it. */
internal class TypeScale(private val family: FontFamily, private val tracking: Boolean) {
    private fun style(size: Int, lineHeight: Int, trackingEm: Double, weight: Int = 400) = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(weight),
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = if (tracking) trackingEm.em else 0.em,
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

/** The scale in the font chosen in [UnoFonts]. Both sets are built once; reading a step allocates nothing. */
internal object UnoType {
    private val inter by lazy { TypeScale(InterFamily, tracking = true) }
    private val system by lazy { TypeScale(FontFamily.Default, tracking = false) }
    private val scale: TypeScale get() = if (UnoFonts.system) system else inter

    val largeTitle  get() = scale.largeTitle
    val title1      get() = scale.title1
    val title2      get() = scale.title2
    val title3      get() = scale.title3
    val headline    get() = scale.headline
    val body        get() = scale.body
    val callout     get() = scale.callout
    val subheadline get() = scale.subheadline
    val footnote    get() = scale.footnote
    val caption1    get() = scale.caption1
    val caption2    get() = scale.caption2
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
 * | headlineMedium | title1                    | page title (My feed, App Library) |
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
 *
 * Lazy, not built when this file is first touched: [UnoType] uses this file's [InterFamily], so when UnoType was the first thing used (the
 * island drawing before any theme), building it loaded this file, which read UnoType's styles while they were still null and crashed the
 * process at start.
 */
internal val DuoTypography: Typography get() = if (UnoFonts.system) systemTypography else interTypography

private val interTypography by lazy { typographyOf(TypeScale(InterFamily, tracking = true)) }
private val systemTypography by lazy { typographyOf(TypeScale(FontFamily.Default, tracking = false)) }

private fun typographyOf(t: TypeScale) = Typography(
    displayLarge   = t.largeTitle,
    displayMedium  = t.title1,
    displaySmall   = t.title2,
    headlineLarge  = t.title3,
    headlineMedium = t.title1,
    headlineSmall  = t.title2,
    titleLarge     = t.title3.copy(fontWeight = FontWeight.SemiBold),
    titleMedium    = t.headline,
    titleSmall     = t.subheadline.copy(fontWeight = FontWeight.Medium),
    bodyLarge      = t.body,
    bodyMedium     = t.callout,
    bodySmall      = t.footnote,
    labelLarge     = t.subheadline.copy(fontWeight = FontWeight.SemiBold),
    labelMedium    = t.caption1.copy(fontWeight = FontWeight.Medium),
    labelSmall     = t.caption2.copy(fontWeight = FontWeight.Medium),
)
