package com.jake.duolauncher

import com.jake.duolauncher.AppShortcuts.ShortcutFacts
import org.junit.Assert.*
import org.junit.Test

class AppShortcutsTest {
    @Test fun disabledAndNamelessShortcutsAreDropped() {
        val picked = AppShortcuts.pick(listOf(
            ShortcutFacts("a", true, 0, "New tab"), ShortcutFacts("b", false, 1, "Off"),
            ShortcutFacts("c", true, 2, "  "), ShortcutFacts("d", true, 3, "Incognito")))
        assertEquals(listOf("a", "d"), picked.map { it.id })
    }

    @Test fun theAppsOwnOrderWinsAndTheListIsCappedAtFour() {
        val all = (1..7).map { ShortcutFacts("s$it", true, 8 - it, "Label $it") }
        val picked = AppShortcuts.pick(all)
        assertEquals(AppShortcuts.MAX, picked.size)
        assertEquals(listOf("s7", "s6", "s5", "s4"), picked.map { it.id })
    }

    @Test fun duplicateIdsAppearOnce() {
        assertEquals(1, AppShortcuts.pick(listOf(ShortcutFacts("a", true, 0, "x"), ShortcutFacts("a", true, 1, "x"))).size)
    }
}
