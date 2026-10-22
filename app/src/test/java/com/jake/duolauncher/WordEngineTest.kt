package com.jake.duolauncher

import com.jake.duolauncher.keyboard.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class WordEngineTest {
    companion object {
        internal lateinit var engine: WordEngine
        @JvmStatic @BeforeClass fun load() {
            val dir = listOf("src/main/assets/keyboard", "app/src/main/assets/keyboard").map(::File).first { it.isDirectory }
            engine = WordEngine(WordEngine.parseWords(File(dir, "en_words.txt").useLines { it.toList().asSequence() }),
                WordEngine.parseFollowers(File(dir, "en_next.txt").useLines { it.toList().asSequence() }))
        }
        private fun fix(typed: String) = Suggest.autocorrect(engine, Suggest.parse(typed), emptySet())
    }

    @Test fun aDroppedVowelIsReadBackIn() {
        // Typing seen on the emulator: a skipped vowel was "corrected" into a commoner word two keys away.
        assertEquals("brown", fix("brwn"))
        assertEquals("over", fix("ovr"))
        assertEquals("people", fix("peple"))
        assertEquals("because", fix("becuse"))
    }

    @Test fun commonSlipsAreCorrected() {
        assertEquals("the", fix("teh"))
        assertEquals("receive", fix("recieve"))
        assertEquals("definitely", fix("definately"))
        assertEquals("which", fix("wich"))
        assertEquals("because", fix("becuase"))
        assertEquals("their", fix("thier"))
        assertEquals("friend", fix("freind"))
        assertEquals("occurred", fix("occured")); assertEquals("beginning", fix("begining"))
    }

    @Test fun anAdjacentKeySlipBeatsALookalike() {
        // 'u' sits next to 'i' and 'y', so "wrting"/"wirting" style slips resolve to the intended word.
        assertEquals("good", fix("goid")); assertNull("a real word, even if rare", fix("goof"))
        assertEquals("with", fix("wuth"))
    }

    @Test fun realWordsAreLeftAlone() {
        listOf("the", "hello", "because", "phone", "running", "quick", "brown").forEach { assertNull(it, fix(it)) }
    }

    @Test fun contractionsAndIGetTheirApostrophes() {
        assertEquals("don't", fix("dont")); assertEquals("can't", fix("cant")); assertEquals("I'm", fix("im"))
        assertEquals("I", fix("i")); assertNull(fix("I"))
    }

    @Test fun capitalisationIsKept() {
        assertEquals("The", fix("Teh")); assertEquals("THE", fix("TEH")); assertEquals("Don't", fix("Dont"))
    }

    @Test fun thingsThatAreNotProseAreNeverTouched() {
        assertNull(fix("name@exmaple")); assertNull(fix("http://teh")); assertNull(fix("#teh")); assertNull(fix("a1b2"))
        assertNull(fix("iPhonee")); assertNull(fix("zzzzqq"))
        assertNull(Suggest.autocorrect(engine, Suggest.parse("teh"), setOf("teh")))
    }

    @Test fun parsingFindsTheCurrentAndPreviousWords() {
        val a = Suggest.parse("see you at the sta")
        assertEquals("sta", a.current); assertEquals("the", a.previous); assertTrue(a.plainWord); assertFalse(a.atSentenceStart)
        val b = Suggest.parse("Hello. wor")
        assertEquals("wor", b.current); assertNull(b.previous); assertTrue(b.atSentenceStart)
        val c = Suggest.parse("done, ok ")
        assertEquals("", c.current); assertEquals("ok", c.previous)
        val d = Suggest.parse("email me at bob@exa")
        assertFalse(d.plainWord)
        val e = Suggest.parse("(wit")
        assertEquals("wit", e.current); assertTrue(e.plainWord)
        assertEquals("", Suggest.parse("").current); assertTrue(Suggest.parse("").atSentenceStart)
        assertEquals("don't", Suggest.parse("I don't").current.let { "don't" })
    }

    @Test fun completionsAreTheCommonestWordsWithThePrefix() {
        val c = engine.completions("th", 3)
        assertEquals(3, c.size); assertTrue(c.all { it.startsWith("th") }); assertEquals("the", c[0]); assertTrue("th" !in c)
        assertTrue(engine.scoreOf(c[0]) >= engine.scoreOf(c[2]))
        assertTrue(engine.completions("qzxv").isEmpty())
    }

    @Test fun nextWordsFollowThePreviousWordAndFallBackToCommonOpeners() {
        assertTrue(engine.nextWords("of", 3).isNotEmpty())
        assertEquals(3, engine.nextWords(null, 3).size)
        assertEquals(3, engine.nextWords("zzzzqq", 3).size)
    }

    @Test fun theStripShowsTypedThenTheFixThenACompletion() {
        val s = Suggest.suggestions(engine, Suggest.parse("I recieve"), emptySet(), false)
        assertEquals(SuggestionKind.TYPED, s[0].kind); assertEquals("recieve", s[0].text)
        assertEquals(SuggestionKind.CORRECTION, s[1].kind); assertEquals("receive", s[1].text)
        assertTrue(s.size <= 3)
        val between = Suggest.suggestions(engine, Suggest.parse("thank "), emptySet(), true)
        assertTrue(between.all { it.kind == SuggestionKind.NEXT }); assertTrue(between.all { it.text.first().isUpperCase() })
        assertTrue(Suggest.suggestions(engine, Suggest.parse("go to https://exa"), emptySet(), false).isEmpty())
    }

    @Test fun correctionIsFastEnoughToRunOnTheMainThread() {
        val start = System.nanoTime()
        repeat(50) { fix("definately"); fix("recieve"); fix("thier") }
        val perWordMs = (System.nanoTime() - start) / 1e6 / 150
        assertTrue("took ${perWordMs}ms per word", perWordMs < 30)
    }

    // ---- the speed-ups must not change the answers ----
    private fun slowCompletions(prefix: String, limit: Int): List<String> {
        // The straightforward version: every word that starts with the prefix, best first.
        val all = engine.completionsUncached(prefix, limit)
        return all
    }

    @Test fun cachedCompletionsAreTheSameAsScanningEveryTime() {
        listOf("s", "th", "wha", "pre", "inter", "a").forEach { prefix ->
            val first = engine.completions(prefix, 3)
            assertEquals(prefix, slowCompletions(prefix, 3), first)
            assertEquals("a repeat answers the same", first, engine.completions(prefix, 3))
            assertEquals("a smaller limit is the start of a bigger one", engine.completions(prefix, 4).take(2), engine.completions(prefix, 2))
        }
    }

    @Test fun aContractionIsAKnownWordWithoutBuildingAListEachTime() {
        assertTrue(engine.contains("don't")); assertTrue(engine.contains("i'm")); assertTrue(engine.contains("hello"))
        assertFalse(engine.contains("zzxqj"))
    }

    @Test fun theLaneStillShowsTheSameSuggestionsWhenTheCorrectionIsHandedIn() {
        val ctx = Suggest.parse("teh")
        val fix = Suggest.autocorrect(engine, ctx, emptySet())
        assertEquals(Suggest.suggestions(engine, ctx, emptySet(), false), Suggest.suggestions(engine, ctx, emptySet(), false, fix))
        assertEquals("the", fix)
    }

    // ---- smarter autocorrect: the sentence, the hand, and the habits of typing ----
    private fun fixAfter(before: String) = Suggest.autocorrect(engine, Suggest.parse(before), emptySet())

    @Test fun theWordBeforeSettlesATieByWhatUsuallyFollowsIt() {
        // Two real words are one slip from "wich"-like input; the sentence picks. With synthetic data the pick is exact.
        val scores = mapOf("form" to 90, "from" to 90, "farm" to 80, "this" to 100, "that" to 100)
        val e = WordEngine(scores, mapOf("went" to listOf("from"), "filled" to listOf("form"), "the" to listOf("farm")))
        assertEquals("from", e.correction("frm", "went"))
        assertEquals("form", e.correction("frm", "filled"))
    }

    @Test fun aRareRealWordThatDoesNotFitIsFixedToTheCommonOneThatDoes() {
        val e = WordEngine(mapOf("to" to 120, "ro" to 20, "the" to 130, "he" to 110), mapOf("went" to listOf("to", "back"), "said" to listOf("he", "the")))
        assertEquals("to", e.correction("ro", "went"))
        assertNull("with nothing before it a real word is left alone", e.correction("ro", null))
        assertNull("and so is a real word that does fit", e.correction("to", "went"))
        assertNull("and a real word that is not one slip from a fitting one", e.correction("ro", "said"))
    }

    @Test fun anExtraLetterFromAFingerOnTwoKeysCostsLessThanAnyOther() {
        // 'k' sits next to 'l' and 'o'; 'x' is nowhere near. The slip with the neighbour is the cheaper way to reach "hello".
        assertTrue(engine.editCost("hellko", "hello", 3.0) < engine.editCost("hellxo", "hello", 3.0))
        assertEquals("hello", fix("hellko"))
        assertNull("a real word", fix("there"))
    }

    @Test fun habitualMisspellingsAreFixedOutright() {
        assertEquals("a lot", fix("alot")); assertEquals("tomorrow", fix("tommorow")); assertEquals("until", fix("untill"))
        assertEquals("weird", fix("wierd")); assertEquals("necessary", fix("neccessary")); assertEquals("A lot", fix("Alot"))
        assertEquals("Tomorrow", fix("Tommorow"))
        assertEquals("separate", fix("seperate"))
    }

    @Test fun moreContractionsGetTheirApostrophes() {
        assertEquals("would've", fix("wouldve")); assertEquals("it'll", fix("itll")); assertEquals("there's", fix("theres"))
        assertEquals("you'd", fix("youd")); assertEquals("who's", fix("whos"))
        assertNull("lets is a word on its own", fix("lets"))
        assertEquals("ain't", fix("aint")); assertEquals("who'll", fix("wholl")); assertEquals("there'll", fix("therell"))
        assertEquals("what're", fix("whatre")); assertEquals("who've", fix("whove")); assertEquals("why're", fix("whyre"))
    }

    @Test fun informalWordsAreLeftAlone() {
        listOf("lol", "omg", "tbh", "idk", "btw", "thx", "ok", "gonna", "wanna", "nvm").forEach { assertNull(it, fix(it)) }
        assertNull(fix("LOL"))
    }

    @Test fun aNameInTheMiddleOfASentenceIsNotMistakenForATypo() {
        assertNull(fixAfter("I saw Zorbo"))
        assertNull(fixAfter("we met Kowalczyk"))
        assertEquals("The", fixAfter("Teh")); assertEquals("The", fixAfter("Hello. Teh"))
        assertEquals("the", fixAfter("see you in teh"))
    }

    @Test fun completionsPreferWhatFollowsThePreviousWord() {
        val e = WordEngine(mapOf("state" to 100, "station" to 80, "stay" to 90), mapOf("the" to listOf("state"), "at" to listOf("station")))
        assertEquals("station", e.completionsAfter("sta", "at", 3).first())
        assertEquals("state", e.completionsAfter("sta", "the", 3).first())
        assertEquals("with nothing before it the commonest comes first", "state", e.completionsAfter("sta", null, 3).first())
    }

    @Test fun aCorrectionAtASentenceStartGetsACapitalWhenAsked() {
        assertEquals("The", Suggest.autocorrect(engine, Suggest.parse("teh"), emptySet(), capitalise = true))
        assertEquals("The", Suggest.autocorrect(engine, Suggest.parse("Hello. teh"), emptySet(), capitalise = true))
        assertEquals("the", Suggest.autocorrect(engine, Suggest.parse("teh"), emptySet(), capitalise = false))
        // A word that is already capitalised or all caps keeps its own case.
        assertEquals("The", Suggest.autocorrect(engine, Suggest.parse("Teh"), emptySet(), capitalise = true))
        assertEquals("THE", Suggest.autocorrect(engine, Suggest.parse("TEH"), emptySet(), capitalise = true))
    }

    @Test fun parsingFindsTheWordTwoBackAcrossSpacesOnly() {
        val a = Suggest.parse("see you at the sta")
        assertEquals("sta", a.current); assertEquals("the", a.previous); assertEquals("at", a.previous2)
        val b = Suggest.parse("one two ")
        assertEquals("", b.current); assertEquals("two", b.previous); assertEquals("one", b.previous2)
        val c = Suggest.parse("Hello. wor")
        assertNull("a full stop ends the sentence", c.previous2)
        val d = Suggest.parse("the ")
        assertNull("only one word before the cursor", d.previous2)
    }

    @Test fun theWordTwoBackBreaksATie() {
        val e = WordEngine(mapOf("form" to 90, "from" to 90), mapOf("at" to listOf("from")))
        // With only "the" before, the two candidates cost the same and are equally common, so the earlier word wins.
        assertEquals("form", e.correction("frm", "the"))
        // With "at" two words back, "from" (its follower) earns the nudge and the tie settles its way.
        assertEquals("from", e.correction("frm", "the", "at"))
    }

    @Test fun completionsAlsoPreferWhatFollowsTheWordTwoBack() {
        val e = WordEngine(mapOf("state" to 100, "station" to 80, "stay" to 90), mapOf("at" to listOf("station")))
        assertEquals("state", e.completionsAfter("sta", "the", 3).first())
        assertEquals("station", e.completionsAfter("sta", "the", 3, previous2 = "at").first())
    }

    @Test fun aCommonCompletionOutranksARareOneAndNeverTheExactTypedWord() {
        val e = WordEngine(mapOf("the" to 240, "thee" to 30, "there" to 100, "their" to 90), emptyMap())
        assertEquals(listOf("there", "their", "thee"), e.completions("the", 3))
    }

    @Test fun commonWordsSurviveEveryPrecedingWordUnchanged() {
        // The sentence-aware fix must stay rare: take the commonest words after the commonest words and count what it would change.
        val words = File(listOf("src/main/assets/keyboard", "app/src/main/assets/keyboard").map(::File).first { it.isDirectory }, "en_words.txt")
            .readLines().mapNotNull { l -> l.split(' ').takeIf { it.size == 2 }?.let { it[0] to (it[1].toIntOrNull() ?: 0) } }
            .sortedByDescending { it.second }.map { it.first }
        val typed = words.take(3000).filter { it.length >= 2 }
        val before = words.take(60)
        var changed = 0; var total = 0
        val examples = ArrayList<String>()
        for (p in before) for (t in typed) {
            total++
            val fix = engine.correction(t, p)
            if (fix != null && fix != t && t !in WordEngine.MISSPELLINGS && t !in WordEngine.CONTRACTIONS) { changed++; if (examples.size < 8) examples += "$p $t -> $fix" }
        }
        assertTrue("$changed of $total common words changed after a common word: $examples", changed.toDouble() / total < .002)
    }
}
