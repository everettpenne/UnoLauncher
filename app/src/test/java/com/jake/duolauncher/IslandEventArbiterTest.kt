package com.jake.duolauncher

import com.jake.duolauncher.island.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "one home per event" ladder (docs/design.md, Metaphor policy): which launcher surface owns each
 * event, so nothing visibly fires twice at the same moment. */
class IslandEventArbiterTest {
    // --- notification surface ---

    @Test fun aCardIsDroppedOnlyWhenTheUserOptedIntoTitling() {
        assertEquals(IslandEventArbiter.NotificationSurface.CARD,
            IslandEventArbiter.notificationSurface(hasTitleOrText = true, cardAlreadyDown = false, shadeOpenedAtMs = 0L, nowMs = 1_000L))
        assertEquals(IslandEventArbiter.NotificationSurface.PILL,
            IslandEventArbiter.notificationSurface(hasTitleOrText = false, cardAlreadyDown = false, shadeOpenedAtMs = 0L, nowMs = 1_000L))
    }

    @Test fun aNameOnlyFlashDoesNotFireOnTopOfACardAlreadyDown() {
        assertEquals(IslandEventArbiter.NotificationSurface.NONE,
            IslandEventArbiter.notificationSurface(hasTitleOrText = false, cardAlreadyDown = true, shadeOpenedAtMs = 0L, nowMs = 1_000L))
        // A title/text notification replaces the card, so it still drops.
        assertEquals(IslandEventArbiter.NotificationSurface.CARD,
            IslandEventArbiter.notificationSurface(hasTitleOrText = true, cardAlreadyDown = true, shadeOpenedAtMs = 0L, nowMs = 1_000L))
    }

    @Test fun aNotificationJustShownByTheShadeIsNotReAnnounced() {
        val opened = 10_000L
        assertEquals(IslandEventArbiter.NotificationSurface.NONE,
            IslandEventArbiter.notificationSurface(hasTitleOrText = true, cardAlreadyDown = false, shadeOpenedAtMs = opened, nowMs = opened + 1))
        assertEquals(IslandEventArbiter.NotificationSurface.NONE,
            IslandEventArbiter.notificationSurface(hasTitleOrText = false, cardAlreadyDown = false, shadeOpenedAtMs = opened, nowMs = opened + IslandEventArbiter.SHADE_ANNOUNCE_COOLDOWN_MS - 1))
    }

    @Test fun afterTheShadeCooldownTheIslandOwnsTheNotificationAgain() {
        val opened = 10_000L
        val now = opened + IslandEventArbiter.SHADE_ANNOUNCE_COOLDOWN_MS
        assertEquals(IslandEventArbiter.NotificationSurface.CARD,
            IslandEventArbiter.notificationSurface(hasTitleOrText = true, cardAlreadyDown = false, shadeOpenedAtMs = opened, nowMs = now))
        assertEquals(IslandEventArbiter.NotificationSurface.PILL,
            IslandEventArbiter.notificationSurface(hasTitleOrText = false, cardAlreadyDown = false, shadeOpenedAtMs = opened, nowMs = now))
    }

    @Test fun whileSpotlightIsOpenNoNotificationDropsACardOrFlashes() {
        // Spotlight owns the screen: neither a titled card (which would auto-expand the island) nor a name-only flash
        // fires underneath the search.
        assertEquals(IslandEventArbiter.NotificationSurface.NONE,
            IslandEventArbiter.notificationSurface(hasTitleOrText = true, cardAlreadyDown = false, shadeOpenedAtMs = 0L, nowMs = 1_000L, spotlightOpen = true))
        assertEquals(IslandEventArbiter.NotificationSurface.NONE,
            IslandEventArbiter.notificationSurface(hasTitleOrText = false, cardAlreadyDown = false, shadeOpenedAtMs = 0L, nowMs = 1_000L, spotlightOpen = true))
    }

    @Test fun whenSpotlightClosesTheIslandOwnsNotificationsAgain() {
        assertEquals(IslandEventArbiter.NotificationSurface.CARD,
            IslandEventArbiter.notificationSurface(hasTitleOrText = true, cardAlreadyDown = false, shadeOpenedAtMs = 0L, nowMs = 1_000L, spotlightOpen = false))
        assertEquals(IslandEventArbiter.NotificationSurface.PILL,
            IslandEventArbiter.notificationSurface(hasTitleOrText = false, cardAlreadyDown = false, shadeOpenedAtMs = 0L, nowMs = 1_000L, spotlightOpen = false))
    }

    // --- clock and battery owners ---

    @Test fun theRailOwnsTimeWhenShownElseTheOpenPanelElseNothing() {
        assertEquals(IslandEventArbiter.TimeOwner.RAIL, IslandEventArbiter.timeOwner(statusRailShown = true, islandExpanded = false))
        assertEquals(IslandEventArbiter.TimeOwner.PANEL, IslandEventArbiter.timeOwner(statusRailShown = false, islandExpanded = true))
        assertEquals(IslandEventArbiter.TimeOwner.NONE, IslandEventArbiter.timeOwner(statusRailShown = false, islandExpanded = false))
    }

    @Test fun theRailOwnsTheBatteryLevelWhenShown() {
        assertEquals(IslandEventArbiter.BatteryOwner.RAIL, IslandEventArbiter.batteryLevelOwner(statusRailShown = true))
        assertEquals(IslandEventArbiter.BatteryOwner.ISLAND, IslandEventArbiter.batteryLevelOwner(statusRailShown = false))
    }

    @Test fun theIslandDrawsAChargingLevelRingOnlyWhenTheRailIsOff() {
        assertFalse(IslandEventArbiter.chargingRingSurface(statusRailShown = true, charging = true))
        assertTrue(IslandEventArbiter.chargingRingSurface(statusRailShown = false, charging = true))
        assertFalse(IslandEventArbiter.chargingRingSurface(statusRailShown = false, charging = false))
    }

    // --- the island pill owns its own pull ---

    @Test fun aPressOnTheCollapsedPillIsTheIslandsPullAlone() {
        val pill = IslandFrame(left = 100f, top = 4f, width = 90f, height = 30f, hole = null)
        assertTrue(IslandEventArbiter.pressOnIslandPill(pill, 100f, 4f))
        assertTrue(IslandEventArbiter.pressOnIslandPill(pill, 190f, 34f))
        assertTrue(IslandEventArbiter.pressOnIslandPill(pill, 145f, 19f))
    }

    @Test fun aPressJustOffThePillIsNotTheIslands() {
        val pill = IslandFrame(left = 100f, top = 4f, width = 90f, height = 30f, hole = null)
        assertFalse(IslandEventArbiter.pressOnIslandPill(pill, 99f, 19f))
        assertFalse(IslandEventArbiter.pressOnIslandPill(pill, 191f, 19f))
        assertFalse(IslandEventArbiter.pressOnIslandPill(pill, 145f, 3f))
        assertFalse(IslandEventArbiter.pressOnIslandPill(pill, 145f, 35f))
    }

    @Test fun withoutAPillNothingIsExcluded() {
        assertFalse(IslandEventArbiter.pressOnIslandPill(null, 0f, 0f))
    }
}
