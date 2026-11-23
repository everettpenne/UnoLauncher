package com.jake.duolauncher

import com.jake.duolauncher.widgets.SuggestionGrid
import org.junit.Assert.*
import org.junit.Test

/** What Home learns and what it makes of it: habits by the hour that fade, suggestions that never show what is hidden, and a stack
 *  that only turns itself for a real habit. */
class HomeHabitsTest {
    private val day = 20_000L
    private fun habit(vararg at: Pair<Int, Int>, on: Long = day): HabitRules.Habit {
        var h: HabitRules.Habit? = null
        for ((hour, times) in at) repeat(times) { h = HabitRules.noted(h, hour, on) }
        return h ?: HabitRules.empty(on)
    }

    @Test fun aHabitIsStrongestAtItsHourAndStillCountsBesideIt() {
        val coffee = habit(8 to 5)
        assertEquals(5f, HabitRules.pull(coffee, 8, day), .001f)
        assertEquals(2.5f, HabitRules.pull(coffee, 9, day), .001f); assertEquals(2.5f, HabitRules.pull(coffee, 7, day), .001f)
        assertEquals(.75f, HabitRules.pull(coffee, 10, day), .001f)
        assertEquals(0f, HabitRules.pull(coffee, 14, day), 0f)
        // Midnight's neighbours are eleven and one.
        assertEquals(1.5f, HabitRules.pull(habit(23 to 3), 0, day), .001f)
    }

    @Test fun aHabitFadesAndIsGoneInAFewWeeks() {
        val once = habit(8 to 1)
        assertEquals(.9f, HabitRules.pull(once, 8, day + 1), .001f)
        assertTrue(HabitRules.pull(once, 8, day + 7) < .5f)
        assertFalse(HabitRules.isGone(once, day + 20)); assertTrue(HabitRules.isGone(once, day + 40))
        // Noting it again fades what was there first, then adds one.
        assertEquals(1.9f, HabitRules.noted(once, 8, day + 1).counts[8], .001f)
        // A clock put back does not grow a habit.
        assertEquals(1f, HabitRules.pull(once, 8, day - 5), .001f)
    }

    @Test fun suggestionsFollowTheHourThenFallBackToWhatIsOpenedMost() {
        val habits = mapOf("mail" to habit(8 to 6, 13 to 1), "music" to habit(18 to 4), "maps" to habit(8 to 2), "bank" to habit(8 to 9))
        val all = setOf("mail", "music", "maps", "bank", "camera", "notes")
        assertEquals(listOf("bank", "mail", "maps", "camera"), HabitRules.suggest(habits, 8, day, all, listOf("camera", "mail", "notes"), 4))
        assertEquals(listOf("music", "camera", "mail", "notes"), HabitRules.suggest(habits, 18, day, all, listOf("camera", "mail", "notes"), 4))
        // With nothing learned it is the fallback alone, each app once.
        assertEquals(listOf("camera", "mail"), HabitRules.suggest(emptyMap(), 8, day, all, listOf("camera", "mail", "camera"), 2))
        assertEquals(emptyList<String>(), HabitRules.suggest(habits, 8, day, all, emptyList(), 0))
    }

    @Test fun aHiddenAppIsNeverSuggestedHoweverStrongItsHabit() {
        val habits = mapOf("bank" to habit(8 to 50), "mail" to habit(8 to 1))
        val shown = HabitRules.suggest(habits, 8, day, allowed = setOf("mail", "camera"), fallback = listOf("bank", "camera"), limit = 4)
        assertEquals(listOf("mail", "camera"), shown)
    }

    @Test fun aStackTurnsOnlyForARealHabit() {
        // The calendar in the morning, the player in the evening; one look is not a habit.
        val members = listOf(habit(8 to 3), habit(19 to 4), null)
        assertEquals(0, HabitRules.stackPage(members, 8, day)); assertEquals(1, HabitRules.stackPage(members, 19, day))
        assertNull("nothing is usual at three in the afternoon", HabitRules.stackPage(members, 15, day))
        assertNull(HabitRules.stackPage(listOf(habit(8 to 1), null), 8, day))
        assertNull(HabitRules.stackPage(emptyList(), 8, day))
        assertNull("a habit that has faded no longer turns the stack", HabitRules.stackPage(members, 8, day + 30))
    }

    @Test fun aHabitIsWrittenDownAndReadBack() {
        val h = habit(8 to 3, 23 to 1)
        val back = HabitRules.decode(HabitRules.encode(h))!!
        assertEquals(h.day, back.day); assertArrayEquals(h.counts, back.counts, .01f)
        for (bad in listOf(null, "", "x", "5|1,2,3", "5|" + List(24) { "a" }.joinToString(","), "5|" + List(24) { "-1" }.joinToString(","), "|" + List(24) { "0" }.joinToString(",")))
            assertNull(bad, HabitRules.decode(bad))
    }

    @Test fun theWidgetShowsAsManyAppsAsItHasRoomFor() {
        assertEquals(4, SuggestionGrid.columns(340f)); assertEquals(2, SuggestionGrid.columns(150f)); assertEquals(2, SuggestionGrid.columns(40f))
        assertEquals(2, SuggestionGrid.rows(170f)); assertEquals(2, SuggestionGrid.rows(134f)); assertEquals(1, SuggestionGrid.rows(100f)); assertEquals(1, SuggestionGrid.rows(10f))
    }
}
