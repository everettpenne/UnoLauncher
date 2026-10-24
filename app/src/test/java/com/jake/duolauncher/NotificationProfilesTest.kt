package com.jake.duolauncher

import android.os.UserManager
import org.junit.Assert.assertEquals
import org.junit.Test

/** Notifications from the work profile and Private Space stay with their own profile's apps (roadmap: per-profile awareness). */
class NotificationProfilesTest {
    @Test fun aWorkNotificationBadgesOnlyTheWorkCopyOfTheApp() {
        val counts = BadgeLogic.counts(listOf(
            ListedNotification("com.slack", ongoing = false, groupSummary = false),
            ListedNotification("com.slack", ongoing = false, groupSummary = false, userSerial = 10),
            ListedNotification("com.slack", ongoing = false, groupSummary = false, userSerial = 10),
        ))
        assertEquals(1, counts[NotificationProfiles.badgeKey("com.slack", null)])
        assertEquals(2, counts[NotificationProfiles.badgeKey("com.slack", 10)])
    }

    @Test fun thePersonalKeyIsThePlainPackageSoExistingBadgesAreUnchanged() {
        assertEquals("com.slack", NotificationProfiles.badgeKey("com.slack", null))
        assertEquals("10:com.slack", NotificationProfiles.badgeKey("com.slack", 10))
    }

    @Test fun aCardFromAnotherProfileSaysWhichOne() {
        assertEquals("Slack", NotificationProfiles.label("Slack", null))
        assertEquals("Slack (Work)", NotificationProfiles.label("Slack", NotificationProfiles.profileName(UserManager.USER_TYPE_PROFILE_MANAGED)))
        assertEquals("Signal (Private)", NotificationProfiles.label("Signal", NotificationProfiles.profileName(UserManager.USER_TYPE_PROFILE_PRIVATE)))
        assertEquals("Work", NotificationProfiles.profileName(null))
    }
}
