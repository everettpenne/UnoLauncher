package com.jake.duolauncher

import com.jake.duolauncher.island.*

import org.junit.Assert.*
import org.junit.Test

class LiveUpdateTest {
    private fun update(key: String = "k", title: String = "Ride to the airport", short: String? = null, progress: Int = 0, max: Int = 0,
        indeterminate: Boolean = false, base: Long? = null, down: Boolean = false, post: Long = 0L) =
        LiveUpdate(key, "com.example.ride", "Ride", title, null, short, progress, max, indeterminate, base, down, null, 0, post, null)

    @Test fun onlyTheSystemsPromotedFlagMakesALiveUpdate() {
        assertTrue(LiveUpdateLogic.isLiveUpdate(0x40000)); assertTrue(LiveUpdateLogic.isLiveUpdate(0x40000 or 0x2))
        assertFalse(LiveUpdateLogic.isLiveUpdate(0x2)); assertFalse(LiveUpdateLogic.isLiveUpdate(0))
    }

    @Test fun progressIsAFractionOrNothing() {
        assertEquals(.25f, LiveUpdateLogic.fraction(25, 100, false)!!, .001f)
        assertEquals(1f, LiveUpdateLogic.fraction(150, 100, false)!!, .001f)
        assertNull(LiveUpdateLogic.fraction(5, 0, false)); assertNull(LiveUpdateLogic.fraction(5, 10, true))
    }

    @Test fun timersCountUpOrDownAndNeverGoNegative() {
        assertEquals("1:05", LiveUpdateLogic.timer(0L, false, 65_000L))
        assertEquals("2:00", LiveUpdateLogic.timer(130_000L, true, 10_000L))
        assertEquals("0:00", LiveUpdateLogic.timer(5_000L, true, 90_000L))
        assertEquals("1:01:01", LiveUpdateLogic.timer(0L, false, 3_661_000L))
    }

    @Test fun theGlanceIsTheAppsOwnTextThenTheClockThenThePercentThenTheTitle() {
        assertEquals("12 min", LiveUpdateLogic.glance(update(short = "12 min", base = 0L, progress = 1, max = 2), 1_000L))
        assertEquals("0:30", LiveUpdateLogic.glance(update(base = 0L, progress = 1, max = 2), 30_000L))
        assertEquals("50%", LiveUpdateLogic.glance(update(progress = 1, max = 2), 0L))
        assertEquals("Ride to the ai", LiveUpdateLogic.glance(update(), 0L))
        assertTrue("never wider than the pill allows", LiveUpdateLogic.glance(update(short = "a very long status text indeed"), 0L).length <= 14)
        assertEquals("a blank short text is ignored", "50%", LiveUpdateLogic.glance(update(short = "  ", progress = 1, max = 2), 0L))
    }

    @Test fun updatesAreNewestFirstAtMostThreeAndOnePerKey() {
        val list = LiveUpdateLogic.ordered(listOf(update("a", post = 1), update("b", post = 5), update("c", post = 3), update("d", post = 4), update("b", post = 9)))
        assertEquals(listOf("b", "d", "c"), list.map { it.key })
    }

    @Test fun anUpdateWhoseNotificationIsGoneIsDropped() {
        val a = update("a", post = 1); val b = update("b", post = 2); val c = update("c", post = 3)
        assertEquals(listOf("c", "b", "a"), LiveUpdateLogic.present(listOf(a, b, c), setOf("a", "b", "c")).map { it.key })
        assertEquals(listOf("c", "a"), LiveUpdateLogic.present(listOf(a, b, c), setOf("a", "c")).map { it.key })
        assertTrue(LiveUpdateLogic.present(listOf(a, b, c), emptySet()).isEmpty())
        assertTrue(LiveUpdateLogic.present(emptyList(), setOf("a")).isEmpty())
        assertTrue("the safety sweep is a positive interval", LiveUpdateLogic.SAFETY_SWEEP_MS > 0L)
    }

    @Test fun onlyAClockWithoutShortTextNeedsASecondTick() {
        assertTrue(LiveUpdateLogic.needsSecondTick(listOf(update(base = 0L))))
        assertFalse(LiveUpdateLogic.needsSecondTick(listOf(update(base = 0L, short = "12 min"))))
        assertFalse(LiveUpdateLogic.needsSecondTick(listOf(update())))
    }

    @Test fun aLiveUpdateRanksAfterTheStopwatchAndBeforeMusic() {
        val all = IslandLive.active(call = true, timer = true, stopwatch = true, media = true, update = true)
        assertEquals(listOf(LiveKind.CALL, LiveKind.TIMER, LiveKind.STOPWATCH, LiveKind.UPDATE, LiveKind.MEDIA), all)
        // With music as well, the update is the one in full on the right and the music is the glyph on the left.
        val plan = IslandLive.plan(IslandLive.active(false, false, false, media = true, update = true))
        assertEquals(LiveKind.MEDIA to SlotShow.GLYPH, plan.leading); assertEquals(LiveKind.UPDATE to SlotShow.DETAIL, plan.trailing)
    }

    @Test fun theLedgerSaysLiveUpdatesReadOnlyTitleStatusProgressAndIcon() {
        val notifications = PermissionLedger.rows(LedgerInputs(false, false, false, true, false, false)).first { it.id == "notifications" }
        assertTrue(notifications.usedFor.contains("Live Updates")); assertTrue(notifications.canSee.contains("progress"))
        assertTrue(notifications.canSee.contains("only if you choose it") && notifications.canSee.contains("opt-in"))
    }
}

class ActivityClassifyTest {
    private fun c(promoted: Boolean = false, category: String? = null, ongoing: Boolean = true, progress: Int = 0, max: Int = 0,
        indeterminate: Boolean = false, live: Boolean = true, nav: Boolean = true, tasks: Boolean = true) =
        LiveUpdateLogic.classify(promoted, category, ongoing, progress, max, indeterminate, live, nav, tasks)

    @Test fun aPromotedNotificationIsALiveUpdateOnlyWhileThatSwitchIsOn() {
        org.junit.Assert.assertEquals(ActivitySource.PROMOTED, c(promoted = true))
        org.junit.Assert.assertNull(c(promoted = true, live = false))
    }

    @Test fun anOngoingNavigationNotificationIsNavigation() {
        org.junit.Assert.assertEquals(ActivitySource.NAVIGATION, c(category = "navigation"))
        org.junit.Assert.assertNull(c(category = "navigation", nav = false))
        org.junit.Assert.assertNull(c(category = "navigation", ongoing = false))
    }

    @Test fun aDownloadIsAProgressTask() {
        org.junit.Assert.assertEquals(ActivitySource.PROGRESS, c(progress = 40, max = 100))
        org.junit.Assert.assertEquals(ActivitySource.PROGRESS, c(category = "progress", progress = 1, max = 3))
    }

    @Test fun notEverythingWithABarIsATask() {
        org.junit.Assert.assertNull(c(progress = 40, max = 100, tasks = false))
        org.junit.Assert.assertNull(c(progress = 40, max = 100, ongoing = false))
        org.junit.Assert.assertNull(c(progress = 0, max = 100, indeterminate = true))
        org.junit.Assert.assertNull(c(progress = 100, max = 100))          // finished
        org.junit.Assert.assertNull(c(progress = 5, max = 0))              // no bar
        listOf("call", "transport", "alarm", "msg", "email", "social").forEach {
            org.junit.Assert.assertNull("$it is not a task", c(category = it, progress = 5, max = 10))
        }
    }

    @Test fun ongoingMeansOngoingOrForegroundService() {
        org.junit.Assert.assertTrue(LiveUpdateLogic.isOngoing(0x2))
        org.junit.Assert.assertTrue(LiveUpdateLogic.isOngoing(0x40))
        org.junit.Assert.assertFalse(LiveUpdateLogic.isOngoing(0x10))
    }
}
