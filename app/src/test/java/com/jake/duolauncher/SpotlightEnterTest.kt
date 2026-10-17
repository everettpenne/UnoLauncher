package com.jake.duolauncher

import com.jake.duolauncher.search.SpotlightEnter

import org.junit.Assert.*
import org.junit.Test

class SpotlightEnterTest {
    @Test fun enterPrefersTheTopAppThenActionThenContactThenWeb() {
        assertEquals(SpotlightEnter.Route.APP, SpotlightEnter.route(hasApps = true, hasQuick = true, hasContacts = true, queryBlank = false))
        assertEquals(SpotlightEnter.Route.QUICK, SpotlightEnter.route(hasApps = false, hasQuick = true, hasContacts = true, queryBlank = false))
        assertEquals(SpotlightEnter.Route.CONTACT, SpotlightEnter.route(hasApps = false, hasQuick = false, hasContacts = true, queryBlank = false))
        assertEquals(SpotlightEnter.Route.WEB, SpotlightEnter.route(hasApps = false, hasQuick = false, hasContacts = false, queryBlank = false))
        assertEquals(SpotlightEnter.Route.NONE, SpotlightEnter.route(hasApps = false, hasQuick = false, hasContacts = false, queryBlank = true))
    }
}
