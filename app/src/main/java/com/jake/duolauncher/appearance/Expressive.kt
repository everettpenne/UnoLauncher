package com.jake.duolauncher.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The expressive layer: one display face for big moments (the open island's headline, a page title, a lead story), one family of soft
 * shapes, and one tonal ramp built round an accent. Everything else stays on the calm Inter scale and the neutral glass (see docs/design.md,
 * "Expressive layer"). Kept to large text: a script or a condensed face is a headline, never a paragraph or a button.
 */
internal enum class DisplayFace(val label: String) {
    /** The launcher's own text font (Inter, or the system font when Appearance › Font says so): exactly the look before display faces existed. */
    CLASSIC("Classic"),
    /** The phone's own font (Roboto on GrapheneOS), whatever the launcher's text is set in. */
    SYSTEM("System"),
    /** A flowing script (the phone's own cursive face), for a hand-written "hello". */
    SCRIPT("Script"),
    /** Roboto Flex at its heaviest and widest: big, round-shouldered numbers. */
    CHUNKY("Chunky"),
    /** Roboto Flex narrowed right down and set tall, as a lock-screen clock. */
    TALL("Tall"),
    /** A book serif. */
    EDITORIAL("Editorial"),
    /** A marker hand. */
    MARKER("Marker"),
    /** Bagel Fat One: soft, puffy, very heavy. Bundled with Uno (the only face that is), so every phone has it. */
    BAGEL("Bagel");
}

/** What a display face needs to be drawn: its family, weight, tracking, and how much bigger or smaller it is set than Inter at the same size. */
internal class DisplayStyle(val family: FontFamily, val weight: FontWeight, val trackingEm: Float, val scale: Float)

internal object DisplayFonts {
    /** The files each face is read from. These are the phone's own (AOSP ships them); nothing is bundled or downloaded. */
    private val files = mapOf(
        DisplayFace.SCRIPT to listOf("/system/fonts/DancingScript-Regular.ttf", "/system/fonts/DancingScript.ttf"),
        DisplayFace.CHUNKY to listOf("/system/fonts/RobotoFlex-Regular.ttf"),
        DisplayFace.TALL to listOf("/system/fonts/RobotoFlex-Regular.ttf"),
        DisplayFace.EDITORIAL to listOf("/system/fonts/NotoSerif-Bold.ttf", "/system/fonts/NotoSerif-Regular.ttf"),
        DisplayFace.MARKER to listOf("/system/fonts/ComingSoon.ttf"),
    )

    /** Overridden in unit tests, where there is no /system/fonts. */
    internal var exists: (String) -> Boolean = { File(it).canRead() }

    private fun fileFor(face: DisplayFace): String? = files[face]?.firstOrNull(exists)

    /** The faces this phone can draw. Classic always; the others only when their font is on the phone, so a choice never silently falls back. */
    fun available(): List<DisplayFace> = DisplayFace.values().filter { it in bundled || fileFor(it) != null }

    /** [face] if this phone has it, otherwise Classic: a theme made on another phone still loads, in Inter. */
    fun resolve(face: DisplayFace): DisplayFace = if (face in bundled || fileFor(face) != null) face else DisplayFace.CLASSIC

    /** The faces that ship inside Uno rather than being read from the phone. */
    private val bundled = setOf(DisplayFace.CLASSIC, DisplayFace.SYSTEM, DisplayFace.BAGEL)

    private val cache = HashMap<DisplayFace, DisplayStyle>()

    /**
     * The family for the big numbers in widgets when Appearance › Style is Expressive: Roboto Flex, heavy, at its normal width (the wide
     * Chunky cut would not fit "12:41" in a small widget). Its figures are all one width. Inter's boldest when the phone has no Roboto Flex.
     */
    @OptIn(ExperimentalTextApi::class)
    val numerals: DisplayStyle by lazy {
        val path = fileFor(DisplayFace.CHUNKY)
        if (path == null) DisplayStyle(InterFamily, FontWeight.Bold, -.03f, 1f)
        else DisplayStyle(FontFamily(Font(File(path), FontWeight.ExtraBold, variationSettings = FontVariation.Settings(
            FontVariation.weight(820), FontVariation.width(100f), FontVariation.Setting("opsz", 144f)))), FontWeight.ExtraBold, -.02f, 1f)
    }

    @OptIn(ExperimentalTextApi::class)
    fun style(face: DisplayFace): DisplayStyle {
        // Classic follows the text font chosen in Appearance, so it is never cached.
        if (face == DisplayFace.CLASSIC) return DisplayStyle(UnoFonts.body, FontWeight.SemiBold, if (UnoFonts.system) 0f else -.022f, 1f)
        return synchronized(cache) { cache.getOrPut(face) {
            val path = fileFor(face)
            fun fromFile(weight: FontWeight, vararg axes: FontVariation.Setting): FontFamily =
                FontFamily(Font(File(path!!), weight, variationSettings = FontVariation.Settings(*axes)))
            when (if (path == null && face !in bundled) DisplayFace.CLASSIC else face) {
                DisplayFace.CLASSIC -> DisplayStyle(InterFamily, FontWeight.SemiBold, -.022f, 1f)
                DisplayFace.SYSTEM -> DisplayStyle(FontFamily.Default, FontWeight.SemiBold, 0f, 1f)
                DisplayFace.SCRIPT -> DisplayStyle(fromFile(FontWeight.Bold, FontVariation.weight(700)), FontWeight.Bold, 0f, 1.18f)
                DisplayFace.CHUNKY -> DisplayStyle(fromFile(FontWeight.Black, FontVariation.weight(1000), FontVariation.width(151f),
                    FontVariation.Setting("opsz", 144f)), FontWeight.Black, -.03f, .92f)
                DisplayFace.TALL -> DisplayStyle(fromFile(FontWeight.SemiBold, FontVariation.weight(640), FontVariation.width(25f),
                    FontVariation.Setting("opsz", 144f), FontVariation.Setting("YTUC", 760f), FontVariation.Setting("YTFI", 788f)), FontWeight.SemiBold, 0f, 1.22f)
                DisplayFace.EDITORIAL -> DisplayStyle(fromFile(FontWeight.Bold), FontWeight.Bold, -.02f, 1f)
                DisplayFace.MARKER -> DisplayStyle(fromFile(FontWeight.Normal), FontWeight.Normal, 0f, 1.02f)
                DisplayFace.BAGEL -> DisplayStyle(FontFamily(Font(com.jake.duolauncher.R.font.bagel_fat_one, FontWeight.Normal)), FontWeight.Normal, 0f, 1f)
            }
        } }
    }
}

/** Display text tokens. The size is the line's, so a face that is drawn bigger or smaller (a script's small x-height) still fills it the same. */
internal object Expressive {
    /** A page's title (My feed, App Library): heavy and wide, the Pixel side of the launcher. */
    val pageTitle: TextStyle get() = display(DisplayFace.CHUNKY, 34)

    /** A lead story's headline. */
    val leadTitle: TextStyle get() = display(DisplayFace.CHUNKY, 24, lineHeight = 28)

    /** A big letter or numeral in a badge. */
    val badge: TextStyle get() = display(DisplayFace.CHUNKY, 20, lineHeight = 22)

    fun display(face: DisplayFace, size: Int, lineHeight: Int = (size * 1.18f).toInt()): TextStyle {
        val s = DisplayFonts.style(DisplayFonts.resolve(face))
        return TextStyle(fontFamily = s.family, fontWeight = s.weight, fontSize = (size * s.scale).sp, lineHeight = lineHeight.sp,
            letterSpacing = s.trackingEm.em, fontFeatureSettings = "tnum")
    }
}

/**
 * The expressive layer's shapes: Material 3 Expressive's own (see ExpressiveShapes.kt), for badges and markers only. A panel keeps its
 * continuous corner, since the glass lens cannot bend round other outlines.
 */
/** The names of things that open, in one place: a sheet's title and a dialog's. At Expressive they are in the display face (as the
 *  pages' titles are); otherwise Material's own, as they were. */
internal object Heads {
    val sheet: TextStyle @androidx.compose.runtime.Composable get() =
        if (Expression.now.displayHeads) Expressive.display(DisplayFace.CHUNKY, 23, lineHeight = 27) else androidx.compose.material3.MaterialTheme.typography.headlineSmall
    val title: TextStyle @androidx.compose.runtime.Composable get() =
        if (Expression.now.displayHeads) Expressive.display(DisplayFace.CHUNKY, 21, lineHeight = 25) else androidx.compose.material3.MaterialTheme.typography.titleLarge
}

internal object SoftShapes {
    /** The 9-sided cookie: counts and letters. */
    val Cookie9: Shape get() = ExpressiveShapes.Cookie9
    /** The 7-sided cookie: small source marks. */
    val Cookie7: Shape get() = ExpressiveShapes.Cookie7
    /** The 8-petal flower: decoration in a lead card's corner. */
    val Flower: Shape get() = ExpressiveShapes.Flower
}

/**
 * A small set of colours harmonised round an accent: the accent itself and its neighbours a fixed turn round the hue wheel, at the same
 * lightness, so a page can give each feed its own colour without a rainbow. Each comes with the ink that reads on it.
 */
internal object TonalRamp {
    class Tone(val container: Color, val onContainer: Color)

    private val turns = floatArrayOf(0f, 40f, -40f, 150f, 200f)

    fun tones(accent: Color, dark: Boolean): List<Tone> {
        val (h0, s0, _) = hsl(accent)
        return turns.map { turn ->
            val h = ((h0 + turn) % 360f + 360f) % 360f
            val s = s0.coerceIn(.45f, .80f)
            Tone(fromHsl(h, s, if (dark) .22f else .80f), fromHsl(h, s.coerceAtMost(.7f), if (dark) .94f else .15f))
        }
    }

    fun tone(accent: Color, dark: Boolean, index: Int): Tone = tones(accent, dark).let { it[((index % it.size) + it.size) % it.size] }

    /** The tone for a colour of hue [h] and saturation [s]: the same lightness as every other, so a set of them never reads as a rainbow. */
    fun toneOf(h: Float, s: Float, dark: Boolean, deep: Boolean = false): Tone {
        val sat = s.coerceIn(.45f, .80f)
        // [deep]: a shade further from white, for a small shape that has to stand out from a pale plate by its outline alone.
        return Tone(fromHsl(h, sat, if (dark) .22f else if (deep) .72f else .80f), fromHsl(h, sat.coerceAtMost(.7f), if (dark) .94f else if (deep) .12f else .15f))
    }

    internal fun hsl(c: Color): Triple<Float, Float, Float> {
        val max = maxOf(c.red, c.green, c.blue); val min = minOf(c.red, c.green, c.blue)
        val l = (max + min) / 2f
        if (max == min) return Triple(0f, 0f, l)
        val d = max - min
        val s = if (l > .5f) d / (2f - max - min) else d / (max + min)
        val h = when (max) {
            c.red -> ((c.green - c.blue) / d + (if (c.green < c.blue) 6f else 0f))
            c.green -> ((c.blue - c.red) / d + 2f)
            else -> ((c.red - c.green) / d + 4f)
        } * 60f
        return Triple(h, s, l)
    }

    internal fun fromHsl(h: Float, s: Float, l: Float): Color {
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r, g, b) = when ((h / 60f).toInt().coerceIn(0, 5)) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        return Color(r + m, g + m, b + m)
    }
}

/**
 * The launcher's tonal roles: which colour a thing takes for what it is, all from the one accent. Tonal colour fills containers that sit in
 * or on the glass (a tile that is on, a selected segment, a widget's accent); the glass itself stays neutral.
 */
internal object UnoTones {
    /** Something switched on (the torch tile): the accent itself, with the ink that reads on it. */
    fun on(accent: Color, accentInk: Color): TonalRamp.Tone = TonalRamp.Tone(accent, accentInk)

    /** The chosen one of a few (a ringer mode): the accent's own pastel, quieter than [on] so a panel can hold both. */
    fun selected(accent: Color): TonalRamp.Tone = TonalRamp.tone(accent, dark = false, index = 0)

    /** A widget's accent (a ring, a bar, a mark): the accent's neighbours round the ramp, by [index], so two widgets side by side differ. */
    fun widgetAccent(accent: Color, index: Int): TonalRamp.Tone = TonalRamp.tone(accent, dark = false, index = 1 + index)

    /**
     * The colour for a kind of thing (a kind of search result, a section of Settings, an action): by [index], so the same kind is the
     * same colour wherever it is met. At Expressive it is one of the three colours of [UnoPalette] (the accent and two from the
     * wallpaper); below that, the accent's own ramp, as [widgetAccent].
     */
    fun kind(accent: Color, index: Int, deep: Boolean = false): TonalRamp.Tone =
        if (Expression.now.palette >= 3) UnoPalette.tone(accent, UnoPalette.swatches, index, deep) else TonalRamp.tone(accent, dark = false, index = index)
}

/**
 * Expressive's wider palette, as Pixel's: the launcher's accent, and a second and third colour taken from the wallpaper. Every tonal
 * role draws on these three when [Expression.Values.palette] is 3 and on the accent alone otherwise; there is one tonal system
 * ([TonalRamp]'s tones, one lightness for all), and this only chooses which hues go into it.
 *
 * The wallpaper's colours are read where its accent is ([WallpaperAccents]) and kept in [swatches]; the choosing is pure and tested.
 */
internal object UnoPalette {
    /** The wallpaper's colours (ARGB), the most present first. Snapshot state: what draws with the palette redraws when it changes. */
    var swatches by androidx.compose.runtime.mutableStateOf<List<Int>>(emptyList())

    /** Two colours count as different when their hues are at least this far apart, in degrees. */
    const val MIN_APART = 35f
    /** A wallpaper colour greyer than this has no hue worth taking. */
    const val MIN_SATURATION = .18f
    /** Where the second and third colours are put when the wallpaper has none to give: the accent's neighbours, as [TonalRamp]'s. */
    private val FALLBACK_TURNS = floatArrayOf(40f, -40f)

    private fun apart(a: Float, b: Float): Float { val d = kotlin.math.abs(a - b) % 360f; return if (d > 180f) 360f - d else d }

    /**
     * The three hues, each with its saturation: the accent's, then the first two of [swatches] that are colourful and far enough
     * round the wheel from every hue already chosen. A wallpaper of one colour (or none read yet) gives the accent's neighbours.
     */
    fun hues(accent: Color, swatches: List<Int>): List<Pair<Float, Float>> {
        val (h0, s0, _) = TonalRamp.hsl(accent)
        val chosen = arrayListOf(h0 to s0)
        for (argb in swatches) {
            if (chosen.size == 3) break
            val (h, s, _) = TonalRamp.hsl(Color(argb))
            if (s < MIN_SATURATION || chosen.any { apart(it.first, h) < MIN_APART }) continue
            chosen += h to s
        }
        var turn = 0
        while (chosen.size < 3 && turn < FALLBACK_TURNS.size) {
            val h = ((h0 + FALLBACK_TURNS[turn++]) % 360f + 360f) % 360f
            // A wallpaper colour already sitting where the fallback would go is passed over for the next place.
            if (chosen.none { apart(it.first, h) < MIN_APART / 2f }) chosen += h to s0
        }
        while (chosen.size < 3) chosen += (((h0 + 150f + 50f * chosen.size) % 360f) to s0)
        return chosen
    }

    /** The tone for [index] of the three (it wraps): a pastel container and the dark ink that reads on it, in either theme. */
    fun tone(accent: Color, swatches: List<Int>, index: Int, deep: Boolean = false): TonalRamp.Tone {
        val (h, s) = hues(accent, swatches).let { it[((index % it.size) + it.size) % it.size] }
        return TonalRamp.toneOf(h, s, dark = false, deep = deep)
    }
}
