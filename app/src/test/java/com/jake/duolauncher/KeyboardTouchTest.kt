package com.jake.duolauncher

import com.jake.duolauncher.keyboard.*
import org.junit.Assert.*
import org.junit.Test

/** The plain arithmetic behind the keys' touch: the order of overlapping fingers, where a key's card goes, and which key a tap on the
 * line between two was meant for.
 */
class KeyboardTouchTest {
    // ---- overlapping fingers ----------------------------------------------------------------------------------------------------

    @Test fun aKeyStillHeldActsBeforeTheKeyThatLandsAfterIt() {
        val t = KeyTouches(); val log = ArrayList<String>()
        val space = t.wait { log += "space" }                 // the thumb is on the space bar
        t.landed(); log += "w"; t.acted()                     // the next letter lands before the thumb lifts
        assertEquals(listOf("space", "w"), log)
        assertFalse("the space bar's own release types nothing more", t.release(space))
        assertEquals(listOf("space", "w"), log)
        assertEquals(2, t.count)
    }

    @Test fun aKeyLetGoOnItsOwnActsOnceAndACancelledOneNever() {
        val t = KeyTouches(); val log = ArrayList<String>()
        val a = t.wait { log += "space" }
        assertTrue(t.isWaiting(a)); assertTrue(t.release(a)); assertFalse(t.release(a))
        assertEquals(listOf("space"), log)
        val b = t.wait { log += "enter" }
        t.cancel(b); t.landed(); assertFalse(t.release(b))
        assertEquals("a finger that slid off its key typed nothing", listOf("space"), log)
        assertEquals(1, t.count)
    }

    @Test fun twoWaitingKeysActInTheOrderTheyLanded() {
        val t = KeyTouches(); val log = ArrayList<String>()
        val space = t.wait { log += "space" }
        val enter = t.wait { log += "enter" }                 // Return lands while space is held: the space is typed first
        assertEquals(listOf("space"), log)
        assertFalse(t.release(space)); assertTrue(t.release(enter))
        assertEquals(listOf("space", "enter"), log)
    }

    @Test fun aKeyThatMustBeLetGoOnPurposeIsCalledOffByTheNextKey() {
        val t = KeyTouches(); val log = ArrayList<String>()
        val send = t.wait(early = false) { log += "send" }     // Return on a Send field
        t.landed(); log += "x"; t.acted()
        assertFalse(t.release(send))
        assertEquals("the message was not sent by the next key", listOf("x"), log)
        val again = t.wait(early = false) { log += "send" }
        assertTrue(t.release(again)); assertEquals(listOf("x", "send"), log)
    }

    // ---- the card ---------------------------------------------------------------------------------------------------------------

    private fun place(left: Float, top: Float, right: Float, panel: Float = 1080f) =
        KeyBalloon.place(left, top, right, panel, full = 160f, least = 94f, grow = 55f, margin = 6f)

    @Test fun theCardSitsOnItsKeyAndStaysInsideThePanelForEveryKeyPlace() {
        val panel = 1080f
        for (width in listOf(60f, 92f, 140f, 260f, 520f)) {
            var left = 0f
            while (left + width <= panel) {
                for (top in listOf(100f, 150f, 166f, 400f, 900f)) {
                    val b = place(left, top, left + width, panel)!!
                    assertEquals("the card ends where the key begins", top, b.top + b.height, .01f)
                    assertTrue("inside the panel: $b", b.left >= 0f && b.left + b.width <= panel)
                    assertTrue("over the whole key: key $left..${left + width}, card $b", b.left <= left + 6.01f && b.left + b.width >= left + width - 6.01f)
                    assertTrue("no wider than the key and its allowance", b.width <= width + 55f + .01f && b.width >= width)
                    assertTrue(b.height in 94f..160f && b.top >= 6f - .01f)
                    // Centred on the key wherever the panel's edge does not stop it.
                    val centred = left + width / 2f - b.width / 2f
                    if (centred >= 6f && centred + b.width <= panel - 6f) assertEquals(left + width / 2f, b.left + b.width / 2f, .01f)
                }
                left += 23f
            }
        }
    }

    @Test fun thereIsNoCardWhereThereIsNoRoomForOne() {
        assertNull("a top row with nothing above it", place(100f, 40f, 192f))
        assertNull(place(100f, 99f, 192f))
        assertNotNull(place(100f, 100f, 192f))
        assertEquals("shorter when there is less room, never clipped", 110f, place(100f, 116f, 192f)!!.height, .01f)
        assertNull("a key with no width", place(100f, 400f, 100f))
    }

    @Test fun aWideKeysCardIsNotEnormous() {
        assertEquals(92f * 1.5f, place(400f, 400f, 492f)!!.width, .01f)
        assertEquals(260f + 55f, place(400f, 400f, 660f)!!.width, .01f)
    }

    // ---- which key was meant ------------------------------------------------------------------------------------------------------

    /** QWERTY with keys 100 wide and 140 tall, 18 between keys and 30 between rows. */
    private val geometry: KeyGeometry = run {
        val rows = listOf("qwertyuiop" to 0f, "asdfghjkl" to 59f, "zxcvbnm" to 177f)
        val centres = HashMap<Char, Pt>()
        rows.forEachIndexed { r, (letters, inset) -> letters.forEachIndexed { i, ch -> centres[ch] = Pt(inset + 50f + i * 118f, 70f + r * 170f) } }
        KeyGeometry(centres, 100f, 140f)
    }
    private fun centre(ch: Char) = geometry.centres[ch]!!
    private fun strengths(vararg pairs: Pair<Char, Int>): (Char) -> Int = { ch -> pairs.firstOrNull { it.first == ch }?.second ?: 0 }

    @Test fun aTapWellInsideItsKeyIsAlwaysThatKey() {
        val w = centre('w')
        // "th" then a tap in the middle of W: "thw" is rare and "the" is everywhere, and it is still a W.
        assertEquals('w', TouchModel.aim(geometry, w, 'w', strengths('w' to 139, 'e' to 240)))
        assertEquals('w', TouchModel.aim(geometry, Pt(w.x + 30f, w.y), 'w', strengths('w' to 139, 'e' to 240)))
        assertEquals("even when W leads to no word at all", 'w', TouchModel.aim(geometry, Pt(w.x + 30f, w.y), 'w', strengths('e' to 240)))
        // Anywhere on its face but the last few pixels by an edge, and whatever the keys round it would spell.
        val every: (Char) -> Int = { if (it == 'w') 0 else 240 }
        for (dx in -40..40 step 8) for (dy in -55..55 step 11) assertEquals("$dx, $dy", 'w', TouchModel.aim(geometry, Pt(w.x + dx, w.y + dy), 'w', every))
    }

    @Test fun aTapOnTheLineBetweenTwoKeysIsTheOneTheWordWants() {
        val w = centre('w')
        val edge = Pt(w.x + 47f, w.y)                         // on W, 3 from its right edge and 21 from E's face
        assertEquals('e', TouchModel.aim(geometry, edge, 'w', strengths('w' to 139, 'e' to 240)))
        assertEquals("no word begins that way with W", 'e', TouchModel.aim(geometry, edge, 'w', strengths('e' to 120)))
        assertEquals("both go on to common words: it is the key that was hit", 'w', TouchModel.aim(geometry, edge, 'w', strengths('w' to 200, 'e' to 240)))
        assertEquals("neither goes anywhere (a name, a new word): the key that was hit", 'w', TouchModel.aim(geometry, edge, 'w', strengths()))
        assertEquals("the other side's key is too far to be meant", 'w', TouchModel.aim(geometry, edge, 'w', strengths('q' to 240)))
        // In the gap itself, nearer E's face than W's.
        assertEquals('e', TouchModel.aim(geometry, Pt(w.x + 58f, w.y), 'w', strengths('w' to 139, 'e' to 240)))
    }

    @Test fun aTapJustUnderAKeyIsNotTheKeyOfTheRowBelow() {
        // Found on a device: "w" typed, then a tap 3 px under W's bottom edge came out as "a" ("wa" is commoner than "ww"). The row
        // below is a whole key away and half a key to one side; a tap that close to W is W.
        val w = centre('w')
        val below = strengths('a' to 240, 's' to 240, 'w' to 100)
        assertEquals('w', TouchModel.aim(geometry, Pt(w.x, w.y + 73f), 'w', below))            // 3 under the edge (the key is 140 tall)
        assertEquals('w', TouchModel.aim(geometry, Pt(w.x, w.y + 78f), 'w', below))
        assertEquals('w', TouchModel.aim(geometry, Pt(w.x - 20f, w.y + 66f), 'w', below))      // low on its face, toward A
        // Only round the middle of the gap between the rows, and toward the key in question, is it in doubt.
        assertEquals('s', TouchModel.aim(geometry, Pt(w.x + 30f, w.y + 86f), 'w', below))
    }

    @Test fun theWordsAreNotLookedUpForATapThatIsNotInDoubt() {
        var asked = 0
        TouchModel.aim(geometry, centre('g'), 'g') { asked++; 240 }
        assertEquals(0, asked)
    }

    @Test fun withoutTheKeysPlacesNothingIsGuessed() {
        assertEquals('w', TouchModel.aim(KeyGeometry.EMPTY, Pt(0f, 0f), 'w', strengths('e' to 240)))
        assertEquals('1', TouchModel.aim(geometry, Pt(0f, 0f), '1', strengths('e' to 240)))
    }

    @Test fun howCommonAPrefixIsComesFromTheCommonestWordItBegins() {
        val e = WordEngine(mapOf("the" to 240, "they" to 209, "thwart" to 135, "thwarted" to 139, "zebra" to 131), emptyMap())
        assertEquals(240, e.prefixStrength("th")); assertEquals(240, e.prefixStrength("the"))
        assertEquals(139, e.prefixStrength("thw")); assertEquals(209, e.prefixStrength("they"))
        assertEquals(0, e.prefixStrength("thx")); assertEquals(0, e.prefixStrength("")); assertEquals(131, e.prefixStrength("z"))
    }
}
