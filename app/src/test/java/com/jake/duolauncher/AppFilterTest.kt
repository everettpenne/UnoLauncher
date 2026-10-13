package com.jake.duolauncher

import com.jake.duolauncher.appearance.AppFilter
import org.junit.Assert.*
import org.junit.Test
import org.kxml2.io.KXmlParser

class AppFilterTest {
    private fun parse(xml: String) = KXmlParser().apply { setInput(xml.reader()) }.let { AppFilter.parse(it) }

    @Test fun componentsAreNormalised() {
        assertEquals("com.android.chrome/com.google.android.apps.chrome.Main",
            AppFilter.componentKey("ComponentInfo{com.android.chrome/com.google.android.apps.chrome.Main}"))
        assertEquals("org.fdroid.fdroid/org.fdroid.fdroid.views.main.MainActivity",
            AppFilter.componentKey("ComponentInfo{org.fdroid.fdroid/.views.main.MainActivity}"))
        assertEquals("a.b/a.b.C", AppFilter.componentKey(" a.b/a.b.C "))
        listOf("", "ComponentInfo{}", "ComponentInfo{com.foo}", "ComponentInfo{/x}", "ComponentInfo{com.foo/}", "com foo/bar").forEach {
            assertNull(it, AppFilter.componentKey(it))
        }
    }

    @Test fun itemsAreReadAndTheFirstMappingWins() {
        val map = parse("""<?xml version="1.0" encoding="utf-8"?>
            <resources>
              <iconback img1="back" />
              <item component="ComponentInfo{com.android.chrome/com.google.android.apps.chrome.Main}" drawable="chrome" />
              <item component="ComponentInfo{com.android.chrome/com.google.android.apps.chrome.Main}" drawable="chrome_alt" />
              <item component="ComponentInfo{org.fdroid.fdroid/.views.main.MainActivity}" drawable="fdroid" />
              <item component=":CALENDAR" drawable="calendar" />
              <item component="ComponentInfo{com.x/com.x.Y}" drawable="" />
              <item drawable="orphan" />
            </resources>""")
        assertEquals(mapOf("com.android.chrome/com.google.android.apps.chrome.Main" to "chrome",
            "org.fdroid.fdroid/org.fdroid.fdroid.views.main.MainActivity" to "fdroid"), map)
    }

    @Test fun aHugeFileIsCutOffNotReadForever() {
        val items = (0 until AppFilter.MAX_ITEMS + 50).joinToString("") { """<item component="ComponentInfo{p$it/p$it.A}" drawable="d$it"/>""" }
        assertEquals(AppFilter.MAX_ITEMS, parse("<resources>$items</resources>").size)
    }
}
