package com.jake.duolauncher.keyboard

import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.hypot

/** Where touches go on the keys: every point of the key area belongs to the nearest key, so a thumb that lands between two keys or
 * between two rows still types, and what two overlapping fingers type comes out in the order they landed.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardTouchInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    /** Records what the keys ask for, each as the tag of the key that asks it. With [live] it also does what the service does to
     * shift and the page, so the keys change as they would in use.
     */
    private class Recorder(val ui: KeyboardUiState, val live: Boolean = false) : KeyboardActions {
        val log = mutableListOf<String>()
        var aim: (String) -> String = { it }
        override fun type(text: String, at: Pt?) { log += "key-$text"; if (live && !ui.shiftHeld) ui.shift = KeyboardModel.afterLetter(ui.shift) }
        override fun aim(label: String, at: Pt): String = aim(label)
        override fun replaceLast(text: String) { log += "replace:$text" }
        override fun backspace(held: Int) { log += "key-backspace" }
        override fun space() { log += "key-space" }
        override fun enter() { log += "key-enter" }
        override fun globe() { log += "key-globe" }
        var lastShiftRelease = 0L
        var now = 100_000L
        override fun shift() { log += "key-shift"; if (live) ui.shift = KeyboardModel.tapShift(ui.shift, doubleTap = KeyboardModel.isShiftDoubleTap(lastShiftRelease, now)); lastShiftRelease = now }
        override fun shiftReleased() { lastShiftRelease = now }
        override fun swipeStart() { log += "swipe-start" }
        override fun swipeEnd(path: List<Pt>) { log += "swipe-end:${path.size > 4}" }
        override fun page(target: KeyPage) { log += if (target == KeyPage.EMOJI) "key-emoji" else "key-page"; if (live) ui.page = target }
        override fun haptic(kind: HapticKind) = Unit
        override fun pick(suggestion: Suggestion) { log += "pick" }
        override fun moveCursor(delta: Int) { log += "cursor:$delta" }
        override fun emoji(text: String) { log += "emoji:$text" }
        override fun paste() { log += "paste" }
        override fun setOneHanded(mode: Int) { log += "oneHanded:$mode" }
    }

    private fun keys(): List<Pair<String, Rect>> = compose.onAllNodes(SemanticsMatcher("key") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("key-") == true &&
            node.config.getOrNull(SemanticsProperties.TestTag) != "key-preview" }).fetchSemanticsNodes()
        .map { it.config[SemanticsProperties.TestTag] to it.boundsInRoot }

    private fun distance(p: Offset, r: Rect): Float =
        hypot(maxOf(r.left - p.x, 0f, p.x - r.right), maxOf(r.top - p.y, 0f, p.y - r.bottom))

    /** Whether [p] is level with the key [tag] and that key is the nearest of its row: the keys at the ends of a row take everything
     * out to the panel's edge, level with them, though a key of the row above may be a little nearer as the crow flies.
     */
    private fun inRowOf(p: Offset, tag: String, keys: List<Pair<String, Rect>>): Boolean {
        val mine = keys.first { it.first == tag }.second
        val pad = 7.5f * compose.density.density                 // half the gap between rows, and a pixel or two of rounding
        if (p.y < mine.top - pad || p.y > mine.bottom + pad) return false
        val row = keys.filter { kotlin.math.abs(it.second.top - mine.top) < 2f }
        return row.minByOrNull { maxOf(it.second.left - p.x, 0f, p.x - it.second.right) }?.first == tag
    }

    /** Taps a grid of points over the keys and returns how many typed nothing, typed twice, or typed a key other than the nearest. */
    private fun sweep(ui: KeyboardUiState, stepDp: Float = 6f): Triple<Int, List<String>, Int> {
        val actions = Recorder(ui)
        compose.setContent { KeyboardScreen(ui, actions) }
        compose.waitForIdle()
        val keys = keys()
        assertTrue("keys were composed", keys.size > 25)
        val area = keys.map { it.second }.reduce { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }
        val step = stepDp * compose.density.density
        val wrong = ArrayList<String>()
        var points = 0; var dead = 0
        var y = area.top + 1f
        while (y < area.bottom) {
            var x = area.left + 1f
            while (x < area.right) {
                val p = Offset(x, y)
                val ranked = keys.map { it.first to distance(p, it.second) }.sortedBy { it.second }
                // A point as near to two keys as makes no difference may go to either.
                if (ranked[1].second - ranked[0].second > 2f) {
                    points++
                    actions.log.clear()
                    compose.onRoot().performTouchInput { down(p); up() }
                    compose.waitForIdle()
                    // Beside a row that is inset (QWERTY's middle row) the key above can be a shade nearer than the key whose row
                    // the point is in; either is right there, so a key within a few dp of the nearest counts as the nearest.
                    val typed = actions.log.singleOrNull()?.let { tag -> ranked.firstOrNull { it.first == tag } }
                    when {
                        actions.log.isEmpty() -> { dead++; if (wrong.size < 12) wrong += "nothing at (${x.toInt()}, ${y.toInt()}), nearest ${ranked[0].first}" }
                        typed == null || (typed.second > ranked[0].second + 8f * compose.density.density && !inRowOf(p, typed.first, keys)) ->
                            if (wrong.size < 12) wrong += "${actions.log} at (${x.toInt()}, ${y.toInt()}), nearest ${ranked[0].first}"
                    }
                    // Whatever the tap did to the page or to shift is put back, so every point is tried on the same keys.
                    ui.page = KeyPage.LETTERS; ui.shift = ShiftState.OFF
                    compose.waitForIdle()
                }
                x += step
            }
            y += step
        }
        return Triple(points, wrong, dead)
    }

    @Test fun everyPointOfTheKeysBelongsToTheNearestKey() {
        val (points, wrong, dead) = sweep(KeyboardUiState().apply { keyPreviews = false; swipe = false })
        assertTrue("swept the keys ($points points)", points > 1000)
        assertEquals("$dead of $points points typed nothing; first misses: $wrong", emptyList<String>(), wrong)
    }

    @Test fun everyPointBelongsToTheNearestKeyWithANumberRowAndOneHanded() {
        val (points, wrong, dead) = sweep(KeyboardUiState().apply { keyPreviews = false; swipe = false; numberRow = true; oneHanded = 2 }, stepDp = 7f)
        assertTrue("swept the keys ($points points)", points > 500)
        assertEquals("$dead of $points points typed nothing; first misses: $wrong", emptyList<String>(), wrong)
    }

    private fun show(ui: KeyboardUiState, live: Boolean = true): Recorder {
        val actions = Recorder(ui, live)
        compose.setContent { KeyboardScreen(ui, actions) }
        compose.waitForIdle()
        return actions
    }

    private fun centreOf(tag: String): Offset = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center
    private fun touch(block: androidx.compose.ui.test.TouchInjectionScope.() -> Unit) { compose.onRoot().performTouchInput(block); compose.waitForIdle() }
    private fun count(tag: String) = compose.onAllNodes(SemanticsMatcher(tag) { it.config.getOrNull(SemanticsProperties.TestTag) == tag }).fetchSemanticsNodes().size

    @Test fun aLetterThatLandsWhileTheSpaceBarIsStillDownComesAfterTheSpace() {
        val actions = show(KeyboardUiState())
        val space = centreOf("key-space"); val w = centreOf("key-w")
        touch { down(0, space) }
        assertEquals("the space waits for its release", emptyList<String>(), actions.log)
        touch { down(1, w) }
        assertEquals(listOf("key-space", "key-w"), actions.log)
        touch { up(0) }; touch { up(1) }
        assertEquals("letting the space bar go types nothing more", listOf("key-space", "key-w"), actions.log)
    }

    @Test fun returnAndASuggestionAlsoComeAfterASpaceStillHeld() {
        val ui = KeyboardUiState().apply { showStrip = true; suggestions = listOf(Suggestion("pancakes", SuggestionKind.NEXT)) }
        val actions = show(ui)
        val space = centreOf("key-space")
        touch { down(0, space) }; touch { down(1, centreOf("key-enter")) }; touch { up(1) }; touch { up(0) }
        assertEquals(listOf("key-space", "key-enter"), actions.log)
        actions.log.clear()
        touch { down(0, space) }; touch { down(1, centreOf("suggestion-0")) }; touch { up(1) }; touch { up(0) }
        assertEquals(listOf("key-space", "pick"), actions.log)
    }

    @Test fun slidingAlongTheSpaceBarMovesTheCursorAndTypesNoSpace() {
        val actions = show(KeyboardUiState())
        val space = centreOf("key-space")
        touch { down(0, space); moveBy(0, Offset(160f, 0f)); moveBy(0, Offset(80f, 0f)); up(0) }
        assertTrue("moved the cursor: ${actions.log}", actions.log.isNotEmpty() && actions.log.all { it.startsWith("cursor:") })
    }

    @Test fun twoFingersDownLeaveNoKeyBlankAndTheCardFollowsTheLatest() {
        // With a number row Q and W have nothing to offer when held, so holding them keeps the card up (a key with alternatives swaps
        // its card for the strip after a moment, and the test's clock runs on while it waits).
        val ui = KeyboardUiState().apply { numberRow = true }
        assertTrue(KeyboardModel.alternates("q").isEmpty() && KeyboardModel.alternates("w").isEmpty())
        val actions = show(ui)
        touch { down(0, centreOf("key-q")) }
        assertEquals("q", ui.preview?.label)
        touch { down(1, centreOf("key-w")) }
        assertEquals(listOf("key-q", "key-w"), actions.log)
        assertEquals("the card is the latest key's", "w", ui.preview?.label)
        assertEquals(1, count("key-preview-head"))
        // The first finger lifting does not take the second key's card with it.
        touch { up(0) }
        assertEquals("w", ui.preview?.label)
        touch { up(1) }
        compose.mainClock.advanceTimeBy(200); compose.waitForIdle()
        assertNull("the card goes once the finger has lifted", ui.preview)
    }

    /** Presses every character key in turn and checks its card: over that key, touching its top, inside the keyboard, with its label. */
    private fun checkCards(ui: KeyboardUiState) {
        show(ui)
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val keys = keys().filter { (tag, _) -> tag.removePrefix("key-").length == 1 }
        assertTrue("character keys: ${keys.size}", keys.size >= 20)
        for ((tag, face) in keys) {
            touch { down(0, face.center) }
            val p = ui.preview
            assertNotNull("$tag has a card", p)
            assertEquals(tag.removePrefix("key-"), p!!.label)
            val head = compose.onNodeWithTag("key-preview-head").fetchSemanticsNode().boundsInRoot
            assertEquals("$tag: the card sits on its key", face.top, head.bottom, 2f)
            assertTrue("$tag: the card covers its key (key $face, card $head)", head.left <= face.left + 8f && head.right >= face.right - 8f)
            assertTrue("$tag: inside the keyboard ($head in $root)", head.left >= root.left && head.right <= root.right && head.top >= root.top)
            val centred = face.center.x - head.width / 2f
            if (centred > root.left + 8f && centred + head.width < root.right - 8f) assertEquals("$tag: centred on its key", face.center.x, head.center.x, 2f)
            touch { up(0) }
            compose.mainClock.advanceTimeBy(200); compose.waitForIdle()
            assertNull("$tag: its card is gone after the tap", ui.preview)
        }
    }

    @Test fun everyLetterKeysCardIsOverItsKey() = checkCards(KeyboardUiState())
    @Test fun everyCardIsOverItsKeyWithANumberRowOneHandedInTheClassicStyle() = checkCards(KeyboardUiState().apply { numberRow = true; oneHanded = 1; classic = true })
    @Test fun everyCardIsOverItsKeyOnTheNumbersAndSymbolsPages() {
        checkCards(KeyboardUiState().apply { page = KeyPage.NUMBERS })
    }
    @Test fun everyCardIsOverItsKeyOnTheSymbolsPageAndInAnEmailField() = checkCards(KeyboardUiState().apply { page = KeyPage.SYMBOLS; field = FieldKind.EMAIL; heightScale = 1.15f })
    @Test fun everyCardIsOverItsKeyOnAnotherLayoutWithShiftOn() =
        checkCards(KeyboardUiState().apply { layout = KeyLayouts.byId("qwertz")!!; shift = ShiftState.LOCKED })

    @Test fun aSymbolLeavesTheSymbolsPageUp() {
        val ui = KeyboardUiState()
        val actions = show(ui)
        touch { down(0, centreOf("key-page")) }
        assertEquals("the page key acts as it is touched", KeyPage.NUMBERS, ui.page)
        touch { up(0) }
        assertEquals(listOf("key-page"), actions.log)
        touch { down(0, centreOf("key-@")); up(0) }
        touch { down(0, centreOf("key-?")); up(0) }
        assertEquals(listOf("key-page", "key-@", "key-?"), actions.log)
        assertEquals("still on the numbers after two symbols", KeyPage.NUMBERS, ui.page)
        compose.mainClock.advanceTimeBy(200); compose.waitForIdle()
        assertNull(ui.preview)
    }

    @Test fun aKeyHeldWhenThePageChangesTakesItsCardWithIt() {
        val ui = KeyboardUiState()
        show(ui)
        touch { down(0, centreOf("key-q")) }
        assertEquals("q", ui.preview?.label)
        touch { down(1, centreOf("key-page")) }
        assertEquals(KeyPage.NUMBERS, ui.page)
        assertNull("no card is left over a key that is no longer there", ui.preview)
        touch { up(0) }; touch { up(1) }
        assertNull(ui.preview); assertNull(ui.proxy)
    }

    @Test fun shiftHeldTypesCapitalsAndEndsWhenItIsLetGo() {
        val ui = KeyboardUiState()
        val actions = show(ui)
        touch { down(0, centreOf("key-shift")) }
        assertEquals("shift acts as it is touched", ShiftState.ONCE, ui.shift)
        touch { down(1, centreOf("key-A")); up(1) }
        touch { down(1, centreOf("key-B")); up(1) }
        assertEquals("still on while it is held", ShiftState.ONCE, ui.shift)
        touch { up(0) }
        assertEquals(listOf("key-shift", "key-A", "key-B"), actions.log)
        assertEquals(ShiftState.OFF, ui.shift)
        // A tap on its own is one capital, as before (some while later: straight after another tap it would be a double tap).
        actions.log.clear(); actions.now += 10_000
        touch { down(0, centreOf("key-shift")); up(0) }
        assertEquals(ShiftState.ONCE, ui.shift)
        touch { down(0, centreOf("key-C")); up(0) }
        assertEquals(ShiftState.OFF, ui.shift)
        // And a tap on a shift that is already on (a new sentence) turns it off.
        ui.shift = ShiftState.ONCE; compose.waitForIdle(); actions.now += 10_000
        touch { down(0, centreOf("key-shift")); up(0) }
        assertEquals(ShiftState.OFF, ui.shift)
    }

    @Test fun aTapReadAsTheKeyBesideItTypesAndShowsThatKey() {
        val ui = KeyboardUiState()
        val actions = show(ui)
        actions.aim = { if (it == "w") "e" else it }
        val e = compose.onNodeWithTag("key-e").fetchSemanticsNode().boundsInRoot
        touch { down(0, centreOf("key-w")) }
        assertEquals(listOf("key-e"), actions.log)
        assertEquals("e", ui.preview?.label)
        val head = compose.onNodeWithTag("key-preview-head").fetchSemanticsNode().boundsInRoot
        assertEquals("the card is over the key that was typed", e.center.x, head.center.x, 2f)
        touch { up(0) }
        // With the cards off, that key is the one shown pressed.
        ui.keyPreviews = false; compose.waitForIdle()
        touch { down(0, centreOf("key-w")) }
        assertEquals(ui.faces['e'], ui.proxy); assertNull(ui.preview)
        touch { up(0) }
        assertNull(ui.proxy)
    }

    @Test fun passwordsAndCardsTurnedOffShowNoCard() {
        val ui = KeyboardUiState().apply { password = true }
        val actions = show(ui)
        touch { down(0, centreOf("key-q")) }
        assertEquals(listOf("key-q"), actions.log); assertNull(ui.preview); assertEquals(0, count("key-preview-head"))
        touch { up(0) }
    }

    @Test fun aFlickOrAHoldDoesNotAmendAnotherFingersLetter() {
        val ui = KeyboardUiState()
        val actions = show(ui)
        val q = centreOf("key-q")
        // The first finger rests on Q; a second types W; the first then flicks down. Alone that flick would turn Q into a comma.
        touch { down(0, q) }; touch { down(1, centreOf("key-w")); up(1) }
        touch { moveBy(0, Offset(0f, 40f * density)); up(0) }
        assertEquals("nothing was replaced: ${actions.log}", listOf("key-q", "key-w"), actions.log)
        // Alone, the flick still works.
        actions.log.clear()
        touch { down(0, q); moveBy(0, Offset(0f, 40f * density)); up(0) }
        assertEquals(listOf("key-q", "replace:,"), actions.log)
    }

    @Test fun aReturnThatSendsIsCalledOffByTheNextKeyAndAPlainOneIsTypedInOrder() {
        val ui = KeyboardUiState().apply { enter = EnterKind.SEND }
        val actions = show(ui)
        touch { down(0, centreOf("key-enter")) }; touch { down(1, centreOf("key-x")) }; touch { up(0) }; touch { up(1) }
        assertEquals("the message was not sent by the next key landing", listOf("key-x"), actions.log)
        actions.log.clear()
        touch { down(0, centreOf("key-enter")); up(0) }
        assertEquals(listOf("key-enter"), actions.log)
        actions.log.clear()
        ui.enter = EnterKind.RETURN; compose.waitForIdle()
        touch { down(0, centreOf("key-enter")) }; touch { down(1, centreOf("key-x")) }; touch { up(0) }; touch { up(1) }
        assertEquals(listOf("key-enter", "key-x"), actions.log)
    }

    @Test fun twoQuickTapsOnShiftLockCapsAndTwoSlowOnesDoNot() {
        val ui = KeyboardUiState()
        val actions = show(ui)
        val shift = centreOf("key-shift")
        // Each tap held 200 ms, let go 150 ms apart: under the old count from touch to release this was 550 ms and missed.
        actions.now = 1_000; touch { down(0, shift) }; actions.now = 1_200; touch { up(0) }
        assertEquals(ShiftState.ONCE, ui.shift)
        actions.now = 1_250; touch { down(0, shift) }; actions.now = 1_450; touch { up(0) }
        assertEquals(ShiftState.LOCKED, ui.shift)
        actions.now = 9_000; touch { down(0, shift); up(0) }
        assertEquals(ShiftState.OFF, ui.shift)
        actions.now = 10_000; touch { down(0, shift); up(0) }
        actions.now = 11_000; touch { down(0, shift); up(0) }
        assertEquals("two slow taps are on, then off", ShiftState.OFF, ui.shift)
    }

    @Test fun slidingAcrossTheLettersSwipesAWord() {
        val ui = KeyboardUiState().apply { swipe = true; keyPreviews = true }
        val actions = show(ui)
        val from = centreOf("key-h"); val to = centreOf("key-o")
        touch {
            down(0, from)
            for (i in 1..12) moveTo(0, Offset(from.x + (to.x - from.x) * i / 12f, from.y + (to.y - from.y) * i / 12f))
        }
        assertTrue("the swipe began: ${actions.log}", actions.log.take(2) == listOf("key-h", "swipe-start"))
        assertNull("no card rides along a swipe", ui.preview)
        assertTrue("the trail is drawn", ui.trail.size > 4)
        touch { up(0) }
        assertEquals(listOf("key-h", "swipe-start", "swipe-end:true"), actions.log)
        assertTrue(ui.trail.isEmpty()); assertNull(ui.preview); assertNull(ui.proxy)
    }

    /** Holds E until its alternatives open, slides along them and lets go: the letter typed becomes the one let go on. */
    private fun holdForAlternatives(ui: KeyboardUiState) {
        val actions = show(ui)
        val e = compose.onNodeWithTag("key-e").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        touch { down(0, e.center) }
        compose.mainClock.advanceTimeBy(600); compose.waitForIdle()
        val strip = ui.accents
        assertNotNull("the alternatives opened", strip)
        assertNull("the card made way for them", ui.preview)
        val box = compose.onNodeWithTag("accent-strip").fetchSemanticsNode().boundsInRoot
        assertTrue("the strip is inside the keyboard ($box in $root)", box.left >= root.left && box.right <= root.right)
        assertTrue("and over its key", box.left <= e.center.x && box.right >= e.center.x && box.bottom <= e.top + 2f)
        // Slide to the last one and let go there.
        val last = strip!!.options.lastIndex
        touch { moveTo(0, Offset(box.right - box.width / (2f * strip.options.size), e.center.y)) }
        assertEquals(last, ui.accents?.index)
        touch { up(0) }
        assertEquals(listOf("key-e", "replace:${strip.options[last]}"), actions.log)
        assertNull(ui.accents); assertNull(ui.preview)
    }

    @Test fun holdingAKeyOpensItsAlternativesAndLettingGoOnOneTypesIt() = holdForAlternatives(KeyboardUiState())
    @Test fun theAlternativesAreInPlaceOneHanded() = holdForAlternatives(KeyboardUiState().apply { oneHanded = 2; numberRow = true })

    @Test fun theKeysReportWhereTheyAreAndWhereTheyWereTouchedInOneFrame() {
        val ui = KeyboardUiState()
        var at: Pt? = null
        val actions = object : KeyboardActions by Recorder(ui) {
            override fun type(text: String, at0: Pt?) { at = at0 }
        }
        compose.setContent { androidx.compose.foundation.layout.Column { androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(120.dp)); KeyboardScreen(ui, actions) } }
        compose.waitForIdle()
        // The keys' places are read as a key is touched, so one is touched first.
        touch { down(0, centreOf("key-m")); up(0) }
        val geometry = KeyGeometry(HashMap(ui.keyCentres), ui.keyWidth, ui.keyHeight)
        val w = compose.onNodeWithTag("key-w").fetchSemanticsNode().boundsInRoot
        val a = compose.onNodeWithTag("key-a").fetchSemanticsNode().boundsInRoot
        assertEquals("26 letters know where they are", 26, geometry.centres.size)
        assertEquals(w.width, geometry.keyWidth, 1f); assertEquals(w.height, geometry.keyHeight, 1f)
        val cw = geometry.centres.getValue('w'); val ca = geometry.centres.getValue('a')
        assertEquals("in the keyboard view's own coordinates", w.center.x, cw.x, 1f); assertEquals(w.center.y, cw.y, 1f)
        assertEquals("W and A are as far apart in the keyboard's own reckoning as on screen", a.center.y - w.center.y, ca.y - cw.y, 1f)
        assertEquals(a.center.x - w.center.x, ca.x - cw.x, 1f)
        touch { down(0, w.center); up(0) }
        // The touch is reported in the frame the keys' places are in: a tap in the middle of W is in the middle of W.
        assertEquals("touch $at, W at $cw", cw.x, at!!.x, 2f); assertEquals("touch $at, W at $cw", cw.y, at!!.y, 2f)
        // So a tap in the middle of a key is always that key, whatever the words want.
        assertEquals('w', TouchModel.aim(geometry, at!!, 'w') { if (it == 'w') 0 else 240 })
        touch { down(0, Offset(w.right - 2f, w.center.y)); up(0) }
        assertEquals('e', TouchModel.aim(geometry, at!!, 'w') { if (it == 'e') 240 else 0 })
    }
}
