package com.jake.duolauncher

import com.jake.duolauncher.appearance.PlaceInput
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PlaceInputTest {
    private fun ok(a: String, b: String) = PlaceInput.parse(a, b) as PlaceInput.Result.Ok
    private fun assertPlace(lat: Double, lon: Double, a: String, b: String) {
        val r = ok(a, b); assertEquals(lat, r.latitude, 1e-9); assertEquals(lon, r.longitude, 1e-9)
    }

    @Test fun plainDecimalsStillWork() = assertPlace(40.7128, -74.0060, "40.7128", "-74.0060")
    @Test fun commaDecimalsWork() = assertPlace(40.7128, -74.006, "40,7128", "-74,0060")
    @Test fun typographicMinusAndDegreeSignWork() = assertPlace(-33.8688, 151.2093, "−33.8688°", "151.2093°")
    @Test fun hemisphereLettersWork() { assertPlace(-33.8688, 151.2093, "33.8688 S", "151.2093 E"); assertPlace(40.7, -74.0, "N 40.7", "W 74.0") }

    @Test fun aPairPastedIntoEitherFieldFillsBoth() {
        assertPlace(40.7128, -74.0060, "40.7128, -74.0060", "")
        assertPlace(40.7128, -74.0060, "", "40.7128, -74.0060")
        assertPlace(40.7128, -74.0060, "40.7128,-74.0060", "")
        assertPlace(40.7128, -74.0060, "40.7128 -74.0060", "")
        assertPlace(40.7128, -74.0060, "40,7128; -74,0060", "")
        assertPlace(48.8566, 2.3522, "48.8566,2.3522", "")
        assertPlace(40.7128, -74.006, "40.7128°, −74.0060°", "")
        assertPlace(40.0, -74.0, "40,-74", "")
    }

    @Test fun aPastedPairReplacesWhateverTheOtherFieldHeld() {
        assertPlace(48.8566, 2.3522, "48,8566; 2,3522", "-74.006")
        assertPlace(48.8566, 2.3522, "40.7", "48.8566, 2.3522")
        assertPlace(-33.8688, 151.2093, "33.8688 S", "151.2093 E")   // a hemisphere letter is not a pair
    }

    @Test fun aSingleCommaNumberIsADecimalNotAPair() = assertPlace(40.7128, 0.0, "40,7128", "0")

    @Test fun badInputIsRefusedWithTheRangeMessage() {
        listOf("" to "", "abc" to "1", "91" to "0", "0" to "181", "-90.5" to "0", "1.2.3" to "4", "40.7" to "").forEach { (a, b) ->
            val r = PlaceInput.parse(a, b); assertTrue("$a / $b", r is PlaceInput.Result.Bad)
            assertEquals(PlaceInput.RANGE_MESSAGE, (r as PlaceInput.Result.Bad).message)
        }
    }

    @Test fun edgesOfTheRangeAreAccepted() { assertPlace(90.0, 180.0, "90", "180"); assertPlace(-90.0, -180.0, "-90", "-180") }

    @Test fun coordinatesAreWrittenWithHemispheres() {
        assertEquals("40.7° N, 74.0° W", PlaceInput.coordinates(40.7128, -74.006))
        assertEquals("33.9° S, 151.2° E", PlaceInput.coordinates(-33.8688, 151.2093))
    }

    @Test fun describeShowsPlaceAndTodaysSunTimes() {
        val text = PlaceInput.describe("New York", 40.7128, -74.006, LocalDate.of(2026, 6, 21), ZoneId.of("America/New_York"), true)
        assertTrue(text, text.startsWith("Saved: New York, 40.7° N, 74.0° W. Sunrise 05:"))
        assertTrue(text, text.contains("sunset 20:"))
    }

    @Test fun describeHandlesPolarDaysAndBlankNames() {
        assertTrue(PlaceInput.describe("", 78.2, 15.6, LocalDate.of(2026, 6, 21), ZoneId.of("Arctic/Longyearbyen"), true)
            .startsWith("Saved: Your place, 78.2° N, 15.6° E. The sun does not set today"))
        assertTrue(PlaceInput.describe("x", 78.2, 15.6, LocalDate.of(2026, 12, 21), ZoneId.of("Arctic/Longyearbyen"), true)
            .contains("does not rise"))
    }
}
