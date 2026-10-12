package com.jake.duolauncher

import com.jake.duolauncher.widgets.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class WorldClockWidgetTest {
    private val winter = Instant.parse("2026-01-15T22:30:00Z")

    @Test fun everyCuratedPlaceHasAValidZoneAndAName() {
        assertTrue(WorldClock.cities.isNotEmpty())
        WorldClock.cities.forEach {
            assertTrue("${it.zoneId} needs a name", it.label.isNotBlank())
            // Throws for an unknown zone: the picker must never offer a place the phone cannot resolve.
            ZoneId.of(it.zoneId)
        }
        assertEquals("Two places share a zone id", WorldClock.cities.size, WorldClock.cities.map { it.zoneId }.toSet().size)
    }

    @Test fun parseZonesSkipsBlanksUnknownAndRepeatsAndCapsTheList() {
        val raw = "Asia/Tokyo, Europe/London,Asia/Tokyo,,Not/AZone,America/New_York,Asia/Dubai,Australia/Sydney"
        assertEquals(listOf("Asia/Tokyo", "Europe/London", "America/New_York", "Asia/Dubai"), WorldClock.parseZones(raw))
        assertEquals(emptyList<String>(), WorldClock.parseZones(null))
        assertEquals(emptyList<String>(), WorldClock.parseZones(" , ,"))
    }

    @Test fun zonesRoundTripThroughStorage() {
        val zones = listOf("Europe/London", "Asia/Tokyo")
        assertEquals(zones, WorldClock.parseZones(WorldClock.serializeZones(zones)))
        assertEquals("", WorldClock.serializeZones(emptyList()))
    }

    @Test fun addAndRemoveKeepTheListBoundedAndUnique() {
        val full = listOf("Asia/Tokyo", "Europe/London", "America/New_York", "Asia/Dubai")
        assertEquals(full, WorldClock.add(full, "Australia/Sydney"))
        assertEquals(full, WorldClock.add(full, "Asia/Tokyo"))
        assertEquals(full.dropLast(1), WorldClock.add(full.dropLast(1), "Not/AZone"))
        assertEquals(full.dropLast(1) + "Australia/Sydney", WorldClock.add(full.dropLast(1), "Australia/Sydney"))
        assertEquals(listOf("Asia/Tokyo", "America/New_York"), WorldClock.removeAt(full.take(3), 1))
        assertEquals(full, WorldClock.removeAt(full, 4))
        assertEquals(full, WorldClock.removeAt(full, -1))
    }

    @Test fun searchMatchesCaseInsensitivelyAndNeverOffersAChosenPlace() {
        assertEquals(listOf("Europe/London"), WorldClock.search("LON", emptyList()).map { it.zoneId })
        assertTrue(WorldClock.search("london", listOf("Europe/London")).isEmpty())
        val open = WorldClock.search("", listOf("Asia/Tokyo"))
        assertEquals(WorldClock.MAX_RESULTS, open.size)
        assertTrue(open.none { it.zoneId == "Asia/Tokyo" })
        assertTrue(WorldClock.search("zzzz", emptyList()).isEmpty())
    }

    @Test fun rowShowsTheCitysTimeInBothClockStyles() {
        val deviceNow = ZonedDateTime.ofInstant(winter, ZoneId.of("UTC"))
        val tokyo = WorldClock.row(WorldClock.City("Tokyo", "Asia/Tokyo"), deviceNow, is24Hour = true)
        assertEquals("Tokyo", tokyo.label)
        assertEquals("07:30", tokyo.time)
        assertEquals("Tomorrow", tokyo.dayNote)
        assertEquals("7:30 AM", WorldClock.row(WorldClock.City("Tokyo", "Asia/Tokyo"), deviceNow, is24Hour = false).time)
        val london = WorldClock.row(WorldClock.City("London", "Europe/London"), deviceNow, is24Hour = false)
        assertEquals("10:30 PM", london.time)
        assertNull(london.dayNote)
    }

    @Test fun dayNoteNamesOnlyTheDayAheadOrBehind() {
        val day = LocalDate.of(2026, 1, 15)
        assertNull(WorldClock.dayNote(day, day))
        assertEquals("Tomorrow", WorldClock.dayNote(day, day.plusDays(1)))
        assertEquals("Yesterday", WorldClock.dayNote(day, day.minusDays(1)))
        assertNull(WorldClock.dayNote(day, day.plusDays(2)))
    }

    @Test fun aCityAcrossTheDateLineReadsAsTheOtherDay() {
        // 01:00 UTC is still yesterday evening in New York.
        val deviceNow = ZonedDateTime.ofInstant(Instant.parse("2026-01-15T01:00:00Z"), ZoneId.of("UTC"))
        val newYork = WorldClock.row(WorldClock.City("New York", "America/New_York"), deviceNow, is24Hour = true)
        assertEquals("20:00", newYork.time)
        assertEquals("Yesterday", newYork.dayNote)
    }

    @Test fun offsetLabelFormatsGmtWithHoursAndMinutes() {
        assertEquals("GMT", WorldClock.offsetLabel("Europe/London", winter))
        assertEquals("GMT+1", WorldClock.offsetLabel("Europe/London", Instant.parse("2026-07-15T12:00:00Z")))
        assertEquals("GMT+9", WorldClock.offsetLabel("Asia/Tokyo", winter))
        assertEquals("GMT+5:30", WorldClock.offsetLabel("Asia/Kolkata", winter))
        assertEquals("GMT-5", WorldClock.offsetLabel("America/New_York", winter))
        assertEquals("", WorldClock.offsetLabel("Not/AZone", winter))
    }

    @Test fun tagsAreSafeForTestTags() {
        WorldClock.cities.forEach { assertTrue('/' !in it.tag && it.tag.isNotBlank()) }
        assertEquals("Asia_Tokyo", WorldClock.City("Tokyo", "Asia/Tokyo").tag)
    }
}
