package com.jake.duolauncher

import com.jake.duolauncher.appearance.*
import com.jake.duolauncher.widgets.*

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class GlanceWidgetTest {
    private val zone = ZoneId.of("UTC")

    @Test fun timePatternShowsMeridiemOnlyInTwelveHourStyle() {
        assertEquals("HH:mm", Glance.timePattern(true))
        assertEquals("h:mm a", Glance.timePattern(false))
    }

    @Test fun alarmLineNamesOnlyTheOtherDay() {
        val now = ZonedDateTime.of(2026, 1, 15, 22, 0, 0, 0, zone)
        val tomorrow = ZonedDateTime.of(2026, 1, 16, 7, 30, 0, 0, zone)
        assertEquals("07:30 · Tomorrow", Glance.alarmLine(now, tomorrow, is24 = true))
        assertEquals("7:30 AM · Tomorrow", Glance.alarmLine(now, tomorrow, is24 = false))
        val today = ZonedDateTime.of(2026, 1, 15, 7, 30, 0, 0, zone)
        assertEquals("07:30", Glance.alarmLine(now, today, is24 = true))
    }

    @Test fun sunLineShowsBothTimesOrThePolarTruth() {
        val normal = SolarSchedule(ZonedDateTime.of(2026, 1, 15, 6, 58, 0, 0, zone),
            ZonedDateTime.of(2026, 1, 15, 19, 2, 0, 0, zone), PolarDaylight.NORMAL)
        assertEquals("↑ 06:58  ↓ 19:02", Glance.sunLine(normal, is24 = true))
        assertEquals("↑ 6:58  ↓ 7:02", Glance.sunLine(normal, is24 = false))
        assertEquals("The sun does not set today", Glance.sunLine(SolarSchedule(null, null, PolarDaylight.DAY), true))
        assertEquals("The sun does not rise today", Glance.sunLine(SolarSchedule(null, null, PolarDaylight.NIGHT), true))
    }

    @Test fun anAlarmDaysAwayNamesItsDay() {
        val now = ZonedDateTime.of(2026, 10, 9, 22, 0, 0, 0, java.time.ZoneId.of("UTC"))   // a Friday night
        val monday = ZonedDateTime.of(2026, 10, 12, 7, 30, 0, 0, java.time.ZoneId.of("UTC"))
        assertEquals("07:30 · Mon", Glance.alarmLine(now, monday, is24 = true, locale = java.util.Locale.ENGLISH))
        val sixDays = now.plusDays(6).withHour(6)
        assertEquals("06:00 · Thu", Glance.alarmLine(now, sixDays, is24 = true, locale = java.util.Locale.ENGLISH))
        val later = now.plusDays(9).withHour(6)
        assertEquals("6:00 AM · Oct 18", Glance.alarmLine(now, later, is24 = false, locale = java.util.Locale.ENGLISH))
        assertEquals("07:30 · Tomorrow", Glance.alarmLine(now, now.plusDays(1).withHour(7).withMinute(30), is24 = true, locale = java.util.Locale.ENGLISH))
    }
}
