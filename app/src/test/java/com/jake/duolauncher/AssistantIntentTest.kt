package com.jake.duolauncher

import com.jake.duolauncher.search.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantIntentTest {
    private inline fun <reified T : QuickAction> parsed(json: String): T {
        val action = AssistantIntent.parse(json)
        assertTrue("$json gave $action", action is T)
        return action as T
    }

    @Test fun aTimerKeepsItsLengthAndLabel() {
        val t = parsed<QuickAction.Timer>("""{"action":"timer","seconds":720,"label":"pasta"}""")
        assertEquals(720L, t.seconds)
        assertEquals("12 min timer · pasta", t.label)
        assertEquals("90 s timer", parsed<QuickAction.Timer>("""{"action":"timer","seconds":90}""").label)
    }

    @Test fun aTimerOutOfRangeOrNotAWholeNumberIsRefused() {
        assertNull(AssistantIntent.parse("""{"action":"timer","seconds":0}"""))
        assertNull(AssistantIntent.parse("""{"action":"timer","seconds":-5}"""))
        assertNull(AssistantIntent.parse("""{"action":"timer","seconds":${SpotlightSources.MAX_TIMER_SECONDS + 1}}"""))
        assertNull(AssistantIntent.parse("""{"action":"timer","seconds":"600"}"""))
        assertNull(AssistantIntent.parse("""{"action":"timer","seconds":12.5}"""))
        assertEquals(SpotlightSources.MAX_TIMER_SECONDS, parsed<QuickAction.Timer>("""{"action":"timer","seconds":${SpotlightSources.MAX_TIMER_SECONDS}}""").seconds)
    }

    @Test fun alarmsAtAClockTimeOrInMinutes() {
        val at = parsed<QuickAction.Alarm>("""{"action":"alarm","at":"07:30"}""")
        assertEquals(AlarmTarget.At(7, 30), at.target)
        assertEquals("Alarm at 7:30 AM", at.label)
        assertEquals(AlarmTarget.In(45), parsed<QuickAction.Alarm>("""{"action":"alarm","in_minutes":45}""").target)
        assertNull(AssistantIntent.parse("""{"action":"alarm","at":"24:00"}"""))
        assertNull(AssistantIntent.parse("""{"action":"alarm","at":"7:61"}"""))
        assertNull(AssistantIntent.parse("""{"action":"alarm","at":"seven"}"""))
        assertNull(AssistantIntent.parse("""{"action":"alarm","in_minutes":0}"""))
        assertNull(AssistantIntent.parse("""{"action":"alarm","in_minutes":${SpotlightSources.MAX_ALARM_MINUTES + 1}}"""))
        assertNull(AssistantIntent.parse("""{"action":"alarm"}"""))
    }

    @Test fun theArgumentFreeActions() {
        parsed<QuickAction.Stopwatch>("""{"action":"stopwatch"}""")
        parsed<QuickAction.Torch>("""{"action":"torch"}""")
        assertEquals(true, parsed<QuickAction.Focus>("""{"action":"focus","on":true}""").desired)
        assertEquals(false, parsed<QuickAction.Focus>("""{"action":"focus","on":false}""").desired)
        assertNull(AssistantIntent.parse("""{"action":"focus"}"""))
        assertNull(AssistantIntent.parse("""{"action":"focus","on":"yes"}"""))
    }

    @Test fun namesAndTexts() {
        assertEquals("buy milk", parsed<QuickAction.Note>("""{"action":"note","text":" buy milk "}""").text)
        assertEquals("Maps", parsed<QuickAction.OpenApp>("""{"action":"open_app","name":"Maps"}""").name)
        assertEquals("Sam", parsed<QuickAction.Call>("""{"action":"call","name":"Sam"}""").name)
        assertEquals("Sam", parsed<QuickAction.Text>("""{"action":"text","name":"Sam"}""").name)
        assertEquals("weather lisbon", parsed<QuickAction.WebSearch>("""{"action":"web","query":"weather lisbon"}""").query)
        assertNull(AssistantIntent.parse("""{"action":"note","text":"   "}"""))
        assertNull(AssistantIntent.parse("""{"action":"call","name":42}"""))
        assertNull(AssistantIntent.parse("""{"action":"open_app","name":"${"x".repeat(200)}"}"""))
    }

    @Test fun noneUnknownAndGarbageAreNotActions() {
        assertNull(AssistantIntent.parse("""{"action":"none"}"""))
        assertNull(AssistantIntent.parse("""{"action":"format_disk"}"""))
        assertNull(AssistantIntent.parse("""{}"""))
        assertNull(AssistantIntent.parse("not json"))
        assertNull(AssistantIntent.parse(""))
        assertNull(AssistantIntent.parse("""{"action":"timer","seconds":"""))
    }

    @Test fun whitespaceAroundTheObjectIsFine() {
        parsed<QuickAction.Torch>("""  {"action":"torch"}
""")
    }
}

class AskPolicyTest {
    private val ready = AssistantState.READY

    @Test fun questionsOfTwoWordsOrMoreAreOffered() {
        assertTrue(AskPolicy.offer("capital of Portugal", null, null, ready))
        assertTrue(AskPolicy.offer("timer for pasta 12 minutes", null, null, ready))
        assertTrue(AskPolicy.offer("why sky", null, null, AssistantState.LOADING))
    }

    @Test fun oneWordOrBlankIsNot() {
        assertFalse(AskPolicy.offer("maps", null, null, ready))
        assertFalse(AskPolicy.offer("   ", null, null, ready))
        assertFalse(AskPolicy.offer("maps  - ", null, null, ready))
    }

    @Test fun anAnswerOrACommandAlreadyHereWins() {
        assertFalse(AskPolicy.offer("5 km in mi", SmartAnswer("3.11 mi", "5 km"), null, ready))
        assertFalse(AskPolicy.offer("timer 5", null, SpotlightSources.quickAction("timer 5"), ready))
    }

    @Test fun onlyWhenTheAssistantCanAnswer() {
        for (s in listOf(AssistantState.OFF, AssistantState.NOT_INSTALLED, AssistantState.NO_MODEL, AssistantState.FAILED))
            assertFalse(s.name, AskPolicy.offer("capital of Portugal", null, null, s))
        assertFalse(AskPolicy.offer("a ".repeat(200), null, null, ready))
    }
}

class LabTrustTest {
    @Test fun onlyLabsOwnCertificate() {
        assertTrue(LabTrust.trusted(setOf(LabTrust.CERT_SHA256)))
        assertTrue(LabTrust.trusted(setOf(LabTrust.CERT_SHA256.uppercase())))
        assertFalse(LabTrust.trusted(emptySet()))
        assertFalse(LabTrust.trusted(setOf("00".repeat(32))))
        assertFalse(LabTrust.trusted(setOf(LabTrust.CERT_SHA256, "00".repeat(32))))
    }

    @Test fun wireStates() {
        assertEquals(AssistantState.NO_MODEL, LabTrust.fromWire(0))
        assertEquals(AssistantState.LOADING, LabTrust.fromWire(1))
        assertEquals(AssistantState.READY, LabTrust.fromWire(2))
        assertEquals(AssistantState.FAILED, LabTrust.fromWire(3))
        assertEquals(AssistantState.FAILED, LabTrust.fromWire(9))
    }
}
