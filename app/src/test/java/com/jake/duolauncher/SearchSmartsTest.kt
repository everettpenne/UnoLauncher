package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class SearchSmartsTest {
    private fun text(q: String) = SearchSmarts.answer(q)?.text

    @Test fun basicArithmeticAndPrecedence() {
        assertEquals("84", text("12*(3+4)"))
        assertEquals("14", text("2+3*4"))
        assertEquals("20", text("(2+3)*4"))
        assertEquals("512", text("2^9"))
        assertEquals("256", text("2^2^3"))   // right-associative: 2^(2^3)
        assertEquals("2.5", text("5/2"))
        assertEquals("-3", text("2-5"))
        assertEquals("3.5", text("7 ÷ 2"))
        assertEquals("12", text("3 × 4"))
    }

    @Test fun decimalsAreTrimmedAndLongOnesRounded() {
        assertEquals("0.3", text("0.1+0.2"))
        assertEquals("3.333333333", text("10/3"))
        assertEquals("1000000", text("1000*1000"))
    }

    @Test fun percentsWorkTheWayACalculatorDoes() {
        assertEquals("12", text("15% of 80"))
        assertEquals("55", text("50+10%"))
        assertEquals("45", text("50-10%"))
        assertEquals("0.5", text("50%"))
    }

    @Test fun functionsAndConstants() {
        assertEquals("4", text("sqrt(16)"))
        assertEquals("1", text("sin(90)"))
        assertEquals("2", text("log(100)"))
        assertEquals("6.283185307", text("2*pi"))
        assertEquals("5", text("abs(-5)+0"))
    }

    @Test fun badMathIsNotAnAnswer() {
        assertNull(text("1/0"))
        assertNull(text("sqrt(-4)"))
        assertNull(text("(2+3"))
        assertNull(text("2+"))
        assertNull(text("foo(3)"))
    }

    @Test fun appNamesAndBareNumbersAreLeftAlone() {
        listOf("7-zip", "2048", "chrome", "e", "pi", "-5", "1password", "5", "signal", "").forEach {
            assertNull("'$it' should stay an app search", text(it))
        }
    }

    @Test fun lengthMassVolumeAndMore() {
        assertEquals("3.106856 mi", text("5 km in mi"))
        assertEquals("30.48 cm", text("1 ft to cm"))
        assertEquals("1 kg", text("2.2046226 lb to kg"))
        assertEquals("0.4535924 kg", text("1 lb in kg"))
        assertEquals("3.785412 L", text("1 gal to l"))
        assertEquals("1.609344 km/h", text("1 mph to km/h"))
        assertEquals("1.5 h", text("90 min in hours"))
        assertEquals("1000 MB", text("1 gb to mb"))
    }

    @Test fun temperatureUsesOffsetsNotRatios() {
        assertEquals("100 °C", text("212 f to c"))
        assertEquals("32 °F", text("0 c in f"))
        assertEquals("273.15 K", text("0 c to k"))
        assertEquals("-40 °F", text("-40 c to f"))
    }

    @Test fun mixedKindsAndUnknownUnitsGiveNothing() {
        assertNull(text("5 km in kg"))
        assertNull(text("5 parsecs in mi"))
        assertNull(text("5 km in"))
    }

    @Test fun formatSwitchesToScientificOnlyAtTheExtremes() {
        assertEquals("1e+20", SearchSmarts.format(1e20))
        assertEquals("123456789", SearchSmarts.format(123456789.0))
        assertEquals("0", SearchSmarts.format(0.0))
    }
}
