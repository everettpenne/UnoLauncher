package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class ExtrasModelTest {
    @Test fun anOldSavedOrderKeepsItsOrderAndGainsNewTiles() {
        val order = PanelLayout.parseOrder("VOLUME,MEDIA,GONE")
        assertEquals(listOf(PanelTile.VOLUME, PanelTile.MEDIA), order.take(2))
        assertEquals("every tile appears exactly once", PanelTile.entries.toSet(), order.toSet())
        assertEquals(PanelTile.entries.size, order.size)
        assertEquals(PanelLayout.DEFAULT_ORDER, PanelLayout.parseOrder(null))
    }

    @Test fun movingStopsAtTheEnds() {
        val order = PanelLayout.DEFAULT_ORDER
        assertEquals(order, PanelLayout.move(order, order.first(), -1))
        assertEquals(order, PanelLayout.move(order, order.last(), 1))
        val moved = PanelLayout.move(order, PanelTile.VOLUME, 1)
        assertEquals(PanelTile.VOLUME, moved[2])
        assertEquals(PanelTile.BRIGHTNESS, moved[1])
    }

    @Test fun hidingEverythingStillLeavesARouteToQuickSettings() {
        val all = PanelTile.entries.toSet()
        assertEquals(listOf(PanelTile.SYSTEM), PanelLayout.visible(PanelLayout.DEFAULT_ORDER, all))
        assertEquals(PanelTile.entries.size - 1, PanelLayout.visible(PanelLayout.DEFAULT_ORDER, setOf(PanelTile.FOCUS)).size)
    }

    @Test fun ringerAndFlashlightShareARowOnlyWhenAdjacent() {
        val together = PanelLayout.rows(listOf(PanelTile.MEDIA, PanelTile.RINGER, PanelTile.FLASHLIGHT, PanelTile.SYSTEM))
        assertEquals(listOf(listOf(PanelTile.MEDIA), listOf(PanelTile.RINGER, PanelTile.FLASHLIGHT), listOf(PanelTile.SYSTEM)), together)
        val flipped = PanelLayout.rows(listOf(PanelTile.FLASHLIGHT, PanelTile.RINGER))
        assertEquals(listOf(listOf(PanelTile.FLASHLIGHT, PanelTile.RINGER)), flipped)
        val apart = PanelLayout.rows(listOf(PanelTile.RINGER, PanelTile.VOLUME, PanelTile.FLASHLIGHT))
        assertEquals(3, apart.size)
    }

    @Test fun shortcutsToggleAndCapAtFive() {
        var ids = emptyList<String>()
        ('a'..'g').forEach { ids = PanelLayout.toggleShortcut(ids, it.toString()) }
        assertEquals(PanelLayout.MAX_SHORTCUTS, ids.size)
        assertEquals(listOf("c", "d", "e", "f", "g"), ids)
        assertEquals(listOf("d", "e", "f", "g"), PanelLayout.toggleShortcut(ids, "c"))
        assertEquals(ids, PanelLayout.parseIds(PanelLayout.serializeIds(ids)))
    }

    @Test fun focusHidesOnlyWhileOnAndKeepsTheRest() {
        val apps = listOf("mail", "chat", "maps")
        assertEquals(apps, Focus.filter(apps, { it }, on = false, hidden = setOf("chat")))
        assertEquals(listOf("mail", "maps"), Focus.filter(apps, { it }, on = true, hidden = setOf("chat")))
        assertEquals(apps, Focus.filter(apps, { it }, on = true, hidden = emptySet()))
    }

    @Test fun badgesIgnoreOngoingAndGroupSummaries() {
        val counts = BadgeLogic.counts(listOf(
            ListedNotification("chat", ongoing = false, groupSummary = false),
            ListedNotification("chat", false, false),
            ListedNotification("chat", false, true),
            ListedNotification("music", ongoing = true, groupSummary = false),
            ListedNotification("mail", false, false)))
        assertEquals(mapOf("chat" to 2, "mail" to 1), counts)
        assertEquals("9+", BadgeLogic.label(10))
        assertEquals("3", BadgeLogic.label(3))
    }

    @Test fun contactsMatchWordPrefixesNotSubstrings() {
        assertTrue(ContactMatch.matches("Anna Smith", "ann sm"))
        assertTrue(ContactMatch.matches("Mary-Jane Watson", "jane"))
        assertFalse(ContactMatch.matches("Joanna Smith", "ann"))
        assertFalse(ContactMatch.matches("Anna", "   "))
        assertEquals(emptyList<String>(), ContactMatch.filter(listOf("Anna"), "a"))
        assertEquals(ContactMatch.MAX_RESULTS, ContactMatch.filter(List(9) { "Anna $it" }, "an").size)
    }

    @Test fun timerFormatsRoundUpAndStopwatchShowsTenths() {
        assertEquals("5:00", IslandClock.countdown(300_000))
        assertEquals("0:01", IslandClock.countdown(1))
        assertEquals("0:00", IslandClock.countdown(0))
        assertEquals("1:01:01", IslandClock.countdown(3_661_000))
        assertEquals("0:07.4", IslandClock.stopwatch(7_450))
        assertEquals("1:00:05", IslandClock.stopwatch(3_605_000))
        assertEquals(0L, IslandClock.remainingMs(10, 5))
        assertEquals(.5f, IslandClock.progress(50, 100, 100), .001f)
        assertEquals(1f, IslandClock.progress(0, 0, 0), 0f)
    }
}
