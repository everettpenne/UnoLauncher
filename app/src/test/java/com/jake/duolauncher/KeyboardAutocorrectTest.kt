package com.jake.duolauncher

import com.jake.duolauncher.keyboard.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** The smarter autocorrect: capitals that belong, emphasis left alone, runners-up in the strip, and corrections that learn from the
 * person - the slips they fix by hand and the corrections they undo.
 */
class KeyboardAutocorrectTest {
    companion object {
        private lateinit var english: WordEngine
        @JvmStatic @BeforeClass fun load() {
            val dir = listOf("src/main/assets/keyboard", "app/src/main/assets/keyboard").map(::File).first { it.isDirectory }
            english = WordEngine(WordEngine.parseWords(File(dir, "en_words.txt").useLines { it.toList().asSequence() }),
                WordEngine.parseFollowers(File(dir, "en_next.txt").useLines { it.toList().asSequence() }))
        }
        private fun fix(text: String, e: WordEngine = english) = Suggest.autocorrect(e, Suggest.parse(text), emptySet())
        private fun <T> withModel(model: PersonalModel, block: () -> T): T {
            english.personal = model
            try { return block() } finally { english.personal = null }
        }
    }

    @Test fun namesOfDaysLanguagesAndPlacesGetTheirCapital() {
        assertEquals("Monday", fix("see you monday"))
        assertEquals("English", fix("I speak english"))
        assertEquals("a slip in one is fixed and capitalised", "Monday", fix("see you mondya"))
        assertEquals("FRIDAY", fix("FRIDYA"))
        assertNull("already capitalised", fix("see you Monday"))
        assertNull("a month that is also a word stays as typed", fix("you may"))
        assertNull(fix("in march"))
    }

    @Test fun completionsAndPredictionsShowTheCapitalToo() {
        val s = Suggest.suggestions(english, Suggest.parse("see you wedn"), emptySet(), capitalise = false)
        assertTrue(s.toString(), s.any { it.text == "Wednesday" })
    }

    @Test fun contractionsOfIGetTheirCapital() {
        assertEquals("I'm", fix("i'm"))
        assertEquals("I'll", fix("so i'll"))
        assertNull("other contractions are right as typed", fix("don't"))
        assertNull(fix("I'm"))
    }

    @Test fun aLetterHeldForEmphasisIsLeftAlone() {
        assertNull(fix("sooo"))
        assertNull(fix("yesss"))
        assertNull(fix("nooooo"))
        assertNull(fix("goood"))
        assertEquals("three of a consonant mid-word is still a slip", "hello", fix("helllo"))
        assertEquals("the", fix("teh"))
    }

    @Test fun theRunnerUpIsOfferedInTheStrip() {
        val e = WordEngine(mapOf("bake" to 100, "bike" to 95), emptyMap())
        val ctx = Suggest.parse("bske")
        val readings = Suggest.corrections(e, ctx, emptySet())
        assertEquals(listOf("bake", "bike"), readings)
        val strip = Suggest.suggestions(e, ctx, emptySet(), capitalise = false, fix = readings.first(), alternatives = readings.drop(1))
        assertEquals(listOf("bske", "bake", "bike"), strip.map { it.text })
        assertEquals(SuggestionKind.CORRECTION, strip[1].kind)
    }

    @Test fun theWordsYouUseSettleACloseCall() {
        val model = PersonalModel()
        val e = WordEngine(mapOf("bake" to 100, "bike" to 100), emptyMap()).also { it.personal = model }
        repeat(6) { model.learnWord("bike", it, inDictionary = true) }
        assertEquals("bike", e.correction("bxke"))
        assertEquals("but never over a much closer spelling", "bake", e.correction("bske"))
    }

    @Test fun aSlipFixedByHandTwiceIsFixedFromThenOn() {
        val model = PersonalModel()
        assertEquals("this", withModel(model) { fix("thsi") })
        model.learnFix("thsi", "thus")
        assertEquals("once is not a habit", "this", withModel(model) { fix("thsi") })
        model.learnFix("thsi", "thus")
        assertEquals("thus", withModel(model) { fix("thsi") })
        model.learnFix("hel", "hello")
        assertNull("finishing a word is not fixing a slip", model.fixFor("hel"))
    }

    @Test fun anUndoneCorrectionIsNeverMadeAgain() {
        val model = PersonalModel()
        model.refuse("teh", "the")
        val again = withModel(model) { fix("teh") }
        assertNotEquals("the", again)
        model.learnFix("teh", "the"); model.learnFix("teh", "the")
        assertEquals("picking it yourself takes the refusal back", "the", withModel(model) { fix("teh") })
    }

    @Test fun fixesAndRefusalsAreKeptAndForgotten() {
        val model = PersonalModel().apply {
            learnFix("thsi", "thus"); learnFix("thsi", "thus"); refuse("teh", "the")
            learnSequence(null, "love", "pancakes"); learnSequence(null, "love", "pancakes")
        }
        val copy = PersonalModel().apply { decode(model.encode()) }
        assertEquals("thus", copy.fixFor("thsi"))
        assertTrue(copy.refuses("teh", "the"))
        assertEquals(listOf("pancakes"), copy.next(null, "love"))
        copy.forgetSequence("love", "pancakes")
        assertTrue("a prediction can be forgotten on its own", copy.next(null, "love").isEmpty())
        copy.forget("thus")
        assertNull(copy.fixFor("thsi"))
        copy.clear()
        assertTrue(copy.isEmpty())
    }
}
