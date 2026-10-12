package com.jake.duolauncher.search

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

    /** Apps whose name starts with the query come first, then names with a word that starts with it, then names that
     * merely contain it; each group alphabetical. A blank query matches nothing (Spotlight shows nothing until you type).
     */
    fun <T> apps(apps: List<T>, query: String, label: (T) -> String): List<T> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        fun tier(label: String): Int {
            val l = label.lowercase()
            return when {
                l.startsWith(q) -> 0
                l.split(' ', '-', '.', '_').any { it.startsWith(q) } -> 1
                l.contains(q) -> 2
                else -> 3
            }
        }
        return apps.map { it to tier(label(it)) }.filter { it.second < 3 }
            .sortedWith(compareBy({ it.second }, { label(it.first).lowercase() })).map { it.first }.take(MAX_APPS)
    }

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
    val appResults = remember(apps, query) { SpotlightRank.apps(apps, query) { it.label } }
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
                placeholder = { Text("Search apps, answers, feeds", maxLines = 1) }, singleLine = true, shape = Corner.pill,
                // Enter opens the top app, as on iOS; with no app to open it searches the web in your browser with the words typed.
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                    when {
                        appResults.isNotEmpty() -> onLaunch(appResults.first())
                        query.isNotBlank() -> if (handoff.searchWeb(query)) onDismiss()
                    }
                }),
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search") } })
            val nothing = smart == null && appResults.isEmpty() && contacts.isEmpty() && feedResults.isEmpty() && suggestions.isEmpty()
            LazyColumn(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (smart != null) item("smart") {
                    Column(Modifier.fillMaxWidth().clip(Corner.medium).background(ink.copy(alpha = .10f)).padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("spotlight-answer")) {
                        Text(smart.detail, color = ink.copy(alpha = .7f), fontSize = 12.sp)
                        Text(smart.text, color = ink, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    }
                }
                items(appResults, key = { "app-${it.id}" }) { app ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(Corner.small).clickable { onLaunch(app) }
                        .padding(horizontal = 6.dp, vertical = 6.dp).testTag("spotlight-app-${app.id}"), verticalAlignment = Alignment.CenterVertically) {
                        Image(app.image, null, Modifier.size(40.dp).clip(Corner.icon))
                        Text(app.label, Modifier.padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                items(contacts, key = { "contact-${it.id}" }) { contact ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(Corner.small).clickable { onOpenContact(contact) }
                        .padding(horizontal = 6.dp, vertical = 6.dp).testTag("spotlight-contact-${contact.id}"), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).background(ink.copy(alpha = .14f), CircleShape), contentAlignment = Alignment.Center) {
                            Text(contact.name.firstOrNull()?.uppercase() ?: "?", fontWeight = FontWeight.SemiBold)
                        }
                        Text(contact.name, Modifier.padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                items(feedResults, key = { "feed-${it.link}" }) { entry ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(Corner.small).clickable { onOpenFeedLink(entry.link) }
                        .padding(horizontal = 6.dp, vertical = 6.dp).testTag("spotlight-feed"), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).background(ink.copy(alpha = .10f), Corner.icon), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.RssFeed, null, Modifier.size(20.dp))
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                            Text(entry.sourceLabel, fontSize = 11.sp, color = ink.copy(alpha = .6f), maxLines = 1)
                        }
                    }
                }
                items(suggestions, key = { "handoff-${it.kind}-${it.packageName}" }) { target ->
                    HandoffRow(target, ink, Modifier.clickable { handoff.open(target, query); onDismiss() })
                }
                if (query.isNotBlank() && nothing) item("none") {
                    Text("Nothing found", Modifier.padding(12.dp), color = ink.copy(alpha = .7f))
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
