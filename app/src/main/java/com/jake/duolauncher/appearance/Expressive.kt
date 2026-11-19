package com.jake.duolauncher.appearance

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
