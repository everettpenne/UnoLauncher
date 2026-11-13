package com.jake.duolauncher

import com.jake.duolauncher.search.*
import org.junit.Assert.*
import org.junit.Test

class SpotlightSourcesTest {
    private fun labels(q: String) = SpotlightSources.settings(q).map { it.label }

    @Test fun settingsMatchByNameAndAlternativeWords() {
        assertEquals("Wi-Fi", labels("wifi").first())
        assertEquals("Wi-Fi", labels("wi-fi").first())
        assertEquals("Bluetooth", labels("blue").first())
        assertEquals("Display", labels("bright").first())
        assertTrue("Security" in labels("fingerprint"))
        assertTrue("Security" in labels("screen lock"))
    }

    @Test fun shortOrUnknownQueriesFindNoSettings() {
        assertTrue(labels("wi").isEmpty())
        assertTrue(labels("").isEmpty())
        assertTrue(labels("zzzz").isEmpty())
        assertTrue(labels("calculator").isEmpty())
    }

    @Test fun settingsResultsAreCappedAndNameMatchesComeFirst() {
        val result = labels("app")
        assertTrue(result.size <= SpotlightSources.MAX_SETTINGS)
        assertEquals("Apps", result.first())
    }

    @Test fun timersParseWithAndWithoutUnits() {
        fun seconds(q: String) = SpotlightSources.timer(q)?.seconds
        assertEquals(300L, seconds("timer 5"))
        assertEquals(90L, seconds("timer 90 s"))
        assertEquals(600L, seconds("10 min timer"))
        assertEquals(7200L, seconds("set a timer for 2 hours"))
        assertEquals(90L, seconds("Timer 1.5 min"))
        assertEquals("5 min timer", SpotlightSources.timer("timer 5")?.label)
        assertEquals("2 h timer", SpotlightSources.timer("timer 2h")?.label)
    }

    @Test fun nonsenseTimersAreNotTimers() {
        assertNull(SpotlightSources.timer("timer"))
        assertNull(SpotlightSources.timer("timer 0"))
        assertNull(SpotlightSources.timer("timer 100 hours"))
        assertNull(SpotlightSources.timer("timers 5"))
        assertNull(SpotlightSources.timer("5"))
        assertNull(SpotlightSources.quickAction("clock"))
        assertTrue(SpotlightSources.quickAction("stopwatch") is QuickAction.Stopwatch)
    }

    @Test fun settingsMatchWhenYouAppendSettings() {
        assertEquals("Wi-Fi", labels("wifi settings").first())
        assertEquals("Bluetooth", labels("bluetooth settings").first())
        assertEquals("Display", labels("screen brightness").first())
        assertTrue(labels("settings").isEmpty())
    }

    @Test fun timerAcceptsCountdownAndStart() {
        assertEquals(300L, SpotlightSources.timer("countdown 5")?.seconds)
        assertEquals(300L, SpotlightSources.timer("start timer 5")?.seconds)
        assertEquals(600L, SpotlightSources.timer("10 min countdown")?.seconds)
    }

    @Test fun barePhoneNumbersDialAndShortOnesDoNot() {
        assertTrue(SpotlightSources.dial("555 123 4567") is QuickAction.Dial)
        assertTrue(SpotlightSources.dial("+1 555-123-4567") is QuickAction.Dial)
        assertEquals("555-123-4567", SpotlightSources.dial("555-123-4567")?.number)
        assertNull(SpotlightSources.dial("2048"))
        assertNull(SpotlightSources.dial("timer 5"))
        assertNull(SpotlightSources.dial(""))
        assertNull(SpotlightSources.dial("1-2-3-4-5-6"))
    }

    @Test fun callWithANumberDialsInsteadOfLookingUpAContact() {
        assertTrue(SpotlightSources.quickAction("call 555 123 4567") is QuickAction.Dial)
        assertTrue(SpotlightSources.call("call John") is QuickAction.Call)
        assertEquals("555-123-4567", (SpotlightSources.quickAction("call 555-123-4567") as QuickAction.Dial).number)
    }

    @Test fun domainsAndUrlsOpenInTheBrowser() {
        val link = SpotlightSources.openLink("example.com")
        assertTrue(link is QuickAction.OpenLink)
        assertEquals("https://example.com", link?.url)
        assertEquals("https://www.example.com/x", SpotlightSources.openLink("www.example.com/x")?.url)
        assertEquals("https://example.com/path", SpotlightSources.openLink("https://example.com/path")?.url)
        assertNull(SpotlightSources.openLink("maps"))
        assertNull(SpotlightSources.openLink("12.5"))
        assertNull(SpotlightSources.openLink("3.14"))
        assertNull(SpotlightSources.openLink("hello world"))
    }

    @Test fun idleActionsNeedNoArgument() {
        val idle = SpotlightSources.idleActions()
        assertTrue(idle.any { it is QuickAction.Stopwatch })
        assertTrue(idle.any { it is QuickAction.Torch })
        assertTrue(idle.any { it is QuickAction.Focus })
    }

    // ---- as it is asked, not typed as a command (these used to go to the model, which took seconds and once set a timer instead) ----

    @Test fun anAlarmAskedForInWordsIsAnsweredOnTheSpot() {
        fun at(q: String) = (SpotlightSources.alarm(q)?.target as? AlarmTarget.At)?.let { it.hour to it.minute }
        assertEquals(6 to 0, at("set an alarm for 6am tomorrow"))
        assertEquals(6 to 30, at("wake me up at 6:30"))
        assertEquals(6 to 30, at("wake me at 6:30 tomorrow morning"))
        assertEquals(19 to 0, at("alarm for 7 pm"))
        assertEquals(19 to 0, at("alarm at 19:00"))
        assertEquals(19 to 0, at("set an alarm for 7 tonight"))
        assertEquals(7 to 0, at("please set my alarm for 7 in the morning"))
        assertEquals(AlarmTarget.In(45), SpotlightSources.alarm("wake me up in 45 minutes")?.target)
        assertEquals(AlarmTarget.In(90), SpotlightSources.alarm("set an alarm in an hour and a half")?.target)
    }

    @Test fun whatOnlyMentionsAnAlarmIsNotOne() {
        for (q in listOf("what's my alarm", "alarm clock app", "when is my next alarm", "set an alarm", "wake me up", "alarm for 25:00",
            "set an alarm for 6am and text Sam")) assertNull(q, SpotlightSources.alarm(q))
    }

    @Test fun aTimerAskedForInWordsIsAnsweredOnTheSpot() {
        fun secs(q: String) = SpotlightSources.timer(q)?.seconds
        assertEquals(1200L, secs("set a timer for 20 minutes"))
        assertEquals(1200L, secs("20 minute timer"))
        assertEquals(5400L, secs("timer for an hour and a half"))
        assertEquals(5400L, secs("set a timer for 1 hour 30 minutes"))
        assertEquals(1800L, secs("half an hour timer"))
        assertEquals(9000L, secs("timer for 2 and a half hours"))
        assertEquals(90L, secs("count down 90 seconds"))
        assertNull(secs("timer for the pasta"))
        assertNull(secs("how long is my timer"))
    }
}
