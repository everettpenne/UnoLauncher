package com.jake.duolauncher

import com.jake.duolauncher.island.PeekSmarts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeekSmartsTest {
    @Test fun aConversationIsAMessageAMessagingStyleOrAConversationShortcut() {
        assertTrue(PeekSmarts.isConversation(android.app.Notification.CATEGORY_MESSAGE, false, null))
        assertTrue(PeekSmarts.isConversation(null, true, null))
        assertTrue(PeekSmarts.isConversation(null, false, "chat-42"))
        assertFalse(PeekSmarts.isConversation(android.app.Notification.CATEGORY_PROMO, false, null))
    }

    @Test fun fourDropDownsInAMinuteIsAFlood() {
        assertTrue(PeekSmarts.flooding(listOf(0L, 10_000L, 20_000L, 30_000L), now = 30_000L))
        assertFalse(PeekSmarts.flooding(listOf(0L, 10_000L, 20_000L), now = 20_000L))
        assertFalse("old ones no longer count", PeekSmarts.flooding(listOf(0L, 10_000L, 20_000L, 30_000L), now = 200_000L))
    }

    @Test fun anAppNeverOpenedIsQuietedAndOpeningItOnceBringsItBack() {
        var counts = emptyMap<String, Int>()
        repeat(PeekSmarts.UNOPENED_LIMIT - 1) { counts = PeekSmarts.shown(counts, "com.shop") }
        assertFalse(PeekSmarts.quieted(counts, "com.shop"))
        counts = PeekSmarts.shown(counts, "com.shop")
        assertTrue(PeekSmarts.quieted(counts, "com.shop"))
        assertFalse(PeekSmarts.quieted(PeekSmarts.opened(counts, "com.shop"), "com.shop"))
    }

    @Test fun countsSurviveBeingStored() {
        val counts = mapOf("com.a" to 3, "com.b" to 10)
        assertEquals(counts, PeekSmarts.parseCounts(PeekSmarts.serializeCounts(counts)))
        assertEquals(emptyMap<String, Int>(), PeekSmarts.parseCounts(null))
    }
}
