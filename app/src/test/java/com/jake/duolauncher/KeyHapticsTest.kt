package com.jake.duolauncher

import com.jake.duolauncher.keyboard.*
import org.junit.Assert.*
import org.junit.Test

class KeyHapticsTest {
    @Test fun lettersAreTheLightestAndEverythingIsAudibleAtTheDefault() {
        val letter = HapticProfile.plan(HapticKind.LETTER, HapticProfile.DEFAULT_STRENGTH)!!
        HapticKind.entries.forEach { k ->
            val p = HapticProfile.plan(k, HapticProfile.DEFAULT_STRENGTH)!!
            assertTrue("$k", p.scale in .06f..1f)
            if (k != HapticKind.CURSOR && k != HapticKind.DELETE_REPEAT) assertTrue("$k not lighter than letters", p.scale >= letter.scale)
        }
        assertTrue(HapticProfile.plan(HapticKind.DELETE_REPEAT, .6f)!!.scale < HapticProfile.plan(HapticKind.DELETE, .6f)!!.scale)
        assertTrue(HapticProfile.plan(HapticKind.RETURN, .6f)!!.scale >= HapticProfile.plan(HapticKind.LETTER, .6f)!!.scale)
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

    @Test fun ourOwnCursorMovesAreRecognisedEvenWhenTheirUpdatesArriveLate() {
        val c = ExpectedCursor(size = 4)
        (1..6).forEach { c.expect(it) }
        assertTrue(c.isOurs(6)); assertTrue(c.isOurs(3)); assertFalse(c.isOurs(1)); assertFalse(c.isOurs(99))
        c.clear(); assertFalse(c.isOurs(6))
    }
}
