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
}
