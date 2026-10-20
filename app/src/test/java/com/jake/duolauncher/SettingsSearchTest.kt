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
}
