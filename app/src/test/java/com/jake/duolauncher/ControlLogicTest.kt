package com.jake.duolauncher

import android.media.AudioManager
import org.junit.Assert.*
import org.junit.Test

class ControlLogicTest {
    @Test fun volumeRoundTripsAndClamps() {
        assertEquals(0f, ControlLogic.volumeFraction(0, 15), 0f)
        assertEquals(1f, ControlLogic.volumeFraction(15, 15), 0f)
        assertEquals(0f, ControlLogic.volumeFraction(3, 0), 0f)
        (0..15).forEach { v -> assertEquals(v, ControlLogic.volumeFor(ControlLogic.volumeFraction(v, 15), 15)) }
        assertEquals(15, ControlLogic.volumeFor(4f, 15))
        assertEquals(0, ControlLogic.volumeFor(-1f, 15))
    }

    @Test fun brightnessNeverReachesBlackAndHitsBothEnds() {
        assertEquals(ControlLogic.MIN_BRIGHTNESS, ControlLogic.brightnessFor(0f))
        assertEquals(ControlLogic.MAX_BRIGHTNESS, ControlLogic.brightnessFor(1f))
        assertTrue(ControlLogic.brightnessFor(-3f) >= 1)
        assertEquals(ControlLogic.MAX_BRIGHTNESS, ControlLogic.brightnessFor(9f))
    }

    @Test fun brightnessCurveBendsTowardTheDarkEnd() {
        // Half way along the slider is well under half the output.
        assertTrue(ControlLogic.brightnessFor(.5f) < 128 / 2 + 20)
        assertTrue(ControlLogic.brightnessFor(.5f) < ControlLogic.brightnessFor(.75f))
    }

    @Test fun brightnessRoundTripsWithinOneStep() {
        listOf(1, 5, 20, 64, 128, 200, 255).forEach { raw ->
            val back = ControlLogic.brightnessFor(ControlLogic.brightnessFraction(raw))
            assertTrue("raw $raw came back as $back", kotlin.math.abs(back - raw) <= 1)
        }
    }

    @Test fun silentNeedsDoNotDisturbAccessButTheOtherModesDoNot() {
        val normal = AudioManager.RINGER_MODE_NORMAL
        val vibrate = AudioManager.RINGER_MODE_VIBRATE
        val silent = AudioManager.RINGER_MODE_SILENT
        assertEquals(RingerRequest.NEEDS_DND_ACCESS, ControlLogic.ringerRequest(normal, silent, false))
        assertEquals(RingerRequest.SET, ControlLogic.ringerRequest(normal, silent, true))
        assertEquals(RingerRequest.SET, ControlLogic.ringerRequest(silent, normal, false))
        assertEquals(RingerRequest.SET, ControlLogic.ringerRequest(normal, vibrate, false))
        assertEquals(RingerRequest.ALREADY, ControlLogic.ringerRequest(vibrate, vibrate, false))
    }

    @Test fun torchPrefersTheBackCameraWithAFlash() {
        val cameras = listOf(
            TorchCamera("1", hasFlash = false, backFacing = false),
            TorchCamera("2", hasFlash = true, backFacing = false),
            TorchCamera("0", hasFlash = true, backFacing = true))
        assertEquals("0", ControlLogic.pickTorchCamera(cameras))
        assertEquals("2", ControlLogic.pickTorchCamera(cameras.dropLast(1)))
        assertNull(ControlLogic.pickTorchCamera(listOf(TorchCamera("1", false, true))))
        assertNull(ControlLogic.pickTorchCamera(emptyList()))
    }
}
