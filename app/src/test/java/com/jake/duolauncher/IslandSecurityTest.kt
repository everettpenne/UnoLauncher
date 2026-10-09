package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class IslandSecurityTest {
    @Test fun aDataLinkNeedsAHostAConfiguredPortAndADataFunction() {
        assertTrue(UsbState.dataActive(true, true, setOf("mtp")))
        assertTrue(UsbState.dataActive(true, true, setOf("adb")))
        assertFalse("charging only: connected but no data function", UsbState.dataActive(true, true, emptySet()))
        assertFalse("not configured yet", UsbState.dataActive(true, false, setOf("mtp")))
        assertFalse("unplugged", UsbState.dataActive(false, true, setOf("mtp")))
        assertFalse("an unknown extra is not a data function", UsbState.dataActive(true, true, setOf("unlocked", "host_connected")))
    }

    private fun inputs(overlay: Boolean = false, enabled: Boolean = false, running: Boolean = false, notif: Boolean = false,
        contacts: Boolean = false, everywhere: Boolean = false) = LedgerInputs(overlay, enabled, running, notif, contacts, everywhere)

    private fun row(i: LedgerInputs, id: String) = PermissionLedger.rows(i).first { it.id == id }

    @Test fun theLedgerListsEveryPermissionTheLauncherUses() {
        assertEquals(listOf("overlay", "accessibility", "notifications", "contacts", "indicators"),
            PermissionLedger.rows(inputs()).map { it.id })
    }

    @Test fun anAccessibilityServiceThatIsOnButNotRunningIsCalledOutAsBlocked() {
        val r = row(inputs(enabled = true, running = false), "accessibility")
        assertEquals("Turned on, but not running", r.status)
        assertFalse(r.on)
        assertTrue("explains Advanced Protection", r.usedFor.contains("Advanced Protection"))
        assertEquals("Running", row(inputs(enabled = true, running = true), "accessibility").status)
        assertEquals("Off", row(inputs(), "accessibility").status)
    }

    @Test fun everyRowSaysWhatItCanSeeAndNoRowClaimsToReadMessages() {
        PermissionLedger.rows(inputs()).forEach { assertTrue(it.id, it.canSee.isNotBlank() && it.usedFor.isNotBlank()) }
        assertTrue(row(inputs(), "notifications").canSee.contains("Never the text of a message"))
    }

    @Test fun theSummaryTellsYouWhichIslandYouHave() {
        assertEquals("The island is on Home only.", PermissionLedger.islandSummary(inputs(everywhere = false)))
        assertTrue(PermissionLedger.islandSummary(inputs(everywhere = true, running = true)).contains("can be tapped"))
        assertTrue(PermissionLedger.islandSummary(inputs(everywhere = true, overlay = true)).contains("cannot be tapped"))
        assertTrue(PermissionLedger.islandSummary(inputs(everywhere = true)).contains("Home only"))
    }

    @Test fun vpnAndUsbEventsReadPlainly() {
        assertEquals("VPN on", IslandEvents.vpn(true).title)
        assertEquals("VPN off", IslandEvents.vpn(false).title)
        assertEquals(IslandSymbol.VPN, IslandEvents.vpn(true).symbol)
        assertEquals("USB data connected", IslandEvents.usbData(true).title)
        assertEquals("USB data off", IslandEvents.usbData(false).title)
        assertEquals(IslandSymbol.USB, IslandEvents.usbData(true).symbol)
    }

    @Test fun theControlsStayWhenTheClockIsABitBehindThePause() {
        // The island's clock ticks every 5 s, so right after a pause it can be older than the moment the pause was noticed. That used to
        // read as a negative time and hide the controls until the next tick.
        assertTrue(IslandPlayback.controlsVisible(nowMs = 100_000L, playing = false, lastPlayingAtMs = 103_000L))
        assertTrue(IslandPlayback.controlsVisible(100_000L, false, 100_000L))
        assertTrue(IslandPlayback.controlsVisible(100_000L + IslandPlayback.RESUME_WINDOW_MS, false, 100_000L))
        assertFalse(IslandPlayback.controlsVisible(100_001L + IslandPlayback.RESUME_WINDOW_MS, false, 100_000L))
        assertFalse("never played", IslandPlayback.controlsVisible(100_000L, false, 0L))
        assertTrue(IslandPlayback.controlsVisible(100_000L, true, 0L))
    }
}
