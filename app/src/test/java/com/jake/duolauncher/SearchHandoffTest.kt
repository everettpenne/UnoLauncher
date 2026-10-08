package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class SearchHandoffTest {
    @Test fun suggestionsOnlyAppearWhenNothingElseAnswered() {
        assertTrue(HandoffLogic.offerStores("signal", 0, false))
        assertFalse(HandoffLogic.offerStores("signal", 2, false))
        assertFalse(HandoffLogic.offerStores("12*(3+4)", 0, true))
        assertFalse(HandoffLogic.offerStores("ab", 0, false))
        assertTrue(HandoffLogic.offerWeb("ab", 0, false))
        assertFalse(HandoffLogic.offerWeb("a", 0, false))
        assertFalse(HandoffLogic.offerWeb("signal", 1, false))
    }

    @Test fun browserChoicePrefersTheUsersPickThenVanadium() {
        val installed = listOf("com.android.chrome", HandoffLogic.VANADIUM, "org.mozilla.firefox")
        assertEquals("org.mozilla.firefox", HandoffLogic.pickBrowser("org.mozilla.firefox", installed))
        assertEquals(HandoffLogic.VANADIUM, HandoffLogic.pickBrowser(null, installed))
        assertEquals(HandoffLogic.VANADIUM, HandoffLogic.pickBrowser("gone.browser", installed))
        assertEquals("com.android.chrome", HandoffLogic.pickBrowser(null, listOf("com.android.chrome")))
        assertNull(HandoffLogic.pickBrowser(null, emptyList()))
    }

    @Test fun onlyEnabledAndInstalledStoresAreOffered() {
        val installed = setOf("org.fdroid.fdroid", "com.android.vending")
        assertEquals(listOf("fdroid"), HandoffLogic.pickStores(HandoffLogic.DEFAULT_STORES, installed).map { it.id })
        assertEquals(listOf("fdroid", "play"), HandoffLogic.pickStores(setOf("fdroid", "aurora", "play"), installed).map { it.id })
        assertTrue(HandoffLogic.pickStores(emptySet(), installed).isEmpty())
    }

    @Test fun storeLinksEncodeTheQueryAndWebSearchesCarryOnlyTheWords() {
        assertEquals("market://search?q=open%20source%20%26%20more", HandoffLogic.storeIntent("open source & more").dataUri)
        val web = HandoffLogic.webIntent("  best  term\u0007  ")
        assertEquals("best  term", web.searchQuery)
        assertNull(web.dataUri)
        assertEquals(HandoffLogic.MAX_QUERY, HandoffLogic.clean("x".repeat(500)).length)
    }
}
