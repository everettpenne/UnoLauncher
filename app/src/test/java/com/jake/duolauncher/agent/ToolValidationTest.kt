package com.jake.duolauncher.agent

import com.jake.duolauncher.search.agent.Validation
import com.jake.duolauncher.search.agent.tools.*
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolValidationTest {
    private fun ok(tool: com.jake.duolauncher.search.agent.Tool, vararg values: Pair<String, String>) =
        tool.validate(values.toMap()) is Validation.Ok

    private fun rejected(tool: com.jake.duolauncher.search.agent.Tool, vararg values: Pair<String, String>) =
        tool.validate(values.toMap()) is Validation.Rejected

    @Test fun timersNeedAWholeNumberOfSecondsInRange() {
        assertTrue(ok(TimerTool, "seconds" to "1200"))
        assertTrue(ok(TimerTool, "seconds" to "1200", "label" to "pasta"))
        assertTrue(rejected(TimerTool, "seconds" to "0"))
        assertTrue(rejected(TimerTool, "seconds" to "90000"))
        assertTrue(rejected(TimerTool, "seconds" to "twenty"))
        assertTrue(rejected(TimerTool))
        assertTrue(rejected(TimerTool, "seconds" to "60", "label" to "x".repeat(61)))
    }

    @Test fun anAlarmTakesAClockTimeOrMinutesButNotBoth() {
        assertTrue(ok(AlarmTool, "at" to "6:45"))
        assertTrue(ok(AlarmTool, "at" to "06:45"))
        assertTrue(ok(AlarmTool, "in_minutes" to "30"))
        assertTrue(rejected(AlarmTool))
        assertTrue(rejected(AlarmTool, "at" to "06:45", "in_minutes" to "30"))
        assertTrue(rejected(AlarmTool, "at" to "25:00"))
        assertTrue(rejected(AlarmTool, "at" to "6:45pm"))
        assertTrue(rejected(AlarmTool, "in_minutes" to "0"))
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

    @Test fun numbersMustLookLikeNumbers() {
        assertTrue(ok(DialTool, "number" to "+1 555 0100"))
        assertTrue(rejected(DialTool, "number" to "12"))
        assertTrue(rejected(DialTool, "number" to "hello"))
        assertTrue(ok(MessageTool, "number" to "5550100"))
        assertTrue(rejected(MessageTool, "number" to "abc"))
    }

    @Test fun aUrlMustBeAnAddress() {
        assertTrue(ok(OpenUrlTool, "url" to "https://example.com/a?b=c"))
        assertTrue(rejected(OpenUrlTool, "url" to "example.com"))
        assertTrue(rejected(OpenUrlTool, "url" to "file:///etc/passwd"))
    }

    @Test fun aCalendarEventNeedsATitleAndTwoSensibleSpans() {
        assertTrue(ok(CalendarTool, "title" to "Dentist", "in_minutes" to "30", "duration_minutes" to "60"))
        assertTrue(ok(CalendarTool, "title" to "Now", "in_minutes" to "0", "duration_minutes" to "5"))
        assertTrue(rejected(CalendarTool, "title" to "Dentist", "in_minutes" to "30"))
        assertTrue(rejected(CalendarTool, "title" to "Dentist", "in_minutes" to "30", "duration_minutes" to "1"))
        assertTrue(rejected(CalendarTool, "title" to "", "in_minutes" to "30", "duration_minutes" to "60"))
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
        // Seen on the phone: "alarm in 30 minutes" planned as at:"none", and "alarm for 17:00" with in_minutes:0.
        assertTrue(com.jake.duolauncher.search.agent.tools.AlarmTool.validate(mapOf("at" to "none", "in_minutes" to "30")) is Validation.Ok)
        assertTrue(com.jake.duolauncher.search.agent.tools.AlarmTool.validate(mapOf("at" to "17:00", "in_minutes" to "0")) is Validation.Ok)
        assertTrue(com.jake.duolauncher.search.agent.tools.AlarmTool.validate(mapOf("at" to "", "in_minutes" to "")) is Validation.Rejected)
    }
}
