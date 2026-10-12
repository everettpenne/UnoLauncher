package com.jake.duolauncher.island

import com.jake.duolauncher.*
import android.content.Context
import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import org.json.JSONException
import org.json.JSONObject

/*
 * The island's look as data. A theme is a small, closed set of named choices (colours, a panel shape, a control shape,
 * a motion feel); the island reads them from [IslandThemes.active] and nothing in a theme can change what the island
 * does. That is what makes a theme file safe to install: it is parsed against a fixed list of fields, every colour is
 * checked for legibility, and a bad value is rejected with a reason rather than drawn.
 *
 * Deliberately not themable, whatever a file says:
 *  - the privacy indicators (camera, microphone, screen recording, VPN, USB data) keep their fixed colours, because
 *    they exist to be recognised at a glance;
 *  - the incoming-call controls (answer green, end red);
 *  - what the island shows, when, and what taps do.
 * The panel shapes are rounded rectangles only: the glass lens throws on other outlines (see docs/design.md).
 */

/** How round the island's body is. The scale applies to the radius the island would otherwise use. */
internal enum class PanelShape(val label: String, val scale: Float) {
    SOFT("Soft", 1f), ROUND("Round", 1.35f), SQUARE("Square", .45f)
}

/** The shape of the island's buttons and chips. */
internal enum class ControlShape(val label: String) { PILL("Pill"), ROUNDED("Rounded"), SQUARE("Square") }

/** How the island moves: [damping] multiplies a spring's damping ratio (below 1 overshoots more), [stiffness] its stiffness. */
internal enum class IslandMotion(val label: String, val damping: Float, val stiffness: Float) {
    CALM("Calm", 1f, .75f), STANDARD("Standard", 1f, 1f), SNAPPY("Snappy", 1f, 1.4f), BOUNCY("Bouncy", .78f, 1f)
}

internal data class IslandTheme(
    val name: String,
    val author: String = "",
    /** The body of the island. Must be dark (see [IslandThemeMath.MAX_FILL_LUMINANCE]) so the camera hole stays hidden. */
    val fill: Color = Color.Black,
    /** Text and icons. Must reach [IslandThemeMath.MIN_INK_CONTRAST] against [fill]. */
    val ink: Color = Color.White,
    val playback: Color = Color(0xFFFF375F),
    val charging: Color = Color(0xFF34C759),
    val timer: Color = Color(0xFFFF9F0A),
    val focus: Color = Color(0xFF7D7AFF),
    val panel: PanelShape = PanelShape.SOFT,
    val controls: ControlShape = ControlShape.PILL,
    val motion: IslandMotion = IslandMotion.STANDARD,
) {
    /** The colour an event's symbol is drawn in. Symbols that are privacy or safety signals keep their own. */
    fun tint(symbol: IslandSymbol): Color = when (symbol) {
        IslandSymbol.CHARGING -> charging
        IslandSymbol.TIMER, IslandSymbol.SILENT, IslandSymbol.AIRPLANE -> timer
        IslandSymbol.FOCUS -> focus
        IslandSymbol.APP, IslandSymbol.RINGER, IslandSymbol.VIBRATE, IslandSymbol.NOTIFICATION, IslandSymbol.HEADPHONES -> ink
        IslandSymbol.VPN, IslandSymbol.USB, IslandSymbol.RECORDING -> symbol.tint
    }

    /** The shape of a button or chip. Circle controls become rounded squares when [controls] is not [ControlShape.PILL]. */
    fun controlShape(): Shape = when (controls) {
        ControlShape.PILL -> RoundedCornerShape(percent = 50)
        ControlShape.ROUNDED -> RoundedCornerShape(14.dp)
        ControlShape.SQUARE -> RoundedCornerShape(6.dp)
    }

    /** A spring tuned by [motion]. Closing springs pass [tunedDamping] = false so the island still settles without overshoot. */
    fun <T> spring(dampingRatio: Float, stiffness: Float, tunedDamping: Boolean = true): SpringSpec<T> =
        androidx.compose.animation.core.spring(if (tunedDamping) (dampingRatio * motion.damping).coerceIn(.3f, 1.2f) else dampingRatio, stiffness * motion.stiffness)

    /** The chip fill: [ink] at a low alpha. */
    fun chip(alpha: Float = .14f): Color = ink.copy(alpha = alpha)

    companion object {
        val Classic = IslandTheme("Classic")
    }
}

/** Colour maths for the legibility rules, kept pure so they are unit-tested. */
internal object IslandThemeMath {
    /** The body must be darker than this relative luminance, or the camera hole would show against it. */
    const val MAX_FILL_LUMINANCE = .10f
    /** WCAG AA for body text. */
    const val MIN_INK_CONTRAST = 4.5f
    /** Accent colours are icons and short labels (WCAG's 3:1 for large or graphic content). */
    const val MIN_ACCENT_CONTRAST = 3f

    private fun channel(c: Float) = if (c <= .03928f) c / 12.92f else Math.pow(((c + .055f) / 1.055f).toDouble(), 2.4).toFloat()
    fun luminance(c: Color): Float = .2126f * channel(c.red) + .7152f * channel(c.green) + .0722f * channel(c.blue)
    fun contrast(a: Color, b: Color): Float {
        val l1 = luminance(a); val l2 = luminance(b)
        return (maxOf(l1, l2) + .05f) / (minOf(l1, l2) + .05f)
    }
    /** Why [theme] cannot be used, or null if it is fine. */
    fun problem(theme: IslandTheme): String? {
        if (luminance(theme.fill) > MAX_FILL_LUMINANCE) return "The island fill is too bright. It must be dark so the camera hole stays hidden."
        if (contrast(theme.ink, theme.fill) < MIN_INK_CONTRAST) return "The text colour does not stand out enough from the fill (it needs a contrast ratio of at least 4.5)."
        for ((label, c) in listOf("playback" to theme.playback, "charging" to theme.charging, "timer" to theme.timer, "focus" to theme.focus))
            if (contrast(c, theme.fill) < MIN_ACCENT_CONTRAST) return "The $label colour does not stand out enough from the fill (it needs a contrast ratio of at least 3)."
        return null
    }
}

/** Reading and writing a theme file: strict JSON, a fixed set of fields, size-limited, every value checked. */
internal object IslandThemeFile {
    const val FORMAT = "uno-island-theme"
    const val VERSION = 1
    const val MAX_BYTES = 8 * 1024
    private const val MAX_TEXT = 40

    sealed interface Result { data class Ok(val theme: IslandTheme) : Result; data class Rejected(val reason: String) : Result }

    private val colourKeys = listOf("fill", "ink", "playback", "charging", "timer", "focus")

    fun parse(bytes: ByteArray): Result {
        if (bytes.size > MAX_BYTES) return Result.Rejected("That file is too large for an island theme (the limit is ${MAX_BYTES / 1024} KB).")
        val text = runCatching { String(bytes, Charsets.UTF_8) }.getOrElse { return Result.Rejected("That file is not text.") }
        return parse(text)
    }

    fun parse(text: String): Result {
        val root = try { JSONObject(text) } catch (_: JSONException) { return Result.Rejected("That file is not a valid island theme (it is not JSON).") }
        if (root.optString("format") != FORMAT) return Result.Rejected("That file is not an Uno island theme.")
        val version = root.optInt("version", -1)
        if (version != VERSION) return Result.Rejected(if (version > VERSION) "That theme is for a newer version of Uno. Update Uno to use it." else "That theme file has no usable version.")
        val name = cleanText(root.optString("name")) ?: return Result.Rejected("The theme needs a name of 1 to $MAX_TEXT plain characters.")
        val author = cleanText(root.optString("author", "")) ?: if (root.optString("author", "").isEmpty()) "" else return Result.Rejected("The author must be at most $MAX_TEXT plain characters.")
        val colours = root.optJSONObject("colors") ?: JSONObject()
        val parsed = HashMap<String, Color>()
        for (key in colourKeys) {
            if (!colours.has(key)) continue
            parsed[key] = parseColour(colours.opt(key)) ?: return Result.Rejected("The $key colour must be written like \"#RRGGBB\".")
        }
        val shape = root.optJSONObject("shape") ?: JSONObject()
        val panel = enumOf<PanelShape>(shape, "panel") ?: return Result.Rejected("shape.panel must be one of: ${PanelShape.values().joinToString { it.name.lowercase() }}.")
        val controls = enumOf<ControlShape>(shape, "controls") ?: return Result.Rejected("shape.controls must be one of: ${ControlShape.values().joinToString { it.name.lowercase() }}.")
        val motion = if (!root.has("motion")) IslandMotion.STANDARD
            else IslandMotion.values().firstOrNull { it.name.equals(root.optString("motion"), ignoreCase = true) }
                ?: return Result.Rejected("motion must be one of: ${IslandMotion.values().joinToString { it.name.lowercase() }}.")
        val base = IslandTheme.Classic
        val theme = IslandTheme(name = name, author = author,
            fill = parsed["fill"] ?: base.fill, ink = parsed["ink"] ?: base.ink, playback = parsed["playback"] ?: base.playback,
            charging = parsed["charging"] ?: base.charging, timer = parsed["timer"] ?: base.timer, focus = parsed["focus"] ?: base.focus,
            panel = panel, controls = controls, motion = motion)
        IslandThemeMath.problem(theme)?.let { return Result.Rejected(it) }
        return Result.Ok(theme)
    }

    fun toJson(theme: IslandTheme): String = JSONObject().apply {
        put("format", FORMAT); put("version", VERSION); put("name", theme.name); put("author", theme.author)
        put("colors", JSONObject().apply {
            put("fill", hex(theme.fill)); put("ink", hex(theme.ink)); put("playback", hex(theme.playback))
            put("charging", hex(theme.charging)); put("timer", hex(theme.timer)); put("focus", hex(theme.focus))
        })
        put("shape", JSONObject().apply { put("panel", theme.panel.name.lowercase()); put("controls", theme.controls.name.lowercase()) })
        put("motion", theme.motion.name.lowercase())
    }.toString(2)

    private fun hex(c: Color) = "#%02X%02X%02X".format((c.red * 255f + .5f).toInt(), (c.green * 255f + .5f).toInt(), (c.blue * 255f + .5f).toInt())

    /** "#RRGGBB" only: no alpha (a transparent island part could never be legible), no names. */
    private fun parseColour(value: Any?): Color? {
        val s = value as? String ?: return null
        if (s.length != 7 || s[0] != '#' || s.drop(1).any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
        return Color(0xFF000000L or s.drop(1).toLong(16))
    }

    /** Null when the field is present but not one of the allowed names; the default when absent. */
    private inline fun <reified E : Enum<E>> enumOf(obj: JSONObject, key: String): E? {
        if (!obj.has(key)) return enumValues<E>().first()
        return enumValues<E>().firstOrNull { it.name.equals(obj.optString(key), ignoreCase = true) }
    }

    private fun cleanText(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty() || s.length > MAX_TEXT || s.any { it.isISOControl() }) return null
        return s
    }
}

/** The active island theme and the built-in ones. Snapshot state, so the island redraws when it changes. */
internal object IslandThemes {
    private var prefs: android.content.SharedPreferences? = null
    var active by mutableStateOf(IslandTheme.Classic)
        private set

    val presets: List<IslandTheme> = listOf(
        IslandTheme.Classic,
        IslandTheme("Midnight", fill = Color(0xFF05071A), ink = Color(0xFFE9EDFF), playback = Color(0xFF5E8BFF), charging = Color(0xFF4FD1C5),
            timer = Color(0xFFFFB86B), focus = Color(0xFF9D8CFF), panel = PanelShape.ROUND, controls = ControlShape.PILL, motion = IslandMotion.CALM),
        IslandTheme("Graphite", fill = Color(0xFF0F0F10), ink = Color(0xFFF2F2F2), playback = Color(0xFFE0E0E0), charging = Color(0xFFBFD8C0),
            timer = Color(0xFFE8C98A), focus = Color(0xFFB9B6E8), panel = PanelShape.SQUARE, controls = ControlShape.SQUARE, motion = IslandMotion.SNAPPY),
        IslandTheme("Neon", fill = Color(0xFF04040A), ink = Color(0xFFFFFFFF), playback = Color(0xFF00E5FF), charging = Color(0xFF39FF14),
            timer = Color(0xFFFF2BD6), focus = Color(0xFFB388FF), panel = PanelShape.ROUND, controls = ControlShape.ROUNDED, motion = IslandMotion.BOUNCY),
        IslandTheme("Amber", fill = Color(0xFF0B0805), ink = Color(0xFFFFF3DC), playback = Color(0xFFFFB000), charging = Color(0xFFFFD166),
            timer = Color(0xFFFF7A3D), focus = Color(0xFFE0B0FF), panel = PanelShape.SOFT, controls = ControlShape.ROUNDED, motion = IslandMotion.STANDARD),
    )

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("uno_island_theme", Context.MODE_PRIVATE)
        val saved = prefs?.getString("json", null)
        // A saved theme that no longer parses (a hand-edited backup, a rule that tightened) falls back to Classic.
        active = saved?.let { (IslandThemeFile.parse(it) as? IslandThemeFile.Result.Ok)?.theme } ?: IslandTheme.Classic
    }

    fun apply(theme: IslandTheme) {
        active = theme
        prefs?.edit()?.putString("json", IslandThemeFile.toJson(theme))?.apply()
    }

    fun reset() {
        active = IslandTheme.Classic
        prefs?.edit()?.remove("json")?.apply()
    }
}
