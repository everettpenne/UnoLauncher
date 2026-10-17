package com.jake.duolauncher

import com.jake.duolauncher.search.*
import org.junit.Assert.*
import org.junit.Test

class UnoActionsTest {
    // ---- note ----------------------------------------------------------------------------------

    @Test fun noteParsesTextAfterTheWord() {
        assertEquals("buy milk", SpotlightSources.note("note buy milk")?.text)
        assertEquals("remember to call Sam", SpotlightSources.note("  Note   remember to call Sam  ")?.text)
        assertEquals("5", SpotlightSources.note("note 5")?.text)
    }

    @Test fun bareOrWordLikeNotesAreNotNotes() {
        assertNull(SpotlightSources.note("note"))
        assertNull(SpotlightSources.note("note   "))
        assertNull(SpotlightSources.note("notebook"))
        assertNull(SpotlightSources.note("notes"))
    }

    // ---- alarm ---------------------------------------------------------------------------------

    private fun alarm(q: String) = SpotlightSources.alarm(q)

    @Test fun alarmInParsesMinutes() {
        assertEquals(AlarmTarget.In(20), alarm("alarm in 20")?.target)
        assertEquals(AlarmTarget.In(90), alarm("alarm in 90 min")?.target)
        assertEquals(AlarmTarget.In(120), alarm("alarm in 2 hours")?.target)
        assertEquals(AlarmTarget.In(60), alarm("alarm in 1h")?.target)
        assertEquals("Alarm in 20 min", alarm("alarm in 20")?.label)
        assertEquals("Alarm in 1 h 30 min", alarm("alarm in 90")?.label)
        assertEquals("Alarm in 2 h", alarm("alarm in 120")?.label)
    }

    @Test fun alarmAtParsesClockTimesAndMeridiem() {
        assertEquals(AlarmTarget.At(7, 30), alarm("alarm 7:30")?.target)
        assertEquals(AlarmTarget.At(7, 0), alarm("alarm 7am")?.target)
        assertEquals(AlarmTarget.At(19, 0), alarm("alarm 7pm")?.target)
        assertEquals(AlarmTarget.At(19, 30), alarm("alarm 19:30")?.target)
        assertEquals(AlarmTarget.At(0, 0), alarm("alarm 12am")?.target)
        assertEquals(AlarmTarget.At(12, 0), alarm("alarm 12pm")?.target)
        assertEquals(AlarmTarget.At(0, 5), alarm("alarm 0:05")?.target)
        assertEquals(AlarmTarget.At(19, 30), alarm("alarm 7:30 p.m.")?.target)
        assertEquals("Alarm at 7:30 AM", alarm("alarm 7:30")?.label)
        assertEquals("Alarm at 7:00 PM", alarm("alarm 7pm")?.label)
        assertEquals("Alarm at 12:05 AM", alarm("alarm 0:05")?.label)
    }

    @Test fun nonsenseAlarmsAreNotAlarms() {
        assertNull(alarm("alarm"))
        assertNull(alarm("alarm in"))
        assertNull(alarm("alarm in 0"))
        assertNull(alarm("alarm in 2000"))
        assertNull(alarm("alarm 7:60"))
        assertNull(alarm("alarm 24:00"))
        assertNull(alarm("alarm 13pm"))
        assertNull(alarm("alarm 0am"))
    }

    // ---- torch and focus -----------------------------------------------------------------------

    @Test fun torchMatchesItsAliasesOnly() {
        assertTrue(SpotlightSources.torch("torch") is QuickAction.Torch)
        assertTrue(SpotlightSources.torch("  Flashlight ") is QuickAction.Torch)
        assertNull(SpotlightSources.torch("torch on"))
        assertNull(SpotlightSources.torch("flash"))
    }

    @Test fun focusParsesOnOffAndToggle() {
        val toggle = SpotlightSources.focus("focus")
        assertTrue(toggle is QuickAction.Focus && toggle.desired == null)
        assertTrue(SpotlightSources.focus("focus on")?.desired == true)
        assertTrue(SpotlightSources.focus("turn focus off")?.desired == false)
        assertNull(SpotlightSources.focus("focused"))
    }

    // ---- call and text -------------------------------------------------------------------------

    @Test fun callAndTextParseAName() {
        assertEquals("John", SpotlightSources.call("call John")?.name)
        assertEquals("Sam Smith", SpotlightSources.text("Text Sam Smith")?.name)
        // The name is echoed as typed; matching is case-insensitive and the contact row shows the
        // contact's own (already-capitalised) display name, so nothing here should reformat it.
        assertEquals("ann", SpotlightSources.text("message ann")?.name)
        assertEquals("bob", SpotlightSources.text("sms bob")?.name)
        assertNull(SpotlightSources.call("call"))
        assertNull(SpotlightSources.text("text"))
        assertNull(SpotlightSources.call("callback"))
    }

    // ---- action selection ----------------------------------------------------------------------

    @Test fun quickActionChoosesTheRightAction() {
        assertTrue(SpotlightSources.quickAction("timer 5") is QuickAction.Timer)
        assertTrue(SpotlightSources.quickAction("stopwatch") is QuickAction.Stopwatch)
        assertTrue(SpotlightSources.quickAction("note hi") is QuickAction.Note)
        assertTrue(SpotlightSources.quickAction("alarm 7") is QuickAction.Alarm)
        assertTrue(SpotlightSources.quickAction("torch") is QuickAction.Torch)
        assertTrue(SpotlightSources.quickAction("focus") is QuickAction.Focus)
        assertTrue(SpotlightSources.quickAction("call bob") is QuickAction.Call)
        assertTrue(SpotlightSources.quickAction("text bob") is QuickAction.Text)
    }

    @Test fun quickActionLeavesOrdinaryQueriesAlone() {
        assertNull(SpotlightSources.quickAction("clock"))
        assertNull(SpotlightSources.quickAction("5"))
        assertNull(SpotlightSources.quickAction(""))
        assertNull(SpotlightSources.quickAction("calculator"))
        assertNull(SpotlightSources.quickAction("message"))
    }

    // ---- intent and alarm arithmetic ----------------------------------------------------------

    @Test fun phoneNumbersKeepOnlyUriCharacters() {
        assertEquals("+15551234567", ActionLogic.phoneNumber("+1 (555) 123-4567"))
        assertEquals("1234", ActionLogic.phoneNumber("1 2-3.4"))
        assertEquals("", ActionLogic.phoneNumber(""))
    }

    @Test fun dialAndSmsIntentsAreHonest() {
        assertEquals(ActionIntent(ActionLogic.ACTION_DIAL, "tel:+15551234567"), ActionLogic.dialIntent("+1 555 123 4567"))
        assertEquals(ActionIntent(ActionLogic.ACTION_SENDTO, "smsto:+15551234567"), ActionLogic.smsIntent("+1 (555) 123-4567"))
    }

    @Test fun alarmIntentCarriesHourAndMinute() {
        assertEquals(ActionIntent(ActionLogic.ACTION_SET_ALARM, null, 7, 30), ActionLogic.alarmIntent(7, 30))
    }

    @Test fun alarmAtWrapsPastMidnightAndAddsMinutes() {
        assertEquals(0 to 10, ActionLogic.alarmAt(20, 23, 50))
        assertEquals(8 to 30, ActionLogic.alarmAt(90, 7, 0))
        assertEquals(0 to 0, ActionLogic.alarmAt(1440, 0, 0))
        assertEquals(23 to 59, ActionLogic.alarmAt(1, 23, 58))
    }
}
