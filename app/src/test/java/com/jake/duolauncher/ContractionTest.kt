package com.jake.duolauncher

import com.jake.duolauncher.keyboard.Suggest
import com.jake.duolauncher.keyboard.WordEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** Exhaustive coverage of the contraction table: the bare letters are corrected to the apostrophized form (and never the
 * other way round), real words that share the bare spelling are offered as completions rather than stolen, the apostrophe is
 * part of the word either way, and casing is preserved.
 */
class ContractionTest {
    companion object {
        internal lateinit var engine: WordEngine
        @JvmStatic @BeforeClass fun load() {
            val dir = listOf("src/main/assets/keyboard", "app/src/main/assets/keyboard").map(::File).first { it.isDirectory }
            engine = WordEngine(WordEngine.parseWords(File(dir, "en_words.txt").useLines { it.toList().asSequence() }),
                WordEngine.parseFollowers(File(dir, "en_next.txt").useLines { it.toList().asSequence() }))
        }
        private fun fix(typed: String) = Suggest.autocorrect(engine, Suggest.parse(typed), emptySet())
        private fun fixAfter(before: String) = Suggest.autocorrect(engine, Suggest.parse(before), emptySet())

        /** Bare forms that are not real words (or are rare enough that the contraction is what was meant): auto-corrected. */
        private val autoCorrected = mapOf(
            "dont" to "don't", "doesnt" to "doesn't", "didnt" to "didn't", "cant" to "can't", "wont" to "won't",
            "isnt" to "isn't", "arent" to "aren't", "wasnt" to "wasn't", "werent" to "weren't", "couldnt" to "couldn't",
            "wouldnt" to "wouldn't", "shouldnt" to "shouldn't", "hasnt" to "hasn't", "havent" to "haven't", "hadnt" to "hadn't",
            "mustnt" to "mustn't", "neednt" to "needn't", "shant" to "shan't", "aint" to "ain't", "mightnt" to "mightn't",
            "oughtnt" to "oughtn't", "darent" to "daren't",
            "im" to "I'm", "ive" to "I've",
            "youre" to "you're", "youve" to "you've", "youll" to "you'll", "youd" to "you'd",
            "hes" to "he's", "shes" to "she's", "hed" to "he'd",
            "weve" to "we've",
            "theyre" to "they're", "theyve" to "they've", "theyd" to "they'd", "theyll" to "they'll",
            "itll" to "it'll", "itd" to "it'd",
            "thats" to "that's", "thatll" to "that'll", "thatd" to "that'd",
            "whats" to "what's", "whatre" to "what're", "whatll" to "what'll", "whatd" to "what'd",
            "whos" to "who's", "wholl" to "who'll", "whove" to "who've", "whod" to "who'd",
            "hows" to "how's", "howre" to "how're",
            "theres" to "there's", "heres" to "here's", "wheres" to "where's", "therell" to "there'll",
            "wouldve" to "would've", "couldve" to "could've", "shouldve" to "should've", "mightve" to "might've", "mustve" to "must've",
            "yall" to "y'all", "oclock" to "o'clock", "maam" to "ma'am",
            "whyre" to "why're",
            "i" to "I")

        /** Bare forms that are real, common words: never auto-replaced, but the contraction is offered as a completion. */
        private val completionOnly = mapOf(
            "id" to "I'd", "its" to "it's", "ill" to "I'll", "were" to "we're", "well" to "we'll",
            "lets" to "let's", "wed" to "we'd", "shed" to "she'd")
    }

    @Test fun everyBareContractionIsCorrectedToItsApostrophe() {
        autoCorrected.forEach { (bare, form) -> assertEquals("$bare -> $form", form, fix(bare)) }
    }

    @Test fun aRealWordThatSharesTheBareSpellingIsNeverStolen() {
        completionOnly.forEach { (bare, _) -> assertNull("$bare must stay as typed", fix(bare)) }
    }

    @Test fun aRealWordIsOfferedTheContractionAsACompletion() {
        completionOnly.forEach { (bare, form) ->
            assertTrue("typing $bare should offer $form, got ${engine.completions(bare, 4)}", form in engine.completions(bare, 4))
        }
    }

    @Test fun aWordAlreadyWrittenWithItsApostropheIsLeftAlone() {
        (autoCorrected.values + completionOnly.values).forEach { form ->
            assertNull("$form is already correct", fix(form))
            assertTrue("$form must be a known word", engine.contains(form.lowercase()))
        }
    }

    @Test fun aFlickedUpApostropheCompletesToTheContraction() {
        assertEquals(listOf("don't"), engine.completions("don'", 3))
        assertEquals(listOf("doesn't"), engine.completions("doesn'", 3))
        assertTrue("I'm should complete \"i'\"", "I'm" in engine.completions("i'", 4))
        assertTrue("we're should complete \"we'\"", "we're" in engine.completions("we'", 4))
        assertTrue("would've should complete \"would'\"", "would've" in engine.completions("would'", 4))
    }

    @Test fun theBareLettersCompleteToTheContraction() {
        assertEquals(listOf("doesn't"), engine.completions("doesnt", 3))
        assertEquals(listOf("don't"), engine.completions("dont", 3))
        assertTrue("we're should be offered for \"were\"", "we're" in engine.completions("were", 4))
        assertTrue("let's should be offered for \"lets\"", "let's" in engine.completions("lets", 4))
        assertTrue("it's should be offered for \"its\"", "it's" in engine.completions("its", 4))
    }

    @Test fun casingIsPreservedAndSentenceCapsApply() {
        assertEquals("Don't", fix("Dont")); assertEquals("DON'T", fix("DONT"))
        assertEquals("I'm", fix("Im")); assertEquals("I'M", fix("IM"))
        assertEquals("Won't", fix("Wont")); assertEquals("O'clock", fix("Oclock"))
        assertEquals("Don't", Suggest.autocorrect(engine, Suggest.parse("dont"), emptySet(), capitalise = true))
        assertEquals("Don't", Suggest.autocorrect(engine, Suggest.parse("Hello. dont"), emptySet(), capitalise = true))
    }

    @Test fun aCapitalisedBareContractionMidSentenceIsStillCorrected() {
        // Before the fix, a capitalised bare form was mistaken for a name and left alone.
        assertEquals("Don't", fixAfter("I Dont"))
        assertEquals("Didn't", fixAfter("she Didnt"))
    }

    @Test fun theAcronymIDIsNeverRewrittenIntoID() {
        assertNull(fix("ID")); assertNull(fixAfter("my ID"))
    }

    @Test fun theApostropheIsPartOfTheWordEitherWay() {
        assertEquals("doesn't", Suggest.parse("doesn't").current)
        assertEquals("doesn't", Suggest.parse("doesn’t").current)
        assertEquals("don't", Suggest.parse("I don't").current)
        assertEquals("I'm", Suggest.parse("I’m").current)
        assertTrue(Suggest.parse("doesn't").plainWord)
        // A curly apostrophe is normalised before matching, so it is left alone just like the straight one.
        assertNull(fix("doesn’t"))
    }

    @Test fun theTableCoversTheWholeCommonSet() {
        val bare = WordEngine.CONTRACTIONS.keys
        val expected = setOf(
            "dont", "doesnt", "didnt", "isnt", "arent", "wasnt", "werent", "wont", "wouldnt", "couldnt", "shouldnt",
            "cant", "havent", "hasnt", "hadnt", "im", "ive", "id", "ill", "youre", "youve", "youll", "youd", "hes",
            "shes", "were", "theyre", "thats", "theres", "heres", "lets", "whos", "hows", "aint", "wouldve", "couldve",
            "shouldve", "mustnt", "neednt", "shant", "oclock", "maam", "yall", "its", "well", "wed", "shed", "hed",
            "itd", "thatd", "whatd", "whod", "whatll", "howre", "mightnt", "oughtnt", "darent")
        expected.forEach { assertTrue("missing contraction $it", it in bare) }
        assertTrue("the table should hold the full common set, has ${bare.size}", bare.size >= 60)
    }
}
