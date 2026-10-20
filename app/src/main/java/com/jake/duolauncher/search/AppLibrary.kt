@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.jake.duolauncher.search

import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow

@Composable
internal fun AppLibrary(
    state: LauncherState, query: String, onQuery: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit, onPin: (String, Boolean) -> Unit, onActions: (AppEntry) -> Unit,
    modifier: Modifier = Modifier, editing: Boolean = false,
    drag: HomeDragState? = null, page: Int? = null,
    onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit = { app, _ -> onLaunch(app) },
    onTurnOnWork: (Long) -> Unit = {},
    contacts: List<ContactResult> = emptyList(),
    onContact: (ContactResult) -> Unit = {},
    handoff: HandoffResolver? = null,
) {
    val glass = !editing
    val palette = LocalDuoPalette.current
    val ink = if (glass) Ink else MaterialTheme.colorScheme.onSurface
    val pinned = remember(state.homeSlots, state.leadingSlots) {
        (state.homeSlots.asSequence() + state.leadingSlots.asSequence()).filterNotNull().toSet()
    }
    val hasWork = state.profiles.any { it.isWork } || state.visibleApps.any { it.isWork }
    // A sum or a unit conversion typed into search is answered on the spot (nothing leaves the device).
    val smart = remember(query, editing) { if (editing) null else SearchSmarts.answer(query) }
    val clipboard = androidx.compose.ui.platform.LocalClipboard.current
    val clipScope = rememberCoroutineScope()
    val toastContext = androidx.compose.ui.platform.LocalContext.current
    var showWork by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // iOS-style rubber-banding at the list's ends. The 0.70.0 motion pass reached the feed but not All apps;
    // the shared modifier below both draws the resisted offset and springs it back (snapping with animations off).
    val animationsOn = remember {
        android.provider.Settings.Global.getFloat(toastContext.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    val elastic = rememberElasticOverscrollState(animationsOn)
    val selectedProfile = if (showWork) state.profiles.firstOrNull { it.isWork } else state.profiles.firstOrNull { it.isPersonal }
    LaunchedEffect(showWork, selectedProfile?.available, selectedProfile?.quiet) {
        listState.scrollToItem(0)
    }
    val visibleApps = remember(state.apps, state.hiddenApps, query, showWork, hasWork, editing) {
        // Hidden apps are out of the list; typing one's exact name (not while choosing Home apps) brings it back.
        HiddenApps.shown(state.apps, state.hiddenApps, if (editing) "" else query, { it.id }, { it.label }).filter { (!hasWork || it.isWork == showWork) && it.label.contains(query.trim(), true) }
    }
    val groups = remember(visibleApps) {
        visibleApps.groupBy {
            it.label.firstOrNull()?.takeIf(Char::isLetter)?.uppercaseChar()?.toString() ?: "#"
        }
    }
    // On Home the library is a liquid-glass panel over the wallpaper. Heavier blur than the
    // dock keeps the long list legible; the lens still bends the panel's rim.
    val liquid = LocalPageGlass.current?.takeIf { glass }
    CompositionLocalProvider(LocalKitInk provides ink) {
    Surface(modifier.then(if (liquid != null) Modifier.liquidGlass(liquid.backdrop, Corner.large,
            liquid.tint.copy(alpha = .40f), blurRadius = 6f, settings = liquid.settings) else Modifier),
        shape = Corner.large,
        color = if (liquid != null) Color.Transparent else if (glass) Glass.copy(alpha = .48f) else MaterialTheme.colorScheme.surface,
        contentColor = ink,
        border = if (glass && liquid == null) BorderStroke(1.dp, Color.White.copy(alpha = .38f)) else null) {
        Column(Modifier.background(Brush.verticalGradient(if (glass)
            listOf(Color.White.copy(alpha = .09f), Color.Transparent) else listOf(Color.Transparent, Color.Transparent)))
            .padding(horizontal = 16.dp).padding(top = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (editing) "Choose home apps" else "All apps", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                Text(if (editing) "${pinned.size} pinned" else "${visibleApps.size}", color = ink, style = UnoType.caption1)
            }
            if (hasWork) Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip(selected = !showWork, onClick = { showWork = false }, label = { Text(stringResource(com.jake.duolauncher.R.string.personal)) })
                GlassChip(selected = showWork, onClick = { showWork = true }, label = { Text(stringResource(com.jake.duolauncher.R.string.work)) })
            }
            GlassField(query, onQuery, Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag(if (editing) "pin-search" else "library-search"),
                placeholder = { Text(stringResource(com.jake.duolauncher.R.string.search_apps)) }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear search") } })
            // Asking which browsers and stores are installed (and loading their icons) is a trip to the package manager, so it is done when
            // the search changes, not each time the list's content is rebuilt.
            val suggestions = remember(editing, handoff, query, visibleApps.size, smart != null) {
                if (editing || handoff == null) emptyList() else handoff.targets(query, visibleApps.size, smart != null)
            }
            LazyColumn(Modifier.weight(1f).testTag("all-apps-list")
                .elasticVerticalEdges(elastic, atStart = { !listState.canScrollBackward }, atEnd = { !listState.canScrollForward }),
                state = listState,
                contentPadding = PaddingValues(bottom = 12.dp)) {
                if (smart != null) item("smart-answer") {
                    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(Corner.medium).background(ink.copy(alpha = .10f))
                        .clickable {
                            clipScope.launch { clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(android.content.ClipData.newPlainText("", smart.text))) }
                            android.widget.Toast.makeText(toastContext, "Copied ${smart.text}", android.widget.Toast.LENGTH_SHORT).show()
                        }.padding(horizontal = 16.dp, vertical = 12.dp).testTag("smart-answer")) {
                        Text(smart.detail, color = ink.copy(alpha = .7f), style = UnoType.caption1)
                        Text(smart.text, color = ink, style = UnoType.title2)
                        Text(stringResource(com.jake.duolauncher.R.string.tap_to_copy), color = ink.copy(alpha = .55f), style = UnoType.caption2)
                    }
                }
                if (showWork && selectedProfile?.available == false) item("work-paused") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (selectedProfile.quiet) "Work apps are paused" else "Work profile is unavailable")
                        if (selectedProfile.quiet) GlassButton(onClick = { onTurnOnWork(selectedProfile.userSerial) }, prominent = true,
                            modifier = Modifier.padding(top = 10.dp).testTag("turn-on-work")) { Text(stringResource(com.jake.duolauncher.R.string.turn_on_work_apps)) }
                    }
                }
                if (groups.isEmpty() && contacts.isEmpty() && smart == null) item { Text(if (state.loading) "Loading apps…" else "No apps found", Modifier.padding(vertical = 20.dp)) }
                groups.forEach { (letter, entries) ->
                    stickyHeader(key = "heading-$letter") {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            // An opaque small chip prevents text from showing through the sticky letter.
                            Box(Modifier.size(width = 32.dp, height = 28.dp).background(
                                if (glass) (if (palette.dark) Color(0xFF314852) else Color(0xFFB7CBD3))
                                else MaterialTheme.colorScheme.secondaryContainer,
                                Corner.small), contentAlignment = Alignment.Center) {
                                Text(letter, color = ink, style = UnoType.caption1.copy(fontWeight = FontWeight.SemiBold))
                            }
                            if (glass) HorizontalDivider(Modifier.weight(1f).padding(start = 10.dp), color = Color.White.copy(alpha = .24f))
                        }
                    }
                    items(entries, key = { it.id }) { app ->
                        val isPinned = app.id in pinned
                        val launchBounds = remember { android.graphics.Rect() }
                        val dragModifier = if (drag != null) Modifier.dropRegion(drag, DropTarget.Library(app.id), app.id, page) else Modifier
                        val click = { if (editing) onPin(app.id, !isPinned) else onLaunchFrom(app, launchBounds) }
                        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).then(dragModifier).clip(Corner.small).testTag("library-app-${app.id}")
                            .then(if (drag == null) Modifier.combinedClickable(onClick = click, onLongClick = { onActions(app) })
                                else Modifier.clickable(onClick = click).semantics { onLongClick("App options") { onActions(app); true } })
                            .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(app.image, null, Modifier.size(40.dp)
                                .onGloballyPositioned { launchBounds.set(it.boundsInWindow().toAndroidBounds()) }.clip(Corner.icon))
                            Text(app.label, Modifier.weight(1f).padding(start = 12.dp), maxLines = 2, style = UnoType.subheadline)
                            if (editing) IconButton(onClick = { onPin(app.id, !isPinned) }, Modifier.testTag("pin-${app.id}")) {
                                Icon(if (isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                                    if (isPinned) "Remove ${app.label} from home" else "Pin ${app.label} to home",
                                    tint = if (isPinned) MaterialTheme.colorScheme.primary else ink.copy(alpha = .5f),
                                    modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                if (suggestions.isNotEmpty()) {
                    item("handoff-header") {
                        Text(stringResource(com.jake.duolauncher.R.string.look_elsewhere), Modifier.padding(top = 12.dp, bottom = 4.dp), color = ink.copy(alpha = .6f), style = UnoType.caption1.copy(fontWeight = FontWeight.SemiBold))
                    }
                    items(suggestions, key = { "handoff-${it.kind}-${it.packageName}" }) { target ->
                        HandoffRow(target, ink, Modifier.clickable { handoff?.open(target, query) })
                    }
                }
                if (contacts.isNotEmpty()) {
                    item("contacts-header") {
                        Text(stringResource(com.jake.duolauncher.R.string.contacts), Modifier.padding(top = 12.dp, bottom = 4.dp),
                            color = ink.copy(alpha = .6f), style = UnoType.caption1.copy(fontWeight = FontWeight.SemiBold))
                    }
                    items(contacts, key = { "contact-${it.id}" }) { contact ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(Corner.small).clickable { onContact(contact) }
                            .testTag("contact-${contact.id}").padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).background(ink.copy(alpha = .14f), androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center) {
                                Text(contact.name.firstOrNull()?.uppercase() ?: "?", color = ink, fontWeight = FontWeight.SemiBold)
                            }
                            Text(contact.name, Modifier.weight(1f).padding(start = 12.dp), maxLines = 1, style = UnoType.subheadline)
                        }
                    }
                }
            }
        }
    }
    }
}
