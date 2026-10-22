package com.jake.duolauncher

import com.jake.duolauncher.appearance.*

import com.jake.duolauncher.keyboard.*
import org.junit.Assert.*
import org.junit.Test

class KeyHapticsTest {
    @Test fun lettersAreTheLightestAndEverythingIsAudibleAtTheDefault() {
        val letter = HapticProfile.plan(HapticKind.LETTER, HapticProfile.DEFAULT_STRENGTH)!!
        HapticKind.entries.forEach { k ->
            val p = HapticProfile.plan(k, HapticProfile.DEFAULT_STRENGTH)!!
            assertTrue("$k", p.scale in .06f..1f)
            if (k !in setOf(HapticKind.CURSOR, HapticKind.DELETE_REPEAT, HapticKind.PREVIEW, HapticKind.SWIPE_KEY, HapticKind.AUTOCORRECT)) assertTrue("$k not lighter than letters", p.scale >= letter.scale)
        }
        assertTrue(HapticProfile.plan(HapticKind.DELETE_REPEAT, .6f)!!.scale < HapticProfile.plan(HapticKind.DELETE, .6f)!!.scale)
        assertTrue(HapticProfile.plan(HapticKind.RETURN, .6f)!!.scale >= HapticProfile.plan(HapticKind.LETTER, .6f)!!.scale)
    }

    @Test fun eachKindOfKeyHasItsOwnFeel() {
        val letter = HapticProfile.plan(HapticKind.LETTER, .6f)!!
        val delete = HapticProfile.plan(HapticKind.DELETE, .6f)!!
        val space = HapticProfile.plan(HapticKind.SPACE, .6f)!!
        val ret = HapticProfile.plan(HapticKind.RETURN, .6f)!!
        val modifier = HapticProfile.plan(HapticKind.MODIFIER, .6f)!!
        val preview = HapticProfile.plan(HapticKind.PREVIEW, .6f)!!
        // Letters use the softest primitive; delete is the sharper tick.
        assertEquals(HapticProfile.LOW_TICK, letter.primitive)
        assertEquals(HapticProfile.TICK, delete.primitive)
        assertTrue(delete.scale >= letter.scale)
        // Space and Return are a deeper click than the letter tick.
        assertEquals(HapticProfile.CLICK, space.primitive)
        assertEquals(HapticProfile.CLICK, ret.primitive)
        assertTrue(ret.scale >= space.scale)
        // Shift / page / symbol-layer switches feel like a soft two-tick toggle.
        assertEquals(HapticProfile.TICK, modifier.primitive)
        assertTrue(modifier.secondScale > 0f && modifier.secondDelayMs > 0)
        // A popup appearing (the accent strip) pops softer than a letter.
        assertTrue(preview.scale < letter.scale)
    }

    @Test fun strengthScalesTheFeelAndZeroTurnsItOff() {
        val soft = HapticProfile.plan(HapticKind.SPACE, .3f)!!.scale
        val normal = HapticProfile.plan(HapticKind.SPACE, .6f)!!.scale
        val strong = HapticProfile.plan(HapticKind.SPACE, 1f)!!.scale
        assertTrue(soft < normal && normal < strong); assertTrue(strong <= 1f)
        assertNull(HapticProfile.plan(HapticKind.LETTER, 0f))
        assertNull(HapticProfile.plan(HapticKind.CURSOR, .06f))
    }

    @Test fun twoVibrationsTooCloseTogetherBecomeOne() {
        assertFalse(HapticProfile.allow(1_000_000_000L, 1_005_000_000L))
        assertTrue(HapticProfile.allow(1_000_000_000L, 1_020_000_000L))
    }

    @Test fun theMirrorFollowsTypingAndGivesUpWhenItCannotKnow() {
        val m = TextMirror()
        assertFalse(m.valid)
        m.reset("hello wor"); m.commit("l"); m.commit("d"); assertEquals("hello world", m.get())
        m.delete(2); assertEquals("hello wor", m.get()); assertTrue(m.valid)
        m.delete(500); assertFalse(m.valid)
        m.reset("x"); m.invalidate(); m.commit("y"); assertFalse(m.valid)
    }

    @Test fun theMirrorKeepsOnlyTheTail() {
        val m = TextMirror(keep = 10)
        m.reset("abcdefghijklmnopqrstuvwxyz"); assertEquals("qrstuvwxyz", m.get())
        repeat(100) { m.commit("z") }
        assertTrue(m.get().length <= 20); assertTrue(m.get().endsWith("zzzz"))
    }

    @Test fun ourOwnCursorMovesAreRecognisedInOrderEvenWhenSomeAreSkipped() {
        val c = ExpectedCursor(size = 4)
        (1..6).forEach { c.expect(it) }                     // only the last four are kept: 3, 4, 5, 6
        assertTrue(c.isOurs(3)); assertTrue(c.isOurs(4))
        assertTrue("an app may report only the last of a burst", c.isOurs(6))
        assertFalse("an earlier position after a later one is not an echo", c.isOurs(5))
        c.expect(10); assertFalse(c.isOurs(99)); assertFalse("an unexpected update clears what was expected", c.isOurs(10))
    }

    @Test fun anAppsOwnChangeLandingWhereAnEarlierKeystrokeLeftTheCursorIsNotTakenForAnEcho() {
        // Signal: the keyboard types at 40 and 41; the app then rewrites its box and the cursor lands back on 40.
        val c = ExpectedCursor()
        c.expect(40); c.expect(41)
        assertTrue(c.isOurs(40)); assertTrue(c.isOurs(41))
        assertFalse(c.isOurs(40))
    }
}
