package com.jake.duolauncher

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ClockFormatTest {
    private val afternoon = LocalDateTime.of(2026, 10, 12, 13, 5)

    private fun show(pattern: String) = DateTimeFormatter.ofPattern(pattern, Locale.US).format(afternoon)

    @Test fun twentyFourHourShowsTheHourAsIs() = assertEquals("13:05", show(ClockFormat.timePattern(true)))
    @Test fun twelveHourDropsTheTwentyFourHourHour() = assertEquals("1:05", show(ClockFormat.timePattern(false)))
    @Test fun twelveHourCanCarryAmPm() = assertEquals("1:05 PM", show(ClockFormat.timePattern(false, meridiem = true)))
    @Test fun theMeridiemIsIgnoredIn24Hour() = assertEquals("13:05", show(ClockFormat.timePattern(true, meridiem = true)))
}
