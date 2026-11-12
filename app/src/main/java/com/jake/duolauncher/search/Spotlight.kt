package com.jake.duolauncher.search

import android.util.Log
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.island.*
import com.jake.duolauncher.feed.*
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.layout
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import kotlinx.coroutines.withContext

/** The panel's maximum height: 560 dp minus the requested ~25 px (24 dp — the closest 8 dp-grid step), so a
 * search reads as the island grown larger rather than a sheet. The results list scrolls inside it. */
private const val SPOTLIGHT_MAX_HEIGHT_DP = 536f

/** The bottom strip of the panel where a swipe up closes it (a 48 dp touch target, mirroring the island's swipe-up-close). */
private val SPOTLIGHT_SWIPE_CLOSE_ZONE = 48.dp

/** What the swipe-up-close is heading for: the panel's emerge progress (1 = fully open, 0 = collapsed into the pill), and
 * whether the finger is driving it (dragging snaps 1:1; releasing springs back to 1 = open, or is held where the finger left it
 * when the swipe closes). One value so the gesture can change both without an intermediate state. */
private data class SpotlightSwipe(val progress: Float, val dragging: Boolean,
    /** For a release that closes: the panel's speed at the release, in progress per second (negative, toward the pill). */
    val velocity: Float = 0f)

/** Ranking for Spotlight's app results. Pure, so it is unit-tested. */
internal object SpotlightRank {
    const val MAX_APPS = 6
    const val MAX_FEED = 4
    const val MAX_SUGGESTED = 4

    /** How a label matches a query, best first. [NONE] drops the app from the results. */
    enum class Tier { PREFIX, WORD_PREFIX, INITIALS, CONTAINS, FUZZY, TYPO, NONE }

    private fun words(label: String): List<String> {
        // "YouTube Music" -> you, tube, music: split on separators and on lower-to-upper humps.
        val spaced = label.replace(Regex("""([a-z0-9])([A-Z])"""), "$1 $2")
        return spaced.lowercase().split(' ', '-', '.', '_', '/').filter { it.isNotEmpty() }
    }

    /** Whether an app's description ([alias]) matches [query]: three letters at least, or one or two would find nearly every app. The App
     *  Library uses the same rule, so both searches find the same apps. */
    fun matchesAlias(alias: String?, query: String): Boolean {
        val q = query.trim()
        return alias != null && q.length >= 3 && alias.contains(q, ignoreCase = true)
    }

    fun tier(label: String, query: String, alias: String? = null): Tier {
        val q = query.trim().lowercase().replace(" ", "")
        if (q.isEmpty()) return Tier.NONE
        val l = label.lowercase()
        val squashed = l.replace(" ", "")
        val parts = words(label)
        return when {
            l.startsWith(query.trim().lowercase()) || squashed.startsWith(q) -> Tier.PREFIX
            parts.any { it.startsWith(q) } -> Tier.WORD_PREFIX
            // "ytm" finds YouTube Music, "gm" Google Maps: the query is a run of leading letters of the words.
            q.length >= 2 && parts.size >= 2 && initialsMatch(parts, q) -> Tier.INITIALS
            l.contains(q) || squashed.contains(q) -> Tier.CONTAINS
            // The app's own description, so "notes" finds Mentat: a plain-language alias beneath the Dune name. Three letters
            // at least, or "a" would pull in every app whose description contains it.
            matchesAlias(alias, query) -> Tier.CONTAINS
            // Only for longer queries, or two letters would match half the drawer.
            q.length >= 3 && isSubsequence(q, squashed) -> Tier.FUZZY
            // A single slip still finds the app: "gogle" for Google, "calndar" for Calendar. Short queries stay strict.
            typo(q, parts, squashed) -> Tier.TYPO
            else -> Tier.NONE
        }
    }

    /** The span of [query] that appears literally in [label], to bold in the row; null when the match is initials/fuzzy/typo. */
    fun highlightRange(label: String, query: String): IntRange? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val at = label.lowercase().indexOf(q.lowercase())
        return if (at >= 0) at until (at + q.length) else null
    }

    private fun initialsMatch(parts: List<String>, q: String): Boolean {
        var at = 0
        for (part in parts) {
            if (at >= q.length) break
            var taken = 0
            while (at + taken < q.length && taken < part.length && part[taken] == q[at + taken]) taken++
            if (taken == 0) return false
            at += taken
        }
        return at == q.length
    }

    private fun isSubsequence(q: String, text: String): Boolean {
        var at = 0
        for (c in text) if (at < q.length && c == q[at]) at++
        return at == q.length
    }

    /** Whether [q] is a near-miss of the label: one cheap slip (a swap, a doubled letter, one edit) for short queries,
     * a little more for longer ones. Kept here rather than in the keyboard package so Search does not depend on the
     * dictionary-backed [com.jake.duolauncher.keyboard.WordEngine]. */
    private fun typo(q: String, parts: List<String>, squashed: String): Boolean {
        if (q.length < 4) return false
        val budget = if (q.length <= 5) 1.0 else 1.5
        return parts.any { editCost(q, it, budget) <= budget } || editCost(q, squashed, budget) <= budget
    }

    /** Damerau-Levenshtein distance favouring the same cheap slips the keyboard does (a swap of two letters, a doubled or
     * undoubled letter) with plain substitution costs. Returns above [limit] as soon as it cannot finish within it. */
    private fun editCost(a: String, b: String, limit: Double): Double {
        val n = a.length; val m = b.length
        if (kotlin.math.abs(n - m) > limit) return limit + 1
        var prevPrev = DoubleArray(m + 1); var prev = DoubleArray(m + 1) { it.toDouble() }; var cur = DoubleArray(m + 1)
        for (i in 1..n) {
            cur[0] = i.toDouble()
            var rowMin = cur[0]
            for (j in 1..m) {
                val sub = if (a[i - 1] == b[j - 1]) 0.0 else 1.0
                val missing = if ((j >= 2 && b[j - 1] == b[j - 2]) || (j < m && b[j - 1] == b[j])) 0.5 else 1.0
                val extra = if (a[i - 1] == a.getOrNull(i - 2) || a[i - 1] == a.getOrNull(i)) 0.5 else 1.0
                var v = minOf(minOf(prev[j] + extra, cur[j - 1] + missing), prev[j - 1] + sub)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prevPrev[j - 2] + 0.8)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > limit) return limit + 1
            val t = prevPrev; prevPrev = prev; prev = cur; cur = t
        }
        return prev[m]
    }

    /** Frecency: launches count for more when recent. A launch loses half its weight every [HALF_LIFE_DAYS] days. */
    const val HALF_LIFE_DAYS = 14.0
    /** At or below this, an app is treated as "not really used": it can still match, but it never crowds out a daily driver. */
    const val SUGGESTION_FRECENCY = .05
    fun frecency(count: Int, lastLaunchMs: Long, nowMs: Long): Double {
        if (count <= 0) return 0.0
        val ageDays = ((nowMs - lastLaunchMs).coerceAtLeast(0L)) / 86_400_000.0
        return count * Math.pow(0.5, ageDays / HALF_LIFE_DAYS)
    }

    /**
     * An app you have not launched enough to matter is demoted one tier, so a daily-used app whose name merely *contains* the
     * word ("maps" → "Organic Maps") can outrank a never-used app that merely *starts* with it. The boundary is one tier only:
     * a demoted prefix never falls below a used word-prefix by more than its own merit, and a typo match is never demoted away.
     */
    fun effectiveTier(tier: Tier, used: Boolean): Tier = if (used) tier else when (tier) {
        Tier.PREFIX -> Tier.WORD_PREFIX
        Tier.WORD_PREFIX -> Tier.INITIALS
        Tier.INITIALS -> Tier.CONTAINS
        Tier.CONTAINS -> Tier.FUZZY
        else -> tier
    }

    /**
     * Best match first: how the name matches (with a frecency-aware adjustment for never-used apps), then how often and
     * recently you open the app, then alphabetical. A blank query matches nothing (see [suggested] for what Spotlight offers
     * before you type).
     */
    fun <T> apps(apps: List<T>, query: String, label: (T) -> String, frecency: (T) -> Double = { 0.0 }, alias: (T) -> String? = { null }): List<T> {
        if (query.isBlank()) return emptyList()
        return apps.map { it to tier(label(it), query, alias(it)) }.filter { it.second != Tier.NONE }
            .sortedWith(compareBy<Pair<T, Tier>>(
                { effectiveTier(it.second, frecency(it.first) > SUGGESTION_FRECENCY) },
                { -frecency(it.first) },
                { label(it.first).lowercase() }))
            .map { it.first }.take(MAX_APPS)
    }

    /** What to offer before anything is typed: the apps you open most, if you have opened any. */
    fun <T> suggested(apps: List<T>, frecency: (T) -> Double): List<T> =
        apps.filter { frecency(it) > SUGGESTION_FRECENCY }.sortedByDescending(frecency).take(MAX_SUGGESTED)

    /** Saved feed entries whose title or summary mentions every word of the query. */
    fun feed(entries: List<FeedEntry>, query: String): List<FeedEntry> {
        val words = query.trim().lowercase().split(' ').filter { it.length >= 2 }
        if (words.isEmpty()) return emptyList()
        return entries.filter { e -> val text = (e.title + " " + e.summary).lowercase(); words.all { it in text } }.take(MAX_FEED)
    }
}

/**
 * How the search panel grows out of the island. At [progress] 0 the panel is exactly the island's body where it was (the open island, if search
 * was opened from it; the pill otherwise); at 1 it is the full panel. On the way out it shrinks into the settled pill, exactly where the island
 * draws it, so the island takes over without a jump.
 *
 * It is a real change of shape: the panel's outline moves from one rectangle to the other, with the island's own corner rule at every size, and
 * the content stays where it will be and is uncovered. It used to be the finished panel scaled down to the pill's size, a different amount each
 * way, which squashed its corners flat (a pill-sized panel had nearly square corners) and squeezed the text, and it grew from a pill drawn without
 * the status bar grid's edges, a few dp off the real island.
 */
/** The look of the search bar the island becomes. */
internal object SpotlightBar {
    /** The card's height following its results as the query changes: quick enough to keep up with typing, and settling without a bounce. */
    val cardResize = androidx.compose.animation.core.spring<IntSize>(dampingRatio = .92f, stiffness = 420f, visibilityThreshold = IntSize(1, 1))

    /** A row arriving, going or moving as the results change, so they do not pop while the card is resizing. */
    val rowFadeIn = androidx.compose.animation.core.tween<Float>(170, delayMillis = 40)
    val rowFadeOut = androidx.compose.animation.core.tween<Float>(90)
    val rowMove = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = .92f, stiffness = 420f,
        visibilityThreshold = androidx.compose.ui.unit.IntOffset(1, 1))
    /** The search bar's text: larger than a result row's, since it is the one thing in the bar. */
    val fieldStyle get() = UnoType.callout.copy(fontSize = 21.sp, lineHeight = 26.sp)

    /** The bar's fill, down its height: solid black to the bottom of the camera's band ([solidTo], 0 to 1), so the camera stays hidden, then thinning into the glass under it.
     *  Enough stays at the bottom for white text to read on a bright wallpaper. With no glass behind it, it stays black. */
    fun barFill(solidTo: Float, hasGlass: Boolean): androidx.compose.ui.graphics.Brush =
        if (!hasGlass) androidx.compose.ui.graphics.SolidColor(Color.Black)
        else androidx.compose.ui.graphics.Brush.verticalGradient(*barStops(solidTo))

    /** [barFill] with no glass under it (search over another app): it thins less, since nothing frosts the page behind the white text. */
    fun seeThroughFill(solidTo: Float): androidx.compose.ui.graphics.Brush {
        val s = solidTo.coerceIn(0f, .98f)
        return androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Black, s to Color.Black,
            (s + (1f - s) * .55f).coerceAtMost(.99f) to Color.Black.copy(alpha = .86f), 1f to Color.Black.copy(alpha = .72f))
    }

    /** The ramp of [barFill], which the open island's body shares so the two are the same glass: [fill] solid to [solidTo], then thinning. */
    fun barStops(solidTo: Float, fill: Color = Color.Black): Array<Pair<Float, Color>> {
        val s = solidTo.coerceIn(0f, .98f)
        return arrayOf(0f to fill, s to fill, (s + (1f - s) * .55f).coerceAtMost(.99f) to fill.copy(alpha = .68f), 1f to fill.copy(alpha = .42f))
    }
}

internal object SpotlightEmerge {
    /** The panel's outline at [progress], from [start] (the island's body, in the panel's own px) to the whole [panel]. */
    fun rect(progress: Float, start: PxRect, panel: PxRect): PxRect = IslandMorph.lerp(start, panel, progress.coerceIn(0f, 1f))

    /**
     * How far an element [depth] of the way down the panel (0 the search field at the top, 1 the bottom) has come out, at [progress]: none until
     * the outline has room for it, then eased in over [REVEAL_SPAN]. Each starts a little later the lower it sits, so the field comes out of
     * the island first and the rows follow it down the panel, all of them there by the time it is open; closing, they go back bottom first.
     * They all used to fade in together over the second half, which read as a finished page appearing, not one being drawn out of the island.
     */
    fun reveal(progress: Float, depth: Float): Float { val u = phase(progress, depth); return 1f - (1f - u) * (1f - u) * (1f - u) }

    /** Where an element is on its way into place (for its drop and its size): as [reveal], but it runs a touch past and settles back, so it lands
     *  rather than stops. */
    fun settle(progress: Float, depth: Float): Float {
        val v = phase(progress, depth) - 1f
        return 1f + (SETTLE + 1f) * v * v * v + SETTLE * v * v
    }

    private fun phase(progress: Float, depth: Float): Float =
        ((progress - (REVEAL_FIRST + REVEAL_STAGGER * depth.coerceIn(0f, 1f))) / REVEAL_SPAN).coerceIn(0f, 1f)
    private const val REVEAL_FIRST = .18f
    private const val REVEAL_STAGGER = .4f
    private const val REVEAL_SPAN = .42f
    private const val SETTLE = 1.4f

    /**
     * How much longer search takes to open than the island does, on top of the island's own tempo ([com.jake.duolauncher.island.IslandMotion.TEMPO]):
     * it grows into a panel several times the open island's height in about the same time, so it moved several times as far each frame and
     * read as quicker and jumpier than the island. Its outline's stiffness is divided by the square of this ([outlineOpenStiffness]) and the
     * contents' clock ([CONTENT_MS]) runs this much longer.
     */
    const val OPEN_TEMPO = 1.3f

    /** The outline's opening spring stiffness: Uno's open spring at the island's tempo, and [OPEN_TEMPO] slower again. */
    fun outlineOpenStiffness(): Float =
        com.jake.duolauncher.island.IslandMotion.tempoStiffness(com.jake.duolauncher.appearance.UnoMotion.OPEN_STIFFNESS) / (OPEN_TEMPO * OPEN_TEMPO)

    /**
     * The contents come out on their own clock, over this long, while the outline springs open (about 290 ms, see [OPEN_TEMPO]): timed off the outline itself,
     * even staggered, the whole cascade was over in a few frames and read as the page appearing at once. Closing follows the outline (see
     * [Spotlight]'s contentProgress), so a swipe up takes the contents with it.
     */
    const val CONTENT_MS = 806

    /**
     * Closing is the opening in reverse: the contents run their clock back down over [CLOSE_CONTENT_MS], so the bottom rows go first and the
     * field last, each shrinking, fading and rising back toward the island; the outline holds for [OUTLINE_CLOSE_DELAY_MS] and then shrinks into
     * the pill over [OUTLINE_CLOSE_MS], so it is still drawing them in as the field goes. They used to stay put while the outline closed over
     * them in a few frames, which read as the page being cut away rather than taken back.
     */
    const val CLOSE_CONTENT_MS = 310
    const val OUTLINE_CLOSE_DELAY_MS = 155
    const val OUTLINE_CLOSE_MS = 390

    /**
     * Closing is a spring rather than a timed curve: it starts from wherever the panel is, at whatever speed it is moving (a flick up keeps its
     * pace into the collapse), and settles into the pill. Critically damped, so it never shrinks past the pill and back. The stiffness gives
     * about the old close's length (~400 ms) from fully open at rest. Used by Home's search and by search over an app.
     */
    const val CLOSE_STIFFNESS = 300f
    fun <T> closeSpring(visibilityThreshold: T? = null) =
        androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = CLOSE_STIFFNESS, visibilityThreshold = visibilityThreshold)

    /** How far above its place an element is while it comes out ([settle]), in dp: it drops out of the island, the lower ones from further. */
    fun dropDp(settle: Float, depth: Float): Float = -(1f - settle) * (10f + 16f * depth.coerceIn(0f, 1f))

    /** The search field widens from the pill's width to its own as it comes out ([settle]; its share of its own width). */
    fun fieldWidth(settle: Float): Float = .78f + .22f * settle
}

/**
 * [SpotlightEmerge.reveal] on an element of the panel: it fades in, drops into its place and settles to full size as the panel grows, timed by how
 * far down the panel ([panelTop], [panelHeight], window px) it sits; [field] also widens it from the pill's width. Read in the draw phase only,
 * so the growth redraws and never recomposes.
 */
private fun Modifier.emerging(on: Boolean, progress: () -> Float, panelTop: () -> Float, panelHeight: () -> Float, field: Boolean = false): Modifier {
    if (!on) return this
    return this.then(Modifier.composed {
        var top by remember { mutableFloatStateOf(0f) }
        Modifier.onGloballyPositioned { top = it.positionInWindow().y }.graphicsLayer {
            val h = panelHeight()
            val depth = if (h > 0f) (top - panelTop()) / h else 0f
            val p = progress()
            alpha = SpotlightEmerge.reveal(p, depth)
            val m = SpotlightEmerge.settle(p, depth)
            translationY = SpotlightEmerge.dropDp(m, depth).dp.toPx()
            val s = .94f + .06f * m
            scaleX = if (field) SpotlightEmerge.fieldWidth(m) else s; scaleY = s
            transformOrigin = TransformOrigin(.5f, 0f)
        }
    })
}

/** What [emerging] reads, handed down to the panel's rows and headers so each comes out on its own. Null outside search (the App Library shares
 *  HandoffRow), where nothing emerges. */
private class EmergeSource(val on: Boolean, val progress: () -> Float, val panelTop: () -> Float, val panelHeight: () -> Float)
private val LocalEmerge = staticCompositionLocalOf<EmergeSource?> { null }

/** [emerging] with what search handed down, or nothing outside it. */
@Composable
private fun Modifier.emergingHere(field: Boolean = false): Modifier =
    LocalEmerge.current?.let { emerging(it.on, it.progress, it.panelTop, it.panelHeight, field) } ?: this

/** What the search key should do, decided pure so it is unit-tested: the top app (as on iOS), else the typed action,
 * else the first contact for "call"/"text", else the Ask Qwen row if it is offered, else a web search of the words. */
internal object SpotlightEnter {
    enum class Route { APP, QUICK, CONTACT, ASK, WEB, NONE }
    fun route(hasApps: Boolean, hasQuick: Boolean, hasContacts: Boolean, queryBlank: Boolean, hasAsk: Boolean = false): Route = when {
        hasApps -> Route.APP
        hasQuick -> Route.QUICK
        hasContacts -> Route.CONTACT
        hasAsk && !queryBlank -> Route.ASK
        !queryBlank -> Route.WEB
        else -> Route.NONE
    }
}

/** What to say when "call"/"text" can't find anyone. */
internal object ContactHint {
    const val OFF = "Turn on Contacts in search to call or text by name"
    fun none(name: String) = "No contact matches “$name”."
}

/** Where an Ask Qwen request is: thinking, an action to offer as a row (with what it resolved to), or an answer streaming in. */
private sealed interface AskView {
    data object Thinking : AskView
    class Act(val action: QuickAction, val app: AppEntry? = null, val contacts: List<ContactPhone> = emptyList()) : AskView
    data class Answer(val text: String, val done: Boolean, val error: String? = null) : AskView
    /** What was asked is understood but can't be done here ([why]); [settings]: turning something on in search settings would fix it. */
    data class Info(val why: String, val settings: Boolean = false) : AskView
}

/** Search from anywhere on Home: a panel that grows out of the island over whatever page you are on. One box finds apps, contacts (if you
 * turned that on), answers to sums and conversions, and headlines you already saved from your feeds. Nothing leaves
 * the device, and it never touches the web.
 */
@Composable
internal fun Spotlight(
    glass: PageGlass?,
    apps: List<AppEntry>,
    hiddenApps: Set<String>,
    feedEntries: List<FeedEntry>,
    contactsEnabled: Boolean,
    handoff: HandoffResolver,
    onLaunch: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    /** Launches with the app icon's place in the window, so the app opens out of the icon as it does from Home; null uses [onLaunch]. */
    onLaunchFrom: ((AppEntry, android.graphics.Rect?) -> Unit)? = null,
    onOpenFeedLink: (String) -> Unit,
    onOpenContact: (ContactResult) -> Unit,
    onDismiss: () -> Unit,
    /** 0 = the island's body, 1 = fully open; driven by the caller's enter/exit transition, and read only where the panel draws. */
    progress: () -> Float = { 1f },
    /** True from the moment Spotlight is opened until the moment its close begins (the open/close
     *  decision, not the animation progress). Drives focus and the keyboard, so the keyboard is
     *  requested on the same frame the emerge starts and hidden as the dismiss starts. */
    visible: Boolean = true,
    /** The settled island pill, as drawn, that the panel shrinks back into (null without an island: the panel then grows from the top edge). */
    pill: IslandFrame? = null,
    /** Where the island's body was when search opened (the open island, if it was opened from there); it grows out of this. Null: the pill. */
    from: IslandFrame? = null,
    /** The slot of a Note widget on Home, if one is placed; the "note" action writes to it. */
    noteSlot: Int? = null,
    /** Whether Focus is on right now, for "focus on"/"focus off" to know if there is anything to do. */
    focusOn: () -> Boolean = { false },
    /** Flips Focus (and its ringer change), as the control panel does. */
    onToggleFocus: () -> Unit = {},
    /** Uno Lab's assistant, when the person switched it on and Lab is installed; null hides Ask Qwen and binds nothing. */
    assistant: AssistantClient? = null,
    /** Opens Launcher settings at Search (to turn contacts on for "call"/"text"). */
    onOpenSearchSettings: () -> Unit = {},
    /** What the phone's facts are read from, for the assistant (null: questions go without them). */
    contextEnv: com.jake.duolauncher.search.context.ContextEnv? = null,
    /** Whether opening the panel takes the keyboard. A floating host that focuses its field before Compose has placed the panel makes the
     *  field's scroll-to-caret throw, so it leaves the field for a tap instead. */
    autoFocus: Boolean = true,
    /** No glass to draw (search over another app, whose pixels can't be sampled): the bar still thins from black into what is behind it,
     *  and the card is a translucent dark sheet, so the page shows through as Home does through its glass. */
    seeThrough: Boolean = false,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Request/hide the keyboard in lock-step with the open/close decision (not after the emerge
    // completes). On open the field is focused and the IME shown on the same frame the island
    // begins to grow; on close the IME is hidden the moment the dismiss begins, rather than when
    // the field is disposed at the end of the exit transition.
    LaunchedEffect(visible, autoFocus) {
        if (visible && autoFocus) { focus.requestFocus(); keyboard?.show() } else keyboard?.hide()
    }
    val history = remember { SpotlightHistory(context) }
    val scores = remember { history.scores() }
    // Hidden apps are never suggested or ranked; typing one's exact name finds it.
    val searchable = remember(apps, hiddenApps, query) { HiddenApps.shown(apps, hiddenApps, query, { it.id }, { it.label }) }
    val appResults = remember(searchable, query) { SpotlightRank.apps(searchable, query, { it.label }, { scores[it.id] ?: 0.0 }, { it.description }) }
    val suggested = remember(apps, hiddenApps) { SpotlightRank.suggested(apps.filter { it.id !in hiddenApps }) { scores[it.id] ?: 0.0 } }
    val iconBounds = remember { HashMap<String, android.graphics.Rect>() }
    val launch = { app: AppEntry -> history.record(app.id); onLaunchFrom?.invoke(app, iconBounds[app.id]) ?: onLaunch(app) }
    var copied by remember(query) { mutableStateOf(false) }
    val feedResults = remember(feedEntries, query) { SpotlightRank.feed(feedEntries, query) }
    val smart = remember(query) { SearchSmarts.answer(query) }
    val quick = remember(query) { SpotlightSources.quickAction(query) }
    // The quick actions that need no argument, offered before anything is typed so they are one tap instead of a command.
    val idle = remember { SpotlightSources.idleActions() }
    val actions = rememberActionResolver(noteSlot)
    // "call"/"text" resolve to contact rows (one per number found) rather than a single row; a "note" action needs a
    // Note widget on Home to write to. Everything else is one row, the top hit.
    val contactKind = remember(quick) {
        when (quick) { is QuickAction.Call -> ContactActionKind.DIAL; is QuickAction.Text -> ContactActionKind.TEXT; else -> null }
    }
    val singleQuick = remember(quick, noteSlot) {
        quick?.takeUnless { it is QuickAction.Call || it is QuickAction.Text }?.takeUnless { it is QuickAction.Note && noteSlot == null }
    }
    val actionName = remember(quick) {
        when (quick) { is QuickAction.Call -> quick.name; is QuickAction.Text -> quick.name; else -> null }
    }
    var actionContacts by remember { mutableStateOf(emptyList<ContactPhone>()) }
    LaunchedEffect(actionName, contactsEnabled) {
        if (!contactsEnabled || actionName == null || actionName.trim().length < ContactMatch.MIN_QUERY) {
            actionContacts = emptyList(); return@LaunchedEffect
        }
        delay(120)
        actionContacts = withContext(Dispatchers.IO) { ContactActions.search(context, actionName) }
    }
    val settingsHits = remember(query) { SpotlightSources.settings(query) }
    // The stores and browser search can hand off to, with their icons: worked out off the main thread as search opens, not per keystroke.
    LaunchedEffect(handoff, visible) { if (visible) handoff.prepare() }
    // Uno Lab's assistant: bound (and the model warmed) only while search is open with the setting on, unbound as it closes.
    DisposableEffect(assistant, visible) {
        if (assistant != null && visible) assistant.open()
        onDispose { assistant?.close() }
    }
    val assistantState by (assistant?.state ?: remember { kotlinx.coroutines.flow.MutableStateFlow(AssistantState.OFF) }).collectAsState()
    val askOffered = assistant != null && AskPolicy.offer(query, smart, quick, assistantState)
    // What was asked, and where the answer is: reset whenever the query changes, which also cancels a running answer.
    var askedFor by remember(query) { mutableStateOf<String?>(null) }
    var askView by remember(query) { mutableStateOf<AskView?>(null) }
    var askCopied by remember(query) { mutableStateOf(false) }
    // The turn itself lives in AssistantSession, so search can be closed while the model works. This hands it the question and the
    // facts an answer would need (read up front: they cost ~25 ms, and a possible action never sees them).
    LaunchedEffect(askedFor) {
        val q = askedFor ?: return@LaunchedEffect
        // A greeting or a thank-you is answered from a table: no action round trip, no facts read, no model, no session.
        SmallTalk.replyTo(q)?.let { reply -> askView = AskView.Answer(reply, done = true); return@LaunchedEffect }
        if (assistant == null) return@LaunchedEffect
        askView = AskView.Thinking
        val facts = contextEnv?.let { env ->
            withContext(Dispatchers.IO) {
                runCatching { com.jake.duolauncher.search.context.PhoneContext.build(q, action = false, env) }.getOrDefault("")
            }
        }.orEmpty()
        AssistantSession.start(context, q, facts)
    }
    // Whatever the session has for this question: running, an answer that arrived while search was closed, or an action to offer.
    // Shown for the question on screen, or for any of them while the field is empty (the panel has just been reopened).
    val sessionNow = AssistantSession.state
    LaunchedEffect(sessionNow, query) {
        val s = sessionNow
        val mine = when (s) {
            is AssistantSession.State.Running -> query.isBlank() || s.query == query
            is AssistantSession.State.Ready -> query.isBlank() || s.query == query
            is AssistantSession.State.Failed -> query.isBlank() || s.query == query
            AssistantSession.State.Idle -> false
        }
        if (!mine) return@LaunchedEffect
        when (s) {
            is AssistantSession.State.Running -> askView = AskView.Thinking
            is AssistantSession.State.Failed -> askView = AskView.Answer("", done = true, error = s.why)
            is AssistantSession.State.Ready -> askView = if (s.actionJson == null) {
                AnswerText.clean(s.answer.orEmpty())?.let { AskView.Answer(it, done = true) } ?: AskView.Info(AnswerText.UNCLEAR)
            } else when (val outcome = AssistantIntent.read(s.actionJson, s.query)) {
                is AssistantIntent.Outcome.Cannot -> AskView.Info(outcome.why)
                is AssistantIntent.Outcome.None -> null
                is AssistantIntent.Outcome.Act -> when (val action = outcome.action) {
                    is QuickAction.OpenApp -> SpotlightRank.apps(searchable, action.name, { it.label }, { scores[it.id] ?: 0.0 }, { it.description })
                        .firstOrNull()?.let { AskView.Act(QuickAction.OpenApp(it.label, "Open ${it.label}"), app = it) }
                        ?: AskView.Info("No app called “${action.name}” is installed.")
                    is QuickAction.Call, is QuickAction.Text -> {
                        val name = if (action is QuickAction.Call) action.name else (action as QuickAction.Text).name
                        if (!contactsEnabled) AskView.Info(ContactHint.OFF, settings = true)
                        else withContext(Dispatchers.IO) { ContactActions.search(context, name) }.takeIf { it.isNotEmpty() }
                            ?.let { AskView.Act(action, contacts = it) } ?: AskView.Info(ContactHint.none(name))
                    }
                    is QuickAction.Note -> if (noteSlot != null) AskView.Act(action) else AskView.Info("Add a Note widget to Home to save notes from search.")
                    else -> AskView.Act(action)
                }
            }
            AssistantSession.State.Idle -> Unit
        }
    }
    val askAnswered = askView is AskView.Act || askView is AskView.Info || (askView as? AskView.Answer)?.let { it.text.isNotBlank() } == true
    // A recognised quick action is itself an answer, so it also keeps the "search elsewhere" rows away; so does an assistant answer.
    val suggestions = remember(query, appResults.size, smart, quick, handoff, askAnswered) {
        handoff.targets(query, appResults.size, smart != null || quick != null || askAnswered)
    }
    var contacts by remember { mutableStateOf(emptyList<ContactResult>()) }
    LaunchedEffect(query, contactsEnabled) {
        if (!contactsEnabled || query.trim().length < ContactMatch.MIN_QUERY) { contacts = emptyList(); return@LaunchedEffect }
        delay(150)
        contacts = withContext(Dispatchers.IO) { ContactsSearch.search(context, query) }
    }
    // The island's own look: a true-black body (the pill's colour, so the panel reads as the pill grown larger), white ink and
    // a hairline rim. The corners equal the expanded island's (IslandGeometry.cornerCapDp, the shared source of truth) at full
    // size, and morph with the emerge so the panel never reads as a rounded rectangle while it is still pill-sized: a capsule at
    // progress 0, the cap at progress 1. Without an island there is no pill to grow from, so it stays at the cap.
    val ink = Color.White
    val quiet = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val capCorner = IslandGeometry.cornerCapDp.dp
    val capShape = ContinuousShape(capCorner)
    val panelScale = IslandThemes.active.panel.scale
    // Swipe-up-to-close, mirroring the island's swipe-up-close: the finger's upward travel is low-passed and mapped to a reduced
    // emerge progress (IslandDrag.closeFraction), so the panel shrinks back into the pill as the finger moves instead of sliding off
    // the top. On release it closes past the distance or a flick (IslandDrag.closeDecide) and the exit transition finishes the
    // collapse from where the finger left it; a short slow swipe springs back open. With Android's animations off, tracking is raw
    // and the spring-back snaps, as the island does.
    val viewConfiguration = LocalViewConfiguration.current
    val animationsOn = remember {
        android.provider.Settings.Global.getFloat(context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    // The gesture writes this plain state (no suspension, so it works in the pointerInput's restricted scope); the collector
    // below drives the Animatable outside composition (the island's peek-jitter contract), so a drag never recomposes the panel.
    var swipeTarget by remember { mutableStateOf(SpotlightSwipe(1f, dragging = false)) }
    val swipeProgress = remember { Animatable(1f) }
    LaunchedEffect(animationsOn) {
        snapshotFlow { swipeTarget }.collectLatest { target ->
            if (target.dragging || !animationsOn) swipeProgress.snapTo(target.progress)
            // A release that closes carries on at the finger's speed into the pill, on the close spring.
            else if (target.progress == 0f) swipeProgress.animateTo(0f, SpotlightEmerge.closeSpring(.001f), initialVelocity = target.velocity)
            else swipeProgress.animateTo(target.progress, UnoMotion.settle())
        }
    }
    // The collapse under the finger takes the smaller of the parent's enter/exit progress and the gesture's own 1 → 0 progress,
    // so the panel shrinks into the pill even while the parent transition still reads fully open. Read only in draw lambdas
    // (graphicsLayer / drawBehind), never in composition, so a swipe changes drawing and not the panel's composition.
    val effectiveProgress: () -> Float = { minOf(progress(), swipeProgress.value) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    var panelPos by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val pillTop = pill?.top ?: 0f
    val pillBottom = pill?.let { it.top + it.height } ?: 0f
    // Which island body the outline starts from: where the island was when search opened, until the panel has fully opened or begins to close;
    // from then on the settled pill, so a close (a tap outside, a swipe up, a launch) always shrinks into where the island will be drawn. A close
    // that begins before the panel finished opening glides between the two instead of jumping.
    val towardPill = remember { Animatable(if (from == null) 1f else 0f) }
    LaunchedEffect(visible) {
        if (!visible) towardPill.animateTo(1f, spring(dampingRatio = 1f, stiffness = 900f))
        else if (towardPill.value < 1f) { snapshotFlow { progress() }.first { it >= .999f }; towardPill.snapTo(1f) }
    }
    // The panel's outline now, in its own px, or null before it has been measured (or without an island).
    fun outline(): PxRect? {
        val rest = pill ?: return null
        if (panelSize.width <= 0) return null
        val startFrame = IslandMorph.lerp(IslandMorph.rectOf(from ?: rest), IslandMorph.rectOf(rest), towardPill.value)
        val start = PxRect(startFrame.left - panelPos.x, startFrame.top - panelPos.y, startFrame.right - panelPos.x, startFrame.bottom - panelPos.y)
        return SpotlightEmerge.rect(effectiveProgress(), start, PxRect(0f, 0f, panelSize.width.toFloat(), panelSize.height.toFloat()))
    }

    // The panel's parent may sit below the top of the window (under the status bar or the camera cutout, depending on how the window is
    // laid out), so the island's position in window coordinates would land too low, and the dimming would stop short of the top edge,
    // leaving a lighter strip there. The scrim and panel are laid out in window coordinates instead: a full-window box shifted back by
    // wherever this parent really is.
    val view = androidx.compose.ui.platform.LocalView.current
    val windowSize = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    // Search as iOS 27 does it: the island itself becomes the search bar, with the results on a card of their own floating under it and Home left in view.
    // With no island to grow out of, it is still one panel over a dimmed screen. What is searched, and the morph out of the island, are shared.
    val floating = pill != null
    val panelChrome = Modifier
            .onGloballyPositioned { panelSize = it.size; panelPos = it.positionInWindow() }
            .graphicsLayer {
                transformOrigin = TransformOrigin(.5f, 0f)
                val p = effectiveProgress()
                val r = outline()
                if (r != null) {
                    // Clipped to the outline, which moves from the island's body to the panel with the island's corners at every size.
                    shape = MorphRectShape(r, IslandMorph.cornerPx(r.width, r.height, density.density, panelScale))
                } else if (pill != null) {
                    // Not measured yet: nothing to show until it is (one frame), rather than a full panel flashing up.
                    alpha = 0f
                } else {
                    alpha = p.coerceIn(0f, 1f)
                    scaleX = .9f + .1f * p; scaleY = .9f + .1f * p
                    shape = capShape
                }
                clip = true
            }
            .drawWithContent {
                // The floating bar paints its own fill, which is only black at the top (see barFill).
                if (!floating) drawRect(Color.Black)
                drawContent()
                // The hairline rim, on the outline as it grows (the island has none on its pill, so it comes in with the panel).
                val r = outline()
                val rim = Color.White.copy(alpha = .10f * (if (pill != null) effectiveProgress().coerceIn(0f, 1f) else 1f))
                if (r != null) drawPath(IslandMorph.outlinePath(r.left, r.top, r.width, r.height, IslandMorph.cornerPx(r.width, r.height, density.density, panelScale), density.density),
                    rim, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                else if (pill == null) drawPath(continuousPath(0f, 0f, size.width, size.height, capCorner.toPx()),
                    rim, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            }
            .clickable(interactionSource = quiet, indication = null) {}
    val swipeToClose = Modifier
            .pointerInput(Unit) {
                // A swipe up from the bottom of the panel collapses it back into the island, like the island's swipe-up-close: the
                // finger is tracked in the Initial pass (so the panel owns the drag before the result list can scroll it), its upward
                // travel is mapped to a reduced emerge progress (IslandDrag.closeFraction), and the release decision reuses the
                // island's pure IslandDrag. A tap within the slop is left alone, so rows and the field still receive it.
                val slop = viewConfiguration.touchSlop
                val zonePx = SPOTLIGHT_SWIPE_CLOSE_ZONE.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val tracker = VelocityTracker().apply { addPointerInputChange(down) }
                    val fromBottom = down.position.y >= size.height - zonePx
                    // Full-close travel is the panel's height, so the emerge tracks the finger one-to-one, as the island's does.
                    val travelPx = size.height.toFloat().coerceAtLeast(1f)
                    var closing = false
                    var closeFrom = 1f
                    var moved = false
                    var last = down.position
                    var lastEventTime = down.uptimeMillis
                    var fingerFilter: IslandDrag.Filter? = null
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPointerInputChange(change)
                            last = change.position
                            val dt = ((change.uptimeMillis - lastEventTime).coerceAtLeast(0L)) / 1000f
                            lastEventTime = change.uptimeMillis
                            val delta = last - down.position
                            if (!moved && (abs(delta.x) > slop || abs(delta.y) > slop)) moved = true
                            if (fromBottom && moved && !closing && -delta.y > slop && abs(delta.y) > abs(delta.x)) {
                                closing = true
                                closeFrom = swipeProgress.value
                                if (animationsOn) fingerFilter = IslandDrag.Filter()
                            }
                            if (closing) {
                                val dy = fingerFilter?.step(dt, delta.y) ?: delta.y
                                swipeTarget = SpotlightSwipe(IslandDrag.closeFraction(-dy, travelPx, from = closeFrom), dragging = true)
                                change.consume()
                            }
                            if (!change.pressed) break
                        }
                    } finally {
                        if (closing) {
                            val upPx = down.position.y - last.y
                            val velocityY = tracker.calculateVelocity().y
                            if (IslandDrag.closeDecide(upPx / density.density, -velocityY, slop / density.density) == IslandDrag.CloseOutcome.CLOSE) {
                                // Carry on from where the finger left it, at its speed: the panel's own progress springs into the pill
                                // (the parent's close runs too, and the smaller of the two is drawn), so a flick never pauses before closing.
                                swipeTarget = SpotlightSwipe(0f, dragging = false, velocity = (velocityY / travelPx).coerceIn(-12f, 0f))
                                onDismiss()
                            } else {
                                // Spring back to fully open.
                                swipeTarget = SpotlightSwipe(1f, dragging = false)
                            }
                        }
                    }
                }
            }
          val contentClock = remember { Animatable(if (pill != null && animationsOn) 0f else 1f) }
          LaunchedEffect(visible) {
              if (pill == null || !animationsOn) return@LaunchedEffect
              if (visible) contentClock.animateTo(1f, tween(SpotlightEmerge.CONTENT_MS, easing = LinearEasing))
              else contentClock.animateTo(0f, tween(SpotlightEmerge.CLOSE_CONTENT_MS, easing = LinearEasing))
          }
          val emerge = remember(pill != null, animationsOn) {
              EmergeSource(pill != null && animationsOn, { minOf(contentClock.value, effectiveProgress()) }, { panelPos.y }, { panelSize.height.toFloat() })
          }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    var cardPos by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val cardEmerge = remember(pill != null, animationsOn) {
        EmergeSource(pill != null && animationsOn, { minOf(contentClock.value, effectiveProgress()) }, { cardPos.y }, { cardSize.height.toFloat() })
    }
    val onEnter: () -> Unit = {
                    when (SpotlightEnter.route(appResults.isNotEmpty(), singleQuick != null, actionContacts.isNotEmpty(), query.isBlank(), askOffered && askView == null)) {
                        SpotlightEnter.Route.APP -> launch(appResults.first())
                        SpotlightEnter.Route.QUICK -> singleQuick?.let { actions.run(it, focusOn(), onToggleFocus); onDismiss() }
                        SpotlightEnter.Route.CONTACT -> actionContacts.firstOrNull()?.let {
                            actions.openContact(contactKind ?: ContactActionKind.DIAL, it.number); onDismiss()
                        }
                        SpotlightEnter.Route.ASK -> askedFor = query
                        SpotlightEnter.Route.WEB -> if (handoff.searchWeb(query)) onDismiss()
                        SpotlightEnter.Route.NONE -> Unit
                    }
    }
    val searchOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search)
    val searchActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onEnter() })
    val panelField: @Composable () -> Unit = {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().emergingHere(field = true).focusRequester(focus).testTag("spotlight-field"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ink, unfocusedTextColor = ink, cursorColor = ink,
                    focusedBorderColor = ink.copy(alpha = .55f), unfocusedBorderColor = ink.copy(alpha = .25f),
                    focusedPlaceholderColor = ink.copy(alpha = .55f), unfocusedPlaceholderColor = ink.copy(alpha = .55f),
                    focusedLeadingIconColor = ink.copy(alpha = .8f), unfocusedLeadingIconColor = ink.copy(alpha = .8f),
                    focusedTrailingIconColor = ink.copy(alpha = .8f), unfocusedTrailingIconColor = ink.copy(alpha = .8f)),
                placeholder = { Text(stringResource(com.jake.duolauncher.R.string.search_apps_answers_feeds), maxLines = 1) }, singleLine = true, shape = Corner.pill,
                // Enter opens the top app, as on iOS; with no app to open it searches the web in your browser with the words typed.
                keyboardOptions = searchOptions,
                keyboardActions = searchActions,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search") } })
    }
    val barField: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).emergingHere(field = true), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text(stringResource(com.jake.duolauncher.R.string.search_apps_answers_feeds), color = ink.copy(alpha = .72f), style = SpotlightBar.fieldStyle, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                androidx.compose.foundation.text.BasicTextField(query, { query = it }, Modifier.fillMaxWidth().focusRequester(focus).testTag("spotlight-field"), singleLine = true,
                    textStyle = SpotlightBar.fieldStyle.copy(color = ink), cursorBrush = androidx.compose.ui.graphics.SolidColor(ink),
                    keyboardOptions = searchOptions, keyboardActions = searchActions)
            }
            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search", tint = ink.copy(alpha = .8f)) }
        }
    }
    val resultsList: @Composable (Modifier) -> Unit = { listModifier ->
            val contactHint = contactKind != null && actionContacts.isEmpty() && !contactsEnabled
            val nothing = smart == null && !contactHint && !askOffered && askView == null && singleQuick == null && actionContacts.isEmpty() && settingsHits.isEmpty() && appResults.isEmpty() && contacts.isEmpty() && feedResults.isEmpty() && suggestions.isEmpty()
            val typed = query.isNotBlank()
            val webOffered = suggestions.any { it.kind == HandoffKind.WEB }
            LazyColumn(listModifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (smart != null) item("smart") {
                    Column(rowMotion().fillMaxWidth().emergingHere().clip(Corner.inside(capCorner, 16.dp)).background(ink.copy(alpha = .10f))
                        .clickable {
                            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Spotlight answer", smart.text))
                            copied = true
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp).testTag("spotlight-answer")) {
                        Text(smart.detail, color = ink.copy(alpha = .7f), style = UnoType.caption1)
                        Text(smart.text, color = ink, style = UnoType.title2)
                        Text(stringResource(if (copied) com.jake.duolauncher.R.string.spotlight_copied else com.jake.duolauncher.R.string.spotlight_tap_to_copy),
                            color = ink.copy(alpha = .5f), style = UnoType.caption2)
                    }
                }
                if (askOffered || askView != null) askItems(askView, query, assistantState, ink, capCorner, askCopied,
                    onAsk = { askedFor = query },
                    onRun = { action -> actions.run(action, focusOn(), onToggleFocus); onDismiss() },
                    onLaunch = { app -> launch(app) },
                    onContact = { kind, number -> actions.openContact(kind, number); onDismiss() },
                    onWeb = { q -> if (handoff.searchWeb(q)) onDismiss() },
                    onCopy = { text ->
                        context.getSystemService(android.content.ClipboardManager::class.java)
                            ?.setPrimaryClip(android.content.ClipData.newPlainText("Uno Lab answer", text))
                        askCopied = true
                    },
                    onContinue = { assistant?.continueInLab(context, query); onDismiss() },
                    onSettings = { onOpenSearchSettings(); onDismiss() })
                if (singleQuick != null) item("quick-header") {
                    SpotlightHeader(rowMotion(), stringResource(com.jake.duolauncher.R.string.spotlight_actions), ink)
                }
                if (singleQuick != null) item("quick") {
                    SpotlightRow(rowMotion(), singleQuick.label, singleQuick.detail, "spotlight-quick", true, ink, {
                        actions.run(singleQuick, focusOn(), onToggleFocus)
                        onDismiss()
                    }) {
                        Box(Modifier.size(40.dp).background(ink.copy(alpha = .14f), Corner.icon), contentAlignment = Alignment.Center) {
                            Icon(actionIcon(singleQuick), null, Modifier.size(22.dp))
                        }
                    }
                }
                // "call mom" / "text alex" with contact search off has nothing to find: say what would make it work.
                if (contactKind != null && actionContacts.isEmpty() && !contactsEnabled && typed) item("contact-hint") {
                    SpotlightRow(rowMotion(), ContactHint.OFF, stringResource(com.jake.duolauncher.R.string.spotlight_open_search_settings),
                        "spotlight-contact-hint", false, ink, { onOpenSearchSettings(); onDismiss() }) {
                        Box(Modifier.size(40.dp).background(ink.copy(alpha = .14f), Corner.icon), contentAlignment = Alignment.Center) {
                            Icon(if (contactKind == ContactActionKind.DIAL) Icons.Rounded.Call else Icons.Rounded.Sms, null, Modifier.size(22.dp))
                        }
                    }
                }
                if (actionContacts.isNotEmpty()) {
                    item("actions-header") {
                        SpotlightHeader(rowMotion(), if (contactKind == ContactActionKind.DIAL) "Call" else "Message", ink)
                    }
                    items(actionContacts, key = { "action-${it.name}-${it.number}" }) { contact ->
                        SpotlightRow(rowMotion(), contact.name, contact.number, "spotlight-action-${contact.number}", false, ink, {
                            actions.openContact(contactKind ?: ContactActionKind.DIAL, contact.number)
                            onDismiss()
                        }) {
                            Box(Modifier.size(40.dp).background(ink.copy(alpha = .14f), Corner.icon), contentAlignment = Alignment.Center) {
                                Icon(if (contactKind == ContactActionKind.DIAL) Icons.Rounded.Call else Icons.Rounded.Sms, null, Modifier.size(22.dp))
                            }
                        }
                    }
                }
                if (floating && !typed && suggested.isNotEmpty()) item("suggested-grid") {
                    Row(rowMotion().fillMaxWidth().emergingHere().padding(vertical = 8.dp)) {
                        suggested.take(4).forEach { app ->
                            Column(Modifier.weight(1f).clip(Corner.small).clickable(role = androidx.compose.ui.semantics.Role.Button) { launch(app) }.padding(vertical = 4.dp)
                                .testTag("spotlight-app-${app.id}"), horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(app.image, app.label, Modifier.size(58.dp)
                                    .onGloballyPositioned { iconBounds[app.id] = it.boundsInWindow().toAndroidBounds() }.glassIcon(lift = false).clip(Corner.icon))
                                Text(app.label, Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp), color = ink, style = UnoType.caption2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        repeat(4 - suggested.size.coerceAtMost(4)) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (!floating && !typed && suggested.isNotEmpty()) {
                    item("suggested-header") { SpotlightHeader(rowMotion(), stringResource(com.jake.duolauncher.R.string.spotlight_suggested), ink) }
                    items(suggested, key = { "suggested-${it.id}" }) { app ->
                        SpotlightRow(rowMotion(), app.label, null, "spotlight-app-${app.id}", false, ink, { launch(app) }) {
                            Image(app.image, null, Modifier.size(40.dp)
                            .onGloballyPositioned { iconBounds[app.id] = it.boundsInWindow().toAndroidBounds() }.glassIcon(lift = false).clip(Corner.icon))
                        }
                    }
                }
                // Before anything is typed the panel also offers the quick actions that need no argument, one tap instead of a command.
                if (!typed && idle.isNotEmpty()) {
                    if (!floating) item("idle-header") { SpotlightHeader(rowMotion(), stringResource(com.jake.duolauncher.R.string.spotlight_actions), ink) }
                    items(if (floating) idle.take(2) else idle, key = { "idle-${it.javaClass.simpleName}" }) { action ->
                        SpotlightRow(rowMotion(), action.label, action.detail, "spotlight-idle-${action.javaClass.simpleName.lowercase()}", false, ink, {
                            actions.run(action, focusOn(), onToggleFocus)
                            onDismiss()
                        }) {
                            Box(Modifier.size(40.dp).background(ink.copy(alpha = .14f), Corner.icon), contentAlignment = Alignment.Center) {
                                Icon(actionIcon(action), null, Modifier.size(22.dp))
                            }
                        }
                    }
                }
                if (appResults.isNotEmpty()) item("apps-header") { SpotlightHeader(rowMotion(), stringResource(com.jake.duolauncher.R.string.spotlight_apps), ink) }
                itemsIndexed(appResults, key = { _, app -> "app-${app.id}" }) { index, app ->
                    // The first result is what Enter opens, so it is marked as the top hit. A work app says so, so the personal and work
                    // copies of one app are told apart by more than the small briefcase.
                    val topHit = if (index == 0) stringResource(com.jake.duolauncher.R.string.spotlight_top_hit) else null
                    SpotlightRow(rowMotion(), app.label, listOfNotNull(topHit, app.profileLabel.takeIf { app.isWork }, app.description).joinToString(" · ").ifEmpty { null },
                        "spotlight-app-${app.id}", index == 0, ink, { launch(app) },
                        match = SpotlightRank.highlightRange(app.label, query)) {
                        Image(app.image, null, Modifier.size(40.dp)
                            .onGloballyPositioned { iconBounds[app.id] = it.boundsInWindow().toAndroidBounds() }.glassIcon(lift = false).clip(Corner.icon))
                    }
                }
                if (contacts.isNotEmpty()) item("contacts-header") { SpotlightHeader(rowMotion(), stringResource(com.jake.duolauncher.R.string.spotlight_contacts), ink) }
                items(contacts, key = { "contact-${it.id}" }) { contact ->
                    SpotlightRow(rowMotion(), contact.name, null, "spotlight-contact-${contact.id}", false, ink, { onOpenContact(contact) }) {
                        Box(Modifier.size(40.dp).background(ink.copy(alpha = .14f), CircleShape), contentAlignment = Alignment.Center) {
                            Text(contact.name.firstOrNull()?.uppercase() ?: "?", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (settingsHits.isNotEmpty()) item("settings-header") { SpotlightHeader(rowMotion(), stringResource(com.jake.duolauncher.R.string.spotlight_settings), ink) }
                items(settingsHits, key = { "settings-${it.action}" }) { page ->
                    SpotlightRow(rowMotion(), page.label, stringResource(com.jake.duolauncher.R.string.spotlight_settings_detail), "spotlight-settings-${page.label.lowercase().replace(' ', '-')}", false, ink, {
                        runCatching { context.startActivity(android.content.Intent(page.action).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        onDismiss()
                    }) {
                        Box(Modifier.size(40.dp).background(ink.copy(alpha = .12f), Corner.icon), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Settings, null, Modifier.size(22.dp))
                        }
                    }
                }
                if (feedResults.isNotEmpty()) item("feed-header") { SpotlightHeader(rowMotion(), stringResource(com.jake.duolauncher.R.string.spotlight_headlines), ink) }
                items(feedResults, key = { "feed-${it.link}" }) { entry ->
                    SpotlightRow(rowMotion(), entry.title, entry.sourceLabel, "spotlight-feed", false, ink, { onOpenFeedLink(entry.link) }, titleLines = 2) {
                        Box(Modifier.size(40.dp).background(ink.copy(alpha = .10f), Corner.icon), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.RssFeed, null, Modifier.size(20.dp))
                        }
                    }
                }
                if (typed && nothing) item("none") {
                    Text(stringResource(com.jake.duolauncher.R.string.nothing_found), Modifier.emergingHere().padding(12.dp), color = ink.copy(alpha = .7f))
                }
                // With no launch history yet the panel would otherwise just be quick actions; teach the typed commands too.
                if (!floating && !typed && suggested.isEmpty()) item("hint") {
                    Text(stringResource(com.jake.duolauncher.R.string.spotlight_try), Modifier.emergingHere().padding(12.dp),
                        color = ink.copy(alpha = .6f), style = UnoType.footnote)
                }
                if (suggestions.isNotEmpty()) item("elsewhere-header") { SpotlightHeader(rowMotion(), stringResource(com.jake.duolauncher.R.string.spotlight_elsewhere), ink) }
                items(suggestions, key = { "handoff-${it.kind}-${it.packageName}" }) { target ->
                    HandoffRow(target, ink, rowMotion().clickable { handoff.open(target, query); onDismiss() })
                }
                // Enter already searches the web; this row makes the same action visible, whatever the handoff setting.
                if (typed && !webOffered) item("web") {
                    SpotlightRow(rowMotion(), stringResource(com.jake.duolauncher.R.string.spotlight_search_web, query.trim()), null, "spotlight-web", false, ink,
                        { if (handoff.searchWeb(query)) onDismiss() }) {
                        Box(Modifier.size(40.dp).background(ink.copy(alpha = .12f), Corner.icon), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Search, null, Modifier.size(20.dp))
                        }
                    }
                }
            }
    }
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
    Box(Modifier.layout { measurable, constraints ->
            // Exactly the window's size, placed so its top-left corner is the window's (a size modifier would centre it instead).
            // The window's size, from Compose's own (observed) window info: a window added straight to the WindowManager, as the assistant's
            // overlay is, has a root view that is still 0 x 0 on the first measure, and reading it is not observed, so the panel was measured
            // at nothing and never again (it painted nothing). Home's root is its decor view, already sized, so it never showed there.
            val w = windowSize.width.takeIf { it > 0 } ?: view.rootView.width
            val h = windowSize.height.takeIf { it > 0 } ?: view.rootView.height
            val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w.coerceAtLeast(0), h.coerceAtLeast(0)))
            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-origin.x.roundToInt(), -origin.y.roundToInt()) }
        }
        .drawBehind { drawRect(Color.Black.copy(alpha = (if (floating) .12f else .38f) * effectiveProgress().coerceIn(0f, 1f))) }
        .clickable(interactionSource = quiet, indication = null, onClick = onDismiss).testTag("spotlight-scrim"),
        contentAlignment = Alignment.TopCenter) {
        // Without an island the panel opens from the top, inside the safe area. It pads for the
        // system bars and cutout only; the IME is handled by the imePadding below, so the keyboard
        // is never double-counted. The bars are measured whether they show or not: Home hides the status bar while search is open, and
        // following it moved the panel and re-laid it out on every frame of the bar's animation, as search opened and again as it closed.
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        if (floating) Column(Modifier.padding(top = with(density) { pillTop.toDp() }).imePadding().padding(horizontal = 12.dp)
            .widthIn(max = IslandGeometry.expandedWidthDp(LocalConfiguration.current.screenWidthDp.toFloat()).dp).fillMaxWidth()) {
            // The bar: the island's own body, grown wide (the morph follows this and not the card). The camera sits in its top, the field under it.
            // Its fill is black round the camera and fades into liquid glass toward the bottom edge; while it is still the pill it is all black.
            Box(Modifier.fillMaxWidth().then(panelChrome).testTag("spotlight")) {
                if (glass != null && panelSize.width > 0) {
                    val barCorner = with(density) { IslandMorph.cornerPx(panelSize.width.toFloat(), panelSize.height.toFloat(), density.density, panelScale).toDp() }
                    Box(Modifier.matchParentSize().liquidGlass(glass.backdrop, ContinuousShape(barCorner), Color.Black, GlassKind.CLEAR, glass.settings, depth = false))
                }
                val solidPx = pillBottom - pillTop
                Box(Modifier.matchParentSize().drawWithCache {
                    val fill = (if (glass == null && seeThrough) SpotlightBar.seeThroughFill((solidPx / size.height).coerceIn(0f, 1f))
                        else SpotlightBar.barFill((solidPx / size.height).coerceIn(0f, 1f), hasGlass = glass != null))
                    onDrawBehind {
                        drawRect(fill)
                        val p = effectiveProgress().coerceIn(0f, 1f)
                        if (p < 1f) drawRect(Color.Black.copy(alpha = 1f - p))
                    }
                })
                // As tall as the camera's band and a row of text, with the field centred in the whole bar.
                Box(Modifier.fillMaxWidth().height(with(density) { (pillBottom - pillTop).toDp() }.coerceAtLeast(0.dp) + 50.dp).padding(horizontal = 22.dp), contentAlignment = Alignment.CenterStart) {
                    CompositionLocalProvider(LocalEmerge provides emerge, LocalContentColor provides ink) { barField() }
                }
            }
            Spacer(Modifier.height(8.dp))
            // The results, a card floating under the bar that drops out of it as it opens, as tall as what is on it (never under the keyboard).
            val cardShape = Corner.floatingSheet(0.dp)
            Column(Modifier.weight(1f, fill = false).fillMaxWidth().onGloballyPositioned { cardSize = it.size; cardPos = it.positionInWindow() }
                .graphicsLayer {
                    val p = effectiveProgress().coerceIn(0f, 1f)
                    alpha = p; scaleX = .94f + .06f * p; scaleY = .94f + .06f * p
                    translationY = -(1f - p) * 28.dp.toPx(); transformOrigin = TransformOrigin(.5f, 0f)
                }
                .then(if (glass != null) Modifier.liquidGlass(glass.backdrop, cardShape, Color.Black, GlassKind.REGULAR, glass.settings) else Modifier.clip(cardShape).background(Color(0xFF1C1C1E).copy(alpha = if (seeThrough) .94f else 1f)))
                .background(Color.Black.copy(alpha = .52f), cardShape)
                .clickable(interactionSource = quiet, indication = null) {}.then(swipeToClose).testTag("spotlight-results")
                // Inside the glass, so the glass itself is the size that is moving. A letter typed mid-way carries on from the card's current speed.
                .then(if (animationsOn) Modifier.animateContentSize(SpotlightBar.cardResize) else Modifier)
                .padding(horizontal = 10.dp, vertical = 8.dp)) {
                CompositionLocalProvider(LocalEmerge provides cardEmerge, LocalContentColor provides ink) { resultsList(Modifier) }
            }
        } else
        Column(Modifier.then(if (pill == null) Modifier.windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)).padding(top = 8.dp) else Modifier)
            .padding(top = with(density) { pillTop.toDp() })
            .imePadding().padding(horizontal = 12.dp)
            .widthIn(max = IslandGeometry.expandedWidthDp(LocalConfiguration.current.screenWidthDp.toFloat()).dp)
            .fillMaxWidth().heightIn(max = SPOTLIGHT_MAX_HEIGHT_DP.dp)
            .then(panelChrome).then(swipeToClose)
            .padding(16.dp).testTag("spotlight")) {
          CompositionLocalProvider(LocalEmerge provides emerge) {
          Column {
            if (pill != null) Spacer(Modifier.height(with(density) { (pillBottom - pillTop).toDp() }.coerceAtLeast(0.dp)))
            CompositionLocalProvider(LocalContentColor provides ink) {
            panelField()
            resultsList(Modifier.padding(top = 12.dp))
            }
          }
          }
        }
    }
    }
}

/** A "search elsewhere" suggestion: the target app's own icon, and a plain label saying where the search will go. */
@Composable
internal fun HandoffRow(target: HandoffTarget, ink: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().emergingHere().heightIn(min = 52.dp).clip(Corner.small).padding(horizontal = 6.dp, vertical = 6.dp)
        .testTag("handoff-${target.kind.name.lowercase()}-${target.packageName}"), verticalAlignment = Alignment.CenterVertically) {
        val icon = target.icon
        if (icon != null) Image(icon.asImageBitmap(), null, Modifier.size(40.dp).clip(Corner.icon))
        else Box(Modifier.size(40.dp).background(ink.copy(alpha = .12f), Corner.icon))
        Column(Modifier.padding(start = 12.dp)) {
            Text(target.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = UnoType.subheadline)
            Text(if (target.kind == HandoffKind.WEB) "Opens your browser's own search" else "Not installed? Look for it here",
                style = UnoType.caption2, color = ink.copy(alpha = .6f), maxLines = 1)
        }
    }
}

/** The Ask Qwen part of the results: the row to ask, then what came back (an action row, or the answer card). */
private fun androidx.compose.foundation.lazy.LazyListScope.askItems(view: AskView?, query: String, state: AssistantState, ink: Color,
    capCorner: androidx.compose.ui.unit.Dp, copied: Boolean, onAsk: () -> Unit, onRun: (QuickAction) -> Unit, onLaunch: (AppEntry) -> Unit,
    onContact: (ContactActionKind, String) -> Unit, onWeb: (String) -> Unit, onCopy: (String) -> Unit, onContinue: () -> Unit,
    onSettings: () -> Unit) {
    val mark: @Composable (ImageVector) -> Unit = { icon ->
        Box(Modifier.size(40.dp).background(ink.copy(alpha = .14f), Corner.icon), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp))
        }
    }
    when (view) {
        null -> item("ask") {
            SpotlightRow(rowMotion(), stringResource(com.jake.duolauncher.R.string.spotlight_ask), stringResource(if (state == AssistantState.LOADING) com.jake.duolauncher.R.string.spotlight_ask_loading else com.jake.duolauncher.R.string.spotlight_ask_detail),
                "spotlight-ask", false, ink, onAsk) { mark(Icons.Rounded.AutoAwesome) }
        }
        // The asking row, the answer being written and the finished answer are one item, with the card at one place in it, so the text's
        // reveal carries on across the answer finishing (its last word settles rather than snapping). Only this item reads the stream, so
        // the text arriving a frame at a time redraws this card and nothing else in search.
        is AskView.Thinking, is AskView.Answer -> item("ask") {
            val finished = view as? AskView.Answer
            // Cleaning trims the end; keep a trailing space, which tells the reveal the last word is complete.
            val live = if (finished != null) null else AssistantSession.streamed.takeIf { it.isNotBlank() }?.let { raw ->
                AnswerText.clean(raw)?.let { if (raw.last().isWhitespace()) "$it " else it } }
            if (finished == null && live == null) SpotlightRow(rowMotion(), stringResource(com.jake.duolauncher.R.string.spotlight_asking), stringResource(if (state == AssistantState.LOADING) com.jake.duolauncher.R.string.spotlight_ask_loading else com.jake.duolauncher.R.string.spotlight_ask_on_phone),
                "spotlight-ask-thinking", false, ink, {}) { mark(Icons.Rounded.AutoAwesome) }
            else AnswerCard(rowMotion(), finished?.text ?: live.orEmpty(), done = finished?.done ?: false, error = finished?.error, ink = ink,
                capCorner = capCorner, copied = copied, onCopy = onCopy, onContinue = onContinue)
        }
        is AskView.Act -> {
            item("ask-header") { SpotlightHeader(rowMotion(), stringResource(com.jake.duolauncher.R.string.spotlight_ask_suggested), ink) }
            val action = view.action
            when {
                view.app != null -> item("ask-action") {
                    SpotlightRow(rowMotion(), action.label, action.detail, "spotlight-ask-action", true, ink, { onLaunch(view.app) }) {
                        Image(view.app.image, null, Modifier.size(40.dp).glassIcon(lift = false).clip(Corner.icon))
                    }
                }
                view.contacts.isNotEmpty() -> {
                    val kind = if (action is QuickAction.Text) ContactActionKind.TEXT else ContactActionKind.DIAL
                    items(view.contacts.take(3), key = { "ask-contact-${it.name}-${it.number}" }) { contact ->
                        SpotlightRow(rowMotion(), stringResource(if (kind == ContactActionKind.DIAL) com.jake.duolauncher.R.string.spotlight_ask_call else com.jake.duolauncher.R.string.spotlight_ask_text, contact.name), contact.number,
                            "spotlight-ask-action", true, ink, { onContact(kind, contact.number) }) { mark(actionIcon(action)) }
                    }
                }
                action is QuickAction.WebSearch -> item("ask-action") {
                    SpotlightRow(rowMotion(), action.label, action.detail, "spotlight-ask-action", true, ink, { onWeb(action.query) }) { mark(actionIcon(action)) }
                }
                else -> item("ask-action") {
                    SpotlightRow(rowMotion(), action.label, action.detail, "spotlight-ask-action", true, ink, { onRun(action) }) { mark(actionIcon(action)) }
                }
            }
        }
        is AskView.Info -> item("ask-info") {
            Column(rowMotion().fillMaxWidth().emergingHere().clip(Corner.inside(capCorner, 16.dp)).background(ink.copy(alpha = .10f))
                .then(if (view.settings) Modifier.clickable(onClick = onSettings) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp).semantics(mergeDescendants = true) {}.testTag("spotlight-ask-info")) {
                Text(stringResource(com.jake.duolauncher.R.string.spotlight_ask_answer_by), color = ink.copy(alpha = .7f), style = UnoType.caption1)
                Text(view.why, color = ink, style = UnoType.body)
                if (view.settings) Text(stringResource(com.jake.duolauncher.R.string.spotlight_open_search_settings), color = ink.copy(alpha = .5f), style = UnoType.caption2)
            }
        }
    }
}

/** The answer card, styled like the smart answer: a faint plate, tap to copy once the answer is done. Shared by the finished answer and the
 *  one still being written, under the same list key, so the card carries on growing rather than being replaced when the answer completes. */
@Composable
private fun AnswerCard(modifier: Modifier, text: String, done: Boolean, error: String?, ink: Color, capCorner: androidx.compose.ui.unit.Dp,
    copied: Boolean, onCopy: (String) -> Unit, onContinue: () -> Unit) {
            // Styled like the smart answer above it: a faint plate, tap to copy. While the answer is written, each word falls softly into place
            // and a faint glow turns round the card's edge (AnswerReveal); with Android's animations off, it simply appears.
            val context = LocalContext.current
            val animate = remember { android.provider.Settings.Global.getFloat(context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
            val cardShape = Corner.inside(capCorner, 16.dp)
            Column(modifier.fillMaxWidth().emergingHere().clip(cardShape).background(ink.copy(alpha = .10f))
                .answerGlow(glowing = !done && error == null, shape = cardShape, animate = animate)
                .clickable(enabled = done && text.isNotBlank()) { onCopy(text) }
                .padding(horizontal = 16.dp, vertical = 12.dp).testTag("spotlight-ask-answer")) {
                Text(stringResource(com.jake.duolauncher.R.string.spotlight_ask_answer_by), color = ink.copy(alpha = .7f), style = UnoType.caption1)
                when {
                    text.isNotBlank() -> RevealText(text, streaming = !done, ink = ink, style = UnoType.body, animate = animate, modifier = Modifier.testTag("spotlight-ask-text"))
                    error == null -> Text(stringResource(com.jake.duolauncher.R.string.spotlight_ask_thinking), color = ink.copy(alpha = .6f), style = UnoType.body)
                }
                if (error != null) Text(error, color = ink.copy(alpha = .7f), style = UnoType.footnote)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (done && text.isNotBlank()) Text(stringResource(if (copied) com.jake.duolauncher.R.string.spotlight_copied else com.jake.duolauncher.R.string.spotlight_tap_to_copy), Modifier.weight(1f),
                        color = ink.copy(alpha = .5f), style = UnoType.caption2)
                    else Spacer(Modifier.weight(1f))
                    Text(stringResource(com.jake.duolauncher.R.string.spotlight_ask_continue), Modifier.clip(Corner.small).clickable(onClick = onContinue)
                        .padding(horizontal = 8.dp, vertical = 6.dp).testTag("spotlight-ask-continue"),
                        color = ink.copy(alpha = .8f), style = UnoType.caption1.copy(fontWeight = FontWeight.SemiBold))
                }
            }
}

/** How a row of the results list arrives, goes and moves (see SpotlightBar). */
private fun androidx.compose.foundation.lazy.LazyItemScope.rowMotion(): Modifier =
    Modifier.animateItem(fadeInSpec = SpotlightBar.rowFadeIn, placementSpec = SpotlightBar.rowMove, fadeOutSpec = SpotlightBar.rowFadeOut)

@Composable
private fun SpotlightHeader(modifier: Modifier, text: String, ink: Color) {
    Text(text.uppercase(), modifier.emergingHere().padding(start = 8.dp, top = 12.dp, bottom = 4.dp), color = ink.copy(alpha = .6f),
        style = UnoType.caption1.copy(fontWeight = FontWeight.SemiBold))
}

/** The leading mark for a quick action row. */
private fun actionIcon(action: QuickAction): ImageVector = when (action) {
    is QuickAction.Timer -> Icons.Rounded.Timer
    is QuickAction.Stopwatch -> Icons.Rounded.Timer
    is QuickAction.Note -> Icons.Rounded.EditNote
    is QuickAction.Alarm -> Icons.Rounded.Alarm
    is QuickAction.Torch -> Icons.Rounded.FlashlightOn
    is QuickAction.Focus -> Icons.Rounded.Bedtime
    is QuickAction.Call -> Icons.Rounded.Call
    is QuickAction.Text -> Icons.Rounded.Sms
    is QuickAction.Dial -> Icons.Rounded.Call
    is QuickAction.OpenLink -> Icons.AutoMirrored.Rounded.OpenInNew
    is QuickAction.OpenApp -> Icons.AutoMirrored.Rounded.OpenInNew
    is QuickAction.WebSearch -> Icons.Rounded.Search
}

/** One result: a 40 dp leading mark, a title, an optional second line; the top hit sits on a faint plate. */
@Composable
private fun SpotlightRow(modifier: Modifier, title: String, subtitle: String?, tag: String, highlighted: Boolean, ink: Color, onClick: () -> Unit,
    titleLines: Int = 1, match: IntRange? = null, leading: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth().emergingHere().heightIn(min = 52.dp).clip(Corner.small)
        .then(if (highlighted) Modifier.background(ink.copy(alpha = .09f)) else Modifier)
        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
        .padding(horizontal = 6.dp, vertical = 6.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        leading()
        Column(Modifier.padding(start = 12.dp)) {
            val titleStyle = if (titleLines > 1) UnoType.subheadline else UnoType.callout
            if (match == null) Text(title, maxLines = titleLines, overflow = TextOverflow.Ellipsis, style = titleStyle)
            else Text(highlightMatch(title, match), maxLines = titleLines, overflow = TextOverflow.Ellipsis, style = titleStyle)
            if (subtitle != null) Text(subtitle, style = UnoType.caption2, color = ink.copy(alpha = .6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Bolds the matched part of [title] so the eye lands on why it matched. */
private fun highlightMatch(title: String, match: IntRange?): AnnotatedString {
    if (match == null || match.isEmpty() || match.first !in title.indices || match.last !in title.indices) return AnnotatedString(title)
    return buildAnnotatedString {
        append(title.substring(0, match.first))
        pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
        append(title.substring(match.first, match.last + 1))
        pop()
        append(title.substring(match.last + 1))
    }
}
