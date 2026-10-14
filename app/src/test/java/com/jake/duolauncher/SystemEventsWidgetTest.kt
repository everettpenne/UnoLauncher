package com.jake.duolauncher

import com.jake.duolauncher.widgets.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class SystemEventsWidgetTest {
    @Test fun theRingKeepsOnlyTheNewestEvents() {
        val ring = EventRing(3)
        repeat(5) { ring.add("event $it", atMs = it * 1_000L) }
        assertEquals(3, ring.size)
        assertEquals(listOf("event 2", "event 3", "event 4"), ring.latest(3).map { it.text })
    }

    @Test fun latestReturnsNewestFirstInConsoleOrderOldestToNewest() {
        val ring = EventRing(10)
        repeat(5) { ring.add("e$it", atMs = it * 1_000L) }
        val last = ring.latest(2)
        assertEquals(listOf("e3", "e4"), last.map { it.text })
        assertTrue("events come out oldest first so the newest is at the bottom", last.first().atMs < last.last().atMs)
    }

    @Test fun eventWordingReadsPlainly() {
        assertEquals("Screen on", ConsoleEvents.screen(true))
        assertEquals("Screen off", ConsoleEvents.screen(false))
        assertEquals("Charger connected", ConsoleEvents.power(true))
        assertEquals("Charger disconnected", ConsoleEvents.power(false))
        assertEquals("Battery low", ConsoleEvents.batteryLow(true))
        assertEquals("Airplane mode on", ConsoleEvents.airplane(true))
        assertEquals("Do Not Disturb off", ConsoleEvents.dnd(false))
        assertEquals("Headphones connected", ConsoleEvents.headphones(true))
        assertEquals("VPN disconnected", ConsoleEvents.vpn(false))
        assertEquals("Updated com.example.app", ConsoleEvents.packageReplaced("com.example.app"))
        assertEquals("Ringer silent", ConsoleEvents.ringer(0))
        assertEquals("Ringer vibrate", ConsoleEvents.ringer(1))
        assertEquals("Ringer on", ConsoleEvents.ringer(2))
    }

    @Test fun theTimestampFollowsTheClockStyle() {
        // 2026-01-15T14:23:05Z.
        val ms = 1_768_486_985_000L
        assertEquals("14:23:05", ConsoleTime.format(ms, is24 = true, ZoneId.of("UTC")))
        assertEquals("2:23:05 PM", ConsoleTime.format(ms, is24 = false, ZoneId.of("UTC")))
    }

    @Test fun anEmptyZoneNameStillReads() {
        assertEquals("Time zone changed", ConsoleEvents.timezone(""))
        assertEquals("Time zone Europe/London", ConsoleEvents.timezone("Europe/London"))
    }
}
