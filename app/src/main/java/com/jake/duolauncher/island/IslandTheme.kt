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

/** The outline of an app's glyph on the island (the Live Update and navigation icons). Rounded shapes only, like the panel. */
internal enum class IconShape(val label: String) {
    CIRCLE("Circle"), SQUIRCLE("Squircle"), ROUNDED("Rounded"), SQUARE("Square"), ARCH("Arch");

    fun shape(): Shape = when (this) {
        CIRCLE -> RoundedCornerShape(percent = 50)
        SQUIRCLE -> RoundedCornerShape(percent = 34)
        ROUNDED -> RoundedCornerShape(percent = 22)
        SQUARE -> RoundedCornerShape(percent = 8)
        ARCH -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 14, bottomStartPercent = 14)
    }
}

/**
 * How the island moves. [damping] multiplies a spring's damping ratio (below 1 overshoots more) and [stiffness] its stiffness; those
 * tune the island's smaller springs (an event's width, the compact ring, a button's press). Opening is tuned by the per-axis numbers:
 * the island's height and width are two separate springs, so a motion can have a personality. Closing always settles without
 * overshoot, so the island lands back on the camera; [closeStiffness] is how fast. [pulse] is how far the island swells when an event
 * arrives and how hard a press squashes it.
 *
 * Each of the livelier motions is a different cartoon:
 *  - **Bouncy** is a rubber ball: height and width overshoot together, a beat apart, and ring down.
 *  - **Jelly** is a wobbling blob: only the height springs, and the width is *coupled* to it the opposite way (taller means narrower,
 *    shorter means wider, [coupling] says how strongly), so the body breathes like squashed and stretched jelly.
 *  - **Droplet** is a water drop: the height shoots down fast while the width barely moves, so it hangs narrow and tall, then spreads
 *    wide, slowly and smoothly, with no wobble, as a drop does when it lands.
 */
internal enum class IslandMotion(
    val label: String, val damping: Float, val stiffness: Float,
    val heightDamping: Float, val heightStiffness: Float,
    val widthDamping: Float, val widthStiffness: Float,
    val closeStiffness: Float, val pulse: Float,
    val coupling: Float = 0f,
) {
    CALM("Calm", 1f, .75f, .95f, 200f, .95f, 190f, 300f, 1.05f),
    STANDARD("Standard", 1f, 1f, .8f, 330f, .84f, 310f, 420f, 1.09f),
    SNAPPY("Snappy", 1f, 1.4f, .7f, 560f, .74f, 520f, 640f, 1.1f),
    BOUNCY("Bouncy", .78f, 1f, .36f, 250f, .40f, 240f, 420f, 1.18f),
    JELLY("Jelly", .7f, 1f, .33f, 170f, .33f, 170f, 360f, 1.22f, coupling = 1.4f),
    DROPLET("Droplet", .85f, .9f, .55f, 300f, .8f, 70f, 300f, 1.14f);

    companion object {
        /**
         * The width a coupled motion (see [coupling]) gives the body for a height progress [h]. Below half open the two move together;
         * from there the width runs ahead of the height on the way up and comes back the other way as the height overshoots, so the body
         * is wide and flat while it is short and narrow and tall when it is stretched. Pure, so it is unit-tested.
         */
        fun coupledWidth(h: Float, coupling: Float): Float {
            val s = ((h - .5f) / .5f).coerceIn(0f, 1f)
            return h - coupling * (h - 1f) * s
        }
    }
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
    /** The colour of anything that has no colour of its own, or whose own is switched off by [appColors]. */
    val highlight: Color = Color(0xFF34C759),
    /** Whether an app's own colour (from its notification) is used. When off, every app wears [highlight]. */
    val appColors: Boolean = true,
    /** Colours chosen for particular apps by package name; they win over everything else. At most [IslandThemeFile.MAX_APPS]. */
    val appOverrides: Map<String, Color> = emptyMap(),
    val iconShape: IconShape = IconShape.CIRCLE,
    /** The colour of a progress bar or ring while it fills, and when it is full; null follows the app's accent. */
    val progressActive: Color? = null,
    val progressFinished: Color? = null,
    /** Whether a card with progress also says "NN%". */
    val showPercentage: Boolean = false,
    /** Navigation: the turn arrow on the right and the distance on the left, instead of the other way round. */
    val swapNavigation: Boolean = false,
    /** The colour of a navigation card's distance; null follows the app's accent. */
    val navigationColor: Color? = null,
) {
    /**
     * The one place an app's accent is decided. [requested] is the colour the app's notification asked for (null when it asked for none
     * or one too dark to read). An override for the package wins; otherwise the app's own colour, unless [appColors] is off; otherwise [highlight].
     */
    fun accent(packageName: String, requested: Color?): Color =
        appOverrides[packageName] ?: (if (appColors) requested else null) ?: highlight

    /** The glow behind a notification card: the app's override, its own icon colour ([tint], ARGB; null for a grey icon), or [highlight] when app colours are off. */
    fun wash(packageName: String, tint: Int?): Color? =
        appOverrides[packageName] ?: if (appColors) tint?.let { Color(it).copy(alpha = 1f) } else highlight

    /** The colour of progress that is [done] (full) or still filling, for the app whose accent is [accent]. */
    fun progress(accent: Color, done: Boolean): Color = (if (done) progressFinished else progressActive) ?: accent

    /** The colour an event's symbol is drawn in. Symbols that are privacy or safety signals keep their own. */
    fun tint(symbol: IslandSymbol): Color = when (symbol) {
        IslandSymbol.CHARGING -> charging
        IslandSymbol.TIMER, IslandSymbol.SILENT, IslandSymbol.AIRPLANE -> timer
        IslandSymbol.FOCUS -> focus
        IslandSymbol.APP, IslandSymbol.RINGER, IslandSymbol.VIBRATE, IslandSymbol.NOTIFICATION, IslandSymbol.HEADPHONES -> ink
        IslandSymbol.VPN, IslandSymbol.USB, IslandSymbol.RECORDING, IslandSymbol.BLUETOOTH -> symbol.tint
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

    /** The springs that open the island: one for its height, one for its width (see [IslandMotion]). Not clamped to the panel's size: overshoot shows. */
    fun heightSpring(): SpringSpec<Float> = androidx.compose.animation.core.spring(motion.heightDamping.coerceIn(.3f, 1.2f), motion.heightStiffness)
    fun widthSpring(): SpringSpec<Float> = androidx.compose.animation.core.spring(motion.widthDamping.coerceIn(.3f, 1.2f), motion.widthStiffness)
    /** The spring that closes it: critically damped, so it settles onto the camera without going past it. */
    fun closeSpring(): SpringSpec<Float> = androidx.compose.animation.core.spring(1f, motion.closeStiffness)

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
        val extras = buildList {
            add("highlight" to theme.highlight)
            theme.progressActive?.let { add("progress" to it) }; theme.progressFinished?.let { add("finished progress" to it) }
            theme.navigationColor?.let { add("navigation" to it) }
            theme.appOverrides.forEach { (pkg, c) -> add("$pkg" to c) }
        }
        for ((label, c) in listOf("playback" to theme.playback, "charging" to theme.charging, "timer" to theme.timer, "focus" to theme.focus) + extras)
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

    private val colourKeys = listOf("fill", "ink", "playback", "charging", "timer", "focus", "highlight")
    const val MAX_APPS = 16
    private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

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
        val appColors = if (!root.has("appColors")) true else (root.opt("appColors") as? Boolean) ?: return Result.Rejected("appColors must be true or false.")
        val overrides = LinkedHashMap<String, Color>()
        if (root.has("apps") && root.optJSONObject("apps") == null) return Result.Rejected("apps must map package names to colours.")
        root.optJSONObject("apps")?.let { apps ->
            if (apps.length() > MAX_APPS) return Result.Rejected("A theme can colour at most $MAX_APPS apps.")
            for (pkg in apps.keys()) {
                if (pkg.length > 100 || !PACKAGE.matches(pkg)) return Result.Rejected("\"$pkg\" is not an app's package name (like com.example.app).")
                overrides[pkg] = parseColour(apps.opt(pkg)) ?: return Result.Rejected("The colour for $pkg must be written like \"#RRGGBB\".")
            }
        }
        val icons = root.optJSONObject("icons") ?: JSONObject()
        val iconShape = if (!icons.has("shape")) IconShape.CIRCLE else IconShape.values().firstOrNull { it.name.equals(icons.optString("shape"), ignoreCase = true) }
            ?: return Result.Rejected("icons.shape must be one of: ${IconShape.values().joinToString { it.name.lowercase() }}.")
        val progress = root.optJSONObject("progress") ?: JSONObject()
        val active = if (progress.has("active")) parseColour(progress.opt("active")) ?: return Result.Rejected("progress.active must be written like \"#RRGGBB\".") else null
        val finished = if (progress.has("finished")) parseColour(progress.opt("finished")) ?: return Result.Rejected("progress.finished must be written like \"#RRGGBB\".") else null
        val percent = if (!progress.has("showPercentage")) false else (progress.opt("showPercentage") as? Boolean) ?: return Result.Rejected("progress.showPercentage must be true or false.")
        val navigation = root.optJSONObject("navigation") ?: JSONObject()
        val swap = if (!navigation.has("swapSides")) false else (navigation.opt("swapSides") as? Boolean) ?: return Result.Rejected("navigation.swapSides must be true or false.")
        val navColour = if (navigation.has("color")) parseColour(navigation.opt("color")) ?: return Result.Rejected("navigation.color must be written like \"#RRGGBB\".") else null
        val base = IslandTheme.Classic
        val theme = IslandTheme(name = name, author = author,
            fill = parsed["fill"] ?: base.fill, ink = parsed["ink"] ?: base.ink, playback = parsed["playback"] ?: base.playback,
            charging = parsed["charging"] ?: base.charging, timer = parsed["timer"] ?: base.timer, focus = parsed["focus"] ?: base.focus,
            panel = panel, controls = controls, motion = motion,
            highlight = parsed["highlight"] ?: base.highlight, appColors = appColors, appOverrides = overrides, iconShape = iconShape,
            progressActive = active, progressFinished = finished, showPercentage = percent, swapNavigation = swap, navigationColor = navColour)
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
        // The newer fields are written only when they differ from the defaults, so a plain theme stays as small and readable as before.
        if (theme.highlight != IslandTheme.Classic.highlight) (get("colors") as JSONObject).put("highlight", hex(theme.highlight))
        if (!theme.appColors) put("appColors", false)
        if (theme.appOverrides.isNotEmpty()) put("apps", JSONObject().apply { theme.appOverrides.forEach { (k, v) -> put(k, hex(v)) } })
        if (theme.iconShape != IconShape.CIRCLE) put("icons", JSONObject().put("shape", theme.iconShape.name.lowercase()))
        if (theme.progressActive != null || theme.progressFinished != null || theme.showPercentage) put("progress", JSONObject().apply {
            theme.progressActive?.let { put("active", hex(it)) }; theme.progressFinished?.let { put("finished", hex(it)) }
            if (theme.showPercentage) put("showPercentage", true) })
        if (theme.swapNavigation || theme.navigationColor != null) put("navigation", JSONObject().apply {
            if (theme.swapNavigation) put("swapSides", true); theme.navigationColor?.let { put("color", hex(it)) } })
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
        IslandTheme("Aurora", fill = Color(0xFF03070C), ink = Color(0xFFF0FBFF), playback = Color(0xFF5EEAD4), charging = Color(0xFF7CFFB2),
            timer = Color(0xFFFFD580), focus = Color(0xFFA78BFA), panel = PanelShape.ROUND, controls = ControlShape.PILL, motion = IslandMotion.CALM),
        IslandTheme("Rose", fill = Color(0xFF0C0508), ink = Color(0xFFFFEFF3), playback = Color(0xFFFF5D8F), charging = Color(0xFFFFB3C7),
            timer = Color(0xFFFFC857), focus = Color(0xFFD8A8FF), panel = PanelShape.SOFT, controls = ControlShape.ROUNDED, motion = IslandMotion.STANDARD),
        // The expressive ones: a motion with a personality, and colours to match.
        IslandTheme("Droplet", fill = Color(0xFF020B14), ink = Color(0xFFE8F6FF), playback = Color(0xFF4FC3F7), charging = Color(0xFF7CF2D2),
            timer = Color(0xFFFFD27A), focus = Color(0xFF9CB4FF), panel = PanelShape.ROUND, controls = ControlShape.PILL, motion = IslandMotion.DROPLET),
        IslandTheme("Jelly", fill = Color(0xFF12060F), ink = Color(0xFFFFF0FA), playback = Color(0xFFFF6FB5), charging = Color(0xFF8CFFC6),
            timer = Color(0xFFFFC857), focus = Color(0xFFC9A0FF), panel = PanelShape.ROUND, controls = ControlShape.ROUNDED, motion = IslandMotion.JELLY),
        IslandTheme("Spice", fill = Color(0xFF0B0704), ink = Color(0xFFF7E6C8), playback = Color(0xFF3FA9FF), charging = Color(0xFFE8A24C),
            timer = Color(0xFFFF8A3D), focus = Color(0xFF8FB8FF), panel = PanelShape.SOFT, controls = ControlShape.ROUNDED, motion = IslandMotion.DROPLET),
        IslandTheme("Forest", fill = Color(0xFF040A06), ink = Color(0xFFEAFBEF), playback = Color(0xFF5BE37D), charging = Color(0xFFA6F7B4),
            timer = Color(0xFFF2C35B), focus = Color(0xFF8FD3FF), panel = PanelShape.SOFT, controls = ControlShape.ROUNDED, motion = IslandMotion.SNAPPY),
    )

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("uno_island_theme", Context.MODE_PRIVATE)
        // A saved theme that no longer parses (a hand-edited backup, a rule that tightened) falls back to Classic.
        reload()
    }

    /** Re-reads the saved theme (after a settings restore). */
    fun reload() {
        val saved = prefs?.getString("json", null)
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
