package com.jake.duolauncher

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SettingsBackupTest {
    @Test fun portableKeepsPlainValuesAndDropsDeviceOnes() {
        val kept = SettingsBackup.portable(SettingsBackup.EXTRAS, mapOf("liveUpdates" to true, "webPackage" to "org.x", "kbHapticStrength" to .5f, "bad key" to 1, "set" to setOf("a")))
        assertEquals(setOf("liveUpdates", "kbHapticStrength"), kept.keys)
        val appearance = SettingsBackup.portable(SettingsBackup.APPEARANCE, mapOf("islandScale" to .4f, "debugHud" to true, "debugCutout" to "1,2,3,4", "lat" to "1.0", "place" to "Home"))
        assertEquals(setOf("islandScale"), appearance.keys)
    }

    @Test fun roundTripsThroughJson() {
        val snapshot = SettingsSnapshot(mapOf("liveUpdates" to false, "kbOneHanded" to 2, "iconStyle" to "THEMED"), mapOf("islandScale" to .25f))
        val back = SettingsBackup.decode(SettingsBackup.encode(snapshot))
        assertEquals(false, back.extras["liveUpdates"])
        assertEquals(2, (back.extras["kbOneHanded"] as Number).toInt())
        assertEquals("THEMED", back.extras["iconStyle"])
        assertEquals(.25f, (back.appearance["islandScale"] as Number).toFloat(), 1e-6f)
        assertEquals(4, back.count)
    }

    @Test fun decodeIgnoresExcludedKeysEvenIfPresent() {
        val back = SettingsBackup.decode(JSONObject("""{"appearance":{"debugHud":true,"islandScale":0.5},"extras":{"webPackage":"evil"}}"""))
        assertEquals(setOf("islandScale"), back.appearance.keys)
        assertTrue(back.extras.isEmpty())
    }

    @Test fun decodeRefusesBadShapes() {
        listOf(
            """{"extras":{"a b":1}}""",
            """{"extras":{"x":[1]}}""",
            """{"extras":{"x":{"y":1}}}""",
            """{"extras":{"x":null}}""",
            """{"extras":{"x":"${"a".repeat(2_001)}"}}""",
        ).forEach { raw ->
            try { SettingsBackup.decode(JSONObject(raw)); fail("accepted $raw") } catch (_: Exception) { }
        }
    }

    @Test fun missingSectionsAreEmpty() {
        assertEquals(0, SettingsBackup.decode(JSONObject("{}")).count)
        assertFalse(SettingsBackup.exportable(SettingsBackup.EXTRAS, "webPackage"))
        assertNull(null)
    }
}
