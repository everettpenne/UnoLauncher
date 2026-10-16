package com.jake.duolauncher

import com.jake.duolauncher.island.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class IslandPanelLogicTest {
    private val zone = ZoneId.of("UTC")
    private fun at(h: Int, m: Int, dayOffset: Long = 0) = ZonedDateTime.of(2026, 10, 16, h, m, 0, 0, zone).plusDays(dayOffset).toInstant().toEpochMilli()

    @Test fun anAlarmWithinADayShowsOnlyItsTime() {
        assertEquals("7:30 AM", NextAlarmText.format(at(7, 30, 1), at(8, 0), false, zone, Locale.US))
        assertEquals("07:30", NextAlarmText.format(at(7, 30, 1), at(8, 0), true, zone, Locale.US))
        assertEquals("11:15 PM", NextAlarmText.format(at(23, 15), at(8, 0), false, zone, Locale.US))
    }

    @Test fun anAlarmMoreThanADayAwayNamesItsWeekday() {
        // Friday 16 Oct 2026; two days on is a Sunday.
        assertEquals("Sun 6:45 AM", NextAlarmText.format(at(6, 45, 2), at(8, 0), false, zone, Locale.US))
    }

    @Test fun anAlarmInThePastShowsNothing() {
        assertNull(NextAlarmText.format(at(7, 0), at(8, 0), false, zone, Locale.US))
    }

    @Test fun trackTimesRoundDownAndCountWhatIsLeft() {
        assertEquals("0:59", MediaTimes.clock(59_900))
        assertEquals("1:05:03", MediaTimes.clock(3_903_000))
        assertEquals("1:20", MediaTimes.elapsed(.5f, 160_000))
        assertEquals("-1:20", MediaTimes.remaining(.5f, 160_000))
        assertEquals("-0:00", MediaTimes.remaining(1f, 160_000))
        assertEquals("0:00", MediaTimes.elapsed(-1f, 160_000))
    }

    @Test fun thePanelGrowsForAHeadlineAndForALargerFont() {
        assertEquals(0f, PanelHeight.extraDp(false, 1f), 0f)
        assertEquals(PanelHeight.HEADLINE_ROW_DP, PanelHeight.extraDp(true, 1f), 0f)
        assertEquals(PanelHeight.TEXT_BUDGET_DP * .3f, PanelHeight.extraDp(false, 1.3f), 0.01f)
        // Smaller fonts do not shrink it, and huge fonts stop growing it.
        assertEquals(0f, PanelHeight.extraDp(false, .8f), 0f)
        assertEquals(PanelHeight.MAX_EXTRA_DP, PanelHeight.extraDp(true, 3f), 0.01f)
    }

    @Test fun theIslandIsDescribedByWhatIsLiveOnIt() {
        assertEquals("Dynamic island", IslandSpeech.describe(null, false, false, false, false, false))
        assertEquals("Dynamic island, Silent, on a call, music playing", IslandSpeech.describe("Silent", false, true, false, false, true))
        assertEquals("Dynamic island, timer finished", IslandSpeech.describe(null, true, false, true, false, false))
    }
}
