package com.jake.duolauncher.search

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.jake.duolauncher.AppEntry
import com.jake.duolauncher.appearance.AppearanceState
import com.jake.duolauncher.ExtrasState
import com.jake.duolauncher.feed.FeedEntry
import com.jake.duolauncher.LauncherState
import com.jake.duolauncher.widgets.UnoWidgets
import com.jake.duolauncher.appearance.PageGlass
import com.jake.duolauncher.island.AssistantSurface
import com.jake.duolauncher.island.IslandFrame
import com.jake.duolauncher.island.SurfaceGesture
import com.jake.duolauncher.search.context.ContextEnv

/**
 * What differs between the two places search is drawn (Home's window, and the window of its own over another app): how each opens what a
 * result leads to. Everything else about search is [SearchPanel]'s, the same in both.
 */
internal class SearchHost(
    /** Opens an app; the rectangle is its icon's place in the window, for a host that opens the app out of it (Home). */
    val launch: (AppEntry, android.graphics.Rect?) -> Unit,
    val openFeedLink: (String) -> Unit,
    val openContact: (ContactResult) -> Unit,
    /** Flips Focus (and its ringer change), as the control panel does. */
    val toggleFocus: () -> Unit,
    /** Opens Launcher settings at Search; null where the host has no settings to open. */
    val openSearchSettings: (() -> Unit)? = null,
)

/**
 * Search as the assistant surface shows it, in either host: the same panel, the same assistant and phone facts, and the same rule for
 * closing (anything that leads somewhere closes search on the [surface] first, and the host's window follows the surface). The host gives
 * only its glass, the frames the panel grows from, the progress of its own open/close animation, and how to open things ([SearchHost]).
 */
@Composable
internal fun SearchPanel(
    surface: AssistantSurface,
    host: SearchHost,
    context: Context,
    glass: PageGlass?,
    launcher: LauncherState,
    extras: ExtrasState?,
    appearance: AppearanceState?,
    feedEntries: List<FeedEntry>,
    handoff: HandoffResolver,
    /** What was on screen when the assist gesture opened search, as a fact for the assistant. */
    screenText: String?,
    visible: Boolean,
    progress: () -> Float,
    pill: IslandFrame?,
    from: IslandFrame? = null,
    /** No glass can sample what is behind (search over an app without a screenshot): the bar thins from black into it instead. */
    seeThrough: Boolean = false,
) {
    // Uno Lab's assistant, only with the setting on and Lab (signed with its own key) installed; Spotlight binds it while open.
    val assistantOn = extras?.localAssistant == true
    val assistant = remember(assistantOn) {
        if (assistantOn && LocalAssistant.isAvailable(context)) LocalAssistant(context) else null
    }
    // The phone's facts for the assistant, read from the latest state whenever a question is asked.
    val latestLauncher by rememberUpdatedState(launcher)
    val latestExtras by rememberUpdatedState(extras)
    val latestAppearance by rememberUpdatedState(appearance)
    val latestFeed by rememberUpdatedState(feedEntries)
    val latestScreen by rememberUpdatedState(screenText)
    val contextEnv = remember(assistant) {
        assistant?.let { ContextEnv(context.applicationContext, { latestLauncher }, { latestExtras }, { latestAppearance }, { latestFeed }, { latestScreen }) }
    }
    val close = { surface.on(SurfaceGesture.CloseSearch); Unit }
    Spotlight(glass, launcher.apps, launcher.hiddenApps, feedEntries, extras?.contactSearch == true, handoff,
        onLaunch = { app -> close(); host.launch(app, null) },
        onLaunchFrom = { app, bounds -> close(); host.launch(app, bounds) },
        onOpenFeedLink = { link -> close(); host.openFeedLink(link) },
        onOpenContact = { contact -> close(); host.openContact(contact) },
        onDismiss = close, progress = progress, visible = visible, pill = pill, from = from,
        noteSlot = launcher.widgetPlacements.firstOrNull { it.id == UnoWidgets.NOTE }?.slot,
        focusOn = { latestExtras?.focusOn == true },
        onToggleFocus = host.toggleFocus,
        assistant = assistant, contextEnv = contextEnv,
        onOpenSearchSettings = { host.openSearchSettings?.let { open -> close(); open() } },
        seeThrough = seeThrough,
        // Back's first step in search with something typed: the surface asks this before it closes search (AssistantSurface.back).
        onBackStep = { step -> surface.searchBackStep = step })
}
