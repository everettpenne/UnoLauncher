package com.jake.duolauncher

import com.jake.duolauncher.keyboard.TextShortcuts
import org.junit.Assert.*
import org.junit.Test

class TextShortcutsTest {
    @Test fun aNewKeyboardStartsWithIosShortcutAndAnEmptyListStaysEmpty() {
        assertEquals("On my way!", TextShortcuts.parse(null)["omw"])
        assertTrue(TextShortcuts.parse("").isEmpty())
    }

    @Test fun shortcutsSurviveBeingStored() {
        val map = linkedMapOf("brb" to "Be right back", "addr" to "12 Example Street, Springfield")
        assertEquals(map, TextShortcuts.parse(TextShortcuts.serialize(map)))
    }

    @Test fun aShortcutMatchesWhateverItsCaseAndCapitalisesThePhraseAtASentenceStart() {
        val map = mapOf("brb" to "be right back")
        assertEquals("be right back", TextShortcuts.expand("brb", map))
        assertEquals("Be right back", TextShortcuts.expand("Brb", map))
        assertEquals("be right back", TextShortcuts.expand("BRB".lowercase(), map))
        assertNull(TextShortcuts.expand("br", map)); assertNull(TextShortcuts.expand("", map))
    }

    @Test fun aShortcutIsOneWordOfLetters() {
        assertEquals("omw", TextShortcuts.clean("  OMW "))
        assertEquals("dont", TextShortcuts.clean("do nt!"))
        assertEquals("i'm", TextShortcuts.clean("I’m"))
        // Broken lines are skipped rather than failing the whole list.
        assertEquals(mapOf("ok" to "Okay"), TextShortcuts.parse("no tab here\n\tno shortcut\nok\tOkay\nempty\t "))
    }
}
