package com.jake.duolauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.ZonedDateTime

class UnoWidgetLogicTest {
    @Test fun monthGridStartsOnTheChosenDayAndFillsWholeWeeks() {
        // October 2026 starts on a Thursday.
        val monday = MonthGrid.cells(YearMonth.of(2026, 10), DayOfWeek.MONDAY)
        assertEquals(List<Int?>(3) { null }, monday.take(3))
        assertEquals(1, monday[3])
        assertEquals(0, monday.size % 7)
        assertEquals(31, monday.filterNotNull().last())
        val sunday = MonthGrid.cells(YearMonth.of(2026, 10), DayOfWeek.SUNDAY)
        assertEquals(4, sunday.indexOf(1))
        assertEquals(DayOfWeek.SUNDAY, MonthGrid.headers(DayOfWeek.SUNDAY).first())
        assertEquals(DayOfWeek.SATURDAY, MonthGrid.headers(DayOfWeek.SUNDAY).last())
    }

    @Test fun monthGridHandlesAMonthThatStartsOnTheFirstColumn() {
        // February 2027 starts on a Monday and has 28 days: exactly four weeks, no padding at all.
        val cells = MonthGrid.cells(YearMonth.of(2027, 2), DayOfWeek.MONDAY)
        assertEquals(28, cells.size)
        assertTrue(cells.none { it == null })
    }

    @Test fun progressRunsFromZeroToOne() {
        val start = TimeProgress.of(LocalDateTime.of(2026, 1, 1, 0, 0))
        assertEquals(0f, start.year, 1e-6f); assertEquals(0f, start.day, 1e-6f); assertEquals(1, start.dayOfYear)
        val noon = TimeProgress.of(LocalDateTime.of(2026, 7, 2, 12, 0)) // day 183 of 365
        assertEquals(.5f, noon.day, 1e-6f)
        assertEquals((182.5f / 365f), noon.year, 1e-4f)
        assertEquals(365 - 183, noon.daysLeftInYear)
        val end = TimeProgress.of(LocalDateTime.of(2026, 12, 31, 23, 59, 59))
        assertTrue(end.year > .999f && end.year <= 1f)
        assertEquals(366, TimeProgress.of(LocalDateTime.of(2028, 3, 1, 0, 0)).daysInYear)
    }

    @Test fun moonIsNewAtItsReferenceAndFullHalfACycleLater() {
        val newMoon = ZonedDateTime.of(2000, 1, 6, 18, 14, 0, 0, ZoneOffset.UTC)
        assertEquals(0.0, MoonPhase.illumination(newMoon), 1e-3)
        assertEquals("New Moon", MoonPhase.name(MoonPhase.cycle(newMoon)))
        val full = newMoon.plusSeconds((14.765 * 86_400).toLong())
        assertTrue(MoonPhase.illumination(full) > .99)
        assertEquals("Full Moon", MoonPhase.name(MoonPhase.cycle(full)))
        assertTrue(MoonPhase.waxing(MoonPhase.cycle(newMoon.plusDays(5))))
        assertTrue(!MoonPhase.waxing(MoonPhase.cycle(newMoon.plusDays(20))))
    }

    @Test fun moonAgeWrapsForDatesBeforeTheReference() {
        val before = ZonedDateTime.of(1990, 5, 5, 0, 0, 0, 0, ZoneOffset.UTC)
        assertTrue(MoonPhase.ageDays(before) in 0.0..29.54)
        // A known full moon: 2026-10-26 (about 04:12 UTC). Close enough for a mean-cycle model.
        val knownFull = ZonedDateTime.of(2026, 10, 26, 4, 0, 0, 0, ZoneOffset.UTC)
        assertTrue(MoonPhase.illumination(knownFull) > .95)
    }

    @Test fun phaseNamesCoverTheWholeCycle() {
        listOf(0.0 to "New Moon", .1 to "Waxing Crescent", .25 to "First Quarter", .4 to "Waxing Gibbous", .5 to "Full Moon",
            .6 to "Waning Gibbous", .75 to "Last Quarter", .9 to "Waning Crescent", .99 to "New Moon")
            .forEach { (cycle, name) -> assertEquals(name, MoonPhase.name(cycle)) }
    }

    @Test fun countdownWording() {
        val today = LocalDate.of(2026, 10, 10)
        assertEquals("Today" to "", Countdown.label(today, today))
        assertEquals("1" to "day", Countdown.label(today, today.plusDays(1)))
        assertEquals("12" to "days", Countdown.label(today, today.plusDays(12)))
        assertEquals("1" to "day ago", Countdown.label(today, today.minusDays(1)))
        assertEquals("30" to "days ago", Countdown.label(today, today.minusDays(30)))
        assertEquals(LocalDate.of(2027, 1, 1), Countdown.parse("2027-01-01"))
        assertNull(Countdown.parse("soon")); assertNull(Countdown.parse(null))
    }

    @Test fun sunWording() {
        val rise = ZonedDateTime.of(2026, 6, 21, 5, 30, 0, 0, ZoneOffset.UTC)
        assertEquals("15 h 4 min", SunText.dayLength(rise, rise.plusMinutes(15 * 60 + 4)))
        assertEquals("0 h 0 min", SunText.dayLength(rise, rise.minusHours(1)))
    }

    @Test fun counterAndTextLimits() {
        assertEquals(0, WidgetText.clampCount(-5)); assertEquals(WidgetText.MAX_COUNT, WidgetText.clampCount(5_000_000))
        assertEquals(WidgetText.MAX_NOTE, WidgetText.note("x".repeat(5_000)).length)
        assertEquals(WidgetText.MAX_NAME, WidgetText.name("y".repeat(100)).length)
    }

    @Test fun libraryIdsAreUniqueReservedAwayAndFitTheGrid() {
        val ids = UnoWidgets.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        val reserved = setOf(EMPTY_WIDGET, CLOCK_WIDGET, DATE_WIDGET, INFO_WIDGET, NEEDS_BINDING_WIDGET, FOLDER_WIDGET, FOLDER_WIDE_PICK, FOLDER_TALL_PICK)
        assertTrue(ids.none { it in reserved })
        assertTrue(ids.all { it < 0 })
        UnoWidgets.all.forEach {
            assertTrue("${it.label} fits", it.spanX in 1..GRID_COLUMNS && it.spanY in 1..GRID_ROWS)
            assertTrue(it.label.isNotBlank() && it.blurb.isNotBlank())
            assertTrue(isBuiltinWidgetId(it.id))
        }
        assertTrue(isBuiltinWidgetId(FOLDER_WIDGET) && isBuiltinWidgetId(CLOCK_WIDGET))
        assertTrue(!isBuiltinWidgetId(123) && !isBuiltinWidgetId(NEEDS_BINDING_WIDGET))
    }
}
