package com.jake.duolauncher.island

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jake.duolauncher.appearance.DisplayFace
import com.jake.duolauncher.appearance.DisplayFonts
import com.jake.duolauncher.appearance.Expressive
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** What the open island's headline says. The time is the default; the rest put the time in the small line opposite. */
internal enum class HeroContent(val label: String) { TIME("Time"), GREETING("Greeting"), DAY("Day"), DATE("Date") }

/**
 * The open island's headline, as the person has set it: what it says ([content], [greeting]). Its face and colour are the theme's
 * ([IslandTheme.face], [IslandTheme.heroTint]); these are personal, so they live here and are never written into a shared theme file.
 */
internal object IslandHero {
    const val MAX_GREETING = 24
    const val DEFAULT_GREETING = "hello"
    // In the "appearance" preferences, so a settings backup carries them. 0.134.0 kept them in a file of their own, read once if these are unset.
    private const val PREFS = "appearance"
    private const val KEY_CONTENT = "islandHeadline"
    private const val KEY_GREETING = "islandGreeting"
    private const val OLD_PREFS = "uno_island_hero"

    var content by mutableStateOf(HeroContent.TIME)
        private set
    var greeting by mutableStateOf(DEFAULT_GREETING)
        private set
    private var loaded = false

    /** Reads the saved settings the first time; returns this, so a composable can `remember` it. */
    fun load(context: Context): IslandHero {
        if (loaded) return this
        return reload(context)
    }

    /** Reads the saved settings again (after a settings backup is restored). */
    fun reload(context: Context): IslandHero {
        loaded = true
        val app = context.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains(KEY_CONTENT) && !p.contains(KEY_GREETING)) {
            val old = app.getSharedPreferences(OLD_PREFS, Context.MODE_PRIVATE)
            val edit = p.edit()
            old.getString("content", null)?.let { edit.putString(KEY_CONTENT, it) }
            old.getString("greeting", null)?.let { edit.putString(KEY_GREETING, it) }
            edit.apply()
        }
        content = HeroContent.values().firstOrNull { it.name == p.getString(KEY_CONTENT, null) } ?: HeroContent.TIME
        greeting = clean(p.getString(KEY_GREETING, null)) ?: DEFAULT_GREETING
        return this
    }

    fun setContent(context: Context, value: HeroContent) {
        content = value
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_CONTENT, value.name).apply()
    }

    fun setGreeting(context: Context, value: String) {
        greeting = clean(value) ?: DEFAULT_GREETING
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_GREETING, value.take(MAX_GREETING)).apply()
    }

    /** One line of plain text, at most [MAX_GREETING] characters; null when nothing is left. */
    fun clean(raw: String?): String? = raw?.replace(Regex("\\s+"), " ")?.filterNot { it.isISOControl() }?.trim()?.take(MAX_GREETING)?.takeIf { it.isNotEmpty() }

    /** Whether the headline is the plain Inter time it always was, so the island draws exactly what it drew before headlines could change. */
    fun isClassicTime(theme: IslandTheme): Boolean = content == HeroContent.TIME && face(theme) == DisplayFace.CLASSIC && theme.heroTint == HeroTint.INK

    /** The face the headline is drawn in: the theme's, unless Appearance › Style is Calm, where the launcher's text is one font throughout. */
    fun face(theme: IslandTheme): DisplayFace = face(theme.face, com.jake.duolauncher.appearance.Expression.now.displayTitles)
    fun face(themeFace: DisplayFace, displayType: Boolean): DisplayFace = if (displayType) themeFace else DisplayFace.CLASSIC

    /** The headline's words, for anything but the time. */
    fun words(content: HeroContent, greeting: String, now: LocalDateTime): String = when (content) {
        HeroContent.TIME -> ""
        HeroContent.GREETING -> greeting
        HeroContent.DAY -> now.format(DateTimeFormatter.ofPattern("EEEE"))
        HeroContent.DATE -> now.format(DateTimeFormatter.ofPattern("MMM d"))
    }

    /** The small line opposite the headline. With the time as the headline it is the date, as before; otherwise the time joins it, and a
     * date headline is not said twice. */
    fun sideLine(content: HeroContent, date: String, time: String, now: LocalDateTime): String = when (content) {
        HeroContent.TIME -> date
        HeroContent.GREETING -> "$time · $date"
        HeroContent.DAY -> "$time · ${now.format(DateTimeFormatter.ofPattern("MMM d"))}"
        HeroContent.DATE -> "$time · ${now.format(DateTimeFormatter.ofPattern("EEE"))}"
    }
}

/**
 * The headline drawn in the theme's face and colour. Every face sits in the same line box as Classic's at that size, so a script's swashes or a
 * tall face never change the island's height (the panel's height maths knows only that box); a long greeting is set smaller to fit, never cut.
 * With the script face a greeting writes itself in from the left as the island opens, after the face has mostly faded in.
 */
@Composable
internal fun IslandHeadline(theme: IslandTheme, content: HeroContent, words: String, digits: String, meridiem: String, sizeSp: Int,
    animationsOn: Boolean, modifier: Modifier = Modifier) {
    val face = DisplayFonts.resolve(IslandHero.face(theme))
    val base = Expressive.display(face, sizeSp, lineHeight = (sizeSp * 1.22f).toInt())
    val style = base.copy(
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        letterSpacing = if (face == DisplayFace.CLASSIC && !com.jake.duolauncher.appearance.UnoFonts.system) (if (sizeSp >= 30) -.7 else -.3).sp else base.letterSpacing,
    ).let { s ->
        when (theme.heroTint) {
            HeroTint.INK -> s.copy(color = theme.ink)
            HeroTint.HIGHLIGHT -> s.copy(color = theme.highlight)
            HeroTint.SWEEP -> s.copy(brush = Brush.linearGradient(listOf(theme.highlight, theme.playback)))
        }
    }
    val writeOn = face == DisplayFace.SCRIPT && content == HeroContent.GREETING && theme.playful && animationsOn
    val reveal = remember { Animatable(if (writeOn) 0f else 1f) }
    LaunchedEffect(writeOn) {
        if (writeOn) { kotlinx.coroutines.delay(140L); reveal.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) } else reveal.snapTo(1f)
    }
    val text = if (content == HeroContent.TIME) buildAnnotatedString {
        append(digits)
        if (meridiem.isNotEmpty()) withStyle(androidx.compose.ui.text.SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp,
            fontFamily = com.jake.duolauncher.appearance.UnoFonts.body, color = theme.ink.copy(alpha = .6f))) { append(meridiem) }
    } else buildAnnotatedString { append(words) }
    // The text is only as wide as its words, so a colour sweep runs across the words, not the whole panel; the box keeps the line's place.
    androidx.compose.foundation.layout.Box(modifier.fillMaxWidth()) {
    BasicText(text, Modifier
        .graphicsLayer { if (reveal.value < 1f) compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            val r = reveal.value
            drawContent()
            if (r < 1f) {
                // A soft-edged pen: what is left of the pen's position is drawn, a short ramp fades it in, the rest waits.
                val soft = 28.dp.toPx()
                val x = -soft + (size.width + soft) * r
                drawRect(Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = x, endX = x + soft), blendMode = BlendMode.DstIn)
            }
        }
        .testTag("island-headline"),
        style = style, maxLines = 1, softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = (sizeSp * .5f).sp, maxFontSize = style.fontSize, stepSize = 1.sp))
    }
}

/** The headline settings, as a group of the island's Style page: what it says, and (from the theme) its face and colour. */
@Composable
internal fun IslandHeadlineGroup(theme: IslandTheme, onTheme: (IslandTheme) -> Unit, onChanged: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    remember { IslandHero.load(context) }
    val face = DisplayFonts.resolve(theme.face)
    com.jake.duolauncher.SettingsGroup(header = "Headline",
        footer = "The big line on the open island. Fonts are the phone's own, and Bagel comes with Uno; a theme with a font this phone does not have uses Classic.") {
        row { com.jake.duolauncher.DropdownRow("Shows", HeroContent.values().toList(), IslandHero.content, { it.label }, "island-hero",
            optionTag = { "island-hero-${it.name}" }) { IslandHero.setContent(context, it); onChanged() } }
        if (IslandHero.content == HeroContent.GREETING) row {
            var draft by remember { mutableStateOf(IslandHero.greeting) }
            androidx.compose.foundation.layout.Box(Modifier.padding(vertical = 8.dp)) {
                com.jake.duolauncher.appearance.GlassField(draft, { draft = it.take(IslandHero.MAX_GREETING); IslandHero.setGreeting(context, draft) },
                    Modifier.fillMaxWidth().testTag("island-hero-greeting"), singleLine = true,
                    placeholder = { Text(IslandHero.DEFAULT_GREETING) })
            }
        }
        // Each font is drawn in itself in the menu, so the choice is seen, not imagined. Only the fonts this phone can draw are offered.
        // With Appearance › Style at Calm the launcher's text is one font throughout, so the headline is too; the choice is kept for later.
        val calm = !com.jake.duolauncher.appearance.Expression.now.displayTitles
        row { com.jake.duolauncher.DropdownRow("Font", DisplayFonts.available(), face, { it.label }, "island-face",
            value = if (calm) "Classic (Style is Calm)" else null, enabled = !calm,
            optionTag = { "island-face-${it.name}" },
            item = { f -> Text(f.label, style = Expressive.display(f, 17, lineHeight = 22), maxLines = 1) }) { onTheme(theme.copy(face = it)) } }
        row { com.jake.duolauncher.DropdownRow("Colour", HeroTint.values().toList(), theme.heroTint, { it.label }, "island-herotint",
            optionTag = { "island-herotint-${it.name}" }) { onTheme(theme.copy(heroTint = it)) } }
    }
}
