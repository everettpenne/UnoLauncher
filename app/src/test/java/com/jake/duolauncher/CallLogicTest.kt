package com.jake.duolauncher

import android.app.Notification
import org.junit.Assert.*
import org.junit.Test

class CallLogicTest {
    @Test fun liveCallNotificationsYieldTheCaller() {
        assertEquals("Mom", CallLogic.callerFrom(Notification.CATEGORY_CALL, "Mom", true,
            "com.android.dialer", "com.jake.duolauncher"))
    }

    @Test fun nonOngoingOrForeignCategoriesAreIgnored() {
        assertNull(CallLogic.callerFrom(Notification.CATEGORY_CALL, "Mom", false,
            "com.android.dialer", "com.jake.duolauncher"))
        assertNull(CallLogic.callerFrom(Notification.CATEGORY_MESSAGE, "Mom", true,
            "com.android.dialer", "com.jake.duolauncher"))
        assertNull(CallLogic.callerFrom(null, "Mom", true, "com.android.dialer", "com.jake.duolauncher"))
        assertNull(CallLogic.callerFrom(Notification.CATEGORY_CALL, "Mom", true,
            "com.jake.duolauncher", "com.jake.duolauncher"))
    }

    @Test fun blankAndSuspiciouslyLongTitlesAreIgnored() {
        assertNull(CallLogic.callerFrom(Notification.CATEGORY_CALL, "   ", true,
            "com.android.dialer", "com.jake.duolauncher"))
        assertNull(CallLogic.callerFrom(Notification.CATEGORY_CALL, "x".repeat(81), true,
            "com.android.dialer", "com.jake.duolauncher"))
        assertEquals("x".repeat(80), CallLogic.callerFrom(Notification.CATEGORY_CALL, "x".repeat(80),
            true, "com.android.dialer", "com.jake.duolauncher"))
    }
}
