package com.jake.duolauncher.search

import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.island.*
import com.jake.duolauncher.feed.*
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Ranking for Spotlight's app results. Pure, so it is unit-tested. */
internal object SpotlightRank {
    const val MAX_APPS = 6
    const val MAX_FEED = 4
    const val MAX_SUGGESTED = 4

    /** How a label matches a query, best first. [NONE] drops the app from the results. */
    enum class Tier { PREFIX, WORD_PREFIX, INITIALS, CONTAINS, FUZZY, NONE }

    private fun words(label: String): List<String> {
        // "YouTube Music" -> you, tube, music: split on separators and on lower-to-upper humps.
        val spaced = label.replace(Regex("""([a-z0-9])([A-Z])"""), "$1 $2")
        return spaced.lowercase().split(' ', '-', '.', '_', '/').filter { it.isNotEmpty() }
    }

    fun tier(label: String, query: String): Tier {
        val q = query.trim().lowercase().replace(" ", "")
        if (q.isEmpty()) return Tier.NONE
        val l = label.lowercase()
        val parts = words(label)
        return when {
            l.startsWith(query.trim().lowercase()) || l.replace(" ", "").startsWith(q) -> Tier.PREFIX
            parts.any { it.startsWith(q) } -> Tier.WORD_PREFIX
            // "ytm" finds YouTube Music, "gm" Google Maps: the query is a run of leading letters of the words.
            q.length >= 2 && parts.size >= 2 && initialsMatch(parts, q) -> Tier.INITIALS
            l.contains(q) || l.replace(" ", "").contains(q) -> Tier.CONTAINS
            // Only for longer queries, or two letters would match half the drawer.
            q.length >= 3 && isSubsequence(q, l.replace(" ", "")) -> Tier.FUZZY
            else -> Tier.NONE
        }
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

    /** Frecency: launches count for more when recent. A launch loses half its weight every [HALF_LIFE_DAYS] days. */
    const val HALF_LIFE_DAYS = 14.0
    fun frecency(count: Int, lastLaunchMs: Long, nowMs: Long): Double {
        if (count <= 0) return 0.0
        val ageDays = ((nowMs - lastLaunchMs).coerceAtLeast(0L)) / 86_400_000.0
        return count * Math.pow(0.5, ageDays / HALF_LIFE_DAYS)
    }

    /**
     * Best match first: how the name matches, then how often and recently you open the app, then alphabetical.
     * A blank query matches nothing (see [suggested] for what Spotlight offers before you type).
     */
    fun <T> apps(apps: List<T>, query: String, label: (T) -> String, frecency: (T) -> Double = { 0.0 }): List<T> {
        if (query.isBlank()) return emptyList()
        return apps.map { it to tier(label(it), query) }.filter { it.second != Tier.NONE }
            .sortedWith(compareBy<Pair<T, Tier>>({ it.second }, { -frecency(it.first) }, { label(it.first).lowercase() }))
            .map { it.first }.take(MAX_APPS)
    }

    /** What to offer before anything is typed: the apps you open most, if you have opened any. */
    fun <T> suggested(apps: List<T>, frecency: (T) -> Double): List<T> =
        apps.filter { frecency(it) > .05 }.sortedByDescending(frecency).take(MAX_SUGGESTED)

    /** Saved feed entries whose title or summary mentions every word of the query. */
    fun feed(entries: List<FeedEntry>, query: String): List<FeedEntry> {
        val words = query.trim().lowercase().split(' ').filter { it.length >= 2 }
        if (words.isEmpty()) return emptyList()
        return entries.filter { e -> val text = (e.title + " " + e.summary).lowercase(); words.all { it in text } }.take(MAX_FEED)
    }
}

/**
 * How the search panel grows out of the island. At [progress] 0 the panel is exactly the collapsed pill (same width and height,
 * same centre, black like the pill); at 1 it is the full panel. The panel's own content is drawn once and scaled, so the
 * only numbers needed are the pill's size and where its centre is.
 */
internal object SpotlightEmerge {
    /** The panel's horizontal scale: the pill's width as a fraction of the panel's, growing to 1. */
    fun scaleX(progress: Float, pillWidth: Float, panelWidth: Float): Float {
        if (panelWidth <= 0f) return 1f
        val from = (pillWidth / panelWidth).coerceIn(.05f, 1f)
        return from + (1f - from) * progress.coerceIn(0f, 1f)
    }

    /** The panel's vertical scale, from the pill's height as a fraction of the panel's. */
    fun scaleY(progress: Float, pillHeight: Float, panelHeight: Float): Float {
        if (panelHeight <= 0f) return 1f
        val from = (pillHeight / panelHeight).coerceIn(.03f, 1f)
        return from + (1f - from) * progress.coerceIn(0f, 1f)
    }

    /** Sideways shift that keeps the panel's centre moving from the pill's centre to its own as it grows. */
    fun shiftX(progress: Float, pillCenterX: Float, panelCenterX: Float) = (pillCenterX - panelCenterX) * (1f - progress.coerceIn(0f, 1f))

    /** The fields and results fade in during the second half of the growth, so they are never seen squashed. */
    fun contentAlpha(progress: Float) = ((progress - .45f) / .55f).coerceIn(0f, 1f)
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
    onOpenFeedLink: (String) -> Unit,
    onOpenContact: (ContactResult) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** 0 = the collapsed island pill, 1 = fully open; driven by the caller's enter/exit transition. */
    progress: Float = 1f,
    /** The collapsed pill the panel grows from (null without an island: the panel then grows from the top edge). */
    pill: IslandFrame? = null,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
    val history = remember { SpotlightHistory(context) }
    val scores = remember { history.scores() }
    // Hidden apps are never suggested or ranked; typing one's exact name finds it.
    val searchable = remember(apps, hiddenApps, query) { HiddenApps.shown(apps, hiddenApps, query, { it.id }, { it.label }) }
    val appResults = remember(searchable, query) { SpotlightRank.apps(searchable, query, { it.label }, { scores[it.id] ?: 0.0 }) }
    val suggested = remember(apps, hiddenApps) { SpotlightRank.suggested(apps.filter { it.id !in hiddenApps }) { scores[it.id] ?: 0.0 } }
    val launch = { app: AppEntry -> history.record(app.id); onLaunch(app) }
    var copied by remember(query) { mutableStateOf(false) }
    val feedResults = remember(feedEntries, query) { SpotlightRank.feed(feedEntries, query) }
    val smart = remember(query) { SearchSmarts.answer(query) }
    val suggestions = remember(query, appResults.size, smart, handoff) { handoff.targets(query, appResults.size, smart != null) }
    var contacts by remember { mutableStateOf(emptyList<ContactResult>()) }
    LaunchedEffect(query, contactsEnabled) {
        if (!contactsEnabled || query.trim().length < ContactMatch.MIN_QUERY) { contacts = emptyList(); return@LaunchedEffect }
        delay(150)
        contacts = withContext(Dispatchers.IO) { ContactsSearch.search(context, query) }
    }
    // The island's own look: a true-black body (the pill's colour, so the panel reads as the pill grown larger), white ink and
    // a hairline rim. The glass the panel used to have belonged to a sheet that appeared from nowhere.
    val shape = RoundedCornerShape(30.dp)
    val ink = Color.White
    val quiet = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    var panelLeft by remember { mutableFloatStateOf(0f) }
    val pillTop = pill?.top ?: 0f
    val pillBottom = pill?.let { it.top + it.height } ?: 0f

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
        .background(Color.Black.copy(alpha = .38f * progress.coerceIn(0f, 1f)))
        .clickable(interactionSource = quiet, indication = null, onClick = onDismiss).testTag("spotlight-scrim"),
        contentAlignment = Alignment.TopCenter) {
        Column(Modifier.then(if (pill == null) Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 8.dp) else Modifier)
            .padding(top = with(density) { pillTop.toDp() })
            .imePadding().padding(horizontal = 12.dp)
            .widthIn(max = 460.dp).fillMaxWidth().heightIn(max = 560.dp)
            .onGloballyPositioned { panelSize = it.size; panelLeft = it.positionInWindow().x }
            .graphicsLayer {
                transformOrigin = TransformOrigin(.5f, 0f)
                if (pill != null) {
                    scaleX = SpotlightEmerge.scaleX(progress, pill.width, panelSize.width.toFloat())
                    scaleY = SpotlightEmerge.scaleY(progress, pill.height, panelSize.height.toFloat())
                    translationX = SpotlightEmerge.shiftX(progress, pill.left + pill.width / 2f, panelLeft + panelSize.width / 2f)
                } else {
                    alpha = progress.coerceIn(0f, 1f)
                    scaleX = .9f + .1f * progress; scaleY = .9f + .1f * progress
                }
            }
            .clip(shape).background(Color.Black).border(1.dp, Color.White.copy(alpha = .10f), shape)
            .clickable(interactionSource = quiet, indication = null) {}
            .padding(14.dp).testTag("spotlight")) {
          // Everything inside fades in once the panel is big enough to hold it; the top is kept clear of the camera hole.
          Column(Modifier.graphicsLayer { alpha = if (pill != null) SpotlightEmerge.contentAlpha(progress) else 1f }) {
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
                    when {
                        appResults.isNotEmpty() -> launch(appResults.first())
                        query.isNotBlank() -> if (handoff.searchWeb(query)) onDismiss()
                    }
                }),
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search") } })
            val nothing = smart == null && appResults.isEmpty() && contacts.isEmpty() && feedResults.isEmpty() && suggestions.isEmpty()
            val typed = query.isNotBlank()
            val webOffered = suggestions.any { it.kind == HandoffKind.WEB }
            LazyColumn(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (smart != null) item("smart") {
                    Column(Modifier.fillMaxWidth().clip(Corner.medium).background(ink.copy(alpha = .10f))
                        .clickable {
                            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Spotlight answer", smart.text))
                            copied = true
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp).testTag("spotlight-answer")) {
                        Text(smart.detail, color = ink.copy(alpha = .7f), fontSize = 12.sp)
                        Text(smart.text, color = ink, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                        Text(stringResource(if (copied) com.jake.duolauncher.R.string.spotlight_copied else com.jake.duolauncher.R.string.spotlight_tap_to_copy),
                            color = ink.copy(alpha = .5f), fontSize = 11.sp)
                    }
                }
                if (!typed && suggested.isNotEmpty()) {
                    item("suggested-header") { SpotlightHeader(stringResource(com.jake.duolauncher.R.string.spotlight_suggested), ink) }
                    items(suggested, key = { "suggested-${it.id}" }) { app ->
                        SpotlightRow(app.label, null, "spotlight-app-${app.id}", false, ink, { launch(app) }) {
                            Image(app.image, null, Modifier.size(40.dp).clip(Corner.icon))
                        }
                    }
                }
                if (appResults.isNotEmpty()) item("apps-header") { SpotlightHeader(stringResource(com.jake.duolauncher.R.string.spotlight_apps), ink) }
                itemsIndexed(appResults, key = { _, app -> "app-${app.id}" }) { index, app ->
                    // The first result is what Enter opens, so it is marked as the top hit.
                    SpotlightRow(app.label, if (index == 0) stringResource(com.jake.duolauncher.R.string.spotlight_top_hit) else null,
                        "spotlight-app-${app.id}", index == 0, ink, { launch(app) }) {
                        Image(app.image, null, Modifier.size(40.dp).clip(Corner.icon))
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
            Text(target.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            Text(if (target.kind == HandoffKind.WEB) "Opens your browser's own search" else "Not installed? Look for it here",
                fontSize = 11.sp, color = ink.copy(alpha = .6f), maxLines = 1)
        }
    }
}

@Composable
private fun SpotlightHeader(text: String, ink: Color) {
    Text(text.uppercase(), Modifier.padding(start = 8.dp, top = 10.dp, bottom = 2.dp), color = ink.copy(alpha = .5f),
        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .8.sp)
}

/** One result: a 40 dp leading mark, a title, an optional second line; the top hit sits on a faint plate. */
@Composable
private fun SpotlightRow(title: String, subtitle: String?, tag: String, highlighted: Boolean, ink: Color, onClick: () -> Unit,
    titleLines: Int = 1, leading: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(Corner.small)
        .then(if (highlighted) Modifier.background(ink.copy(alpha = .09f)) else Modifier)
        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
        .padding(horizontal = 6.dp, vertical = 6.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        leading()
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, maxLines = titleLines, overflow = TextOverflow.Ellipsis, fontSize = if (titleLines > 1) 14.sp else 16.sp)
            if (subtitle != null) Text(subtitle, fontSize = 11.sp, color = ink.copy(alpha = .6f), maxLines = 1)
        }
    }
}
