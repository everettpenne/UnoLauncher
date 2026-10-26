package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class WidgetStyleRulesTest {
    @Test fun aWidgetFollowsTheDefaultUnlessItChoseItsOwn() {
        assertEquals(WidgetStyle.CARD, WidgetStyleRules.resolve(null, WidgetStyle.CARD, true))
        assertEquals(WidgetStyle.GLASS, WidgetStyleRules.resolve(null, WidgetStyle.GLASS, true))
        assertEquals(WidgetStyle.GLASS, WidgetStyleRules.resolve(WidgetStyle.GLASS, WidgetStyle.CARD, true))
        assertEquals(WidgetStyle.CARD, WidgetStyleRules.resolve(WidgetStyle.CARD, WidgetStyle.GLASS, true))
    }

    @Test fun withGlassOffEverywhereAWidgetIsAlwaysACard() {
        assertEquals(WidgetStyle.CARD, WidgetStyleRules.resolve(WidgetStyle.GLASS, WidgetStyle.GLASS, false))
    }

    @Test fun theStoredKeyAndValueRoundTrip() {
        assertEquals("widgetStyle_7", WidgetStyleRules.key(7))
        assertEquals(WidgetStyle.GLASS, WidgetStyleRules.parse("GLASS"))
        assertNull(WidgetStyleRules.parse("bogus")); assertNull(WidgetStyleRules.parse(null))
    }

    @Test fun theKeyIsAcceptedByTheSettingsBackup() {
        assertTrue(SettingsBackup.exportable(SettingsBackup.APPEARANCE, WidgetStyleRules.key(12)))
        assertTrue(SettingsBackup.exportable(SettingsBackup.APPEARANCE, "widgetStyle"))
    }
}
