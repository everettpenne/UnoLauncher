package com.jake.duolauncher.search

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import androidx.compose.ui.draw.drawWithContent
import kotlinx.coroutines.withContext

/** The panel's maximum height: 560 dp minus the requested ~25 px (24 dp — the closest 8 dp-grid step), so a
 * search reads as the island grown larger rather than a sheet. The results list scrolls inside it. */
private const val SPOTLIGHT_MAX_HEIGHT_DP = 536f

/** The bottom strip of the panel where a swipe up closes it (a 48 dp touch target, mirroring the island's swipe-up-close). */
private val SPOTLIGHT_SWIPE_CLOSE_ZONE = 48.dp

/** What the swipe-up-close is heading for: the panel's emerge progress (1 = fully open, 0 = collapsed into the pill), and
 * whether the finger is driving it (dragging snaps 1:1; releasing springs back to 1 = open, or is held where the finger left it
 * when the swipe closes). One value so the gesture can change both without an intermediate state. */
private data class SpotlightSwipe(val progress: Float, val dragging: Boolean)

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

    fun tier(label: String, query: String): Tier {
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
    fun <T> apps(apps: List<T>, query: String, label: (T) -> String, frecency: (T) -> Double = { 0.0 }): List<T> {
        if (query.isBlank()) return emptyList()
        return apps.map { it to tier(label(it), query) }.filter { it.second != Tier.NONE }
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
internal object SpotlightEmerge {
    /** The panel's outline at [progress], from [start] (the island's body, in the panel's own px) to the whole [panel]. */
    fun rect(progress: Float, start: PxRect, panel: PxRect): PxRect = IslandMorph.lerp(start, panel, progress.coerceIn(0f, 1f))

    /** The fields and results fade in during the second half of the growth, so they are never seen cut off by a small outline. */
    fun contentAlpha(progress: Float) = ((progress - .45f) / .55f).coerceIn(0f, 1f)
}

/** What the search key should do, decided pure so it is unit-tested: the top app (as on iOS), else the typed action,
 * else the first contact for "call"/"text", else a web search of the words. */
internal object SpotlightEnter {
    enum class Route { APP, QUICK, CONTACT, WEB, NONE }
    fun route(hasApps: Boolean, hasQuick: Boolean, hasContacts: Boolean, queryBlank: Boolean): Route = when {
        hasApps -> Route.APP
        hasQuick -> Route.QUICK
        hasContacts -> Route.CONTACT
        !queryBlank -> Route.WEB
        else -> Route.NONE
    }
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
    /** Launches with the app icon's place in the window, so the app opens out of the icon as it does from Home; null uses [onLaunch]. */
    onLaunchFrom: ((AppEntry, android.graphics.Rect?) -> Unit)? = null,
    onOpenFeedLink: (String) -> Unit,
    onOpenContact: (ContactResult) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
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
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Request/hide the keyboard in lock-step with the open/close decision (not after the emerge
    // completes). On open the field is focused and the IME shown on the same frame the island
    // begins to grow; on close the IME is hidden the moment the dismiss begins, rather than when
    // the field is disposed at the end of the exit transition.
    LaunchedEffect(visible) {
        if (visible) { focus.requestFocus(); keyboard?.show() } else keyboard?.hide()
    }
    val history = remember { SpotlightHistory(context) }
    val scores = remember { history.scores() }
    // Hidden apps are never suggested or ranked; typing one's exact name finds it.
    val searchable = remember(apps, hiddenApps, query) { HiddenApps.shown(apps, hiddenApps, query, { it.id }, { it.label }) }
    val appResults = remember(searchable, query) { SpotlightRank.apps(searchable, query, { it.label }, { scores[it.id] ?: 0.0 }) }
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
    // A recognised quick action is itself an answer, so it also keeps the "search elsewhere" rows away.
    val suggestions = remember(query, appResults.size, smart, quick, handoff) {
        handoff.targets(query, appResults.size, smart != null || quick != null)
    }
    var contacts by remember { mutableStateOf(emptyList<ContactResult>()) }
    LaunchedEffect(query, contactsEnabled) {
        if (!contactsEnabled || query.trim().length < ContactMatch.MIN_QUERY) { contacts = emptyList(); return@LaunchedEffect }
        delay(150)
        contacts = withContext(Dispatchers.IO) { ContactsSearch.search(context, query) }
    }
    // The island's own look: a true-black body (the pill's colour, so the panel reads as the pill grown larger), white ink and
    // a hairline rim. The corners equal the expanded island's (IslandGeometry.CORNER_CAP_DP, the shared source of truth) at full
    // size, and morph with the emerge so the panel never reads as a rounded rectangle while it is still pill-sized: a capsule at
    // progress 0, the 34dp cap at progress 1. Without an island there is no pill to grow from, so it stays at the cap.
    val ink = Color.White
    val quiet = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val capCorner = IslandGeometry.CORNER_CAP_DP.dp
    val capShape = RoundedCornerShape(capCorner)
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
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
    Box(Modifier.layout { measurable, constraints ->
            // Exactly the window's size, placed so its top-left corner is the window's (a size modifier would centre it instead).
            val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(view.rootView.width, view.rootView.height))
            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-origin.x.roundToInt(), -origin.y.roundToInt()) }
        }
        .drawBehind { drawRect(Color.Black.copy(alpha = .38f * effectiveProgress().coerceIn(0f, 1f))) }
        .clickable(interactionSource = quiet, indication = null, onClick = onDismiss).testTag("spotlight-scrim"),
        contentAlignment = Alignment.TopCenter) {
        // Without an island the panel opens from the top, inside the safe area. It pads for the
        // system bars and cutout only; the IME is handled by the imePadding below, so the keyboard
        // is never double-counted.
        Column(Modifier.then(if (pill == null) Modifier.windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)).padding(top = 8.dp) else Modifier)
            .padding(top = with(density) { pillTop.toDp() })
            .imePadding().padding(horizontal = 12.dp)
            .widthIn(max = IslandGeometry.expandedWidthDp(LocalConfiguration.current.screenWidthDp.toFloat()).dp)
            .fillMaxWidth().heightIn(max = SPOTLIGHT_MAX_HEIGHT_DP.dp)
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
                drawRect(Color.Black)
                drawContent()
                // The hairline rim, on the outline as it grows (the island has none on its pill, so it comes in with the panel).
                val r = outline()
                val rim = Color.White.copy(alpha = .10f * (if (pill != null) effectiveProgress().coerceIn(0f, 1f) else 1f))
                if (r != null) drawRoundRect(rim, topLeft = androidx.compose.ui.geometry.Offset(r.left, r.top),
                    size = androidx.compose.ui.geometry.Size(r.width, r.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(IslandMorph.cornerPx(r.width, r.height, density.density, panelScale)),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                else if (pill == null) drawRoundRect(rim, cornerRadius = androidx.compose.ui.geometry.CornerRadius(capCorner.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            }
            .clickable(interactionSource = quiet, indication = null) {}
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
                                // Hold the panel where the finger left it, so the parent's exit tween (progress 1 → 0) finishes the
                                // collapse from there rather than snapping back open first.
                                swipeTarget = SpotlightSwipe(swipeProgress.value, dragging = false)
                                onDismiss()
                            } else {
                                // Spring back to fully open.
                                swipeTarget = SpotlightSwipe(1f, dragging = false)
                            }
                        }
                    }
                }
            }
            .padding(16.dp).testTag("spotlight")) {
          // Everything inside fades in once the panel is big enough to hold it; the top is kept clear of the camera hole.
          // It settles down into place from the island as it fades in, and lifts back up into it as it fades out.
          Column(Modifier.graphicsLayer {
              val a = if (pill != null) SpotlightEmerge.contentAlpha(effectiveProgress()) else 1f
              alpha = a
              if (animationsOn) { translationY = -(1f - a) * 8.dp.toPx(); val s = .97f + .03f * a; scaleX = s; scaleY = s; transformOrigin = TransformOrigin(.5f, 0f) }
          }) {
            if (pill != null) Spacer(Modifier.height(with(density) { (pillBottom - pillTop).toDp() }.coerceAtLeast(0.dp)))
            CompositionLocalProvider(LocalContentColor provides ink) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().focusRequester(focus).testTag("spotlight-field"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ink, unfocusedTextColor = ink, cursorColor = ink,
                    focusedBorderColor = ink.copy(alpha = .55f), unfocusedBorderColor = ink.copy(alpha = .25f),
                    focusedPlaceholderColor = ink.copy(alpha = .55f), unfocusedPlaceholderColor = ink.copy(alpha = .55f),
                    focusedLeadingIconColor = ink.copy(alpha = .8f), unfocusedLeadingIconColor = ink.copy(alpha = .8f),
                    focusedTrailingIconColor = ink.copy(alpha = .8f), unfocusedTrailingIconColor = ink.copy(alpha = .8f)),
                placeholder = { Text(stringResource(com.jake.duolauncher.R.string.search_apps_answers_feeds), maxLines = 1) }, singleLine = true, shape = Corner.pill,
                // Enter opens the top app, as on iOS; with no app to open it searches the web in your browser with the words typed.
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                    when (SpotlightEnter.route(appResults.isNotEmpty(), singleQuick != null, actionContacts.isNotEmpty(), query.isBlank())) {
                        SpotlightEnter.Route.APP -> launch(appResults.first())
                        SpotlightEnter.Route.QUICK -> singleQuick?.let { actions.run(it, focusOn(), onToggleFocus); onDismiss() }
                        SpotlightEnter.Route.CONTACT -> actionContacts.firstOrNull()?.let {
                            actions.openContact(contactKind ?: ContactActionKind.DIAL, it.number); onDismiss()
                        }
                        SpotlightEnter.Route.WEB -> if (handoff.searchWeb(query)) onDismiss()
                        SpotlightEnter.Route.NONE -> Unit
                    }
                }),
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search") } })
            val nothing = smart == null && singleQuick == null && actionContacts.isEmpty() && settingsHits.isEmpty() && appResults.isEmpty() && contacts.isEmpty() && feedResults.isEmpty() && suggestions.isEmpty()
            val typed = query.isNotBlank()
            val webOffered = suggestions.any { it.kind == HandoffKind.WEB }
            LazyColumn(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (smart != null) item("smart") {
                    Column(Modifier.fillMaxWidth().clip(Corner.medium).background(ink.copy(alpha = .10f))
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
                if (singleQuick != null) item("quick-header") {
                    SpotlightHeader(stringResource(com.jake.duolauncher.R.string.spotlight_actions), ink)
                }
                if (singleQuick != null) item("quick") {
                    SpotlightRow(singleQuick.label, singleQuick.detail, "spotlight-quick", true, ink, {
                        actions.run(singleQuick, focusOn(), onToggleFocus)
                        onDismiss()
                    }) {
                        Box(Modifier.size(40.dp).background(ink.copy(alpha = .14f), Corner.icon), contentAlignment = Alignment.Center) {
                            Icon(actionIcon(singleQuick), null, Modifier.size(22.dp))
                        }
                    }
                }
                if (actionContacts.isNotEmpty()) {
                    item("actions-header") {
                        SpotlightHeader(if (contactKind == ContactActionKind.DIAL) "Call" else "Message", ink)
                    }
                    items(actionContacts, key = { "action-${it.name}-${it.number}" }) { contact ->
                        SpotlightRow(contact.name, contact.number, "spotlight-action-${contact.number}", false, ink, {
                            actions.openContact(contactKind ?: ContactActionKind.DIAL, contact.number)
                            onDismiss()
                        }) {
                            Box(Modifier.size(40.dp).background(ink.copy(alpha = .14f), Corner.icon), contentAlignment = Alignment.Center) {
                                Icon(if (contactKind == ContactActionKind.DIAL) Icons.Rounded.Call else Icons.Rounded.Sms, null, Modifier.size(22.dp))
                            }
                        }
                    }
                }
                if (!typed && suggested.isNotEmpty()) {
                    item("suggested-header") { SpotlightHeader(stringResource(com.jake.duolauncher.R.string.spotlight_suggested), ink) }
                    items(suggested, key = { "suggested-${it.id}" }) { app ->
                        SpotlightRow(app.label, null, "spotlight-app-${app.id}", false, ink, { launch(app) }) {
                            Image(app.image, null, Modifier.size(40.dp)
                            .onGloballyPositioned { iconBounds[app.id] = it.boundsInWindow().toAndroidBounds() }.glassIcon(lift = false).clip(Corner.icon))
                        }
                    }
                }
                // Before anything is typed the panel also offers the quick actions that need no argument, one tap instead of a command.
                if (!typed && idle.isNotEmpty()) {
                    item("idle-header") { SpotlightHeader(stringResource(com.jake.duolauncher.R.string.spotlight_actions), ink) }
                    items(idle, key = { "idle-${it.javaClass.simpleName}" }) { action ->
                        SpotlightRow(action.label, action.detail, "spotlight-idle-${action.javaClass.simpleName.lowercase()}", false, ink, {
                            actions.run(action, focusOn(), onToggleFocus)
                            onDismiss()
                        }) {
                            Box(Modifier.size(40.dp).background(ink.copy(alpha = .14f), Corner.icon), contentAlignment = Alignment.Center) {
                                Icon(actionIcon(action), null, Modifier.size(22.dp))
                            }
                        }
                    }
                }
                if (appResults.isNotEmpty()) item("apps-header") { SpotlightHeader(stringResource(com.jake.duolauncher.R.string.spotlight_apps), ink) }
                itemsIndexed(appResults, key = { _, app -> "app-${app.id}" }) { index, app ->
                    // The first result is what Enter opens, so it is marked as the top hit.
                    SpotlightRow(app.label, if (index == 0) stringResource(com.jake.duolauncher.R.string.spotlight_top_hit) else null,
                        "spotlight-app-${app.id}", index == 0, ink, { launch(app) },
                        match = SpotlightRank.highlightRange(app.label, query)) {
                        Image(app.image, null, Modifier.size(40.dp)
                            .onGloballyPositioned { iconBounds[app.id] = it.boundsInWindow().toAndroidBounds() }.glassIcon(lift = false).clip(Corner.icon))
                    }
                }
                if (contacts.isNotEmpty()) item("contacts-header") { SpotlightHeader(stringResource(com.jake.duolauncher.R.string.spotlight_contacts), ink) }
                items(contacts, key = { "contact-${it.id}" }) { contact ->
                    SpotlightRow(contact.name, null, "spotlight-contact-${contact.id}", false, ink, { onOpenContact(contact) }) {
                        Box(Modifier.size(40.dp).background(ink.copy(alpha = .14f), CircleShape), contentAlignment = Alignment.Center) {
                            Text(contact.name.firstOrNull()?.uppercase() ?: "?", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (settingsHits.isNotEmpty()) item("settings-header") { SpotlightHeader(stringResource(com.jake.duolauncher.R.string.spotlight_settings), ink) }
                items(settingsHits, key = { "settings-${it.action}" }) { page ->
                    SpotlightRow(page.label, stringResource(com.jake.duolauncher.R.string.spotlight_settings_detail), "spotlight-settings-${page.label.lowercase().replace(' ', '-')}", false, ink, {
                        runCatching { context.startActivity(android.content.Intent(page.action).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        onDismiss()
                    }) {
                        Box(Modifier.size(40.dp).background(ink.copy(alpha = .12f), Corner.icon), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Settings, null, Modifier.size(22.dp))
                        }
                    }
                }
                if (feedResults.isNotEmpty()) item("feed-header") { SpotlightHeader(stringResource(com.jake.duolauncher.R.string.spotlight_headlines), ink) }
                items(feedResults, key = { "feed-${it.link}" }) { entry ->
                    SpotlightRow(entry.title, entry.sourceLabel, "spotlight-feed", false, ink, { onOpenFeedLink(entry.link) }, titleLines = 2) {
                        Box(Modifier.size(40.dp).background(ink.copy(alpha = .10f), Corner.icon), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.RssFeed, null, Modifier.size(20.dp))
                        }
                    }
                }
                if (typed && nothing) item("none") {
                    Text(stringResource(com.jake.duolauncher.R.string.nothing_found), Modifier.padding(12.dp), color = ink.copy(alpha = .7f))
                }
                // With no launch history yet the panel would otherwise just be quick actions; teach the typed commands too.
                if (!typed && suggested.isEmpty()) item("hint") {
                    Text(stringResource(com.jake.duolauncher.R.string.spotlight_try), Modifier.padding(12.dp),
                        color = ink.copy(alpha = .6f), style = UnoType.footnote)
                }
                if (suggestions.isNotEmpty()) item("elsewhere-header") { SpotlightHeader(stringResource(com.jake.duolauncher.R.string.spotlight_elsewhere), ink) }
                items(suggestions, key = { "handoff-${it.kind}-${it.packageName}" }) { target ->
                    HandoffRow(target, ink, Modifier.clickable { handoff.open(target, query); onDismiss() })
                }
                // Enter already searches the web; this row makes the same action visible, whatever the handoff setting.
                if (typed && !webOffered) item("web") {
                    SpotlightRow(stringResource(com.jake.duolauncher.R.string.spotlight_search_web, query.trim()), null, "spotlight-web", false, ink,
                        { if (handoff.searchWeb(query)) onDismiss() }) {
                        Box(Modifier.size(40.dp).background(ink.copy(alpha = .12f), Corner.icon), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Search, null, Modifier.size(20.dp))
                        }
                    }
                }
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
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).clip(Corner.small).padding(horizontal = 6.dp, vertical = 6.dp)
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

@Composable
private fun SpotlightHeader(text: String, ink: Color) {
    Text(text.uppercase(), Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp), color = ink.copy(alpha = .6f),
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
}

/** One result: a 40 dp leading mark, a title, an optional second line; the top hit sits on a faint plate. */
@Composable
private fun SpotlightRow(title: String, subtitle: String?, tag: String, highlighted: Boolean, ink: Color, onClick: () -> Unit,
    titleLines: Int = 1, match: IntRange? = null, leading: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(Corner.small)
        .then(if (highlighted) Modifier.background(ink.copy(alpha = .09f)) else Modifier)
        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
        .padding(horizontal = 6.dp, vertical = 6.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        leading()
        Column(Modifier.padding(start = 12.dp)) {
            val titleStyle = if (titleLines > 1) UnoType.subheadline else UnoType.callout
            if (match == null) Text(title, maxLines = titleLines, overflow = TextOverflow.Ellipsis, style = titleStyle)
            else Text(highlightMatch(title, match), maxLines = titleLines, overflow = TextOverflow.Ellipsis, style = titleStyle)
            if (subtitle != null) Text(subtitle, style = UnoType.caption2, color = ink.copy(alpha = .6f), maxLines = 1)
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
