package com.jake.duolauncher

import com.jake.duolauncher.island.AssistantSurface
import com.jake.duolauncher.island.IslandState
import com.jake.duolauncher.island.SurfaceGesture
import com.jake.duolauncher.island.SurfaceGestures
import com.jake.duolauncher.island.SurfaceHost
import com.jake.duolauncher.island.SurfaceMode
import com.jake.duolauncher.island.SurfaceSeed
import com.jake.duolauncher.island.SurfaceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantSurfaceTest {
    private val rest = SurfaceState(SurfaceMode.REST)
    private val peek = SurfaceState(SurfaceMode.PEEK)
    private val island = SurfaceState(SurfaceMode.ISLAND)
    private val homeSearch = SurfaceState(SurfaceMode.SEARCH, SurfaceHost.HOME)
    private val appSearch = SurfaceState(SurfaceMode.SEARCH, SurfaceHost.OVERLAY, SurfaceSeed.Assist("page"))
    private val all = listOf(rest, peek, island, homeSearch, appSearch)

    private fun go(from: SurfaceState, g: SurfaceGesture) = SurfaceGestures.resolve(from, g)

    @Test fun tappingThePillOpensAndShutsTheIsland() {
        assertEquals(island, go(rest, SurfaceGesture.TapPill))
        assertEquals(island, go(peek, SurfaceGesture.TapPill))
        assertEquals(rest, go(island, SurfaceGesture.TapPill))
    }

    @Test fun pullOutcomesSetTheIsland() {
        for (from in listOf(rest, peek, island)) {
            assertEquals(rest, go(from, SurfaceGesture.PullCollapse))
            assertEquals(peek, go(from, SurfaceGesture.PullPeek))
            assertEquals(island, go(from, SurfaceGesture.PullOpen))
        }
    }

    @Test fun nothingOnTheIslandTakesSearchAway() {
        val islandOnly = listOf(SurfaceGesture.TapPill, SurfaceGesture.PullCollapse, SurfaceGesture.PullPeek, SurfaceGesture.PullOpen,
            SurfaceGesture.PeekTimeout, SurfaceGesture.NotificationCard, SurfaceGesture.Dismiss)
        for (from in listOf(homeSearch, appSearch)) for (g in islandOnly) assertEquals("$g from $from", from, go(from, g))
    }

    @Test fun aPeekTimesOutOnlyFromAPeek() {
        assertEquals(rest, go(peek, SurfaceGesture.PeekTimeout))
        assertEquals(island, go(island, SurfaceGesture.PeekTimeout))
        assertEquals(rest, go(rest, SurfaceGesture.PeekTimeout))
    }

    @Test fun aNotificationCardOpensTheIsland() {
        assertEquals(island, go(rest, SurfaceGesture.NotificationCard))
        assertEquals(island, go(peek, SurfaceGesture.NotificationCard))
    }

    @Test fun dismissShutsTheIslandOnly() {
        assertEquals(rest, go(island, SurfaceGesture.Dismiss))
        assertEquals(rest, go(peek, SurfaceGesture.Dismiss))
        assertEquals(rest, go(rest, SurfaceGesture.Dismiss))
    }

    @Test fun backStepsToRestAndIsUnhandledThere() {
        for (from in all - rest) {
            assertEquals(rest, go(from, SurfaceGesture.Back))
            assertTrue(SurfaceGestures.backHandled(from))
        }
        assertFalse(SurfaceGestures.backHandled(rest))
    }

    @Test fun searchOpensFromAnywhereInTheRightHost() {
        for (from in all) {
            assertEquals(homeSearch, go(from, SurfaceGesture.OpenSearch))
            assertEquals(appSearch, go(from, SurfaceGesture.SearchOverApp(SurfaceSeed.Assist("page"))))
        }
    }

    @Test fun closingSearchRestsAndLeavesTheIslandAlone() {
        assertEquals(rest, go(homeSearch, SurfaceGesture.CloseSearch))
        assertEquals(rest, go(appSearch, SurfaceGesture.CloseSearch))
        assertEquals(island, go(island, SurfaceGesture.CloseSearch))
    }

    @Test fun theIslandsFlagsAreViewsOfTheSurface() {
        val state = IslandState()
        state.toggle()
        assertTrue(state.expanded); assertFalse(state.peeked)
        state.peeked = true
        assertTrue(state.peeked); assertFalse(state.expanded)
        state.toggle()
        assertTrue(state.expanded); assertFalse(state.peeked)
        state.collapse()
        assertFalse(state.expanded); assertFalse(state.peeked)
        assertEquals(SurfaceMode.REST, state.surface.state.mode)
    }

    @Test fun openingSearchShutsTheIslandAndTheIslandCannotReopenOverIt() {
        val state = IslandState()
        state.expanded = true
        var homeOpen by state.surface.homeSearch
        homeOpen = true
        assertFalse(state.expanded)
        assertTrue(homeOpen)
        state.expanded = true
        state.toggle()
        assertTrue(homeOpen); assertFalse(state.expanded)
        homeOpen = false
        assertEquals(SurfaceMode.REST, state.surface.state.mode)
    }

    @Test fun searchOverAnAppIsNotHomesSearch() {
        val surface = AssistantSurface()
        var homeOpen by surface.homeSearch
        surface.on(SurfaceGesture.SearchOverApp(null))
        assertFalse(homeOpen)
        // Home closing its own search (it stops when an app comes in front) leaves search over the app open.
        homeOpen = false
        assertTrue(surface.state.searchOverApp)
        // Home opening search takes it from the app.
        homeOpen = true
        assertTrue(surface.state.searchInHome); assertFalse(surface.state.searchOverApp)
    }

    @Test fun backReportsWhetherItDidAnything() {
        val surface = AssistantSurface()
        assertFalse(surface.back())
        surface.on(SurfaceGesture.TapPill)
        assertTrue(surface.back())
        assertEquals(SurfaceMode.REST, surface.state.mode)
    }
}
