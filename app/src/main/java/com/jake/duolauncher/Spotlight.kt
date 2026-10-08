package com.jake.duolauncher

import androidx.compose.foundation.Image
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

/** Search from anywhere on Home: a glass panel over whatever page you are on. One box finds apps, contacts (if you
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
    val shape = Corner.xlarge
    val panelBackdrop = rememberLayerBackdrop()
    val accent = LocalWallpaperAccent.current
    val surface = MaterialTheme.colorScheme.surface.let { base ->
        if (accent != null) androidx.compose.ui.graphics.lerp(base, accent.glass, .5f) else base
    }.copy(alpha = .66f)
    val ink = MaterialTheme.colorScheme.onSurface
    val quiet = remember { MutableInteractionSource() }

    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = .30f))
        .clickable(interactionSource = quiet, indication = null, onClick = onDismiss).testTag("spotlight-scrim"),
        contentAlignment = Alignment.TopCenter) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(horizontal = 12.dp).padding(top = 8.dp)
            .widthIn(max = 460.dp).fillMaxWidth().heightIn(max = 560.dp)
            .clickable(interactionSource = quiet, indication = null) {}
            .then(if (glass != null) Modifier.drawBackdrop(
                backdrop = glass.backdrop, shape = { shape },
                effects = {
                    vibrancy(); blur(8f.dp.toPx())
                    lens(lerp(8f, 44f, glass.settings.height).dp.toPx(), lerp(16f, 132f, glass.settings.amount).dp.toPx(),
                        depthEffect = true, chromaticAberration = glass.settings.chromatic > 0.05f)
                },
                highlight = { GlassRim.Light }, exportedBackdrop = panelBackdrop,
                onDrawSurface = { drawRect(surface) })
            else Modifier.background(MaterialTheme.colorScheme.surface, shape))
            .padding(14.dp).testTag("spotlight")) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().focusRequester(focus).testTag("spotlight-field"),
                placeholder = { Text("Search apps, answers, feeds", maxLines = 1) }, singleLine = true, shape = Corner.pill,
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
                        Image(app.icon.asImageBitmap(), null, Modifier.size(40.dp).clip(Corner.icon))
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
