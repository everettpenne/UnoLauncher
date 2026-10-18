package com.jake.duolauncher

import com.jake.duolauncher.island.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertEquals("Dynamic island, charging", IslandSpeech.describe(null, false, false, false, false, false, charging = true))
        assertEquals("Dynamic island, stopwatch running, charging, music playing",
            IslandSpeech.describe(null, false, false, false, true, true, charging = true))
    }

    @Test fun actionLabelsFitOnlyWhenThePanelIsWideEnoughForTheFont() {
        assertTrue(PanelHeight.actionsFit(336f, 1f))
        assertTrue(PanelHeight.actionsFit(306f, 1f))
        assertFalse(PanelHeight.actionsFit(305f, 1f))
        // A larger system font needs a wider panel; below the threshold the labels drop to icons.
        assertTrue(PanelHeight.actionsFit(336f, 1.1f))
        assertFalse(PanelHeight.actionsFit(336f, 1.2f))
        assertFalse(PanelHeight.actionsFit(336f, 1.6f))
        // Font scales past the cap do not keep raising the bar (the text is clipped instead).
        assertEquals(PanelHeight.actionsFit(336f, 1.6f), PanelHeight.actionsFit(336f, 3f))
    }

    // --- the panel's narration line (why it shows what it shows) ---

    private fun narrate(card: Boolean = false, call: Boolean = false, navigating: Boolean = false, ringing: Boolean = false,
        timer: Boolean = false, stopwatch: Boolean = false, media: Boolean = false, update: Boolean = false,
        charging: Boolean = false) = IslandNarration.label(card, call, navigating, ringing, timer, stopwatch, media, update, charging)

    @Test fun narrationNamesTheOneLiveThingInOrderOfImportance() {
        assertNull("an idle panel narrates nothing", narrate())
        assertEquals("Charging", narrate(charging = true))
        assertEquals("Live update", narrate(update = true, charging = true))
        assertEquals("Now playing", narrate(media = true, update = true))
        assertEquals("Stopwatch", narrate(stopwatch = true, media = true))
        assertEquals("Timer", narrate(timer = true, stopwatch = true))
        assertEquals("Timer finished", narrate(ringing = true, timer = true))
        assertEquals("You're navigating", narrate(navigating = true, ringing = true))
        assertEquals("You're on a call", narrate(call = true, navigating = true))
        assertEquals("New notification", narrate(card = true, call = true))
    }

    // --- what TalkBack hears when the live facts change ---

    private fun facts(ringing: Boolean = false, call: Boolean = false, timer: Boolean = false, playing: Boolean = false) =
        IslandLiveFacts(ringing, call, timer, playing)

    @Test fun arrivalsAndDeparturesAreEachSaidOnce() {
        assertEquals("Timer started", IslandAnnounce.transition(facts(), facts(timer = true)))
        assertEquals("Timer finished", IslandAnnounce.transition(facts(timer = true), facts(timer = true, ringing = true)))
        assertEquals("Call started", IslandAnnounce.transition(facts(), facts(call = true)))
        assertEquals("Call ended", IslandAnnounce.transition(facts(call = true), facts()))
        assertEquals("Music playing", IslandAnnounce.transition(facts(), facts(playing = true)))
        assertEquals("Music paused", IslandAnnounce.transition(facts(playing = true), facts()))
        assertEquals("Timer ended", IslandAnnounce.transition(facts(timer = true), facts()))
    }

    @Test fun noChangeAndSilencingTheRingSayNothing() {
        assertNull(IslandAnnounce.transition(facts(), facts()))
        assertNull(IslandAnnounce.transition(facts(timer = true), facts(timer = true)))
        // Silencing a finished timer is the person's own action, not something to read back.
        assertNull(IslandAnnounce.transition(facts(timer = true, ringing = true), facts()))
    }

    @Test fun theMostImportantChangeWinsWhenSeveralHappenAtOnce() {
        // A call starting while music stops speaks the call, not the music.
        assertEquals("Call started", IslandAnnounce.transition(facts(playing = true), facts(call = true)))
    }
}
