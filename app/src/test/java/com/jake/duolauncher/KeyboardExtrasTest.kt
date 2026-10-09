package com.jake.duolauncher

import com.jake.duolauncher.keyboard.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class KeyboardExtrasTest {
    @Test fun theBottomRowOffersTheSwitcherOnlyWhenThereIsAnotherKeyboard() {
        val with = KeyboardModel.bottomRow(KeyPage.LETTERS, globe = true)
        val without = KeyboardModel.bottomRow(KeyPage.LETTERS, globe = false)
        assertTrue(with.any { it is Key.Globe }); assertFalse(without.any { it is Key.Globe })
        listOf(with, without).forEach { row ->
            assertTrue(row.first() is Key.Page); assertTrue(row.any { it is Key.Emoji }); assertTrue(row[row.lastIndex - 1] is Key.Space); assertTrue(row.last() is Key.Enter)
        }
    }

    @Test fun theEmojiPageDrawsItsOwnPanelSoItHasNoKeyRows() {
        assertTrue(KeyboardModel.rows(KeyPage.EMOJI, ShiftState.OFF).isEmpty())
        assertEquals(4, KeyboardModel.rows(KeyPage.LETTERS, ShiftState.OFF, globe = false).size)
    }

    @Test fun symbolsAndDigitsHaveLongPressAlternatesLikeIos() {
        assertTrue(KeyboardModel.alternates("-").containsAll(listOf("–", "—")))
        assertTrue(KeyboardModel.alternates("$").contains("€"))
        assertEquals(listOf("°"), KeyboardModel.alternates("0"))
        assertTrue(KeyboardModel.alternates("\"").contains("“"))
        assertTrue(KeyboardModel.alternates("e").contains("é"))
        assertTrue("capital letters keep their case", KeyboardModel.alternates("E").contains("É"))
        assertTrue(KeyboardModel.alternates("q").isEmpty()); assertTrue(KeyboardModel.alternates(",").isEmpty())
    }

    @Test fun aHeldBackspaceStartsByOneCharacterThenTakesWholeWords() {
        assertEquals(0, KeyboardModel.deleteStep("", 50))
        assertEquals(1, KeyboardModel.deleteStep("hello world", 0))
        assertEquals(1, KeyboardModel.deleteStep("hello world", KeyboardModel.WORD_DELETE_AFTER - 1))
        assertEquals("world".length, KeyboardModel.deleteStep("hello world", KeyboardModel.WORD_DELETE_AFTER))
        assertEquals("word  ".length, KeyboardModel.deleteStep("the word  ", 99))
    }

    @Test fun wordDeletionNeverReachesPastALineBreakOrDeletesNothingWhenThereIsText() {
        assertEquals("word  ".length, KeyboardModel.deleteStep("a word  ", KeyboardModel.WORD_DELETE_AFTER))
        assertEquals("two".length, KeyboardModel.deleteStep("one\ntwo", 99))
        assertEquals("\n".length, KeyboardModel.deleteStep("one\n", 99))
        assertEquals(3, KeyboardModel.deleteStep("   ", 99))
        assertTrue(KeyboardModel.deleteStep("x", 99) >= 1)
    }

    @Test fun emojiCategoriesParseFromHeadersAndRows() {
        val cats = EmojiData.parse(sequenceOf("# a comment-less header is ignored", "# Smileys|😀", "😀 😁 😂", "", "# Flags|🏁", "🇺🇸 🇬🇧", "# Empty|x"))
        assertEquals(listOf("Smileys", "Flags"), cats.map { it.name })
        assertEquals(listOf("😀", "😁", "😂"), cats[0].items); assertEquals("😀", cats[0].tab)
        assertEquals(listOf("🇺🇸", "🇬🇧"), cats[1].items)
    }

    @Test fun theBundledEmojiFileHasEveryCategoryAndNoBlankEmoji() {
        val file = File("src/main/assets/keyboard/emoji.txt").let { if (it.exists()) it else File("app/src/main/assets/keyboard/emoji.txt") }
        val cats = EmojiData.parse(file.readLines().asSequence())
        assertEquals(listOf("Smileys", "People", "Animals", "Food", "Activities", "Travel", "Objects", "Symbols", "Flags"), cats.map { it.name })
        cats.forEach { c -> assertTrue(c.name, c.items.size >= 20 && c.items.all { it.isNotBlank() } && c.items.distinct().size == c.items.size) }
    }

    @Test fun recentEmojiAreMostRecentFirstWithoutRepeatsAndCapped() {
        val r = EmojiRecents(cap = 3)
        assertTrue(r.isEmpty)
        r.add("😀"); r.add("😂"); r.add("😀"); r.add("🎉"); r.add("❤")
        assertEquals(listOf("❤", "🎉", "😀"), r.list())
    }
}
