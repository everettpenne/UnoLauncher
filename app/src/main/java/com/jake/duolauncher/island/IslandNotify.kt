package com.jake.duolauncher.island

import com.jake.duolauncher.*
import android.app.PendingIntent
import android.graphics.Bitmap

/** How much of a new notification the island's drop-down card shows. Name only is the original behaviour: a flash of the app's icon and
 * name, never what the notification says.
 */
internal enum class PeekLevel { NAME, TITLE, TEXT;
    companion object { fun of(value: Int) = entries.getOrElse(value) { NAME } }
}

/** A new notification, reduced to what the card may show. [title] and [text] are null unless the level asked for them. */
internal data class PeekNotification(
    val packageName: String, val appLabel: String, val title: String?, val text: String?,
    val icon: Bitmap?, val open: PendingIntent?, val seconds: Int,
    /** The app icon's colour (ARGB), used to wash the card and ring the pill; null for a grey icon. */
    val tint: Int? = null,
    /** The buttons the notification offers (Reply, Archive...), when the card shows a title; empty otherwise. */
    val actions: List<PeekAction> = emptyList(),
    /** A turn-by-turn instruction, drawn large (the arrow, the distance and the street) so it can be read while driving. */
    val navigation: Boolean = false,
)

/** Pure rules for what the drop-down card shows, kept apart so they are unit-tested. */
internal object PeekContent {
    const val MAX_WORDS = 30
    const val MAX_WORD = 40
    const val MAX_TEXT = 140
    const val MAX_TITLE = 60

    /** Blocked words ("spoiler protection"): one per comma or line, lower-cased, no blanks or repeats. */
    fun parseWords(raw: String?): List<String> = raw.orEmpty().split(',', '\n').map { it.trim().take(MAX_WORD).lowercase() }
        .filter { it.isNotEmpty() }.distinct().take(MAX_WORDS)

    fun serializeWords(words: List<String>) = words.joinToString(", ")

    /** True when any of [texts] contains a blocked word (case ignored). */
    fun blocked(words: List<String>, vararg texts: String?): Boolean =
        words.isNotEmpty() && texts.any { t -> t != null && words.any { w -> t.contains(w, ignoreCase = true) } }

    /** The title and text a card may show at [level], or null when it should be only the app's name: at the first level, when a blocked
     * word appears anywhere in the notification, or when there is nothing to show. The text is cut short.
     */
    fun visible(level: PeekLevel, title: String?, text: String?, words: List<String>): Pair<String?, String?>? {
        if (level == PeekLevel.NAME) return null
        if (blocked(words, title, text)) return null
        val shownTitle = title?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_TITLE)
        val shownText = if (level == PeekLevel.TEXT) text?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_TEXT) else null
        return if (shownTitle == null && shownText == null) null else shownTitle to shownText
    }
}

/** How long a card stays, per app. A Set of seconds the person cycles through; the default applies to apps with no choice. */
internal object PeekTimeouts {
    const val DEFAULT_SECONDS = 6
    val CHOICES = listOf(3, 6, 10, 15)

    /** "pkg=10,other=3": unreadable parts and seconds outside [CHOICES] are skipped. */
    fun parse(raw: String?): Map<String, Int> = raw.orEmpty().split(',').mapNotNull { part ->
        val (pkg, secs) = part.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        val s = secs.trim().toIntOrNull()?.takeIf { it in CHOICES } ?: return@mapNotNull null
        pkg.trim().takeIf { it.isNotEmpty() }?.let { it to s }
    }.toMap()

    fun serialize(map: Map<String, Int>) = map.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }

    fun seconds(map: Map<String, Int>, pkg: String) = map[pkg] ?: DEFAULT_SECONDS

    /** The next choice for an app, going default, 3, 6, 10, 15 and back to the default (which drops its entry). */
    fun cycle(map: Map<String, Int>, pkg: String): Map<String, Int> {
        val current = map[pkg]
        val next = if (current == null) CHOICES.first() else CHOICES.getOrNull(CHOICES.indexOf(current) + 1)
        return if (next == null) map - pkg else map + (pkg to next)
    }

    fun label(map: Map<String, Int>, pkg: String) = map[pkg]?.let { "${it}s" } ?: "Auto"
}

/** [ArtTint] on a small copy of the artwork, so it costs next to nothing. */
internal fun android.graphics.Bitmap.artTintArgb(): Int? = runCatching {
    val small = android.graphics.Bitmap.createScaledBitmap(this, 24, 24, true)
    val pixels = IntArray(24 * 24)
    small.getPixels(pixels, 0, 24, 0, 0, 24, 24)
    if (small !== this) small.recycle()
    ArtTint.fromPixels(pixels)
}.getOrNull()
