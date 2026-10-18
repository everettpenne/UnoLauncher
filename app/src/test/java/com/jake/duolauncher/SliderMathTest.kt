package com.jake.duolauncher

import com.jake.duolauncher.panel.*

import org.junit.Assert.*
import org.junit.Test

class SliderMathTest {
    @Test fun fractionMapsTheEndsAndTheMiddle() {
        assertEquals(0f, SliderMath.fraction(40f, 40f..68f), 0f)
        assertEquals(1f, SliderMath.fraction(68f, 40f..68f), 0f)
        assertEquals(.5f, SliderMath.fraction(54f, 40f..68f), 0f)
        assertEquals(.25f, SliderMath.fraction(.25f, 0f..1f), 0f)
    }

    @Test fun fractionClampsOutOfRangeValues() {
        assertEquals(0f, SliderMath.fraction(10f, 40f..68f), 0f)
        assertEquals(1f, SliderMath.fraction(100f, 40f..68f), 0f)
    }

    @Test fun aDegenerateRangeNeverDividesByZero() {
        assertEquals(0f, SliderMath.fraction(5f, 3f..3f), 0f)
        assertEquals(0f, SliderMath.fraction(5f, 7f..3f), 0f)
    }

    @Test fun valueAtIsTheInverseOfFraction() {
        val range = 40f..68f
        listOf(40f, 48f, 54f, 60f, 68f).forEach { value ->
            assertEquals(value, SliderMath.valueAt(SliderMath.fraction(value, range), range), .0001f)
        }
        assertEquals(40f, SliderMath.valueAt(0f, range), 0f)
        assertEquals(68f, SliderMath.valueAt(1f, range), 0f)
        assertEquals(40f, SliderMath.valueAt(-2f, range), 0f)
        assertEquals(68f, SliderMath.valueAt(9f, range), 0f)
    }

    @Test fun thumbSitsAtTheEndsAndKeepsItsWholePillInside() {
        assertEquals(0f, SliderMath.thumbX(0f, 400f, 40f), 0f)
        assertEquals(360f, SliderMath.thumbX(1f, 400f, 40f), 0f)
        assertEquals(180f, SliderMath.thumbX(.5f, 400f, 40f), 0f)
        assertEquals(360f, SliderMath.thumbX(5f, 400f, 40f), 0f)
    }

    @Test fun fractionAndThumbAgreeForAnyRangeAndTrack() {
        // A thumb drawn from thumbX(fraction(value)) lands exactly on the value's share of the track.
        val range = 12f..88f
        listOf(12f, 20f, 33f, 50f, 71f, 88f).forEach { value ->
            val f = SliderMath.fraction(value, range)
            assertEquals(f * 360f, SliderMath.thumbX(f, 400f, 40f), .0001f)
        }
    }
}
