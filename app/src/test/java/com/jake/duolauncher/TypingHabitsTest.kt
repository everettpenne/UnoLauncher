package com.jake.duolauncher

import com.jake.duolauncher.keyboard.*
import org.junit.Assert.*
import org.junit.Test

/** The keyboard learning how the person types: where their taps land, how much autocorrect they want, layouts per app, emoji per word. */
class TypingHabitsTest {
    /** A QWERTY row of keys 100 px wide and 150 tall, with "c" and "v" side by side on the bottom row. */
    private val geometry = KeyGeometry(buildMap {
        "qwertyuiop".forEachIndexed { i, c -> put(c, Pt(50f + 100f * i, 75f)) }
        "asdfghjkl".forEachIndexed { i, c -> put(c, Pt(100f + 100f * i, 225f)) }
        "zxcvbnm".forEachIndexed { i, c -> put(c, Pt(200f + 100f * i, 375f)) }
    }, 100f, 150f)

    @Test fun keysMoveToWhereTheTapsLand() {
        val h = TypingHabits()
        // This person always hits a third of a key to the right of "c" and "a".
        repeat(TypingHabits.MIN_TAPS - 1) { h.learnTaps("ca", listOf(Pt(433f, 375f), Pt(133f, 225f)), geometry) }
        assertNull("not until it has seen enough", h.offsetOf('c'))
        assertSame(geometry, h.adjust(geometry))
        h.learnTaps("ca", listOf(Pt(433f, 375f), Pt(133f, 225f)), geometry)
        assertEquals(.33f, h.offsetOf('c')!!.first, .01f)
        assertEquals(433f, h.adjust(geometry).centres['c']!!.x, 1f)
        assertEquals("keys it has not learned stay put", 500f, h.adjust(geometry).centres['v']!!.x, .01f)
    }

    @Test fun aTapFarFromItsKeyIsNotLearned() {
        val h = TypingHabits()
        repeat(30) { h.learnTaps("c", listOf(Pt(560f, 375f)), geometry) }
        assertNull(h.offsetOf('c'))
    }

    @Test fun aLearnedHandDecidesAnAmbiguousTap() {
        // A tap exactly between "c" and "v": with no history the dictionary's commoner word wins; for someone whose "v" taps land left of
        // centre, that same tap reads as "v".
        val e = WordEngine(mapOf("cat" to 100, "vat" to 100), emptyMap())
        val between = listOf(Pt(450f, 375f), null, null)
        val h = TypingHabits()
        repeat(TypingHabits.MIN_TAPS) { h.learnTaps("v", listOf(Pt(460f, 375f)), geometry) }
        assertEquals("vat", e.correction("xat", touches = between, geometry = h.adjust(geometry)))
    }

    @Test fun undoingManyCorrectionsMakesAutocorrectMoreCareful() {
        val h = TypingHabits()
        repeat(40) { h.correction(undone = false) }
        assertEquals("kept corrections change nothing", 0.0, h.caution(), 0.0)
        repeat(30) { h.correction(undone = false); if (it % 2 == 0) h.correction(undone = true) }
        assertTrue("half undone: ${h.undoRate()}", h.caution() > .2)
        assertTrue(h.caution() <= TypingHabits.MAX_CAUTION)
        repeat(80) { h.correction(undone = false) }
        assertEquals("and it relaxes again once corrections are kept", 0.0, h.caution(), 0.0)
    }

    @Test fun cautionNarrowsWhatIsCorrected() {
        val e = WordEngine(mapOf("people" to 150), emptyMap())
        assertEquals("people", e.correction("pwopel"))
        e.caution = TypingHabits.MAX_CAUTION
        assertNull("two slips are left for the strip", e.correction("pwopel"))
        assertEquals("one slip is still fixed", "people", e.correction("peoplr"))
    }

    @Test fun layoutsAndEmojiAreRememberedAndKept() {
        val h = TypingHabits().apply {
            rememberLayout("org.thoughtcrime.securesms", "es"); rememberLayout("com.example.mail", "qwerty")
            learnEmoji("pizza", "🍕"); learnEmoji("pizza", "🍕"); learnEmoji("pizza", "😋")
            repeat(TypingHabits.MIN_TAPS) { learnTaps("a", listOf(Pt(110f, 230f)), geometry) }
            repeat(25) { correction(false) }
        }
        val copy = TypingHabits().apply { decode(h.encode()) }
        assertEquals("es", copy.layoutFor("org.thoughtcrime.securesms"))
        assertEquals(listOf("🍕"), copy.emojiFor("Pizza"))
        assertNotNull(copy.offsetOf('a'))
        assertEquals(1, copy.fittedKeys()); assertEquals(2, copy.appsWithLayouts())
        val strip = Suggest.withEmoji(listOf(Suggestion("pizza", SuggestionKind.TYPED)), Suggest.parse("pizza"), null, copy)
        assertEquals("🍕", strip.last().text)
        copy.clear()
        assertTrue(copy.isEmpty())
    }
}
