package com.jake.duolauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandNotifyTest {
    @Test fun levelsCoverEverythingAndFallBackToName() {
        assertEquals(PeekLevel.NAME, PeekLevel.of(0)); assertEquals(PeekLevel.TITLE, PeekLevel.of(1)); assertEquals(PeekLevel.TEXT, PeekLevel.of(2))
        assertEquals(PeekLevel.NAME, PeekLevel.of(9)); assertEquals(PeekLevel.NAME, PeekLevel.of(-1))
    }

    @Test fun nameOnlyShowsNothingFromTheNotification() {
        assertNull(PeekContent.visible(PeekLevel.NAME, "Ada", "hi there", emptyList()))
    }

    @Test fun titleLevelShowsTheTitleButNeverTheText() {
        assertEquals("Ada" to null, PeekContent.visible(PeekLevel.TITLE, " Ada ", "secret body", emptyList()))
    }

    @Test fun textLevelShowsBothAndCutsLongText() {
        val (title, text) = PeekContent.visible(PeekLevel.TEXT, "Ada", "x".repeat(500), emptyList())!!
        assertEquals("Ada", title); assertEquals(PeekContent.MAX_TEXT, text!!.length)
        assertEquals(null to "only text", PeekContent.visible(PeekLevel.TEXT, "  ", "only text", emptyList()))
    }

    @Test fun anEmptyNotificationHasNothingToShow() {
        assertNull(PeekContent.visible(PeekLevel.TEXT, null, "", emptyList()))
        assertNull(PeekContent.visible(PeekLevel.TITLE, "", "body", emptyList()))
    }

    @Test fun blockedWordsHideEverythingAndIgnoreCase() {
        val words = PeekContent.parseWords("Spoiler, finale\nPASSWORD")
        assertEquals(listOf("spoiler", "finale", "password"), words)
        assertNull(PeekContent.visible(PeekLevel.TEXT, "Show", "the FINALE was great", words))
        assertNull(PeekContent.visible(PeekLevel.TITLE, "Your Password", null, words))
        assertEquals("Ada" to "see you", PeekContent.visible(PeekLevel.TEXT, "Ada", "see you", words))
        assertTrue(PeekContent.blocked(words, null, "a spoiler"))
        assertFalse(PeekContent.blocked(emptyList(), "anything"))
    }

    @Test fun wordListIsCleanedAndBounded() {
        assertEquals(listOf("a", "b"), PeekContent.parseWords(" a ,, b , A "))
        assertEquals(emptyList<String>(), PeekContent.parseWords(null))
        assertEquals(PeekContent.MAX_WORDS, PeekContent.parseWords((1..100).joinToString(",") { "w$it" }).size)
        assertEquals(PeekContent.MAX_WORD, PeekContent.parseWords("x".repeat(100)).single().length)
        assertEquals("a, b", PeekContent.serializeWords(listOf("a", "b")))
    }

    @Test fun timeoutsParseAndRejectOddValues() {
        assertEquals(mapOf("a.b" to 10, "c.d" to 3), PeekTimeouts.parse("a.b=10,c.d=3,e.f=7,junk,=5,g.h=x"))
        assertEquals(emptyMap<String, Int>(), PeekTimeouts.parse(null))
        assertEquals("a.b=10,c.d=3", PeekTimeouts.serialize(mapOf("c.d" to 3, "a.b" to 10)))
    }

    @Test fun timeoutsCycleThroughTheChoicesAndBackToAuto() {
        var map = emptyMap<String, Int>()
        assertEquals(PeekTimeouts.DEFAULT_SECONDS, PeekTimeouts.seconds(map, "p"))
        val seen = mutableListOf<String>()
        repeat(5) { map = PeekTimeouts.cycle(map, "p"); seen += PeekTimeouts.label(map, "p") }
        assertEquals(listOf("3s", "6s", "10s", "15s", "Auto"), seen)
        assertEquals(emptyMap<String, Int>(), map)
    }
}
