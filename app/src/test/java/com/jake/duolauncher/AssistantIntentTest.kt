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
        // A timer of no length is how the model reads "time how long this takes": that is the stopwatch.
        parsed<QuickAction.Stopwatch>("""{"action":"timer","seconds":0}""")
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

class AssistantReadTest {
    private fun read(json: String, query: String = "") = AssistantIntent.read(json, query)

    @Test fun inMinutesInTheWordsBeatsAClockTimeFromTheModel() {
        // The real model wrote "10:00" for "in 10 minutes" and "01:00" for "in an hour".
        assertEquals(AlarmTarget.In(10), ((read("""{"action":"alarm","at":"10:00"}""", "remind me in 10 minutes to check the oven") as AssistantIntent.Outcome.Act).action as QuickAction.Alarm).target)
        assertEquals(AlarmTarget.In(60), ((read("""{"action":"alarm","at":"01:00"}""", "wake me in an hour") as AssistantIntent.Outcome.Act).action as QuickAction.Alarm).target)
        assertEquals(AlarmTarget.In(30), ((read("""{"action":"alarm","at":"00:30"}""", "alarm in half an hour") as AssistantIntent.Outcome.Act).action as QuickAction.Alarm).target)
        // A clock time in the words is left to the model.
        assertEquals(AlarmTarget.At(17, 0), ((read("""{"action":"alarm","at":"17:00"}""", "remind me to call mom at 5pm") as AssistantIntent.Outcome.Act).action as QuickAction.Alarm).target)
    }

    @Test fun relativeMinutes() {
        assertEquals(10, AssistantIntent.relativeMinutes("remind me in 10 minutes"))
        assertEquals(90, AssistantIntent.relativeMinutes("alarm in 1.5 hours"))
        assertEquals(60, AssistantIntent.relativeMinutes("wake me in an hour"))
        assertNull(AssistantIntent.relativeMinutes("wake me at 7"))
        assertNull(AssistantIntent.relativeMinutes("meeting in the morning"))
    }

    @Test fun whatCantBeDoneSaysWhyInsteadOfAskingForAGuess() {
        assertEquals(AssistantIntent.Outcome.Cannot(AssistantIntent.TIMER_LIMIT), read("""{"action":"timer","seconds":172800,"label":"2 days"}""", "set a timer for 2 days"))
        assertEquals(AssistantIntent.Outcome.Cannot(AssistantIntent.NOT_A_TIME), read("""{"action":"alarm","at":"25:00"}""", "set an alarm for 25:00"))
        assertEquals(AssistantIntent.Outcome.Cannot(AssistantIntent.ALARM_LIMIT), read("""{"action":"alarm","in_minutes":5000}""", "alarm later"))
        assertEquals(AssistantIntent.Outcome.None, read("""{"action":"none"}""", "why is the sky blue"))
        assertEquals(AssistantIntent.Outcome.None, read("garbage", "x"))
    }

    @Test fun aLabelThatOnlyRepeatsTheLengthIsDropped() {
        assertEquals("3 h timer", ((read("""{"action":"timer","seconds":10800,"label":"3 hours"}""") as AssistantIntent.Outcome.Act).action).label)
        assertEquals("3 h timer", ((read("""{"action":"timer","seconds":10800,"label":"timer"}""") as AssistantIntent.Outcome.Act).action).label)
        assertEquals("12 min timer · pasta", ((read("""{"action":"timer","seconds":720,"label":"pasta"}""") as AssistantIntent.Outcome.Act).action).label)
    }
}

class AnswerTextTest {
    @Test fun markdownMarksAreTakenOut() {
        assertEquals("Sam messaged you.", AnswerText.clean("**Sam** messaged you."))
        assertEquals("Lisbon", AnswerText.clean("# Lisbon"))
    }

    @Test fun theModelTalkingToItselfIsNotAnAnswer() {
        assertNull(AnswerText.clean("[Your complete, well-structured response following all given constraints]"))
        assertNull(AnswerText.clean("You are an expert logical reasoner. Think through the problem carefully."))
        assertNull(AnswerText.clean("   "))
        assertEquals("You are at 87% battery.", AnswerText.clean("You are at 87% battery."))
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
    @Test fun noModelRightAfterWarmIsNotFinal() {
        // warm() starts loading in the background, so Lab's first answer can still be "no model".
        assertTrue(LabTrust.keepWatching(AssistantState.NO_MODEL, 0))
        assertFalse(LabTrust.keepWatching(AssistantState.NO_MODEL, LabTrust.NO_MODEL_GRACE_MS))
        assertTrue(LabTrust.keepWatching(AssistantState.LOADING, 30_000))
        assertFalse(LabTrust.keepWatching(AssistantState.LOADING, LabTrust.WATCH_MS))
        assertFalse(LabTrust.keepWatching(AssistantState.READY, 0))
        assertFalse(LabTrust.keepWatching(AssistantState.FAILED, 0))
    }

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
