package com.jake.duolauncher

import com.jake.duolauncher.island.TimerPresets
import com.jake.duolauncher.widgets.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class WidgetLibraryLogicTest {
    // --- moon: what is coming ---

    @Test fun theNextFullOrNewMoonIsTheNearerOne() {
        val newMoon = ZonedDateTime.of(2000, 1, 6, 18, 14, 0, 0, ZoneOffset.UTC)
        // Just after a new moon the next full moon is about two weeks off, the next new moon four.
        val after = newMoon.plusDays(2)
        val next = MoonPhase.next(after)
        assertEquals("Full moon", next.name); assertTrue(next.days in 12..13)
        // Just after a full moon, the next new moon is nearer.
        val afterFull = newMoon.plusDays(17)
        assertEquals("New moon", MoonPhase.next(afterFull).name)
    }

    @Test fun daysUntilACycleNeverGoesBackwardsOrPastAMonth() {
        val at = ZonedDateTime.of(2026, 10, 12, 12, 0, 0, 0, ZoneOffset.UTC)
        for (target in listOf(0.0, .5)) {
            val d = MoonPhase.daysUntilCycle(at, target)
            assertTrue("$target gave $d", d >= 0.0 && d < 29.6)
        }
    }

    @Test fun moonWordingIsPlain() {
        assertEquals("Full moon in 6 days", MoonPhase.nextText(MoonPhase.Next("Full moon", 6)))
        assertEquals("New moon tomorrow", MoonPhase.nextText(MoonPhase.Next("New moon", 1)))
        assertEquals("New moon tomorrow", MoonPhase.nextText(MoonPhase.Next("New moon", 0)))
    }

    // --- countdown: every year ---

    private val today = LocalDate.of(2026, 10, 12)

    @Test fun aSetDateInTheFutureIsLeftAlone() {
        assertEquals(LocalDate.of(2027, 3, 1), Countdown.target(today, LocalDate.of(2027, 3, 1), yearly = true))
        assertEquals(LocalDate.of(2027, 3, 1), Countdown.target(today, LocalDate.of(2027, 3, 1), yearly = false))
    }

    @Test fun aYearlyDateRollsToItsNextDay() {
        assertEquals(LocalDate.of(2027, 5, 3), Countdown.target(today, LocalDate.of(1990, 5, 3), yearly = true))
        assertEquals(LocalDate.of(2026, 12, 25), Countdown.target(today, LocalDate.of(2001, 12, 25), yearly = true))
    }

    @Test fun aYearlyDateIsTodayOnItsDay() {
        assertEquals(today, Countdown.target(today, LocalDate.of(1980, 10, 12), yearly = true))
        assertEquals("Today", Countdown.label(today, Countdown.target(today, LocalDate.of(1980, 10, 12), true)).first)
    }

    @Test fun aPastDateThatIsNotYearlyCountsUp() {
        val target = Countdown.target(today, LocalDate.of(2026, 10, 1), yearly = false)
        assertEquals("11" to "days ago", Countdown.label(today, target))
    }

    @Test fun theTwentyNinthOfFebruaryFallsOnTheTwentyEighthInAYearWithoutOne() {
        val set = LocalDate.of(2024, 2, 29)
        assertEquals(LocalDate.of(2027, 2, 28), Countdown.target(LocalDate.of(2026, 10, 12), set, true))
        assertEquals(LocalDate.of(2028, 2, 29), Countdown.target(LocalDate.of(2027, 10, 12), set, true))
    }

    @Test fun yearsBetweenTheSetDateAndTheTargetNeverGoNegative() {
        assertEquals(37, Countdown.years(LocalDate.of(1990, 5, 3), LocalDate.of(2027, 5, 3)))
        assertEquals(0, Countdown.years(LocalDate.of(2030, 1, 1), LocalDate.of(2026, 1, 1)))
    }

    // --- analog clock ---

    @Test fun handsPointWhereTheTimeIs() {
        assertEquals(0f, AnalogClock.minuteAngle(LocalTime.of(3, 0)), 1e-4f)
        assertEquals(90f, AnalogClock.hourAngle(LocalTime.of(3, 0)), 1e-4f)
        assertEquals(180f, AnalogClock.minuteAngle(LocalTime.of(3, 30)), 1e-4f)
        assertEquals(105f, AnalogClock.hourAngle(LocalTime.of(3, 30)), 1e-4f)
        assertEquals(0f, AnalogClock.hourAngle(LocalTime.of(12, 0)), 1e-4f)
        assertEquals(0f, AnalogClock.hourAngle(LocalTime.of(0, 0)), 1e-4f)
    }

    @Test fun theHourHandInTheAfternoonMatchesTheMorning() {
        assertEquals(AnalogClock.hourAngle(LocalTime.of(3, 15)), AnalogClock.hourAngle(LocalTime.of(15, 15)), 1e-4f)
    }

    @Test fun theClockRedrawsAtTheNextMinute() {
        assertEquals(60_000L, AnalogClock.msToNextMinute(LocalTime.of(10, 0, 0)))
        assertEquals(30_000L, AnalogClock.msToNextMinute(LocalTime.of(10, 0, 30)))
        assertEquals(1L, AnalogClock.msToNextMinute(LocalTime.of(10, 0, 59, 999_000_000)))
    }

    // --- next alarm ---

    private val zone = ZoneId.of("UTC")
    private fun at(day: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 10, day, h, m, 0, 0, zone)

    @Test fun anAlarmLaterTodayReadsAsToday() {
        val parts = NextAlarmText.describe(at(12, 6, 0), at(12, 7, 30), is24Hour = true)
        assertEquals("07:30", parts.time); assertEquals("Today", parts.day); assertEquals("in 1 h 30 min", parts.remaining)
    }

    @Test fun anAlarmTomorrowMorningReadsAsTomorrow() {
        val parts = NextAlarmText.describe(at(12, 22, 0), at(13, 6, 45), is24Hour = true)
        assertEquals("Tomorrow", parts.day); assertEquals("in 8 h 45 min", parts.remaining)
    }

    @Test fun aFurtherAlarmNamesTheDayOrTheDate() {
        assertEquals("Thursday", NextAlarmText.describe(at(12, 8, 0), at(15, 8, 0), true).day)
        assertTrue(NextAlarmText.describe(at(1, 8, 0), at(20, 8, 0), true).day.contains("20"))
    }

    @Test fun twelveHourTimeCarriesAmPm() {
        val time = NextAlarmText.describe(at(12, 6, 0), at(12, 7, 30), is24Hour = false).time
        assertTrue(time, time.startsWith("7:30") && (time.contains("AM") || time.contains("am")))
    }

    @Test fun anAlarmMinutesAwayAndAnImminentOne() {
        assertEquals("in 25 min", NextAlarmText.describe(at(12, 6, 0), at(12, 6, 25), true).remaining)
        assertEquals("in under a minute", NextAlarmText.describe(at(12, 6, 0), at(12, 6, 0).plusSeconds(20), true).remaining)
        assertEquals("in 2 d 3 h", NextAlarmText.describe(at(12, 6, 0), at(14, 9, 0), true).remaining)
    }

    // --- timers ---

    @Test fun oneSetOfTimerPresetsServesTheWidgetAndTheIsland() {
        assertEquals(listOf(5, 10, 25, 45), TimerPresets.MINUTES)
        assertEquals(TimerPresets.MINUTES.sorted(), TimerPresets.MINUTES)
        assertTrue(TimerPresets.MINUTES.all { it > 0 } && TimerPresets.MINUTES.size % 2 == 0)
    }
}
