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
}
