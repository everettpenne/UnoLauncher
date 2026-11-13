package com.jake.duolauncher.island

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.reflect.KProperty

/**
 * What the assistant surface is showing. The pill at rest, held part-way open after a pull, the open island, or search.
 * Only one at a time: the island, Home's search and the search over an app are views of this one value, not three flags kept in step.
 */
internal enum class SurfaceMode { REST, PEEK, ISLAND, SEARCH }

/** Where search is drawn. In Home's own window, or in the window of its own over another app (search.AssistOverlay). */
internal enum class SurfaceHost { HOME, OVERLAY }

/** Why search was opened, for what it starts with. */
internal sealed interface SurfaceSeed {
    /** The system assist gesture, with the text that was on screen. */
    data class Assist(val screenText: String?) : SurfaceSeed
    /** The island's "Ready": the assistant's answer is waiting. */
    data object Answer : SurfaceSeed
}

internal data class SurfaceState(val mode: SurfaceMode = SurfaceMode.REST, val host: SurfaceHost = SurfaceHost.HOME, val seed: SurfaceSeed? = null) {
    val searchInHome: Boolean get() = mode == SurfaceMode.SEARCH && host == SurfaceHost.HOME
    val searchOverApp: Boolean get() = mode == SurfaceMode.SEARCH && host == SurfaceHost.OVERLAY
}

/** What the person (or the system) did to the surface. The hosts report these; [SurfaceGestures] decides what they mean. */
internal sealed interface SurfaceGesture {
    /** A tap on the pill, or on the open island's own toggle. */
    data object TapPill : SurfaceGesture
    /** A pull on the pill let go: back to rest, held part-way open, or open. */
    data object PullCollapse : SurfaceGesture
    data object PullPeek : SurfaceGesture
    data object PullOpen : SurfaceGesture
    /** The part-way-open island tucking itself away after its few seconds. */
    data object PeekTimeout : SurfaceGesture
    /** A notification card dropping down on the island. It never takes over search. */
    data object NotificationCard : SurfaceGesture
    /** A tap outside the open island, or a swipe up that closes it. */
    data object Dismiss : SurfaceGesture
    /** Back. */
    data object Back : SurfaceGesture
    /** Anything on Home that opens its search: the pull down past the island, the island's Search row, the search button. */
    data object OpenSearch : SurfaceGesture
    /** Search shown over another app: the assist gesture, or the island's "Ready" tapped there. */
    data class SearchOverApp(val seed: SurfaceSeed?) : SurfaceGesture
    /** Search closed: a result was opened, it was dismissed, or the screen went off. */
    data object CloseSearch : SurfaceGesture
}

/** The one table of what each gesture does, from each mode. Pure, so every row is unit-tested. */
internal object SurfaceGestures {
    fun resolve(now: SurfaceState, gesture: SurfaceGesture): SurfaceState {
        val mode = now.mode
        val searching = mode == SurfaceMode.SEARCH
        fun to(m: SurfaceMode) = SurfaceState(m)
        return when (gesture) {
            SurfaceGesture.TapPill -> when (mode) {
                SurfaceMode.REST, SurfaceMode.PEEK -> to(SurfaceMode.ISLAND)
                SurfaceMode.ISLAND -> to(SurfaceMode.REST)
                SurfaceMode.SEARCH -> now
            }
            SurfaceGesture.PullCollapse -> if (searching) now else to(SurfaceMode.REST)
            SurfaceGesture.PullPeek -> if (searching) now else to(SurfaceMode.PEEK)
            SurfaceGesture.PullOpen -> if (searching) now else to(SurfaceMode.ISLAND)
            SurfaceGesture.PeekTimeout -> if (mode == SurfaceMode.PEEK) to(SurfaceMode.REST) else now
            SurfaceGesture.NotificationCard -> if (searching) now else to(SurfaceMode.ISLAND)
            SurfaceGesture.Dismiss -> if (mode == SurfaceMode.ISLAND || mode == SurfaceMode.PEEK) to(SurfaceMode.REST) else now
            SurfaceGesture.Back -> if (mode == SurfaceMode.REST) now else to(SurfaceMode.REST)
            SurfaceGesture.OpenSearch -> SurfaceState(SurfaceMode.SEARCH, SurfaceHost.HOME)
            is SurfaceGesture.SearchOverApp -> SurfaceState(SurfaceMode.SEARCH, SurfaceHost.OVERLAY, gesture.seed)
            SurfaceGesture.CloseSearch -> if (searching) to(SurfaceMode.REST) else now
        }
    }

    /** Whether Back did something from [now] (otherwise it belongs to whatever is below). */
    fun backHandled(now: SurfaceState): Boolean = now.mode != SurfaceMode.REST
}

/**
 * The single owner of what the assistant surface shows (see [SurfaceMode]). The island, Home's search and search over an app read it.
 * They change it only through [on], so what a gesture does is decided in one place ([SurfaceGestures]), not by the window that got the touch.
 * One per [IslandState] (the process's is IslandRuntime.state's), so a test's island has its own.
 */
internal class AssistantSurface {
    var state by mutableStateOf(SurfaceState())
        private set

    /** Applies [gesture]; returns whether anything changed. */
    fun on(gesture: SurfaceGesture): Boolean {
        val next = SurfaceGestures.resolve(state, gesture)
        if (next == state) return false
        state = next
        return true
    }

    /** One step back; false when there was nothing to step back from. */
    fun back(): Boolean = SurfaceGestures.backHandled(state).also { if (it) on(SurfaceGesture.Back) }

    /** Home's search as a Boolean, for the code that reads and writes it as one (`var spotlightOpen by surface.homeSearch`). */
    val homeSearch = HomeSearch(this)

    class HomeSearch(private val surface: AssistantSurface) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = surface.state.searchInHome
        operator fun setValue(thisRef: Any?, property: KProperty<*>, open: Boolean) {
            if (open) surface.on(SurfaceGesture.OpenSearch) else if (surface.state.searchInHome) surface.on(SurfaceGesture.CloseSearch)
        }
    }
}
