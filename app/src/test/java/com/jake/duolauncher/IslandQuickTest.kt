package com.jake.duolauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IslandQuickTest {
    @Test fun personalAppsOnlyInOrderWithoutRepeatsAndCapped() {
        val ids = listOf("com.a/.Main", "duo-profile:v1:10:com.work/.Main", "com.b/.Main", "com.a/.Main", "junk", "com.c/.Main", "com.d/.Main", "com.e/.Main", "com.f/.Main")
        val parsed = IslandQuick.components(ids)
        assertEquals(listOf("com.a/.Main", "com.b/.Main", "com.c/.Main", "com.d/.Main", "com.e/.Main"), parsed)
        assertEquals(IslandQuick.MAX, parsed.size)
    }

    @Test fun anEmptyOrBrokenListGivesNothing() {
        assertEquals(emptyList<String>(), IslandQuick.components(emptyList()))
        assertEquals(emptyList<String>(), IslandQuick.components(listOf("", "duo-profile:v1:abc:x/.y", "nocomponent")))
    }

    @Test fun onlyAnOngoingCallHasAnEndAction() {
        assertEquals(0, CallActions.hangUpIndex(CallActions.CALL_TYPE_ONGOING, 2))
        assertNull(CallActions.hangUpIndex(CallActions.CALL_TYPE_ONGOING, 0))
        assertNull(CallActions.hangUpIndex(1, 2))   // incoming
        assertNull(CallActions.hangUpIndex(3, 2))   // screening
        assertNull(CallActions.hangUpIndex(0, 1))   // no call type
    }
}
