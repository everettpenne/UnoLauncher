package com.jake.duolauncher.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * How far the launcher leans from iOS toward Pixel, as one choice (Appearance › Style). Glass is the material at every level; what moves is
 * what sits inside it: type, shape, colour and motion.
 */
internal enum class Personality(val label: String) {
    /** The iOS look: Inter throughout, one accent, capsule controls that only swell when pressed. */
    CALM("Calm"),
    /** The default: display titles and Material shapes on My feed and the App Library, tonal colour on what is selected or on. */
    BALANCED("Balanced"),
    /** The most Pixel: display numerals in widgets, controls and tiles that turn squarer when on, a livelier spring. */
    EXPRESSIVE("Expressive"),
}

/** The chosen [Personality]. Snapshot state, so the launcher redraws when it changes; read once at start and saved on change (as [UnoFonts]). */
internal object UnoPersonality {
    // In the "appearance" preferences, so a settings backup carries it with the rest of the look.
    private const val PREFS = "appearance"
    private const val KEY = "personality"
    var level by mutableStateOf(Personality.BALANCED)
        private set

    fun load(context: android.content.Context) {
        val saved = runCatching { context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString(KEY, null) }.getOrNull()
        level = Personality.values().firstOrNull { it.name == saved } ?: Personality.BALANCED
    }

    fun set(context: android.content.Context, value: Personality) {
        level = value
        context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putString(KEY, value.name).apply()
    }

    /** For tests: the level without a context. */
    internal fun setForTest(value: Personality) { level = value }
}

/**
 * What a [Personality] means, as the things screens ask about. Screens read these and never the level itself, so what each level does is
 * decided in one place (and unit-tested there).
 */
internal object Expression {
    fun of(level: Personality) = Values(level)

    class Values(private val level: Personality) {
        /** Page titles (My feed, App Library) in the display face, not Inter. */
        val displayTitles get() = level != Personality.CALM
        /** Counts and letters in Material's shapes, and the shape-morphing loader, not a pill and a spinning arc. */
        val shapedBadges get() = level != Personality.CALM
        /** Tonal colour: 0 the single accent only, 1 on what is selected or on, 2 on widget accents too. */
        val tonal get() = when (level) { Personality.CALM -> 0; Personality.BALANCED -> 1; Personality.EXPRESSIVE -> 2 }
        /** How far a control's shape changes with its state, 0 (never) to 1 (a capsule turns into a rounded square). */
        val morph get() = when (level) { Personality.CALM -> 0f; Personality.BALANCED -> .35f; Personality.EXPRESSIVE -> 1f }
        /** Whether a selected or on control rests in its squarer shape, not only while pressed. */
        val restsSquare get() = level == Personality.EXPRESSIVE
        /** The press spring's damping, as a factor on the calm one: lower is bouncier. */
        val bounce get() = when (level) { Personality.CALM -> 1f; Personality.BALANCED -> .92f; Personality.EXPRESSIVE -> .72f }
        /** The big numbers in Uno's widgets in the display face. */
        val displayNumerals get() = level == Personality.EXPRESSIVE
        /** Sliders with a thick bar of colour for a track, as Material's, under the glass thumb. */
        val thickTracks get() = level == Personality.EXPRESSIVE
        /** A Material shape can be chosen for a small Uno widget. */
        val widgetShapes get() = level != Personality.CALM
    }

    private val calm = Values(Personality.CALM)
    private val balanced = Values(Personality.BALANCED)
    private val expressive = Values(Personality.EXPRESSIVE)

    /** The values for the level chosen now. Reading it in composition or drawing subscribes to the choice. */
    val now: Values get() = when (UnoPersonality.level) { Personality.CALM -> calm; Personality.BALANCED -> balanced; Personality.EXPRESSIVE -> expressive }
}
