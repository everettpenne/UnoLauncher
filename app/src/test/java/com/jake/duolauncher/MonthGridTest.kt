package com.jake.duolauncher

import com.jake.duolauncher.appearance.MonthGrid
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class MonthGridTest {
    @Test fun weeksAreRowsOfSevenCoveringEveryDayOnce() {
        for (month in listOf(YearMonth.of(2026, 2), YearMonth.of(2024, 2), YearMonth.of(2026, 10), YearMonth.of(2026, 5))) {
            for (first in listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)) {
                val weeks = MonthGrid.weeks(month, first)
                assertTrue(weeks.all { it.size == 7 })
                assertEquals((1..month.lengthOfMonth()).map { month.atDay(it) }, weeks.flatten().filterNotNull())
                assertTrue(weeks.size in 4..6)
            }
        }
    }

    @Test fun theFirstDayLandsInTheRightColumn() {
        // 1 October 2026 is a Thursday: column 3 when the week starts on Monday, column 4 on Sunday.
        assertEquals(LocalDate.of(2026, 10, 1), MonthGrid.weeks(YearMonth.of(2026, 10), DayOfWeek.MONDAY)[0][3])
        assertEquals(LocalDate.of(2026, 10, 1), MonthGrid.weeks(YearMonth.of(2026, 10), DayOfWeek.SUNDAY)[0][4])
        // February 2026 starts on a Sunday and has 28 days: exactly four rows when the week starts on Sunday.
        assertEquals(4, MonthGrid.weeks(YearMonth.of(2026, 2), DayOfWeek.SUNDAY).size)
    }

    @Test fun weekdayLabelsStartAtTheFirstDay() {
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), MonthGrid.weekdayLabels(DayOfWeek.MONDAY, Locale.ENGLISH))
        assertEquals("S", MonthGrid.weekdayLabels(DayOfWeek.SUNDAY, Locale.ENGLISH).first())
    }

    @Test fun shiftingIsBounded() {
        assertEquals(YearMonth.of(2027, 1), MonthGrid.shift(YearMonth.of(2026, 12), 1))
        assertEquals(YearMonth.of(2025, 10), MonthGrid.shift(YearMonth.of(2026, 10), -12))
        assertEquals(YearMonth.of(1900, 1), MonthGrid.shift(YearMonth.of(1900, 3), -12))
        assertEquals(YearMonth.of(2200, 12), MonthGrid.shift(YearMonth.of(2200, 6), 12))
    }
}
