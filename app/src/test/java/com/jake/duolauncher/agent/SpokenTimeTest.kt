package com.jake.duolauncher.agent

import com.jake.duolauncher.search.agent.SpokenTime
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenTimeTest {
    // A Monday morning.
    private val now = LocalDateTime.of(2026, 11, 16, 9, 0)

    @Test fun aLengthIsReadHoweverItIsSaid() {
        val cases = mapOf(
            "20 minutes" to 1200L, "for 20 minutes" to 1200L, "1 hour 30 minutes" to 5400L, "1h 30m" to 5400L, "an hour and a half" to 5400L,
            "half an hour" to 1800L, "ninety seconds" to 90L, "forty five minutes" to 2700L, "forty-five minutes" to 2700L,
            "two and a half hours" to 9000L, "an hour and 15 minutes" to 4500L, "a quarter of an hour" to 900L,
            "three quarters of an hour" to 2700L, "one hour" to 3600L, "a couple of minutes" to 120L, "in ten minutes" to 600L,
            "45 min" to 2700L, "2 hours" to 7200L, "an hour" to 3600L,
        )
        for ((said, seconds) in cases) assertEquals(said, seconds, SpokenTime.seconds(said))
    }

    @Test fun whatIsNotALengthIsNotGuessed() {
        for (said in listOf("", "soon", "a while", "20", "the cake", "tomorrow", "later")) assertNull(said, SpokenTime.seconds(said))
    }

    @Test fun aClockTimeIsReadHoweverItIsSaid() {
        val cases = mapOf(
            "6:45" to (6 to 45), "06:45" to (6 to 45), "7am" to (7 to 0), "7 pm" to (19 to 0), "19:00" to (19 to 0), "at 7" to (7 to 0),
            "7 tonight" to (19 to 0), "6 in the morning" to (6 to 0), "half past six" to (6 to 30), "quarter to 7" to (6 to 45),
            "quarter past 9 pm" to (21 to 15), "noon" to (12 to 0), "midnight" to (0 to 0), "12am" to (0 to 0), "12pm" to (12 to 0),
            "6.30" to (6 to 30), "seven o'clock" to (7 to 0), "6am tomorrow" to (6 to 0), "10 to 8" to (7 to 50),
        )
        for ((said, time) in cases) assertEquals(said, time, SpokenTime.clock(said))
        for (said in listOf("25:00", "13pm", "6:75", "soon", "", "tomorrow")) assertNull(said, SpokenTime.clock(said))
    }

    @Test fun anAlarmIsAtATimeOrInALength() {
        assertEquals(SpokenTime.Alarm.At(6, 45), SpokenTime.alarm("6:45"))
        assertEquals(SpokenTime.Alarm.In(45), SpokenTime.alarm("in 45 minutes"))
        assertEquals(SpokenTime.Alarm.In(90), SpokenTime.alarm("in an hour and a half"))
        assertNull(SpokenTime.alarm("in 3 days"))
        assertNull(SpokenTime.alarm("later"))
    }

    @Test fun aStartIsAMomentFromNow() {
        fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 11, day, hour, minute)
        val cases = mapOf(
            "now" to now, "in 2 hours" to at(16, 11), "in half an hour" to at(16, 9, 30), "at 3pm" to at(16, 15),
            "tomorrow at 3pm" to at(17, 15), "3pm tomorrow" to at(17, 15), "tomorrow 09:30" to at(17, 9, 30),
            "friday at 9:30" to at(20, 9, 30), "on friday at noon" to at(20, 12), "next tuesday at 10am" to at(17, 10),
            "tonight at 8" to at(16, 20), "the day after tomorrow at 10am" to at(18, 10),
            // A time already gone today is tomorrow's.
            "at 8am" to at(17, 8),
            // An event's bare small hour is the afternoon's.
            "at 3" to at(16, 15), "tomorrow at 4:30" to at(17, 16, 30),
            // The same weekday as today is next week's.
            "monday at 10am" to at(23, 10),
        )
        for ((said, moment) in cases) assertEquals(said, moment, SpokenTime.start(said, now))
    }

    @Test fun aDayWithNoTimeIsNotAStart() {
        for (said in listOf("tomorrow", "friday", "", "later", "next week")) assertNull(said, SpokenTime.start(said, now))
    }

    @Test fun aValueIsThePersonsWhenTheirWordsReadAsIt() {
        assertTrue(SpokenTime.saidAs("set a timer for an hour and a half please", 5400L) { SpokenTime.seconds(it) })
        assertTrue(SpokenTime.saidAs("wake me at 7 tonight", 19 to 0) { SpokenTime.clock(it) })
        assertFalse(SpokenTime.saidAs("set a timer for the cake", 3600L) { SpokenTime.seconds(it) })
        // An answer on a later line counts.
        assertTrue(SpokenTime.saidAs("set a timer\nAnswer: 10 minutes", 600L) { SpokenTime.seconds(it) })
    }

    @Test fun oneIsANumberOnlyWhereItCountsSomething() {
        assertEquals("1 hour", SpokenTime.digits("one hour"))
        assertEquals("the last one", SpokenTime.digits("the last one"))
        assertEquals("25 minutes", SpokenTime.digits("twenty five minutes"))
    }
}
