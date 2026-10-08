package com.jake.duolauncher

import com.jake.duolauncher.keyboard.*
import org.junit.Assert.*
import org.junit.Test

class KeyboardModelTest {
    private fun labels(row: List<Key>) = row.map { when (it) { is Key.Char -> it.label; is Key.Page -> it.label; else -> it::class.simpleName } }

    @Test fun letterPageHasTheFamiliarRowsAndShiftChangesCase() {
        val lower = KeyboardModel.rows(KeyPage.LETTERS, ShiftState.OFF)
        assertEquals(4, lower.size)
        assertEquals("qwertyuiop".map { it.toString() }, labels(lower[0]))
        assertEquals(listOf("Shift") + "zxcvbnm".map { it.toString() } + "Backspace", labels(lower[2]))
        assertEquals("Q", labels(KeyboardModel.rows(KeyPage.LETTERS, ShiftState.ONCE)[0]).first())
        assertEquals("Q", labels(KeyboardModel.rows(KeyPage.LETTERS, ShiftState.LOCKED)[0]).first())
    }

    @Test fun everyPageEndsWithTheSameControlRowAndCanReachTheOthers() {
        KeyPage.entries.forEach { page ->
            val bottom = KeyboardModel.rows(page, ShiftState.OFF).last()
            assertTrue(bottom.any { it is Key.Space }); assertTrue(bottom.any { it is Key.Enter }); assertTrue(bottom.any { it is Key.Globe })
        }
        assertTrue(KeyboardModel.rows(KeyPage.LETTERS, ShiftState.OFF).last().first().let { it is Key.Page && it.target == KeyPage.NUMBERS })
        assertTrue(KeyboardModel.rows(KeyPage.NUMBERS, ShiftState.OFF).last().first().let { it is Key.Page && it.target == KeyPage.LETTERS })
    }

    @Test fun theOptionalNumberRowSitsAboveTheLettersOnly() {
        val rows = KeyboardModel.rows(KeyPage.LETTERS, ShiftState.OFF, numberRow = true)
        assertEquals(5, rows.size); assertEquals("1234567890".map { it.toString() }, labels(rows[0]))
        assertEquals(4, KeyboardModel.rows(KeyPage.NUMBERS, ShiftState.OFF, numberRow = true).size)
        assertEquals(4, KeyboardModel.rows(KeyPage.LETTERS, ShiftState.OFF).size)
    }

    @Test fun suggestionsAndAutocorrectStayOutOfAddressesNamesAndSecrets() {
        val text = 1
        assertTrue(KeyboardModel.allowsSuggestions(text)); assertTrue(KeyboardModel.allowsAutocorrect(text))
        assertTrue(KeyboardModel.allowsAutocorrect(text or 0x40))                 // short message
        assertFalse(KeyboardModel.allowsSuggestions(text or 0x20))                // email address
        assertFalse(KeyboardModel.allowsSuggestions(text or 0x10))                // web address
        assertFalse(KeyboardModel.allowsSuggestions(text or 0x80))                // password
        assertFalse(KeyboardModel.allowsSuggestions(text or 0x80000))             // the app asked for none
        assertTrue(KeyboardModel.allowsSuggestions(text or 0x60)); assertFalse(KeyboardModel.allowsAutocorrect(text or 0x60)) // a name
        assertFalse(KeyboardModel.allowsSuggestions(2)); assertFalse(KeyboardModel.allowsSuggestions(3)) // numbers, phones
    }

    @Test fun capitalisationFromTextFollowsSentencesWordsAndCharacters() {
        val sentences = 1 or 0x4000
        assertTrue(KeyboardModel.capsFromText("", sentences)); assertTrue(KeyboardModel.capsFromText("Hello. ", sentences))
        assertTrue(KeyboardModel.capsFromText("Really? ", sentences)); assertTrue(KeyboardModel.capsFromText("one\n", sentences))
        assertFalse(KeyboardModel.capsFromText("Hello", sentences)); assertFalse(KeyboardModel.capsFromText("Hello. ok", sentences))
        assertFalse(KeyboardModel.capsFromText("Hello, ", sentences)); assertFalse(KeyboardModel.capsFromText("", 1))
        assertFalse(KeyboardModel.capsFromText("Hello. ", 1))
        assertTrue(KeyboardModel.capsFromText("big ", 1 or 0x2000)); assertFalse(KeyboardModel.capsFromText("big", 1 or 0x2000))
        assertTrue(KeyboardModel.capsFromText("abc", 1 or 0x1000)); assertFalse(KeyboardModel.capsFromText("", 2 or 0x4000))
    }

    @Test fun shiftCyclesAndDoubleTapLocks() {
        assertEquals(ShiftState.ONCE, KeyboardModel.tapShift(ShiftState.OFF, false))
        assertEquals(ShiftState.OFF, KeyboardModel.tapShift(ShiftState.ONCE, false))
        assertEquals(ShiftState.LOCKED, KeyboardModel.tapShift(ShiftState.ONCE, true))
        assertEquals(ShiftState.OFF, KeyboardModel.tapShift(ShiftState.LOCKED, false))
        assertEquals(ShiftState.OFF, KeyboardModel.afterLetter(ShiftState.ONCE))
        assertEquals(ShiftState.LOCKED, KeyboardModel.afterLetter(ShiftState.LOCKED))
    }

    @Test fun capitalisationFollowsTheEditorButNeverBreaksCapsLock() {
        assertEquals(ShiftState.ONCE, KeyboardModel.shiftFor(8192, ShiftState.OFF))
        assertEquals(ShiftState.OFF, KeyboardModel.shiftFor(0, ShiftState.ONCE))
        assertEquals(ShiftState.LOCKED, KeyboardModel.shiftFor(0, ShiftState.LOCKED))
    }

    @Test fun enterShowsTheFieldsActionUnlessTheFieldWantsANewline() {
        assertEquals(EnterKind.SEARCH, KeyboardModel.enterKind(3))
        assertEquals(EnterKind.SEND, KeyboardModel.enterKind(4))
        assertEquals(EnterKind.NEXT, KeyboardModel.enterKind(5))
        assertEquals(EnterKind.DONE, KeyboardModel.enterKind(6))
        assertEquals(EnterKind.GO, KeyboardModel.enterKind(2))
        assertEquals(EnterKind.RETURN, KeyboardModel.enterKind(0))
        assertEquals(EnterKind.RETURN, KeyboardModel.enterKind(3 or 0x40000000))
        assertTrue(EnterKind.SEARCH.sendsAction); assertFalse(EnterKind.RETURN.sendsAction)
    }

    @Test fun numberAndPhoneFieldsOpenOnNumbersAndPasswordsAreRecognised() {
        assertEquals(KeyPage.NUMBERS, KeyboardModel.startPage(2)); assertEquals(KeyPage.NUMBERS, KeyboardModel.startPage(3))
        assertEquals(KeyPage.LETTERS, KeyboardModel.startPage(1))
        assertTrue(KeyboardModel.isPassword(1 or 0x80)); assertTrue(KeyboardModel.isPassword(1 or 0x90)); assertTrue(KeyboardModel.isPassword(1 or 0xe0))
        assertTrue(KeyboardModel.isPassword(2 or 0x10))
        assertFalse(KeyboardModel.isPassword(1)); assertFalse(KeyboardModel.isPassword(1 or 0x10))
    }

    @Test fun doubleSpaceMakesAFullStopOnlyAfterAWord() {
        assertEquals(1 to ". ", KeyboardModel.doubleSpace("hello "))
        assertEquals(1 to ". ", KeyboardModel.doubleSpace("done) "))
        assertNull(KeyboardModel.doubleSpace("hello. "))
        assertNull(KeyboardModel.doubleSpace(" "))
        assertNull(KeyboardModel.doubleSpace("hello"))
    }

    @Test fun accentChoicesFollowTheCaseOfTheKey() {
        assertTrue("é" in KeyboardModel.alternates("e")); assertTrue("É" in KeyboardModel.alternates("E"))
        assertTrue(KeyboardModel.alternates("q").isEmpty())
    }
}
