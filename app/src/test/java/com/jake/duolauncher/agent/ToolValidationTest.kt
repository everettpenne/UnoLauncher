package com.jake.duolauncher.agent

import com.jake.duolauncher.search.agent.Validation
import com.jake.duolauncher.search.agent.tools.*
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolValidationTest {
    private fun ok(tool: com.jake.duolauncher.search.agent.Tool, vararg values: Pair<String, String>) =
        tool.validate(values.toMap()) is Validation.Ok

    private fun rejected(tool: com.jake.duolauncher.search.agent.Tool, vararg values: Pair<String, String>) =
        tool.validate(values.toMap()) is Validation.Rejected

    private fun missing(tool: com.jake.duolauncher.search.agent.Tool, said: String, vararg values: Pair<String, String>) =
        tool.missing(com.jake.duolauncher.search.agent.Params(values.toMap()), said)

    @Test fun aTimersLengthIsThePersonsWordsAndMustBeOnesTheyGave() {
        assertTrue(ok(TimerTool, "length" to "20 minutes"))
        assertTrue(ok(TimerTool, "length" to "an hour and a half", "label" to "pasta"))
        assertTrue(rejected(TimerTool))
        assertTrue(rejected(TimerTool, "length" to "1 minute", "label" to "x".repeat(61)))
        assertNull(missing(TimerTool, "timer for 20 minutes", "length" to "20 minutes"))
        // However the planner wrote it, it is the length that was said.
        assertNull(missing(TimerTool, "set a timer for an hour and a half", "length" to "90 minutes"))
        assertNull(missing(TimerTool, "ninety second timer", "length" to "90 seconds"))
        // Seen from the model: "set a timer for the cake" planned as an hour. A length nobody gave is asked for.
        assertEquals("length", missing(TimerTool, "set a timer for the cake", "length" to "1 hour")?.param)
        assertNotNull(missing(TimerTool, "set a timer", "length" to "soon"))
        assertNotNull(missing(TimerTool, "timer for 2 days", "length" to "2 days"))
    }

    @Test fun anAlarmsTimeIsAClockTimeOrALengthFromNow() {
        assertTrue(ok(AlarmTool, "when" to "6:45"))
        assertTrue(rejected(AlarmTool))
        assertNull(missing(AlarmTool, "wake me at 6:45", "when" to "06:45"))
        assertNull(missing(AlarmTool, "alarm for 7am", "when" to "7:00"))
        assertNull(missing(AlarmTool, "alarm at 7 tonight", "when" to "19:00"))
        assertNull(missing(AlarmTool, "wake me up in 45 minutes", "when" to "in 45 minutes"))
        assertNull(missing(AlarmTool, "set an alarm for half past six", "when" to "half past six"))
        assertEquals("when", missing(AlarmTool, "set an alarm", "when" to "07:00")?.param)
        assertNotNull(missing(AlarmTool, "alarm for 25:00", "when" to "25:00"))
    }

    @Test fun focusNeedsARealBoolean() {
        assertTrue(ok(FocusTool, "on" to "true"))
        assertTrue(ok(FocusTool, "on" to "off"))
        assertTrue(rejected(FocusTool, "on" to "maybe"))
        assertTrue(rejected(FocusTool))
    }

    @Test fun aNoteIsCapped() {
        assertTrue(ok(NoteTool, "text" to "buy bread"))
        assertTrue(rejected(NoteTool, "text" to "x".repeat(401)))
        assertTrue(rejected(NoteTool, "text" to "   "))
    }

    @Test fun aNumberNobodyGaveIsAskedFor() {
        assertNull(missing(DialTool, "dial +1 555 0100", "number" to "+1 555 0100"))
        assertEquals("number", missing(DialTool, "call my dentist", "number" to "5550199")?.param)
        assertNotNull(missing(DialTool, "call hello", "number" to "hello"))
        assertNull(missing(MessageTool, "text 555 0100 saying hi", "number" to "5550100", "text" to "hi"))
        // The question names who the number is missing for.
        assertEquals("I don't have a number for Priya. What is it?", missing(MessageTool, "tell Priya I'll call tonight", "number" to "+123456789")?.question)
    }

    @Test fun aUrlMustBeAnAddress() {
        assertTrue(ok(OpenUrlTool, "url" to "https://example.com/a?b=c"))
        assertTrue(rejected(OpenUrlTool, "url" to "example.com"))
        assertTrue(rejected(OpenUrlTool, "url" to "file:///etc/passwd"))
    }

    @Test fun aCalendarEventNeedsATitleAndAStartThePersonGave() {
        assertTrue(ok(CalendarTool, "title" to "Dentist", "start" to "in 30 minutes", "length" to "an hour"))
        assertTrue(ok(CalendarTool, "title" to "Dentist", "start" to "tomorrow at 3pm"))
        assertTrue(rejected(CalendarTool, "title" to "Dentist"))
        assertTrue(rejected(CalendarTool, "title" to "", "start" to "now"))
        assertNull(missing(CalendarTool, "dentist tomorrow at 3pm for an hour", "title" to "Dentist", "start" to "tomorrow at 3pm", "length" to "60 minutes"))
        assertNull(missing(CalendarTool, "lunch friday at noon", "title" to "Lunch", "start" to "friday 12:00"))
        // Seen from the model: "add a haircut to my calendar" planned as starting now.
        assertEquals("start", missing(CalendarTool, "add a haircut to my calendar", "title" to "Haircut", "start" to "now")?.param)
        assertEquals("length", missing(CalendarTool, "gym at 6pm", "title" to "Gym", "start" to "6pm", "length" to "90 minutes")?.param)
        // The hour without the day they gave is not their time.
        assertEquals("start", missing(CalendarTool, "dentist tomorrow at 3pm", "title" to "Dentist", "start" to "3pm")?.param)
    }

    @Test fun aWebSearchNeedsAQuery() {
        assertTrue(ok(WebSearchTool, "query" to "rain tomorrow"))
        assertTrue(rejected(WebSearchTool))
        assertTrue(rejected(WebSearchTool, "query" to "  "))
    }

    @Test fun aSettingsPanelIsOneOfTheOnesWeCanOpen() {
        for (panel in listOf("wifi", "bluetooth", "nfc", "airplane", "display", "sound", "battery")) {
            assertTrue("$panel should be allowed", ok(SettingsPanelTool, "panel" to panel))
        }
        assertTrue(rejected(SettingsPanelTool, "panel" to "developer options"))
        assertTrue(rejected(SettingsPanelTool))
    }

    @Test fun anAppNameIsRequired() {
        assertTrue(ok(OpenAppTool, "app" to "Clock"))
        assertTrue(rejected(OpenAppTool))
        assertTrue(rejected(OpenAppTool, "app" to ""))
    }

    @Test fun anOptionalValueTheModelOnlyFilledInIsTreatedAsLeftOut() {
        // Seen on the phone: an optional value written as "none" because something had to be written.
        val checked = TimerTool.validate(mapOf("length" to "5 minutes", "label" to "none")) as Validation.Ok
        assertNull(checked.value.text("label"))
        assertTrue(CalendarTool.validate(mapOf("title" to "Gym", "start" to "6pm", "length" to "n/a")) is Validation.Ok)
    }
}
