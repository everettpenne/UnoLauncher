package com.jake.duolauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsSearchTest {
    @Test fun aTitleThatStartsWithTheQueryComesFirst() {
        val r = SettingsSearch.search("island")
        assertTrue(r.isNotEmpty())
        assertTrue("starts-with first", r.first().title.lowercase().startsWith("island"))
    }

    @Test fun keywordsFindASettingByAnotherName() {
        assertEquals(CustomizationPage.PRIVACY, SettingsSearch.search("do not disturb").first().page)
        assertEquals(CustomizationPage.APPEARANCE, SettingsSearch.search("dark mode").first().page)
        assertEquals(CustomizationPage.KEYBOARD, SettingsSearch.search("typo").first().page)
    }

    @Test fun everyWordMustMatchAndEmptyFindsNothing() {
        assertTrue(SettingsSearch.search("").isEmpty())
        assertTrue(SettingsSearch.search("haptics banana").isEmpty())
        assertEquals("Haptics", SettingsSearch.search("HAPTICS").first().title)
    }

    /** Every switch, slider and group on the settings pages is findable, so a new one cannot be added without being added to search. */
    @Test fun everySettingOnThePagesCanBeFound() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val labels = root.walkTopDown().filter { it.extension == "kt" && it.name != "IslandDebugSettings.kt" }.flatMap { f ->
            Regex("""(?:SettingsSwitch|CustomizationSlider|SettingsGroupLabel)\("([^"]+)"""").findAll(f.readText()).map { it.groupValues[1] }
        }.toSet() - setOf("Developer HUD (debug build)")
        val missing = labels.filter { label -> SettingsSearch.search(label).none { it.title.equals(label, ignoreCase = true) } }
        assertTrue("Add these to SettingsSearch.entries: $missing", missing.isEmpty())
    }

    @Test fun aSettingIsFoundOnThePageItMovedTo() {
        fun page(query: String) = SettingsSearch.search(query).first().page
        assertEquals(CustomizationPage.APP_ICONS, page("icon shape"))
        // "hyper glass" is two settings now: the icon style, and the cards (Appearance › Glass).
        assertEquals(CustomizationPage.APP_ICONS, page("hyper glass icon"))
        assertEquals(CustomizationPage.APPEARANCE, page("hyper glass cards"))
        assertTrue(SettingsSearch.search("hyper glass").map { it.page }.containsAll(listOf(CustomizationPage.APP_ICONS, CustomizationPage.APPEARANCE)))
        assertEquals(CustomizationPage.ISLAND_SHOWS, page("live scores"))
        assertEquals(CustomizationPage.ISLAND_SHOWS, page("favourite teams"))
        assertEquals(CustomizationPage.ISLAND_NOTIFICATIONS, page("people only"))
        assertEquals(CustomizationPage.ISLAND_EXTRAS, page("island widget"))
        assertEquals(CustomizationPage.ASSISTANT, page("let qwen do tasks"))
        assertEquals(CustomizationPage.PRIVACY_DATA, page("permission ledger"))
        // And what stayed where it was.
        assertEquals(CustomizationPage.ISLAND, page("pill at rest"))
        assertEquals(CustomizationPage.SEARCH, page("search button opens google"))
        assertEquals(CustomizationPage.PRIVACY, page("badges"))
    }

    @Test fun everyPageHasANameAndAWayBack() {
        for (page in CustomizationPage.entries) {
            assertTrue(page.name, SettingsSearch.pageName(page).isNotBlank())
            // Back from any page reaches the first page in at most two steps, and never loops.
            var at = page; var steps = 0
            while (at != CustomizationPage.OVERVIEW) { at = at.parent(); steps++; assertTrue("$page loops", steps <= 2) }
        }
        assertEquals(CustomizationPage.APPEARANCE, CustomizationPage.APP_ICONS.parent())
        for (inside in listOf(CustomizationPage.ISLAND_SHOWS, CustomizationPage.ISLAND_NOTIFICATIONS, CustomizationPage.ISLAND_EXTRAS))
            assertEquals(CustomizationPage.ISLAND, inside.parent())
        // Every entry of the search points at a page that exists, and no two pages share a name.
        assertEquals(CustomizationPage.entries.size, CustomizationPage.entries.map { SettingsSearch.pageName(it) }.toSet().size)
    }
}
