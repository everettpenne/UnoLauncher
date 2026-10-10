package com.jake.duolauncher

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IslandExtrasTest {
    @Test fun bluetoothAndUsbDevicesUseTheirOwnName() {
        assertEquals("Pixel Buds Pro", AudioDevices.label(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, " Pixel Buds Pro "))
        assertEquals("Bluetooth audio", AudioDevices.label(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, ""))
        assertEquals("USB headset", AudioDevices.label(AudioDeviceInfo.TYPE_USB_HEADSET, null))
        assertEquals("Hearing aid", AudioDevices.label(AudioDeviceInfo.TYPE_HEARING_AID, null))
    }

    @Test fun wiredOutputsAreHeadphonesAndSpeakersAreIgnored() {
        assertEquals("Headphones", AudioDevices.label(AudioDeviceInfo.TYPE_WIRED_HEADSET, "whatever"))
        assertEquals("Headphones", AudioDevices.label(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, null))
        assertNull(AudioDevices.label(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Speaker"))
        assertNull(AudioDevices.label(AudioDeviceInfo.TYPE_HDMI, null))
    }

    @Test fun audioEventWording() {
        assertEquals("Buds connected", IslandEvents.audioDevice(true, "Buds").title)
        assertEquals("Buds disconnected", IslandEvents.audioDevice(false, "Buds").title)
        assertEquals(IslandSymbol.HEADPHONES, IslandEvents.audioDevice(true, "Buds").symbol)
    }

    @Test fun chargeTimeWording() {
        assertEquals("40 min to full", ChargeText.toFull(40 * 60_000L))
        assertEquals("1 h to full", ChargeText.toFull(60 * 60_000L))
        assertEquals("1 h 5 min to full", ChargeText.toFull(65 * 60_000L))
        assertEquals("1 min to full", ChargeText.toFull(61_000L))
        assertNull(ChargeText.toFull(-1))
        assertNull(ChargeText.toFull(30_000L))
        assertNull(ChargeText.toFull(48L * 60 * 60_000L))
    }

    @Test fun recordingIsAPrivacyMarkAheadOfOrdinaryActivities() {
        val kinds = IslandLive.active(camera = false, mic = true, call = true, timer = false, stopwatch = false, media = true, recording = true)
        assertEquals(listOf(LiveKind.MIC, LiveKind.RECORDING, LiveKind.CALL, LiveKind.MEDIA), kinds)
        assertEquals(true, LiveKind.RECORDING.privacy)
    }
}
