package com.jake.duolauncher

import com.jake.duolauncher.keyboard.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** The keyboard's newer intelligence: learning, touch-aware autocorrect, split words, swipe typing, emoji, clipboard history, layouts
 * and haptic feels. Uses the real bundled word list, in an engine of its own so the learned words never leak into other tests.
 */
class KeyboardSmartsTest {
    companion object {
        private lateinit var engine: WordEngine
        private lateinit var emoji: EmojiIndex
        private val dir = listOf("src/main/assets/keyboard", "app/src/main/assets/keyboard").map(::File).first { it.isDirectory }
        @JvmStatic @BeforeClass fun load() {
            engine = WordEngine(WordEngine.parseWords(File(dir, "en_words.txt").useLines { it.toList().asSequence() }),
                WordEngine.parseFollowers(File(dir, "en_next.txt").useLines { it.toList().asSequence() }))
            emoji = EmojiIndex.parse(File(dir, "emoji_words.txt").useLines { it.toList().asSequence() })
        }

        /** A QWERTY keyboard 100 px a key and 150 px a row, rows offset as a phone draws them. */
        internal val qwerty: KeyGeometry = run {
            val offsets = listOf(0f, .5f, 1.5f)
            val centres = HashMap<Char, Pt>()
            KeyLayouts.QWERTY.rows.forEachIndexed { r, row -> row.forEachIndexed { c, ch -> centres[ch] = Pt((c + offsets[r]) * 100f + 50f, r * 150f + 75f) } }
            KeyGeometry(centres, 100f, 150f)
        }

        /** A finger's path through [word]'s keys: the key centres, wobbled a little, with points between them. */
        internal fun swipe(word: String, sloppy: Boolean = false): List<Pt> {
            val rnd = java.util.Random(word.hashCode().toLong())
            // A sloppy swipe misses each key by up to a quarter of a key and cuts the corners between them a little, as a quick thumb does.
            val keys = word.map { qwerty.centres.getValue(it) }.let { k -> if (!sloppy) k else k.map { Pt(it.x + (rnd.nextFloat() - .5f) * 50f, it.y + (rnd.nextFloat() - .5f) * 60f) } }
                .let { k -> if (!sloppy || k.size < 3) k else listOf(k.first()) + k.windowed(3).map { (a, b, c) -> Pt(b.x * .84f + (a.x + c.x) * .08f, b.y * .84f + (a.y + c.y) * .08f) } + k.last() }
            val out = ArrayList<Pt>()
            keys.zipWithNext().forEachIndexed { i, (a, b) ->
                for (s in 0 until 6) { val t = s / 6f; out += Pt(a.x + (b.x - a.x) * t + (if (i % 2 == 0) 9f else -7f), a.y + (b.y - a.y) * t + 6f) }
            }
            out += keys.last()
            return out
        }
    }

    // ---- learning ----------------------------------------------------------------------------------------------------------------

    @Test fun aWordTypedTwiceIsLearnedAndThenNeverCorrected() {
        val model = PersonalModel()
        val e = WordEngine(mapOf("zebra" to 120, "zorbla" to 150), emptyMap()).also { it.personal = model }
        assertFalse("one use is not enough", model.learnWord("Zorblax", 1, inDictionary = false))
        assertEquals("before learning, it is corrected away", "zorbla", e.correction("zorblax"))
        assertTrue(model.learnWord("Zorblax", 2, inDictionary = false))
        assertTrue(model.knows("zorblax"))
        assertNull("a learned word is never corrected", e.correction("zorblax"))
        assertEquals("and typed in lower case it gets its capitals back", "Zorblax", Suggest.autocorrect(e, Suggest.parse("I met zorblax"), emptySet()))
        assertTrue("it completes", e.completions("zor").contains("Zorblax"))
    }

    @Test fun anUndoneCorrectionTeachesTheWordAtOnce() {
        val model = PersonalModel()
        model.learnWord("grokked", 1, inDictionary = false, explicit = true)
        assertTrue(model.knows("grokked"))
        assertEquals(listOf("grokked"), model.list())
    }

    @Test fun learnedCapitalsNeverReachADictionaryWord() {
        val model = PersonalModel().apply { learnWord("Will", 1, true); learnWord("Will", 1, true) }
        val e = WordEngine(mapOf("will" to 200), emptyMap()).also { it.personal = model }
        assertNull(Suggest.autocorrect(e, Suggest.parse("I will"), emptySet()))
    }

    @Test fun predictionsLearnWhatYouPutAfterAWord() {
        val model = PersonalModel()
        engine.personal = model
        try {
            assertNotEquals("pancakes", engine.nextWords("love").first())
            model.learnSequence(null, "love", "pancakes"); model.learnSequence(null, "love", "pancakes")
            assertEquals("pancakes", engine.nextWords("love").first())
            model.learnSequence("i", "love", "waffles"); model.learnSequence("i", "love", "waffles")
            assertEquals("the two-word context comes first", "waffles", engine.nextWords("love", previous2 = "I").first())
        } finally { engine.personal = null }
    }

    @Test fun theModelRoundTripsAndForgets() {
        val model = PersonalModel().apply {
            learnWord("Everett", 1, false); learnWord("Everett", 2, false); learnSequence("my", "friend", "Everett"); learnSequence("my", "friend", "Everett")
        }
        val copy = PersonalModel().apply { decode(model.encode()) }
        assertTrue(copy.knows("everett")); assertEquals("Everett", copy.formOf("everett"))
        assertEquals(listOf("Everett"), copy.next("my", "friend"))
        copy.forget("Everett")
        assertFalse(copy.knows("everett")); assertTrue(copy.next("my", "friend").isEmpty())
        assertNull("numbers and codes are never learned", PersonalModel.clean("A1B2"))
    }

    @Test fun learningIsOffInPrivateFields() {
        val text = 0x1; val password = 0x81; val email = 0x21; val number = 0x2
        assertTrue(LearningPolicy.allowed(text, 0, learningOn = true))
        assertFalse(LearningPolicy.allowed(text, 0, learningOn = false))
        assertFalse("incognito", LearningPolicy.allowed(text, LearningPolicy.IME_FLAG_NO_PERSONALIZED_LEARNING, true))
        assertFalse(LearningPolicy.allowed(password, 0, true)); assertFalse(LearningPolicy.allowed(email, 0, true)); assertFalse(LearningPolicy.allowed(number, 0, true))
    }

    // ---- autocorrect ---------------------------------------------------------------------------------------------------------------

    @Test fun whereTheKeyWasTouchedDecidesBetweenTwoEqualCorrections() {
        val e = WordEngine(mapOf("cat" to 100, "vat" to 100), emptyMap())
        val nearV = listOf(Pt(460f, 290f), null, null)
        val nearC = listOf(Pt(340f, 290f), null, null)
        assertEquals("vat", e.correction("fat", touches = nearV, geometry = qwerty))
        assertEquals("cat", e.correction("fat", touches = nearC, geometry = qwerty))
        val edge = TouchModel.substitutionCost(qwerty, Pt(140f, 75f), 'w', 1.3)!!
        val middle = TouchModel.substitutionCost(qwerty, Pt(50f, 75f), 'w', 1.3)!!
        assertTrue("a tap near the next key's edge makes it a cheap reading", edge < middle)
    }

    @Test fun wordsRunTogetherAreSplit() {
        fun fix(t: String) = Suggest.autocorrect(engine, Suggest.parse(t), emptySet())
        assertEquals("of the", fix("ofthe"))
        assertEquals("this is", fix("thisnis"))
        assertNull("a real word is never cut in two", fix("therapist"))
    }

    @Test fun emojiAreSuggestedForWordsThatNameThem() {
        assertEquals(listOf("🍕"), emoji.forWord("pizza"))
        assertTrue("a word shared by many emoji says too little", emoji.forWord("face").isEmpty())
        val list = Suggest.withEmoji(listOf(Suggestion("pizza", SuggestionKind.TYPED), Suggestion("pizzas", SuggestionKind.COMPLETION),
            Suggestion("pizzeria", SuggestionKind.COMPLETION)), Suggest.parse("pizza"), emoji)
        assertEquals(SuggestionKind.EMOJI, list.last().kind); assertEquals(3, list.size)
        assertTrue(emoji.search("thumbs up").contains("👍"))
        assertTrue(emoji.toneable("👍")); assertFalse(emoji.toneable("🍕"))
        assertEquals("👍🏽", EmojiIndex.withTone("👍", EmojiIndex.TONES[2]))
    }

    // ---- swipe typing --------------------------------------------------------------------------------------------------------------

    @Test fun aSwipeThroughAWordsKeysReadsAsThatWord() {
        listOf("the", "hello", "keyboard", "people", "with").forEach { word ->
            val got = GestureDecoder.decode(swipe(word), qwerty, { c -> engine.wordsStartingWith(c) })
            assertEquals("swiping \"$word\" gave $got", word, got.firstOrNull())
        }
    }

    @Test fun swipeTypingReadsMostCommonWordsRight() {
        val words = File(dir, "en_words.txt").readLines().map { it.split(' ') }.filter { it.size == 2 && it[0].length in 3..9 && it[0].all { c -> c in 'a'..'z' } }
            .sortedByDescending { it[1].toInt() }.take(300).map { it[0] }
        val wrong = words.filter { w -> GestureDecoder.decode(swipe(w), qwerty, { c -> engine.wordsStartingWith(c) }).firstOrNull() != w }
        val inTop3 = words.count { w -> w in GestureDecoder.decode(swipe(w), qwerty, { c -> engine.wordsStartingWith(c) }).take(3) }
        println("swipe: ${words.size - wrong.size}/${words.size} first, $inTop3 in the strip; wrong: ${wrong.take(40)}")
        assertTrue("${wrong.size} of ${words.size} wrong: $wrong", wrong.size <= words.size / 10)
        assertTrue(inTop3 >= words.size * 97 / 100)
        val sloppyWrong = words.filter { w -> GestureDecoder.decode(swipe(w, sloppy = true), qwerty, { c -> engine.wordsStartingWith(c) }).firstOrNull() != w }
        val sloppyTop3 = words.count { w -> w in GestureDecoder.decode(swipe(w, sloppy = true), qwerty, { c -> engine.wordsStartingWith(c) }).take(3) }
        println("sloppy: ${words.size - sloppyWrong.size}/${words.size} first, $sloppyTop3 in the strip; wrong: ${sloppyWrong.take(40)}")
        assertTrue("sloppy: ${sloppyWrong.size} wrong", sloppyWrong.size <= words.size / 10)
        assertTrue(sloppyTop3 >= words.size * 95 / 100)
    }

    @Test fun aFlickIsNotASwipe() {
        val h = qwerty.centres.getValue('h')
        assertFalse(GestureDecoder.isSwipe(listOf(h, Pt(h.x, h.y - 30f), Pt(h.x + 5f, h.y - 60f)), qwerty))
        assertTrue(GestureDecoder.isSwipe(swipe("the"), qwerty))
    }

    // ---- clipboard ----------------------------------------------------------------------------------------------------------------

    @Test fun recentClipsFadeAfterAnHourButPinnedOnesStay() {
        var now = 0L
        val h = ClipHistory { now }
        h.add("one"); h.add("two"); h.add("one")
        assertEquals(listOf("one", "two"), h.list().map { it.text })
        assertNotNull(h.fresh())
        h.setPinned("two", true)
        now += ClipHistory.RECENT_FOR_MS + 1
        assertEquals(listOf("two"), h.list().map { it.text })
        assertNull(h.fresh())
        repeat(30) { h.add("clip $it") }
        assertEquals(ClipHistory.MAX_RECENT, h.list().count { !it.pinned })
        val kept = PersonalStore.decodeClips(PersonalStore.encodeClips(listOf(Clip("a\tb\nc", 5L, true))))
        assertEquals(listOf(Clip("a\tb\nc", 5L, true)), kept)
        assertEquals("a b…", ClipHistory.preview("a   b cdefg", 4))
    }

    // ---- layouts and keys ----------------------------------------------------------------------------------------------------------

    @Test fun layoutsGiveTheirOwnKeysAndNeighbours() {
        assertEquals(listOf(KeyLayouts.QWERTY), KeyLayouts.parse("nonsense"))
        val azerty = KeyLayouts.byId("azerty")!!
        val rows = KeyboardModel.rows(KeyPage.LETTERS, ShiftState.OFF, layout = azerty)
        assertEquals("a", (rows[0][0] as Key.Char).label)
        assertTrue(KeyboardModel.rows(KeyPage.LETTERS, ShiftState.OFF, emojiKey = false).last().none { it is Key.Emoji })
        assertEquals("1", KeyLayouts.digitFor(KeyLayouts.QWERTY, 'q')); assertEquals("0", KeyLayouts.digitFor(KeyLayouts.QWERTY, 'p'))
        assertNull(KeyLayouts.digitFor(KeyLayouts.QWERTY, 'a'))
        val n = KeyLayouts.neighbours(KeyLayouts.QWERTY)
        assertTrue(n.getValue('g').containsAll(listOf('f', 'h', 't', 'y', 'v', 'b')))
        assertFalse(n.getValue('q').contains('p'))
        assertTrue(KeyLayouts.ALL.all { it.rows.size == 3 }); assertEquals(KeyLayouts.ALL.size, KeyLayouts.ALL.map { it.id }.toSet().size)
    }

    @Test fun eachFeelKeepsItsCharacter() {
        val soft = HapticProfile.plan(HapticKind.DELETE, .6f, HapticFeel.SOFT)!!
        val crisp = HapticProfile.plan(HapticKind.DELETE, .6f, HapticFeel.CRISP)!!
        val firm = HapticProfile.plan(HapticKind.SPACE, .6f, HapticFeel.FIRM)!!
        assertEquals(HapticProfile.LOW_TICK, soft.primitive); assertTrue(soft.scale < crisp.scale)
        assertEquals(HapticProfile.THUD, firm.secondPrimitive)
        assertTrue("caps lock is a double click", HapticProfile.plan(HapticKind.SHIFT_LOCK, .6f)!!.secondScale > 0f)
        assertTrue("a correction is felt as a double pulse", HapticProfile.plan(HapticKind.AUTOCORRECT, .6f)!!.secondDelayMs > 0)
    }
}
