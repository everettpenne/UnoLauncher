package com.jake.duolauncher

import com.jake.duolauncher.island.*

import java.io.File
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

    @Test fun aQuietUpdateGoesStaleButNavigationAndClocksDoNot() {
        val now = 1_000_000L
        assertTrue(LiveUpdateLogic.isStale(update(post = now - LiveUpdateLogic.STALE_MS - 1), now))
        assertFalse(LiveUpdateLogic.isStale(update(post = now - 5_000L), now))
        // A navigation instruction (reposted only when the manoeuvre changes) stays fresh however old the post.
        val nav = LiveUpdate("k", "com.example", "Maps", "Turn left", "Main St", null, 0, 0, false, null, false, null, 0,
            now - LiveUpdateLogic.STALE_MS - 1, null, source = ActivitySource.NAVIGATION)
        assertFalse(LiveUpdateLogic.isStale(nav, now))
        // A counting clock is still moving, so it is never stale.
        assertFalse(LiveUpdateLogic.isStale(update(post = now - LiveUpdateLogic.STALE_MS - 1, base = 0L), now))
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

/** The reference parser: the spec's notification examples mapped through [LiveUpdateLogic.build], asserting every field lands in the
 * right place. These run on the JVM, apart from the Android notification service. */
class LiveUpdateParseTest {
    private fun parse(
        key: String = "k",
        packageName: String = "com.example.ride",
        appLabel: String = "Ride",
        postTime: Long = 1_700_000_000_000L,
        source: ActivitySource = ActivitySource.PROMOTED,
        promoted: Boolean = true,
        category: String? = null,
        title: String = "Ride to the airport",
        shortCriticalText: String? = "4 min",
        text: String? = "Driver is 4 minutes away",
        progress: Int = 40,
        progressMax: Int = 100,
        indeterminate: Boolean = false,
        chronometerBase: Long? = null,
        countDown: Boolean = false,
        color: Int = 0xFF1FA84A.toInt(),
    ) = LiveUpdateLogic.build(key, packageName, appLabel, postTime, source, promoted, category, title,
        shortCriticalText, text, progress, progressMax, indeterminate, chronometerBase, countDown, color)

    @Test fun theRideShareExampleMapsEveryFieldToTheRightPlace() {
        val u = parse()
        assertEquals("k", u.key)
        assertEquals("com.example.ride", u.packageName)
        assertEquals("Ride", u.appLabel)
        assertEquals("Ride to the airport", u.title)
        assertEquals("4 min", u.shortText)
        assertEquals("Driver is 4 minutes away", u.text)
        assertEquals(40, u.progress)
        assertEquals(100, u.progressMax)
        assertFalse(u.indeterminate)
        assertEquals(ActivitySource.PROMOTED, u.source)
        assertEquals(0xFF1FA84A.toInt(), u.color)
        assertEquals(1_700_000_000_000L, u.postTime)
        // Context-derived values stay out of the pure path; the caller fills them in.
        assertNull(u.icon); assertNull(u.openIntent); assertNull(u.largeIcon)
    }

    @Test fun theDeliveryExampleMapsItsOwnFields() {
        val u = parse(
            key = "d", packageName = "com.example.delivery", appLabel = "Courier",
            title = "Order arriving", shortCriticalText = "2 stops", text = "Your order is next",
            progress = 2, progressMax = 5, color = 0xFFE02020.toInt(),
        )
        assertEquals("Order arriving", u.title)
        assertEquals("2 stops", u.shortText)
        assertEquals("Your order is next", u.text)
        assertEquals(2, u.progress)
        assertEquals(5, u.progressMax)
        assertEquals(ActivitySource.PROMOTED, u.source)
        assertEquals(0xFFE02020.toInt(), u.color)
    }

    @Test fun theBodyAndShortStatusAreKeptOnlyForTheirOwnKinds() {
        // A progress task shows its percentage, not the app's body text (which may name a file), and has no short status.
        val task = parse(source = ActivitySource.PROGRESS, promoted = false, category = "progress", shortCriticalText = null, text = "firmware.zip")
        assertNull(task.text); assertNull(task.shortText); assertEquals(ActivitySource.PROGRESS, task.source)
        // A navigation instruction keeps its body line (the instruction under the arrow) but has no short status.
        val nav = parse(source = ActivitySource.NAVIGATION, promoted = false, category = "navigation", shortCriticalText = null, text = "Main St")
        assertEquals("Main St", nav.text); assertNull(nav.shortText); assertEquals(ActivitySource.NAVIGATION, nav.source)
    }

    @Test fun promotedIsRecognisedByTheFlagAndClassifiedAsALiveUpdate() {
        assertTrue(LiveUpdateLogic.isLiveUpdate(LiveUpdateLogic.PROMOTED_FLAG))
        assertFalse(LiveUpdateLogic.isLiveUpdate(0))
        assertEquals(ActivitySource.PROMOTED,
            LiveUpdateLogic.classify(true, null, true, 0, 0, false, liveUpdatesOn = true, navigationOn = true, tasksOn = true))
    }

    @Test fun everyReadFieldNameAppearsInPrivacyDotMd() {
        val doc = File("../PRIVACY.md").takeIf { it.exists() } ?: File("PRIVACY.md")
        assertTrue("PRIVACY.md not found from ${System.getProperty("user.dir")}", doc.isFile)
        val text = doc.readText()
        LiveUpdateLogic.READ_FIELDS.forEach { field ->
            assertTrue("\"$field\" is missing from PRIVACY.md", text.contains(field))
        }
    }
}
